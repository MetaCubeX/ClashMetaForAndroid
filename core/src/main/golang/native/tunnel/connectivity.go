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

func HealthCheck(name string) {
	p := tunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Request health check for `%s`: not found", name)

		return
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		testURL := "https://www.gstatic.com/generate_204"
		for k := range p.ExtraDelayHistories() {
			if len(k) > 0 {
				testURL = k
				break
			}
		}

		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()

		if _, err := p.URLTest(ctx, testURL, nil); err != nil && ctx.Err() == nil {
			log.Warnln("Request health check for `%s`: %s", name, err.Error())
		}

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

func HealthCheckProxy(groupName string, proxyName string) {
	p := tunnel.Proxies()[groupName]

	if p == nil {
		log.Warnln("Request health check for proxy `%s` in group `%s`: group not found", proxyName, groupName)
		return
	}
 
	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Request health check for proxy `%s` in group `%s`: not a proxy group", proxyName, groupName)
		return
	}

	for _, proxy := range g.Proxies() {
		if proxy.Name() == proxyName {
			testURL := "https://www.gstatic.com/generate_204"
			for k := range proxy.ExtraDelayHistories() {
				if len(k) > 0 {
					testURL = k
					break
				}
			}

			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()

			if _, err := proxy.URLTest(ctx, testURL, nil); err != nil && ctx.Err() == nil {
				log.Warnln("Request health check for proxy `%s`: %s", proxyName, err.Error())
			}
			return
		}
	}

	log.Warnln("Request health check for proxy `%s` in group `%s`: proxy not found", proxyName, groupName)
}
