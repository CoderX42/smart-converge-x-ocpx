-- S7 immutable MonitorRoute publication, transactional distribution and runtime
-- acceptance dimensions. Forward-only and replay-safe on MySQL 8.x.
-- Legacy Monitor rows remain unmapped; this migration never infers a Task,
-- Product, MediaAccount, event mapping or route code from names or URLs.

CREATE TABLE IF NOT EXISTS scx_adx_route_version (
  id VARCHAR(32) NOT NULL,
  monitor_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  task_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  route_version BIGINT NOT NULL,
  route_kind VARCHAR(16) NOT NULL,
  route_code_hash CHAR(64) NOT NULL,
  route_code_ciphertext TEXT NOT NULL,
  public_template_ciphertext TEXT NOT NULL,
  route_schema_version INT NOT NULL DEFAULT 3,
  snapshot_fingerprint CHAR(64) NOT NULL,
  runtime_snapshot_ciphertext LONGTEXT NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  advertiser_revision BIGINT NOT NULL,
  product_id VARCHAR(32) NOT NULL,
  product_revision BIGINT NOT NULL,
  product_event_policy_revision BIGINT NOT NULL,
  app_link_id VARCHAR(32) NOT NULL,
  app_link_revision BIGINT NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  media_account_revision BIGINT NOT NULL,
  media_credential_id VARCHAR(32) NOT NULL,
  media_credential_secret_revision BIGINT NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  channel_adapter_code VARCHAR(32) NOT NULL,
  channel_adapter_revision BIGINT NOT NULL,
  task_config_revision BIGINT NOT NULL,
  event_snapshot_schema_version INT NOT NULL,
  macro_schema_json JSON NOT NULL,
  dedup_window_seconds INT NOT NULL,
  exposure_enabled TINYINT(1) NOT NULL,
  exposure_as_click TINYINT(1) NOT NULL,
  rta_mode VARCHAR(16) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
  published_by VARCHAR(100) NOT NULL,
  published_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_route_version (monitor_id,route_version),
  UNIQUE KEY uk_scx_adx_route_code_hash (route_code_hash),
  KEY idx_scx_adx_route_version_task (task_id,published_at),
  KEY idx_scx_adx_route_version_scope (advertiser_id,channel_id,media_account_id,published_at),
  CONSTRAINT fk_scx_adx_route_version_monitor FOREIGN KEY (monitor_id) REFERENCES scx_adx_monitor_link(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_route_version_task FOREIGN KEY (task_id) REFERENCES scx_adx_task(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_adx_route_version_core CHECK (
    route_version>=1 AND route_schema_version=3 AND route_kind IN ('CLICK','EXPOSURE') AND
    CHAR_LENGTH(route_code_hash)=64 AND CHAR_LENGTH(snapshot_fingerprint)=64 AND
    advertiser_revision>=1 AND product_revision>=1 AND product_event_policy_revision>=1 AND
    app_link_revision>=1 AND media_account_revision>=1 AND media_credential_secret_revision>=1 AND
    channel_adapter_revision>=1 AND task_config_revision>=1 AND event_snapshot_schema_version=2 AND
    dedup_window_seconds BETWEEN 0 AND 86400 AND rta_mode='OFF' AND status='PUBLISHED' AND
    ((exposure_enabled=0 AND exposure_as_click=0) OR exposure_enabled=1)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='S7 immutable published route version';

CREATE TABLE IF NOT EXISTS scx_adx_route_runtime_state (
  id VARCHAR(32) NOT NULL,
  route_version_id VARCHAR(32) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  route_version BIGINT NOT NULL,
  acceptance_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_DISTRIBUTION',
  accept_from DATETIME(3) NULL,
  accept_until DATETIME(3) NULL,
  deny_at DATETIME(3) NULL,
  state_revision BIGINT NOT NULL DEFAULT 1,
  reason_code VARCHAR(64) NULL,
  update_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_route_runtime_version (route_version_id),
  UNIQUE KEY uk_scx_adx_route_runtime_monitor (monitor_id,route_version),
  KEY idx_scx_adx_route_runtime_accept (acceptance_status,accept_from,accept_until,deny_at),
  CONSTRAINT fk_scx_adx_route_runtime_version FOREIGN KEY (route_version_id) REFERENCES scx_adx_route_version(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_adx_route_runtime_state CHECK (
    acceptance_status IN ('PENDING_DISTRIBUTION','ACTIVE','OVERLAP','SUPERSEDED','DENIED','EXPIRED') AND
    route_version>=1 AND state_revision>=1 AND
    (accept_until IS NULL OR accept_from IS NULL OR accept_until>=accept_from)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Mutable audited acceptance state outside immutable route snapshot';

CREATE TABLE IF NOT EXISTS scx_adx_route_publish_record (
  id VARCHAR(32) NOT NULL,
  publish_request_id VARCHAR(64) NOT NULL,
  task_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  expected_task_version BIGINT NOT NULL,
  check_fingerprint CHAR(64) NOT NULL,
  route_version_id VARCHAR(32) NOT NULL,
  route_version BIGINT NOT NULL,
  route_bundle_json JSON NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
  actor VARCHAR(100) NOT NULL,
  response_fingerprint CHAR(64) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_route_publish_request (publish_request_id),
  UNIQUE KEY uk_scx_adx_route_publish_version (route_version_id),
  KEY idx_scx_adx_route_publish_task (task_id,create_time),
  CONSTRAINT fk_scx_adx_route_publish_task FOREIGN KEY (task_id) REFERENCES scx_adx_task(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_route_publish_version FOREIGN KEY (route_version_id) REFERENCES scx_adx_route_version(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_adx_route_publish_record CHECK (
    publish_request_id REGEXP '^[A-Za-z0-9_-]{16,64}$' AND expected_task_version>=1 AND
    route_version>=1 AND CHAR_LENGTH(check_fingerprint)=64 AND CHAR_LENGTH(response_fingerprint)=64 AND
    status='PUBLISHED'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Idempotent S7 route publication record';

CREATE TABLE IF NOT EXISTS scx_adx_route_distribution (
  id VARCHAR(32) NOT NULL,
  route_version_id VARCHAR(32) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  route_version BIGINT NOT NULL,
  target_group VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
  minimum_required_acks INT NOT NULL DEFAULT 1,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  lease_owner VARCHAR(100) NULL,
  lease_until DATETIME(3) NULL,
  last_error_code VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_route_distribution (route_version_id,target_group),
  KEY idx_scx_adx_route_distribution_claim (status,next_attempt_at,lease_until),
  CONSTRAINT fk_scx_adx_route_distribution_version FOREIGN KEY (route_version_id) REFERENCES scx_adx_route_version(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_adx_route_distribution CHECK (
    route_version>=1 AND minimum_required_acks>=1 AND attempt_count>=0 AND
    status IN ('PENDING','DISTRIBUTING','ACTIVE','DEGRADED','FAILED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='S7 route distribution truth';

CREATE TABLE IF NOT EXISTS scx_adx_route_instance_ack (
  id VARCHAR(32) NOT NULL,
  distribution_id VARCHAR(32) NOT NULL,
  route_version_id VARCHAR(32) NOT NULL,
  instance_id VARCHAR(100) NOT NULL,
  applied_version BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  error_code VARCHAR(64) NULL,
  acked_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_route_instance_ack (distribution_id,instance_id),
  KEY idx_scx_adx_route_instance_version (route_version_id,status,acked_at),
  CONSTRAINT fk_scx_adx_route_ack_distribution FOREIGN KEY (distribution_id) REFERENCES scx_adx_route_distribution(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_route_ack_version FOREIGN KEY (route_version_id) REFERENCES scx_adx_route_version(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_adx_route_ack CHECK (applied_version>=1 AND status IN ('APPLIED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Per-instance applied route version acknowledgement';

CREATE TABLE IF NOT EXISTS scx_adx_route_pause_command (
  id VARCHAR(32) NOT NULL,
  pause_request_id VARCHAR(64) NOT NULL,
  task_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  expected_task_version BIGINT NOT NULL,
  preview_fingerprint CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DENY_MARKER_WRITTEN',
  affected_route_count INT NOT NULL,
  actor VARCHAR(100) NOT NULL,
  reason_summary VARCHAR(255) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_route_pause_request (pause_request_id),
  KEY idx_scx_adx_route_pause_task (task_id,create_time),
  CONSTRAINT fk_scx_adx_route_pause_task FOREIGN KEY (task_id) REFERENCES scx_adx_task(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_adx_route_pause CHECK (
    pause_request_id REGEXP '^[A-Za-z0-9_-]{16,64}$' AND expected_task_version>=1 AND
    CHAR_LENGTH(preview_fingerprint)=64 AND affected_route_count>=0 AND status='DENY_MARKER_WRITTEN'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Idempotent auditable S7 emergency deny command';

DROP PROCEDURE IF EXISTS scx_upgrade_v88_publication_contract;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_v88_publication_contract()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_adx_task' AND constraint_name='ck_scx_adx_task_status') THEN
    ALTER TABLE scx_adx_task DROP CHECK ck_scx_adx_task_status;
  END IF;
  ALTER TABLE scx_adx_task ADD CONSTRAINT ck_scx_adx_task_status CHECK (status IN ('DRAFT','READY','ACTIVE','PAUSED','ARCHIVED'));

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_config_outbox' AND column_name='payload_json') THEN
    ALTER TABLE scx_ocpx_config_outbox
      ADD COLUMN payload_json JSON NULL AFTER event_type,
      ADD COLUMN delivery_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' AFTER published,
      ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER delivery_status,
      ADD COLUMN available_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER attempt_count,
      ADD COLUMN lease_owner VARCHAR(100) NULL AFTER available_at,
      ADD COLUMN lease_until DATETIME(3) NULL AFTER lease_owner,
      ADD COLUMN last_error_code VARCHAR(64) NULL AFTER lease_until,
      ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER published_at;
  END IF;
  UPDATE scx_ocpx_config_outbox
  SET delivery_status=IF(published=1,'DELIVERED','PENDING'),
      available_at=COALESCE(create_time,NOW(3)),attempt_count=GREATEST(attempt_count,0)
  WHERE delivery_status='PENDING';
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_config_outbox' AND index_name='idx_scx_ocpx_config_claim') THEN
    ALTER TABLE scx_ocpx_config_outbox ADD KEY idx_scx_ocpx_config_claim (delivery_status,available_at,lease_until);
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_v88_publication_contract();
DROP PROCEDURE scx_upgrade_v88_publication_contract;

DROP TRIGGER IF EXISTS trg_scx_adx_route_version_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_route_version_no_delete;
DELIMITER $$
CREATE TRIGGER trg_scx_adx_route_version_no_update BEFORE UPDATE ON scx_adx_route_version
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RouteVersion is immutable; publish a new version'$$
CREATE TRIGGER trg_scx_adx_route_version_no_delete BEFORE DELETE ON scx_adx_route_version
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RouteVersion history cannot be deleted'$$
DELIMITER ;

UPDATE scx_ocpx_adapter
SET capabilities_json=JSON_SET(capabilities_json,
  '$.trackingMacroSchemaVersion',1,
  '$.trackingMacroPairs',JSON_ARRAY(
    JSON_OBJECT('key','imei','macro','__IMEI__','required',FALSE),
    JSON_OBJECT('key','imei_md5','macro','__IMEI_MD5__','required',FALSE),
    JSON_OBJECT('key','oaid','macro','__OAID__','required',FALSE),
    JSON_OBJECT('key','oaid_md5','macro','__OAID_MD5__','required',FALSE),
    JSON_OBJECT('key','ip','macro','__IP__','required',FALSE),
    JSON_OBJECT('key','android_id','macro','__ANDROIDID__','required',FALSE),
    JSON_OBJECT('key','android_id_md5','macro','__ANDROIDID_MD5__','required',FALSE),
    JSON_OBJECT('key','aaid','macro','__AAID__','required',FALSE),
    JSON_OBJECT('key','ua','macro','__UA__','required',FALSE),
    JSON_OBJECT('key','os','macro','__OS__','required',FALSE),
    JSON_OBJECT('key','model','macro','__MODEL__','required',FALSE),
    JSON_OBJECT('key','callback','macro','__CALLBACK__','required',FALSE),
    JSON_OBJECT('key','clickid','macro','__CLICKID__','required',TRUE),
    JSON_OBJECT('key','campaign_id','macro','__CAMPAIGNID__','required',FALSE),
    JSON_OBJECT('key','adgroup_id','macro','__ADGROUPID__','required',FALSE),
    JSON_OBJECT('key','creative_id','macro','__CREATIVEID__','required',FALSE),
    JSON_OBJECT('key','advertiser_id','macro','__ADVERTISERID__','required',FALSE)
  )),
  capability_revision=capability_revision+1,update_time=NOW(3)
WHERE direction='DOWNSTREAM' AND status='ACTIVE'
  AND NOT JSON_CONTAINS_PATH(capabilities_json,'all','$.trackingMacroSchemaVersion','$.trackingMacroPairs');

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_adx_task_publish_v88','发布任务路由','','menu_adx_task',NULL,NULL,'adx:task:publish',70,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_monitor_view_v88','查看路由版本','','menu_adx_task',NULL,NULL,'adx:monitor:view',80,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_monitor_publish_v88','发布路由','','menu_adx_task',NULL,NULL,'adx:monitor:publish',90,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_monitor_copy_v88','复制监测地址','','menu_adx_task',NULL,NULL,'adx:monitor:copy',100,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_monitor_status_v88','停流及恢复发布','','menu_adx_task',NULL,NULL,'adx:monitor:status',110,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v88_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_adx_task_publish_v88','perm_adx_monitor_view_v88','perm_adx_monitor_publish_v88',
  'perm_adx_monitor_copy_v88','perm_adx_monitor_status_v88')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v88_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_adx_task_publish_v88','perm_adx_monitor_view_v88','perm_adx_monitor_publish_v88',
  'perm_adx_monitor_copy_v88','perm_adx_monitor_status_v88')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';
