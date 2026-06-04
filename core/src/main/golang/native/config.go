package main

//#include "bridge.h"
import "C"

import (
	"runtime"
	"unsafe"

	"cfa/native/config"
)

type remoteValidCallback struct {
	callback unsafe.Pointer
}

func (r *remoteValidCallback) reportStatus(json string) {
	C.fetch_report(r.callback, marshalString(json))
}

//export fetchAndValid
func fetchAndValid(callback unsafe.Pointer, path, url C.c_string, force C.int) {
	go func(path, url string, callback unsafe.Pointer) {
		cb := &remoteValidCallback{callback: callback}

		err := config.FetchAndValid(path, url, force != 0, cb.reportStatus)

		C.fetch_complete(callback, marshalString(err))

		C.release_object(callback)

		runtime.GC()
	}(C.GoString(path), C.GoString(url), callback)
}

//export load
func load(completable unsafe.Pointer, path C.c_string) {
	go func(path string) {
		C.complete(completable, marshalString(config.Load(path)))

		C.release_object(completable)

		runtime.GC()
	}(C.GoString(path))
}

//export readOverride
func readOverride(slot C.int) *C.char {
	return C.CString(config.ReadOverride(config.OverrideSlot(slot)))
}

//export writeOverride
func writeOverride(slot C.int, content C.c_string) {
	c := C.GoString(content)

	config.WriteOverride(config.OverrideSlot(slot), c)
}

//export clearOverride
func clearOverride(slot C.int) {
	config.ClearOverride(config.OverrideSlot(slot))
}

//export setAgeSecretKey
func setAgeSecretKey(key C.c_string) {
	if key == nil {
		config.SetGlobalSecretKeys()
		return
	}

	k := C.GoString(key)
	config.SetGlobalSecretKeys(k)
}
