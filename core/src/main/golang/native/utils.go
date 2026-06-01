package main

import "C"

import (
	"encoding/json"
	"reflect"
	"runtime/debug"

	"github.com/metacubex/mihomo/log"
)

// logRecover reports a recovered panic from a native export with its stack.
func logRecover(name string, r any) {
	log.Errorln("[NATIVE] %s panicked: %v\n%s", name, r, string(debug.Stack()))
}

// guard returns a deferred recover for a synchronous //export function.
// Usage: `defer guard("funcName")()`. On panic it logs and lets the function
// return its zero value (nil for *C.char, 0 for C.int) — so a bridge panic
// never crosses the cgo boundary and aborts the process (BUG-2).
func guard(name string) func() {
	return func() {
		if r := recover(); r != nil {
			logRecover(name, r)
		}
	}
}

func marshalJson(obj any) *C.char {
	res, err := json.Marshal(obj)
	if err != nil {
		panic(err.Error())
	}

	return C.CString(string(res))
}

func marshalString(obj any) *C.char {
	if obj == nil {
		return nil
	}

	switch o := obj.(type) {
	case error:
		return C.CString(o.Error())
	case string:
		return C.CString(o)
	}

	panic("invalid marshal type " + reflect.TypeOf(obj).Name())
}
