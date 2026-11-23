# Contributing to AgenticClash

## License

This project is licensed under GPL-3.0. By contributing you agree that your contribution is
licensed under the same terms. Contributions are not merged into any closed-source branch.

## Before you start

1. Read [AGENTS.md](AGENTS.md). Document workflow is [scripts/wiki/workflow.md](scripts/wiki/workflow.md).
2. Pick up existing work from [dev/INDEX.md](dev/INDEX.md).
3. Behavior changes update the current design or requirement in the same change.
   Add a plan when the work needs several steps, a handoff, or an explicit risk.

## Code style

Open the project in Android Studio or IntelliJ IDEA and use the project code style profile:

`File` -> `Settings` -> `Editor` -> `Code Style` -> `C/C++ and Kotlin` -> `Scheme` -> `Project`

## Build and test

```bash
git submodule update --init --recursive
./gradlew :service:testMetaDebugUnitTest
./gradlew :design:testMetaDebugUnitTest
python3 scripts/wiki/wiki.py verify --root .
```

See the [build guide](docs/guides/getting-started/build.md) for signing and submodule details.

Each clone can enable the documentation hooks once:

```bash
git config core.hooksPath scripts/wiki/hooks
```

Do not pass `--no-verify` to skip them.

## Commits

- Keep each commit focused on one topic.
- When behavior or contracts change, update the current docs in the same change.
  The order is in [scripts/wiki/workflow.md](scripts/wiki/workflow.md).
- `release.keystore` stays tracked and is the same file as upstream. Do not commit another keystore,
  `signing.properties`, `local.properties`, tokens, device identifiers, or real server addresses.
  Use reserved domains such as `example.com` in tests and documentation.

## Security issues

Do not open a public issue for a vulnerability. Report it privately to the maintainer through the
contact details in [SECURITY.md](SECURITY.md).
