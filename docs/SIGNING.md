# Signing & Update Compatibility

APK updates can only install **over** an existing install (keeping app data and
settings) when both APKs share the same package name and are signed by the same
key. This document explains how this repository keeps that identity stable.

## Development (debug) builds

All agent debug builds — local and CI — are signed with a **single stable
keystore**:

- Local: `~/.android/debug.keystore` (alias `androiddebugkey`, password `android`)
- CI: restored from the `AGENT_DEBUG_KEYSTORE` Actions secret into the same path

`build.gradle.kts` enforces this:

- The `agentDebug` signing config points at the stable keystore for every debug build.
- If the keystore is missing, the build **fails** with a hint instead of silently
  creating a fresh key (a new key would make the APK unable to update existing installs).
- If `-PexpectedDebugKeySha256=<hex>` is supplied, the keystore file is verified
  against that digest and the build fails on mismatch.
- The CI workflow additionally compares the built APK's certificate digest against
  the pinned stable fingerprint, so a lost/rotated key fails loudly in CI too.

### Known identity

- Certificate SHA-256: `b7123699861708e7c515c6e436ba3aea6b0e364e08dd97f8001c5052fa50380e`
- Keystore file SHA-256: `c832343ac1f8e70bc6729f132e2b0e2844ce79415a6ac6c3d2dc1394f68a438a`

Verify a downloaded APK:

```bash
keytool -printcert -jarfile path/to.apk | grep SHA256
```

## Backing up the development keystore

`~/.android/debug.keystore` is the only copy of the identity used for dev
builds. If this Mac is lost/replaced and the CI secret is also gone, future
builds cannot update phones that installed an earlier build. Back it up:

1. Keep a copy in a private, encrypted location (password manager, encrypted
   cloud drive, or a secrets vault).
2. Keep the CI secret `AGENT_DEBUG_KEYSTORE` (base64 of the keystore file) in
   sync with the backed-up copy.
3. Never regenerate `~/.android/debug.keystore` manually unless you intend to
   break updates for existing users.

## Release builds

Release builds must be signed with the **production** keystore, configured via
`signing.properties` (see `README.md`):

```properties
keystore.path=/absolute/path/to/your-release.keystore
keystore.password=<store password>
key.alias=<key alias>
key.password=<key password>
```

The build refuses to sign release variants with the debug key: `assemble*Release`
requires `signing.properties` to exist, otherwise Gradle fails with a clear error.

### Do not use the repository's `release.keystore`

A file named `release.keystore` sits in the repository root. It is inherited from
upstream history (committed in 2022, before this fork existed) and is therefore
**public**. Signing a distributed build with it would let anyone who cracks its
password publish an APK that Android accepts as an update to yours.

`keystore.path` is what decides which key is used. Point it at a key you generated
yourself and keep outside the working tree:

```bash
keytool -genkeypair -v -keystore ~/keys/cmfa-ai-release.keystore \
  -alias cmfa-ai -keyalg RSA -keysize 4096 -validity 10000
```

If `keystore.path` is missing or empty the build falls back to the committed
`release.keystore` and prints a warning. Treat that warning as an error for
anything you intend to distribute.

## Version code

`versionCode` is derived from the git history: `211032 + git rev-list --count HEAD`.
It is therefore monotonic — every later build has a strictly larger version code,
which is required for Android to allow an in-place update.

## Updating this document

If the stable keystore identity ever changes intentionally (e.g. moving to a new
production signing scheme), update the pinned fingerprint above and the CI
fingerprint check, and document the migration for users.

## Local build prerequisites

Building the Go core requires a CA bundle checked into the mihomo submodule.
The file is intentionally empty in git and must be populated before a local build:

```bash
cp /etc/ssl/cert.pem core/src/foss/golang/clash/component/ca/ca-certificates.crt
```

CI does the equivalent step (`update-ca-certificates` + copy). Remember to
re-run it after `git submodule update --init --recursive`, since a fresh
submodule checkout restores the empty file.
