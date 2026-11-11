package config

import (
	"net/netip"
	"sync"
)

// Only the successfully applied configuration is visible to Android.
var routeExclusions struct {
	sync.RWMutex
	prefixes []string
}

func publishRouteExclusions(prefixes []netip.Prefix) {
	values := make([]string, len(prefixes))
	for i, prefix := range prefixes {
		values[i] = prefix.Masked().String()
	}
	routeExclusions.Lock()
	routeExclusions.prefixes = values
	routeExclusions.Unlock()
}

func QueryRouteExclusions() []string {
	routeExclusions.RLock()
	defer routeExclusions.RUnlock()
	// Return [] rather than null, and never expose the shared backing array.
	return append([]string{}, routeExclusions.prefixes...)
}
