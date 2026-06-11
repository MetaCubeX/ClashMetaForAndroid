package main

//#include "bridge.h"
import "C"

import (
	"github.com/metacubex/mihomo/component/updater"
)

// updateGeoDatabases downloads fresh GeoIP/GeoSite databases from the configured
// geox-url into the data dir (mihomo's own UpdateGeoDatabases). Returns an empty
// string on success, or the error message on failure.
//
//export updateGeoDatabases
func updateGeoDatabases() *C.char {
	defer guard("updateGeoDatabases")()
	if err := updater.UpdateGeoDatabases(); err != nil {
		return marshalString(err.Error())
	}
	return marshalString("")
}
