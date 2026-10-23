#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -z "$(printenv LOGBOOK_JAR 2>/dev/null || true)" || ! -f "$LOGBOOK_JAR" ]]; then
  echo "LOGBOOK_JAR must point to a real logbook-kai.jar" >&2
  exit 2
fi
VERSION="$(cat "$ROOT/VERSION")"
OUT="$ROOT/dist/kqs-logbook-plugin-v$VERSION.jar"
if [[ -e "$OUT" ]]; then
  echo "Refusing to overwrite existing output: $OUT" >&2
  exit 3
fi
for bin in javac jar; do command -v "$bin" >/dev/null; done
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$ROOT/dist" "$TMP/classes/META-INF"
javac --release 21 -proc:none -encoding UTF-8 -cp "$LOGBOOK_JAR" -d "$TMP/classes" "$ROOT/src/main/java/local/kancolle/kqs/QuestStore.java" "$ROOT/src/main/java/local/kancolle/kqs/KqsLogbookPlugin.java"
cp -R "$ROOT/src/main/resources/." "$TMP/classes/"
cp "$ROOT/LICENSE" "$TMP/classes/META-INF/LICENSE"
cp "$ROOT/NOTICE.md" "$TMP/classes/META-INF/NOTICE.md"
cat > "$TMP/MANIFEST.MF" <<EOF
Manifest-Version: 1.0
Implementation-Title: KQS Logbook Plugin
Implementation-Vendor: ItukiMac
Implementation-Version: $VERSION
Bundle-License: MIT
EOF
jar --create --file "$OUT" --manifest "$TMP/MANIFEST.MF" -C "$TMP/classes" .
sha256sum "$OUT"
echo "Created $OUT"
