#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -z "$(printenv LOGBOOK_JAR 2>/dev/null || true)" || ! -f "$LOGBOOK_JAR" ]]; then
  echo "LOGBOOK_JAR must point to logbook-kai.jar" >&2
  exit 2
fi
JAR="$ROOT/dist/kqs-logbook-plugin-v$(cat "$ROOT/VERSION").jar"
test -f "$JAR"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac --release 21 -proc:none -encoding UTF-8 -cp "$LOGBOOK_JAR:$JAR" -d "$TMP" "$ROOT"/tests/*.java
java -cp "$TMP:$LOGBOOK_JAR:$JAR" local.kancolle.kqs.QuestStoreTest
java -cp "$TMP:$LOGBOOK_JAR:$JAR" local.kancolle.kqs.QuestExporterTest
java -cp "$TMP:$LOGBOOK_JAR:$JAR" local.kancolle.kqs.QuestRegressionTest
jar tf "$JAR" | grep -qx 'META-INF/services/logbook.listener.ContentListenerSpi'
jar tf "$JAR" | grep -qx 'META-INF/services/logbook.plugin.lifecycle.StartUp'
