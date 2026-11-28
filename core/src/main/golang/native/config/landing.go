package config

import (
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strings"
	"sync"

	"github.com/dlclark/regexp2"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/log"
	RC "github.com/metacubex/mihomo/rules/common"
	"github.com/metacubex/mihomo/tunnel"
)

// LandingProxyName is the name of the injected landing (exit) proxy.
const LandingProxyName = "Landing Proxy"

// LandingProxy describes an explicit landing (exit) proxy chained behind a
// front group of the profile:
//
//	device -> front group's selected node -> landing proxy -> target
//
// Every rule, sub-rule and group member that targets the front group is
// redirected to the landing proxy, while the front group itself stays
// selectable and decides which node is used as the relay.
type LandingProxy struct {
	Type           string `json:"type"` // http, https, socks5
	Server         string `json:"server"`
	Port           int    `json:"port"`
	Username       string `json:"username"`
	Password       string `json:"password"`
	FrontGroup     string `json:"frontGroup"` // empty = first `select` group
	SkipCertVerify bool   `json:"skipCertVerify"`
	Sni            string `json:"sni"`
	Udp            bool   `json:"udp"`
}

var (
	landingLock sync.Mutex
	landing     *LandingProxy
)

// SetLandingProxy sets (or clears, with an empty string) the landing proxy
// used by subsequent loads.
func SetLandingProxy(content string) error {
	landingLock.Lock()
	defer landingLock.Unlock()

	if strings.TrimSpace(content) == "" {
		landing = nil

		return nil
	}

	l := &LandingProxy{}
	if err := json.Unmarshal([]byte(content), l); err != nil {
		landing = nil

		return err
	}

	landing = l

	return nil
}

func currentLanding() *LandingProxy {
	landingLock.Lock()
	defer landingLock.Unlock()

	return landing
}

func (l *LandingProxy) toMapping(front string) (map[string]any, error) {
	if l.Server == "" {
		return nil, errors.New("landing proxy: server is empty")
	}
	if l.Port <= 0 || l.Port > 65535 {
		return nil, fmt.Errorf("landing proxy: invalid port %d", l.Port)
	}

	m := map[string]any{
		"name":         LandingProxyName,
		"server":       l.Server,
		"port":         l.Port,
		"dialer-proxy": front,
	}

	if l.Username != "" {
		m["username"] = l.Username
		m["password"] = l.Password
	}

	switch strings.ToLower(l.Type) {
	case "http":
		m["type"] = "http"
	case "https":
		m["type"] = "http"
		m["tls"] = true
		m["skip-cert-verify"] = l.SkipCertVerify
		if l.Sni != "" {
			m["sni"] = l.Sni
		}
	case "socks5":
		m["type"] = "socks5"
		m["udp"] = l.Udp
	default:
		return nil, fmt.Errorf("landing proxy: unsupported type `%s`", l.Type)
	}

	return m, nil
}

func groupName(group map[string]any) string {
	name, _ := group["name"].(string)

	return name
}

func toStringList(value any) []string {
	switch v := value.(type) {
	case []string:
		return v
	case []any:
		result := make([]string, 0, len(v))
		for _, item := range v {
			if s, ok := item.(string); ok {
				result = append(result, s)
			}
		}
		return result
	default:
		return nil
	}
}

func toBool(value any) bool {
	switch v := value.(type) {
	case bool:
		return v
	case string:
		return strings.EqualFold(v, "true")
	default:
		return false
	}
}

func resolveFrontGroup(cfg *config.RawConfig, front string) (string, error) {
	if front != "" {
		for _, g := range cfg.ProxyGroup {
			if groupName(g) == front {
				return front, nil
			}
		}

		return "", fmt.Errorf("landing proxy: front group `%s` not found in profile", front)
	}

	for _, g := range cfg.ProxyGroup {
		if t, _ := g["type"].(string); t == "select" && groupName(g) != "" && groupName(g) != "GLOBAL" {
			return groupName(g), nil
		}
	}

	return "", errors.New("landing proxy: profile has no `select` group to use as front group")
}

// expandIncludeAllProxies replaces `include-all`/`include-all-proxies` with an
// explicit proxy list computed from the profile's own proxies, mirroring
// mihomo's ParseProxyGroup. Otherwise the injected landing proxy would be
// pulled into these groups and could form a dial loop through the front group.
func expandIncludeAllProxies(group map[string]any, allProxies []string, hasProviders bool) {
	includeAll := toBool(group["include-all"])
	includeAllProxies := toBool(group["include-all-proxies"])

	if !includeAll && !includeAllProxies {
		return
	}

	if includeAll {
		delete(group, "include-all")
		group["include-all-providers"] = true
	}
	delete(group, "include-all-proxies")

	proxies := toStringList(group["proxies"])

	if filter, _ := group["filter"].(string); filter != "" {
		var regs []*regexp2.Regexp
		for _, f := range strings.Split(filter, "`") {
			reg, err := regexp2.Compile(f, regexp2.None)
			if err != nil {
				continue // mihomo will report the invalid filter itself
			}
			regs = append(regs, reg)
		}
		for _, p := range allProxies {
			for _, reg := range regs {
				if ok, _ := reg.MatchString(p); ok {
					proxies = append(proxies, p)
					break
				}
			}
		}
	} else {
		proxies = append(proxies, allProxies...)
	}

	use := toStringList(group["use"])
	providersIncluded := toBool(group["include-all-providers"]) && hasProviders
	if len(proxies) == 0 && len(use) == 0 && !providersIncluded {
		fallback, _ := group["empty-fallback"].(string)
		if fallback == "" {
			fallback = "COMPATIBLE"
		}
		proxies = []string{fallback}
	}

	group["proxies"] = proxies
}

func retargetRule(line, front string) string {
	tp, payload, target, params := RC.ParseRulePayload(line, true)
	if target != front || tp == "SUB-RULE" {
		return line
	}

	switch tp {
	case "MATCH":
		return tp + "," + LandingProxyName
	case "NOT", "OR", "AND", "DOMAIN-REGEX", "PROCESS-NAME-REGEX", "PROCESS-PATH-REGEX":
		return tp + "," + payload + "," + LandingProxyName
	default:
		parts := append([]string{tp, payload, LandingProxyName}, params...)
		return strings.Join(parts, ",")
	}
}

func patchLanding(cfg *config.RawConfig, _ string) error {
	l := currentLanding()
	if l == nil {
		return nil
	}

	front, err := resolveFrontGroup(cfg, l.FrontGroup)
	if err != nil {
		return err
	}

	mapping, err := l.toMapping(front)
	if err != nil {
		return err
	}

	allProxies := make([]string, 0, len(cfg.Proxy))
	for _, p := range cfg.Proxy {
		name, _ := p["name"].(string)
		if name == LandingProxyName {
			return fmt.Errorf("landing proxy: profile already contains a proxy named `%s`", LandingProxyName)
		}
		allProxies = append(allProxies, name)
	}
	sort.Strings(allProxies) // same order mihomo uses for include-all

	for _, g := range cfg.ProxyGroup {
		if groupName(g) == LandingProxyName {
			return fmt.Errorf("landing proxy: profile already contains a group named `%s`", LandingProxyName)
		}

		expandIncludeAllProxies(g, allProxies, len(cfg.ProxyProvider) > 0)

		if groupName(g) == front {
			continue
		}

		if members := toStringList(g["proxies"]); members != nil {
			replaced := make([]string, 0, len(members))
			for _, m := range members {
				if m == front {
					m = LandingProxyName
				}
				replaced = append(replaced, m)
			}
			g["proxies"] = replaced
		}
	}

	for i, line := range cfg.Rule {
		cfg.Rule[i] = retargetRule(line, front)
	}

	for name, rules := range cfg.SubRules {
		for i, line := range rules {
			rules[i] = retargetRule(line, front)
		}
		cfg.SubRules[name] = rules
	}

	cfg.Proxy = append(cfg.Proxy, mapping)

	log.Infoln("[APP] landing proxy %s://%s:%d via `%s`", strings.ToLower(l.Type), l.Server, l.Port, front)

	return nil
}

// selectLandingInGlobal makes GLOBAL default to the landing proxy, so global
// mode also exits through it. A selection restored afterwards still wins.
func selectLandingInGlobal() {
	if currentLanding() == nil {
		return
	}

	p := tunnel.Proxies()["GLOBAL"]
	if p == nil {
		return
	}

	if s, ok := p.Adapter().(outboundgroup.SelectAble); ok {
		if err := s.Set(LandingProxyName); err != nil {
			log.Warnln("[APP] select landing proxy in GLOBAL: %s", err.Error())
		}
	}
}
