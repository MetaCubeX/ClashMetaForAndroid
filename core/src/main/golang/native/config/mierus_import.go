package config

import (
	"fmt"
	"os"
	P "path"
	"strconv"
	"strings"

	"github.com/metacubex/mihomo/common/convert"
	"github.com/metacubex/mihomo/common/yaml"
)

func isInlineMierusImport(raw string) bool {
	s := strings.TrimSpace(raw)
	if s == "" {
		return false
	}
	any := false
	for _, line := range strings.Split(s, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		any = true
		if !strings.HasPrefix(strings.ToLower(line), "mierus://") {
			return false
		}
	}
	return any
}

// renameDefaultMieruProfile replaces clash "name" derived from profile=default with "mieru"
// (and uniquifies if several links produced the same label).
func renameDefaultMieruProfile(proxies []map[string]any) {
	defaultCount := 0
	for i := range proxies {
		n, ok := proxies[i]["name"].(string)
		if !ok {
			continue
		}
		if !strings.EqualFold(strings.TrimSpace(n), "default") {
			continue
		}
		defaultCount++
		if defaultCount == 1 {
			proxies[i]["name"] = "mieru"
		} else {
			proxies[i]["name"] = "mieru-" + strconv.Itoa(defaultCount)
		}
	}
}

func writeConfigFromMierusShare(configPath string, raw string) error {
	payload := strings.TrimSpace(raw)
	proxies, err := convert.ConvertsV2Ray([]byte(payload))
	if err != nil {
		return err
	}
	if len(proxies) == 0 {
		return fmt.Errorf("no proxies parsed from mierus share")
	}
	renameDefaultMieruProfile(proxies)
	names := make([]string, 0, len(proxies))
	for _, p := range proxies {
		n, _ := p["name"].(string)
		if n == "" {
			continue
		}
		names = append(names, n)
	}
	if len(names) == 0 {
		return fmt.Errorf("converted proxies missing names")
	}
	group := "AUTO"
	doc := map[string]any{
		"mixed-port": 7890,
		"mode":       "rule",
		"log-level":  "info",
		"proxies":    proxies,
		"proxy-groups": []map[string]any{
			{
				"name":    group,
				"type":    "select",
				"proxies": names,
			},
		},
		"rules": []string{"MATCH," + group},
	}
	out, err := yaml.Marshal(doc)
	if err != nil {
		return err
	}
	_ = os.MkdirAll(P.Dir(configPath), 0o700)
	return os.WriteFile(configPath, out, 0o600)
}
