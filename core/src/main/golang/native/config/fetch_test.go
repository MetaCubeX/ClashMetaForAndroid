package config

import (
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/metacubex/mihomo/constant"
)

const acceptableConfig = "" +
	"proxies:\n" +
	"  - name: socks\n" +
	"    type: socks5\n" +
	"    server: 127.0.0.1\n" +
	"    port: 1080\n"

func useTempHome(t *testing.T) {
	t.Helper()
	constant.SetHomeDir(t.TempDir())
}

func writeTreeFile(t *testing.T, root, name, body string) {
	t.Helper()
	path := filepath.Join(root, filepath.FromSlash(name))
	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(body), 0600); err != nil {
		t.Fatal(err)
	}
}

func snapshotTree(t *testing.T, root string) map[string]string {
	t.Helper()
	out := map[string]string{}
	err := filepath.WalkDir(root, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			return nil
		}
		rel, err := filepath.Rel(root, path)
		if err != nil {
			return err
		}
		body, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		out[rel] = string(body)
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	return out
}

func mustSameTree(t *testing.T, before, after map[string]string) {
	t.Helper()
	if len(before) != len(after) {
		t.Fatalf("directory files changed\nbefore: %#v\nafter: %#v", before, after)
	}
	for name, body := range before {
		if after[name] != body {
			t.Fatalf("file %s changed\nbefore: %q\nafter: %q", name, body, after[name])
		}
	}
}

func assertNoSubscription(t *testing.T, reports []string) {
	t.Helper()
	for _, report := range reports {
		if strings.Contains(report, "SubscriptionInfo") {
			t.Fatalf("subscription reported before commit: %s", report)
		}
	}
}

func hijackClose(w http.ResponseWriter) {
	hj, ok := w.(http.Hijacker)
	if !ok {
		http.Error(w, "hijack unsupported", http.StatusInternalServerError)
		return
	}
	conn, _, err := hj.Hijack()
	if err != nil {
		return
	}
	_ = conn.Close()
}

func TestFetchAndValidStagesOutsideSystemTemp(t *testing.T) {
	useTempHome(t)
	dir := t.TempDir()
	writeTreeFile(t, dir, "config.yaml", acceptableConfig)
	t.Setenv("TMPDIR", filepath.Join(t.TempDir(), "unwritable"))

	if err := FetchAndValid(dir, "http://127.0.0.1/unused", false, func(string) {}); err != nil {
		t.Fatal(err)
	}
	entries, err := os.ReadDir(filepath.Dir(dir))
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range entries {
		if strings.HasPrefix(entry.Name(), ".cmfa-profile-") {
			t.Fatalf("staging directory left behind: %s", entry.Name())
		}
	}
}

func TestFetchAndValidRejectsInvalidBodyWithoutReplacingConfig(t *testing.T) {
	useTempHome(t)
	dir := t.TempDir()
	writeTreeFile(t, dir, "config.yaml", acceptableConfig)
	writeTreeFile(t, dir, "note.txt", "keep\n")

	payload := ":\ninvalid"
	var hits int
	var reports []string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits++
		w.Header().Set("subscription-userinfo", "upload=1; download=2; total=3; expire=10")
		_, _ = io.WriteString(w, payload)
	}))
	t.Cleanup(srv.Close)

	before := snapshotTree(t, dir)
	err := FetchAndValid(dir, srv.URL+"/profile", true, func(status string) {
		reports = append(reports, status)
	})
	if err == nil {
		t.Fatal("expected error for invalid profile")
	}
	if hits != 1 {
		t.Fatalf("profile requests = %d, want 1", hits)
	}
	mustSameTree(t, before, snapshotTree(t, dir))
	assertNoSubscription(t, reports)

	payload = "also-invalid"
	err = FetchAndValid(dir, srv.URL+"/profile", false, func(status string) {
		reports = append(reports, status)
	})
	if hits != 1 {
		t.Fatalf("force=false downloaded again, requests = %d, err = %v", hits, err)
	}
	mustSameTree(t, before, snapshotTree(t, dir))
	assertNoSubscription(t, reports)
}

func TestFetchAndValidLeavesNoConfigWhenMissingAndInvalid(t *testing.T) {
	useTempHome(t)
	dir := t.TempDir()

	var hits int
	var reports []string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits++
		w.Header().Set("subscription-userinfo", "upload=1; download=2; total=3; expire=10")
		_, _ = io.WriteString(w, ":\ninvalid")
	}))
	t.Cleanup(srv.Close)

	err := FetchAndValid(dir, srv.URL+"/profile", true, func(status string) {
		reports = append(reports, status)
	})
	if err == nil {
		t.Fatal("expected error for invalid profile")
	}
	if hits != 1 {
		t.Fatalf("profile requests = %d, want 1", hits)
	}
	if files := snapshotTree(t, dir); len(files) != 0 {
		t.Fatalf("official directory has files: %#v", files)
	}
	if _, statErr := os.Stat(filepath.Join(dir, "config.yaml")); !os.IsNotExist(statErr) {
		t.Fatalf("config.yaml stat = %v", statErr)
	}
	assertNoSubscription(t, reports)

	err = FetchAndValid(dir, srv.URL+"/profile", false, func(status string) {
		reports = append(reports, status)
	})
	if err == nil {
		t.Fatal("expected error for invalid profile")
	}
	if hits != 2 {
		t.Fatalf("force=false requests = %d, want 2", hits)
	}
	if files := snapshotTree(t, dir); len(files) != 0 {
		t.Fatalf("official directory has files after retry: %#v", files)
	}
	assertNoSubscription(t, reports)
}

func TestFetchAndValidRollsBackWhenProviderFails(t *testing.T) {
	useTempHome(t)
	dir := t.TempDir()
	writeTreeFile(t, dir, "config.yaml", "original-profile\n")
	writeTreeFile(t, dir, "providers/alpha.yaml", "old-alpha\n")
	writeTreeFile(t, dir, "note.txt", "note\n")

	var betaHits int
	var reports []string
	var srv *httptest.Server
	srv = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/alpha":
			_, _ = io.WriteString(w, "new-alpha\n")
		case "/beta":
			betaHits++
			hijackClose(w)
		default:
			w.Header().Set("subscription-userinfo", "upload=9; download=8; total=7; expire=6")
			_, _ = fmt.Fprintf(w, "proxy-providers:\n  alpha:\n    type: http\n    url: %q\n    path: alpha.yaml\n  beta:\n    type: http\n    url: %q\n    path: beta.yaml\n", srv.URL+"/alpha", srv.URL+"/beta")
		}
	}))
	t.Cleanup(srv.Close)

	before := snapshotTree(t, dir)
	err := FetchAndValid(dir, srv.URL+"/profile", true, func(status string) {
		reports = append(reports, status)
	})
	if err == nil {
		t.Fatal("expected provider failure")
	}
	if betaHits == 0 {
		t.Fatalf("provider stage was not reached: %v", err)
	}
	mustSameTree(t, before, snapshotTree(t, dir))
	if _, statErr := os.Stat(filepath.Join(dir, "providers", "beta.yaml")); !os.IsNotExist(statErr) {
		t.Fatalf("failed provider left on official path: %v", statErr)
	}
	assertNoSubscription(t, reports)
}

func TestFetchAndValidSkipsExistingFiles(t *testing.T) {
	useTempHome(t)
	dir := t.TempDir()

	var profileHits, keepHits int
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/profile":
			profileHits++
		case "/keep":
			keepHits++
		}
		hijackClose(w)
	}))
	t.Cleanup(srv.Close)

	profile := fmt.Sprintf("proxy-providers:\n  keep:\n    type: http\n    url: %q\n    path: keep.yaml\n", srv.URL+"/keep")
	writeTreeFile(t, dir, "config.yaml", profile)
	writeTreeFile(t, dir, "providers/keep.yaml", "stored\n")
	before := snapshotTree(t, dir)

	err := FetchAndValid(dir, srv.URL+"/profile", false, func(string) {})
	if profileHits != 0 || keepHits != 0 {
		t.Fatalf("downloaded existing files, profile=%d keep=%d err=%v", profileHits, keepHits, err)
	}
	mustSameTree(t, before, snapshotTree(t, dir))

	if err := os.Remove(filepath.Join(dir, "providers", "keep.yaml")); err != nil {
		t.Fatal(err)
	}
	err = FetchAndValid(dir, srv.URL+"/profile", false, func(string) {})
	if profileHits != 0 {
		t.Fatalf("force=false downloaded existing config.yaml, hits=%d", profileHits)
	}
	if keepHits == 0 {
		t.Fatalf("missing provider was not downloaded: %v", err)
	}
	if err == nil {
		t.Fatal("expected provider download to fail")
	}
	if _, statErr := os.Stat(filepath.Join(dir, "providers", "keep.yaml")); !os.IsNotExist(statErr) {
		t.Fatalf("failed provider left on disk: %v", statErr)
	}
	got, readErr := os.ReadFile(filepath.Join(dir, "config.yaml"))
	if readErr != nil {
		t.Fatal(readErr)
	}
	if string(got) != profile {
		t.Fatalf("config.yaml = %q", got)
	}
}

func TestFetchAndValidReportsSubscriptionAfterCommit(t *testing.T) {
	useTempHome(t)
	dir := t.TempDir()

	const providerBody = "" +
		"proxies:\n" +
		"  - name: p1\n" +
		"    type: socks5\n" +
		"    server: 127.0.0.1\n" +
		"    port: 1080\n"

	var profileHits, providerHits int
	var profile string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/p.yaml":
			providerHits++
			_, _ = io.WriteString(w, providerBody)
		default:
			profileHits++
			w.Header().Set("subscription-userinfo", "upload=1; download=2; total=3; expire=10")
			_, _ = io.WriteString(w, profile)
		}
	}))
	t.Cleanup(srv.Close)
	profile = fmt.Sprintf("proxies:\n  - name: socks\n    type: socks5\n    server: 127.0.0.1\n    port: 1080\nproxy-providers:\n  p:\n    type: http\n    url: %q\n    path: p.yaml\n", srv.URL+"/p.yaml")

	var saw bool
	var late string
	err := FetchAndValid(dir, srv.URL+"/profile", true, func(status string) {
		if !strings.Contains(status, "SubscriptionInfo") {
			return
		}
		saw = true
		got, readErr := os.ReadFile(filepath.Join(dir, "config.yaml"))
		if readErr != nil {
			late = readErr.Error()
			return
		}
		if string(got) != profile {
			late = "subscription reported before config.yaml matched the download"
			return
		}
		got, readErr = os.ReadFile(filepath.Join(dir, "providers", "p.yaml"))
		if readErr != nil {
			late = "subscription reported before provider commit: " + readErr.Error()
			return
		}
		if string(got) != providerBody {
			late = "provider bytes were not committed"
		}
	})
	if late != "" {
		t.Fatal(late)
	}
	if err != nil {
		t.Fatal(err)
	}
	if !saw {
		t.Fatal("subscription was not reported")
	}
	if profileHits != 1 || providerHits != 1 {
		t.Fatalf("hits profile=%d provider=%d", profileHits, providerHits)
	}

	err = FetchAndValid(dir, srv.URL+"/profile", false, func(string) {})
	if err != nil {
		t.Fatal(err)
	}
	if profileHits != 1 || providerHits != 1 {
		t.Fatalf("force=false downloaded again, profile=%d provider=%d", profileHits, providerHits)
	}
	got, readErr := os.ReadFile(filepath.Join(dir, "config.yaml"))
	if readErr != nil {
		t.Fatal(readErr)
	}
	if string(got) != profile {
		t.Fatalf("config.yaml changed: %q", got)
	}
}
