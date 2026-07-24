-- Smart Converge X MVP bootstrap through V42 (empty database only).
-- Run from this directory with the MySQL command-line client.
-- Includes repeatable sandbox/demo data with reconciled business references.
-- Never use this aggregate entry to upgrade an existing database.

source smart_converge_x_mvp_v41_init.sql
source ../../scx-boot/src/main/resources/db/migration/V42__reconcile_sandbox_basic_test_data.sql
