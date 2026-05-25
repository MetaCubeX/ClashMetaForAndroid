# Path B — Delegating mihomo Config Parsing to the Engine

> Status: **shipped**
> Final architecture reference. Pull from this when touching anything that
> reads or writes `config.yaml`.

## 0. TL;DR

Before Path B, Kotlin parsed `config.yaml` with SnakeYAML + naive
`String.split(",")`. Every logical rule (`AND`, `OR`, `NOT`, `SUB-RULE`) was
one save away from being corrupted into something like `AND,((NETWORK,UDP)`
and getting rejected by mihomo at load time with `proxy [UDP] not found`.

After Path B:

- **READ** goes through mihomo via `Clash.parseProfileSnapshot(profileDir)` /
  `parseProfileSnapshotFromYaml(yaml)` and returns a `ProfileSnapshot` data
  class. Kotlin no longer parses the main config YAML.
- **WRITE** stays in Kotlin (top-level block replacement via
  `MihomoConfigDocument`), but before any commit to disk we ask the engine
  via `Clash.validateProfileBytes(yaml)` and log its verdict.
- **Source of truth** stays the YAML file on disk. The engine's JSON is a
  derived view.

This is the same pattern [kr328/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)
already uses for `queryProviders()`, [FlClash](https://github.com/chen08209/FlClash)
uses for every read, and [Clash Verge](https://github.com/clash-verge-rev/clash-verge-rev)
uses via the mihomo HTTP API. We adopted it for the same reasons.

## 1. The Problem We Closed

```
rules[47] [AND,((NETWORK,UDP)] error: proxy [UDP] not found
```

Root cause — the old `RuleMapper.parseRuleLine` split by comma without
honouring nested parens:

```kotlin
val parts = t.split(",")        // ["AND", "((NETWORK", "UDP)", "(DST-PORT", "53))", "DIRECT"]
val type = parts[0]             // "AND"
val value = parts[1]             // "((NETWORK"   ← truncated
val policy = parts[2]            // "UDP)"        ← truncated
```

`toRuleLine` then re-emitted `"AND,((NETWORK,UDP)"` on save — the tail
`,(DST-PORT,53)),DIRECT` was gone. Same trap on `OR/NOT/SUB-RULE`, and on
`DOMAIN-REGEX,foo(a,b),...`.

## 2. Architecture

```
┌──────────────────────────┐    READ      ┌──────────────────────────────┐
│ UI / RuleMapper /        │ ─────────────┤ Clash.parseProfileSnapshot   │
│ SubscriptionUpdateMerge /│              │   → JNI                      │
│ *YamlPreview helpers     │              │   → snapshot.Parse(profileDir)│
│                          │              │   → mihomo UnmarshalRawConfig│
│                          │              │   → JSON envelope            │
│                          │              └──────────────────────────────┘
│                          │
│                          │    WRITE     ┌──────────────────────────────┐
│                          │ ─────────────┤ MihomoConfigDocument         │
│                          │              │   .renderReplacing(...)      │
│                          │              │ (top-level block patcher,    │
│                          │              │  preserves comments outside  │
│                          │              │  the touched block)          │
│                          │              └──────────────────────────────┘
│                          │
│                          │    VALIDATE  ┌──────────────────────────────┐
│                          │ ─────────────┤ Clash.validateProfileBytes   │
│                          │              │   → JNI                      │
│                          │              │   → snapshot.ValidateBytes   │
│                          │              │   → mihomo UnmarshalRaw +    │
│                          │              │     ParseRawConfig +         │
│                          │              │     closeProviders           │
│                          │              └──────────────────────────────┘
└──────────────────────────┘
```

## 3. Native Layer (`cfa/native/snapshot`)

Isolated Go package that **does not** depend on `cfa/native/app` or the
Linux-only `cfa/native/platform`. That keeps `go test` runnable on any
developer workstation. The production build pulls `mihomo/hub/executor`
transitively; the snapshot package adds a blank import of the same package
so the linker can resolve the `temporaryUpdateGeneral` `go:linkname` symbol
when running tests standalone.

| Function | Description | File |
|---|---|---|
| `snapshot.Parse(profileDir)` | `<profileDir>/config.yaml` → `ProfileSnapshot` | `snapshot/snapshot.go` |
| `snapshot.ParseBytes(data)` | In-memory YAML → `ProfileSnapshot` | `snapshot/snapshot.go` |
| `snapshot.MarshalJSON(profileDir)` | Success envelope `{"ok":true,"snapshot":...}` or error envelope `{"ok":false,"error":"..."}` | `snapshot/snapshot.go` |
| `snapshot.MarshalJSONFromBytes(data)` | Same for in-memory YAML | `snapshot/snapshot.go` |
| `snapshot.ValidateBytes(data)` | `UnmarshalRawConfig` + `ParseRawConfig` + `closeProviders`, returns verbatim error string or `""` | `snapshot/validate.go` |

JNI wrappers in `core/src/main/cpp/main.c`:
- `nativeParseProfileSnapshot(path)` — sync, returns JSON envelope.
- `nativeParseProfileSnapshotFromBytes(yaml)` — sync, returns JSON envelope.
- `nativeValidateProfileBytes(yaml)` — sync, returns `String?` (null on success).
- `nativeValidateProfile(deferred, path)` — async, full pipeline (pre-existing, used by the active-config load path).

### Wire envelope shape

`ProfileSnapshot` is marshalled with every field tagged `,omitempty` because
mihomo's `json.Marshal` emits literal `null` for nil maps and slices and
kotlinx.serialization will not coerce `null` into a non-nullable Map/List
with a default value. Belt and braces: the Kotlin decoder also sets
`coerceInputValues = true` so any `null` that still slips through gets
coerced into the default. Both layers are tested.

## 4. Kotlin Facade

| API | What it does |
|---|---|
| `Clash.parseProfileSnapshot(path: File): ProfileSnapshot` | Parses a profile from a directory |
| `Clash.parseProfileSnapshotFromYaml(yaml: String): ProfileSnapshot` | Same for an in-memory YAML string |
| `Clash.validateProfileBytes(yaml: String): String?` | `null` if mihomo accepts the YAML, otherwise mihomo's verbatim error |
| `Clash.validateProfile(path: File): CompletableDeferred<Unit>` | Async, runs the full `process()` pipeline (used by the runtime apply path, not on the Path B hot read/write path) |

`ProfileSnapshot` data class lives in
`core/src/main/java/com/github/kr328/clash/core/model/ProfileSnapshot.kt`:

```kotlin
@Serializable
data class ProfileSnapshot(
    val rules: List<String> = emptyList(),
    @SerialName("sub-rules") val subRules: Map<String, List<String>> = emptyMap(),
    val proxies: List<JsonObject> = emptyList(),
    @SerialName("proxy-groups") val proxyGroups: List<JsonObject> = emptyList(),
    @SerialName("proxy-providers") val proxyProviders: Map<String, JsonObject> = emptyMap(),
    @SerialName("rule-providers") val ruleProviders: Map<String, JsonObject> = emptyMap(),
    val listeners: List<JsonObject> = emptyList(),
)
```

Free-form substructures (proxies, groups, providers) stay as
`JsonObject`. The UI only reads the few fields it cares about (`name`,
`type`, `use`, ...) — we don't force a typed data class per proxy protocol.

## 5. `JsonElementToYaml` Bridge

Adapter between engine-parsed `JsonElement` trees and the plain
Map/List/scalar trees SnakeYAML can dump. Required because the WRITE helpers
still serialize through SnakeYAML when replacing top-level blocks. Lives at
`service/.../util/JsonElementToYaml.kt`.

## 6. What Migrated

| Layer | Before | After |
|---|---|---|
| `RuleMapper.parseStateFromConfig(text)` | SnakeYAML → split-by-comma | `parseStateFromSnapshot(snapshot)` |
| `RuleMapper.parseRuleLine` for AND/OR/NOT/SUB-RULE/SCRIPT | Split + reassemble (tail lost) | Opaque types keep `raw` whole; `toRuleLine` returns `raw` verbatim |
| `RuleRepository.load(uuid, configText)` | Took the text | `load(uuid, profileDir)` via snapshot |
| `SubscriptionUpdateMerge.extractPreserved(configYaml)` | SnakeYAML | `extractPreserved(snapshot)` |
| `ProxyGroupsYamlPreview.*` | SnakeYAML walks | Takes `ProfileSnapshot` |
| `ProxyTransportYamlPreview.parse` | SnakeYAML | Takes `ProfileSnapshot` (standalone provider .yaml files on disk still use SnakeYAML — they're not full configs) |
| `ProxyYamlPreview.extractProxyEntry` | SnakeYAML | Takes `ProfileSnapshot` |
| `RuleApplyService.dryRunState` write check | `YamlPreviewSupport.validateConfigYaml` shape check | `Clash.validateProfileBytes` engine check (soft — see §8) |
| `RuleApplyService.reconcileWithStoredState` | No engine verdict | `Clash.validateProfileBytes` (soft) |
| `ProfileProcessor` after `mergeAfterFetch` | No check | `Clash.validateProfileBytes` (soft) |

## 7. What Stays on SnakeYAML (Intentionally)

- `MihomoConfigDocument` (WRITE block patcher): mutates the root Map, dumps
  through SnakeYAML, replaces only the targeted block in the original text
  so comments outside the block survive byte-for-byte.
- `RuleMapper.parseProvidersYaml(yamlText)`: parses a **partial** YAML
  fragment (a single `rule-providers:` block from a UI textarea) —
  `parseProfileSnapshot` would reject that as not-a-full-config.
- `YamlPreviewSupport.validateConfigYaml`: lightweight shape check for UI
  diff previews of partial YAML.
- Provider `.yaml` files on disk inside
  `ProxyGroupsYamlPreview.collectAllLeafProxyNames` and
  `ProxyTransportYamlPreview.collectProviderProxies`: those are standalone
  provider documents (just a `proxies:` list), not full configs, and the
  engine snapshot does not load them.
- `GeoUrlSanitizer.sanitizeYaml`: import-time hardening before the profile
  reaches `processingDir`; pure text manipulation.

## 8. Engine Veto Is a Warning, Not a Hard Block

`Clash.validateProfileBytes` runs `UnmarshalRawConfig` + `ParseRawConfig` +
`closeProviders`. `ParseRawConfig` also loads provider files (`.mrs` /
`.yaml` from `<profileDir>/ruleset/...`), which means a broken provider on
disk in the active profile would refuse the entire validate call with an
error like `file must have a 'payload' field`. A hard veto on that would
block any unrelated edit (toggle, delete, subscription refresh) until the
user fixed the provider — wrong tradeoff.

Behaviour:
- Validation runs in three places: `RuleApplyService.dryRunState`,
  `RuleApplyService.reconcileWithStoredState`, `ProfileProcessor`'s
  subscription merge.
- A non-null result from mihomo is **logged at WARN level** and the write
  continues.
- The runtime apply path (`Clash.load(path)` after the file is on disk)
  still fails loudly if the config is genuinely unloadable. That's the right
  place to surface a provider/file issue: the user fixes the provider, not
  their edits.

## 9. Rule Source Classification

`parseStateFromSnapshot` overrides the parseRuleLine default
(`RuleSource.MANUAL`) to `PROVIDER` for any rule whose type is in
`SUBSCRIPTION_OWNED_RULE_TYPES`:

```kotlin
private val SUBSCRIPTION_OWNED_RULE_TYPES = setOf(
    "RULE-SET", "GEOSITE", "GEOIP", "MATCH",
    "SUB-RULE", "AND", "OR", "NOT",
)
```

These types have no UI add form — when they appear in `config.yaml` they
necessarily came from the subscription. Marking them MANUAL caused
`syncProviderRules` to retain stale entries across subscription refreshes
that no longer contained them (e.g. `GEOSITE,category-ads-all,REJECT`
surviving an update that deleted it).

User-addable types (`DOMAIN`, `DOMAIN-SUFFIX`, `IP-CIDR`, ...) keep MANUAL
when read from `config.yaml`, which is what lets manual entries survive a
subscription refresh as expected.

The same set lives in `SubscriptionUpdateMerge.SUBSCRIPTION_OWNED_RULE_TYPES`
by intent: those are also the types `mergeRulesLists` drops from preserved
overlays. Both files own a copy to keep the dependency direction clean.

## 10. Provider `format` Round-Trip

`RuleProviderItem` carries a `format` field (empty = mihomo's default
`yaml`). Before the round-trip was added, any UI save rewrote
`rule-providers:` with only five fields and silently stripped `format: mrs`,
which made mihomo parse the `.mrs` binary as classical YAML and fail with
`file must have a 'payload' field` on the next refresh.

`mergeStateIntoConfig` writes `format:` back when non-empty (clean configs
that never had it stay clean).

### Legacy recovery via `inferFormat`

For configs already stripped by the old write path, `RuleMapper.inferFormat`
restores the format heuristically on read: if the `format` field is empty
but `path` or `url` ends with `.mrs`, we fill in `mrs`. Never overrides an
explicit declaration. Only `.mrs` is recovered — `.txt` / `.list` could be
either text or classical-yaml so we don't guess there.

## 11. Provider GC on Subscription Refresh

After `mergeAfterFetch` overlays the pre-fetch state on top of the fresh
subscription, a GC pass drops anything nothing references:
- `rule-providers.X` is dropped when no rule matches `RULE-SET,X,...`.
- `proxy-providers.Y` is dropped when no proxy-group uses it via `use:` —
  unless any group declares `include-all-providers: true` or
  `include-all: true`, in which case mihomo expands the universe at runtime
  and all providers are implicitly used (we keep them all).

Why: previously a provider deleted in a new subscription survived locally
because of "preserved wins" merge semantics, so mihomo kept refetching it
every `interval:` for nothing, the UI listed dead entries, and broken
providers (e.g. mismatched `.mrs` declared as classical-yaml) continued to
flood logs. A provider exists in a profile to back at least one rule or one
group; if nothing references it, it's dead weight.

Edge case for users who add a "draft" provider through the UI textarea
without yet wiring it into a rule: it will be dropped on the next refresh.
To survive, the provider must be referenced by at least one rule.

## 12. Test Infrastructure

- **Go**: `go test -tags "foss,with_gvisor,cmfa" ./native/snapshot/...` —
  covers `Parse`, `ParseBytes`, `MarshalJSON`, `ValidateBytes`. Gradle hook
  `goTestNativeSnapshot` runs them before every `compileJava`; any
  regression in the snapshot package fails the build before Kotlin compiles.
- **Kotlin** (no JNI required): `RuleMapperTest`,
  `SubscriptionUpdateMergeTest`, `ProxyGroupsYamlPreviewTest`,
  `ProxyYamlPreviewTest`, `ProxyTransportYamlPreviewTest`,
  `ProfileSnapshotEnvelopeTest`. They feed `ProfileSnapshot` instances
  directly — pure unit tests, no JNI mock needed.

## 13. Step-by-Step History (For the Record)

| Step | What landed | Main effect |
|---|---|---|
| **0** — PR #29 | `MihomoConfigDocument` for WRITE + `Clash.validateProfile` infrastructure | Top-level block replacement; comments survive |
| **1** | `snapshot` Go package + `parseProfileSnapshot` JNI + `ProfileSnapshot` Kotlin | Native READ path exists |
| **1.5** | `goTestNativeSnapshot` Gradle hook | Go tests run before every build |
| **2.1** | `RuleMapper` on snapshot, opaque-type-aware `parseRuleLine`, `toRuleLine` respects `raw` | **Logical-rule corruption closed** |
| **2.2** | `SubscriptionUpdateMerge.extractPreserved` on snapshot | Manual rules survive subscription refresh |
| **2.3a** | `parseProfileSnapshotFromBytes` for in-memory YAML | Needed for dry-run and validate-before-write |
| **2.3b** | All preview helpers on snapshot | Group picker / transport badges / proxy preview no longer suffer YAML-quoting quirks |
| **3** | `validateProfileBytes` + write-path veto | Caught more issues earlier — later softened to a warning, see §8 |
| **4** | Removed dead READ getters from `MihomoConfigDocument` + redundant `validateMergedYaml` | `MihomoConfigDocument` is now WRITE-only |
| **Hotfix** | `omitempty` + `coerceInputValues` for null envelope sections | Rules screen crash on profiles without `proxy-providers` block |
| **Hotfix** | Soft engine veto (§8) | Existing broken providers no longer block edits |
| **Hotfix** | Subscription-owned source override (§9) | Stale GEOSITE/GEOIP entries no longer survive refresh |
| **Hotfix** | `RuleProviderItem.format` round-trip + `inferFormat` recovery (§10) | `.mrs` providers no longer break after any UI save |
| **Hotfix** | GC unused providers on merge (§11) | Subscription refresh leaves a clean config |

## 14. Known Future Work

- **Performance**: `parseProfileSnapshot` is ~10–50ms on a large config. If
  the UI ever starts triggering it on hot paths, cache the result in
  `RuleRepository` keyed by `config.yaml.lastModified`.
- **Partial YAML validation**: `ProfileManager.previewYaml*` still uses
  `YamlPreviewSupport.validateConfigYaml` for partial blocks
  (rule-providers blob edits). If we want engine-grade validation for
  fragments, a new native `validatePartialBlock(key, yaml)` API would be
  needed — currently low priority.
- **`SUBSCRIPTION_OWNED_RULE_TYPES` includes AND/OR/NOT** in
  `SubscriptionUpdateMerge`. That's a deliberate policy decision (treat
  logical rules as subscription-owned), not a bug. Revisit if users
  complain that a manual AND was dropped after a refresh.

## References

- mihomo `RawConfig` shape: `core/src/foss/golang/clash/config/config.go:392+`
- mihomo `temporaryUpdateGeneral` linkname trick:
  `core/src/foss/golang/clash/config/config.go:738-739` +
  `hub/executor/executor.go:382-383`
- Upstream kr328 reference implementation:
  `core/src/main/golang/native/tunnel/providers.go QueryProviders()`
- AGENTS.md §6 (source of truth = file on disk): [AGENTS.md](../AGENTS.md)
