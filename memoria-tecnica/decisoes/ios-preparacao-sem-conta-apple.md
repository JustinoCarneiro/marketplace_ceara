---
tipo: decisao
data: 2026-10-04
status: Ativa
---

# iOS: preparar a configuração agora, sem conta Apple Developer

## Contexto
O app é React Native + Expo e deve abrir em iPhone, mas nada do projeto dizia isso por escrito: o `eas.json` só tinha
perfis de Android, o CI cobre o emulador Android e a web, e o `app.json` só tinha `ios.supportsTablet`. Em 2026-10-04 o
usuário confirmou que iOS é requisito e que **não existe conta Apple Developer**. O iOS nunca foi construído nem testado.

## Decisão
Preparar tudo o que não depende de assinatura e documentar o caminho para quando houver conta:
- `app.json`: `ios.bundleIdentifier` (obrigatório para buildar iOS; igual ao pacote Android), `ITSAppUsesNonExemptEncryption: false`
  e textos de permissão em português só para o que o app usa — câmera, microfone, fotos e **localização em primeiro plano**.
  O plugin do `expo-location` escrevia por padrão os textos em inglês e ainda declarava "Always" e movimento, que o app não usa
  (só `requestForegroundPermissionsAsync` + `getCurrentPositionAsync`); passar `false` remove as chaves.
- `eas.json`: perfil `development-simulator` (herda `development`, `ios.simulator: true`) — único build de iOS que não exige conta.
- `docs/DEV_MOBILE.md`: seção iOS (o que dá para fazer sem conta, o que exige conta, pendências antes da App Store).
- `CLAUDE.md`: "Plataformas: Android e iOS".

**Caminho provisório para ver o app num iPhone:** a versão web (a mesma da demo) no Safari — não testada em iPhone real.

## Alternativas descartadas
- **Job de CI com simulador iOS (runner macOS):** sem conta Apple e sem teste escrito para iOS, seria custo e manutenção sem sinal útil.
  Fica para quando houver conta e fluxos a testar.
- **Deixar os textos padrão do plugin:** o iPhone pediria localização em inglês e a revisão da Apple pode questionar "Always" sem uso.

## Efeito
O manifesto do Android sai **idêntico** (9 permissões, conferido com `expo prebuild` antes e depois); nenhum teste mudou.
O projeto iOS gera (`expo prebuild --platform ios`); o `Info.plist` traz os textos em português e nenhuma chave "Always".

## O que continua pendente
- **Exclusão de conta pelo app** — exigência da App Store (5.1.1(v)) e da Play Store, e direito da LGPD; não existe hoje.
  Mexe em dado pessoal (R2): plano e revisão cruzada antes de implementar.
- **Conta Apple Developer** (US$ 99/ano; individual com CPF ou organização com CNPJ + D-U-N-S) e teste em iPhone real.
- Rótulos de privacidade da App Store.

## Ligado a
- [[prestador-verificado-para-operar]] (mesma frente de endurecimento do app); `docs/DEV_MOBILE.md` (seção iOS).
