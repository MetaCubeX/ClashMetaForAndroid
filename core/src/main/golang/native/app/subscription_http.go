package app

import (
	"encoding/json"
	"net/http"
	"sync"
)

var subscriptionFetchHeadersMu sync.Mutex
var subscriptionFetchHeaders http.Header

// SetSubscriptionFetchHeadersJSON parses a JSON object of string->string header names/values
// (e.g. x-hwid, x-device-os) and stores them for the next subscription HTTP fetches from native fetch.
func SetSubscriptionFetchHeadersJSON(jsonStr string) {
	subscriptionFetchHeadersMu.Lock()
	defer subscriptionFetchHeadersMu.Unlock()
	subscriptionFetchHeaders = nil
	if jsonStr == "" {
		return
	}
	var m map[string]string
	if err := json.Unmarshal([]byte(jsonStr), &m); err != nil {
		return
	}
	h := make(http.Header)
	for k, v := range m {
		if k == "" || v == "" {
			continue
		}
		h.Set(k, v)
	}
	if len(h) == 0 {
		return
	}
	subscriptionFetchHeaders = h
}

// ClearSubscriptionFetchHeaders removes injected headers after a fetch completes.
func ClearSubscriptionFetchHeaders() {
	subscriptionFetchHeadersMu.Lock()
	defer subscriptionFetchHeadersMu.Unlock()
	subscriptionFetchHeaders = nil
}

// MergeSubscriptionFetchHeaders returns a clone of base with subscription headers applied (overriding same keys).
func MergeSubscriptionFetchHeaders(base http.Header) http.Header {
	subscriptionFetchHeadersMu.Lock()
	defer subscriptionFetchHeadersMu.Unlock()
	out := base.Clone()
	if subscriptionFetchHeaders == nil {
		return out
	}
	for k, vv := range subscriptionFetchHeaders {
		out.Del(k)
		for _, v := range vv {
			out.Add(k, v)
		}
	}
	return out
}
