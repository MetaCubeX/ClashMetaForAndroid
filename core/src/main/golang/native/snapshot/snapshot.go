// Package snapshot exposes a read-only structural view of a mihomo profile
// for ClashFest UI consumers. It is intentionally isolated from
// cfa/native/config (which transitively depends on cfa/native/app and
// Android-specific platform helpers) so that the package can be go-tested
// on any developer workstation.
//
// The snapshot reflects the YAML on disk *as written by the user* — we do
// not apply session/persist overrides or rewrite provider paths the way
// the runtime loader does. That matches what the UI should display when
// the user is editing the file.
package snapshot

import (
	"encoding/json"
	"os"
	P "path"

	"github.com/metacubex/mihomo/config"
)

// ProfileSnapshot is the wire shape sent to Kotlin via JNI. Field names
// match mihomo's YAML keys so that the JSON is recognisable at a glance.
type ProfileSnapshot struct {
	Rules          []string                  `json:"rules"`
	SubRules       map[string][]string       `json:"sub-rules,omitempty"`
	Proxies        []map[string]any          `json:"proxies"`
	ProxyGroups    []map[string]any          `json:"proxy-groups"`
	ProxyProviders map[string]map[string]any `json:"proxy-providers"`
	RuleProviders  map[string]map[string]any `json:"rule-providers"`
	Listeners      []map[string]any          `json:"listeners,omitempty"`
}

// Build projects a parsed RawConfig into the wire-format snapshot. Exposed
// for tests that already have a RawConfig in hand.
func Build(rawCfg *config.RawConfig) ProfileSnapshot {
	return ProfileSnapshot{
		Rules:          rawCfg.Rule,
		SubRules:       rawCfg.SubRules,
		Proxies:        rawCfg.Proxy,
		ProxyGroups:    rawCfg.ProxyGroup,
		ProxyProviders: rawCfg.ProxyProvider,
		RuleProviders:  rawCfg.RuleProvider,
		Listeners:      rawCfg.Listeners,
	}
}

// Parse reads <profileDir>/config.yaml and runs mihomo's YAML→RawConfig
// step. No overrides are applied and no provider paths are rewritten:
// the snapshot is exactly what the user wrote.
func Parse(profileDir string) (ProfileSnapshot, error) {
	data, err := os.ReadFile(P.Join(profileDir, "config.yaml"))
	if err != nil {
		return ProfileSnapshot{}, err
	}
	rawCfg, err := config.UnmarshalRawConfig(data)
	if err != nil {
		return ProfileSnapshot{}, err
	}
	return Build(rawCfg), nil
}

// envelope wraps the snapshot in an ok/error frame so that the synchronous
// JNI getter can carry mihomo's error message back to Kotlin in a single
// call.
type envelope struct {
	OK       bool             `json:"ok"`
	Snapshot *ProfileSnapshot `json:"snapshot,omitempty"`
	Error    string           `json:"error,omitempty"`
}

// MarshalJSON is the JNI-facing entry point. It always returns valid JSON:
// { "ok": true, "snapshot": {...} } on success or
// { "ok": false, "error": "..." } when mihomo refuses the profile.
func MarshalJSON(profileDir string) string {
	snapshot, err := Parse(profileDir)
	if err != nil {
		return mustMarshal(envelope{OK: false, Error: err.Error()})
	}
	return mustMarshal(envelope{OK: true, Snapshot: &snapshot})
}

func mustMarshal(env envelope) string {
	bytes, err := json.Marshal(env)
	if err == nil {
		return string(bytes)
	}
	return `{"ok":false,"error":"failed to marshal snapshot envelope"}`
}
