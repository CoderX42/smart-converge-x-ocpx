SHELL := /bin/bash

.PHONY: db-init db-migrate mvp-db-migrate backend-build backend-test backend-run-dev ocpx-engine-build ocpx-engine-test ocpx-engine-run-dev frontend-build frontend-check contract-check naming-check schema-ledger-check ocpx-four-entity-check db-verify

# The application uses externally managed MySQL, Redis and RocketMQ services.
# Export the SCX_* and MYSQL_* variables from the deployment environment first.
db-init: schema-ledger-check
	@test -n "$${MYSQL_HOST:-}" || (echo "MYSQL_HOST is required" >&2; exit 1)
	@test -n "$${MYSQL_USERNAME:-}" || (echo "MYSQL_USERNAME is required" >&2; exit 1)
	@test -n "$${MYSQL_PASSWORD:-}" || (echo "MYSQL_PASSWORD is required" >&2; exit 1)
	cd scx-backend/db/smart-converge-x && ./init-database.sh

db-migrate: db-init

mvp-db-migrate: db-migrate

backend-build: schema-ledger-check ocpx-four-entity-check
	cd scx-backend && mvn -pl scx-boot -am -DskipTests clean package

backend-test: schema-ledger-check ocpx-four-entity-check
	cd scx-backend && mvn -pl scx-boot -am test

backend-run-dev:
	cd scx-backend && mvn -pl scx-boot -am spring-boot:run -Dspring-boot.run.profiles=dev

ocpx-engine-build:
	cd scx-backend && mvn -pl scx-ocpx-engine -am -DskipTests clean package

ocpx-engine-test:
	cd scx-backend && mvn -pl scx-ocpx-engine -am test

ocpx-engine-run-dev:
	cd scx-backend && mvn -pl scx-ocpx-engine -am spring-boot:run

frontend-build:
	cd scx-fronted && pnpm build:ele

frontend-check: ocpx-four-entity-check
	cd scx-fronted && pnpm check:type && pnpm lint && pnpm test:unit

contract-check:
	bash ./tools/quality/check-contracts.sh

naming-check:
	bash ./tools/quality/check-no-adx-naming.sh

schema-ledger-check:
	bash ./tools/quality/check-no-schema-migration-ledger.sh

ocpx-four-entity-check:
	bash ./tools/quality/check-ocpx-four-entities.sh

db-verify: schema-ledger-check
	@test -n "$${MYSQL_HOST:-}" || (echo "MYSQL_HOST is required" >&2; exit 1)
	@test -n "$${MYSQL_USERNAME:-}" || (echo "MYSQL_USERNAME is required" >&2; exit 1)
	@test -n "$${MYSQL_PASSWORD:-}" || (echo "MYSQL_PASSWORD is required" >&2; exit 1)
	cd scx-backend/db/smart-converge-x && bash ./verify-database.sh
