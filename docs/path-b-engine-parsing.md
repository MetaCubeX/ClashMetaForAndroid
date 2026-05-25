# Path B — Делегация парсинга mihomo конфига движку

> Статус: **shipped**
> Финальная архитектура и пройденный путь.

## 0. TL;DR

До Path B Kotlin сам парсил `config.yaml` через SnakeYAML + `String.split(",")`, и каждое логическое правило (`AND`, `OR`, `NOT`, `SUB-RULE`) рисковало превратиться в огрызок типа `AND,((NETWORK,UDP)` при сохранении. После Path B:

- **READ** идёт через mihomo: `Clash.parseProfileSnapshot(profileDir)` / `parseProfileSnapshotFromYaml(yaml)` → `ProfileSnapshot` data class. Никакой YAML-парсинг в Kotlin для главного конфига.
- **WRITE** остаётся в Kotlin (точечная замена top-level блоков через `MihomoConfigDocument`), но перед каждым commit'ом на диск зовётся `Clash.validateProfileBytes(yaml)` — движок-арбитр.
- **Источник истины** — YAML файл на диске. JSON от движка — derived view.

Один и тот же паттерн что у [kr328/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) для `queryProviders()`, у [FlClash](https://github.com/chen08209/FlClash) для всего READ, и у [Clash Verge](https://github.com/clash-verge-rev/clash-verge-rev) через HTTP API.

## 1. Проблема, которую закрыли

```
rules[47] [AND,((NETWORK,UDP)] error: proxy [UDP] not found
```

Root cause — [старый `RuleMapper.parseRuleLine`](https://github.com/Nemu-x/ClashFest/blob/main/service/src/main/java/com/github/kr328/clash/service/util/RuleMapper.kt) делал `t.split(",")` без учёта вложенных скобок:

```kotlin
val parts = t.split(",")        // ["AND", "((NETWORK", "UDP)", "(DST-PORT", "53))", "DIRECT"]
val type = parts[0]             // "AND"
val value = parts[1]            // "((NETWORK"   ← обрезок
val policy = parts[2]           // "UDP)"        ← обрезок
```

При обратном `toRuleLine` собиралось `"AND,((NETWORK,UDP)"` — хвост `,(DST-PORT,53)),DIRECT` терялся. Те же грабли на `OR/NOT/SUB-RULE/DOMAIN-REGEX,foo(a,b),...`.

## 2. Архитектура

```
┌──────────────────────────┐    READ      ┌──────────────────────────────┐
│ UI / RuleMapper /        │ ─────────────┤ Clash.parseProfileSnapshot     │
│ SubscriptionUpdateMerge /│              │   → JNI                       │
│ *YamlPreview helpers     │              │   → snapshot.Parse(profileDir)│
│                          │              │   → mihomo UnmarshalRawConfig │
│                          │              │   → JSON envelope             │
│                          │              └──────────────────────────────┘
│                          │
│                          │    WRITE     ┌──────────────────────────────┐
│                          │ ─────────────┤ MihomoConfigDocument         │
│                          │              │   .renderReplacing(...)      │
│                          │              │ (top-level block patcher,    │
│                          │              │  сохраняет комменты вне блок)│
│                          │              └──────────────────────────────┘
│                          │
│                          │    VALIDATE  ┌──────────────────────────────┐
│                          │ ─────────────┤ Clash.validateProfileBytes   │
│                          │              │   → JNI                       │
│                          │              │   → snapshot.ValidateBytes   │
│                          │              │   → mihomo UnmarshalRaw +    │
│                          │              │     ParseRawConfig +         │
│                          │              │     close providers           │
│                          │              └──────────────────────────────┘
└──────────────────────────┘
```

## 3. Native слой (`cfa/native/snapshot`)

Изолированный Go-пакет, **не** зависит от `cfa/native/app` и Linux-only `cfa/native/platform`. Это даёт `go test` на любой машине (Windows/macOS/Linux). Production-сборка тянет hub/executor через blank import, без него линкер не находит `temporaryUpdateGeneral` (см. `snapshot/validate.go`).

| Функция | Описание | Файл |
|---|---|---|
| `snapshot.Parse(profileDir)` | `<profileDir>/config.yaml` → `ProfileSnapshot` | `snapshot/snapshot.go` |
| `snapshot.ParseBytes(data)` | YAML в памяти → `ProfileSnapshot` | `snapshot/snapshot.go` |
| `snapshot.MarshalJSON(profileDir)` | success envelope `{"ok":true,"snapshot":...}` или error envelope `{"ok":false,"error":"..."}` | `snapshot/snapshot.go` |
| `snapshot.MarshalJSONFromBytes(data)` | то же для in-memory YAML | `snapshot/snapshot.go` |
| `snapshot.ValidateBytes(data)` | `UnmarshalRawConfig` + `ParseRawConfig` + `closeProviders`, возвращает verbatim error string или `""` | `snapshot/validate.go` |

JNI обёртки в `core/src/main/cpp/main.c`:
- `nativeParseProfileSnapshot(path)` — sync, returns JSON envelope
- `nativeParseProfileSnapshotFromBytes(yaml)` — sync, returns JSON envelope
- `nativeValidateProfileBytes(yaml)` — sync, returns `String?` (null = OK)
- `nativeValidateProfile(deferred, path)` — async, full pipeline (для активной загрузки)

## 4. Kotlin фасад

| API | Что делает |
|---|---|
| `Clash.parseProfileSnapshot(path: File): ProfileSnapshot` | Парсит профиль из директории |
| `Clash.parseProfileSnapshotFromYaml(yaml: String): ProfileSnapshot` | То же для YAML в памяти |
| `Clash.validateProfileBytes(yaml: String): String?` | null если mihomo принял, иначе verbatim error message |
| `Clash.validateProfile(path: File): CompletableDeferred<Unit>` | Async, full `process()` pipeline (используется до этого PR'а; в Path B не на горячем пути) |

`ProfileSnapshot` data class в `core/src/main/java/com/github/kr328/clash/core/model/ProfileSnapshot.kt`:
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

`JsonObject` (kotlinx.serialization) для свободных подструктур — UI читает только нужные поля (`name`, `type`, `use`...), не форсируем типизированные дата-классы под каждый proxy protocol.

## 5. Утилита `JsonElementToYaml`

Мост между Engine-parsed `JsonElement` деревьями и плоскими Map/List/scalar которые SnakeYAML дампит обратно в YAML. Нужен пока WRITE-helper'ы используют SnakeYAML для сериализации (мутации top-level блоков). Файл: `service/.../util/JsonElementToYaml.kt`.

## 6. Что мигрировано

| Слой | До | После |
|---|---|---|
| `RuleMapper.parseStateFromConfig(text)` | SnakeYAML → split-by-comma | `parseStateFromSnapshot(snapshot)` |
| `RuleMapper.parseRuleLine` для AND/OR/NOT/SUB-RULE/SCRIPT | split + reassemble (терял хвост) | Opaque types сохраняют `raw` целиком; `toRuleLine` возвращает `raw` verbatim |
| `RuleRepository.load(uuid, configText)` | принимает текст | `load(uuid, profileDir)` через snapshot |
| `SubscriptionUpdateMerge.extractPreserved(configYaml)` | SnakeYAML | `extractPreserved(snapshot)` |
| `ProxyGroupsYamlPreview.*` | SnakeYAML walks | принимают `ProfileSnapshot` |
| `ProxyTransportYamlPreview.parse` | SnakeYAML | принимает `ProfileSnapshot` (provider .yaml файлы на диске всё ещё через SnakeYAML — они standalone documents, не главный config) |
| `ProxyYamlPreview.extractProxyEntry` | SnakeYAML | принимает `ProfileSnapshot` |
| `RuleApplyService.dryRunState` write veto | `YamlPreviewSupport.validateConfigYaml` (наш shape-check) | `Clash.validateProfileBytes` (mihomo arbiter) |
| `RuleApplyService.reconcileWithStoredState` | без engine verdict | `Clash.validateProfileBytes` |
| `ProfileProcessor` после `mergeAfterFetch` | без проверки | `Clash.validateProfileBytes`; при отказе остаётся fetched-only |

## 7. Что осталось в SnakeYAML (intentionally)

- `MihomoConfigDocument` (WRITE): mutates top-level Map/List, дампит через SnakeYAML, точечно заменяет блок в исходном тексте (комменты вне блока сохраняются).
- `RuleMapper.parseProvidersYaml(yamlText)`: parsing **partial** YAML (`rule-providers:` блок отдельно от полного config) — мы не можем дёрнуть `parseProfileSnapshot` на огрызке.
- `YamlPreviewSupport.validateConfigYaml`: lightweight shape check для UI previews edits (любой partial YAML).
- Provider .yaml файлы внутри `ProxyGroupsYamlPreview.collectAllLeafProxyNames` и `ProxyTransportYamlPreview.collectProviderProxies` — они **standalone** documents (просто `proxies:` список), не main config; mihomo snapshot их не загружает.
- `GeoUrlSanitizer.sanitizeYaml`: импорт-хардненинг до того как профиль попадёт в processingDir; чисто текстовая правка.

## 8. Инфраструктура тестов

- **Go**: `go test -tags "foss,with_gvisor,cmfa" ./native/snapshot/...` — покрытие `Parse`, `ParseBytes`, `MarshalJSON`, `ValidateBytes`. Gradle hook `goTestNativeSnapshot` запускает их перед каждым `compileJava`, любая регрессия в snapshot пакете → красная сборка.
- **Kotlin** (без JNI): `RuleMapperTest`, `SubscriptionUpdateMergeTest`, `ProxyGroupsYamlPreviewTest`, `ProxyYamlPreviewTest`, `ProxyTransportYamlPreviewTest`, `ProfileSnapshotEnvelopeTest`. Подают `ProfileSnapshot` вручную — purely unit, mock JNI не нужен.

## 9. Этапы (для истории)

| Шаг | Что добавлено | Главный эффект |
|---|---|---|
| **0** — PR #29 | `MihomoConfigDocument` для WRITE + `Clash.validateProfile` infrastructure | Точечная замена блоков, комменты сохраняются |
| **1** | `snapshot` Go package + `parseProfileSnapshot` JNI + `ProfileSnapshot` Kotlin | Native READ path появился |
| **1.5** | `goTestNativeSnapshot` Gradle hook | Go тесты прогоняются перед каждой сборкой |
| **2.1** | `RuleMapper` мигрирован на snapshot, `parseRuleLine` opaque-type-aware, `toRuleLine` уважает `raw` | **Боевой баг с logical rules закрыт** |
| **2.2** | `SubscriptionUpdateMerge.extractPreserved` через snapshot | Manual rules не теряются при subscription refresh |
| **2.3a** | `parseProfileSnapshotFromBytes` для in-memory YAML | Нужно для dry-run и validate-before-write |
| **2.3b** | Все preview-helper'ы через snapshot | Group picker / transport badges / proxy preview не страдают от YAML квотинга |
| **3** | `validateProfileBytes` + `RuleApplyService` validates перед write + `ProfileProcessor` валидирует merged subscription | Невозможно записать на диск конфиг, который mihomo не примет |
| **4** | Удалены READ-getter'ы `MihomoConfigDocument` + `validateMergedYaml` (дублировал native validate) | Mihомное `MihomoConfigDocument` теперь чистый WRITE-only хелпер |

## 10. Открытые вопросы / будущая работа

- **Performance**: `parseProfileSnapshot` ~10-50ms на крупном конфиге. Если UI начнёт триггерить её часто — кешировать на уровне `RuleRepository` с инвалидацией по `config.yaml.lastModified`.
- **rule-providers blob editor** (`ProfileManager.previewYaml*`) всё ещё использует `YamlPreviewSupport.validateConfigYaml` для partial YAML. Если потребуется engine-grade validation для partial — нужен новый native API `validatePartialBlock(key, yaml)`.
- **`SUBSCRIPTION_OWNED_RULE_TYPES` в `SubscriptionUpdateMerge`** включает AND/OR/NOT — это **дизайн-решение**, не баг (логические правила считаем "принадлежат подписке"). Если пользователь жалуется что его manual AND пропал после refresh — обсуждать отдельно.

## Ссылки

- mihomo `RawConfig` shape: `core/src/foss/golang/clash/config/config.go:392+`
- mihomo `temporaryUpdateGeneral` linkname trick: `core/src/foss/golang/clash/config/config.go:738-739` + `hub/executor/executor.go:382-383`
- Эталон апстрима kr328: `core/src/main/golang/native/tunnel/providers.go`
- AGENTS.md §6 (источник истины — файл на диске): [AGENTS.md](../AGENTS.md)
