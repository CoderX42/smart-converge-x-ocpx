-- Local/demo-safe operational chain. It is deliberately scoped to the existing
-- sandbox media subject and gives a new environment enough real objects to run
-- the event -> attribution -> callback -> reporting walkthrough.

INSERT INTO scx_partner (id,media_id,code,name,type,description,status,version,create_by,update_by,create_time,update_time)
SELECT 'partner_demo','media_sandbox','demo-upstream','演示上游合作方','UPSTREAM','用于本地 MVP 事件闭环演示','ACTIVE',0,'system','system',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM scx_partner WHERE id='partner_demo');

INSERT INTO scx_upstream_customer (id,media_id,code,name,description,status,version,create_by,update_by,create_time,update_time)
SELECT 'customer_demo','media_sandbox','demo-customer','演示上游客户','与演示产品、链接和任务关联','ACTIVE',0,'system','system',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM scx_upstream_customer WHERE id='customer_demo');

INSERT INTO scx_integration_document (id,media_id,name,type,document_version,storage_type,external_url,content_hash,status,version,create_by,update_by,create_time,update_time)
SELECT 'document_demo','media_sandbox','演示媒体接入文档','MEDIA_CALLBACK',1,'EXTERNAL','https://example.com/scx-demo-callback-guide','sandbox-demo-v1','ACTIVE',0,'system','system',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM scx_integration_document WHERE id='document_demo');

INSERT INTO scx_media_channel (id,media_id,code,name,document_id,description,status,version,create_by,update_by,create_time,update_time)
SELECT 'channel_demo','media_sandbox','demo-channel','演示媒体渠道','document_demo','使用演示文档的可投放媒体渠道','ACTIVE',0,'system','system',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM scx_media_channel WHERE id='channel_demo');

INSERT INTO scx_product (id,media_id,partner_id,customer_id,code,name,platform,description,status,version,create_by,update_by,create_time,update_time)
SELECT 'product_demo','media_sandbox','partner_demo','customer_demo','demo-product','SCX 演示产品','ANDROID','用于追踪和转化归因演示','ACTIVE',0,'system','system',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM scx_product WHERE id='product_demo');

INSERT INTO scx_media_channel_user (id,media_id,channel_id,account,name,status,version,create_time,update_time)
SELECT 'channel_user_demo','media_sandbox','channel_demo','demo-channel-account','演示渠道账户','ACTIVE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM scx_media_channel_user WHERE id='channel_user_demo');

INSERT INTO scx_tracking_link (id,media_id,code,name,product_id,partner_id,customer_id,document_id,device_type,redirect_enabled,target_url,download_url,template_text,config_version,status,version,create_by,update_by,create_time,update_time)
SELECT 'link_demo','media_sandbox','demo-link','SCX 演示追踪链接','product_demo','partner_demo','customer_demo','document_demo','ANDROID',1,'https://example.com/scx-demo-landing','https://example.com/scx-demo.apk','https://example.com/scx-demo-landing?task={task_code}',1,'ACTIVE',0,'system','system',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM scx_tracking_link WHERE id='link_demo');

INSERT INTO scx_link_event_mapping (id,link_id,config_version,internal_event,external_event,create_time) VALUES
('mapping_demo_activate','link_demo',1,'ACTIVATE','activate',NOW()),
('mapping_demo_register','link_demo',1,'REGISTER','register',NOW()),
('mapping_demo_pay','link_demo',1,'PAY','pay',NOW()),
('mapping_demo_order','link_demo',1,'ORDER','order',NOW())
ON DUPLICATE KEY UPDATE external_event=VALUES(external_event);

INSERT INTO scx_tracking_task (id,media_id,code,name,link_id,channel_id,impression_enabled,rta_enabled,rta_endpoint_id,exposure_report_url,big_click_enabled,dedup_window_sec,attribution_window_hours,daily_conversion_limit,callback_target_ref,config_version,status,version,create_by,update_by,create_time,update_time)
SELECT 'task_demo','media_sandbox','demo-task','SCX 演示监测任务','link_demo','channel_demo',1,0,NULL,NULL,0,3600,168,10000,'sandbox',1,'ACTIVE',0,'system','system',NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM scx_tracking_task WHERE id='task_demo');

INSERT INTO scx_delivery_config_revision (id,resource_type,resource_id,config_version,snapshot_json,create_by,create_time)
VALUES
('revision_link_demo','LINK','link_demo',1,JSON_OBJECT('seed','sandbox-demo','status','ACTIVE'),'system',NOW(3)),
('revision_task_demo','TASK','task_demo',1,JSON_OBJECT('seed','sandbox-demo','status','ACTIVE'),'system',NOW(3))
ON DUPLICATE KEY UPDATE snapshot_json=VALUES(snapshot_json);

INSERT INTO scx_delivery_status_history (id,resource_type,resource_id,previous_status,next_status,reason,operation_version,create_by,create_time)
VALUES
('history_link_demo','LINK','link_demo','DRAFT','ACTIVE','沙箱演示初始化',0,'system',NOW(3)),
('history_task_demo','TASK','task_demo','DRAFT','ACTIVE','沙箱演示初始化',0,'system',NOW(3))
ON DUPLICATE KEY UPDATE reason=VALUES(reason);
