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
4. `03_cadastro_prestador.yaml` — tocava um botão "Cadastrar" que nunca existiu na tela; o botão
   real é "Enviar para verificação" (`RegisterProviderScreen.tsx:214`).
5. `03_cadastro_prestador.yaml` — `assertVisible: "Criar conta"` nunca casava nessa tela, mesmo
   com 15s de espera e o texto visivelmente na tela no screenshot de debug (não era timing).
   Causa: `RegisterClientScreen.tsx` tem um botão PRÓPRIO com o texto exato "Criar conta" (linha
   222, nó de acessibilidade simples e isolado) — é ele, não o título, quem satisfazia o
   `assertVisible` de `01_cadastro_cliente.yaml`. `RegisterProviderScreen.tsx` só tem "Criar
   conta" dentro de um `<Text>` aninhado no título ("Criar conta" + `<Text>`"prestador"`</Text>`,
   linhas 63-66) — nunca existiu um nó isolado com esse texto nessa tela, tocável ou não. Trocado
   por `"CPF"`, rótulo simples e exclusivo desta tela.
6. `03_cadastro_prestador.yaml` — `checkbox-termos` continuava "not found" mesmo depois do
   `hideKeyboard` que resolveu o mesmo sintoma em `01_cadastro_cliente.yaml`. Causa real, visível
   no screenshot: o formulário de prestador tem **BIO** (textarea) e **LOCALIZAÇÃO DE
   ATENDIMENTO** entre a categoria e o checkbox (`RegisterProviderScreen.tsx:147-169`, campos que
   não existem na tela de cliente) — conteúdo mais alto que a tela, checkbox abaixo da dobra
   mesmo sem teclado. `scrollUntilVisible` em vez de mais um `hideKeyboard`/wait.

## Causa raiz

**1. Não era testID/acessibilidade quebrada.** A suspeita registrada em memória de sessão
(07/08, "testID não visível ao Maestro no Android nativo") estava errada. O screenshot de debug
do Maestro (baixado do artifact `maestro-reports-53` do run) mostra a causa real: o teclado AOSP
do emulador de CI pergunta *"Allow Android Keyboard (AOSP) to access your contacts?"* — um
diálogo de **sistema**, fora da árvore do app, cobrindo a tela inteira. `testID="checkbox-termos"`
está certinho no código (`RegisterClientScreen.tsx:178`); o elemento só está inalcançável
enquanto o diálogo está por cima.

**Achado extra ao validar o fix — 8 iterações até fechar de verdade.** O diálogo não tem timing
fixo, e isso levou a duas hipóteses erradas antes da causa raiz completa:

- *Runs 1-2:* dispensar só depois do campo NOME "resolveu" uma run, mas deixou SENHA vazio **em
  silêncio** na seguinte (`inputText` reporta `COMPLETED` mesmo digitando no vazio — o diálogo
  intercepta a digitação, não só cobre visualmente). Só ficou visível porque o botão "Criar
  conta" desabilitado impedia `assertVisible: "Elétrica"`, não porque Maestro acusou erro na
  digitação em si.
- *Runs 3-4:* suspeitei de corrida de foco no campo e troquei o seletor de texto de placeholder
  por `testID="input-senha"` (mudança de código aditiva, mantida — é mais robusta de qualquer
  forma) e tentei tap duplo. Nenhum dos dois resolveu; a árvore de acessibilidade nativa do
  device (baixada dos artifacts) confirmou `focused: "false"` e nenhum texto no campo mesmo com
  Maestro reportando tudo `COMPLETED`.
- *Runs 5-6, causa raiz confirmada:* screenshot de debug forçado logo após o tap em
  `input-senha` (antes até de chamar `inputText`) mostrou **o mesmo diálogo de contatos aberto
  nesse ponto**. Ou seja: o diálogo pode aparecer como reação ao FOCO de qualquer campo, não só
  depois de digitar nele — inclusive no campo de senha, que não tem nada a ver com contatos.

Não dá pra prever depois de qual evento (foco ou digitação, em qual campo) o diálogo aparece —
a dispensa tolerante precisa ir **antes E depois de cada `inputText`**, nos 3 campos, não só
depois.

- *Runs 7-8, SEGUNDA causa raiz (coexistindo com a do diálogo — duas causas diferentes, não uma):*
  mesmo numa run em que **nenhuma** das 6 dispensas de "DENY" encontrou o diálogo (nenhuma
  interferência dele), o fluxo ainda falhava no mesmo `assertVisible: "Elétrica"`. O screenshot
  dessa run mostra a prova direta: o campo E-MAIL exibia `lucia.teste@onda.devsenha1234` — a
  senha foi digitada **dentro do campo de e-mail**. O `tapOn: { id: "input-senha" }` não estava
  de fato tirando o foco do campo anterior; é uma corrida entre o layout ainda se ajustando à
  troca de teclado (email-address → padrão/secureTextEntry) e o toque calculado em cima dos
  bounds de uma captura de hierarquia que ainda não refletia o layout assentado. `hideKeyboard`
  antes de tocar em `input-senha` força esse assentamento.

- *Run 9, TERCEIRA causa raiz:* com as duas primeiras corrigidas, o screenshot dessa run mostra
  a senha certinha (pontos mascarados + medidor de força "Boa") — mas o teclado continuava
  aberto, cobrindo a linha do checkbox de termos por completo. `Tap on id: checkbox-termos...
  FAILED` de novo, só que agora por um motivo trivial: elemento fora da área alcançável, não
  diálogo nem corrida de foco. `hideKeyboard` antes de procurar o checkbox resolve.

Lição dentro da lição: um `tapOn` opcional "resolver" uma run não é prova de causa raiz correta,
uma hipótese plausível (corrida de foco, runs 3-4) confirmada por evidência real (árvore de
acessibilidade) ainda pode ser a hipótese errada, e **o mesmo sintoma final pode ter mais de uma
causa raiz diferente, reveladas em camadas** — cada fix corrigia a run o suficiente pra chegar
até a camada seguinte (diálogo → corrida de foco/teclado → teclado cobrindo o alvo), nunca
todas de uma vez. Só uma run **limpa e repetível**, com a causa raiz fotografada no exato momento
do sintoma, fecha cada camada — e "resolvido" só depois de uma run inteira sem nenhuma
interferência reportada.

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
  true }` **antes E depois de cada `inputText`** (nome, CPF quando existe, email, senha) — cobre
  o diálogo aparecendo tanto no foco quanto durante/depois da digitação, em qualquer campo.
  Tolerante: sem custo relevante quando o diálogo não aparece (~3s de busca por tentativa).
- `RegisterClientScreen.tsx` e `RegisterProviderScreen.tsx`: `testID="input-senha"` adicionado
  ao campo de senha (mudança aditiva, sem alterar comportamento) — seletor por id em vez de texto
  de placeholder, mais robusto independente do bug do diálogo.
- `01_cadastro_cliente.yaml` e `03_cadastro_prestador.yaml`: `hideKeyboard` entre o `inputText`
  do e-mail e o `tapOn` em `input-senha` — resolve a 2ª causa raiz (corrida de layout na troca
  de teclado, ver achado das runs 7-8).
- `01_cadastro_cliente.yaml` e `03_cadastro_prestador.yaml`: `hideKeyboard` depois do `inputText`
  da senha, antes de procurar `checkbox-termos` — resolve a 3ª causa raiz (teclado ainda aberto
  cobrindo a linha do checkbox, ver achado da run 9).
- `03_cadastro_prestador.yaml`: também ganhou o `tapOn: { id: "checkbox-termos" }` que nunca
  existia, antes de selecionar categoria.
- `05_enviar_proposta.yaml`: preenche `input-data-proposta` (`20/12/2026`) e
  `input-hora-proposta` (`14:00`) antes de tocar `btn-enviar-proposta`, mais `hideKeyboard` pra
  não cobrir o botão.

Validado via `workflow_dispatch` na branch da correção — 8 iterações até fechar de verdade (ver
achado extra acima). Não assumir "deve funcionar" só pela leitura do YAML/código, e não assumir
que uma única run limpa prova a causa raiz certa.

## Lição
Quando um guard de infra (canal de alerta, e antes dele outros) derruba o boot do backend em CI,
o dano real não é só "esse teste específico falha" — é que **toda a superfície de teste
downstream para de ser exercida**, silenciosamente, e nenhuma mudança de contrato posterior é
verificada até alguém notar o vermelho e investigar. Vale, depois de qualquer fix de boot de CI,
checar se há mudanças de contrato feitas *durante* a janela de vermelho que nunca chegaram a ser
testadas de verdade — não assumir que "CI verde de novo" significa "nada mais quebrado".

**Achado adicional (01/03 já verdes, avançando pra 04):** `04_criar_pedido.yaml` usa
`subflows/login_cliente.yaml`, um arquivo compartilhado por vários fluxos (02, 04, 05...) que
nunca tinha recebido o tratamento do diálogo AOSP — só os arquivos de cadastro (01/03) tinham
sido corrigidos até aqui. Mesmo diálogo, mesmo sintoma ("Entrar" não encontrado).

**Regressão introduzida e corrigida na hora:** copiei o padrão de 01/03 de cabeça, incluindo um
`hideKeyboard` antes do `tapOn: "Entrar"` — sem evidência de que fosse necessário aqui (diferente
das telas de cadastro, a de login é curta: e-mail + senha + botão, sem BIO/CPF/chips empurrando
conteúdo). Resultado real, visto no screenshot da run seguinte: o app **voltou pra tela Splash**
— não é que "Entrar" ficasse coberto, o app simplesmente não estava mais na tela de login.
Causa: `hideKeyboard` do Maestro cai pra um `BACK` de sistema como fallback quando não há teclado
de fato aberto no momento (o dismiss do diálogo AOSP, alguns instantes antes, já tinha fechado o
teclado como efeito colateral) — e a tela de Login não intercepta esse back, então ele navega de
volta pra Splash. `hideKeyboard` **removido** de `login_cliente.yaml`/`login_prestador.yaml`
(mantido em 01/03, onde já está confirmado necessário e correto por runs verdes).

**Lição:** copiar um fix que funcionou num arquivo pra outro arquivo parecido, sem evidência
própria de que o mesmo problema existe ali, pode introduzir uma regressão nova em vez de
resolver algo — cada `hideKeyboard`/passo defensivo precisa da sua própria justificativa
observada, não só "funcionou lá, deve funcionar aqui também".

**Achado adicional (com login corrigido, 03 fica flaky de novo):** depois do fix do
`hideKeyboard`/Splash, `01` continuou verde mas `03_cadastro_prestador.yaml` voltou a falhar —
dessa vez em `Tap on "Enviar para verificação"... FAILED`. Screenshot mostra o app na tela
**"Termos de Uso"** (`LegalScreen`), não no formulário. O `tapOn: id: "checkbox-termos"` (depois
do `scrollUntilVisible`) tocou no link "Termos de Uso" aninhado dentro do mesmo
`TouchableOpacity` do checkbox, em vez do quadrado do checkbox em si — o tap no centro da linha
inteira corre risco de cair sobre o link se a linha ocupar mais espaço vertical (ex.: depois de
scroll, ou se quebrar em 2 linhas). `RegisterClientScreen.tsx` tem a mesma estrutura e não tinha
mostrado esse sintoma ainda (sorte de layout, não ausência do risco).

Primeira tentativa: `point: "10%,50%"` — **também errado**, caiu em "Política de Privacidade" na
run seguinte (chutado sem medir, só "mais pra esquerda"). Segunda tentativa, calculada a partir
do style real em vez de chute: `checkbox { width: 20 }` + `termsRow { gap: 10 }`, linha com
largura ≈345dp (tela 393dp de um Pixel 3a − `paddingHorizontal: space[5]` × 2 = 24dp × 2) → o
quadrado ocupa só os primeiros ~5,8% da largura da linha. `point: "4%,50%"` mira o centro do
quadrado com folga dos dois lados — dentro da faixa seguro, não no chute redondo "10%".

**Achado adicional (01/03 verdes e estáveis, avançando pra 04 de verdade):** login em
`subflows/login_cliente.yaml` completou (`COMPLETED`), mas o pedido falhou logo depois em
`Tap on "Novo pedido"`. Screenshot mostra o app **de volta na tela de login**, com erro "senha:
must not be blank" e o campo E-MAIL mostrando `...devsenha1234` — a mesmíssima corrida de layout
já resolvida em `01_cadastro_cliente.yaml` (email→senha, teclado ainda se ajustando), só que o
`hideKeyboard` entre esses dois campos nunca tinha sido replicado pros subflows de login (só o
de antes do botão "Entrar" foi mexido, e esse foi removido por outro motivo). Adicionado
`hideKeyboard` entre `inputText` do e-mail e o tap no campo de senha, nos dois subflows —
posição seguro porque o teclado está garantidamente aberto ali (acabou de digitar), diferente do
caso antes de "Entrar" onde não havia teclado pra fechar de verdade.

**Achado adicional (login preenche certo, mas "Entrar" oscila coberto/livre):** com o
`hideKeyboard` entre e-mail e senha resolvendo a corrida de digitação, a run seguinte mostrou —
por screenshot — os dois campos preenchidos certinho e o **teclado aberto cobrindo "Entrar"**.
Ou seja, a suposição anterior ("tela de login é curta, Entrar nunca fica coberto") estava errada:
o estado do teclado nesse ponto é tão não-determinístico quanto o diálogo AOSP — às vezes fecha
sozinho antes de chegar aqui (caso em que `hideKeyboard` incondicional já causou a regressão pro
Splash), às vezes continua aberto cobrindo o botão (caso em que falta `hideKeyboard`). As duas
causas são reais e se alternam entre runs — não dá pra resolver com uma chamada incondicional
nos dois sentidos.

Fix: tenta tocar "Entrar" direto primeiro (`optional: true`); só recorre a `hideKeyboard` +
retry dentro de um `runFlow: when: visible: "Bom te ver de novo"` — condicionado a ainda estar
comprovadamente na tela de login. Depois de um login bem-sucedido esse título nunca está mais
visível, então o bloco condicional nunca dispara ali, eliminando o risco do BACK indevido no
caminho de sucesso.

**Achado adicional (login 100% verde, chegando na Home pela 1ª vez):** com o subflow de login
resolvido, `04_criar_pedido.yaml` finalmente chegou na Home — e `tapOn: "Novo pedido"` falhou.
Screenshot mostra a Home normal, logada, com o botão real chamado **"Criar pedido"**
(`HomeScreen.tsx`); "Novo pedido" só existe como título da tela seguinte
(`NewRequestScreen.tsx:164`), nunca como botão na Home — mesma classe de erro de `03` ("Cadastrar"
que nunca existiu): suposição de texto nunca validada ao vivo. Corrigido pra `tapOn: "Criar
pedido"`; a `assertVisible: "Novo pedido"` logo depois continua certa (é o título da tela pra
onde se navega).

**Achado adicional (Home resolvida, chegando no formulário de pedido):** `tapOn: "Criar
pedido"` e a categoria "Elétrica" funcionaram, mas o tap no placeholder da descrição
("A tomada da cozinha") deu "Element not found" com o campo claramente renderizado no
screenshot de debug. `extendedWaitUntil: visible: "DESCRIÇÃO"` **não resolveu** — o label
aparece instantâneo (0,15s), não era render lento.

Investigação mais funda: a dump de hierarquia de acessibilidade capturada no exato momento
da falha mostra o campo certinho — `hintText` com o texto completo, `clickable: true`,
bounds válidos — mas o `tapOn` por texto nunca encontrava durante os ~18s de busca ativa do
Maestro. Suspeita (não 100% confirmada): `TextInput` com `multiline` (`NewRequestScreen.tsx:207`)
não casa de forma confiável por conteúdo de hint via `tapOn` de texto nesta versão do Maestro,
mesmo quando a dump final da hierarquia mostra o atributo presente — só a captura de debug (no
momento de desistir) via uma leitura de árvore diferente da busca incremental em si. `testID=
"input-descricao"` adicionado ao campo (mudança aditiva) e o seletor trocado pra `id:` — mesmo
padrão já usado pra senha/checkbox neste projeto quando texto de placeholder se mostra pouco
confiável.

**Achado adicional (descrição resolvida, "Continuar" coberto):** com o testID, a descrição
digitou certinho — e o próximo passo, "Continuar", falhou com o teclado visivelmente aberto no
screenshot (com a barra de sugestão de contatos do Android, a mesma feature por trás do diálogo
AOSP). Mesmo padrão condicional já usado em `login_cliente.yaml`: tenta tocar direto primeiro,
só chama `hideKeyboard` dentro de `runFlow: when: visible: "Novo pedido"` — evita o risco de
BACK indevido se o teclado já estiver fechado nessa tentativa.

**Achado adicional (gap estrutural, não flakiness):** com "Continuar" resolvido, o próximo
`assertVisible: "Pedido criado!"` falhou de verdade — screenshot mostra a tela **"Revisar com
a IA"** (sugestão da IA + botão "Confirmar e publicar pedido"), uma tela inteira que o fluxo
nunca contemplava. Diferente de todos os achados anteriores desta nota (timing/seletor), esse é
um passo genuinamente faltando: `NewRequestScreen` → `IA review` → `RequestCreatedScreen`, não
`NewRequestScreen` → `RequestCreatedScreen` direto. Adicionado `assertVisible: "Revisar com a
IA"` + `tapOn: "Confirmar e publicar pedido"` entre os dois. Sem risco de teclado aqui — tela
sem nenhum campo de texto.

## Ligado a
- [[maestro-e2e-backend-nao-sobe]]
- [[e2e-fluxo-principal-quebrado]]
