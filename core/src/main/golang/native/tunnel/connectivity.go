package tunnel

import (
	"context"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

const (
	healthCheckTimeout     = 5 * time.Second
	healthCheckConcurrency = 10
)

// healthCheckURLer is implemented by mihomo proxy providers and exposes the url they test with.
type healthCheckURLer interface {
	HealthCheckURL() string
}

// groupTestURL returns the url the members of the group should be tested with.
//
// It follows the same rules as mihomo's own parser: the provider owned by the group first (a group
// which lists `proxies:` gets one and its url is the group's `url:`, falling back to the core
// default test url), then the first provider with a configured health check url (subscriptions
// referenced by `use:`), and finally the core default test url.
func groupTestURL(g outboundgroup.ProxyGroup) string {
	for _, pr := range g.Providers() {
		if u, ok := pr.(healthCheckURLer); ok {
			if url := u.HealthCheckURL(); url != "" {
				return url
			}
		}
	}

	return C.DefaultTestURL
}

// HealthCheck tests every proxy of the group with groupTestURL(g), so the result can be read back
// afterwards with proxy.LastDelayForTestUrl(groupTestURL(g)).
//
// provider.HealthCheck() is intentionally not used here: it only runs the urls a provider already
// knows about, and it silently does nothing when that url is empty. mihomo builds the reserved
// `default` provider (which backs the auto created GLOBAL group) with an empty health check url
// and never registers a test url on it, so the delay test looked like a no-op for such groups.
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

	url := groupTestURL(g)

	var proxies []C.Proxy
	for _, pr := range g.Providers() {
		proxies = append(proxies, pr.Proxies()...)
	}

	log.Debugln("Health checking group `%s` with url `%s` (%d proxies)", name, url, len(proxies))

	limit := make(chan struct{}, healthCheckConcurrency)
	wg := &sync.WaitGroup{}

	for _, px := range proxies {
		px := px

		limit <- struct{}{}
		wg.Add(1)

		go func() {
			defer wg.Done()
			defer func() { <-limit }()

			ctx, cancel := context.WithTimeout(context.Background(), healthCheckTimeout)
			defer cancel()

			if _, err := px.URLTest(ctx, url, nil); err != nil {
				log.Debugln("Health check `%s` with url `%s`: %s", px.Name(), url, err.Error())
			}
		}()
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
