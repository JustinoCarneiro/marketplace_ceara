#!/usr/bin/env bash
# Checkpoint de handoff entre agentes (OndaDev 3.0).
#
# Coleta SOMENTE metadados seguros e preenche .ondadev/handoff/current.md a
# partir de .ondadev/handoff/TEMPLATE.md:
#   - branch, último commit, ahead/behind
#   - git status --short e git diff --stat (nomes e números, nunca o conteúdo)
#   - resultado das validações determinísticas do repositório
#
# NÃO lê conteúdo de arquivo, NÃO gera diff completo (patch), NÃO imprime valor
# de variável de ambiente. Determinístico: nenhuma decisão mora aqui.
#
# Uso:
#   bash scripts/ai-checkpoint.sh              # escreve .ondadev/handoff/current.md
#   bash scripts/ai-checkpoint.sh --stdout     # imprime, não escreve
#   bash scripts/ai-checkpoint.sh --no-tests   # não roda as validações
#   bash scripts/ai-checkpoint.sh -h

set -euo pipefail

onda_run_tests=1
onda_to_stdout=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --stdout) onda_to_stdout=1 ;;
    --no-tests) onda_run_tests=0 ;;
    -h|--help)
      sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      printf '[ERROR] opção desconhecida: %s\n' "$1" >&2
      exit 2
      ;;
  esac
  shift
done

cd "$(git rev-parse --show-toplevel)"
onda_template=".ondadev/handoff/TEMPLATE.md"
onda_current=".ondadev/handoff/current.md"
[ -f "$onda_template" ] || { printf '[ERROR] ausente: %s\n' "$onda_template" >&2; exit 1; }

onda_tmp="$(mktemp -d)"
trap 'rm -rf "$onda_tmp"' EXIT

# --- metadados de git (só porcelanas seguras) -------------------------------
onda_date="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
onda_branch="$(git branch --show-current 2>/dev/null || echo '(detached)')"
onda_commit="$(git log -1 --format='%h %s (%cI)' 2>/dev/null || echo '(sem commits)')"

onda_track='sem upstream configurado'
if git rev-parse --abbrev-ref '@{u}' >/dev/null 2>&1; then
  read -r behind ahead < <(git rev-list --left-right --count '@{u}...HEAD' 2>/dev/null || echo '0 0')
  onda_track="ahead ${ahead:-0} / behind ${behind:-0} vs $(git rev-parse --abbrev-ref '@{u}')"
fi

{
  echo '```text'
  echo "# git status --short"
  git status --short || true
  echo
  echo "# git diff --stat (working tree)"
  git diff --stat || true
  echo
  echo "# git diff --cached --stat (staged)"
  git diff --cached --stat || true
  echo
  echo "# git worktree list"
  git worktree list || true
  echo '```'
} > "$onda_tmp/status.md"

# --- validações determinísticas -------------------------------------------
onda_run_check() {
  local label="$1"; shift
  local out rc
  out="$("$@" 2>&1)" && rc=0 || rc=$?
  if [ "$rc" -eq 0 ]; then
    printf '| %s | ✅ PASS | |\n' "$label"
  else
    printf '| %s | ❌ FAIL | %s |\n' "$label" "$(printf '%s\n' "$out" | tail -n1 | tr '|' '/')"
  fi
}

{
  echo "| Validação | Resultado | Nota |"
  echo "| --- | --- | --- |"
  if [ "$onda_run_tests" -eq 1 ]; then
    # Verificação rápida do Marketplace Ceará. Suites completas (backend mvn test,
    # Playwright) são lentas: rode-as à parte e anote na seção "Último teste específico".
    onda_run_check 'mobile typecheck'  bash -c 'cd mobile && npx tsc --noEmit'
  else
    echo "| (validações puladas: --no-tests) | — | rode sem --no-tests |"
  fi
} > "$onda_tmp/tests.md"

# --- bloco automático (só a seção 0, entre os marcadores) -------------------
{
  if [ -n "${ANTHROPIC_API_KEY-}" ]; then
    echo '> ⚠️ ANTHROPIC_API_KEY está DEFINIDA no ambiente. Não use como fallback automático de cota — o Claude Code pode cobrar via API. Ver .ondadev/README.md.'
    echo
  fi
  echo "_Atualizado (UTC): ${onda_date}_"
  echo
  echo "- **Branch:** ${onda_branch}  ·  ${onda_track}"
  echo "- **Último commit:** ${onda_commit}"
  echo
  cat "$onda_tmp/status.md"
  echo
  cat "$onda_tmp/tests.md"
} > "$onda_tmp/auto.md"

# --- escolher a base: current.md preserva as seções 1-9 já escritas --------
if [ -f "$onda_current" ]; then
  onda_base="$onda_current"
else
  onda_base="$onda_template"
fi

if ! grep -q 'ai-checkpoint:auto:start' "$onda_base" \
   || ! grep -q 'ai-checkpoint:auto:end' "$onda_base"; then
  printf '[ERROR] marcadores ai-checkpoint:auto ausentes em %s.\n' "$onda_base" >&2
  printf '        Apague %s e rode de novo para regenerar a partir do TEMPLATE.md.\n' "$onda_current" >&2
  exit 1
fi

# Substitui só o trecho entre os marcadores; o resto do arquivo fica intacto.
awk -v autofile="$onda_tmp/auto.md" '
  /<!-- ai-checkpoint:auto:start -->/ {
    print
    while ((getline l < autofile) > 0) print l
    close(autofile)
    skip = 1
    next
  }
  /<!-- ai-checkpoint:auto:end -->/ { skip = 0; print; next }
  !skip { print }
' "$onda_base" > "$onda_tmp/out.md"

if [ "$onda_to_stdout" -eq 1 ]; then
  cat "$onda_tmp/out.md"
else
  mkdir -p "$(dirname "$onda_current")"
  cp "$onda_tmp/out.md" "$onda_current"
  if [ "$onda_base" = "$onda_template" ]; then
    printf '[OK] %s criado (%s). Preencha as seções 1 a 9.\n' "$onda_current" "$onda_date"
  else
    printf '[OK] %s: seção 0 atualizada (%s). Seções 1 a 9 preservadas.\n' "$onda_current" "$onda_date"
  fi
fi
