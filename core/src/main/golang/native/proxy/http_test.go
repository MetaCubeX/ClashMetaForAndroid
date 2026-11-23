package proxy

import (
	"net"
	"testing"
	"time"
)

func TestStartKeepsOldListenerUntilNewBindSucceeds(t *testing.T) {
	t.Cleanup(Stop)

	first, err := Start("127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	if first == "" {
		t.Fatal("empty listen address")
	}
	if listenerAddress() != first {
		t.Fatalf("listener address = %s, want %s", listenerAddress(), first)
	}
	if !canDial(first) {
		t.Fatalf("%s is not accepting", first)
	}

	held, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer held.Close()

	if _, err := Start(held.Addr().String()); err == nil {
		t.Fatal("expected bind failure")
	}
	if listenerAddress() != first {
		t.Fatalf("listener changed after bind failure: %s", listenerAddress())
	}
	if !canDial(first) {
		t.Fatalf("%s stopped accepting after bind failure", first)
	}

	var second string
	for i := 0; i < 5; i++ {
		probe, err := net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			t.Fatal(err)
		}
		addr := probe.Addr().String()
		_ = probe.Close()
		if addr == first {
			continue
		}
		second, err = Start(addr)
		if err != nil {
			t.Fatal(err)
		}
		break
	}
	if second == "" || second == first {
		t.Fatalf("second address = %q, first = %q", second, first)
	}
	if listenerAddress() != second {
		t.Fatalf("listener address = %s, want %s", listenerAddress(), second)
	}
	if canDial(first) {
		t.Fatalf("old listener %s still accepts connections", first)
	}
	if !canDial(second) {
		t.Fatalf("new listener %s is not accepting", second)
	}
}

func listenerAddress() string {
	if listener == nil {
		return ""
	}
	return listener.Address()
}

func canDial(addr string) bool {
	conn, err := net.DialTimeout("tcp", addr, time.Second)
	if err != nil {
		return false
	}
	_ = conn.Close()
	return true
}
