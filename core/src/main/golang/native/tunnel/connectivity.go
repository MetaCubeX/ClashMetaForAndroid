package tunnel

import (
	"context"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

// defaultHealthCheckURL is mihomo's stock generate_204 endpoint. Used as a
// fallback when a provider has no configured health-check URL (typical for
// compatible providers built from inline `proxies:` lists).
const defaultHealthCheckURL = "http://www.gstatic.com/generate_204"

// perProxyHealthCheckTimeout caps each individual proxy URLTest. Provider's
// own health-check pipeline uses a similar 5s default — we follow it to
// keep ping totals comparable between the batch and per-proxy paths.
const perProxyHealthCheckTimeout = 5 * time.Second

func HealthCheck(name string) {
	p := tunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Request health check for `%s`: not found", name)

		return
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Request health check for `%s`: invalid type %s", name, p.Type().String())

		return
	}

	wg := &sync.WaitGroup{}

	for _, pr := range g.Providers() {
		wg.Add(1)

		go func(provider provider.ProxyProvider) {
			provider.HealthCheck()

			wg.Done()
		}(pr)
	}

	wg.Wait()
}

func HealthCheckAll() {
	for _, g := range QueryProxyGroupNames(false) {
		go func(group string) {
			HealthCheck(group)
		}(g)
	}
}

// HealthCheckWithCallback runs URLTest against every proxy of every provider
// in the named group in parallel and pushes each result to onDelay the moment
// that proxy's test resolves. Mirrors what provider.HealthCheck does
// internally (URLTest with the provider's own health-check URL, no filter)
// but reports per proxy instead of a single batch return — UI can patch a
// row as soon as it has data instead of polling queryProxyGroup on a timer.
//
// Returns ("", nil) when every proxy reported; ("group not found" / "invalid
// type", nil) on the obvious early-outs. onDelay is called from a goroutine,
// so the caller is responsible for any cross-goroutine synchronisation.
//
// errMsg is empty on success, otherwise carries the proxy's URLTest error
// reason; delayMs is meaningful only when errMsg == "".
func HealthCheckWithCallback(
	name string,
	onDelay func(proxyName string, delayMs int, errMsg string),
) string {
	p := tunnel.Proxies()[name]
	if p == nil {
		return "group not found"
	}
	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		return "invalid group type"
	}

	var wg sync.WaitGroup
	for _, prov := range g.Providers() {
		url := prov.HealthCheckURL()
		if url == "" {
			url = defaultHealthCheckURL
		}
		for _, target := range prov.Proxies() {
			target := target
			wg.Add(1)
			go func() {
				defer wg.Done()
				ctx, cancel := context.WithTimeout(context.Background(), perProxyHealthCheckTimeout)
				defer cancel()
				delay, err := target.URLTest(ctx, url, nil)
				if err != nil {
					onDelay(target.Name(), 0, err.Error())
					return
				}
				onDelay(target.Name(), int(delay), "")
			}()
		}
	}
	wg.Wait()
	return ""
}
