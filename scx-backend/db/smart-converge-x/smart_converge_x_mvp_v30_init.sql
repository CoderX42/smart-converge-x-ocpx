-- Smart Converge X MVP one-file bootstrap through V30 (empty database only).
-- Run from this directory with the MySQL command-line client.
-- Includes sandbox/demo data; never use this file to upgrade an existing database.

source smart_converge_x_mvp_v29_init.sql
source ../../scx-boot/src/main/resources/db/migration/V30__seed_sandbox_basic_test_data.sql
