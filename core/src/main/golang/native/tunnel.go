package main

//#include "bridge.h"
import "C"

import (
	"unsafe"

	"cfa/native/app"
	"cfa/native/tunnel"
)

//export queryTunnelState
func queryTunnelState() *C.char {
	mode := tunnel.QueryMode()

	response := &struct {
		Mode string `json:"mode"`
	}{mode}

	return marshalJson(response)
}

//export queryNow
func queryNow(upload, download *C.uint64_t) {
	up, down := tunnel.Now()

	*upload = C.uint64_t(up)
	*download = C.uint64_t(down)
}

//export queryTotal
func queryTotal(upload, download *C.uint64_t) {
	up, down := tunnel.Total()

	*upload = C.uint64_t(up)
	*download = C.uint64_t(down)
}

//export queryGroupNames
func queryGroupNames(excludeNotSelectable C.int) *C.char {
	return marshalJson(tunnel.QueryProxyGroupNames(excludeNotSelectable != 0))
}

//export queryAllGroupNamesIncludingHidden
func queryAllGroupNamesIncludingHidden() *C.char {
	return marshalJson(tunnel.QueryAllProxyGroupNamesIncludingHidden())
}

//export queryGroup
func queryGroup(name C.c_string, sortMode C.c_string) *C.char {
	n := C.GoString(name)
	s := C.GoString(sortMode)

	mode := tunnel.Default

	switch s {
	case "Title":
		mode = tunnel.Title
	case "Delay":
		mode = tunnel.Delay
	}

	response := tunnel.QueryProxyGroup(n, mode, app.SubtitlePattern())

	if response == nil {
		return nil
	}

	return marshalJson(response)
}

//export healthCheck
func healthCheck(completable unsafe.Pointer, name C.c_string) {
	go func(name string) {
		tunnel.HealthCheck(name)

		C.complete(completable, nil)
	}(C.GoString(name))
}

//export healthCheckWithCallback
func healthCheckWithCallback(callback unsafe.Pointer, name C.c_string) {
	go func(name string, callback unsafe.Pointer) {
		earlyErr := tunnel.HealthCheckWithCallback(name, func(proxyName string, delayMs int, errMsg string) {
			var errCStr *C.char
			if errMsg != "" {
				errCStr = marshalString(errMsg)
			}
			C.proxy_delay_report(callback, marshalString(proxyName), C.int(delayMs), errCStr)
		})

		var earlyErrCStr *C.char
		if earlyErr != "" {
			earlyErrCStr = marshalString(earlyErr)
		}
		C.proxy_delay_complete(callback, earlyErrCStr)

		C.release_object(callback)
	}(C.GoString(name), callback)
}

//export healthCheckAll
func healthCheckAll() {
	tunnel.HealthCheckAll()
}

//export patchSelector
func patchSelector(selector, name C.c_string) C.int {
	s := C.GoString(selector)
	n := C.GoString(name)

	if tunnel.PatchSelector(s, n) {
		return 1
	}

	return 0
}

//export queryProviders
func queryProviders() *C.char {
	return marshalJson(tunnel.QueryProviders())
}

//export queryConnectionsSnapshot
func queryConnectionsSnapshot() *C.char {
	return marshalJson(tunnel.QueryConnectionsSnapshot())
}

//export closeConnection
func closeConnection(id C.c_string) C.int {
	if tunnel.CloseConnection(C.GoString(id)) {
		return 1
	}
	return 0
}

//export closeAllConnections
func closeAllConnections() C.int {
	return C.int(tunnel.CloseAllConnections())
}

//export updateProvider
func updateProvider(completable unsafe.Pointer, pType C.c_string, name C.c_string) {
	go func(pType, name string) {
		C.complete(completable, marshalString(tunnel.UpdateProvider(pType, name)))

		C.release_object(completable)
	}(C.GoString(pType), C.GoString(name))
}

//export suspend
func suspend(suspended C.int) {
	tunnel.Suspend(suspended != 0)
}
