#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [[ "${1:-}" != "--confirm-tv-install" ]]; then
  echo "Instalação bloqueada. Só execute novamente com --confirm-tv-install após autorização explícita do usuário." >&2
  exit 2
fi
if [[ ! -s LauncherTV.apk ]]; then
  echo "LauncherTV.apk não existe; rode ./build.sh primeiro." >&2
  exit 2
fi

adb get-state >/dev/null
REMOTE_APK="$(adb shell pm path br.casa.sala | sed -n 's/^package://p' | tr -d '\r' | head -n 1)"
if [[ -z "$REMOTE_APK" ]]; then
  echo "Não encontrei o Launcher TV instalado na TV; nenhuma instalação foi iniciada." >&2
  exit 2
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="backups/tv/$STAMP"
mkdir -p "$BACKUP_DIR"
adb exec-out cat "$REMOTE_APK" > "$BACKUP_DIR/LauncherTV-before-install.apk"
if [[ ! -s "$BACKUP_DIR/LauncherTV-before-install.apk" ]]; then
  echo "Não consegui criar uma cópia válida do APK atual da TV; instalação cancelada." >&2
  exit 2
fi
shasum -a 256 "$BACKUP_DIR/LauncherTV-before-install.apk" > "$BACKUP_DIR/LauncherTV-before-install.apk.sha256"
adb shell dumpsys package br.casa.sala \
  | grep -E 'versionCode=|versionName=|firstInstallTime=|lastUpdateTime=|codePath=' \
  > "$BACKUP_DIR/package-info.txt"

echo "Backup da versão atual salvo em $BACKUP_DIR"
adb install -r LauncherTV.apk
