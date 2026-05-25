package snapshot

import (
	"testing"
)

// Минимальный валидный mihomo конфиг — должен пройти UnmarshalRawConfig +
// ParseRawConfig без ошибок и без сетевых fetch'ей.
const validYAML = `
mixed-port: 7890
proxies:
  - name: node-a
    type: ss
    server: 1.2.3.4
    port: 8388
    cipher: aes-256-gcm
    password: test
proxy-groups:
  - name: GLOBAL
    type: select
    proxies:
      - DIRECT
      - node-a
rules:
  - DOMAIN,example.com,DIRECT
  - AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT
  - MATCH,GLOBAL
`

func TestValidateBytes_ReturnsEmptyOnValidConfig(t *testing.T) {
	if errMsg := ValidateBytes([]byte(validYAML)); errMsg != "" {
		t.Fatalf("expected validation to succeed, got: %q", errMsg)
	}
}

func TestValidateBytes_AcceptsLogicalRules(t *testing.T) {
	// The bug that motivated Path B was logical rules being corrupted before
	// they reached mihomo. This test confirms that a profile containing every
	// flavour of logical rule passes engine validation when it arrives whole.
	const yaml = `
proxies:
  - name: node-a
    type: ss
    server: 1.2.3.4
    port: 8388
    cipher: aes-256-gcm
    password: test
proxy-groups:
  - name: GLOBAL
    type: select
    proxies:
      - node-a
rules:
  - AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT
  - OR,((NETWORK,UDP),(DOMAIN-SUFFIX,cn)),DIRECT
  - NOT,((DOMAIN,blocked.example)),GLOBAL
  - MATCH,GLOBAL
`
	if errMsg := ValidateBytes([]byte(yaml)); errMsg != "" {
		t.Errorf("logical rules should validate, got: %q", errMsg)
	}
}

func TestValidateBytes_RejectsBrokenYAML(t *testing.T) {
	errMsg := ValidateBytes([]byte("rules: [\n  - unterminated"))
	if errMsg == "" {
		t.Fatal("expected error on broken YAML, got empty")
	}
}

func TestValidateBytes_RejectsRuleTargetingMissingGroup(t *testing.T) {
	// This is the class of error our Path B WRITE-side validation is meant to
	// catch: a structurally-valid YAML where mihomo's semantic check ("does
	// the proxy/group name exist?") will refuse the load.
	const yaml = `
proxies:
  - name: node-a
    type: ss
    server: 1.2.3.4
    port: 8388
    cipher: aes-256-gcm
    password: test
proxy-groups:
  - name: GLOBAL
    type: select
    proxies:
      - node-a
rules:
  - DOMAIN,example.com,NonexistentGroup
  - MATCH,GLOBAL
`
	errMsg := ValidateBytes([]byte(yaml))
	if errMsg == "" {
		t.Fatal("expected error for rule referencing missing group")
	}
	// We don't assert the exact message — mihomo's wording can shift between
	// versions. Non-empty error is enough to confirm the engine refused.
	t.Logf("engine rejected with: %s", errMsg)
}
