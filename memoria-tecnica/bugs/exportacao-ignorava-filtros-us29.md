---
tipo: bug
data: 2026-10-01
severidade: Média
status: Resolvido — CSV e PDF respeitam período e bairro; o PDF do dashboard usa o período da tela
resolvido_em: 2026-10-01
---

# Exportação (US29) ignorava o período e o dashboard perdia um dia à noite

## Sintoma
A US29 pede exportar "respeitando os filtros aplicados". Não respeitava:

- O botão **Exportar** do dashboard mandava só o bairro para `metrics.pdf`, e o endpoint nem
  aceitava período. Quem filtrava "Últimos 7 dias" baixava um PDF com **o histórico inteiro**,
  sem nenhum aviso — a tela dizia uma coisa e o arquivo entregava outra.
- A tela de Relatórios não tinha seletor de período (admitia no texto: "histórico completo").
- O CSV de **transações** ignorava o bairro (a transação não guarda bairro; ele é do pedido).
- O PDF não dizia com que recorte foi gerado: um de 7 dias e um do histórico eram indistinguíveis.

Achado adicional: o dashboard montava as datas com `toISOString().slice(0, 10)` (dia **UTC**),
enquanto o backend interpreta `de`/`ate` em `America/Fortaleza`. Depois das 21h locais, "últimos
7 dias" virava `de` = dia seguinte ao correto e `ate` = amanhã: **perdia um dia e incluía um dia
que ainda nem começou** (22:30 de 01/10 → `25/09..02/10` em vez de `24/09..01/10`).

## Por que passou despercebido
Os testes antigos de download só conferiam o **nome** do arquivo e que `download.path()` existia
(`08-relatorios.spec.ts`), e o teste do PDF olhava `%PDF` e a ausência de "cpf" nos bytes — que
são **comprimidos**, então a busca por "cpf" nunca poderia achar nada.

## Correção
- Backend: `de`/`ate` (yyyy-MM-dd, mesma conversão das métricas) em `reports/{recurso}.csv` e
  `reports/metrics.pdf`. CSV por consultas JPQL de período (faixa fechada, nunca parâmetro nulo —
  o Postgres não infere o tipo) e, em `transactions`, join transação→pedido para o bairro. A
  conversão de dia e o fuso moram em `AdminReportService` (fonte única; o controller usa a mesma
  para `/metrics` e para os dois relatórios). O PDF ganhou no cabeçalho `Período: …` e, com
  bairro, um aviso de que só os números de pedidos o respeitam (US23).
- Painel: `admin/src/utils/periodo.ts` (`filtrosDoPainel`) é a conta única de período+bairro,
  no fuso do negócio, usada pelas métricas e pelas exportações — o arquivo não pode divergir da
  tela. Relatórios ganhou o seletor de período e a opção **Transações** (CSV).

## Provas
- Backend: `AdminReportServiceTest` (serviço pede a consulta certa; PDF com texto **extraído** do
  arquivo: período, bairro, aviso, "Total de pedidos"), `AdminControllerTest` e
  `NotificationControllerTest` (conversão de dia no fuso de Fortaleza, fim exclusivo).
- **Postgres real:** passo 19 do `E2EFluxoPrincipalTest` — dois pedidos em bairros diferentes, cada
  um com sua transação, e cenários de período e bairro isolados e combinados. Mutações na SQL/serviço
  derrubam o passo pelo motivo certo: join sem a igualdade de id ("a transação do outro bairro não
  pode vazar") e `ate` ignorado ("período no passado não traz nada").
- Painel: `10-periodo.spec.ts` (inclui o caso da noite, que a fórmula antiga erra) e
  `11-exportacao-respeita-filtros.spec.ts` (a requisição do PDF do dashboard carrega o **mesmo**
  período das métricas na tela; o CSV baixado só tem linhas dentro da janela). Mutações na tela
  (exportar sem período; Relatórios ignorando o período) derrubam os testes esperados.

## Não coberto / observações
- Só os números de **pedidos** respeitam o bairro no PDF/dashboard (GMV, comissão, disputas, SOS e
  ticket médio continuam da base inteira) — limitação da US23, agora avisada no arquivo.
- Não existe exportação **só de disputas**: `requests.csv` cobre pedidos e disputas (EM_DISPUTA) sem
  filtro de status.
- O `npm run lint` do admin tem 11 erros + 1 aviso em 8 páginas **anteriores** a esta mudança
  (`react-hooks/set-state-in-effect` e afins); o CI roda só o build.

## Ligado a
- [[valor-exibido-vs-cobrado-pix]] — mesma classe: a tela diz uma coisa e o sistema entrega outra.
