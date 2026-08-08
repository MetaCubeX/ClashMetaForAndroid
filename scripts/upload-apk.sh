#!/usr/bin/env bash
# Upload the latest built agent APK to a public temp file host and print the
# download link. Usage: ./scripts/upload-apk.sh [path-to-apk]
set -euo pipefail

APK="${1:-}"
if [[ -z "$APK" ]]; then
  APK="$(ls -t apk/cmfa-*-arm64-v8a-debug.apk 2>/dev/null | head -n 1)"
fi
if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "ERROR: APK not found. Pass a path or build first (./gradlew ... assembleAgentDebug)." >&2
  exit 1
fi

echo "Uploading $APK ($(du -h "$APK" | cut -f1)) ..." >&2
resp="$(curl -s --max-time 600 -F "file=@$APK" https://tmpfiles.org/api/v1/upload)"
echo "$resp" | grep -q '"status":"success"' || { echo "Upload failed: $resp" >&2; exit 1; }

url="$(echo "$resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['url'])")"
dl="${url/https:\/\/tmpfiles.org\//https:\/\/tmpfiles.org\/dl\/}"
dl="${dl//\\/}"
echo "Download: $dl"
