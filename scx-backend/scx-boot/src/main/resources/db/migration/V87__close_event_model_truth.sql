-- S6 canonical EventDefinition -> ChannelEventMapping -> TaskEventBinding ->
-- RouteEventSnapshot contract. Forward-only and replay-safe on MySQL 8.x.
-- Existing V80 rows remain LEGACY_UNMAPPED/LEGACY_V80. No business mapping is
-- inferred from names, external codes, URLs, or old Monitor relationships.

DROP PROCEDURE IF EXISTS scx_v87_close_event_model;
DELIMITER $$
CREATE PROCEDURE scx_v87_close_event_model()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_event_definition' AND column_name='direction') THEN
    ALTER TABLE scx_adx_event_definition
      ADD COLUMN direction VARCHAR(32) NULL AFTER event_name,
      ADD COLUMN standard_type VARCHAR(64) NULL AFTER direction,
      ADD COLUMN billing_eligible TINYINT(1) NOT NULL DEFAULT 0 AFTER standard_type,
      ADD COLUMN subject_type VARCHAR(24) NULL AFTER billing_eligible,
      ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER built_in,
      ADD COLUMN mapping_state VARCHAR(24) NOT NULL DEFAULT 'LEGACY_UNMAPPED' AFTER config_version,
      ADD COLUMN create_request_key VARCHAR(64) NULL AFTER mapping_state,
      ADD COLUMN create_request_hash CHAR(64) NULL AFTER create_request_key,
      ADD COLUMN archived_at DATETIME(3) NULL AFTER create_request_hash,
      ADD COLUMN create_by VARCHAR(100) NULL AFTER archived_at,
      ADD COLUMN update_by VARCHAR(100) NULL AFTER create_by;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_event_definition' AND index_name='uk_scx_adx_event_create_request') THEN
    ALTER TABLE scx_adx_event_definition ADD UNIQUE KEY uk_scx_adx_event_create_request (create_request_key);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_event_definition' AND index_name='idx_scx_adx_event_canonical') THEN
    ALTER TABLE scx_adx_event_definition ADD KEY idx_scx_adx_event_canonical (mapping_state,status,direction,standard_type,event_code);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_adx_event_definition' AND constraint_name='ck_scx_adx_event_canonical') THEN
    ALTER TABLE scx_adx_event_definition ADD CONSTRAINT ck_scx_adx_event_canonical CHECK (
      (mapping_state='LEGACY_UNMAPPED') OR
      (mapping_state='CANONICAL' AND category='STANDARD' AND channel_id='' AND
       direction IS NOT NULL AND standard_type IS NOT NULL AND subject_type IS NOT NULL AND
       direction IN ('UPSTREAM_CALLBACK','DOWNSTREAM_DELIVERY','BIDIRECTIONAL') AND
       standard_type REGEXP '^[A-Z][A-Z0-9_]{1,63}$' AND
       event_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$' AND
       subject_type IN ('DEVICE','ACCOUNT','ORDER','LEAD') AND
       status IN ('ACTIVE','DISABLED','ARCHIVED') AND config_version>=1)
    );
  END IF;

  ALTER TABLE scx_adx_channel_event_mapping
    MODIFY upstream_event_id VARCHAR(32) NULL,
    MODIFY conversion_event_id VARCHAR(32) NULL,
    MODIFY media_event_id VARCHAR(32) NULL;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel_event_mapping' AND column_name='adapter_code') THEN
    ALTER TABLE scx_adx_channel_event_mapping
      ADD COLUMN adapter_code VARCHAR(32) NULL AFTER channel_id,
      ADD COLUMN external_code VARCHAR(128) NULL AFTER adapter_code,
      ADD COLUMN direction VARCHAR(32) NULL AFTER external_code,
      ADD COLUMN event_definition_id VARCHAR(32) NULL AFTER direction,
      ADD COLUMN parameter_template_code VARCHAR(64) NULL AFTER event_definition_id,
      ADD COLUMN success_rule_code VARCHAR(32) NULL AFTER parameter_template_code,
      ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER status,
      ADD COLUMN mapping_state VARCHAR(24) NOT NULL DEFAULT 'LEGACY_UNMAPPED' AFTER config_version,
      ADD COLUMN create_request_key VARCHAR(64) NULL AFTER mapping_state,
      ADD COLUMN create_request_hash CHAR(64) NULL AFTER create_request_key,
      ADD COLUMN archived_at DATETIME(3) NULL AFTER create_request_hash,
      ADD COLUMN create_by VARCHAR(100) NULL AFTER archived_at,
      ADD COLUMN update_by VARCHAR(100) NULL AFTER create_by;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_channel_event_mapping' AND index_name='uk_scx_adx_mapping_external') THEN
    ALTER TABLE scx_adx_channel_event_mapping ADD UNIQUE KEY uk_scx_adx_mapping_external (channel_id,adapter_code,direction,external_code);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_channel_event_mapping' AND index_name='uk_scx_adx_mapping_channel_id') THEN
    ALTER TABLE scx_adx_channel_event_mapping ADD UNIQUE KEY uk_scx_adx_mapping_channel_id (id,channel_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_channel_event_mapping' AND index_name='uk_scx_adx_mapping_request') THEN
    ALTER TABLE scx_adx_channel_event_mapping ADD UNIQUE KEY uk_scx_adx_mapping_request (create_request_key);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_channel_event_mapping' AND index_name='idx_scx_adx_mapping_canonical') THEN
    ALTER TABLE scx_adx_channel_event_mapping ADD KEY idx_scx_adx_mapping_canonical (mapping_state,channel_id,adapter_code,direction,status,event_definition_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_adx_channel_event_mapping' AND constraint_name='fk_scx_adx_mapping_channel_v87') THEN
    ALTER TABLE scx_adx_channel_event_mapping ADD CONSTRAINT fk_scx_adx_mapping_channel_v87 FOREIGN KEY (channel_id) REFERENCES scx_adx_channel(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_adx_channel_event_mapping' AND constraint_name='fk_scx_adx_mapping_adapter_v87') THEN
    ALTER TABLE scx_adx_channel_event_mapping ADD CONSTRAINT fk_scx_adx_mapping_adapter_v87 FOREIGN KEY (adapter_code) REFERENCES scx_ocpx_adapter(adapter_code) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_adx_channel_event_mapping' AND constraint_name='fk_scx_adx_mapping_definition_v87') THEN
    ALTER TABLE scx_adx_channel_event_mapping ADD CONSTRAINT fk_scx_adx_mapping_definition_v87 FOREIGN KEY (event_definition_id) REFERENCES scx_adx_event_definition(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_adx_channel_event_mapping' AND constraint_name='ck_scx_adx_mapping_canonical') THEN
    ALTER TABLE scx_adx_channel_event_mapping ADD CONSTRAINT ck_scx_adx_mapping_canonical CHECK (
      (mapping_state='LEGACY_UNMAPPED' AND upstream_event_id IS NOT NULL AND conversion_event_id IS NOT NULL AND media_event_id IS NOT NULL) OR
      (mapping_state='CANONICAL' AND adapter_code IS NOT NULL AND external_code IS NOT NULL AND
       parameter_template_code IS NOT NULL AND success_rule_code IS NOT NULL AND
       external_code REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$' AND
       direction IN ('UPSTREAM_CALLBACK','DOWNSTREAM_DELIVERY') AND event_definition_id IS NOT NULL AND
       parameter_template_code IN ('STANDARD_EVENT_V1','FLAT_EVENT_V1') AND success_rule_code IN ('HTTP_2XX','BODY_CODE_ZERO') AND
       upstream_event_id IS NULL AND conversion_event_id IS NULL AND media_event_id IS NULL AND
       status IN ('ACTIVE','DISABLED','ARCHIVED') AND config_version>=1)
    );
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_task' AND index_name='uk_scx_adx_task_channel_id') THEN
    ALTER TABLE scx_adx_task ADD UNIQUE KEY uk_scx_adx_task_channel_id (id,channel_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='event_mapping_mode') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN event_mapping_mode VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNMAPPED' AFTER event_snapshot_version;
  END IF;
  UPDATE scx_adx_monitor_link SET event_mapping_mode='LEGACY_V80_SNAPSHOT'
    WHERE event_snapshot_version=1 AND event_mapping_mode='LEGACY_UNMAPPED';
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND constraint_name='ck_scx_adx_monitor_event_mode') THEN
    ALTER TABLE scx_adx_monitor_link ADD CONSTRAINT ck_scx_adx_monitor_event_mode CHECK (
      (event_snapshot_version=0 AND event_mapping_mode IN ('LEGACY_UNMAPPED','LEGACY_ADAPTER')) OR
      (event_snapshot_version=1 AND event_mapping_mode='LEGACY_V80_SNAPSHOT') OR
      (event_snapshot_version>=2 AND event_mapping_mode='SNAPSHOT')
    );
  END IF;
END$$
DELIMITER ;
CALL scx_v87_close_event_model();
DROP PROCEDURE scx_v87_close_event_model;

CREATE TABLE IF NOT EXISTS scx_adx_task_event_binding (
  id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  task_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  channel_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  event_definition_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  channel_event_mapping_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  callback_ratio_basis_points INT NOT NULL DEFAULT 10000,
  limit_value BIGINT NULL COMMENT 'NULL unlimited; 0 rejects every sampled event',
  limit_window VARCHAR(24) NOT NULL DEFAULT 'NATURAL_DAY',
  limit_timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  over_limit_delivery TINYINT(1) NOT NULL DEFAULT 0,
  sampling_algorithm VARCHAR(32) NOT NULL DEFAULT 'SHA256_MOD_10000_V1',
  source_type VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
  source_id VARCHAR(32) NULL,
  source_revision BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  config_version BIGINT NOT NULL DEFAULT 1,
  create_by VARCHAR(100) NOT NULL,
  update_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_task_event (task_id,event_definition_id),
  KEY idx_scx_adx_task_event_mapping (channel_event_mapping_id,status),
  KEY idx_scx_adx_task_event_channel (channel_id,status),
  CONSTRAINT fk_scx_adx_task_event_task FOREIGN KEY (task_id,channel_id) REFERENCES scx_adx_task(id,channel_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_task_event_definition FOREIGN KEY (event_definition_id) REFERENCES scx_adx_event_definition(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_task_event_mapping FOREIGN KEY (channel_event_mapping_id,channel_id) REFERENCES scx_adx_channel_event_mapping(id,channel_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_adx_task_event_ratio CHECK (callback_ratio_basis_points BETWEEN 0 AND 10000),
  CONSTRAINT ck_scx_adx_task_event_limit CHECK (limit_value IS NULL OR limit_value>=0),
  CONSTRAINT ck_scx_adx_task_event_window CHECK (limit_window='NATURAL_DAY' AND limit_timezone='Asia/Shanghai'),
  CONSTRAINT ck_scx_adx_task_event_sampling CHECK (sampling_algorithm='SHA256_MOD_10000_V1'),
  CONSTRAINT ck_scx_adx_task_event_source CHECK ((source_type='MANUAL' AND source_id IS NULL AND source_revision=0) OR (source_type='PRODUCT_DEFAULT' AND source_id IS NOT NULL AND source_revision>=1)),
  CONSTRAINT ck_scx_adx_task_event_status CHECK (status IN ('ACTIVE','DISABLED') AND config_version>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='S6 Task event binding draft truth';

CREATE TABLE IF NOT EXISTS scx_adx_event_change_audit (
  id VARCHAR(32) NOT NULL,
  object_type VARCHAR(32) NOT NULL,
  object_id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NULL,
  task_id VARCHAR(32) NULL,
  action VARCHAR(40) NOT NULL,
  before_version BIGINT NOT NULL,
  after_version BIGINT NOT NULL,
  actor VARCHAR(100) NOT NULL,
  change_summary VARCHAR(500) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_adx_event_audit_object (object_type,object_id,create_time),
  KEY idx_scx_adx_event_audit_scope (channel_id,task_id,create_time),
  CONSTRAINT ck_scx_adx_event_audit_type CHECK (object_type IN ('EVENT_DEFINITION','CHANNEL_EVENT_MAPPING','TASK_EVENT_BINDING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Sensitive-payload-free S6 event configuration audit';

DROP PROCEDURE IF EXISTS scx_v87_close_snapshot_contract;
DELIMITER $$
CREATE PROCEDURE scx_v87_close_snapshot_contract()
BEGIN
  ALTER TABLE scx_adx_route_event_snapshot
    MODIFY event_mapping_id VARCHAR(32) NULL,
    MODIFY upstream_event_code VARCHAR(64) NULL,
    MODIFY upstream_event_name VARCHAR(100) NULL,
    MODIFY conversion_event_code VARCHAR(64) NULL,
    MODIFY conversion_event_name VARCHAR(100) NULL,
    MODIFY media_event_code VARCHAR(64) NULL,
    MODIFY media_event_name VARCHAR(100) NULL,
    MODIFY assessment_unit_price DECIMAL(20,6) NULL,
    MODIFY daily_limit INT NULL;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_route_event_snapshot' AND column_name='snapshot_schema_version') THEN
    ALTER TABLE scx_adx_route_event_snapshot
      ADD COLUMN snapshot_schema_version INT NOT NULL DEFAULT 1 AFTER route_version,
      ADD COLUMN snapshot_mode VARCHAR(24) NOT NULL DEFAULT 'LEGACY_V80' AFTER snapshot_schema_version,
      ADD COLUMN task_id VARCHAR(32) NULL AFTER snapshot_mode,
      ADD COLUMN task_config_revision BIGINT NULL AFTER task_id,
      ADD COLUMN task_event_binding_id VARCHAR(32) NULL AFTER task_config_revision,
      ADD COLUMN task_event_binding_revision BIGINT NULL AFTER task_event_binding_id,
      ADD COLUMN event_definition_id VARCHAR(32) NULL AFTER task_event_binding_revision,
      ADD COLUMN event_definition_revision BIGINT NULL AFTER event_definition_id,
      ADD COLUMN event_mapping_revision BIGINT NULL AFTER event_mapping_id,
      ADD COLUMN standard_event_code VARCHAR(64) NULL AFTER event_mapping_revision,
      ADD COLUMN direction VARCHAR(32) NULL AFTER standard_event_code,
      ADD COLUMN external_event_code VARCHAR(128) NULL AFTER direction,
      ADD COLUMN parameter_template_code VARCHAR(64) NULL AFTER external_event_code,
      ADD COLUMN callback_ratio_basis_points INT NULL AFTER parameter_template_code,
      ADD COLUMN limit_value BIGINT NULL AFTER callback_ratio_basis_points,
      ADD COLUMN limit_window VARCHAR(24) NULL AFTER limit_value,
      ADD COLUMN limit_timezone VARCHAR(64) NULL AFTER limit_window,
      ADD COLUMN over_limit_delivery TINYINT(1) NULL AFTER limit_timezone,
      ADD COLUMN sampling_algorithm VARCHAR(32) NULL AFTER over_limit_delivery,
      ADD COLUMN snapshot_fingerprint CHAR(64) NULL AFTER sampling_algorithm;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_route_event_snapshot' AND index_name='uk_scx_adx_route_event_canonical') THEN
    ALTER TABLE scx_adx_route_event_snapshot ADD UNIQUE KEY uk_scx_adx_route_event_canonical (monitor_id,route_version,event_definition_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_route_event_snapshot' AND index_name='idx_scx_adx_route_event_task') THEN
    ALTER TABLE scx_adx_route_event_snapshot ADD KEY idx_scx_adx_route_event_task (task_id,task_event_binding_id,snapshot_schema_version);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_adx_route_event_snapshot' AND constraint_name='fk_scx_adx_snapshot_binding_v87') THEN
    ALTER TABLE scx_adx_route_event_snapshot ADD CONSTRAINT fk_scx_adx_snapshot_binding_v87 FOREIGN KEY (task_event_binding_id) REFERENCES scx_adx_task_event_binding(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_adx_route_event_snapshot' AND constraint_name='fk_scx_adx_snapshot_definition_v87') THEN
    ALTER TABLE scx_adx_route_event_snapshot ADD CONSTRAINT fk_scx_adx_snapshot_definition_v87 FOREIGN KEY (event_definition_id) REFERENCES scx_adx_event_definition(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_adx_route_event_snapshot' AND constraint_name='fk_scx_adx_snapshot_mapping_v87') THEN
    ALTER TABLE scx_adx_route_event_snapshot ADD CONSTRAINT fk_scx_adx_snapshot_mapping_v87 FOREIGN KEY (event_mapping_id) REFERENCES scx_adx_channel_event_mapping(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_adx_route_event_snapshot' AND constraint_name='ck_scx_adx_snapshot_v87') THEN
    ALTER TABLE scx_adx_route_event_snapshot ADD CONSTRAINT ck_scx_adx_snapshot_v87 CHECK (
      (snapshot_schema_version=1 AND snapshot_mode='LEGACY_V80') OR
      (snapshot_schema_version=2 AND snapshot_mode='CANONICAL' AND task_id IS NOT NULL AND task_config_revision>=1 AND
       task_event_binding_id IS NOT NULL AND task_event_binding_revision>=1 AND event_definition_id IS NOT NULL AND
       event_definition_revision>=1 AND event_mapping_id IS NOT NULL AND event_mapping_revision>=1 AND
       standard_event_code IS NOT NULL AND direction IS NOT NULL AND external_event_code IS NOT NULL AND
       parameter_template_code IS NOT NULL AND callback_ratio_basis_points IS NOT NULL AND
       limit_window IS NOT NULL AND limit_timezone IS NOT NULL AND sampling_algorithm IS NOT NULL AND
       direction='DOWNSTREAM_DELIVERY' AND parameter_template_code IN ('STANDARD_EVENT_V1','FLAT_EVENT_V1') AND
       callback_ratio_basis_points BETWEEN 0 AND 10000 AND (limit_value IS NULL OR limit_value>=0) AND
       limit_window='NATURAL_DAY' AND limit_timezone='Asia/Shanghai' AND
       sampling_algorithm='SHA256_MOD_10000_V1' AND snapshot_fingerprint IS NOT NULL)
    );
  END IF;
END$$
DELIMITER ;
CALL scx_v87_close_snapshot_contract();
DROP PROCEDURE scx_v87_close_snapshot_contract;

DROP TRIGGER IF EXISTS trg_scx_adx_route_event_snapshot_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_route_event_snapshot_no_delete;
DELIMITER $$
CREATE TRIGGER trg_scx_adx_route_event_snapshot_no_update BEFORE UPDATE ON scx_adx_route_event_snapshot
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RouteEventSnapshot is immutable; publish a new version'$$
CREATE TRIGGER trg_scx_adx_route_event_snapshot_no_delete BEFORE DELETE ON scx_adx_route_event_snapshot
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RouteEventSnapshot history cannot be deleted'$$
DELIMITER ;

UPDATE scx_ocpx_adapter
SET capabilities_json=JSON_SET(capabilities_json,
  '$.eventParameterTemplates',JSON_ARRAY('STANDARD_EVENT_V1','FLAT_EVENT_V1'),
  '$.eventMappingDirections',IF(direction='UPSTREAM',JSON_ARRAY('UPSTREAM_CALLBACK'),JSON_ARRAY('DOWNSTREAM_DELIVERY'))),
  update_time=NOW(3)
WHERE status='ACTIVE';

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_adx_event','事件字典','/adx/event','menu_ocpx_system','lucide:workflow','adx/event/index','adx:event:view',45,'0','app_base',NOW(),NOW(),'0'),
  ('perm_adx_event_view_v87','查看事件定义','','menu_adx_event',NULL,NULL,'adx:event:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_event_add_v87','新增事件定义','','menu_adx_event',NULL,NULL,'adx:event:add',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_event_edit_v87','编辑事件定义','','menu_adx_event',NULL,NULL,'adx:event:edit',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_event_status_v87','变更事件状态','','menu_adx_event',NULL,NULL,'adx:event:status',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_event_mapping_view_v87','查看渠道事件映射','','menu_adx_event',NULL,NULL,'adx:event-mapping:view',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_event_mapping_add_v87','新增渠道事件映射','','menu_adx_event',NULL,NULL,'adx:event-mapping:add',60,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_event_mapping_edit_v87','编辑渠道事件映射','','menu_adx_event',NULL,NULL,'adx:event-mapping:edit',70,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_event_mapping_status_v87','变更渠道事件映射状态','','menu_adx_event',NULL,NULL,'adx:event-mapping:status',80,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_task_event_edit_v87','编辑任务事件绑定','','menu_adx_task',NULL,NULL,'adx:task:event:edit',60,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

UPDATE sys_menu SET del_flag='1',update_time=NOW() WHERE id='perm_adx_event_write';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v87_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_adx_event','perm_adx_event_view_v87','perm_adx_event_add_v87','perm_adx_event_edit_v87',
  'perm_adx_event_status_v87','perm_adx_event_mapping_view_v87','perm_adx_event_mapping_add_v87',
  'perm_adx_event_mapping_edit_v87','perm_adx_event_mapping_status_v87','perm_adx_task_event_edit_v87')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v87_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_adx_event','perm_adx_event_view_v87','perm_adx_event_mapping_view_v87','perm_adx_task_event_edit_v87')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v87_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_adx_event','perm_adx_event_view_v87','perm_adx_event_mapping_view_v87')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0' AND m.del_flag='0';
