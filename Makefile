SHELL := /bin/bash

COMPOSE := docker compose -f scx-backend/docker-compose.yml

.PHONY: bootstrap dev-up dev-down dev-reset backend-build backend-test frontend-build frontend-check contract-check db-verify

bootstrap:
	@echo "Local dependencies use the fixed development values in docker-compose.yml."

dev-up: bootstrap
	$(COMPOSE) up -d smart-converge-x-mysql smart-converge-x-redis smart-converge-x-rocketmq-namesrv smart-converge-x-rocketmq-broker smart-converge-x-minio smart-converge-x-callback-mock

dev-down:
	$(COMPOSE) down

dev-reset:
	$(COMPOSE) down -v

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

db-verify:
	cd scx-backend/db/smart-converge-x && ./verify-database.sh
