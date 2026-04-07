# ClashFest

<!--
https://img.shields.io/github/actions/workflow/status/Nemu-x/ClashFest/android-debug.yml?branch=feat/init-clashfest
-->

![License: GPL v3](https://img.shields.io/badge/license-GPLv3-green.svg)
![Platform: Android](https://img.shields.io/badge/platform-Android-3DDC84)
![Status: WIP](https://img.shields.io/badge/status-WIP-orange)
![Branch](https://img.shields.io/badge/branch-feat%2Finit--clashfest-blue)

ClashFest is a modern Android client built on top of the Clash / Clash Meta ecosystem, with refreshed branding, a cleaner UI, slimmer profile cards, routing tools, and a more expressive visual style.

> Status: work in progress  
> Active branch: `feat/init-clashfest`

## Highlights

- ClashFest branding and app identity
- Modernized home screen with slim profile cards
- Rule / Global / Direct mode switching
- Profile management with quick actions
- Rules and routing related tools
- Connections / traffic inspection screens
- Neon-accent dark UI direction
- Light theme support in progress
- Delay / ping related helpers
- Subscription import helpers

## Screenshots

<p align="center">
  <img src="docs/screenshots/home.png" width="180"/>
  <img src="docs/screenshots/connections.png" width="180"/>
  <img src="docs/screenshots/online_list.png" width="180"/>
  <img src="docs/screenshots/rules.jpg" width="180"/>
  <img src="docs/screenshots/add_rules.jpg" width="180"/>
</p>

<p align="center">
  <img src="docs/screenshots/routing_rules1.jpg" width="180"/>
  <img src="docs/screenshots/routing_rules2.jpg" width="180"/>
  <img src="docs/screenshots/profile_options.png" width="180"/>
  <img src="docs/screenshots/add_profile.png" width="180"/>
</p>

## Project Structure

- `app/` — Android app entry points, activities, packaging
- `common/` — shared utilities and helpers
- `core/` — native/core bridge, tunnel interaction, low-level logic
- `design/` — UI layer, layouts, themes, adapters, design logic
- `service/` — background service, profile management, rule helpers

## Current Focus

- stabilizing the redesigned home screen
- finishing ClashFest branding across all user-facing screens
- improving rules and routing UX
- improving connections visibility
- fixing theme inconsistencies, especially light theme
- cleaning up temporary and experimental code safely
- polishing launcher and app icon assets

## Build

### Requirements

- Android Studio
- Android SDK / NDK required by the project
- JDK compatible with the project
- Gradle wrapper included in the repository

### Debug build

Linux / macOS:

```bash
./gradlew assembleAlphaDebug
```

Windows:

```powershell
.\gradlew.bat assembleAlphaDebug
```

## Branding Assets

- `app/src/main/res/drawable-nodpi/clashfest_shield.png`

Optional folders:

- `branding/` — source branding assets
- `docs/` — documentation materials

## License

This project is licensed under the GNU General Public License v3.0.

See:

- `LICENSE`
- `NOTICE`

## Upstream / Attribution

ClashFest is a fork / derivative built on top of the Clash Android ecosystem.

Original copyright notices, license terms, and attribution requirements must be preserved where applicable.

## Disclaimer

ClashFest is provided as-is, without warranty.

Use it at your own responsibility and in accordance with:

- local laws
- service terms
- upstream license requirements

## Development Notes

This repository is currently being actively reworked in a personal feature branch.

The current implementation includes:

- UI redesign work
- branding updates
- rules and routing tooling experiments
- connections inspection work
- subscription UX improvements

## Contributing

At this stage, development is focused on the ClashFest fork workflow and internal iteration.
Contribution guidelines may be expanded later.
