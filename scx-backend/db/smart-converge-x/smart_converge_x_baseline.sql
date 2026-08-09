-- Smart Converge X complete empty-database bootstrap
-- Run from this directory with the MySQL client.
-- Existing databases must use an explicitly reviewed forward-only delta.

source 00schema.sql
source 10smart_converge_x_core.sql
source 20xxl_job_schema.sql
source ../../scx-boot/src/main/resources/db/schema/scx_current_schema.sql
