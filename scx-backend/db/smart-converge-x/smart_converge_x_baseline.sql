-- Smart Converge X complete database bootstrap
-- Execute order:
--   00schema.sql
--   10smart_converge_x_core.sql
--   20xxl_job_schema.sql

source 00schema.sql
source 10smart_converge_x_core.sql
source 20xxl_job_schema.sql

SET FOREIGN_KEY_CHECKS = 1;
