package snapshot

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/metacubex/mihomo/config"

	_ "github.com/metacubex/mihomo/hub/executor"
)

// Repro of a user report (2026-07): a url-test group with `include-all: true`
// and `exclude-filter: '🇸🇪|SE|Sweden'` still listed the Sweden node in
// ClashFest, while other mihomo clients excluded it. This oracle feeds the
// EXACT shape to the real engine and asserts the group membership the engine
// computes — proving whether the exclude-filter semantics live in the engine
// (they do) or are lost somewhere in our pipeline before the engine sees them.
const excludeFilterYAML = `
mixed-port: 7890
mode: rule
proxies:
  - {name: "🇩🇪 Germany", type: ss, server: 1.1.1.1, port: 443, cipher: aes-128-gcm, password: a}
  - {name: "🇫🇮 Finland", type: ss, server: 1.1.1.2, port: 443, cipher: aes-128-gcm, password: a}
  - {name: "🇳🇱 Netherlands 1", type: ss, server: 1.1.1.3, port: 443, cipher: aes-128-gcm, password: a}
  - {name: "🇳🇱 Netherlands 2", type: ss, server: 1.1.1.4, port: 443, cipher: aes-128-gcm, password: a}
  - {name: "🇳🇱 Netherlands 3", type: ss, server: 1.1.1.5, port: 443, cipher: aes-128-gcm, password: a}
  - {name: "🇸🇪 Sweden", type: ss, server: 1.1.1.6, port: 443, cipher: aes-128-gcm, password: a}
proxy-groups:
  - name: "⚡ Fastest For All"
    type: url-test
    url: https://www.gstatic.com/generate_204
    interval: 300
    tolerance: 300
    lazy: true
    include-all: true
    exclude-filter: '🇸🇪|SE|Sweden'
    proxies: []
rules:
  - MATCH,⚡ Fastest For All
`

func groupAllMembers(t *testing.T, cfg *config.Config, groupName string) []string {
	t.Helper()
	p, ok := cfg.Proxies[groupName]
	if !ok {
		names := make([]string, 0, len(cfg.Proxies))
		for k := range cfg.Proxies {
			names = append(names, k)
		}
		t.Fatalf("group %q not found; proxies present: %v", groupName, names)
	}
	data, err := p.MarshalJSON()
	if err != nil {
		t.Fatalf("marshal group: %v", err)
	}
	var meta struct {
		All []string `json:"all"`
	}
	if err := json.Unmarshal(data, &meta); err != nil {
		t.Fatalf("decode group json: %v", err)
	}
	return meta.All
}

func TestExcludeFilter_ExcludesSwedenFromUrlTest(t *testing.T) {
	rawCfg, err := config.UnmarshalRawConfig([]byte(excludeFilterYAML))
	if err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	cfg, err := config.ParseRawConfig(rawCfg)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	defer closeProviders(cfg)

	all := groupAllMembers(t, cfg, "⚡ Fastest For All")

	for _, name := range all {
		if strings.Contains(name, "Sweden") || strings.Contains(name, "🇸🇪") {
			t.Fatalf("Sweden must be excluded by exclude-filter, but group members are: %v", all)
		}
	}
	if len(all) != 5 {
		t.Fatalf("expected 5 members after excluding Sweden, got %d: %v", len(all), all)
	}
}
