# Assistente de IA do pedido (US14)

A sugestão de descrição e faixa de orçamento usa a API Gemini via `GeminiAiSuggestionService`.
**Desligada por padrão**: sem ativação o app usa só o fluxo manual (`source: FALLBACK_MANUAL`).

## Ativar

Variáveis protegidas do backend (Coolify; nunca no Git, chat ou log):

```dotenv
MARKETPLACE_AI_ENABLED=true
MARKETPLACE_AI_API_KEY=<chave-inserida-no-ambiente-protegido>
MARKETPLACE_AI_MODEL=gemini-2.5-flash
```

Opcionais: `MARKETPLACE_AI_BASE_URL`, `MARKETPLACE_AI_TIMEOUT_MS` (padrão 8000).
O compose só repassa o que está listado em `environment:`; as três acima já estão.

## Garantias de código

- **Nunca bloqueia:** rede, timeout, 4xx/5xx, cota (429), bloqueio de segurança do modelo, JSON
  inválido ou valores implausíveis devolvem `FALLBACK_MANUAL`; nenhuma exceção chega ao cliente.
- **Chave só em cabeçalho** (`x-goog-api-key`), nunca na URL nem no corpo.
- **Minimização de dados / LGPD (`AiInputSanitizer`):** antes de qualquer chamada ao modelo,
  e-mails, telefones (com/sem DDD ou código de país), CPFs (formatados ou apenas dígitos) e sequências
  numéricas longas (ex.: números de cartão) são automaticamente detectados e substituídos por máscaras
  neutras (`[telefone removido]`, `[cpf removido]`, etc.).
- **Proteção contra abuso e controle de custo (`AiRateLimiter`):** limitador de taxa em memória por janela
  deslizante configurável em 3 níveis:
  - Limite horário por usuário (`MARKETPLACE_AI_RATE_LIMIT_USER_HOURLY`, padrão: 5).
  - Limite diário por usuário (`MARKETPLACE_AI_RATE_LIMIT_USER_DAILY`, padrão: 20).
  - Limite diário global (`MARKETPLACE_AI_RATE_LIMIT_GLOBAL_DAILY`, padrão: 200).
  Ao estourar o limite, a chamada aciona graciosamente o fallback manual (`FALLBACK_MANUAL`), sem erro HTTP.
- **Entrada limitada** a 2000 caracteres e enviada como dado dentro de `<relato>`, com instrução
  para ignorar comandos contidos nele (mitiga injeção de prompt; não elimina).
- **Saída não confiável:** exige descrição não vazia, faixas numéricas, `0 <= min <= max <= 1.000.000`;
  descrição truncada em 2000 caracteres. A faixa é só sugestão e continua editável pelo cliente.
- Logs registram só o tipo da falha, nunca o texto do usuário nem o corpo da resposta.

## Limites conhecidos e pendências antes de ligar em produção

- **Contrato de subprocessador (LGPD):** o relato sanitizado é enviado ao provedor de IA.
  Antes de ativar em produção, firmar contrato comercial/DPA com o provedor (Google Cloud / Vertex AI)
  garantindo não retenção e não uso dos dados de clientes para treinamento de modelos
  (registrado em `docs/PENDENCIAS_JURIDICAS.md`).
- **Texto apenas:** áudio e foto (`mediaUrls`) não são enviados ao modelo.
- Não validado contra o serviço real (sem chave); testes usam servidor HTTP simulado.

