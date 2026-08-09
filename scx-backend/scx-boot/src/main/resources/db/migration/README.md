# Database delta SQL

This directory is no longer scanned or executed by `init-database.sh`.

The historical `V1` through `V78` scripts were consolidated into
`db/schema/scx_current_schema.sql` on 2026-08-09 and removed from this
directory. Do not restore or replay them.

For every future database change:

1. Add one forward-only, repeat-safe delta SQL here for existing databases.
2. Back up and preview the exact target before applying the delta.
3. Apply the delta explicitly; initialization never auto-runs this directory.
4. Synchronize the resulting final DDL and seed state into
   `db/schema/scx_current_schema.sql`.
5. Update `schema-required-tables.sh`, `verify-database.sh`, and contract
   tests when tables, columns, indexes, or required seeds change.

Never edit a delta after it has been applied to a shared database. Add a new
corrective delta instead.
