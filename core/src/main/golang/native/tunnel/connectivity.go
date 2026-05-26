package tunnel

import (
	"context"
	"reflect"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
	"golang.org/x/sync/errgroup"
)

// defaultHealthCheckURL is mihomo's stock generate_204 endpoint. Used as a
// fallback when a provider has no configured health-check URL (typical for
// compatible providers built from inline `proxies:` lists).
const defaultHealthCheckURL = "http://www.gstatic.com/generate_204"

// perProxyHealthCheckTimeout caps each individual proxy URLTest when the
// provider's own health-check timeout cannot be extracted. Mirrors the
// mihomo default so behaviour matches a vanilla provider.HealthCheck()
// for subscriptions that don't override `health-check.timeout`.
const perProxyHealthCheckTimeout = 5 * time.Second

// perGroupConcurrencyLimit mirrors the cap mihomo's provider.HealthCheck
// imposes via errgroup.SetLimit(10) in adapter/provider/healthcheck.go.
// Without it, a group with 100+ proxies fires 100+ simultaneous URLTest
// goroutines — TLS handshake storming + local socket pressure inflates
// every reported delay 2-3x compared to the vanilla path. Matching the
// engine's own cap keeps per-proxy push results comparable.
const perGroupConcurrencyLimit = 10

// extractHealthCheckSettings pulls the configured health-check timeout off
// a ProxyProvider so per-proxy URLTest can honour the subscription's
// `health-check.timeout` instead of hard-coding 5s. mihomo only exposes
// HealthCheckURL() through the ProxyProvider interface — timeout lives on
// the private baseProvider.healthCheck field. Reflection is the least
// invasive workaround until we fork mihomo and add a public getter
// (recorded in project memory).
//
// expectedStatus extraction is intentionally NOT attempted via reflection:
// reflect.Value.Interface() panics unconditionally on values obtained from
// unexported fields, regardless of IsNil/IsValid guards, so any subscription
// with a non-default `health-check.expected-status` would crash the Go
// runtime through SIGABRT — observed in the wild on user reports. Until the
// submodule getter lands we always pass nil to URLTest, which matches
// mihomo's own default (accept any 2xx) for subscriptions that do not set
// the field. Custom expected-status ranges silently degrade to that default
// instead of panicking — acceptable; the affected configurations are rare.
//
// If the field layout shifts in a future mihomo bump this falls back to
// timeout = perProxyHealthCheckTimeout (the mihomo default), keeping the
// call path safe.
func extractHealthCheckSettings(prov provider.ProxyProvider) (time.Duration, utils.IntRanges[uint16]) {
	timeout := perProxyHealthCheckTimeout
	var expectedStatus utils.IntRanges[uint16]

	pv := reflect.ValueOf(prov)
	if pv.Kind() == reflect.Ptr {
		pv = pv.Elem()
	}
	if !pv.IsValid() || pv.Kind() != reflect.Struct {
		return timeout, expectedStatus
	}

	hcField := pv.FieldByName("healthCheck")
	if !hcField.IsValid() || hcField.Kind() != reflect.Ptr || hcField.IsNil() {
		return timeout, expectedStatus
	}
	hc := hcField.Elem()
	if !hc.IsValid() || hc.Kind() != reflect.Struct {
		return timeout, expectedStatus
	}

	if t := hc.FieldByName("timeout"); t.IsValid() && t.Kind() == reflect.Uint {
		if tval := t.Uint(); tval > 0 {
			timeout = time.Duration(tval) * time.Millisecond
		}
	}
	return timeout, expectedStatus
}

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

	// Bound concurrent URLTests across the whole group, not per provider —
	// a kaso-style config can stack several providers behind one group and
	// each one's leaf count adds to the outgoing socket pressure.
	eg := new(errgroup.Group)
	eg.SetLimit(perGroupConcurrencyLimit)
	for _, prov := range g.Providers() {
		url := prov.HealthCheckURL()
		if url == "" {
			url = defaultHealthCheckURL
		}
		// Honour provider-level health-check.timeout / expected-status from
		// the subscription. Without this a custom `health-check.timeout: 10`
		// would still cap at 5s here, producing false timeout entries that
		// disagree with what the engine's own provider.HealthCheck() would
		// report. See extractHealthCheckSettings for the reflection caveat.
		timeout, expectedStatus := extractHealthCheckSettings(prov)
		for _, target := range prov.Proxies() {
			target := target
			url := url
			timeout := timeout
			expectedStatus := expectedStatus
			eg.Go(func() error {
				ctx, cancel := context.WithTimeout(context.Background(), timeout)
				defer cancel()
				delay, err := target.URLTest(ctx, url, expectedStatus)
				if err != nil {
					onDelay(target.Name(), 0, err.Error())
					return nil
				}
				onDelay(target.Name(), int(delay), "")
				return nil
			})
		}
	}
	_ = eg.Wait()
	return ""
}
