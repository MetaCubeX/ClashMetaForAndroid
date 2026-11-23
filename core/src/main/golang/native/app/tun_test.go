package app

import "testing"

func TestMarkSocketReturnsProtectResult(t *testing.T) {
	t.Cleanup(func() { ApplyTunContext(nil, nil) })

	ApplyTunContext(func(int) bool { return false }, nil)
	if MarkSocket(7) {
		t.Fatal("expected false")
	}

	ApplyTunContext(func(int) bool { return true }, nil)
	if !MarkSocket(7) {
		t.Fatal("expected true")
	}

	ApplyTunContext(nil, nil)
	if !MarkSocket(7) {
		t.Fatal("nil protect should not fail the dial")
	}
}
