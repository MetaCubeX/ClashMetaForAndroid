package config

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"path/filepath"
	"strings"
	"testing"
)

func TestFetchSubscriptionAnnouncement(t *testing.T) {
	tests := []struct {
		name     string
		header   string
		userInfo string
		want     string
	}{
		{name: "plain text", header: "Scheduled maintenance", want: "Scheduled maintenance"},
		{name: "base64 UTF-8", header: "base64:0JLQtdGA0YHQuNGPINC/0L7QtNC/0LjRgdC60Lg6IHYxLjEuMQ==", want: "Версия подписки: v1.1.1"},
		{name: "missing"},
		{name: "blank", header: "base64:ICA="},
		{name: "malformed base64", header: "base64:???", userInfo: "upload=7; download=12; total=100; expire=0"},
		{name: "invalid UTF-8", header: "base64:/w=="},
		{name: "too large", header: strings.Repeat("a", 4097)},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if tt.header != "" {
					w.Header().Set("Announce", tt.header)
				}
				if tt.userInfo != "" {
					w.Header().Set("Subscription-Userinfo", tt.userInfo)
				}
				_, _ = io.WriteString(w, "proxies: []\n")
			}))
			defer server.Close()

			source, err := url.Parse(server.URL)
			if err != nil {
				t.Fatal(err)
			}
			header, err := fetch(source, filepath.Join(t.TempDir(), "config.yaml"))
			if err != nil {
				t.Fatal(err)
			}

			var status struct {
				Announce string `json:"subAnnounce"`
				Upload   int64  `json:"subUpload"`
			}
			reportSubscriptionInfo(header, func(payload string) {
				if err := json.Unmarshal([]byte(payload), &status); err != nil {
					t.Fatal(err)
				}
			})
			if status.Announce != tt.want {
				t.Errorf("announcement = %q, want %q", status.Announce, tt.want)
			}
			if tt.userInfo != "" && status.Upload != 7 {
				t.Errorf("upload = %d, want 7", status.Upload)
			}
		})
	}
}
