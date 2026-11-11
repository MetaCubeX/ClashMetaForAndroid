package tunnel

import (
	"sort"
	"strings"

	"github.com/dlclark/regexp2"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

type SortMode int

const (
	Default SortMode = iota
	Title
	Delay
)

// noDelay is mihomo's sentinel for "no usable delay for this test url". The ui renders it as blank.
const noDelay uint16 = 0xffff

type Proxy struct {
	Name     string `json:"name"`
	Title    string `json:"title"`
	Subtitle string `json:"subtitle"`
	Type     string `json:"type"`
	Delay    int    `json:"delay"`
	IsGroup  bool   `json:"isGroup"`
}

type ProxyGroup struct {
	Type    string   `json:"type"`
	Now     string   `json:"now"`
	Proxies []*Proxy `json:"proxies"`
}

type sortableProxyList struct {
	list []*Proxy
	less func(a, b *Proxy) bool
}

func (s *sortableProxyList) Len() int {
	return len(s.list)
}

func (s *sortableProxyList) Less(i, j int) bool {
	return s.less(s.list[i], s.list[j])
}

func (s *sortableProxyList) Swap(i, j int) {
	s.list[i], s.list[j] = s.list[j], s.list[i]
}

func QueryProxyGroupNames(excludeNotSelectable bool) []string {
	mode := tunnel.Mode()

	if mode == tunnel.Direct {
		return []string{}
	}

	global := tunnel.Proxies()["GLOBAL"].Adapter().(outboundgroup.ProxyGroup)
	proxies := global.Providers()[0].Proxies()
	result := make([]string, 0, len(proxies)+1)

	if mode == tunnel.Global {
		result = append(result, "GLOBAL")
	}

	for _, p := range proxies {
		if g, ok := p.Adapter().(outboundgroup.ProxyGroup); ok {
			if !excludeNotSelectable || p.Type() == C.Selector {
				if g.Hidden() {
					continue
				}
				result = append(result, p.Name())
			}
		}
	}

	return result
}

func QueryProxyGroup(name string, sortMode SortMode, uiSubtitlePattern *regexp2.Regexp) *ProxyGroup {
	p := tunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Query group `%s`: not found", name)

		return nil
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Query group `%s`: invalid type %s", name, p.Type().String())

		return nil
	}

	testURL := groupTestURL(g)

	proxies := convertProxies(g.Proxies(), uiSubtitlePattern, testURL)
	// 	proxies := collectProviders(g.Providers(), uiSubtitlePattern, testURL)

	switch sortMode {
	case Title:
		wrapper := &sortableProxyList{
			list: proxies,
			less: func(a, b *Proxy) bool {
				return strings.Compare(a.Title, b.Title) < 0
			},
		}

		sort.Sort(wrapper)
	case Delay:
		wrapper := &sortableProxyList{
			list: proxies,
			less: func(a, b *Proxy) bool {
				return a.Delay < b.Delay
			},
		}

		sort.Sort(wrapper)
	case Default:
	default:
	}

	return &ProxyGroup{
		Type:    g.Type().String(),
		Now:     g.Now(),
		Proxies: proxies,
	}
}

func PatchSelector(selector, name string) bool {
	p := tunnel.Proxies()[selector]

	if p == nil {
		log.Warnln("Patch selector `%s`: not found", selector)

		return false
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Patch selector `%s`: invalid type %s", selector, p.Type().String())

		return false
	}

	s, ok := g.(outboundgroup.SelectAble)
	if !ok {
		log.Warnln("Patch selector `%s`: invalid type %s", selector, p.Type().String())

		return false
	}

	if err := s.Set(name); err != nil {
		log.Warnln("Patch selector `%s`: %s", selector, err.Error())
	}

	log.Infoln("Patch selector %s -> %s", selector, name)

	closeConnByGroup(selector)

	return true
}

// lastDelay returns the delay of the proxy for the test url the group is checked with.
//
// mihomo keeps one delay history per test url and LastDelayForTestUrl returns 0xffff unless the
// last test of that exact url was alive, so the url must never be picked at random (map iteration
// order is randomized): ask for the group's own url first, then fall back to any alive url.
func lastDelay(p C.Proxy, testURL string) uint16 {
	if testURL != "" {
		if delay := p.LastDelayForTestUrl(testURL); delay != noDelay {
			return delay
		}
	}

	var best uint16

	for _, state := range p.ExtraDelayHistories() {
		if !state.Alive || len(state.History) == 0 {
			continue
		}

		delay := state.History[len(state.History)-1].Delay

		if delay == 0 || delay == noDelay {
			continue
		}

		if best == 0 || delay < best {
			best = delay
		}
	}

	if best == 0 {
		return noDelay
	}

	return best
}

func convertProxies(proxies []C.Proxy, uiSubtitlePattern *regexp2.Regexp, testURL string) []*Proxy {
	result := make([]*Proxy, 0, 128)

	for _, p := range proxies {
		name := p.Name()
		title := name
		subtitle := p.Type().String()

		if uiSubtitlePattern != nil {
			if _, ok := p.Adapter().(outboundgroup.ProxyGroup); !ok {
				runes := []rune(name)
				match, err := uiSubtitlePattern.FindRunesMatch(runes)
				if err == nil && match != nil {
					title = string(runes[:match.Index]) + string(runes[match.Index+match.Length:])
					subtitle = string(runes[match.Index : match.Index+match.Length])
				}
			}
		}
		_, isGroup := p.Adapter().(outboundgroup.ProxyGroup)

		result = append(result, &Proxy{
			Name:     name,
			Title:    strings.TrimSpace(title),
			Subtitle: strings.TrimSpace(subtitle),
			Type:     p.Type().String(),
			Delay:    int(lastDelay(p, testURL)),
			IsGroup:  isGroup,
		})
	}
	return result
}

func collectProviders(providers []provider.ProxyProvider, uiSubtitlePattern *regexp2.Regexp, testURL string) []*Proxy {
	result := make([]*Proxy, 0, 128)

	for _, p := range providers {
		for _, px := range p.Proxies() {
			name := px.Name()
			title := name
			subtitle := px.Type().String()

			if uiSubtitlePattern != nil {
				if _, ok := px.Adapter().(outboundgroup.ProxyGroup); !ok {
					runes := []rune(name)
					match, err := uiSubtitlePattern.FindRunesMatch(runes)
					if err == nil && match != nil {
						title = string(runes[:match.Index]) + string(runes[match.Index+match.Length:])
						subtitle = string(runes[match.Index : match.Index+match.Length])
					}
				}
			}
			_, isGroup := px.Adapter().(outboundgroup.ProxyGroup)

			result = append(result, &Proxy{
				Name:     name,
				Title:    strings.TrimSpace(title),
				Subtitle: strings.TrimSpace(subtitle),
				Type:     px.Type().String(),
				Delay:    int(lastDelay(px, testURL)),
				IsGroup:  isGroup,
			})
		}
	}

	return result
}
