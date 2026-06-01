# AGENTS.md — Правила работы AI-агентов в ClashFest

Этот файл — обязательное руководство для любого AI-агента (Copilot, Codex, Cursor, Claude Code и т. п.), работающего в этом репозитории. Игнорировать эти правила нельзя.

## 0. Контекст проекта

ClashFest — Android-клиент на базе ядра **Clash.Meta (mihomo)**.

Цепочка форков:
```
xuhaoyang/ClashForAndroid
└── MetaCubeX/ClashMetaForAndroid (ядро + базовый клиент, remote `upstream`)
    └── Nemu-x/ClashFest (ребрендинг, UI-редизайн)  ← мы здесь (origin)
```
`Rerowros/ClashFest` — параллельный форк, подключён отдельным remote `rerowros` (не вверх по цепочке).

Активная ветка разработки: `feat/init-clashfest`.

Основные модули:
- `app/` — точки входа: `MainActivity`, `ExternalControlActivity`, deeplink-обработчики.
- `common/` — общие утилиты, константы, логирование.
- `core/` — мост к Go-ядру **mihomo** (submodule `core/src/foss/golang/clash`).
- `design/` — UI: XML-layouts, кастомные `View`, адаптеры, превью, диалоги. **Compose не используется.**
- `service/` — `TunService` (`VpnService`), `ClashRuntime` модули, импорт/обработка профилей, `ServiceStore`.
- `hideapi/` — заглушки скрытых API.

## 1. Цели проекта (приоритеты)

1. **Безопасность пользователя по умолчанию.** Любая утечка трафика, любой открытый локальный порт без авторизации, любой прямой доступ приложений к `tun0` — это баг приоритета P0.
2. **Простота для рядового пользователя.** «Скачал → импортировал подписку → нажал Connect → защищён».
3. **Сохранение совместимости** с конфигами Clash.Meta и подписками формата `clash.yaml`.
4. **Не ломать апстрим.** Изменения проектируются так, чтобы их можно было поддерживать поверх Nemu-x/MetaCubeX.

## 2. Безопасность (обязательно)

При обработке профилей и сетевых настроек агент **обязан**:

- **Санитизировать импортируемый YAML.** Два слоя: file-level `service/util/YamlHardener.kt` (при импорте/обновлении подписки) + override `service/util/ProxyHardener.kt` (рантайм), оба вызываются из `ProfileProcessor`:
  - Глобальные порты `port`/`socks-port`/`mixed-port`/`redir-port`/`tproxy-port` гасятся (в Strict — в `0`) через `ConfigurationOverride`.
  - `listeners:` в Strict — удаляются целиком; в Compat — каждый `listen:` принудительно биндится к `127.0.0.1`.
  - Принудительно `allow-lan: false` (если был явно `true`), `bind-address` и `external-controller` — к `127.0.0.1`.
  - Поведение управляется `ProxyHardeningMode` в `ServiceStore`: `Strict` (default на Android 10+), `Compat` (default ниже 10), `Off`.
- **Не открывать SOCKS5 без auth наружу никогда.** SOCKS5 без `authentication` на Android 10+ отключён по умолчанию (фикс уязвимости: `https://publish.obsidian.md/zapret/VLESS-SOCKS5-vulnerability`).
- **Защита tun0 от чужих UID — на уровне ОС, не в ядре.** Гейт доступа — per-app VPN: `TunService` через `addAllowedApplication`/`addDisallowedApplication` (по `accessControlMode`/`accessControlPackages`) задаёт, чей трафик вообще попадает в туннель. `ConnectivityManager.getConnectionOwnerUid()` (в `TunModule.queryUid`) НЕ является гейтом — он лишь резолвит имя пакета (`app.QueryAppByUid`) для правил `PROCESS-NAME` и логов; `uid == -1` (lookup не удался / API<29) означает «пакет не определён», а не «разрешить».
- **Внешнее управление VPN** (`ExternalControlActivity`, экшены `START/STOP/TOGGLE_CLASH`) гейтится настройкой `allowExternalControl` (default on); внешний стоп показывает нотификацию (не сайлентный). Экшены — в неймспейсе `${applicationId}.action.*` (а не upstream `com.github.metacubex.clash.meta.action.*`).
- **Любые сетевые загрузки** (geoip/geosite/подписки) — только с доверенных хостов из allowlist (см. п. 4), fail-closed, с таймаутами + ретраями.

## 3. Стиль кода

- Kotlin для Android-кода, Go для ядра.
- Соблюдать существующий стиль модуля; не переформатировать чужие файлы целиком.
- Никаких лишних рефакторингов «по дороге». Минимально-инвазивные правки.
- Не добавлять docstring/комментарии к коду, который не меняется.
- Имена пакетов сохраняются: `com.github.kr328.clash.*` (не переименовывать — ломает интеграции).
- Строки — только через `strings.xml`. Обязательны три языка: EN (`values/strings.xml`), RU (`values-ru/strings.xml`) и упрощённый китайский (`values-zh/strings.xml`). Все остальные локали (включая `values-zh-rTW/`, `values-zh-rHK/` и прочие) — опциональны; апдейтить их при изменениях не обязательно.
- Логирование — через `com.github.kr328.clash.common.log.Log`, не `android.util.Log`.

## 4. Сеть и внешние ресурсы

`geox-url` принимается **только из allowlist доверенных хостов** (`GeoMirrors.TRUSTED_HOSTS`, derived из курируемых mirror-списков) — **fail-closed**: любой хост вне allowlist (включая пустой/битый) переписывается на доверенный primary. Это заменило прежний denylist. Доверенные хосты:

```
github.com
raw.githubusercontent.com
cdn.jsdelivr.net
fastly.jsdelivr.net
```
(см. `service/util/GeoMirrors.kt` — единственный источник истины; `service/util/GeoUrlSanitizer.kt` и `ProxyHardener.kt` используют `isTrusted`/`primaryFor`.)

При импорте подписки агент **обязан**:
1. Любой `geox-url` с недоверенного хоста — переписать на доверенный primary (а не «проверять валидность»); пустой/малформед — тоже fail-closed на primary.
2. Bundled geo-ассеты (`geoip.metadb`, `geoip.dat`, …) ставятся в `files/clash/` через `ensureBundledGeoAssets` (`GeoAssetInstaller`); периодический онлайн-апдейт ядром гасится (`geo-auto-update: false` в `GeoUrlSanitizer`).
3. Никогда не блокировать UI-поток на сетевых операциях.

## 5. UI / UX правила

- Дизайн-система: **Material Design 3** (Material Components, `Theme.Material3.*`). Compose не вводим в этой итерации.
- **Единый режим интерфейса** — без разделения Simple/Advanced. Всё должно быть доступно из главного экрана и Settings.
- **Нижняя навигация на главном экране — ровно 4 кнопки:**
  1. **Подписки** (`OpenProfiles` → `ProfilesActivity`) — управление списком подписок и серверов.
  2. **Логи** (`OpenLogs` → `LogsActivity`) — live-логи ядра.
  3. **Правила** (`OpenRouting` → `RoutingHubActivity`) — единая ветка маршрутизации: rule snippets + active rules + per-app routing + proxy chain.
  4. **Соединения** (`OpenConnections` → `ConnectionsActivity`) — live-просмотр потоков.
- Выбор серверов / групп mihomo живёт inline в карточках профилей на главном экране (раскрытие активного профиля показывает группы со spinner-ом, выбор узла, URL-test).
- Отдельной страницы `ProxyActivity` больше нет — функциональность перенесена в раскрываемые карточки.
- Кнопка «О приложении» **только** внутри Settings (`SettingsDesign.Request.StartAbout`), не на главном экране.
- Per-app routing: один экран со списком приложений (иконка+имя+чекбокс), вверху сегментированный переключатель Allow-list / Block-list / All, поиск, сортировка, кнопка «Сбросить». Доступ — через «Правила» → «По приложениям» (а также через Settings → Network → Access Control Packages для совместимости).

## 6. Изменения в YAML/конфигах

- Источник истины — текст профиля на диске. «Hardening»-логика применяется как пост-процессинг при сохранении/обновлении профиля (`YamlHardener.hardenProfile`).
- Оригинальный пользовательский YAML сохраняется рядом как `config.original.yaml` (создаётся при первой правке, далее не перезаписывается) для возможности восстановления.

## 7. Git / Workflow

- Базовая ветка: `feat/init-clashfest`.
- Каждая логическая задача — отдельный коммит с conventional-стилем:
  - `feat(security): harden imported profile against socks5 leak`
  - `fix(geo): fallback mirrors for geoip download`
  - `ui(nav): merge rules+routing into single hub screen`
- Никаких force-push, rebase или удаления чужих коммитов без явного запроса пользователя.
- Submodules (`core/src/foss/golang/clash`) трогаем только при необходимости — обновление ядра делается отдельным PR. Текущее ядро запинено на `v1.19.26`. При bump'е: `go mod tidy` в `core/src/main/golang` и `core/src/foss/golang`, и учитывай, что golang-таск не трекает submodule как input → закэшированная `libclash.so` может не пересобраться (чистить `core/build` при необходимости).
- **Сборка требует JDK 21** (Java/Kotlin target 21). Старый JDK падает с `invalid source release: 21` — в Android Studio выставить Gradle JDK 21, в CLI `JAVA_HOME` на JDK 21.
- Перед push агент должен убедиться, что `./gradlew.bat assembleAlphaDebug` собирается локально (если нет — пометить TODO в сообщении пользователю).

## 8. Разрешения и системные API

- Целевая Android API: 35, минимальная: 21.
- Новые опасные permissions добавлять только если без них функция нереализуема, и обосновывать в коммите. Уже объявлены и ожидаемы: `QUERY_ALL_PACKAGES` (per-app routing), `REQUEST_INSTALL_PACKAGES` (self-update APK).
- `BIND_VPN_SERVICE` уже есть. Не запрашивать root.

## 9. Что агент НЕ ДОЛЖЕН делать

- Не переходить на Compose в этой ветке.
- Не менять `applicationId`, `packageName`, схемы deeplink (`clash://`, `clashmeta://`, `clashfest://`).
- Не удалять/переименовывать публичные строки `R.string.*` без миграции переводов.
- Не отключать существующую защиту/проверки «потому что мешают».
- Не коммитить keystore, токены, ссылки на персональные подписки пользователя в код или примеры.
- Не делать обращения к интернету из тестов и сборки без явной необходимости.

## 10. Вопросы агенту

Если требование неоднозначно — спрашивать у пользователя через интерактивный канал (askQuestion / комментарий в PR), **не выдумывать поведение**.

---

Файл версии: 2. Любые изменения этих правил — отдельным PR с явным согласованием.
