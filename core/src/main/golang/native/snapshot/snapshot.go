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

	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/config"
)

// ProfileSnapshot is the wire shape sent to Kotlin via JNI. Field names
// match mihomo's YAML keys so that the JSON is recognisable at a glance.
//
// All sections are omitempty: nil Go maps/slices marshal as `null` by default,
// and kotlinx.serialization on the Kotlin side won't accept `null` for a
// non-nullable Map/List with a default value — it expects either the key
// missing or a real value. Omitting the key entirely lets the Kotlin
// data class default (emptyMap/emptyList) take over cleanly.
type ProfileSnapshot struct {
	Rules          []string                  `json:"rules,omitempty"`
	SubRules       map[string][]string       `json:"sub-rules,omitempty"`
	Proxies        []map[string]any          `json:"proxies,omitempty"`
	ProxyGroups    []map[string]any          `json:"proxy-groups,omitempty"`
	ProxyProviders map[string]map[string]any `json:"proxy-providers,omitempty"`
	RuleProviders  map[string]map[string]any `json:"rule-providers,omitempty"`
	Listeners      []map[string]any          `json:"listeners,omitempty"`
	// Dns and Hosts are taken from a generic parse of the raw YAML — i.e. exactly
	// what the user wrote — NOT from RawConfig (UnmarshalRawConfig starts from
	// DefaultRawConfig, so RawConfig.DNS is always populated with defaults). The
	// DNS & Hosts editor must distinguish "user set a dns: block" from "absent".
	Dns   map[string]any `json:"dns,omitempty"`
	Hosts map[string]any `json:"hosts,omitempty"`
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
	return ParseBytes(data)
}

// ParseBytes is the in-memory counterpart to Parse. It exists for WRITE-side
// flows (dry-runs, validate-before-commit, preview of edited-but-unsaved
// config) where the YAML lives in a Kotlin String, not on disk.
func ParseBytes(data []byte) (ProfileSnapshot, error) {
	rawCfg, err := config.UnmarshalRawConfig(data)
	if err != nil {
		return ProfileSnapshot{}, err
	}
	snapshot := Build(rawCfg)
	snapshot.Dns, snapshot.Hosts = extractDnsHosts(data)
	return snapshot, nil
}

// extractDnsHosts re-parses the raw YAML generically to recover the `dns:` and
// `hosts:` blocks exactly as the user wrote them (no engine defaults). Returns
// nil for an absent block. Best-effort: a parse failure (e.g. age-encrypted
// config) yields nils and is not fatal — the structured parse already
// succeeded.
func extractDnsHosts(data []byte) (dns map[string]any, hosts map[string]any) {
	var generic map[string]any
	if err := yaml.Unmarshal(data, &generic); err != nil {
		return nil, nil
	}
	return asStringMap(generic["dns"]), asStringMap(generic["hosts"])
}

// asStringMap coerces a generic YAML value into a JSON-marshalable
// map[string]any (recursively), or nil if it is not a map.
func asStringMap(v any) map[string]any {
	switch m := v.(type) {
	case map[string]any:
		return normalizeMap(m)
	case map[any]any:
		out := make(map[string]any, len(m))
		for k, val := range m {
			out[toString(k)] = normalizeValue(val)
		}
		return out
	default:
		return nil
	}
}

func normalizeMap(m map[string]any) map[string]any {
	out := make(map[string]any, len(m))
	for k, v := range m {
		out[k] = normalizeValue(v)
	}
	return out
}

func normalizeValue(v any) any {
	switch t := v.(type) {
	case map[string]any:
		return normalizeMap(t)
	case map[any]any:
		return asStringMap(t)
	case []any:
		out := make([]any, len(t))
		for i, e := range t {
			out[i] = normalizeValue(e)
		}
		return out
	default:
		return v
	}
}

func toString(v any) string {
	if s, ok := v.(string); ok {
		return s
	}
	return ""
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

// MarshalJSONFromBytes is the in-memory variant. Same envelope contract as
// MarshalJSON; see ParseBytes for when to use it.
func MarshalJSONFromBytes(data []byte) string {
	snapshot, err := ParseBytes(data)
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
