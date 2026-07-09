package config

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	U "net/url"
	"os"
	P "path"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"cfa/native/app"

	clashHttp "github.com/metacubex/mihomo/component/http"
	"github.com/metacubex/mihomo/config"
)

type Status struct {
	Action      string   `json:"action"`
	Args        []string `json:"args"`
	Progress    int      `json:"progress"`
	MaxProgress int      `json:"max"`
}

type providerFetchTask struct {
	name string
	url  *U.URL
	path string
}

func openUrl(ctx context.Context, url string) (io.ReadCloser, map[string][]string, error) {
	base := http.Header{"User-Agent": {"ClashMetaForAndroid/" + app.VersionName()}}
	hdr := app.MergeSubscriptionFetchHeaders(base)
	response, err := clashHttp.HttpRequest(ctx, url, http.MethodGet, hdr, nil)

	if err != nil {
		return nil, nil, err
	}

	// Plain map type: mihomo's forked metacubex/http.Header and net/http.Header
	// are distinct named types over the same underlying map.
	return response.Body, response.Header, nil
}

func openContent(url string) (io.ReadCloser, error) {
	return app.OpenContent(url)
}

const (
	// The main subscription is the single critical download — give it generous
	// room (matches upstream ClashMetaForAndroid) so a slow CDN / DPI-laggy link
	// doesn't fail the whole update.
	subscriptionFetchTimeout = 60 * time.Second
	// Each rule-/proxy-provider runs on a short budget so one stuck provider can't
	// hold up the parallel import (see [fetchProviders] / [maxProviderConcurrency]).
	providerFetchTimeout = 20 * time.Second
)

// Downloads a single URL to a file with a caller-supplied timeout. The timeout
// is decoupled per use case (see [subscriptionFetchTimeout] / [providerFetchTimeout]):
// the main subscription is one critical fetch and gets generous room for slow
// CDNs / DPI, while each rule-/proxy-provider runs on a short budget so a stuck
// provider can't hold up the (parallel, see [fetchProviders]) import.
//
// For http(s) URLs the response headers are returned too (nil for content://
// and on error) — the subscription fetch persists them via [writeFetchHeaders]
// so the Kotlin side can read subscription-userinfo / X-Brand-* / naming
// headers without issuing a second GET of its own.
func fetch(url *U.URL, file string, timeout time.Duration) (map[string][]string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	var reader io.ReadCloser
	var header map[string][]string
	var err error

	switch url.Scheme {
	case "http", "https":
		reader, header, err = openUrl(ctx, url.String())
	case "content":
		reader, err = openContent(url.String())
	default:
		err = fmt.Errorf("unsupported scheme %s of %s", url.Scheme, url)
	}

	if err != nil {
		return nil, err
	}

	defer reader.Close()

	_ = os.MkdirAll(P.Dir(file), 0700)

	f, err := os.OpenFile(file, os.O_WRONLY|os.O_TRUNC|os.O_CREATE, 0600)
	if err != nil {
		return nil, err
	}

	defer f.Close()

	_, err = io.Copy(f, reader)
	if err != nil {
		_ = os.Remove(file)
		return nil, err
	}

	return header, nil
}

// FetchHeadersFileName is the per-profile snapshot of the subscription
// response headers, written next to config.yaml on every successful
// subscription download. One flat JSON object, keys lowercased, multi-value
// headers joined the HTTP way (", "). The Kotlin side feeds it to the same
// header parsers (usage / brand / display-name) that used to run against a
// SECOND OkHttp GET of the subscription — that extra request doubled traffic
// (panels count fetches against quota), raced the primary download, and kept
// a whole parallel header path alive (the mojibake class of bugs).
const FetchHeadersFileName = "fetch-headers.json"

func writeFetchHeaders(profilePath string, header map[string][]string) {
	file := P.Join(profilePath, FetchHeadersFileName)

	if header == nil {
		// content:// / inline imports have no HTTP response; drop any stale
		// snapshot from a previous source so consumers don't read old quota.
		_ = os.Remove(file)
		return
	}

	flat := make(map[string]string, len(header))
	for key, values := range header {
		if len(values) == 0 {
			continue
		}
		flat[strings.ToLower(key)] = strings.Join(values, ", ")
	}

	bytes, err := json.Marshal(flat)
	if err != nil {
		return
	}

	// Best-effort: header persistence must never fail the import itself.
	_ = os.WriteFile(file, bytes, 0600)
}

func FetchAndValid(
	path string,
	url string,
	force bool,
	reportStatus func(string),
) error {
	configPath := P.Join(path, "config.yaml")

	trimmed := strings.TrimSpace(url)
	if isInlineMierusImport(trimmed) {
		bytes, _ := json.Marshal(&Status{
			Action:      "FetchConfiguration",
			Args:        []string{"mierus"},
			Progress:    -1,
			MaxProgress: -1,
		})

		reportStatus(string(bytes))

		if err := writeConfigFromMierusShare(configPath, trimmed); err != nil {
			return err
		}

		writeFetchHeaders(path, nil)
	} else if _, err := os.Stat(configPath); os.IsNotExist(err) || force {
		parsed, err := U.Parse(url)
		if err != nil {
			return err
		}

		bytes, _ := json.Marshal(&Status{
			Action:      "FetchConfiguration",
			Args:        []string{parsed.Host},
			Progress:    -1,
			MaxProgress: -1,
		})

		reportStatus(string(bytes))

		header, err := fetch(parsed, configPath, subscriptionFetchTimeout)
		if err != nil {
			return err
		}

		writeFetchHeaders(path, header)
	}

	defer runtime.GC()

	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		return err
	}

	// Provider files re-fetch is driven separately via FetchProvidersAndValid;
	// here we only pick up providers that have no cached file on disk yet,
	// regardless of the config-level force flag.
	if err := fetchProviders(rawCfg, false, reportStatus); err != nil {
		return err
	}

	bytes, _ := json.Marshal(&Status{
		Action:      "Verifying",
		Args:        []string{},
		Progress:    0xffff,
		MaxProgress: 0xffff,
	})

	reportStatus(string(bytes))

	cfg, err := Parse(rawCfg)
	if err != nil {
		return err
	}

	destroyProviders(cfg)

	return nil
}

func FetchProvidersAndValid(
	path string,
	force bool,
	reportStatus func(string),
) error {
	defer runtime.GC()

	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		return err
	}

	if err := fetchProviders(rawCfg, force, reportStatus); err != nil {
		return err
	}

	bytes, _ := json.Marshal(&Status{
		Action:      "Verifying",
		Args:        []string{},
		Progress:    0xffff,
		MaxProgress: 0xffff,
	})

	reportStatus(string(bytes))

	cfg, err := Parse(rawCfg)
	if err != nil {
		return err
	}

	destroyProviders(cfg)

	return nil
}

// maxProviderConcurrency caps how many rule/proxy-providers can be downloaded
// at once. Higher values reduce wall-clock time for big subscriptions on fast
// links, but on mobile / EDGE / Cloudflare-fronted CDNs each parallel stream
// adds its own DNS lookup + TLS handshake; opening 20 of them simultaneously
// can starve the link and trigger DNS timeouts (UnknownHostException-style
// failures). 6 is a good compromise — fast enough for a 10-provider import
// to finish in ~3 seconds on Wi-Fi, low enough not to overwhelm mobile DNS.
const maxProviderConcurrency = 6

func fetchProviders(rawCfg *config.RawConfig, force bool, reportStatus func(string)) error {
	providerFetchTasks := make([]providerFetchTask, 0)
	forEachProviders(rawCfg, func(index int, total int, name string, provider map[string]any, prefix string) {
		u, uok := provider["url"]
		p, pok := provider["path"]

		if !uok || !pok {
			return
		}

		us, uok := u.(string)
		ps, pok := p.(string)

		if !uok || !pok {
			return
		}

		// A provider that declares `proxy:` is meant to be downloaded THROUGH that
		// proxy — mihomo honors it via WithSpecialProxy when it loads the provider.
		// Our import-time fetch here is a plain DIRECT GET with no proxy, so for such
		// a provider it cannot succeed on a host the proxy exists to reach (e.g. a
		// geo/DPI-blocked raw.githubusercontent.com): it just burns the full
		// providerFetchTimeout and the swallowed error leaves no file on disk anyway.
		// Skip it — the engine fetches it correctly through the proxy on activation
		// (Fetcher.Initial → Update with the proxy) and keeps it fresh on its
		// background pull loop thereafter.
		if px, ok := provider["proxy"].(string); ok && strings.TrimSpace(px) != "" {
			return
		}

		if !force {
			if _, err := os.Stat(ps); err == nil {
				return
			}
		}

		url, err := U.Parse(us)
		if err != nil {
			return
		}

		providerFetchTasks = append(providerFetchTasks, providerFetchTask{
			name: name,
			url:  url,
			path: ps,
		})
	})

	total := len(providerFetchTasks)
	if total == 0 {
		return nil
	}

	// Parallel fetch with a bounded semaphore. Previous implementation was
	// sequential — one slow / stuck provider could stall the entire import
	// for tens of seconds even though all other providers were ready in <1s.
	// We still report Action=FetchProviders status messages, but [Progress]
	// now reflects completed count (an atomic counter incremented after each
	// fetch resolves), not the launch index, so the UI doesn't run ahead of
	// the actual work.
	sem := make(chan struct{}, maxProviderConcurrency)
	var wg sync.WaitGroup
	var done int32

	for _, task := range providerFetchTasks {
		wg.Add(1)
		sem <- struct{}{}
		go func(t providerFetchTask) {
			defer wg.Done()
			defer func() { <-sem }()

			// Per-task errors are intentionally swallowed (mirrors the prior
			// behaviour): a missing rule-provider should not abort the whole
			// import — the user can still activate the profile and the
			// provider re-fetches on the next FetchProvidersAndValid pass.
			// Provider response headers are irrelevant (only the subscription
			// fetch persists them, see writeFetchHeaders).
			_, _ = fetch(t.url, t.path, providerFetchTimeout)

			current := atomic.AddInt32(&done, 1)
			bytes, _ := json.Marshal(&Status{
				Action:      "FetchProviders",
				Args:        []string{t.name},
				Progress:    int(current),
				MaxProgress: total,
			})
			reportStatus(string(bytes))
		}(task)
	}
	wg.Wait()

	return nil
}
