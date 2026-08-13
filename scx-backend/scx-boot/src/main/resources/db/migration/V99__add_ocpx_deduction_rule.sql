-- Forward-only additive migration: OCPX deduction rule for link-media routes.
-- Back up and preview the exact target before applying this file to an
-- existing database; initialization never auto-runs this directory.

USE `smart_converge_x`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS scx_ocpx_deduction_rule (
  id VARCHAR(32) NOT NULL,
  promotion_link_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  ratio DOUBLE NOT NULL DEFAULT 1.0,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_deduction_rule_route (media_id,promotion_link_id,active_marker),
  KEY idx_ocpx_deduction_rule_link (promotion_link_id),
  CONSTRAINT fk_ocpx_deduction_rule_promotion_link FOREIGN KEY (promotion_link_id) REFERENCES scx_ocpx_promotion_link(id),
  CONSTRAINT fk_ocpx_deduction_rule_media FOREIGN KEY (media_id) REFERENCES scx_ocpx_media(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BUSINESS: link-media deduction ratio rule';

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_deduction_rule','扣量规则','/ocpx/deduction','0','lucide:percent','ocpx/deduction/index','ocpx:deduction-rule:list',45,'0','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_deduction_rule_list','查询扣量规则','','menu_ocpx_deduction_rule','ocpx:deduction-rule:list',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_deduction_rule_create','新增扣量规则','','menu_ocpx_deduction_rule','ocpx:deduction-rule:create',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_deduction_rule_update','编辑扣量规则','','menu_ocpx_deduction_rule','ocpx:deduction-rule:update',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_deduction_rule_delete','删除扣量规则','','menu_ocpx_deduction_rule','ocpx:deduction-rule:delete',40,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT CONCAT('v99_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (
  'menu_ocpx_deduction_rule','perm_ocpx_deduction_rule_list','perm_ocpx_deduction_rule_create',
  'perm_ocpx_deduction_rule_update','perm_ocpx_deduction_rule_delete'
)
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
