# Fluxo de Desenvolvimento Mobile

## Pré-requisitos de sessão

Todo início de sessão, rode o script único da raiz:

```bash
./dev.sh
```

Ele automatiza, em sequência: sobe o backend (compose **homolog**), espelha as portas via `adb reverse` (8080 backend, 8081 Metro) e inicia o Metro. Também exporta `ELECTRON_DISABLE_SANDBOX=1` (ver **Troubleshooting**).

**Fallback manual** (se o `dev.sh` falhar, rode os passos na mão):

```bash
# 1. Backend (usar SEMPRE o compose homolog)
docker compose -f docker-compose.homolog.yml up -d

# 2. Espelhar portas para o celular (cabo USB obrigatório)
adb reverse tcp:8080 tcp:8080
adb reverse tcp:8081 tcp:8081

# 3. Metro bundler (hot reload)
cd mobile && npx expo start --port 8081
```

Confirme os containers e o dispositivo:
```bash
docker compose -f docker-compose.homolog.yml ps   # todos devem estar "healthy"
adb devices                                        # deve listar o Xiaomi
```

> **Atenção:** nunca subir com `docker compose up -d` (sem o `-f`). O compose padrão não tem backend nem admin e corrompeu o volume em 27/06.

---

## Modo 1 — Hot Reload real-time (recomendado para desenvolvimento)

Usa o **Expo Dev Client** já instalado no projeto. O JS atualiza em ~1s sem precisar reinstalar o APK.

### Setup único (uma vez)

```bash
cd mobile/android
./gradlew assembleDebug -q
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> O APK debug usa o mesmo package (`com.onda.marketplace`) e **substitui** o release. Para voltar ao release, rebuildar com `assembleRelease` e reinstalar.

### Uso diário

```bash
# Terminal 1 — backend (se não estiver no ar)
docker compose up -d

# Terminal 2 — Metro bundler
adb reverse tcp:8081 tcp:8081
cd mobile && npx expo start --port 8081

# No celular: abrir o app Onda — conecta ao Metro automaticamente via adb reverse
```

Qualquer mudança em `.tsx`/`.ts` aplica em ~1s via Fast Refresh.  
Para ver o menu dev: **agitar o celular** ou `adb shell input keyevent 82`.

---

## Modo 2 — OTA Update (~3 min, sem cabo após publicação)

Para quando o dev client não está disponível ou para homologar no APK release.

```bash
cd mobile
/home/marcos/.npm-global/bin/eas update \
  --branch preview \
  --environment preview \
  --message "descrição da mudança"
```

Depois no celular:
1. Fechar o app completamente (Forçar parada)
2. Abrir → aguarda download em background (~10s)
3. Fechar e abrir de novo → bundle novo aplicado

> Aplica apenas mudanças JS/UI. Não altera código nativo.

---

## Modo 3 — Rebuild completo do APK

Necessário quando mudar: `app.json` (android), `AndroidManifest.xml`, `build.gradle`, novo pacote com código nativo.

```bash
cd mobile/android
./gradlew assembleRelease -q --max-workers=2
adb reverse tcp:8080 tcp:8080
adb install -r app/build/outputs/apk/release/app-release.apk
```

> Dura ~5 min com cache Gradle. Aceitar o diálogo de instalação no Xiaomi se aparecer.

---

## Tabela de decisão

| Tipo de mudança | Modo |
|---|---|
| Cor, texto, layout, lógica JS | Hot Reload (1s) ou OTA (3 min) |
| Novo arquivo `.tsx`/`.ts` | Hot Reload (1s) ou OTA (3 min) |
| Nova biblioteca JS pura | OTA (3 min) |
| Nova biblioteca com código nativo | Rebuild APK |
| `app.json` → seção `android` | Rebuild APK |
| `AndroidManifest.xml` | Rebuild APK |
| Permissão nova do Android | Rebuild APK |

---

## Variáveis de ambiente

| Variável | Valor atual | Onde muda |
|---|---|---|
| `EXPO_PUBLIC_API_URL` | `http://localhost:8080/api/v1` | EAS dashboard (preview env) + `mobile/.env` |

Para apontar para outro servidor: atualizar em ambos os lugares e publicar OTA ou rebuildar.

```bash
# Ver valor atual no EAS
/home/marcos/.npm-global/bin/eas env:list --environment preview

# Atualizar
/home/marcos/.npm-global/bin/eas env:update \
  --variable-name EXPO_PUBLIC_API_URL \
  --value "http://nova-url/api/v1" \
  --environment preview
```

---

## Diagnóstico rápido

```bash
# Verificar se adb reverse está ativo
adb reverse --list

# Ver logs do app em tempo real
adb logcat | grep -E "Onda|expo|ReactNative"

# Testar se o backend responde do ponto de vista do celular
adb shell curl -s http://localhost:8080/actuator/health
```

---

## Troubleshooting

### React Native DevTools não instala / crash de sandbox no Metro
Sintoma no log do Metro:
```
ERROR  An unknown error occurred while installing React Native DevTools.
... chrome-sandbox is owned by ... mode 4755   (ou)   FATAL ... zygote_host ... Invalid argument
```
O DevTools é um app **Electron** empacotado via `dotslash` num diretório com **espaço no nome**, e o sandbox SUID do Chromium não lida com isso no Linux. Solução: desligar o sandbox do Electron (seguro — é ferramenta de dev local). O `dev.sh` **já faz isso** (`export ELECTRON_DISABLE_SANDBOX=1`). Se subir o Metro fora do `dev.sh`:
```bash
export ELECTRON_DISABLE_SANDBOX=1
npx expo start --port 8081
```

### Backend na IDE: enxurrada de avisos `code 1102` ("category 'null' is not analysed")
**Não é erro de código** — é config do language server Java (redhat.java). O backend usa `@SuppressWarnings("null")` no nível da classe em ~41 arquivos, que exigem a análise de null **ligada**. Garanta em `.vscode/settings.json` (arquivo local, ignorado pelo git):
```json
"java.compile.nullAnalysis.mode": "automatic"
```
Depois: `Ctrl+Shift+P` → **Java: Clean Java Language Server Workspace** → **Restart and delete**. Pôr como `disabled` reintroduz os avisos. Editar arquivos `.prefs` não adianta — o redhat.java regenera do Maven.

---

## APK atual instalado no Xiaomi

- Tipo: **Debug** com Expo Dev Client (`com.onda.marketplace`)
- API URL: `http://localhost:8080/api/v1` (via `adb reverse`)
- Metro: `http://localhost:8081` (via `adb reverse`)
- Canal OTA: `preview`
- Runtime version: `1.0.0`
- Cleartext: habilitado (`network_security_config.xml` + `usesCleartextTraffic`)
