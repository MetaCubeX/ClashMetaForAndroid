package fetchheaders

import (
	"reflect"
	"testing"
)

func TestFilterPersistsOnlyConsumerAllowlist(t *testing.T) {
	got := Filter(map[string][]string{
		"Subscription-Userinfo": {"upload=1; download=2; total=3"},
		"X-Brand-Name":          {"Example VPN"},
		"Profile-Title":         {"Premium"},
		"Content-Disposition":   {"attachment; filename=example.yaml"},
		"Set-Cookie":            {"session=secret"},
		"Authorization":         {"Bearer secret"},
		"X-Backend-Trace":       {"account-123"},
	})

	want := map[string]string{
		"subscription-userinfo": "upload=1; download=2; total=3",
		"x-brand-name":          "Example VPN",
		"profile-title":         "Premium",
		"content-disposition":   "attachment; filename=example.yaml",
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("unexpected persisted headers: %#v", got)
	}
	if _, exists := got["set-cookie"]; exists {
		t.Fatal("Set-Cookie must never be persisted")
	}
}
