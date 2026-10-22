package config

import (
	"encoding/json"
	"net/netip"
	"sync"
	"testing"
)

func TestRouteSnapshotCopiesAndMasks(t *testing.T) {
	prefixes := []netip.Prefix{netip.MustParsePrefix("192.0.2.7/24"), netip.MustParsePrefix("2001:db8::1/64")}
	publishRouteExclusions(prefixes)
	prefixes[0] = netip.MustParsePrefix("10.0.0.0/8")
	got := QueryRouteExclusions()
	if got[0] != "192.0.2.0/24" || got[1] != "2001:db8::/64" {
		t.Fatal(got)
	}
	got[0] = "modified"
	if QueryRouteExclusions()[0] != "192.0.2.0/24" {
		t.Fatal("snapshot aliases caller memory")
	}
}

func TestRouteSnapshotClearsToJsonArray(t *testing.T) {
	publishRouteExclusions([]netip.Prefix{netip.MustParsePrefix("0.0.0.0/0")})
	publishRouteExclusions(nil)
	encoded, err := json.Marshal(QueryRouteExclusions())
	if err != nil || string(encoded) != "[]" {
		t.Fatalf("%s %v", encoded, err)
	}
}

func TestConcurrentRouteSnapshot(t *testing.T) {
	var wg sync.WaitGroup
	for i := 0; i < 8; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < 100; j++ {
				publishRouteExclusions([]netip.Prefix{netip.MustParsePrefix("::/0")})
				if got := QueryRouteExclusions(); len(got) != 1 || got[0] != "::/0" {
					t.Error(got)
				}
			}
		}()
	}
	wg.Wait()
}
