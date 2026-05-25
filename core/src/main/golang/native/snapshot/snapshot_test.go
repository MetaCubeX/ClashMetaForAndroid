package snapshot

import (
	"encoding/json"
	"os"
	P "path"
	"strings"
	"testing"
)

// Минимальный профиль с logical rule и несколькими провайдерами/группами.
// AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT — это форма правила, которая
// сейчас разваливается в RuleMapper.parseRuleLine на стороне Kotlin
// (split по запятой не понимает скобок). Snapshot должен вернуть его
// одной целой строкой.
const fixtureProfileYAML = `
mixed-port: 7890
allow-lan: false

proxy-providers:
  sub1:
    type: http
    url: "https://example.com/sub1.yaml"
    path: ./providers/sub1.yaml
    interval: 86400

rule-providers:
  myrules:
    type: http
    behavior: classical
    url: "https://example.com/rules.yaml"
    path: ./ruleset/myrules.yaml
    interval: 86400

proxies:
  - name: "manual-node"
    type: ss
    server: 1.2.3.4
    port: 8388
    cipher: aes-256-gcm
    password: "test"

proxy-groups:
  - name: GLOBAL
    type: select
    proxies:
      - DIRECT
      - manual-node

rules:
  - DOMAIN,example.com,DIRECT
  - AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT
  - OR,((GEOIP,CN),(DOMAIN-SUFFIX,cn)),DIRECT
  - SUB-RULE,((DOMAIN,foo.com)),GLOBAL
  - DOMAIN-REGEX,foo(a|b),DIRECT
  - MATCH,GLOBAL
`

func writeFixtureProfile(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	if err := os.WriteFile(P.Join(dir, "config.yaml"), []byte(fixtureProfileYAML), 0o644); err != nil {
		t.Fatalf("write fixture: %v", err)
	}
	return dir
}

func TestParse_KeepsLogicalRulesWhole(t *testing.T) {
	dir := writeFixtureProfile(t)

	snap, err := Parse(dir)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}

	if len(snap.Rules) != 6 {
		t.Fatalf("expected 6 rules, got %d (%v)", len(snap.Rules), snap.Rules)
	}

	// Именно те типы правил, которые раньше ломались split-by-comma.
	// Все они должны прийти целыми строками с балансом скобок.
	wantRules := map[int]string{
		1: "AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT",
		2: "OR,((GEOIP,CN),(DOMAIN-SUFFIX,cn)),DIRECT",
		3: "SUB-RULE,((DOMAIN,foo.com)),GLOBAL",
		4: "DOMAIN-REGEX,foo(a|b),DIRECT",
	}
	for index, expected := range wantRules {
		got := snap.Rules[index]
		if got != expected {
			t.Errorf("rules[%d] = %q, want %q", index, got, expected)
		}
		if strings.Count(got, "(") != strings.Count(got, ")") {
			t.Errorf("rules[%d] = %q has unbalanced parens", index, got)
		}
	}
}

func TestParse_PopulatesAllSections(t *testing.T) {
	dir := writeFixtureProfile(t)

	snap, err := Parse(dir)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}

	if got := len(snap.Proxies); got != 1 {
		t.Errorf("expected 1 inline proxy, got %d", got)
	}
	if got := len(snap.ProxyGroups); got != 1 {
		t.Errorf("expected 1 proxy group, got %d", got)
	}
	if got := len(snap.ProxyProviders); got != 1 {
		t.Errorf("expected 1 proxy provider, got %d", got)
	}
	if _, ok := snap.ProxyProviders["sub1"]; !ok {
		t.Errorf("proxy-providers should contain key 'sub1', got %v", snap.ProxyProviders)
	}
	if got := len(snap.RuleProviders); got != 1 {
		t.Errorf("expected 1 rule provider, got %d", got)
	}
	if _, ok := snap.RuleProviders["myrules"]; !ok {
		t.Errorf("rule-providers should contain key 'myrules', got %v", snap.RuleProviders)
	}
}

func TestMarshalJSON_SuccessEnvelope(t *testing.T) {
	dir := writeFixtureProfile(t)

	raw := MarshalJSON(dir)

	var env envelope
	if err := json.Unmarshal([]byte(raw), &env); err != nil {
		t.Fatalf("unmarshal envelope: %v\nraw=%s", err, raw)
	}
	if !env.OK {
		t.Fatalf("expected ok=true, got envelope=%+v", env)
	}
	if env.Snapshot == nil {
		t.Fatal("expected snapshot to be non-nil on success")
	}
	if env.Error != "" {
		t.Errorf("expected empty error on success, got %q", env.Error)
	}
}

func TestMarshalJSON_ErrorEnvelopeOnMissingFile(t *testing.T) {
	dir := t.TempDir()

	raw := MarshalJSON(dir)

	var env envelope
	if err := json.Unmarshal([]byte(raw), &env); err != nil {
		t.Fatalf("unmarshal envelope: %v\nraw=%s", err, raw)
	}
	if env.OK {
		t.Errorf("expected ok=false for missing config, got %+v", env)
	}
	if env.Error == "" {
		t.Error("expected non-empty error message in envelope")
	}
	if env.Snapshot != nil {
		t.Errorf("expected snapshot to be nil on error, got %+v", env.Snapshot)
	}
}

func TestMarshalJSON_ErrorEnvelopeOnBrokenYAML(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(P.Join(dir, "config.yaml"), []byte("rules: [\n  - unterminated"), 0o644); err != nil {
		t.Fatalf("write broken fixture: %v", err)
	}

	raw := MarshalJSON(dir)

	var env envelope
	if err := json.Unmarshal([]byte(raw), &env); err != nil {
		t.Fatalf("unmarshal envelope: %v\nraw=%s", err, raw)
	}
	if env.OK {
		t.Errorf("expected ok=false on broken YAML, got %+v", env)
	}
	if env.Error == "" {
		t.Error("expected non-empty error message describing parse failure")
	}
}

func TestParseBytes_KeepsLogicalRulesWhole(t *testing.T) {
	// Same coverage as the on-disk Parse path: in-memory fixture must give
	// the same snapshot shape so callers can use either entry interchangeably.
	snap, err := ParseBytes([]byte(fixtureProfileYAML))
	if err != nil {
		t.Fatalf("ParseBytes: %v", err)
	}
	if len(snap.Rules) != 6 {
		t.Fatalf("expected 6 rules, got %d", len(snap.Rules))
	}
	if got := snap.Rules[1]; got != "AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT" {
		t.Errorf("rules[1] = %q, want logical rule whole", got)
	}
}

func TestMarshalJSONFromBytes_SuccessEnvelope(t *testing.T) {
	raw := MarshalJSONFromBytes([]byte(fixtureProfileYAML))

	var env envelope
	if err := json.Unmarshal([]byte(raw), &env); err != nil {
		t.Fatalf("unmarshal envelope: %v\nraw=%s", err, raw)
	}
	if !env.OK || env.Snapshot == nil {
		t.Fatalf("expected ok=true with snapshot, got %+v", env)
	}
}

func TestMarshalJSONFromBytes_ErrorEnvelopeOnBrokenYAML(t *testing.T) {
	raw := MarshalJSONFromBytes([]byte("rules: [\n  - unterminated"))

	var env envelope
	if err := json.Unmarshal([]byte(raw), &env); err != nil {
		t.Fatalf("unmarshal envelope: %v\nraw=%s", err, raw)
	}
	if env.OK {
		t.Errorf("expected ok=false on broken YAML, got %+v", env)
	}
	if env.Error == "" {
		t.Error("expected non-empty error message")
	}
}
