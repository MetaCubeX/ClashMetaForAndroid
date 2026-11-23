package config

import (
	"os"
	P "path"
	"sort"

	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/component/age"
	"github.com/metacubex/mihomo/config"
)

type ProfilePreview struct {
	Groups    []ProfilePreviewGroup    `json:"groups,omitempty"`
	Proxies   []ProfilePreviewProxy    `json:"proxies,omitempty"`
	Providers []ProfilePreviewProvider `json:"providers,omitempty"`
	Error     string                   `json:"error,omitempty"`
}

type ProfilePreviewGroup struct {
	Name       string                 `json:"name"`
	Type       string                 `json:"type"`
	Members    []ProfilePreviewMember `json:"members"`
	IncludeAll bool                   `json:"includeAll,omitempty"`
}

type ProfilePreviewMember struct {
	Name string `json:"name"`
	Type string `json:"type,omitempty"`
	Kind string `json:"kind"`
}

type ProfilePreviewProxy struct {
	Name string `json:"name"`
	Type string `json:"type,omitempty"`
}

type ProfilePreviewProvider struct {
	Name string `json:"name"`
	Type string `json:"type,omitempty"`
}

func PreviewProfile(profilePath string, ageSecretKey string) (*ProfilePreview, error) {
	data, err := os.ReadFile(P.Join(profilePath, "config.yaml"))
	if err != nil {
		return nil, err
	}

	if ageSecretKey == "" {
		data, err = age.DecryptBytes(data)
	} else {
		data, err = age.DecryptBytes(data, ageSecretKey)
	}
	if err != nil {
		return nil, err
	}

	raw := config.DefaultRawConfig()
	if err := yaml.Unmarshal(data, raw); err != nil {
		return nil, err
	}

	proxyTypes := make(map[string]string, len(raw.Proxy))
	proxies := make([]ProfilePreviewProxy, 0, len(raw.Proxy))
	seenProxies := make(map[string]struct{}, len(raw.Proxy))
	for _, proxy := range raw.Proxy {
		name := previewString(proxy, "name")
		if name == "" {
			continue
		}
		if _, exists := seenProxies[name]; exists {
			continue
		}
		seenProxies[name] = struct{}{}
		proxyType := previewString(proxy, "type")
		proxyTypes[name] = proxyType
		proxies = append(proxies, ProfilePreviewProxy{Name: name, Type: proxyType})
	}

	groupNames := make(map[string]struct{}, len(raw.ProxyGroup))
	groupTypes := make(map[string]string, len(raw.ProxyGroup))
	for _, group := range raw.ProxyGroup {
		if name := previewString(group, "name"); name != "" {
			groupNames[name] = struct{}{}
			groupTypes[name] = previewString(group, "type")
		}
	}

	providerNames := make([]string, 0, len(raw.ProxyProvider))
	providerTypes := make(map[string]string, len(raw.ProxyProvider))
	for name, provider := range raw.ProxyProvider {
		providerNames = append(providerNames, name)
		providerTypes[name] = previewString(provider, "type")
	}
	sort.Strings(providerNames)
	providers := make([]ProfilePreviewProvider, 0, len(providerNames))
	for _, name := range providerNames {
		providers = append(providers, ProfilePreviewProvider{
			Name: name,
			Type: providerTypes[name],
		})
	}

	groups := make([]ProfilePreviewGroup, 0, len(raw.ProxyGroup))
	for _, group := range raw.ProxyGroup {
		name := previewString(group, "name")
		if name == "" {
			continue
		}

		members := make([]ProfilePreviewMember, 0)
		seenMembers := make(map[string]struct{})
		for _, member := range previewStrings(group["proxies"]) {
			if member == "" {
				continue
			}
			if _, exists := seenMembers[member]; exists {
				continue
			}
			seenMembers[member] = struct{}{}
			kind := "proxy"
			memberType := proxyTypes[member]
			if _, isGroup := groupNames[member]; isGroup {
				kind = "group"
				memberType = groupTypes[member]
			}
			members = append(members, ProfilePreviewMember{Name: member, Type: memberType, Kind: kind})
		}
		for _, provider := range previewStrings(group["use"]) {
			if provider == "" {
				continue
			}
			if _, exists := seenMembers[provider]; exists {
				continue
			}
			seenMembers[provider] = struct{}{}
			members = append(members, ProfilePreviewMember{
				Name: provider,
				Type: providerTypes[provider],
				Kind: "provider",
			})
		}

		groups = append(groups, ProfilePreviewGroup{
			Name:    name,
			Type:    previewString(group, "type"),
			Members: members,
			IncludeAll: previewBool(group, "include-all") ||
				previewBool(group, "include-all-proxies") ||
				previewBool(group, "include-all-providers"),
		})
	}

	return &ProfilePreview{Groups: groups, Proxies: proxies, Providers: providers}, nil
}

func previewString(mapping map[string]any, key string) string {
	value, _ := mapping[key].(string)
	return value
}

func previewBool(mapping map[string]any, key string) bool {
	value, _ := mapping[key].(bool)
	return value
}

func previewStrings(value any) []string {
	switch typed := value.(type) {
	case []string:
		return typed
	case []any:
		result := make([]string, 0, len(typed))
		for _, item := range typed {
			if text, ok := item.(string); ok {
				result = append(result, text)
			}
		}
		return result
	case string:
		return []string{typed}
	default:
		return nil
	}
}
