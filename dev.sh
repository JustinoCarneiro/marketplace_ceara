#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "==> Backend"
docker compose -f "$ROOT/docker-compose.homolog.yml" up -d
docker compose -f "$ROOT/docker-compose.homolog.yml" ps

echo ""
echo "==> ADB reverse"
if adb devices | grep -q "device$"; then
  adb reverse tcp:8080 tcp:8080
  adb reverse tcp:8081 tcp:8081
  echo "    OK — 8080 (backend) e 8081 (metro) espelhados"
else
  echo "    AVISO: nenhum dispositivo USB detectado — conecte o Xiaomi e rode:"
  echo "    adb reverse tcp:8080 tcp:8080 && adb reverse tcp:8081 tcp:8081"
fi

echo ""
echo "==> Metro"
cd "$ROOT/mobile"
# React Native DevTools (Electron empacotado via dotslash) fica num diretório com
# espaço no nome, e o sandbox SUID do Chromium não lida com isso no Linux. Desligar
# o sandbox do Electron evita o crash do DevTools — seguro por ser ferramenta de dev local.
export ELECTRON_DISABLE_SANDBOX=1
npx expo start --port 8081
