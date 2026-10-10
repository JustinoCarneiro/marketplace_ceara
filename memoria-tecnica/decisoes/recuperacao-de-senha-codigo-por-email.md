---
tipo: decisao
data: 2026-10-01
status: Ativa
---

# Recuperação de senha por código enviado ao e-mail (US35)

## Contexto
O link "Esqueci a senha" do login mostrava só um alerta "Em breve" (que no react-native-web nem
aparece): quem esquecia a senha ficava sem saída, a não ser o suporte. A spec não tinha a história;
foi criada como US35.

## Decisão
Código de 8 caracteres enviado ao e-mail, digitado no app, em vez de um link.

**Por que código e não link:** o link teria que abrir o app (deep link). Esquema próprio
(`onda://…`) não é clicável na maioria dos clientes de e-mail, e link universal exige domínio — que
o projeto ainda não tem (a demo roda em `sslip.io`). O código funciona hoje, em qualquer cliente de
e-mail, e dá para trocar por link depois sem mexer na regra de segurança.

## Desenho de segurança (OWASP Forgot Password)
| Ameaça | Defesa |
|---|---|
| Descobrir quem tem conta | Resposta única (202 com o mesmo texto) para e-mail cadastrado, desconhecido, suspenso e limite excedido; e-mail enviado **depois do commit e em outra thread** (não aparece no tempo de resposta); piso de 300 ms de resposta; erro do código sem distinguir o motivo (`INVALID_RESET_CODE`); o HMAC é calculado mesmo sem usuário |
| Código vazado do banco | Só o HMAC-SHA256 é gravado (chave = `jwt.secret`, domínio `pwd-reset:v1`, usuário no cálculo). 40 bits de código não resistiriam a um hash simples; com HMAC o dump não entrega código válido |
| Código vazado em log | Evento e DTO com `toString` sem conteúdo; log de falha de envio só com a classe da exceção; o e-mail de teste vai para arquivo, nunca para log |
| Força bruta | 5 erros invalidam o código; validade de 30 min; uso único; a consulta do código **trava a linha** (`PESSIMISTIC_WRITE`) para palpites paralelos não lerem o mesmo contador |
| Inundar a caixa de terceiros | No máximo 3 códigos por hora por usuário; um pedido novo invalida o anterior |
| Sessão do atacante continuar | A troca revoga **todos** os refresh tokens e manda e-mail "sua senha foi alterada" |
| Conta suspensa se recuperar | Suspensa não recebe código (e login/refresh a recusam — ver `usuario-suspenso-continuava-entrando.md`) |
| Senha que o BCrypt truncaria | Teto de 72 **bytes**, conferido antes de consumir tentativa |

Alfabeto do código: Crockford base32 (`0-9 A-Z` sem I, L, O, U), 32^8 = 2^40. O que o usuário digita é
normalizado (minúsculas, hífen, O→0, I/L→1).

## Armadilhas que os testes de serviço (com repositório mockado) NÃO pegam
- **O contador de tentativas precisa persistir mesmo com a exceção.** `BusinessException` desfaz a
  transação; sem `@Transactional(noRollbackFor = BusinessException.class)` o erro nunca era gravado e o
  limite de 5 não valia. Provado no E2E contra o Postgres (sem o `noRollbackFor`: `attempts` fica 0).
- **A trava de escrita só se prova com duas transações de verdade.** Um teste com 30 requisições
  paralelas passou **mesmo sem a trava** (as requisições chegam espaçadas demais); por isso o passo 26
  do E2E segura a linha numa transação e exige que a segunda espere.

## Dependências e limites assumidos
- **Exige SMTP.** Sem `MAIL_USERNAME`/`MAIL_PASSWORD` o servidor responde `PASSWORD_RESET_UNAVAILABLE` e
  o app avisa, em vez de prometer um e-mail que não sai. A demo da VPS está nessa situação até haver SMTP.
  Em dev/CI, `NOTIFICATION_MAIL_SINK_DIR` grava o e-mail em arquivo (o SMTP real tem prioridade); **nunca**
  em produção — o código ficaria legível em disco.
- **Sem limite por IP.** Não há infraestrutura de rate limit; o limite é por usuário. Um atacante pode
  ainda testar muitos e-mails (sem aprender nada com as respostas). Vale um limite por IP no proxy
  (nginx) quando houver tráfego real.
- O JWT já emitido continua válido até expirar (até 15 min); só o refresh é cortado.
- Administradores (painel interno) ficam fora: a senha deles é redefinida pelo suporte/operação.

## Rodada 3 de revisão cruzada (2026-10-10) — sessão emitida em voo sobrevivia à troca
- **O defeito (provado em Postgres real, E2E 69):** "troca encerra as sessões" era um `UPDATE` (`revogarTodosDoUsuario`) que só enxerga as
  linhas JÁ commitadas no instante dele. Uma renovação (ou login) em voo — que consumiu o token antigo e inseriu o novo, sem commitar — faz o
  `UPDATE` esperar a linha antiga e, ao acordar, não ver a nova: o token novo seguia válido por 30 dias depois de uma troca de senha feita para
  encerrar sessões. Quem tem um refresh roubado e renova em laço consegue isso de propósito; a janela é a duração da transação de renovação.
- **O conserto (V28):** cada refresh token grava a **impressão da senha** vigente na emissão (`senha_fp` = sha256 do hash bcrypt, separado por
  domínio, truncado a 32) e o consumo (renovar, trocar de papel, virar prestador — caminho único `consumirRefresh`) recusa o token cuja
  impressão não bate com a senha atual. Não depende de ordem de transações nem de travas: o token que nasce com a senha antiga já nasce inválido.
  `NULL` = sessão anterior à migration, continua valendo (o deploy não desloga ninguém; essas sessões seguem sujeitas à corrida até expirarem, ≤ 30 dias).
- **Não coberto:** o access token já emitido segue valendo até expirar (≤ 15 min), como antes — fechar isso exigiria consultar o banco a cada requisição.
- Prova: unitários (`AuthServiceTest`: emissão grava a impressão, senha trocada recusa sem consumir nem emitir, `switchRole` pelo mesmo caminho) e E2E 69
  (renovação segurada em voo × troca de senha; controle positivo: login com a senha nova renova). Mutação — emitir sem a impressão; consumir sem conferir —
  derruba unitário e E2E.

## Ligado a
- US35 e US26 em `docs/spec.md`; contrato em `ROADMAP.md` (M01); migração `V20__password_reset_codes.sql`.
