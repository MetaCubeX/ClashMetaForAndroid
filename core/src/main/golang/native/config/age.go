package config

import (
	"bytes"
	"os"

	"github.com/metacubex/mihomo/component/age"
)

// SetGlobalSecretKeys installs (or clears, when called with no arguments) the
// process-global age identities mihomo uses to transparently decrypt
// age-encrypted configs and provider files (see UnmarshalRawConfig and
// adapter/provider). ClashFest also decrypts the freshly fetched subscription
// on disk right after download (fetch.go decryptConfigInPlace) so the Kotlin
// overlay/composer pipeline always sees plain YAML.
func SetGlobalSecretKeys(secretKeys ...string) {
	age.SetGlobalSecretKeys(secretKeys...)
}

// decryptConfigInPlace rewrites an age-encrypted file with its decrypted
// plaintext, using the global secret keys. A plain (non-age) file is left
// untouched — DecryptBytes passes non-armor data through unchanged. A missing
// key (or wrong key) surfaces as the engine's "decrypt config error" so the
// import fails loudly instead of handing armor bytes to the YAML pipeline.
func decryptConfigInPlace(path string) error {
	data, err := os.ReadFile(path)
	if err != nil {
		return err
	}

	decrypted, err := age.DecryptBytes(data)
	if err != nil {
		return err
	}

	if bytes.Equal(decrypted, data) {
		return nil
	}

	return os.WriteFile(path, decrypted, 0600)
}

func GenX25519KeyPair() (secretKey string, publicKey string, err error) {
	return age.GenX25519KeyPair()
}

func GenHybridKeyPair() (secretKey string, publicKey string, err error) {
	return age.GenHybridKeyPair()
}

func ToPublicKeys(secretKeys ...string) (publicKeys []string, err error) {
	return age.ToPublicKeys(secretKeys...)
}

func VeritySecretKeys(secretKeys ...string) error {
	return age.VeritySecretKeys(secretKeys...)
}

func VerityPublicKeys(publicKeys ...string) error {
	return age.VerityPublicKeys(publicKeys...)
}
