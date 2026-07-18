SHELL := /bin/bash

COMPOSE_BIN := $(shell if docker compose version >/dev/null 2>&1; then printf '%s' 'docker compose'; elif command -v docker-compose >/dev/null 2>&1; then printf '%s' 'docker-compose'; else printf '%s' 'docker compose'; fi)
COMPOSE := $(COMPOSE_BIN) -f scx-backend/docker-compose.yml

.PHONY: bootstrap dev-up dev-down dev-reset mvp-local-up mvp-db-migrate backend-build backend-test frontend-build frontend-check contract-check migration-packaging-check prod-secret-injection-check db-verify

bootstrap:
	@echo "Local dependencies use the fixed development values in docker-compose.yml."

dev-up: bootstrap
	$(COMPOSE) up -d smart-converge-x-mysql smart-converge-x-redis smart-converge-x-rocketmq-namesrv smart-converge-x-rocketmq-broker smart-converge-x-minio smart-converge-x-callback-mock

dev-down:
	$(COMPOSE) down

dev-reset:
	$(COMPOSE) down -v

# Starts only isolated local dependencies and applies all MVP database migrations.
mvp-local-up: dev-up mvp-db-migrate

# Only upgrades the local Compose database already created by `make dev-up`.
# It intentionally targets loopback and never reads application-dev.yml.
mvp-db-migrate:
	cd scx-backend/db/smart-converge-x && MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USERNAME=root MYSQL_PASSWORD=scx_local_only SCX_SKIP_BASELINE=true SCX_MIGRATION_BASELINE_VERSION=auto ./init-database.sh

backend-build:
	cd scx-backend && mvn -pl scx-boot -am -DskipTests package

backend-test:
	cd scx-backend && mvn -pl scx-boot -am test

frontend-build:
	cd scx-fronted && pnpm build:ele

frontend-check:
	cd scx-fronted && pnpm check:type && pnpm lint && pnpm test:unit

contract-check:
	bash ./scripts/check-contracts.sh

migration-packaging-check:
	bash ./scripts/check-migration-packaging.sh

prod-secret-injection-check:
	bash ./scripts/check-prod-secret-injection.sh

db-verify:
	cd scx-backend/db/smart-converge-x && ./verify-database.sh
