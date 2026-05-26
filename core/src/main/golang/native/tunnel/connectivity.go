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

// extractHealthCheckSettings pulls the configured health-check timeout and
// expected-status range out of a ProxyProvider so per-proxy URLTest can
// honour the subscription's settings instead of hard-coding defaults.
// mihomo only exposes HealthCheckURL() through the ProxyProvider interface
// — timeout / expectedStatus live on the private baseProvider.healthCheck
// field. Reflection is the least invasive workaround: a submodule patch
// adding public getters would be cleaner but requires forking mihomo
// (the current submodule points at upstream MetaCubeX/mihomo).
//
// If the field layout shifts in a future mihomo bump the function falls
// back to mihomo's own defaults — timeout = perProxyHealthCheckTimeout,
// expectedStatus = nil (match any 2xx) — keeping the call path safe.
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
	if es := hc.FieldByName("expectedStatus"); es.IsValid() {
		if v, ok := es.Interface().(utils.IntRanges[uint16]); ok {
			expectedStatus = v
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

	var wg sync.WaitGroup
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
			wg.Add(1)
			go func() {
				defer wg.Done()
				ctx, cancel := context.WithTimeout(context.Background(), timeout)
				defer cancel()
				delay, err := target.URLTest(ctx, url, expectedStatus)
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
