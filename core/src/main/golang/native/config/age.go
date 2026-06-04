package config

import "github.com/metacubex/mihomo/component/age"

func SetGlobalSecretKeys(secretKeys ...string) {
	age.SetGlobalSecretKeys(secretKeys...)
}
