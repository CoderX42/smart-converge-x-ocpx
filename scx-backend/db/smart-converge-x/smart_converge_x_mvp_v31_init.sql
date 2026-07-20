-- Smart Converge X MVP one-file bootstrap through V31 (empty database only).
-- Run from this directory with the MySQL command-line client.
-- Includes sandbox/demo data; never use this file to upgrade an existing database.

source smart_converge_x_mvp_v30_init.sql
source ../../scx-boot/src/main/resources/db/migration/V31__reconcile_mvp_access_control_seeds.sql
