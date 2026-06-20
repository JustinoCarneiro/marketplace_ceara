#!/bin/bash
# Onda Dev — Configuração de ambiente em nova máquina (Ubuntu/Debian)
# Uso: bash setup/install.sh

set -e

BOLD=$(tput bold 2>/dev/null || echo "")
RESET=$(tput sgr0 2>/dev/null || echo "")
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

step()  { echo -e "\n${BOLD}▸ $1${RESET}"; }
ok()    { echo -e "${GREEN}  ✓ $1${NC}"; }
warn()  { echo -e "${YELLOW}  ⚠ $1${NC}"; }
skip()  { echo -e "  · $1 (já instalado)"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo ""
echo "${BOLD}════════════════════════════════════════════${RESET}"
echo "${BOLD}  Onda Dev — Configuração de ambiente${RESET}"
echo "${BOLD}════════════════════════════════════════════${RESET}"

# ── 1. Git ───────────────────────────────────────────────────────────
step "Git"
if ! command -v git &>/dev/null; then
  sudo apt-get update -q && sudo apt-get install -y -q git
  ok "git instalado"
else
  skip "git $(git --version | awk '{print $3}')"
fi

GIT_NAME=$(git config --global user.name 2>/dev/null || echo "")
GIT_EMAIL=$(git config --global user.email 2>/dev/null || echo "")
if [ -z "$GIT_NAME" ]; then
  read -p "  Nome para commits git: " GIT_NAME
  git config --global user.name "$GIT_NAME"
fi
if [ -z "$GIT_EMAIL" ]; then
  read -p "  E-mail para commits git: " GIT_EMAIL
  git config --global user.email "$GIT_EMAIL"
fi
ok "git identity: $GIT_NAME <$GIT_EMAIL>"

# ── 2. Node.js via nvm ───────────────────────────────────────────────
step "Node.js (via nvm)"
export NVM_DIR="$HOME/.nvm"
if [ ! -s "$NVM_DIR/nvm.sh" ]; then
  curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
  [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
  nvm install 20
  nvm alias default 20
  ok "node $(node --version) instalado via nvm"
else
  \. "$NVM_DIR/nvm.sh"
  skip "node $(node --version)"
fi

# ── 3. Docker ────────────────────────────────────────────────────────
step "Docker"
if ! command -v docker &>/dev/null; then
  curl -fsSL https://get.docker.com | sh
  sudo usermod -aG docker "$USER"
  ok "docker instalado"
  warn "Faça logout e login para aplicar o grupo docker sem sudo"
else
  skip "docker $(docker --version | awk '{print $3}' | tr -d ',')"
fi

# ── 4. GitHub CLI ────────────────────────────────────────────────────
step "GitHub CLI (gh)"
if ! command -v gh &>/dev/null; then
  curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
    | sudo dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg 2>/dev/null
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] \
    https://cli.github.com/packages stable main" \
    | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null
  sudo apt update -q && sudo apt install -y -q gh
  ok "gh instalado"
else
  skip "gh $(gh --version | head -1 | awk '{print $3}')"
fi

# ── 5. Claude Code CLI ───────────────────────────────────────────────
step "Claude Code"
if ! command -v claude &>/dev/null; then
  npm install -g @anthropic-ai/claude-code
  ok "claude instalado"
else
  skip "claude code (já presente)"
fi

# ── 6. Skills e agentes ~/.claude ────────────────────────────────────
step "Skills e agentes Onda"
mkdir -p "$HOME/.claude/commands" "$HOME/.claude/agents"

COPIED=0
for f in "$SCRIPT_DIR/claude/commands/"*.md; do
  dest="$HOME/.claude/commands/$(basename "$f")"
  cp "$f" "$dest"
  COPIED=$((COPIED + 1))
done

for f in "$SCRIPT_DIR/claude/agents/"*.md; do
  dest="$HOME/.claude/agents/$(basename "$f")"
  cp "$f" "$dest"
  COPIED=$((COPIED + 1))
done
ok "$COPIED skills/agentes copiados para ~/.claude"

# ── Resumo ───────────────────────────────────────────────────────────
echo ""
echo "${BOLD}════════════════════════════════════════════${RESET}"
echo "${BOLD}  Instalação concluída!${RESET}"
echo "${BOLD}════════════════════════════════════════════${RESET}"
echo ""
echo "  Próximos passos manuais (ver setup/CHECKLIST.md):"
echo "  1. claude auth login     — autenticar com a Anthropic"
echo "  2. gh auth login         — autenticar com o GitHub"
echo "  3. Instalar VSCode + extensão Claude Code"
echo ""
