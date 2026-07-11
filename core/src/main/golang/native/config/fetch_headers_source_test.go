package config

import (
	"os"
	"strings"
	"testing"
)

func TestProviderFetchDoesNotReceiveSubscriptionHeaders(t *testing.T) {
	source, err := os.ReadFile("fetch.go")
	if err != nil {
		t.Fatal(err)
	}
	text := string(source)
	if !strings.Contains(text, "fetch(t.url, t.path, providerFetchTimeout, false)") {
		t.Fatal("provider fetch must explicitly exclude subscription headers")
	}
	if !strings.Contains(text, "fetch(parsed, configPath, subscriptionFetchTimeout, true)") {
		t.Fatal("subscription fetch must retain its scoped headers")
	}
}
