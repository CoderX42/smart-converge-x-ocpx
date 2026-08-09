# Smart Converge X Database

This directory provides one current-state entry point for a new empty MySQL 8
database. Historical V1-V78 migrations are not replayed.

## Empty database initialization

`smart_converge_x_baseline.sql` executes, in order:

1. `00schema.sql`: create and select `smart_converge_x`.
2. `10smart_converge_x_core.sql`: current system and code-generator tables.
3. `20xxl_job_schema.sql`: XXL-JOB tables and minimal seed.
4. `../../scx-boot/src/main/resources/db/schema/scx_current_schema.sql`:
   compact ADX, OCPX, API debugger, menus, permissions, and adapter seeds.

Use the guarded entry point:

```bash
cd scx-backend/db/smart-converge-x
MYSQL_HOST=<mysql-host> MYSQL_PORT=3306 \
MYSQL_USERNAME=<mysql-user> MYSQL_PASSWORD=<mysql-password> \
./init-database.sh
```

The initializer refuses to run when the target schema already contains tables.
It never scans or executes `db/migration`.

## Existing database changes

Every future change uses two synchronized artifacts:

1. Add one forward-only delta SQL under
   `scx-boot/src/main/resources/db/migration/`.
2. Back up and explicitly apply that delta to the approved existing database.
3. Synchronize the resulting final DDL and seed state into
   `scx-boot/src/main/resources/db/schema/scx_current_schema.sql`.
4. Update the required-table manifest, verification checks, and contract tests.

Do not run `init-database.sh` against development or production databases that
already contain tables. Do not restore or replay the retired V1-V78 scripts.

## Read-only inspection

```bash
MYSQL_HOST=<mysql-host> MYSQL_USERNAME=<mysql-user> MYSQL_PASSWORD=<mysql-password> \
./verify-database.sh

MYSQL_HOST=<mysql-host> MYSQL_USERNAME=<mysql-user> MYSQL_PASSWORD=<mysql-password> \
./reconcile-missing-tables.sh
```

`reconcile-missing-tables.sh` is read-only. It reports missing and unexpected
tables; it no longer generates or applies DDL.

## Security

Default bootstrap users must be changed before release. Never place production
passwords, tokens, certificates, or exported business data in these scripts.
