---
tipo: bug
data: 2026-09-07
severidade: Alta
status: Resolvido
resolvido_em: 2026-09-07
---

# `usesCleartextTraffic: true` do app.json não faz nada — release bloqueava HTTP puro

## Sintoma
Depois de resolver as 3 causas raiz de flakiness do formulário de cadastro
([[maestro-flows-nunca-validados-ao-vivo]]), a 10ª run de validação finalmente preencheu e
submeteu o formulário de cadastro do zero ao fim — e o app mostrou um erro em tela, visível no
próprio screenshot de debug do Maestro:

```
fetch failed: java.net.UnknownServiceException:
CLEARTEXT communication to 10.0.2.2 not permitted by network security policy
```

## Causa raiz
`mobile/app.json` tem `"android": { "usesCleartextTraffic": true }` — mas esse campo **não é
lido por nenhum plugin do `@expo/prebuild-config` nesta versão do Expo** (confirmado por grep no
código-fonte do pacote: zero ocorrências). É um campo morto, provavelmente copiado de
documentação/exemplo de uma versão anterior do Expo onde funcionava assim.

Gerando o `android/` localmente (`npx expo prebuild --platform android`) e inspecionando o
manifest: `android:usesCleartextTraffic="true"` só aparece nas variantes **debug** e
**debugOptimized** (`android/app/src/{debug,debugOptimized}/AndroidManifest.xml`) — geradas
automaticamente pelo próprio Expo/RN pra suportar o Metro em desenvolvimento, sem nenhuma
relação com o `app.json`. O manifest **main** (usado por *todas* as variantes, inclusive
`release`) não tem o atributo. Como o workflow builda `gradlew assembleRelease` — não
`assembleDebug` —, a APK final nunca herda a permissão de cleartext, e Android (API 28+,
default) bloqueia qualquer chamada HTTP pura, incluindo pro backend local do emulador
(`10.0.2.2`).

**Sem impacto em produção real:** o app de produção (EAS build, ambiente de demo pública) só
fala `https://onda-api...` — HTTPS nunca é afetado por essa política. O bug só existe pra quem
builda uma variante `release` apontando pra um backend HTTP puro, que hoje é só este workflow de
CI.

## Solução
Escopado só ao workflow (`mobile-e2e.yml`), sem tocar `app.json` nem afetar o build de produção:
novo passo logo depois do `expo prebuild`, que escreve
`android/app/src/main/res/xml/network_security_config.xml` (permitindo cleartext só pra
`10.0.2.2`/`localhost`) e injeta `android:networkSecurityConfig="@xml/network_security_config"`
no `<application>` do manifest via `sed`. Validado localmente gerando o manifest e conferindo o
patch antes de gastar outro ciclo de CI.

## Lição
`usesCleartextTraffic` no `app.json` de projetos Expo modernos é config mostrando confiança falsa
— parece resolver o problema, mas nesta versão do SDK simplesmente não é lido. Se aparecer de
novo a necessidade de builds `release` falando com backend HTTP puro fora deste workflow (outro
CI, outro ambiente de teste manual), o caminho certo de longo prazo é o plugin oficial
`expo-build-properties` (não instalado neste projeto) — não reinstalar esse campo morto
esperando que funcione.

## Ligado a
- [[maestro-flows-nunca-validados-ao-vivo]]
- [[maestro-e2e-backend-nao-sobe]]
