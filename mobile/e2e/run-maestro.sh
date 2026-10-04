#!/usr/bin/env bash
# Executado pelo step "E2E no emulador Android 29" (android-emulator-runner).
# A action roda o `script:` linha a linha via `sh -c`, então construções
# multi-linha (funções, if/for) precisam ficar AQUI, num arquivo rodado de uma
# vez por bash. O workflow apenas chama: bash mobile/e2e/run-maestro.sh
set -eu

# Maestro CLI foi instalado em $HOME/.maestro/bin (step anterior)
export PATH="$HOME/.maestro/bin:$PATH"

# Desativar animações e popup de stylus (quebram taps do Maestro)
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put secure stylus_handwriting_enabled 0

# Rota redundante (10.0.2.2 já cobre, mas garante o backend em :8080)
adb reverse tcp:8080 tcp:8080

# Instalar o APK release (standalone, com o bundle JS embutido)
adb install -r mobile/android/app/build/outputs/apk/release/app-release.apk
sleep 3

# Roda um fluxo required; qualquer falha derruba a suíte.
run_test() {
  local f="$1"
  echo ""
  echo "▶ $(basename "$f")"
  if maestro test "$f"; then
    echo "✓ PASS: $(basename "$f")"
  else
    echo "✗ FAIL: $(basename "$f")"
    return 1
  fi
}

# Só prestador VERIFICADO envia proposta (ProviderVerificationGuard). O fluxo 03 deixa o prestador
# EM_VERIFICACAO e este job não sobe o profile seed (não há admin para aprovar pela API), então a
# aprovação é gravada direto no banco do CI — o mesmo UPDATE do botão "Verificar" do painel.
# A conexão vem das variáveis PG* do step do workflow (mesmo banco do serviço postgres).
aprovar_prestador() {
  local email="$1"
  command -v psql >/dev/null 2>&1 || { sudo apt-get update -qq && sudo apt-get install -y -qq postgresql-client; } >/dev/null
  local linhas
  # -q: sem a etiqueta "UPDATE 1", que o psql imprime junto com a linha do RETURNING.
  linhas=$(psql -q -v ON_ERROR_STOP=1 -tA -c \
    "UPDATE providers_profile SET status_verificacao = 'VERIFICADO', updated_at = now() WHERE user_id = (SELECT id FROM users WHERE email = '$email') RETURNING 1")
  if [ "$linhas" != "1" ]; then
    echo "✗ Prestador $email não encontrado para aprovar (linhas afetadas: '$linhas')"
    return 1
  fi
  echo "✓ Prestador $email aprovado (VERIFICADO)"
}

# Ordem importa — estado de BD persiste entre os fluxos.
run_test mobile/e2e/01_cadastro_cliente.yaml
run_test mobile/e2e/03_cadastro_prestador.yaml
aprovar_prestador "jose.prestador@onda.dev"
run_test mobile/e2e/04_criar_pedido.yaml
run_test mobile/e2e/05_enviar_proposta.yaml
run_test mobile/e2e/06_aceitar_proposta.yaml
run_test mobile/e2e/07_concluir_e_avaliar.yaml
run_test mobile/e2e/02_login_cliente.yaml
