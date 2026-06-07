package snapshot

import (
	"strings"
	"testing"
)

const dnsHostsProfile = `
mixed-port: 7890
dns:
  enable: true
  enhanced-mode: fake-ip
  listen: 127.0.0.1:0
  cache-algorithm: arc
  nameserver:
    - https://1.1.1.1/dns-query
  direct-nameserver:
    - https://common.dot.dns.yandex.net/dns-query
  proxy-server-nameserver:
    - https://8.8.8.8/dns-query
hosts:
  example.com: 1.2.3.4
  router.lan: 192.168.1.1
proxies:
  - {name: p1, type: socks5, server: 127.0.0.1, port: 1080}
proxy-groups:
  - {name: G, type: select, proxies: [p1]}
rules:
  - MATCH,G
`

const noDnsHostsProfile = `
mixed-port: 7890
proxies:
  - {name: p1, type: socks5, server: 127.0.0.1, port: 1080}
proxy-groups:
  - {name: G, type: select, proxies: [p1]}
rules:
  - MATCH,G
`

func TestSnapshot_DnsHosts_RoundTrips(t *testing.T) {
	snap, err := ParseBytes([]byte(dnsHostsProfile))
	if err != nil {
		t.Fatalf("ParseBytes failed: %v", err)
	}
	if snap.Dns == nil {
		t.Fatal("expected dns block, got nil")
	}
	if snap.Dns["enhanced-mode"] != "fake-ip" {
		t.Errorf("enhanced-mode = %v, want fake-ip", snap.Dns["enhanced-mode"])
	}
	if snap.Dns["cache-algorithm"] != "arc" {
		t.Errorf("cache-algorithm = %v, want arc", snap.Dns["cache-algorithm"])
	}
	ns, ok := snap.Dns["direct-nameserver"].([]any)
	if !ok || len(ns) != 1 || ns[0] != "https://common.dot.dns.yandex.net/dns-query" {
		t.Errorf("direct-nameserver = %v", snap.Dns["direct-nameserver"])
	}
	if snap.Hosts["example.com"] != "1.2.3.4" {
		t.Errorf("hosts[example.com] = %v, want 1.2.3.4", snap.Hosts["example.com"])
	}

	// The envelope must marshal to valid JSON (interface-keyed maps would break it).
	env := MarshalJSONFromBytes([]byte(dnsHostsProfile))
	if !strings.HasPrefix(env, `{"ok":true`) {
		t.Fatalf("envelope not ok: %s", env)
	}
	if !strings.Contains(env, `"cache-algorithm":"arc"`) || !strings.Contains(env, `"example.com":"1.2.3.4"`) {
		t.Errorf("envelope missing dns/hosts: %s", env)
	}
}

func TestSnapshot_NoDnsHosts_OmitsKeys(t *testing.T) {
	snap, err := ParseBytes([]byte(noDnsHostsProfile))
	if err != nil {
		t.Fatalf("ParseBytes failed: %v", err)
	}
	if snap.Dns != nil {
		t.Errorf("expected nil dns, got %v", snap.Dns)
	}
	if snap.Hosts != nil {
		t.Errorf("expected nil hosts, got %v", snap.Hosts)
	}
	env := MarshalJSONFromBytes([]byte(noDnsHostsProfile))
	if strings.Contains(env, `"dns"`) || strings.Contains(env, `"hosts"`) {
		t.Errorf("envelope should omit dns/hosts: %s", env)
	}
}
