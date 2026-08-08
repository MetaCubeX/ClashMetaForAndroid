package tunnel

import (
	"fmt"
	"reflect"
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

type Proxy struct {
	Name     string   `json:"name"`
	Title    string   `json:"title"`
	Subtitle string   `json:"subtitle"`
	Type     string   `json:"type"`
	Delay    int      `json:"delay"`
	IsGroup  bool     `json:"isGroup"`
	Chain    []string `json:"chain,omitempty"`
	// Server is the dial address (host:port) of the proxy, when available.
	Server string `json:"server,omitempty"`
	// ChainDetail carries per-hop metadata for the dialer-proxy chain. Each
	// element pairs a node name with its server address (or empty when the
	// node is a group / has no address).
	ChainDetail []ProxyChainNode `json:"chainDetail,omitempty"`
}

type ProxyChainNode struct {
	Name   string `json:"name"`
	Type   string `json:"type"`
	Server string `json:"server,omitempty"`
	// Details is an ordered key/value list describing the node's configuration
	// (cipher, TLS, UUID masked, network, etc). Values are already localized-free
	// raw strings; the UI renders them as label : value rows.
	Details []ProxyDetail `json:"details,omitempty"`
}

type ProxyDetail struct {
	Label string `json:"label"`
	Value string `json:"value"`
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

	proxies := convertProxies(g.Proxies(), uiSubtitlePattern)
	// 	proxies := collectProviders(g.Providers(), uiSubtitlePattern)

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

func convertProxies(proxies []C.Proxy, uiSubtitlePattern *regexp2.Regexp) []*Proxy {
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
		testURL := "https://www.gstatic.com/generate_204"
		for k := range p.ExtraDelayHistories() {
			if len(k) > 0 {
				testURL = k
				break
			}
		}
		_, isGroup := p.Adapter().(outboundgroup.ProxyGroup)
		chain := buildProxyChain(p)
		chainDetail := buildProxyChainDetail(chain)

		result = append(result, &Proxy{
			Name:        name,
			Title:       strings.TrimSpace(title),
			Subtitle:    strings.TrimSpace(subtitle),
			Type:        p.Type().String(),
			Delay:       int(p.LastDelayForTestUrl(testURL)),
			IsGroup:     isGroup,
			Chain:       chain,
			Server:      serverOf(p),
			ChainDetail: chainDetail,
		})
	}
	return result
}

// serverOf returns the dial address of a proxy (host:port) when the adapter
// exposes one, otherwise an empty string.
func serverOf(p C.Proxy) string {
	if p == nil {
		return ""
	}
	addr := p.Addr()
	if addr == "" {
		return ""
	}
	return addr
}

// buildProxyChainDetail resolves per-hop metadata for a chain of names so the
// UI can show each hop's type and server alongside its name.
func buildProxyChainDetail(chain []string) []ProxyChainNode {
	if len(chain) == 0 {
		return nil
	}
	nodes := make([]ProxyChainNode, 0, len(chain))
	for _, name := range chain {
		node := ProxyChainNode{Name: name}
		if p, ok := tunnel.Proxies()[name]; ok && p != nil {
			node.Type = p.Type().String()
			node.Server = serverOf(p)
			node.Details = proxyDetailsOf(p)
		}
		nodes = append(nodes, node)
	}
	return nodes
}

// buildProxyChain resolves the dialer-proxy chain of a proxy into physical
// order: from the outermost entry node (dialed by the local device) to the
// proxy itself which acts as the exit node towards the target server.
func buildProxyChain(p C.Proxy) []string {
	var reversed []string
	seen := map[string]bool{}
	cur := p

	for cur != nil && !seen[cur.Name()] {
		seen[cur.Name()] = true

		dialerName := cur.Adapter().ProxyInfo().DialerProxy
		if dialerName == "" {
			break
		}

		next, ok := tunnel.Proxies()[dialerName]
		if !ok {
			reversed = append(reversed, dialerName)
			break
		}

		reversed = append(reversed, dialerName)
		cur = next
	}

	chain := make([]string, 0, len(reversed)+1)
	for i := len(reversed) - 1; i >= 0; i-- {
		chain = append(chain, reversed[i])
	}
	chain = append(chain, p.Name())

	return chain
}

func collectProviders(providers []provider.ProxyProvider, uiSubtitlePattern *regexp2.Regexp) []*Proxy {
	result := make([]*Proxy, 0, 128)

	for _, p := range providers {
		for _, px := range p.Proxies() {
			name := px.Name()
			title := name
			proxyType := px.Type().String()
			subtitle := proxyType

			if uiSubtitlePattern != nil {
				if _, ok := px.Adapter().(outboundgroup.ProxyGroup); !ok {
					runes := []rune(name)
					match, err := uiSubtitlePattern.FindRunesMatch(runes)
					if err == nil && match != nil {
						title = string(runes[:match.Index]) + string(runes[match.Index+match.Length:])
						fragment := string(runes[match.Index : match.Index+match.Length])
						if fragment != "" && fragment != proxyType {
							subtitle = proxyType + " · " + fragment
						} else {
							subtitle = proxyType
						}
					}
				}
			}

			testURL := "https://www.gstatic.com/generate_204"
			for k := range px.ExtraDelayHistories() {
				if len(k) > 0 {
					testURL = k
					break
				}
			}
			_, isGroup := px.Adapter().(outboundgroup.ProxyGroup)

			result = append(result, &Proxy{
				Name:     name,
				Title:    strings.TrimSpace(title),
				Subtitle: strings.TrimSpace(subtitle),
				Type:     px.Type().String(),
				Delay:    int(px.LastDelayForTestUrl(testURL)),
				IsGroup:  isGroup,
			})
		}
	}

	return result
}

// proxyDetailsOf extracts the key configuration of a proxy node as an ordered
// label/value list for the UI. The per-type option struct is a private field
// on each outbound adapter, so it is read via reflection. Sensitive values are
// masked, and empty/default fields are skipped.
func proxyDetailsOf(p C.Proxy) []ProxyDetail {
	if p == nil {
		return nil
	}
	out := []ProxyDetail{
		{Label: "Type", Value: p.Type().String()},
	}
	if addr := p.Addr(); addr != "" {
		out = append(out, ProxyDetail{Label: "Server", Value: addr})
	}

	opt := reflectOption(p)
	if !opt.IsValid() {
		// An empty detail list is indistinguishable from a node that genuinely
		// has no options, so name the adapter the walk gave up on. Debug level:
		// this runs for every node on every proxy-list refresh.
		log.Debugln("proxy detail: no option struct for %s (%T)", p.Name(), p.Adapter())
	}
	if opt.IsValid() {
		// Order matters: show identity/encryption fields first, then transport.
		fields := []string{
			"UUID", "Cipher", "Encryption", "AlterID", "alterId", "Flow",
			"Password", "Token", "Up", "Down", "Obfs", "Network", "TLS", "SNI",
			"ServerName", "Fingerprint", "ClientFingerprint", "ALPN", "UDP",
			"UDPOverTCP", "Plugin", "Protocol", "ObfsParam",
			"ProtocolParam", "UserName", "CongestionController", "UdpRelayMode",
			"ReduceRtt", "Version",
		}
		for _, f := range fields {
			fv := opt.FieldByName(f)
			if !fv.IsValid() {
				continue
			}
			val := fieldValue(fv)
			if strings.TrimSpace(val) == "" {
				continue
			}
			label := f
			switch f {
			case "AlterID", "alterId":
				label = "alterId"
			case "ServerName":
				label = "SNI"
			case "ClientFingerprint":
				label = "Fingerprint"
			case "CongestionController":
				label = "Congestion"
			case "UdpRelayMode":
				label = "UDP relay"
			case "ReduceRtt":
				label = "Reduce RTT"
			case "UserName":
				label = "Username"
			case "UDPOverTCP":
				label = "UDP over TCP"
			}
			if label == "UUID" || label == "Password" || label == "Token" {
				val = maskSecret(val)
			}
			out = append(out, ProxyDetail{Label: label, Value: val})
		}
	}

	info := p.ProxyInfo()
	if info.XUDP {
		out = append(out, ProxyDetail{Label: "XUDP", Value: "on"})
	}
	if info.TFO {
		out = append(out, ProxyDetail{Label: "TFO", Value: "on"})
	}
	if info.MPTCP {
		out = append(out, ProxyDetail{Label: "MPTCP", Value: "on"})
	}
	return out
}

// maxAdapterUnwrapDepth bounds the wrapper walk in reflectOption. Two levels
// are enough today (autoClose -> outbound); the rest is loop insurance.
const maxAdapterUnwrapDepth = 4

// reflectOption returns the private `option` struct field of the concrete
// outbound behind a proxy, or an invalid value when there is none.
//
// Two layers sit between C.Proxy and that struct, and both have to be crossed:
//
//   - C.Proxy is an *adapter.Proxy, whose fields are just the embedded
//     ProxyAdapter plus liveness bookkeeping. Adapter() steps past it.
//   - What Adapter() returns is normally not the outbound either: the runtime
//     wraps proxies in *outbound.autoCloseProxyAdapter, which embeds the real
//     adapter as an interface field named ProxyAdapter.
//
// So the walk follows that embedded field until a struct carrying `option`
// turns up. Values are threaded as reflect.Value rather than via Interface(),
// which would panic once a read-only (unexported) field is involved.
func reflectOption(p C.Proxy) reflect.Value {
	v := reflect.ValueOf(p.Adapter())

	for depth := 0; depth < maxAdapterUnwrapDepth; depth++ {
		if v.Kind() == reflect.Interface {
			if v.IsNil() {
				return reflect.Value{}
			}
			v = v.Elem()
		}
		if v.Kind() != reflect.Ptr || v.IsNil() {
			return reflect.Value{}
		}

		s := v.Elem()
		if s.Kind() != reflect.Struct {
			return reflect.Value{}
		}

		if f := s.FieldByName("option"); f.IsValid() && f.Kind() == reflect.Ptr && !f.IsNil() {
			// `option` is unexported, so the result is read-only. Only the
			// kind-specific getters are used on it, which read-only permits.
			return f.Elem()
		}

		inner := s.FieldByName("ProxyAdapter")
		if !inner.IsValid() {
			return reflect.Value{}
		}
		v = inner
	}

	return reflect.Value{}
}

func fieldValue(v reflect.Value) string {
	switch v.Kind() {
	case reflect.String:
		return v.String()
	case reflect.Bool:
		if v.Bool() {
			return "yes"
		}
		return ""
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		if v.Int() != 0 {
			return fmt.Sprintf("%d", v.Int())
		}
		return ""
	case reflect.Slice:
		if v.Type().Elem().Kind() == reflect.String {
			var parts []string
			for i := 0; i < v.Len(); i++ {
				s := v.Index(i).String()
				if s != "" {
					parts = append(parts, s)
				}
			}
			return strings.Join(parts, ",")
		}
	}
	return ""
}

func maskSecret(s string) string {
	if len(s) <= 4 {
		return "****"
	}
	return s[:2] + "****" + s[len(s)-2:]
}
