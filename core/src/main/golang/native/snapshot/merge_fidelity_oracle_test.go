package snapshot

import (
	"os"
	P "path/filepath"
	"strings"
	"testing"
)

// Layer 2 (oracle half) of subscription-update-fidelity. For each fetched/merged
// fixture pair emitted by MergeFidelityFixtureTest (Kotlin), assert the engine
// accepts the merged config AND that it CONTAINS everything the fetched
// subscription shipped: no proxy, group, rule, rule-provider or proxy-provider is
// lost by the merge/GC. (Containment, not equality — the merge legitimately ADDS
// preserved local entries.)
//
// Skips if fixtures are absent (run the Kotlin emitter first).
func TestMergeFidelity_Containment(t *testing.T) {
	dir := P.Join("testdata", "merge")
	fetchedFiles, _ := P.Glob(P.Join(dir, "*__fetched.yaml"))
	if len(fetchedFiles) == 0 {
		t.Skip("merge fixtures absent — run MergeFidelityFixtureTest (Kotlin) first")
	}

	for _, ff := range fetchedFiles {
		name := strings.TrimSuffix(P.Base(ff), "__fetched.yaml")
		mf := strings.Replace(ff, "__fetched.yaml", "__merged.yaml", 1)

		fetched := mustSnapshot(t, ff)
		merged := mustSnapshot(t, mf) // structural parse

		// Full engine validation (the real runtime parse, config.go:1105) — this is
		// the check that emits the user-visible `rule set [X] not found` when the
		// merge drops a referenced rule-provider. ParseBytes above is only structural.
		if mergedBytes, err := os.ReadFile(mf); err == nil {
			if errMsg := ValidateBytes(mergedBytes); errMsg != "" {
				t.Errorf("%s: engine REJECTED merged config (user-visible failure): %s", name, errMsg)
			}
		}

		subset(t, name, "rules", fetched.Rules, merged.Rules)
		subset(t, name, "proxies", names(fetched.Proxies), names(merged.Proxies))
		subset(t, name, "proxy-groups", names(fetched.ProxyGroups), names(merged.ProxyGroups))
		subset(t, name, "rule-providers", keys(fetched.RuleProviders), keys(merged.RuleProviders))
		subset(t, name, "proxy-providers", keys(fetched.ProxyProviders), keys(merged.ProxyProviders))
	}
}

func mustSnapshot(t *testing.T, path string) ProfileSnapshot {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	snap, err := ParseBytes(data)
	if err != nil {
		t.Fatalf("engine rejected %s: %v", P.Base(path), err)
	}
	return snap
}

func names(items []map[string]any) []string {
	out := make([]string, 0, len(items))
	for _, m := range items {
		if n, ok := m["name"].(string); ok {
			out = append(out, n)
		}
	}
	return out
}

func keys(m map[string]map[string]any) []string {
	out := make([]string, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	return out
}

func subset(t *testing.T, fixture, kind string, want, have []string) {
	t.Helper()
	set := make(map[string]bool, len(have))
	for _, h := range have {
		set[h] = true
	}
	for _, w := range want {
		if !set[w] {
			t.Errorf("%s: merge LOST %s %q (fetched had it, merged does not)", fixture, kind, w)
		}
	}
}
