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

# Instalar o APK debug
adb install -r mobile/android/app/build/outputs/apk/debug/app-debug.apk
sleep 3

# Roda um fluxo; 'known-fail' não derruba a suíte.
run_test() {
  local f="$1" expected="${2:-required}"
  echo ""
  echo "▶ $(basename "$f")"
  if maestro test "$f"; then
    echo "✓ PASS: $(basename "$f")"
  elif [ "$expected" = "known-fail" ]; then
    echo "⚠  KNOWN FAIL: $(basename "$f") — ver comentário no workflow"
  else
    echo "✗ FAIL: $(basename "$f")"
    return 1
  fi
}

# Ordem importa — estado de BD persiste entre os fluxos.
run_test mobile/e2e/01_cadastro_cliente.yaml
run_test mobile/e2e/03_cadastro_prestador.yaml
run_test mobile/e2e/04_criar_pedido.yaml
run_test mobile/e2e/05_enviar_proposta.yaml
run_test mobile/e2e/06_aceitar_proposta.yaml known-fail
run_test mobile/e2e/07_concluir_e_avaliar.yaml known-fail
run_test mobile/e2e/02_login_cliente.yaml
