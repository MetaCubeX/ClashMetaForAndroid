# Contributing to ClashFest

Thanks for your interest in ClashFest — an Android client in the **Clash.Meta / Mihomo** family with a redesigned Home, single-mode UI, routing-focused tools, and safety defaults baked in.

This document covers the things you need before opening a PR. If you are using an AI assistant (Copilot, Cursor, Claude Code, Codex, …) to write code in this repo, you (and it) **must** also read [AGENTS.md](AGENTS.md) — those rules are non-negotiable and apply equally to human contributors.

---

## 1. Before you start

- **Base branch:** `feat/init-clashfest`. Cut a topic branch off it, do not commit there directly. Open PRs back into `feat/init-clashfest`.
- **Topic branch naming:** use a conventional-commit prefix that matches the work:
  - `feat/<slug>` — new feature.
  - `fix/<slug>` — bug fix.
  - `chore/<slug>` — tooling, deps, branding, non-functional cleanup.
  - `docs/<slug>` — documentation-only changes.
  - `refactor/<slug>` — restructure without behavior change.
  - `ui/<slug>` — UI-only tweaks where the scope is purely visual.

  Example: `fix/proxy-pill-1hop`, `feat/native-snapshot-parsing`, `chore/launcher-icon-refresh`.

  One topic branch per logical change. Long-running umbrella work (e.g. Path B) lives on its own branch and ships as a series of small commits inside it; do not pile unrelated fixes on the same branch.
- **Fork lineage:** `kr328/ClashForAndroid` → `MetaCubeX/ClashMetaForAndroid` → `Nemu-x/ClashFest`. We try not to drift further from upstream than necessary — see "Working with upstream" below.
- **Read first:** [AGENTS.md](AGENTS.md) (security rules, UI invariants, module layout, what *not* to do) and the relevant docs under [`docs/`](docs/) for the area you are touching.
- **Language:** authoring text — code comments, KDoc, files under [`docs/`](docs/), commit messages, and PR descriptions — is in English. User-visible app strings stay localized as section 3 requires (EN + RU + ZH mandatory, others optional). Working drafts kept locally (untracked) can be in any language while in progress; once it lands in a PR the authoring text is English.

## 2. Building

### Requirements

- Android Studio (current stable) or a compatible JetBrains IDE
- Android SDK + **NDK** as configured by the project
- **JDK** matching the Gradle toolchain
- The Gradle wrapper is committed — do not run a global `gradle`

### Submodules

The Mihomo core lives in `core/src/foss/golang/clash` as a submodule. After cloning:

```bash
git submodule update --init --recursive
```

### Debug build (default **alpha** flavor)

**Windows:**
```powershell
.\gradlew.bat assembleAlphaDebug
```

**Linux / macOS:**
```bash
./gradlew assembleAlphaDebug
```

Before pushing, please make sure `assembleAlphaDebug` builds clean locally. If it does not, call that out in the PR description so reviewers know.

## 3. Code style

- **Kotlin** for Android code, **Go** for core. Match the surrounding file — do not reformat unrelated code.
- Use the bundled IDE code-style profile: `File → Settings → Editor → Code Style → C/C++ and Kotlin → Scheme → Project`.
- Keep edits minimally invasive. No drive-by refactors, no speculative abstractions, no new comments on code you did not change.
- Package names stay as they are (`com.github.kr328.clash.*`) — renaming breaks integrations and Quick Settings tiles.
- User-visible strings live in `strings.xml`. Required languages: **EN** (`values/`), **RU** (`values-ru/`), and **Simplified Chinese** (`values-zh/`). Other locales (`values-zh-rTW/`, `values-zh-rHK/`, etc.) are optional — update them only if you want to, no requirement.
- Logging goes through `com.github.kr328.clash.common.log.Log`, not `android.util.Log`.
- Do **not** introduce Jetpack Compose in this branch — the project is XML + Material 3.

Full style and architectural constraints are in [AGENTS.md §3 and §5](AGENTS.md).

## 4. Commits and branches

We follow a conventional-commit style with a scope:

```
feat(security): harden imported profile against socks5 leak
fix(geo): fallback mirrors for geoip download
ui(nav): merge rules+routing into single hub screen
chore(release): bump version to 0.6.0
docs(operator-api): describe X-Brand-Renew-URL behavior
```

Common scopes: `security`, `ui`, `nav`, `proxy`, `profiles`, `geo`, `routing`, `connections`, `service`, `core`, `branding`, `i18n`, `build`, `ci`, `docs`, `release`.

Rules of thumb:

- One logical change per commit. Squash WIP commits before opening the PR.
- Branch prefix must match the commit type: a branch called `feat/X` should not land a PR whose only commits are `fix(Y)`. Pick the prefix when you cut the branch and keep them aligned.
- **No force-push** to shared branches (`main`, `feat/init-clashfest`) and **no rebases that rewrite other people's commits**. Force-push on your own topic branch before review is fine.
- Do not bump the core submodule in the same commit as application changes — core updates ship in their own PR.
- Never commit secrets, personal subscription URLs, or test profiles with real keys. The release keystore in the tree is intentional; do not add others.

## 5. Pull requests

A good PR:

1. Targets `feat/init-clashfest`.
2. Has a short, descriptive title (the commit-style line works well).
3. Includes a "what changed / why / how to verify" section in the description.
4. Lists any security-relevant changes explicitly — flag them, even if you think they are obviously safe.
5. For UI changes: a screenshot or short clip of the before/after, and a note about which themes / orientations / screen sizes you verified.
6. For release-tagging changes: run through [`docs/security-smoke-checklist.md`](docs/security-smoke-checklist.md) and link it in the PR.

Reviewers will look for: clean build, no regressions in the smoke checklist, no new permissions without justification, no broken EN/RU strings, no upstream-divergence that we cannot maintain.

## 6. Security

Security defaults are a P0 concern in this project — see [AGENTS.md §2](AGENTS.md) for the full list of invariants (loopback-only listeners, sanitized YAML imports, hardened SOCKS5 behavior, TUN0 cross-UID guard, whitelisted geo mirrors).

If you find a vulnerability, **do not open a public issue**. Email the maintainer (see GitHub profile) or open a private security advisory on the repo. Fixes that touch the hardening pipeline must keep the original user YAML preserved as `*.original.yaml` and apply changes idempotently — again, see AGENTS.md.

## 7. Issues and feature requests

- Search existing issues first — this codebase has a long fork history and a lot of ground is already covered upstream.
- For bug reports: include device model, Android version, app version (from About), and reproduction steps. If the bug involves a subscription, redact the URL and any per-user tokens before pasting logs.
- For feature requests: explain the user problem before proposing a solution. Features that conflict with the "single unified UI mode" or the bottom-nav layout described in [AGENTS.md §5](AGENTS.md) are unlikely to land as-is.

## 8. Working with upstream

ClashFest is a soft fork — we re-export work back when it makes sense and pull from upstream when we can. To keep that path open:

- Avoid renaming or moving upstream files unless you have to.
- Keep ClashFest-specific UI changes in their own modules / files where possible.
- If you are porting a change from upstream, credit the upstream commit in the body of the PR description.

Upstream projects we track:

- [MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) — Android client
- [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo) — core
- [MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat) — geo data

## 9. License

ClashFest is licensed under the **GNU General Public License v3.0**. By submitting a contribution you agree that it will be released under the same license. Keep upstream copyright and license notices intact in any file you touch.
