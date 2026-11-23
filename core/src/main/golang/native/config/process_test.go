package config

import (
	"testing"

	mihomoConfig "github.com/metacubex/mihomo/config"
)

func TestPatchExternalControllerStripsProfileSecurityState(t *testing.T) {
	cfg := &mihomoConfig.RawConfig{
		ExternalController:     "0.0.0.0:9090",
		ExternalControllerTLS:  "0.0.0.0:9443",
		ExternalControllerUnix: "/data/local/tmp/clash.sock",
		ExternalControllerPipe: "clash-pipe",
		ExternalControllerCors: mihomoConfig.RawCors{
			AllowOrigins:        []string{"https://attacker.example"},
			AllowPrivateNetwork: true,
		},
		Secret: "profile-controlled-secret",
		DNS: mihomoConfig.RawDNS{
			Enable: true,
			Listen: ":1053",
		},
	}

	if err := patchExternalController(cfg, ""); err != nil {
		t.Fatalf("patchExternalController failed: %v", err)
	}

	if cfg.ExternalController != "" || cfg.ExternalControllerTLS != "" ||
		cfg.ExternalControllerUnix != "" || cfg.ExternalControllerPipe != "" {
		t.Fatal("profile-controlled external controller endpoint was preserved")
	}
	if len(cfg.ExternalControllerCors.AllowOrigins) != 0 || cfg.ExternalControllerCors.AllowPrivateNetwork {
		t.Fatal("profile-controlled external controller CORS was preserved")
	}
	if cfg.Secret != "" {
		t.Fatal("profile-controlled external controller secret was preserved")
	}
	if cfg.DNS.Listen != "" {
		t.Fatal("profile-controlled DNS listener was preserved")
	}
}

func TestPatchOverrideCanRestoreTrustedSecurityState(t *testing.T) {
	cfg := &mihomoConfig.RawConfig{
		ExternalController: "0.0.0.0:9090",
		Secret:             "profile-controlled-secret",
		DNS: mihomoConfig.RawDNS{
			Enable: true,
			Listen: ":1053",
		},
	}

	if err := patchExternalController(cfg, ""); err != nil {
		t.Fatalf("patchExternalController failed: %v", err)
	}

	WriteOverride(OverrideSlotSession, `{
		"external-controller":"127.0.0.1:9090",
		"external-controller-cors":{
			"allow-origins":["https://dashboard.example"],
			"allow-private-network":true
		},
		"secret":"trusted-secret",
		"dns":{"listen":"127.0.0.1:1053"}
	}`)
	defer ClearOverride(OverrideSlotSession)

	if err := patchOverride(cfg, ""); err != nil {
		t.Fatalf("patchOverride failed: %v", err)
	}

	if cfg.ExternalController != "127.0.0.1:9090" || cfg.Secret != "trusted-secret" {
		t.Fatal("trusted external controller override was not restored")
	}
	if len(cfg.ExternalControllerCors.AllowOrigins) != 1 ||
		cfg.ExternalControllerCors.AllowOrigins[0] != "https://dashboard.example" ||
		!cfg.ExternalControllerCors.AllowPrivateNetwork {
		t.Fatal("trusted external controller CORS override was not restored")
	}
	if cfg.DNS.Listen != "127.0.0.1:1053" {
		t.Fatal("trusted DNS listener override was not restored")
	}
}
