package config

import (
	"os"
	P "path"
	"runtime"
	"strings"

	"cfa/native/app"

	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/log"
)

func logDns(cfg *config.RawConfig) {
	bytes, err := yaml.Marshal(&cfg.DNS)
	if err != nil {
		log.Warnln("Marshal dns: %s", err.Error())

		return
	}

	log.Infoln("dns:")

	for _, line := range strings.Split(string(bytes), "\n") {
		log.Infoln("  %s", line)
	}
}

func UnmarshalAndPatch(profilePath string) (*config.RawConfig, error) {
	configPath := P.Join(profilePath, "config.yaml")

	configData, err := os.ReadFile(configPath)
	if err != nil {
		return nil, err
	}

	rawConfig, err := config.UnmarshalRawConfig(configData)
	if err != nil {
		return nil, err
	}

	if err := process(rawConfig, profilePath); err != nil {
		return nil, err
	}

	return rawConfig, nil
}

func Parse(rawConfig *config.RawConfig) (*config.Config, error) {
	cfg, err := config.ParseRawConfig(rawConfig)
	if err != nil {
		return nil, err
	}

	return cfg, nil
}

// stripInbounds disables everything that accepts connections, so a profile can
// be loaded only to browse proxies and run health checks while the service is
// stopped.
func stripInbounds(cfg *config.RawConfig) {
	cfg.Port = 0
	cfg.SocksPort = 0
	cfg.RedirPort = 0
	cfg.TProxyPort = 0
	cfg.MixedPort = 0
	cfg.ShadowSocksConfig = ""
	cfg.VmessConfig = ""
	cfg.Tunnels = nil
	cfg.Listeners = nil
	cfg.TuicServer.Enable = false
	cfg.DNS.Listen = ""
	cfg.ExternalController = ""
	cfg.ExternalControllerTLS = ""
	cfg.ExternalControllerUnix = ""
	cfg.ExternalControllerPipe = ""
	cfg.GeoAutoUpdate = false
}

// disableProviderHealthCheck keeps proxy providers from testing latency on
// their own.
func disableProviderHealthCheck(cfg *config.RawConfig) {
	for _, mapping := range cfg.ProxyProvider {
		if healthCheck, ok := mapping["health-check"].(map[string]any); ok {
			healthCheck["enable"] = false
		}
	}

	// providers without an interval inherit the one of a group using them
	for _, group := range cfg.ProxyGroup {
		delete(group, "interval")
	}
}

// skipGroupHealthCheck does the same for the proxies listed in groups, which
// the core always tests periodically. Initializing a compatible provider only
// starts that schedule. Groups keep their reference, so testing on request
// still works.
func skipGroupHealthCheck(cfg *config.Config) {
	for name, p := range cfg.Providers {
		if p.VehicleType() == provider.Compatible {
			delete(cfg.Providers, name)
		}
	}
}

// Load applies the profile at path to the core. A preview accepts no
// connections and only tests latency on request.
func Load(path string, preview bool) error {
	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		log.Errorln("Load %s: %s", path, err.Error())

		return err
	}

	if err := patchLanding(rawCfg, path); err != nil {
		log.Errorln("Load %s: %s", path, err.Error())

		return err
	}

	if preview {
		stripInbounds(rawCfg)
		disableProviderHealthCheck(rawCfg)
	}

	logDns(rawCfg)

	cfg, err := Parse(rawCfg)
	if err != nil {
		log.Errorln("Load %s: %s", path, err.Error())

		return err
	}

	if preview {
		skipGroupHealthCheck(cfg)
	}

	// like hub.Parse()
	hub.ApplyConfig(cfg)

	selectLandingInGlobal()

	app.ApplySubtitlePattern(rawCfg.ClashForAndroid.UiSubtitlePattern)

	runtime.GC()

	return nil
}

func LoadDefault() {
	cfg, err := config.Parse([]byte{})
	if err != nil {
		panic(err.Error())
	}

	hub.ApplyConfig(cfg)
}
