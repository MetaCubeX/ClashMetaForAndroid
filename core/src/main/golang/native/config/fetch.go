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

func openUrl(ctx context.Context, url string) (io.ReadCloser, error) {
	base := http.Header{"User-Agent": {"ClashMetaForAndroid/" + app.VersionName()}}
	hdr := app.MergeSubscriptionFetchHeaders(base)
	response, err := clashHttp.HttpRequest(ctx, url, http.MethodGet, hdr, nil)

	if err != nil {
		return nil, err
	}

	return response.Body, nil
}

func openContent(url string) (io.ReadCloser, error) {
	return app.OpenContent(url)
}

// 20s per single fetch keeps a stuck rule-provider from holding up the whole
// import. With 60s and sequential fetches, one slow CDN edge could turn a
// 10-provider import into a 10-minute hang. With parallel fetches (see
// [fetchProviders]) the slowest individual stream is also the worst-case
// total — 20s gives enough room for honest slow links without making the user
// wait.
func fetch(url *U.URL, file string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	var reader io.ReadCloser
	var err error

	switch url.Scheme {
	case "http", "https":
		reader, err = openUrl(ctx, url.String())
	case "content":
		reader, err = openContent(url.String())
	default:
		err = fmt.Errorf("unsupported scheme %s of %s", url.Scheme, url)
	}

	if err != nil {
		return err
	}

	defer reader.Close()

	_ = os.MkdirAll(P.Dir(file), 0700)

	f, err := os.OpenFile(file, os.O_WRONLY|os.O_TRUNC|os.O_CREATE, 0600)
	if err != nil {
		return err
	}

	defer f.Close()

	_, err = io.Copy(f, reader)
	if err != nil {
		_ = os.Remove(file)
	}

	return err
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

		if err := fetch(parsed, configPath); err != nil {
			return err
		}
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
			_ = fetch(t.url, t.path)

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
