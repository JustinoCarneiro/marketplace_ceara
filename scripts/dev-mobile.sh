#!/usr/bin/env bash
# Sobe o ambiente completo de homologação + tunnel + build do APK se necessário.
# Uso: ./scripts/dev-mobile.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EAS_BIN="$HOME/.npm-global/bin/eas"
EAS_JSON="$ROOT/mobile/eas.json"
TUNNEL_LOG=$(mktemp /tmp/cloudflared-XXXXXX.log)

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

cleanup() {
  echo ""
  echo -e "${YELLOW}→ Encerrando tunnel (PID $TUNNEL_PID)...${NC}"
  kill "$TUNNEL_PID" 2>/dev/null || true
  rm -f "$TUNNEL_LOG"
  echo -e "${GREEN}✓ Tunnel encerrado. Containers continuam rodando.${NC}"
  echo -e "  Para parar os containers: ${CYAN}make homolog-down${NC}"
}
trap cleanup INT TERM

# ── 1. Containers ─────────────────────────────────────────────────────────────
echo -e "${CYAN}[1/4] Subindo containers de homologação...${NC}"
cd "$ROOT"
make homolog-up

# ── 2. Aguardar backend ───────────────────────────────────────────────────────
echo -e "${CYAN}[2/4] Aguardando backend ficar saudável...${NC}"
ATTEMPTS=0
until curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; do
  ATTEMPTS=$((ATTEMPTS + 1))
  if [ $ATTEMPTS -ge 60 ]; then
    echo -e "${RED}✗ Backend não respondeu após 2 minutos. Verifique: make homolog-logs${NC}"
    exit 1
  fi
  printf "."
  sleep 2
done
echo -e " ${GREEN}OK${NC}"

# ── 3. Tunnel ─────────────────────────────────────────────────────────────────
echo -e "${CYAN}[3/4] Iniciando tunnel Cloudflare...${NC}"
cloudflared tunnel --url http://localhost:8080 > "$TUNNEL_LOG" 2>&1 &
TUNNEL_PID=$!

TUNNEL_URL=""
for i in $(seq 1 30); do
  TUNNEL_URL=$(grep -oP 'https://[a-z0-9-]+\.trycloudflare\.com' "$TUNNEL_LOG" 2>/dev/null | head -1 || true)
  [ -n "$TUNNEL_URL" ] && break
  sleep 1
done

if [ -z "$TUNNEL_URL" ]; then
  echo -e "${RED}✗ Não foi possível obter a URL do tunnel. Verifique se cloudflared está instalado.${NC}"
  kill "$TUNNEL_PID" 2>/dev/null || true
  exit 1
fi

TUNNEL_API_URL="${TUNNEL_URL}/api/v1"

# ── 4. APK — rebuild se URL mudou ─────────────────────────────────────────────
CURRENT_API_URL=$(python3 -c "import json; d=json.load(open('$EAS_JSON')); print(d['build']['preview']['env']['EXPO_PUBLIC_API_URL'])" 2>/dev/null || echo "")

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e " Backend  → ${CYAN}http://localhost:8080${NC}"
echo -e " Admin    → ${CYAN}http://localhost:3000${NC}"
echo -e " pgAdmin  → ${CYAN}http://localhost:5050${NC}"
echo -e " Tunnel   → ${CYAN}${TUNNEL_URL}${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

if [ "$TUNNEL_API_URL" = "$CURRENT_API_URL" ]; then
  echo -e "${GREEN}✓ URL do tunnel não mudou — APK atual ainda é válido.${NC}"
  echo -e "  Appetize: ${CYAN}https://appetize.io/app/b_vz2ngcilcnhfyxvriegpgy7v3y${NC}"
else
  echo -e "${YELLOW}⚠ URL do tunnel mudou. Atualizando eas.json e iniciando novo build...${NC}"
  echo -e "  Anterior: ${CURRENT_API_URL}"
  echo -e "  Nova:     ${TUNNEL_API_URL}"

  # Atualiza eas.json
  python3 - <<PYEOF
import json
with open('$EAS_JSON', 'r') as f:
    d = json.load(f)
d['build']['preview']['env']['EXPO_PUBLIC_API_URL'] = '$TUNNEL_API_URL'
with open('$EAS_JSON', 'w') as f:
    json.dump(d, f, indent=2)
    f.write('\n')
PYEOF

  echo ""
  echo -e "${CYAN}[4/4] Buildando APK (isso leva ~15 min)...${NC}"
  cd "$ROOT/mobile"
  "$EAS_BIN" build --platform android --profile preview --non-interactive

  # Obtém URL do novo APK
  SESSION=$(python3 -c "import json; d=json.load(open('$HOME/.expo/state.json')); print(d['auth']['sessionSecret'])")
  APK_PAGE=$(curl -sf -X POST "https://api.expo.dev/graphql" \
    -H "Content-Type: application/json" \
    -H "expo-session: $SESSION" \
    -d '{"query":"{ app { byId(appId: \"bcc17c4a-9f69-40b9-af45-a4d0b6c107a7\") { builds(offset:0,limit:1) { id } } } }"}' \
    | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['data']['app']['byId']['builds'][0]['id'])")

  echo ""
  echo -e "${GREEN}✓ Build concluído!${NC}"
  echo -e "  Baixe o APK e suba no Appetize:"
  echo -e "  ${CYAN}https://expo.dev/accounts/ondaenterprise/projects/onda-marketplace/builds/${APK_PAGE}${NC}"
fi

echo ""
echo -e "Pressione ${YELLOW}Ctrl+C${NC} para encerrar o tunnel."
wait "$TUNNEL_PID"
