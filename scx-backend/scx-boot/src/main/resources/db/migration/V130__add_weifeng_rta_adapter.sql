-- V130: allow the production Weifeng RTA adapter in existing RTA Profiles.
-- Forward-only migration. Back up the target database, preview the DDL and
-- obtain explicit approval before execution. No profile, endpoint or secret
-- is seeded by this migration.
-- Resulting CHECK expression: adapter_code IN ('MOCK','WEIFENG_RTA').

SET @scx_rta_adapter_check_exists := (
  SELECT COUNT(*)
  FROM information_schema.table_constraints
  WHERE constraint_schema=DATABASE()
    AND table_name='scx_ocpx_rta_profile'
    AND constraint_name='ck_ocpx_rta_profile_adapter'
    AND constraint_type='CHECK'
);
SET @scx_rta_adapter_check_sql := IF(
  @scx_rta_adapter_check_exists=0,
  'ALTER TABLE scx_ocpx_rta_profile ADD CONSTRAINT ck_ocpx_rta_profile_adapter CHECK (adapter_code IN (''MOCK'',''WEIFENG_RTA''))',
  'ALTER TABLE scx_ocpx_rta_profile DROP CHECK ck_ocpx_rta_profile_adapter, ADD CONSTRAINT ck_ocpx_rta_profile_adapter CHECK (adapter_code IN (''MOCK'',''WEIFENG_RTA''))'
);
PREPARE scx_rta_adapter_check_stmt FROM @scx_rta_adapter_check_sql;
EXECUTE scx_rta_adapter_check_stmt;
DEALLOCATE PREPARE scx_rta_adapter_check_stmt;
