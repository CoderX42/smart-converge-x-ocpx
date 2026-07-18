CREATE TABLE IF NOT EXISTS scx_export_task (
  id VARCHAR(32) NOT NULL,
  requester_user_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  export_type VARCHAR(32) NOT NULL,
  report_start_date DATE NOT NULL,
  report_end_date DATE NOT NULL,
  query_snapshot_hash VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  row_count INT NOT NULL DEFAULT 0,
  artifact_name VARCHAR(255) NULL,
  artifact_content MEDIUMBLOB NULL,
  artifact_sha256 VARCHAR(64) NULL,
  expires_at DATETIME(3) NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(1000) NULL,
  requested_at DATETIME(3) NOT NULL,
  started_at DATETIME(3) NULL,
  finished_at DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_export_task_media_requested (media_id, requested_at),
  KEY idx_scx_export_task_status_requested (status, requested_at),
  KEY idx_scx_export_task_expire (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='受限日报 CSV 导出任务与受控产物';

INSERT INTO sys_menu (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag) VALUES
('menu_ad_exports','导出任务','/operations/exports','menu_ad_data','lucide:file-down','operations/exports/index',60,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),component=VALUES(component),del_flag='0';

INSERT INTO sys_menu (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag) VALUES
('perm_ad_export_create','创建报表导出','ad:export:create','menu_ad_exports',0,'1','app_base',NOW(),'0'),
('perm_ad_export_read','查询和下载报表导出','ad:export:read','menu_ad_exports',0,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),del_flag='0';

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adm_', id), '1', id FROM sys_menu WHERE id IN ('menu_ad_exports','perm_ad_export_create','perm_ad_export_read')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id FROM sys_menu WHERE id IN ('menu_ad_exports','perm_ad_export_create','perm_ad_export_read')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id FROM sys_menu WHERE id IN ('menu_ad_exports','perm_ad_export_create','perm_ad_export_read')
ON DUPLICATE KEY UPDATE id=VALUES(id);
