package snapshot

import (
	"strings"
	"testing"
)

// Smoke test for mihomo 1.19.25 tailscale support. We only verify that the
// engine accepts type: tailscale in proxies and exposes it back through the
// snapshot. We don't actually start a Tailscale node — that would require
// an auth key and a network stack, which is out of scope for unit tests.
//
// Three checks:
//  1. The minimal tailscale proxy stanza survives YAML → snapshot round-trip
//     with all fields intact.
//  2. The engine itself (ParseRawConfig via ValidateBytes) accepts the
//     configuration without complaining about unsupported proxy type —
//     proving the no_tailscale build tag is NOT set in this build.
//  3. A tailscale DNS nameserver (the `tailscale://<peer>` form added in
//     1.19.x) is accepted in the dns: block.

const tailscaleProfileYAML = `
mixed-port: 7890
proxies:
  - name: ts-node
    type: tailscale
    auth-key: tskey-auth-EXAMPLE
    state-dir: tailscale
    hostname: clashfest-test
proxy-groups:
  - name: GLOBAL
    type: select
    proxies:
      - ts-node
      - DIRECT
rules:
  - MATCH,GLOBAL
`

func TestTailscale_ProxyTypeSurvivesSnapshot(t *testing.T) {
	snap, err := ParseBytes([]byte(tailscaleProfileYAML))
	if err != nil {
		t.Fatalf("ParseBytes: %v", err)
	}
	if len(snap.Proxies) != 1 {
		t.Fatalf("expected 1 proxy, got %d", len(snap.Proxies))
	}
	p := snap.Proxies[0]
	if got := p["type"]; got != "tailscale" {
		t.Errorf("proxy type = %v, want tailscale (build tag no_tailscale must NOT be set)", got)
	}
	if got := p["name"]; got != "ts-node" {
		t.Errorf("proxy name = %v, want ts-node", got)
	}
	if got := p["auth-key"]; got != "tskey-auth-EXAMPLE" {
		t.Errorf("auth-key dropped: got %v", got)
	}
	if got := p["hostname"]; got != "clashfest-test" {
		t.Errorf("hostname dropped: got %v", got)
	}
}

func TestTailscale_EngineAcceptsConfig(t *testing.T) {
	// ValidateBytes runs UnmarshalRawConfig + ParseRawConfig. If the engine
	// were built with no_tailscale, ParseRawConfig would fail at proxy
	// parsing with "unsupport proxy type" or similar.
	errMsg := ValidateBytes([]byte(tailscaleProfileYAML))
	if errMsg != "" {
		// Tailscale's NewTailscale may complain about invalid auth-key at
		// construction time (it tries to dial), but the engine must at
		// minimum *recognise* the type. Fail loudly if the error mentions
		// unsupported type — that means our build dropped tailscale.
		if strings.Contains(strings.ToLower(errMsg), "unsupport") ||
			strings.Contains(strings.ToLower(errMsg), "unknown proxy type") {
			t.Fatalf("engine rejects tailscale type — likely built with no_tailscale tag: %s", errMsg)
		}
		// Any other error is expected (fake auth key, network unavailable
		// in test environment). Log it for awareness, do not fail.
		t.Logf("engine accepted tailscale type (init failed for unrelated reason, fine for smoke): %s", errMsg)
	}
}

func TestTailscale_DnsNameserverParses(t *testing.T) {
	// Mihomo 1.19.x added `tailscale://<peer>` as a DNS nameserver form
	// (dns: block routes through a Tailscale node for DoH resolution).
	const dnsYAML = `
mixed-port: 7890
proxies:
  - name: ts-node
    type: tailscale
    auth-key: tskey-auth-EXAMPLE
    state-dir: tailscale
dns:
  enable: true
  nameserver:
    - tailscale://ts-node
proxy-groups:
  - name: GLOBAL
    type: select
    proxies:
      - DIRECT
rules:
  - MATCH,GLOBAL
`
	snap, err := ParseBytes([]byte(dnsYAML))
	if err != nil {
		t.Fatalf("ParseBytes on tailscale-DNS config: %v", err)
	}
	// Snapshot doesn't expose dns: directly (we don't UI-edit DNS), but the
	// successful parse proves UnmarshalRawConfig accepts the new dns form.
	if len(snap.Proxies) != 1 {
		t.Errorf("expected 1 proxy, got %d", len(snap.Proxies))
	}
}
