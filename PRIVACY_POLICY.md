# Privacy Policy

AgenticClash is an open-source Android client for mihomo. It has no analytics, advertising, crash
reporting, or telemetry SDKs, and it does not send usage data to the maintainer.

## Data the app handles

The app stores data locally on the device:

- Profiles you import, including their source URLs and any credentials contained in them
- Routing strategies, app sets, and access-control package lists
- App preferences such as theme, sorting, and notification settings
- WebDAV server address, username, and password when you enable WebDAV sync
- A local MCP bearer token when you enable the local MCP server
- Logs you choose to capture

Profile sources, WebDAV servers, and the mihomo core are contacted directly by the app. The
maintainer does not operate a proxy, relay, or account service for this app and does not receive
this data.

## Network access

- Profile subscriptions are fetched from the URLs you add.
- WebDAV sync connects only to the server you configure.
- The local MCP server listens on `127.0.0.1` only and is disabled by default.
- Geo data and rule files are downloaded from the upstream MetaCubeX release URLs when building or
  updating them.

## Backups

Android cloud backup and device transfer include only the app, tips, and UI preference files. They
do not include the profile database, imported or pending profile files, WebDAV settings, or the MCP
token. Use the app's own export features if you want to move profiles between devices.

## Logs

Logs are written to app-private storage and remain on the device unless you export or share them.
Review logs before sharing because they can contain hostnames or addresses from your configuration.

## Children

The app is not directed at children under 13 and does not knowingly collect personal information
from them.

## Changes

Material changes to this policy will be published in this repository. The current version applies
from the date of the commit that changes it.

## Contact

Clarence Henry

- Coolapk: https://www.coolapk.com/u/469700
- Telegram: https://t.me/universesoftware
- WeChat Official Account: 超越观
