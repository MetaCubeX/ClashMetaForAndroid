# AGENTS.md — Правила работы AI-агентов в ClashFest

Этот файл — обязательное руководство для любого AI-агента (Copilot, Codex, Cursor, Claude Code и т. п.), работающего в этом репозитории. Игнорировать эти правила нельзя.

## 0. Контекст проекта

ClashFest — Android-клиент на базе ядра **Clash.Meta (mihomo)**.

Цепочка форков:
```
xuhaoyang/ClashForAndroid
└── MetaCubeX/ClashMetaForAndroid (ядро + базовый клиент)
    └── Nemu-x/ClashFest (ребрендинг, UI-редизайн)
        └── Rerowros/ClashFest  ← мы здесь
```

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

- **Санитизировать импортируемый YAML** (см. `service/.../profile`):
  - Удалять или принудительно биндить к `127.0.0.1` поля: `port`, `socks-port`, `mixed-port`, `redir-port`, `tproxy-port`.
  - Удалять записи в `listeners:` типа `socks`/`mixed`/`http` без `authentication`.
  - Принудительно `allow-lan: false`, если пользователь явно не включил «Расширенный режим».
  - Принудительно биндить `external-controller` к `127.0.0.1`.
  - Защищать всё это поведение флагом `ProxyHardeningMode` в `ServiceStore` (значения: `Strict` (default, Android 10+), `Compat`, `Off`).
- **Не открывать SOCKS5 без auth наружу никогда.** SOCKS5 без `authentication` на Android 10+ должен быть отключён по умолчанию (фикс уязвимости описан в `https://publish.obsidian.md/zapret/VLESS-SOCKS5-vulnerability`).
- **TUN0 защита** (Android 10+): использовать `ConnectivityManager.getConnectionOwnerUid()` (уже подключён в `TunModule.queryUid`) для фильтрации соединений к адресам tun-интерфейса и loopback-портам прокси, исходящих от чужих UID.
- **Любые сетевые загрузки** (geoip/geosite/подписки) идут только через явные whitelisted-зеркала (см. п. 4) и с таймаутами + ретраями.

## 3. Стиль кода

- Kotlin для Android-кода, Go для ядра.
- Соблюдать существующий стиль модуля; не переформатировать чужие файлы целиком.
- Никаких лишних рефакторингов «по дороге». Минимально-инвазивные правки.
- Не добавлять docstring/комментарии к коду, который не меняется.
- Имена пакетов сохраняются: `com.github.kr328.clash.*` (не переименовывать — ломает интеграции).
- Строки — только через `strings.xml`. Обязательны три языка: EN (`values/strings.xml`), RU (`values-ru/strings.xml`) и упрощённый китайский (`values-zh/strings.xml`). Все остальные локали (включая `values-zh-rTW/`, `values-zh-rHK/` и прочие) — опциональны; апдейтить их при изменениях не обязательно.
- Логирование — через `com.github.kr328.clash.common.log.Log`, не `android.util.Log`.

## 4. Сеть и внешние ресурсы

Разрешённые источники для GeoIP/GeoSite (в порядке fallback):

```
https://github.com/MetaCubeX/meta-rules-dat/raw/release/geoip.dat
https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/release/geoip.dat
https://gh.zukijourney.com/MetaCubeX/meta-rules-dat/release/geoip.dat
https://github.com/Loyalsoldier/v2ray-rules-dat/raw/release/geoip.dat
```
(аналогично для `geosite.dat`).

При импорте подписки агент **обязан**:
1. Проверять, что `geox-url` валиден; если ядро жалуется на «cant download geoip.dat» — подменять на дефолтный набор зеркал из настроек.
2. Кэшировать gzip-варианты в `files/providers/geo/`.
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

- Источник истины — текст профиля на диске. Любая «hardening»-логика применяется как пост-процессинг при сохранении/обновлении профиля и помечается комментарием:
  ```
  # ClashFest hardening applied (vN) — DO NOT EDIT THIS BLOCK
  ```
- Оригинальный пользовательский YAML сохраняется рядом как `*.original.yaml` для возможности восстановления.

## 7. Git / Workflow

- Базовая ветка: `feat/init-clashfest`.
- Каждая логическая задача — отдельный коммит с conventional-стилем:
  - `feat(security): harden imported profile against socks5 leak`
  - `fix(geo): fallback mirrors for geoip download`
  - `ui(nav): merge rules+routing into single hub screen`
- Никаких force-push, rebase или удаления чужих коммитов без явного запроса пользователя.
- Submodules (`core/src/foss/golang/clash`) трогаем только при необходимости — обновление ядра делается отдельным PR.
- Перед push агент должен убедиться, что `./gradlew.bat assembleAlphaDebug` собирается локально (если нет — пометить TODO в сообщении пользователю).

## 8. Разрешения и системные API

- Целевая Android API: 35, минимальная: 21.
- Новые опасные permissions (например, `QUERY_ALL_PACKAGES`) добавлять только если без них функция нереализуема, и обосновывать в коммите.
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

Файл версии: 1. Любые изменения этих правил — отдельным PR с явным согласованием.
