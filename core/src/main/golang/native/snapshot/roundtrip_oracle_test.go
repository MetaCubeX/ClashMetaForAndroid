package snapshot

import (
	"encoding/json"
	"os"
	P "path/filepath"
	"testing"
)

// Layer 2 (oracle half) — the engine certifies our Kotlin re-serialization.
// WritePipelineOracleFixtureTest (Kotlin) emits, for a curated config, the
// original plus a per-top-level-block re-dump into testdata/roundtrip/. Here we
// snapshot each and assert every block re-dump is snapshot-identical to the
// original: the engine reads our re-dump with the same semantics.
//
// Skips if fixtures are absent (run the Kotlin emitter first).
func TestRoundTripOracle_SnapshotEquality(t *testing.T) {
	dir := P.Join("testdata", "roundtrip")
	original := P.Join(dir, "original.yaml")
	if _, err := os.Stat(original); err != nil {
		t.Skip("oracle fixtures absent — run WritePipelineOracleFixtureTest (Kotlin) first")
	}

	base := snapshotJSON(t, original)

	blocks, err := P.Glob(P.Join(dir, "block_*.yaml"))
	if err != nil || len(blocks) == 0 {
		t.Fatalf("no block fixtures found in %s", dir)
	}
	for _, f := range blocks {
		got := snapshotJSON(t, f)
		if got != base {
			t.Errorf("%s: snapshot differs from original\n  base = %s\n  got  = %s",
				P.Base(f), base, got)
		}
	}
	t.Logf("verified %d block re-dumps are snapshot-equal to the original", len(blocks))
}

// Security transforms intentionally change semantics. Their oracle is engine
// acceptance rather than snapshot equality: the post-processed config must
// still be a config mihomo itself accepts.
func TestHardeningOracle_EngineAcceptsFixtures(t *testing.T) {
	fixtures, err := P.Glob(P.Join("testdata", "hardening", "*.yaml"))
	if err != nil || len(fixtures) == 0 {
		t.Skip("hardening fixtures absent — run WritePipelineOracleFixtureTest (Kotlin) first")
	}

	for _, f := range fixtures {
		snapshotJSON(t, f)
	}
	t.Logf("verified %d hardened fixtures are accepted by the engine", len(fixtures))
}

func snapshotJSON(t *testing.T, path string) string {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	snap, err := ParseBytes(data)
	if err != nil {
		t.Fatalf("engine rejected %s: %v", P.Base(path), err)
	}
	b, err := json.Marshal(snap)
	if err != nil {
		t.Fatalf("marshal snapshot %s: %v", P.Base(path), err)
	}
	return string(b)
}
