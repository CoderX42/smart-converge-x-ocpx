-- Apply only after backup, target preview and explicit production approval.
-- Old RTA tables remain isolated. No automatic profile conversion or role expansion.
CREATE TABLE IF NOT EXISTS scx_ocpx_rta_upstream (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  advertiser_id VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  protocol VARCHAR(32) NOT NULL DEFAULT 'GENERIC_ASYNC',
  access_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  endpoint VARCHAR(2048) NOT NULL,
  credential_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  timeout_ms INT NOT NULL DEFAULT 5000,
  admission_seconds INT NOT NULL DEFAULT 300,
  enabled TINYINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 1,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_rta_upstream_code(access_code),
  KEY idx_rta_upstream_advertiser(advertiser_id),
  CONSTRAINT fk_rta_async_upstream_advertiser FOREIGN KEY(advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CHECK(protocol='GENERIC_ASYNC'), CHECK(enabled IN (0,1)), CHECK(version>0),
  CHECK(timeout_ms BETWEEN 100 AND 30000), CHECK(admission_seconds BETWEEN 1 AND 86400)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS scx_ocpx_rta_media_access (
  media_id VARCHAR(32) NOT NULL PRIMARY KEY,
  notify_url VARCHAR(2048) NOT NULL,
  credential_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 1,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_rta_media_credential(credential_ref),
  CONSTRAINT fk_rta_async_media FOREIGN KEY(media_id) REFERENCES scx_ocpx_media(id),
  CHECK(enabled IN (0,1)), CHECK(version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS scx_ocpx_rta_binding (
  monitor_task_id VARCHAR(32) NOT NULL PRIMARY KEY,
  upstream_id VARCHAR(32) NOT NULL,
  product_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  external_task_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 1,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_rta_binding_upstream(upstream_id),
  CONSTRAINT fk_rta_async_binding_task FOREIGN KEY(monitor_task_id) REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT fk_rta_async_binding_upstream FOREIGN KEY(upstream_id) REFERENCES scx_ocpx_rta_upstream(id),
  CHECK(enabled IN (0,1)), CHECK(version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS scx_ocpx_rta_log (
  id CHAR(32) NOT NULL PRIMARY KEY,
  relay_request_id VARCHAR(32) NOT NULL,
  request_id VARCHAR(128) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  upstream_id VARCHAR(32) NOT NULL,
  task_id VARCHAR(32) NOT NULL,
  stage VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  reason VARCHAR(64) NOT NULL,
  duration_ms BIGINT NOT NULL,
  event_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_rta_log_time(event_time), KEY idx_rta_log_scope(advertiser_id,event_time),
  KEY idx_rta_log_request(media_id,request_id), KEY idx_rta_log_relay(relay_request_id),
  CHECK(duration_ms>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='RTA redacted stage summaries, retained 24h';

CREATE TABLE IF NOT EXISTS scx_ocpx_rta_hour_stat (
  time_hour DATETIME(3) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  task_id VARCHAR(32) NOT NULL,
  upstream_id VARCHAR(32) NOT NULL,
  request_count BIGINT NOT NULL DEFAULT 0,
  allow_count BIGINT NOT NULL DEFAULT 0,
  deny_count BIGINT NOT NULL DEFAULT 0,
  error_count BIGINT NOT NULL DEFAULT 0,
  timeout_count BIGINT NOT NULL DEFAULT 0,
  delivery_success_count BIGINT NOT NULL DEFAULT 0,
  delivery_failed_count BIGINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(time_hour,media_id,task_id,upstream_id),
  KEY idx_rta_stat_scope(advertiser_id,time_hour),
  CHECK(version>0), CHECK(request_count>=0),
  CHECK(allow_count>=0 AND deny_count>=0 AND error_count>=0 AND timeout_count>=0),
  CHECK(allow_count+deny_count+error_count+timeout_count<=request_count),
  CHECK(delivery_success_count>=0 AND delivery_failed_count>=0 AND delivery_success_count+delivery_failed_count<=request_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Legacy tables remain as historical data. Remove only their foreign keys so they
-- no longer constrain live advertiser/media/task lifecycle after runtime retirement.
DELIMITER $$
CREATE PROCEDURE scx_rta131_detach_legacy()
BEGIN
  DECLARE finished INT DEFAULT 0;
  DECLARE legacy_table VARCHAR(64);
  DECLARE constraint_to_drop VARCHAR(64);
  DECLARE constraints_cursor CURSOR FOR
    SELECT table_name,constraint_name FROM information_schema.table_constraints
    WHERE constraint_schema=DATABASE() AND constraint_type='FOREIGN KEY'
      AND table_name IN ('scx_ocpx_rta_profile','scx_ocpx_rta_task_binding',
                        'scx_ocpx_rta_decision_fact','scx_ocpx_rta_hourly_metric');
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=1;
  OPEN constraints_cursor;
  detach_loop: LOOP
    FETCH constraints_cursor INTO legacy_table,constraint_to_drop;
    IF finished=1 THEN LEAVE detach_loop; END IF;
    SET @scx_rta_detach_sql=CONCAT('ALTER TABLE `',REPLACE(legacy_table,'`','``'),
      '` DROP FOREIGN KEY `',REPLACE(constraint_to_drop,'`','``'),'`');
    PREPARE scx_rta_detach_statement FROM @scx_rta_detach_sql;
    EXECUTE scx_rta_detach_statement;
    DEALLOCATE PREPARE scx_rta_detach_statement;
  END LOOP;
  CLOSE constraints_cursor;
END$$
CALL scx_rta131_detach_legacy()$$
DROP PROCEDURE scx_rta131_detach_legacy$$
DELIMITER ;

UPDATE sys_menu SET del_flag='1',update_time=NOW() WHERE id IN ('perm_ocpx_rta_add','perm_ocpx_rta_edit','perm_ocpx_rta_bind','perm_ocpx_rta_decision_view','perm_ocpx_rta_report');
INSERT INTO sys_menu(id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag) VALUES
('menu_ocpx_rta','RTA 管理','/ocpx/rta','0','lucide:shield-check','','',42,'0','app_base',NOW(),NOW(),'0'),
('menu_ocpx_rta_upstreams','广告主接入','/ocpx/rta/upstreams','menu_ocpx_rta','lucide:list','ocpx/rta/upstreams/index','ocpx:rta:upstreams:view',1,'0','app_base',NOW(),NOW(),'0'),
('perm_ocpx_rta_upstreams_edit','编辑广告主接入','','menu_ocpx_rta_upstreams','','','ocpx:rta:upstreams:edit',10,'1','app_base',NOW(),NOW(),'0'),
('menu_ocpx_rta_media','媒体接入','/ocpx/rta/media','menu_ocpx_rta','lucide:list','ocpx/rta/media/index','ocpx:rta:media:view',2,'0','app_base',NOW(),NOW(),'0'),
('perm_ocpx_rta_media_edit','编辑媒体接入','','menu_ocpx_rta_media','','','ocpx:rta:media:edit',10,'1','app_base',NOW(),NOW(),'0'),
('menu_ocpx_rta_bindings','任务绑定','/ocpx/rta/bindings','menu_ocpx_rta','lucide:list','ocpx/rta/bindings/index','ocpx:rta:bindings:view',3,'0','app_base',NOW(),NOW(),'0'),
('perm_ocpx_rta_bindings_edit','编辑任务绑定','','menu_ocpx_rta_bindings','','','ocpx:rta:bindings:edit',10,'1','app_base',NOW(),NOW(),'0'),
('menu_ocpx_rta_records','请求记录','/ocpx/rta/records','menu_ocpx_rta','lucide:list','ocpx/rta/records/index','ocpx:rta:records:view',4,'0','app_base',NOW(),NOW(),'0'),
('menu_ocpx_rta_logs','流程日志','/ocpx/rta/logs','menu_ocpx_rta','lucide:list','ocpx/rta/logs/index','ocpx:rta:logs:view',5,'0','app_base',NOW(),NOW(),'0'),
('menu_ocpx_rta_reports','统计报表','/ocpx/rta/reports','menu_ocpx_rta','lucide:list','ocpx/rta/reports/index','ocpx:rta:reports:view',6,'0','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),del_flag='0';
INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id) SELECT CONCAT('rta131_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r JOIN sys_menu m ON (m.id='menu_ocpx_rta' OR m.id LIKE 'menu_ocpx_rta_%' OR m.id LIKE 'perm_ocpx_rta_%') WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

-- Disable legacy gates until individual new bindings are reviewed and enabled.
UPDATE scx_ocpx_monitor_task SET rta_enabled=0 WHERE rta_enabled=1;
