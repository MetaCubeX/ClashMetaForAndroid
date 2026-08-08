package tunnel

import (
	"testing"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/adapter/outbound"
)

// Builds a real Vless outbound wrapped the same way tunnel.Proxies() holds
// them, then checks that proxyDetailsOf actually reaches the adapter's private
// option struct. Guards the reflection target: reflecting over the C.Proxy
// wrapper instead of over Adapter() silently yields nothing at all.
func TestProxyDetailsReachAdapterOption(t *testing.T) {
	out, err := outbound.NewVless(outbound.VlessOption{
		Name:              "test-vless",
		Server:            "example.com",
		Port:              443,
		UUID:              "b831381d-6324-4d53-ad4f-8cda48b30811",
		TLS:               true,
		Network:           "ws",
		ServerName:        "sni.example.com",
		ClientFingerprint: "chrome",
	})
	if err != nil {
		t.Fatalf("NewVless: %v", err)
	}

	// The runtime does not hand out bare outbounds: proxies are wrapped in
	// *outbound.autoCloseProxyAdapter, which embeds the real adapter as an
	// interface field. Both shapes must resolve.
	for _, tc := range []struct {
		name    string
		adapter outbound.ProxyAdapter
	}{
		{"bare", out},
		{"autoClose", outbound.NewAutoCloseProxyAdapter(out)},
	} {
		t.Run(tc.name, func(t *testing.T) {
			got := map[string]string{}
			for _, d := range proxyDetailsOf(adapter.NewProxy(tc.adapter)) {
				got[d.Label] = d.Value
			}
			t.Logf("details: %v", got)

			for _, label := range []string{"UUID", "TLS", "Network", "SNI", "Fingerprint"} {
				if _, ok := got[label]; !ok {
					t.Errorf("missing %q; option reflection did not resolve", label)
				}
			}
			if got["UUID"] == "b831381d-6324-4d53-ad4f-8cda48b30811" {
				t.Errorf("UUID leaked unmasked: %q", got["UUID"])
			}
		})
	}
}
