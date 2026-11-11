package config

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	U "net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestOpenURLRejectsHTTPError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, "upstream unavailable", http.StatusBadGateway)
	}))
	t.Cleanup(server.Close)

	body, _, err := openUrl(context.Background(), server.URL)
	if body != nil {
		_ = body.Close()
		t.Fatal("expected no response body for an HTTP error")
	}
	if err == nil || !strings.Contains(err.Error(), "502 Bad Gateway") {
		t.Fatalf("expected HTTP status error, got %v", err)
	}
}

func TestFetchConfigurationRejectsInvalidResponseWithoutOverwriting(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = fmt.Fprint(w, "检测到不受支持的客户端")
	}))
	t.Cleanup(server.Close)

	profileDir := t.TempDir()
	configPath := filepath.Join(profileDir, "config.yaml")
	original := []byte("proxies: []\nrules: []\n")
	if err := os.WriteFile(configPath, original, 0600); err != nil {
		t.Fatalf("write existing configuration: %v", err)
	}

	url, err := U.Parse(server.URL)
	if err != nil {
		t.Fatalf("parse server URL: %v", err)
	}
	if _, err := fetchConfiguration(url, configPath); err == nil || !strings.Contains(err.Error(), "invalid configuration response") {
		t.Fatalf("expected invalid configuration error, got %v", err)
	}

	current, err := os.ReadFile(configPath)
	if err != nil {
		t.Fatalf("read existing configuration: %v", err)
	}
	if string(current) != string(original) {
		t.Fatal("invalid response overwrote the existing configuration")
	}
}

func TestFetchConfigurationWritesValidResponse(t *testing.T) {
	configuration := []byte("proxies: []\nrules: []\n")
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write(configuration)
	}))
	t.Cleanup(server.Close)

	url, err := U.Parse(server.URL)
	if err != nil {
		t.Fatalf("parse server URL: %v", err)
	}
	configPath := filepath.Join(t.TempDir(), "config.yaml")
	if _, err := fetchConfiguration(url, configPath); err != nil {
		t.Fatalf("fetch valid configuration: %v", err)
	}

	written, err := os.ReadFile(configPath)
	if err != nil {
		t.Fatalf("read fetched configuration: %v", err)
	}
	if string(written) != string(configuration) {
		t.Fatal("fetched configuration does not match the response")
	}
}
