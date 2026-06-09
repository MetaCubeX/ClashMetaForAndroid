package snapshot

import "testing"

func TestSnapshotTunnels(t *testing.T) {
	cfg := "" +
		"proxies:\n" +
		"  - {name: p1, type: socks5, server: 127.0.0.1, port: 1080}\n" +
		"proxy-groups:\n" +
		"  - {name: G, type: select, proxies: [p1]}\n" +
		"rules:\n" +
		"  - MATCH,G\n" +
		"tunnels:\n" +
		"  - tcp/udp,127.0.0.1:6553,1.1.1.1:53,G\n" +
		"  - network: [tcp]\n" +
		"    address: 127.0.0.1:7777\n" +
		"    target: target.com:80\n" +
		"    proxy: G\n"

	snap, err := ParseBytes([]byte(cfg))
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if len(snap.Tunnels) != 2 {
		t.Fatalf("want 2 tunnels, got %d (%v)", len(snap.Tunnels), snap.Tunnels)
	}
	// string form normalized to the map shape
	t0 := snap.Tunnels[0]
	if t0["address"] != "127.0.0.1:6553" || t0["target"] != "1.1.1.1:53" || t0["proxy"] != "G" {
		t.Errorf("string-form tunnel not normalized: %v", t0)
	}
	if nets, ok := t0["network"].([]string); !ok || len(nets) != 2 {
		t.Errorf("string-form network want [tcp udp], got %v", t0["network"])
	}
	// map form
	if snap.Tunnels[1]["address"] != "127.0.0.1:7777" {
		t.Errorf("map-form tunnel wrong: %v", snap.Tunnels[1])
	}
}

func TestSnapshotTunnelsAbsentOmitted(t *testing.T) {
	snap, err := ParseBytes([]byte("proxies:\n  - {name: p1, type: socks5, server: 127.0.0.1, port: 1080}\nrules:\n  - MATCH,p1\n"))
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if snap.Tunnels != nil {
		t.Errorf("tunnels should be nil when absent, got %v", snap.Tunnels)
	}
}
