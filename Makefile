SHELL := /bin/bash

.PHONY: db-migrate mvp-db-migrate backend-build backend-test backend-run-dev frontend-build frontend-check contract-check migration-packaging-check prod-secret-injection-check db-verify

# The application uses externally managed MySQL, Redis and RocketMQ services.
# Export the SCX_* and MYSQL_* variables from the deployment environment first.
db-migrate:
	@test -n "$${MYSQL_HOST:-}" || (echo "MYSQL_HOST is required" >&2; exit 1)
	@test -n "$${MYSQL_USERNAME:-}" || (echo "MYSQL_USERNAME is required" >&2; exit 1)
	@test -n "$${MYSQL_PASSWORD:-}" || (echo "MYSQL_PASSWORD is required" >&2; exit 1)
	cd scx-backend/db/smart-converge-x && ./init-database.sh

mvp-db-migrate: db-migrate

backend-build:
	cd scx-backend && mvn -pl scx-boot -am -DskipTests package

backend-test:
	cd scx-backend && mvn -pl scx-boot -am test

backend-run-dev:
	cd scx-backend && mvn -pl scx-boot -am spring-boot:run -Dspring-boot.run.profiles=dev

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
	@test -n "$${MYSQL_HOST:-}" || (echo "MYSQL_HOST is required" >&2; exit 1)
	@test -n "$${MYSQL_USERNAME:-}" || (echo "MYSQL_USERNAME is required" >&2; exit 1)
	@test -n "$${MYSQL_PASSWORD:-}" || (echo "MYSQL_PASSWORD is required" >&2; exit 1)
	cd scx-backend/db/smart-converge-x && ./verify-database.sh
