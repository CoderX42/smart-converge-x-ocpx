SHELL := /bin/bash

.PHONY: db-migrate mvp-db-migrate backend-build backend-test backend-run-dev rtb-build rtb-test rtb-run-dev frontend-build frontend-check contract-check db-verify

# The application uses externally managed MySQL, Redis and RocketMQ services.
# Export the SCX_* and MYSQL_* variables from the deployment environment first.
db-migrate:
	@test -n "$${MYSQL_HOST:-}" || (echo "MYSQL_HOST is required" >&2; exit 1)
	@test -n "$${MYSQL_USERNAME:-}" || (echo "MYSQL_USERNAME is required" >&2; exit 1)
	@test -n "$${MYSQL_PASSWORD:-}" || (echo "MYSQL_PASSWORD is required" >&2; exit 1)
	cd scx-backend/db/smart-converge-x && ./init-database.sh

mvp-db-migrate: db-migrate

backend-build:
	cd scx-backend && mvn -pl scx-boot,scx-rtb -am -DskipTests package

backend-test:
	cd scx-backend && mvn -pl scx-boot,scx-rtb -am test

backend-run-dev:
	cd scx-backend && mvn -pl scx-boot -am spring-boot:run -Dspring-boot.run.profiles=dev

rtb-build:
	cd scx-backend && mvn -pl scx-rtb -am -DskipTests package

rtb-test:
	cd scx-backend && mvn -pl scx-rtb -am test

rtb-run-dev:
	cd scx-backend && mvn -pl scx-rtb -am spring-boot:run -Dspring-boot.run.profiles=dev

frontend-build:
	cd scx-fronted && pnpm build:ele

frontend-check:
	cd scx-fronted && pnpm check:type && pnpm lint && pnpm test:unit

contract-check:
	bash ./tools/quality/check-contracts.sh

db-verify:
	@test -n "$${MYSQL_HOST:-}" || (echo "MYSQL_HOST is required" >&2; exit 1)
	@test -n "$${MYSQL_USERNAME:-}" || (echo "MYSQL_USERNAME is required" >&2; exit 1)
	@test -n "$${MYSQL_PASSWORD:-}" || (echo "MYSQL_PASSWORD is required" >&2; exit 1)
	cd scx-backend/db/smart-converge-x && ./verify-database.sh
