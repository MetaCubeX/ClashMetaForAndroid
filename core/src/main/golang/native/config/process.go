package config

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"github.com/dlclark/regexp2"

	"cfa/native/common"

	"github.com/metacubex/mihomo/common/orderedmap"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
)

var processors = []processor{
	patchExternalController, // must before patchOverride, so we only apply ExternalController in Override settings
	patchOverride,
	patchTailscale, // expand override `tailscale` (single node + routing) into proxies/groups/rules
	patchGeneral,
	patchProfile,
	patchDns,
	patchTun,
	patchListeners,
	patchProviders,
	validConfig,
}

type processor func(cfg *config.RawConfig, profileDir string) error

func patchOverride(cfg *config.RawConfig, _ string) error {
	if err := json.NewDecoder(strings.NewReader(ReadOverride(OverrideSlotPersist))).Decode(cfg); err != nil {
		log.Warnln("Apply persist override: %s", err.Error())
	}
	if err := json.NewDecoder(strings.NewReader(ReadOverride(OverrideSlotSession))).Decode(cfg); err != nil {
		log.Warnln("Apply session override: %s", err.Error())
	}

	return nil
}

// tailscaleOverride mirrors the flat `tailscale` object the Kotlin app writes
// into the override JSON. json tags match the kotlinx serialization output
// (property names, since the Kotlin data class has no @SerialName annotations).
type tailscaleOverride struct {
	Enabled    bool     `json:"enabled"`
	Hostname   string   `json:"hostname,omitempty"`
	AuthKey    string   `json:"authKey,omitempty"`
	ControlURL string   `json:"controlUrl,omitempty"`
	StateDir   string   `json:"stateDir,omitempty"`
	ExitNode   string   `json:"exitNode,omitempty"`
	IpCidrs    []string `json:"ipCidrs"`
}

type tailscaleOverrideEnvelope struct {
	Tailscale tailscaleOverride `json:"tailscale"`
}

// loadTailscaleOverride parses the tailscale block from the persist override
// JSON. It is a pure function (no file I/O) so it can be unit-tested directly;
// callers pass ReadOverride(OverrideSlotPersist). The bool result reports
// whether tailscale is enabled. A single `enabled` toggle (rather than "any
// field non-empty") lets the user keep a fully-default node — e.g. after
// clearing a one-time auth-key while still using the persistent state dir, or
// relying on tsnet's interactive login with no options at all.
func loadTailscaleOverride(overrideJSON string) (tailscaleOverride, bool) {
	var envelope tailscaleOverrideEnvelope
	if err := json.Unmarshal([]byte(overrideJSON), &envelope); err != nil {
		log.Warnln("Apply tailscale override: %s", err.Error())
		return tailscaleOverride{}, false
	}
	if !envelope.Tailscale.Enabled {
		return tailscaleOverride{}, false
	}
	return envelope.Tailscale, true
}

// Fixed names for the injected tailscale proxy and its select group. They are
// constants (not user-configurable) so they are always URL-safe in the
// "tailscale://<name>" nameserver-policy and never collide with mihomo's
// built-in proxy names (DIRECT/REJECT/...). A proxy and a group may NOT share
// a name — mihomo registers both in one map (config.go proxies[...]) — hence
// the distinct "-Group" suffix.
const (
	tailscaleProxyName = "Tailscale"
	tailscaleGroupName = "Tailscale-Group"
)

// patchTailscale expands the override `tailscale` block into a proxy node, a
// select group and prepend rules, all appended/prepended on top of the
// subscription config. Keeping the expansion on the Go side lets the Kotlin
// model stay a plain flat object (no custom serializer), which is safe for the
// Android Parcel IPC path.
//
// Any same-named proxy/group already present in the subscription is removed
// first, so re-applying the override (or a subscription that happens to define
// its own "Tailscale") replaces rather than triggers mihomo's "duplicate name"
// error. Both fixed names are cleared from BOTH cfg.Proxy and cfg.ProxyGroup,
// because mihomo registers proxies and groups in one shared map — a group named
// "Tailscale" would clash with our proxy even though we only append a group
// named "Tailscale-Group", and vice versa.
func patchTailscale(cfg *config.RawConfig, _ string) error {
	ts, ok := loadTailscaleOverride(ReadOverride(OverrideSlotPersist))
	if !ok {
		return nil
	}

	removeProxyByName(cfg, tailscaleProxyName)
	removeProxyGroupByName(cfg, tailscaleProxyName)
	removeProxyByName(cfg, tailscaleGroupName)
	removeProxyGroupByName(cfg, tailscaleGroupName)

	// 1. append the tailscale proxy node. Only non-empty option fields are set
	// so mihomo applies its own defaults for the rest.
	proxy := map[string]any{
		"name": tailscaleProxyName,
		"type": "tailscale",
	}
	if ts.Hostname != "" {
		proxy["hostname"] = ts.Hostname
	}
	if ts.AuthKey != "" {
		proxy["auth-key"] = ts.AuthKey
	}
	if ts.ControlURL != "" {
		proxy["control-url"] = ts.ControlURL
	}
	if ts.StateDir != "" {
		proxy["state-dir"] = ts.StateDir
	}
	if ts.ExitNode != "" {
		proxy["exit-node"] = ts.ExitNode
	}
	cfg.Proxy = append(cfg.Proxy, proxy)
	log.Infoln("Append tailscale proxy: %s", tailscaleProxyName)

	// 2. select group + prepend rules routing tailnet traffic to it.
	//
	// CMFA forces fake-ip mode. mihomo returns a synthetic IP for a name,
	// then maps it back to the host on the connection; rules then match on
	// that host. Two rules cover tailnet access:
	//  - DOMAIN-SUFFIX,ts.net: all tailnet FQDNs (nas.tail<hex>.ts.net).
	//    Tailscale short names (e.g. "nas") are NOT supported: the tsnet
	//    QueryDNS path does not append the MagicDNS search suffix, so they
	//    cannot be resolved. Users must use full *.ts.net names or IPs.
	//  - IP-CIDR 100.64.0.0/10: the CGNAT range tailscale allocates from.
	//
	// Note: the +.ts.net fake-ip filter and the nameserver-policy are added in
	// patchDns (below), not here, because patchDns runs AFTER patchTailscale
	// and would otherwise reset cfg.DNS for subscriptions that don't enable
	// their own DNS.
	cfg.ProxyGroup = append(cfg.ProxyGroup, map[string]any{
		"name":    tailscaleGroupName,
		"type":    "select",
		"proxies": []string{tailscaleProxyName},
	})

	// Default IP range: Tailscale allocates node IPs from the CGNAT block
	// 100.64.0.0/10, which is identical for every tailnet.
	ips := ts.IpCidrs
	if len(ips) == 0 {
		ips = []string{"100.64.0.0/10"}
	}
	rules := make([]string, 0, len(ips)+1)

	// Cover all tailnet FQDNs (*.ts.net) automatically.
	rules = append(rules, "DOMAIN-SUFFIX,ts.net,"+tailscaleGroupName)

	// Direct IP access into the tailnet.
	for _, c := range ips {
		rules = append(rules, "IP-CIDR,"+c+","+tailscaleGroupName+",no-resolve")
	}

	merged := make([]string, 0, len(rules)+len(cfg.Rule))
	merged = append(merged, rules...)
	merged = append(merged, cfg.Rule...)
	cfg.Rule = merged

	log.Infoln("Apply tailscale routing: group=%s rules=%d", tailscaleGroupName, len(rules))

	return nil
}

// removeProxyByName drops every proxy whose "name" equals name from cfg.Proxy.
// Used to overwrite a same-named entry the subscription already ships before
// appending our own, avoiding mihomo's "duplicate name" load error.
func removeProxyByName(cfg *config.RawConfig, name string) {
	filtered := cfg.Proxy[:0]
	for _, p := range cfg.Proxy {
		if n, _ := p["name"].(string); n == name {
			continue
		}
		filtered = append(filtered, p)
	}
	cfg.Proxy = filtered
}

// removeProxyGroupByName is the proxy-group counterpart of removeProxyByName.
func removeProxyGroupByName(cfg *config.RawConfig, name string) {
	filtered := cfg.ProxyGroup[:0]
	for _, g := range cfg.ProxyGroup {
		if n, _ := g["name"].(string); n == name {
			continue
		}
		filtered = append(filtered, g)
	}
	cfg.ProxyGroup = filtered
}

func patchExternalController(cfg *config.RawConfig, _ string) error {
	cfg.ExternalController = ""
	cfg.ExternalControllerTLS = ""

	return nil
}

func patchGeneral(cfg *config.RawConfig, profileDir string) error {
	cfg.Interface = ""
	cfg.RoutingMark = 0
	if cfg.ExternalController != "" || cfg.ExternalControllerTLS != "" {
		cfg.ExternalUI = profileDir + "/ui"
	}

	return nil
}

func patchProfile(cfg *config.RawConfig, _ string) error {
	cfg.Profile.StoreSelected = false
	cfg.Profile.StoreFakeIP = true

	return nil
}

func patchDns(cfg *config.RawConfig, _ string) error {
	if !cfg.DNS.Enable {
		cfg.DNS = config.DefaultRawConfig().DNS
		cfg.DNS.Enable = true
		cfg.DNS.NameServer = defaultNameServers
		cfg.DNS.EnhancedMode = C.DNSFakeIP
		cfg.DNS.FakeIPRange = defaultFakeIPRange
		cfg.DNS.FakeIPFilter = defaultFakeIPFilter

		cfg.ClashForAndroid.AppendSystemDNS = true
	}

	if cfg.ClashForAndroid.AppendSystemDNS {
		cfg.DNS.NameServer = append(cfg.DNS.NameServer, "system://")
	}

	// Route *.ts.net to the Tailscale MagicDNS transport when tailscale is
	// configured. This runs after the default/reset block above so it survives
	// the DNS replacement for subscriptions without their own DNS.
	if _, ok := loadTailscaleOverride(ReadOverride(OverrideSlotPersist)); ok {
		applyTailscaleDNS(&cfg.DNS)
	}

	return nil
}

// tsPolicyKey / tsFilterEntry / tsFilterRule are the domain keys injected into
// DNS policy and fake-ip-filter. The key form "+.ts.net" is accepted by both
// parseNameServerPolicy (via ValidAndSplitDomain) and the fake-ip trie.
const (
	tsPolicyKey   = "+.ts.net"
	tsFilterEntry = "+.ts.net"
	// tsFilterRule is the rule-mode form of the same intent: a real-ip action so
	// the domain is excluded from fake-ip. parseFakeIPRules requires an explicit
	// action suffix; a bare "+.ts.net" would fail config parsing.
	tsFilterRule = "DOMAIN-SUFFIX,ts.net,real-ip"
)

// applyTailscaleDNS wires *.ts.net to the registered tailscale:// DNS transport
// and keeps the domain out of fake-ip. Both must be done for MagicDNS FQDNs to
// resolve correctly:
//
//  1. nameserver-policy: mihomo only instantiates the tailscale DNS client when
//     the scheme "tailscale://<node>" appears in a nameserver / policy slot.
//     Without this entry the transport is registered but never queried, so FQDNs
//     fall through to the system DNS and fail.
//
//  2. fake-ip-filter: the fake-ip middleware consults the skipper before any
//     upstream, so an unlisted *.ts.net would get a synthetic IP and never reach
//     the policy. The correct filter entry depends on the mode:
//     - blacklist (default): "+.ts.net" is a skip entry → real DNS (the policy).
//     - whitelist: only listed domains use fake-ip; since we don't list *.ts.net
//     it is skipped out of fake-ip and falls through to the resolver/policy.
//     Adding "+.ts.net" here would be wrong — it would FORCE fake-ip for the
//     domain, the opposite of what we want.
//     - rule: filter entries must be full rules with an action, otherwise
//     parseFakeIPRules rejects the whole config.
//
// The policy entry is always written (overwriting any subscription value for
// "+.ts.net"), because enabling tailscale means THIS node must own MagicDNS
// resolution. The node name is the fixed tailscaleProxyName constant.
func applyTailscaleDNS(dns *config.RawDNS) {
	// (1) nameserver-policy → tailscale://<node>.
	if dns.NameServerPolicy == nil {
		dns.NameServerPolicy = orderedmap.New[string, any]()
	}
	dns.NameServerPolicy.Set(tsPolicyKey, "tailscale://"+tailscaleProxyName)

	// (2) fake-ip-filter, mode-dependent.
	switch dns.FakeIPFilterMode {
	case C.FilterBlackList:
		if !containsString(dns.FakeIPFilter, tsFilterEntry) {
			dns.FakeIPFilter = append(dns.FakeIPFilter, tsFilterEntry)
		}
	case C.FilterWhiteList:
		// Intentionally do nothing. In whitelist mode only listed domains use
		// fake-ip; *.ts.net is not listed, so the skipper already routes it to
		// real DNS where the policy above answers it. Adding "+.ts.net" here
		// would invert the intent and force fake-ip for the domain.
	case C.FilterRule:
		if !containsString(dns.FakeIPFilter, tsFilterRule) {
			dns.FakeIPFilter = append(dns.FakeIPFilter, tsFilterRule)
		}
	}
}

// containsString reports whether s is in list.
func containsString(list []string, s string) bool {
	for _, v := range list {
		if v == s {
			return true
		}
	}
	return false
}

func patchTun(cfg *config.RawConfig, _ string) error {
	cfg.Tun.Enable = false
	cfg.Tun.AutoRoute = false
	cfg.Tun.AutoDetectInterface = false
	return nil
}

func patchListeners(cfg *config.RawConfig, _ string) error {
	newListeners := make([]map[string]any, 0, len(cfg.Listeners))
	for _, mapping := range cfg.Listeners {
		if proxyType, existType := mapping["type"].(string); existType {
			switch proxyType {
			case "tproxy", "redir", "tun":
				continue // remove those listeners which is not supported
			}
		}
		newListeners = append(newListeners, mapping)
	}
	cfg.Listeners = newListeners
	return nil
}

func patchProviders(cfg *config.RawConfig, profileDir string) error {
	forEachProviders(cfg, func(index int, total int, key string, provider map[string]any, prefix string) {
		path, _ := provider["path"].(string)
		if len(path) > 0 {
			path = common.ResolveAsRoot(path)
		} else if url, ok := provider["url"].(string); ok {
			path = prefix + "/" + utils.MakeHash([]byte(url)).String() // same as C.GetPathByHash
		} else {
			return // both path and url are empty, maybe inline provider
		}
		provider["path"] = profileDir + "/providers/" + path
	})

	return nil
}

func validConfig(cfg *config.RawConfig, _ string) error {
	if len(cfg.Proxy) == 0 && len(cfg.ProxyProvider) == 0 {
		return errors.New("profile does not contain `proxies` or `proxy-providers`")
	}

	if _, err := regexp2.Compile(cfg.ClashForAndroid.UiSubtitlePattern, 0); err != nil {
		return fmt.Errorf("compile ui-subtitle-pattern: %s", err.Error())
	}

	return nil
}

func process(cfg *config.RawConfig, profileDir string) error {
	for _, p := range processors {
		if err := p(cfg, profileDir); err != nil {
			return err
		}
	}

	return nil
}
