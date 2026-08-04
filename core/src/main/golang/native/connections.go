package main

//#include "bridge.h"
import "C"

import (
	"encoding/json"

	"github.com/metacubex/mihomo/tunnel/statistic"
)

//export queryConnections
func queryConnections() *C.char {
	content, err := json.Marshal(statistic.DefaultManager.Snapshot())
	if err != nil {
		return C.CString(`{"connections":[],"uploadTotal":0,"downloadTotal":0}`)
	}

	return C.CString(string(content))
}

//export closeConnectionById
func closeConnectionById(id C.c_string) C.int {
	connection := statistic.DefaultManager.Get(C.GoString(id))
	if connection == nil {
		return 0
	}

	if err := connection.Close(); err != nil {
		return 0
	}

	return 1
}

//export closeAllTrackedConnections
func closeAllTrackedConnections() {
	statistic.DefaultManager.Range(func(connection statistic.Tracker) bool {
		_ = connection.Close()
		return true
	})
}
