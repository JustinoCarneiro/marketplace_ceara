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
- **Entrada limitada** a 2000 caracteres e enviada como dado dentro de `<relato>`, com instrução
  para ignorar comandos contidos nele (mitiga injeção de prompt; não elimina).
- **Saída não confiável:** exige descrição não vazia, faixas numéricas, `0 <= min <= max <= 1.000.000`;
  descrição truncada em 2000 caracteres. A faixa é só sugestão e continua editável pelo cliente.
- Logs registram só o tipo da falha, nunca o texto do usuário nem o corpo da resposta.

## Limites conhecidos (decisão pendente)

- **Privacidade/LGPD:** o relato do cliente sai para o provedor. Antes de dados reais, definir
  provedor, contrato de tratamento e retenção; o relato pode conter dado pessoal digitado livremente.
- **Texto apenas:** áudio e foto (`mediaUrls`) ainda não são enviados ao modelo.
- **Sem limite por usuário:** um cliente autenticado pode gerar custo. Definir limite de taxa
  antes de ativar em produção.
- Não validado contra o serviço real (sem chave); testes usam servidor HTTP simulado.
