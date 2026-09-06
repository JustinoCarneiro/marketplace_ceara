# Marketplace Ceará - Contrato canônico de trabalho

## Objetivo

Plataforma hiperlocal que conecta clientes a prestadores de serviços
residenciais, com pagamento retido (Escrow) e reputação verificada. Fase 5 da
metodologia OndaDev. Versão adotada: `ONDA_VERSION`.

## Mapa do repositório

| Caminho | Finalidade |
| --- | --- |
| `CLAUDE.md` | Espec Viva: stack, princípios não-funcionais, resumo de épicos, máquina de estados, convenções. |
| `docs/spec.md` | Histórias de usuário completas e critérios de aceite BDD. |
| `ROADMAP.md` | Blueprint técnico: módulos, pesos, contratos. |
| `backend/` | Spring Boot 3 + Java 21 (Maven, Virtual Threads); PostgreSQL + PostGIS. |
| `mobile/` | React Native + Expo (EAS Build). |
| `admin/` | Painel web React + Vite (mediação, moderação, reconciliação). |
| `memoria-tecnica/` | Bugs cabeludos e decisões fora da spec; consulte antes de investigar. |
| `design/` | `tokens.css` + `DESIGN.md`. |
| `scripts/jira_sync.py` | Sincronização pontual de issues no board Jira `MKT`; nunca automática. |
| `.agents/`, `.claude/` | Skills dos agentes. |
| `docker-compose*.yml` | PostGIS, backend, admin locais. |

## Autoridade da informação

| Assunto | Fonte canônica | Papel das demais fontes |
| --- | --- | --- |
| Escopo, histórias e aceite | `CLAUDE.md` + `docs/spec.md` | Jira (board `MKT`) apenas reflete o status. |
| Ordem técnica e progresso | `ROADMAP.md` | Jira é projeção visual. |
| Decisão de arquitetura | `memoria-tecnica/decisoes/` | — |
| Dados sensíveis e LGPD | `CLAUDE.md` (Princípios) | Nenhuma tarefa pode contrariar. |
| Código e histórico versionado | Git | GitHub registra PRs, revisão e CI. |
| Trabalho externo | Jira/GitHub | Nunca sobrescreve a verdade local sem decisão explícita. |

Jira é uma projeção do status, nunca o bloqueio da edição local. A spec muda
primeiro nos arquivos; o board `MKT` é acertado depois, à mão na UI ou com
`scripts/jira_sync.py` para lotes pontuais — **nunca disparado automaticamente
por edição de doc**. Exclusão de issue exige confirmação explícita.

## Comandos verificados

```bash
# Backend (Java 21 + Maven)
mvn -f backend/pom.xml test --no-transfer-progress

# Admin (React + Vite)
cd admin && npm ci
cd admin && npm run lint
cd admin && npm run build

# Mobile (React Native + Expo)
cd mobile && npm ci
cd mobile && npx tsc --noEmit

# Ambiente local
docker compose up -d
docker compose config
```

## Fronteiras e convenções

- **Diretiva Primária:** não altere a sintaxe ou o comportamento de código
  existente sem um teste que justifique a quebra (ciclo TDD).
- **Escrow nunca em `@Transactional` sobre o gateway** — Saga + Outbox +
  idempotência + reconciliação por webhook. Estado financeiro dirigido por
  eventos confirmados.
- Geobusca por PostGIS (índice GiST), não haversine em SQL puro.
- Idempotência em todo endpoint que move dinheiro ou cria pedido.
- API REST `/api/v1`, JSON, erros via `@ControllerAdvice`.
- DTOs como Records; controller nunca expõe entidade.
- Multimídia em object storage; banco guarda só a URL.
- IA nunca é caminho crítico bloqueante — fallback manual obrigatório.
- Documentação em português claro; nomes técnicos no idioma da tecnologia.

## Segurança e classes de risco

CPF e dados sensíveis criptografados em repouso; mínimo necessário trafegado.
Nunca versione, exiba em log ou cole em prompt: tokens, chaves, senhas, dados
pessoais reais ou exports. Use `.env` local (`.env`, `.env.homolog`,
`.env.prod.example`, `.env.jira` não versionados como segredo).

| Nível | Exemplos | Regra |
| --- | --- | --- |
| R0 | Leitura, docs, testes locais | Executar e validar normalmente. |
| R1 | Código, dependência, schema, migration, CI, configuração compartilhada | Declarar impacto, testar e pedir revisão de diff. |
| R2 | Produção, Escrow/pagamento, credenciais, PII, deploy, exclusão e escrita externa | Exigir autorização explícita e alvo confirmado. Plano + revisão cruzada. |

## Definition of Done

1. atende a uma história de `docs/spec.md` ou escopo escrito com critérios verificáveis;
2. executa os testes que existem (backend `mvn test`, admin `lint`+`build`,
   mobile `tsc --noEmit`, e os E2E quando aplicável) e reporta o resultado;
3. atualiza `CLAUDE.md`, `docs/spec.md`, `ROADMAP.md` ou `memoria-tecnica/`
   quando o contrato mudou;
4. não introduz segredo, credencial ou PII no repositório;
5. passa por revisão proporcional ao risco e deixa um diff compreensível;
6. registra handoff com mudanças, validações, decisões, riscos e pendências.

Não afirme que testes, CI, deploy ou sincronização passaram sem evidência.

## Revisão e handoff entre agentes

Claude e Codex seguem este arquivo como núcleo comum. Um autor por PR; o outro
revisa o diff quando o risco (R1/R2) exige, com o mínimo suficiente (contrato,
diff, logs de teste). Quando a cota de um agente acaba, o outro assume por
handoff — protocolo na metodologia OndaDev 3.0 (`ONDA_VERSION`).

Síntese de handoff:

```text
Escopo: …
Mudanças: …
Validações executadas e resultado: …
Decisões/ADRs: …
Riscos, bloqueios e próximos passos: …
```
