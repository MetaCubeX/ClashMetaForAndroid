package main

//#include "bridge.h"
import "C"

import (
	"context"
	"io"
	"sync"
	"time"
	"unsafe"

	"golang.org/x/sync/semaphore"

	"cfa/native/app"
	"cfa/native/tun"
)

var rTunLock sync.Mutex
var rTun *remoteTun

type remoteTun struct {
	closer   io.Closer
	callback unsafe.Pointer

	closed bool
	limit  *semaphore.Weighted
}

func (t *remoteTun) markSocket(fd int) {
	_ = t.limit.Acquire(context.Background(), 1)
	defer t.limit.Release(1)

	if t.closed {
		return
	}

	C.mark_socket(t.callback, C.int(fd))
}

func (t *remoteTun) querySocketUid(protocol int, source, target string) int {
	_ = t.limit.Acquire(context.Background(), 1)
	defer t.limit.Release(1)

	if t.closed {
		return -1
	}

	return int(C.query_socket_uid(t.callback, C.int(protocol), C.CString(source), C.CString(target)))
}

func (t *remoteTun) close() {
	_ = t.limit.Acquire(context.TODO(), 4)

	t.closed = true

	// Drop the global tun context synchronously: no new markSocket/querySocketUid should target this
	// instance while its stack drains, and doing it now (rather than in the background goroutine)
	// avoids a late clear clobbering the context a subsequent startTun installs on reconnect.
	app.ApplyTunContext(nil, nil)

	// sing_tun's gVisor/mixed Close() drains a userspace netstack and can block for several seconds.
	// Don't hold the VpnService teardown (and the reconnect handoff under rTunLock) hostage to it —
	// finish the close, callback release and semaphore drain in the background, waiting only briefly.
	// The System stack closes instantly and hits the fast path; gVisor/mixed keep closing after we
	// return, which is safe: the context is already cleared and the fd is per-instance.
	closer := t.closer
	callback := t.callback
	done := make(chan struct{})
	go func() {
		if closer != nil {
			_ = closer.Close()
		}
		C.release_object(callback)
		t.limit.Release(4)
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(700 * time.Millisecond):
	}
}

//export startTun
func startTun(fd C.int, stack, gateway, portal, dns C.c_string, callback unsafe.Pointer) (result C.int) {
	// On panic return a failure code (1), not the success value (0).
	defer func() {
		if r := recover(); r != nil {
			logRecover("startTun", r)
			result = 1
		}
	}()
	rTunLock.Lock()
	defer rTunLock.Unlock()

	if rTun != nil {
		rTun.close()
		rTun = nil
	}

	f := int(fd)
	s := C.GoString(stack)
	g := C.GoString(gateway)
	p := C.GoString(portal)
	d := C.GoString(dns)

	remote := &remoteTun{callback: callback, closed: false, limit: semaphore.NewWeighted(4)}

	app.ApplyTunContext(remote.markSocket, remote.querySocketUid)

	closer, err := tun.Start(f, s, g, p, d)
	if err != nil {
		remote.close()

		return 1
	}

	remote.closer = closer

	rTun = remote

	return 0
}

//export stopTun
func stopTun() {
	defer guard("stopTun")()
	rTunLock.Lock()
	defer rTunLock.Unlock()

	if rTun != nil {
		rTun.close()
		rTun = nil
	}
}
