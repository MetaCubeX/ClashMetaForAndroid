package main

/*
#cgo LDFLAGS: -llog

#include "bridge.h"
*/
import "C"

import (
	"runtime"
	"runtime/debug"

	"cfa/native/config"
	"cfa/native/delegate"
	"cfa/native/tunnel"

	"github.com/metacubex/mihomo/log"
)

func main() {
	panic("Stub!")
}

//export coreInit
func coreInit(home, versionName, gitVersion C.c_string, sdkVersion C.int) {
	defer guard("coreInit")()
	h := C.GoString(home)
	v := C.GoString(versionName)
	g := C.GoString(gitVersion)
	s := int(sdkVersion)

	delegate.Init(h, v, g, s)

	reset()
}

//export reset
func reset() {
	defer guard("reset")()
	// Abort in-flight health checks on the outgoing config first: their url-test dials to dead nodes
	// otherwise keep the tunnel (and, on teardown, the Android VpnService / system VPN key) alive for
	// their full timeout — 10-30s after the tunnel is already logically down. See CancelHealthChecks.
	tunnel.CancelHealthChecks()
	config.LoadDefault()
	tunnel.ResetStatistic()
	tunnel.CloseAllConnections()

	runtime.GC()
	debug.FreeOSMemory()
}

//export forceGc
func forceGc() {
	go func() {
		defer guard("forceGc")()

		log.Infoln("[APP] request force GC")

		runtime.GC()
		debug.FreeOSMemory()
	}()
}
