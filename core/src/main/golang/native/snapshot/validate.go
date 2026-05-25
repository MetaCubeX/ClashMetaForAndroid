package snapshot

import (
	"io"

	"github.com/metacubex/mihomo/config"

	// config.ParseRawConfig declares temporaryUpdateGeneral via go:linkname;
	// the actual implementation lives in mihomo/hub/executor. Importing it
	// blank pulls the symbol into the link graph so go test (and any other
	// consumer of this package) resolves the relocation. The production
	// libclash.so build already pulls hub/executor transitively via
	// cfa/native/config -> mihomo/hub; we make it explicit here so the
	// isolated snapshot package stays self-sufficient.
	_ "github.com/metacubex/mihomo/hub/executor"
)

// ValidateBytes runs the engine's full UnmarshalRawConfig + ParseRawConfig
// pipeline on in-memory YAML and returns the engine's verbatim error message,
// or an empty string on success.
//
// This is the same parse mihomo performs at load time minus the ClashFest-
// specific processors (patchDns/patchTun/...) which mutate paths and DNS for
// the running tunnel. For "is this YAML valid?" — which is what the WRITE
// path needs before committing to disk — this is enough: rule grammar,
// proxy/group reference resolution, provider definition shape, and regex
// compilation all happen inside ParseRawConfig.
//
// Lives in the snapshot package on purpose: it stays isolated from
// cfa/native/app and its Linux-only platform helpers, so `go test` runs on
// any developer workstation.
func ValidateBytes(data []byte) string {
	rawCfg, err := config.UnmarshalRawConfig(data)
	if err != nil {
		return err.Error()
	}
	cfg, err := config.ParseRawConfig(rawCfg)
	if err != nil {
		return err.Error()
	}
	closeProviders(cfg)
	return ""
}

// closeProviders mirrors cfa/native/config.destroyProviders but lives here to
// keep the snapshot package free of the app/platform dependency. Providers
// created during ParseRawConfig hold open files / sockets that must be
// released even on success — the engine itself only does this on apply.
func closeProviders(cfg *config.Config) {
	for _, p := range cfg.Providers {
		if c, ok := p.(io.Closer); ok {
			_ = c.Close()
		}
	}
	for _, p := range cfg.RuleProviders {
		if c, ok := p.(io.Closer); ok {
			_ = c.Close()
		}
	}
}
