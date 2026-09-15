package routeconfig

import (
	"testing"

	"github.com/metacubex/mihomo/config"
)

// Host test of the actual pinned parser used by CMFA UnmarshalAndPatch.
// The CMFA native/config package itself imports Android-only platform code.
func TestActiveYamlRouteField(t *testing.T) {
	raw, err := config.UnmarshalRawConfig([]byte("tun:\n  route-exclude-address:\n    - 192.0.2.0/24\n    - 2001:db8::/32\n"))
	if err != nil {
		t.Fatal(err)
	}
	if len(raw.Tun.RouteExcludeAddress) != 2 || raw.Tun.RouteExcludeAddress[0].String() != "192.0.2.0/24" || raw.Tun.RouteExcludeAddress[1].String() != "2001:db8::/32" {
		t.Fatal("typed parser lost the exclusion field")
	}
}

func TestBadYamlCidrRejects(t *testing.T) {
	if _, err := config.UnmarshalRawConfig([]byte("tun:\n  route-exclude-address:\n    - invalid-prefix\n")); err == nil {
		t.Fatal("malformed typed CIDR accepted")
	}
}

func TestAbsentAndEmptyYaml(t *testing.T) {
	for _, yaml := range []string{"{}", "tun:\n  route-exclude-address: []\n"} {
		raw, err := config.UnmarshalRawConfig([]byte(yaml))
		if err != nil || len(raw.Tun.RouteExcludeAddress) != 0 {
			t.Fatalf("empty/absent exclusion changed: %v", err)
		}
	}
}
