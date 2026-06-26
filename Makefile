# Marketplace Ceará — Makefile
# Orquestra o ambiente de homologação local completo.
#
# Pré-requisitos: Docker 24+ com Compose v2 (docker compose, não docker-compose).

COMPOSE_FILE := docker-compose.homolog.yml
ENV_FILE     := .env.homolog
DC           := docker compose -f $(COMPOSE_FILE) --env-file $(ENV_FILE)

.PHONY: homolog-build homolog-up homolog-down homolog-logs homolog-ps homolog-reset \
        db-up dev-backend dev-admin

# ── Homologação completa (todos os serviços em Docker) ───────────────────────

homolog-build:           ## Rebuild das imagens sem cache
	$(DC) build --no-cache

homolog-up:             ## Sobe todos os serviços em background
	$(DC) up -d --build

homolog-down:           ## Para e remove containers (mantém volumes)
	$(DC) down

homolog-logs:           ## Acompanha logs em tempo real (Ctrl+C para sair)
	$(DC) logs -f

homolog-ps:             ## Exibe status dos containers
	$(DC) ps

homolog-reset:          ## Para, remove containers E volumes (banco zerado)
	$(DC) down -v
	$(DC) up -d --build

# ── Modo dev (só banco no Docker, backend e admin locais) ────────────────────

db-up:                  ## Sobe apenas o banco + pgAdmin
	docker compose up -d

dev-backend:            ## Inicia o backend com Maven (usa .env para vars)
	cd backend && export $$(grep -v '^#' ../.env | xargs) && mvn spring-boot:run

dev-admin:              ## Inicia o painel admin com Vite dev server
	cd admin && npm run dev

dev-mobile:             ## Sobe tudo + tunnel + rebuild APK se URL mudou
	@bash scripts/dev-mobile.sh

update-mobile:          ## Publica atualização JS/telas sem rebuildar o APK (~30s)
	cd mobile && ~/.npm-global/bin/eas update --branch preview --message "$(msg)"

# ── Ajuda ─────────────────────────────────────────────────────────────────────
help:                   ## Lista todos os targets disponíveis
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

.DEFAULT_GOAL := help
