---
tipo: bug
data: 2026-09-07
severidade: Alta
status: Resolvido
resolvido_em: 2026-09-07
---

# 3 fluxos Maestro quebrados, nunca detectados — o pipeline nunca tinha rodado vivo

## Sintoma
Depois de corrigir o boot do backend em CI ([[maestro-e2e-backend-nao-sobe]]), disparei o
workflow manualmente (`workflow_dispatch`) pra validar. Pela primeira vez em pelo menos um mês
o job avançou de verdade — instalou o APK, subiu o emulador, rodou o Maestro. E imediatamente
achou 3 bugs reais nos próprios arquivos de teste, nenhum deles vindo do app:

1. `01_cadastro_cliente.yaml` — `Tap on id: checkbox-termos... FAILED / Element not found`.
2. `03_cadastro_prestador.yaml` — nunca tocava o checkbox de termos (achado por leitura de
   código, ainda não observado ao vivo).
3. `05_enviar_proposta.yaml` — preenchia só valor e tocava "Enviar", sem preencher data/hora.

## Causa raiz

**1. Não era testID/acessibilidade quebrada.** A suspeita registrada em memória de sessão
(07/08, "testID não visível ao Maestro no Android nativo") estava errada. O screenshot de debug
do Maestro (baixado do artifact `maestro-reports-53` do run) mostra a causa real: o teclado AOSP
do emulador de CI pergunta *"Allow Android Keyboard (AOSP) to access your contacts?"* — um
diálogo de **sistema**, fora da árvore do app, cobrindo a tela inteira. `testID="checkbox-termos"`
está certinho no código (`RegisterClientScreen.tsx:178`); o elemento só está inalcançável
enquanto o diálogo está por cima.

**Achado extra ao validar o fix (3 iterações):** o diálogo não tem timing fixo. Numa run
apareceu logo após o campo NOME; dispensar só ali "resolveu" aquela run mas deixou o campo SENHA
vazio *em silêncio* na run seguinte (`inputText` reporta `COMPLETED` mesmo digitando no vazio —
o diálogo intercepta a digitação, não só cobre visualmente) — só ficou visível porque o botão
"Criar conta" desabilitado impedia o `assertVisible: "Elétrica"` de passar, não porque Maestro
acusou erro na digitação em si. Uma 3ª run, com screenshot de debug forçado logo após `inputText
${SENHA}`, confirmou o mesmo diálogo aberto **nesse** ponto dessa vez. Não dá pra prever depois
de qual campo ele aparece — a dispensa tolerante precisa ir depois de **cada** campo, não de um
só. Lição dentro da lição: um `tapOn` opcional "resolver" uma run não é prova de causa raiz
correta — só validação depois de uma run **limpa e repetível** conta.

**2 e 3. O pipeline nunca chegou vivo até aqui.** Desde que o guard do canal de alerta passou a
bloquear o boot do backend (~07/08), *nenhuma* run de CI executou de fato os fluxos Maestro —
inclusive os que dependiam de mudanças de contrato feitas depois dessa data:
- US15 (agendamento, commit `9989772`, 13/08) tornou `horarioProposto` obrigatório em
  `SendProposalScreen.tsx` — `dataProposta`/`horaProposta` partem de `useState('')`, sem
  default (diferente de `prazoDias`, que já vem `'2'`). `send()` nem chama a API sem os dois
  preenchidos. **Mesma causa raiz do bug já corrigido no `E2EFluxoPrincipalTest` do backend**
  ([[e2e-fluxo-principal-quebrado]]) — o mesmo commit quebrou um teste do backend e um do
  mobile, só que o do mobile ficou invisível por mais tempo porque o CI nem rodava.
- O registro de aceite de termos (09/08) tornou `acceptedTerms` obrigatório também no cadastro
  de prestador (`RegisterProviderScreen.tsx:33`) — sem aceitar, `handleSubmit` só seta
  `error` e retorna, nunca chama a API. `03_cadastro_prestador.yaml` nunca ganhou esse passo.

## Solução
- `01_cadastro_cliente.yaml` e `03_cadastro_prestador.yaml`: `tapOn: { text: "DENY", optional:
  true }` **depois de cada campo de texto** (nome, CPF quando existe, email, senha) — não só uma
  vez. Tolerante: sem custo relevante quando o diálogo não aparece.
- `03_cadastro_prestador.yaml`: também ganhou o `tapOn: { id: "checkbox-termos" }` que nunca
  existia, antes de selecionar categoria.
- `05_enviar_proposta.yaml`: preenche `input-data-proposta` (`20/12/2026`) e
  `input-hora-proposta` (`14:00`) antes de tocar `btn-enviar-proposta`, mais `hideKeyboard` pra
  não cobrir o botão.

Validado via `workflow_dispatch` na branch da correção, 4 iterações até ficar realmente
repetível — não assumir "deve funcionar" só pela leitura do YAML/código, e não assumir que uma
única run limpa prova a causa raiz certa (ver achado extra acima).

## Lição
Quando um guard de infra (canal de alerta, e antes dele outros) derruba o boot do backend em CI,
o dano real não é só "esse teste específico falha" — é que **toda a superfície de teste
downstream para de ser exercida**, silenciosamente, e nenhuma mudança de contrato posterior é
verificada até alguém notar o vermelho e investigar. Vale, depois de qualquer fix de boot de CI,
checar se há mudanças de contrato feitas *durante* a janela de vermelho que nunca chegaram a ser
testadas de verdade — não assumir que "CI verde de novo" significa "nada mais quebrado".

## Ligado a
- [[maestro-e2e-backend-nao-sobe]]
- [[e2e-fluxo-principal-quebrado]]
