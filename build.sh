#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# Preserve the previous build before any rebuild can overwrite the rollback APK.
if [ -f LauncherTV.apk ]; then
  mkdir -p backups/builds
  BACKUP_STAMP="$(date +%Y%m%d-%H%M%S)"
  BACKUP_APK="backups/builds/LauncherTV-before-build-$BACKUP_STAMP.apk"
  cp -p LauncherTV.apk "$BACKUP_APK"
  shasum -a 256 "$BACKUP_APK" > "$BACKUP_APK.sha256"
  echo "Backup anterior: $BACKUP_APK"
fi
SDK="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
AJ="$(ls -d "$SDK"/platforms/android-*/android.jar | sort -V | tail -1)"
rm -rf out && mkdir -p out/classes out/gen/br/casa/sala
# config.properties is optional; missing keys disable their integrations.
KEY="$( (grep -E '^tmdb_key=' config.properties 2>/dev/null || true) | tail -1 | cut -d= -f2- | tr -d ' "\\\r' || true)"
CINEMA_TOKEN="$( (grep -E '^cinema_token=' config.properties 2>/dev/null || true) | tail -1 | cut -d= -f2- | tr -cd 'A-Za-z0-9._-' || true)"
INGRESSO_PARTNER="$( (grep -E '^ingresso_partner=' config.properties 2>/dev/null || true) | tail -1 | cut -d= -f2- | tr -cd 'A-Za-z0-9_-' || true)"
cat > out/gen/br/casa/sala/Config.java <<CFG
package br.casa.sala;
final class Config {
    private Config() {}
    static final String TMDB_KEY = "$KEY";
    static final String CINEMA_TOKEN = "$CINEMA_TOKEN";
    static final String INGRESSO_PARTNER = "$INGRESSO_PARTNER";
}
CFG
"$BT/aapt2" compile --dir res -o out/res.zip
"$BT/aapt2" link -o out/base.apk -I "$AJ" --manifest AndroidManifest.xml --java out/gen out/res.zip \
  --min-sdk-version 28 --target-sdk-version 33
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" -classpath "$AJ" \
  -d out/classes $(find src out/gen -name '*.java')
(cd out/classes && jar cf ../classes.jar .)
"$BT/d8" --release --min-api 28 --lib "$AJ" --output out out/classes.jar
(cd out && zip -qj base.apk classes.dex)
"$BT/zipalign" -f -p 4 out/base.apk out/aligned.apk
KEYSTORE="${SIGNING_KEYSTORE:-sala.keystore}"
KEY_ALIAS="${SIGNING_KEY_ALIAS:-sala}"
if [[ -n "${SIGNING_STORE_PASS:-}" ]]; then
  STORE_PASS="$SIGNING_STORE_PASS"
elif [[ -t 0 ]]; then
  read -r -s -p "Senha da keystore: " STORE_PASS
  printf '\n'
else
  echo "Defina SIGNING_STORE_PASS para compilar sem terminal interativo." >&2
  exit 2
fi
KEY_PASS="${SIGNING_KEY_PASS:-$STORE_PASS}"
[ -f "$KEYSTORE" ] || keytool -genkeypair -keystore "$KEYSTORE" -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 20000 \
  -storepass "$STORE_PASS" -keypass "$KEY_PASS" -dname "CN=Sala" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$STORE_PASS" --key-pass "pass:$KEY_PASS" --out LauncherTV.apk out/aligned.apk
echo "OK LauncherTV.apk $(du -h LauncherTV.apk | cut -f1)"
