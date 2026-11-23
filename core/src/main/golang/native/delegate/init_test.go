package delegate

import (
	"errors"
	"net"
	"syscall"
	"testing"

	"cfa/native/app"
	"cfa/native/platform"

	"github.com/metacubex/mihomo/component/dialer"
)

func TestDefaultSocketHookProtectResult(t *testing.T) {
	if platform.ShouldBlockConnection() {
		t.Fatal("ShouldBlockConnection is true")
	}

	Init(t.TempDir(), "test", "branch_hash_time", 29)
	t.Cleanup(func() {
		app.ApplyTunContext(nil, nil)
	})

	raw := tcpRawConn(t)

	called := false
	app.ApplyTunContext(func(fd int) bool {
		called = true
		return false
	}, nil)
	err := dialer.DefaultSocketHook("tcp", "127.0.0.1:9", raw)
	if !called {
		t.Fatal("mark was not called")
	}
	if !errors.Is(err, errProtect) {
		t.Fatalf("protect false: got %v", err)
	}

	called = false
	app.ApplyTunContext(func(fd int) bool {
		called = true
		return true
	}, nil)
	if err := dialer.DefaultSocketHook("tcp", "127.0.0.1:9", raw); err != nil {
		t.Fatalf("protect true: %v", err)
	}
	if !called {
		t.Fatal("mark was not called")
	}
}

func tcpRawConn(t *testing.T) syscall.RawConn {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = ln.Close() })

	conn, err := net.Dial("tcp", ln.Addr().String())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = conn.Close() })

	raw, err := conn.(syscall.Conn).SyscallConn()
	if err != nil {
		t.Fatal(err)
	}
	return raw
}
