-- S5 Task operational aggregate. Forward-only and replay-safe on MySQL 8.x.
-- Saving a Task never publishes a route, creates an Outbox row, or mutates a
-- published snapshot. Existing Monitor rows remain explicitly LEGACY_UNMAPPED;
-- this migration deliberately contains no ownership backfill UPDATE.

CREATE TABLE IF NOT EXISTS scx_adx_task (
  id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  biz_code VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  product_id VARCHAR(32) NOT NULL,
  click_app_link_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  exposure_app_link_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  channel_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  media_credential_id VARCHAR(32) NOT NULL,
  media_credential_secret_revision BIGINT NOT NULL,
  operator_user_id VARCHAR(64) NOT NULL,
  task_name VARCHAR(64) NOT NULL,
  normalized_name VARCHAR(128) NOT NULL,
  exposure_enabled TINYINT(1) NOT NULL DEFAULT 0,
  exposure_as_click TINYINT(1) NOT NULL DEFAULT 0,
  dedup_window_seconds INT NOT NULL DEFAULT 3600,
  rta_mode VARCHAR(16) NOT NULL DEFAULT 'OFF',
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT,READY,ARCHIVED; not traffic state',
  config_version BIGINT NOT NULL DEFAULT 1,
  republish_required TINYINT(1) NOT NULL DEFAULT 1,
  republish_reason VARCHAR(255) NOT NULL DEFAULT 'TASK_DRAFT_SAVED',
  published_task_version BIGINT NOT NULL DEFAULT 0,
  create_request_key VARCHAR(64) NOT NULL,
  create_request_hash CHAR(64) NOT NULL,
  archived_by VARCHAR(100) NULL,
  archived_at DATETIME(3) NULL,
  create_by VARCHAR(100) NOT NULL,
  update_by VARCHAR(100) NOT NULL,
  current_name_key VARCHAR(128) GENERATED ALWAYS AS (IF(status<>'ARCHIVED',normalized_name,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_task_code (biz_code),
  UNIQUE KEY uk_scx_adx_task_name (advertiser_id,current_name_key),
  UNIQUE KEY uk_scx_adx_task_request (create_request_key),
  KEY idx_scx_adx_task_filter (advertiser_id,channel_id,media_account_id,status,update_time),
  KEY idx_scx_adx_task_product_link (product_id,click_app_link_id,exposure_app_link_id),
  KEY idx_scx_adx_task_operator (operator_user_id,status),
  KEY idx_scx_adx_task_credential (media_credential_id,media_credential_secret_revision),
  CONSTRAINT fk_scx_adx_task_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_adx_advertiser(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_task_product FOREIGN KEY (product_id) REFERENCES scx_adx_product(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_task_click_link FOREIGN KEY (click_app_link_id) REFERENCES scx_adx_app_link(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_task_exposure_link FOREIGN KEY (exposure_app_link_id) REFERENCES scx_adx_app_link(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_task_channel FOREIGN KEY (channel_id) REFERENCES scx_adx_channel(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_task_media_account FOREIGN KEY (media_account_id) REFERENCES scx_adx_media_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_task_media_credential FOREIGN KEY (media_credential_id) REFERENCES scx_adx_media_credential(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_task_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_adx_task_status CHECK (status IN ('DRAFT','READY','ARCHIVED')),
  CONSTRAINT ck_scx_adx_task_rta CHECK (rta_mode='OFF'),
  CONSTRAINT ck_scx_adx_task_dedup CHECK (dedup_window_seconds BETWEEN 0 AND 86400),
  CONSTRAINT ck_scx_adx_task_exposure CHECK ((exposure_enabled=0 AND exposure_app_link_id IS NULL AND exposure_as_click=0) OR (exposure_enabled=1 AND exposure_app_link_id IS NOT NULL)),
  CONSTRAINT ck_scx_adx_task_revision CHECK (config_version>=1 AND media_credential_secret_revision>=1 AND published_task_version>=0 AND published_task_version<=config_version),
  CONSTRAINT ck_scx_adx_task_archive CHECK ((status='ARCHIVED' AND archived_at IS NOT NULL) OR (status<>'ARCHIVED' AND archived_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='S5 Task draft aggregate; publishing belongs to S7';

CREATE TABLE IF NOT EXISTS scx_adx_task_change_audit (
  id VARCHAR(32) NOT NULL,
  task_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  action VARCHAR(40) NOT NULL,
  before_config_version BIGINT NOT NULL,
  after_config_version BIGINT NOT NULL,
  affected_monitor_count INT NOT NULL DEFAULT 0,
  actor VARCHAR(100) NOT NULL,
  change_summary VARCHAR(500) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_adx_task_audit (task_id,create_time,action),
  KEY idx_scx_adx_task_audit_scope (advertiser_id,channel_id,media_account_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Secret-free Task configuration audit';

DROP PROCEDURE IF EXISTS scx_v86_add_task_monitor_contract;
DELIMITER $$
CREATE PROCEDURE scx_v86_add_task_monitor_contract()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='task_id') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN task_id VARCHAR(32) NULL AFTER id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='task_mapping_status') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN task_mapping_status VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNMAPPED' AFTER task_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='task_route_kind') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN task_route_kind VARCHAR(16) NOT NULL DEFAULT 'LEGACY' AFTER task_mapping_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='task_config_revision') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN task_config_revision BIGINT NOT NULL DEFAULT 0 AFTER task_route_kind;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND index_name='idx_scx_adx_monitor_task') THEN
    ALTER TABLE scx_adx_monitor_link ADD KEY idx_scx_adx_monitor_task (task_id,task_route_kind,route_status,deleted);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND constraint_name='fk_scx_adx_monitor_task') THEN
    ALTER TABLE scx_adx_monitor_link ADD CONSTRAINT fk_scx_adx_monitor_task FOREIGN KEY (task_id) REFERENCES scx_adx_task(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND constraint_name='ck_scx_adx_monitor_task_mapping') THEN
    ALTER TABLE scx_adx_monitor_link ADD CONSTRAINT ck_scx_adx_monitor_task_mapping CHECK ((task_id IS NULL AND task_mapping_status='LEGACY_UNMAPPED' AND task_route_kind='LEGACY' AND task_config_revision=0) OR (task_id IS NOT NULL AND task_mapping_status='READY' AND task_route_kind IN ('CLICK','EXPOSURE') AND task_config_revision>=1));
  END IF;
END$$
DELIMITER ;
CALL scx_v86_add_task_monitor_contract();
DROP PROCEDURE scx_v86_add_task_monitor_contract;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_adx_task','任务管理','/adx/task','0','lucide:list-checks','adx/task/index','adx:task:view',40,'0','app_base',NOW(),NOW(),'0'),
  ('perm_adx_task_view_v86','查看任务','','menu_adx_task',NULL,NULL,'adx:task:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_task_add_v86','新增任务草稿','','menu_adx_task',NULL,NULL,'adx:task:add',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_task_edit_v86','编辑任务草稿','','menu_adx_task',NULL,NULL,'adx:task:edit',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_task_status_v86','变更任务就绪状态','','menu_adx_task',NULL,NULL,'adx:task:status',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_task_archive_v86','归档恢复任务','','menu_adx_task',NULL,NULL,'adx:task:archive',50,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v86_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_adx_task','perm_adx_task_view_v86','perm_adx_task_add_v86',
  'perm_adx_task_edit_v86','perm_adx_task_status_v86','perm_adx_task_archive_v86')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v86_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_adx_task','perm_adx_task_view_v86','perm_adx_task_add_v86',
  'perm_adx_task_edit_v86','perm_adx_task_status_v86')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v86_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_adx_task','perm_adx_task_view_v86')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0' AND m.del_flag='0';
