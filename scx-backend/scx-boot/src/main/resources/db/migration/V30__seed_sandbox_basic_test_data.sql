-- Repeatable, recognisable MVP test data.  It is limited to the existing
-- sandbox media subject and never configures a real callback or media endpoint.

INSERT INTO scx_partner (id,media_id,code,name,type,description,status,version,create_by,update_by,create_time,update_time)
SELECT 'partner_demo_mvp','media_sandbox','demo-upstream','MVP 演示合作方','UPSTREAM','sandbox/demo 基础测试数据','ACTIVE',0,'system','system',NOW(),NOW()
WHERE EXISTS (SELECT 1 FROM scx_media WHERE id='media_sandbox')
ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description),status='ACTIVE',update_by='system',update_time=NOW();

INSERT INTO scx_upstream_customer (id,media_id,code,name,description,status,version,create_by,update_by,create_time,update_time)
SELECT 'customer_demo_mvp','media_sandbox','demo-customer','MVP 演示客户','用于本地和受控 sandbox 验收','ACTIVE',0,'system','system',NOW(),NOW()
WHERE EXISTS (SELECT 1 FROM scx_media WHERE id='media_sandbox')
ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description),status='ACTIVE',update_by='system',update_time=NOW();

INSERT INTO scx_integration_document (id,media_id,name,type,document_version,storage_type,external_url,content_hash,status,version,create_by,update_by,create_time,update_time)
SELECT 'document_demo_mvp','media_sandbox','MVP 演示接入文档','MEDIA_CALLBACK',1,'EXTERNAL','https://example.invalid/scx/mvp-demo','sandbox-demo-document-v1','ACTIVE',0,'system','system',NOW(),NOW()
WHERE EXISTS (SELECT 1 FROM scx_media WHERE id='media_sandbox')
ON DUPLICATE KEY UPDATE name=VALUES(name),external_url=VALUES(external_url),content_hash=VALUES(content_hash),status='ACTIVE',update_by='system',update_time=NOW();

INSERT INTO scx_media_channel (id,media_id,code,name,document_id,description,status,version,create_by,update_by,create_time,update_time)
SELECT 'channel_demo_mvp','media_sandbox','demo-channel','MVP 演示媒体渠道','document_demo_mvp','用于 MVP 测试任务','ACTIVE',0,'system','system',NOW(),NOW()
WHERE EXISTS (SELECT 1 FROM scx_media WHERE id='media_sandbox')
ON DUPLICATE KEY UPDATE name=VALUES(name),document_id=VALUES(document_id),description=VALUES(description),status='ACTIVE',update_by='system',update_time=NOW();

INSERT INTO scx_media_channel_user (id,media_id,channel_id,account,name,status,version,create_time,update_time)
SELECT 'channel_user_demo_mvp','media_sandbox','channel_demo_mvp','demo-channel-account','MVP 演示渠道账户','ACTIVE',0,NOW(),NOW()
WHERE EXISTS (SELECT 1 FROM scx_media_channel WHERE id='channel_demo_mvp')
ON DUPLICATE KEY UPDATE name=VALUES(name),status='ACTIVE',update_time=NOW();

INSERT INTO scx_product (id,media_id,partner_id,customer_id,code,name,platform,description,status,version,create_by,update_by,create_time,update_time)
SELECT 'product_demo_mvp','media_sandbox','partner_demo_mvp','customer_demo_mvp','demo-product','MVP 演示产品','ANDROID','用于追踪、归因和日报测试','ACTIVE',0,'system','system',NOW(),NOW()
WHERE EXISTS (SELECT 1 FROM scx_partner WHERE id='partner_demo_mvp')
ON DUPLICATE KEY UPDATE customer_id=VALUES(customer_id),name=VALUES(name),description=VALUES(description),status='ACTIVE',update_by='system',update_time=NOW();

INSERT INTO scx_tracking_link (id,media_id,code,name,product_id,partner_id,customer_id,document_id,device_type,redirect_enabled,target_url,download_url,template_text,config_version,status,version,create_by,update_by,create_time,update_time)
SELECT 'link_demo_mvp','media_sandbox','demo-link','MVP 演示追踪链接','product_demo_mvp','partner_demo_mvp','customer_demo_mvp','document_demo_mvp','ANDROID',1,'https://example.invalid/scx/mvp-landing','https://example.invalid/scx/mvp.apk','https://example.invalid/scx/mvp-landing?task={task_code}',1,'ACTIVE',0,'system','system',NOW(),NOW()
WHERE EXISTS (SELECT 1 FROM scx_product WHERE id='product_demo_mvp')
ON DUPLICATE KEY UPDATE customer_id=VALUES(customer_id),document_id=VALUES(document_id),name=VALUES(name),target_url=VALUES(target_url),download_url=VALUES(download_url),template_text=VALUES(template_text),status='ACTIVE',update_by='system',update_time=NOW();

INSERT INTO scx_link_event_mapping (id,link_id,config_version,internal_event,external_event,unit_price,daily_limit,create_time) VALUES
('mapping_demo_mvp_click','link_demo_mvp',1,'CLICK','click',0.000000,NULL,NOW()),
('mapping_demo_mvp_activate','link_demo_mvp',1,'ACTIVATE','activate',1.500000,10000,NOW()),
('mapping_demo_mvp_register','link_demo_mvp',1,'REGISTER','register',3.000000,5000,NOW()),
('mapping_demo_mvp_pay','link_demo_mvp',1,'PAY','pay',8.000000,1000,NOW())
ON DUPLICATE KEY UPDATE external_event=VALUES(external_event),unit_price=VALUES(unit_price),daily_limit=VALUES(daily_limit);

INSERT INTO scx_tracking_task (id,media_id,code,name,link_id,channel_id,impression_enabled,rta_enabled,rta_endpoint_id,exposure_report_url,big_click_enabled,dedup_window_sec,attribution_window_hours,daily_conversion_limit,callback_target_ref,config_version,status,version,create_by,update_by,create_time,update_time)
SELECT 'task_demo_mvp','media_sandbox','demo-task','MVP 演示监测任务','link_demo_mvp','channel_demo_mvp',1,0,NULL,NULL,0,3600,168,10000,'sandbox',1,'ACTIVE',0,'system','system',NOW(),NOW()
WHERE EXISTS (SELECT 1 FROM scx_tracking_link WHERE id='link_demo_mvp')
ON DUPLICATE KEY UPDATE name=VALUES(name),link_id=VALUES(link_id),channel_id=VALUES(channel_id),dedup_window_sec=VALUES(dedup_window_sec),attribution_window_hours=VALUES(attribution_window_hours),daily_conversion_limit=VALUES(daily_conversion_limit),status='ACTIVE',update_by='system',update_time=NOW();

INSERT INTO scx_delivery_config_revision (id,resource_type,resource_id,config_version,snapshot_json,create_by,create_time) VALUES
('revision_link_demo_mvp','LINK','link_demo_mvp',1,JSON_OBJECT('seed','sandbox-demo','status','ACTIVE'),'system',NOW(3)),
('revision_task_demo_mvp','TASK','task_demo_mvp',1,JSON_OBJECT('seed','sandbox-demo','status','ACTIVE'),'system',NOW(3))
ON DUPLICATE KEY UPDATE snapshot_json=VALUES(snapshot_json),create_time=NOW(3);

INSERT INTO scx_delivery_status_history (id,resource_type,resource_id,previous_status,next_status,reason,operation_version,create_by,create_time) VALUES
('history_link_demo_mvp','LINK','link_demo_mvp','DRAFT','ACTIVE','sandbox/demo 初始化',1,'system',NOW(3)),
('history_task_demo_mvp','TASK','task_demo_mvp','DRAFT','ACTIVE','sandbox/demo 初始化',1,'system',NOW(3))
ON DUPLICATE KEY UPDATE reason=VALUES(reason),create_time=NOW(3);

INSERT INTO scx_rta_endpoint (id,media_id,code,name,rta_identifier,endpoint_url,description,status,version,create_by,update_by,create_time,update_time)
SELECT 'rta_demo_mvp','media_sandbox','demo-rta','MVP 演示 RTA','demo-rta','https://example.invalid/scx/rta','仅用于配置页面展示，不可作为真实目标','DRAFT',0,'system','system',NOW(),NOW()
WHERE EXISTS (SELECT 1 FROM scx_media WHERE id='media_sandbox')
ON DUPLICATE KEY UPDATE name=VALUES(name),endpoint_url=VALUES(endpoint_url),description=VALUES(description),status='DRAFT',update_by='system',update_time=NOW();

INSERT INTO scx_raw_event (id,source,event_id,task_id,event_type,business_event_key_hash,occurred_at,received_at,device_key_hash,payload_hash,payload_json,status) VALUES
('raw_demo_mvp_click','sandbox-seed','demo-mvp-click-001','task_demo_mvp','CLICK',NULL,NOW(3)-INTERVAL 5 MINUTE,NOW(3)-INTERVAL 5 MINUTE,'demo-device-hash-001','demo-payload-click-001',JSON_OBJECT('seed','sandbox-demo','event','CLICK'),'ACCEPTED'),
('raw_demo_mvp_activate','sandbox-seed','demo-mvp-activate-001','task_demo_mvp','ACTIVATE','demo-business-key-001',NOW(3)-INTERVAL 4 MINUTE,NOW(3)-INTERVAL 4 MINUTE,'demo-device-hash-001','demo-payload-activate-001',JSON_OBJECT('seed','sandbox-demo','event','ACTIVATE'),'ACCEPTED')
ON DUPLICATE KEY UPDATE received_at=VALUES(received_at),payload_json=VALUES(payload_json),status=VALUES(status);

INSERT INTO scx_click_dedup_result (click_event_id,rule_version,status,reason_code,canonical_click_event_id,processed_at)
VALUES ('raw_demo_mvp_click','v1','ELIGIBLE','FIRST_IN_WINDOW','raw_demo_mvp_click',NOW(3))
ON DUPLICATE KEY UPDATE status=VALUES(status),reason_code=VALUES(reason_code),canonical_click_event_id=VALUES(canonical_click_event_id),processed_at=NOW(3);

INSERT INTO scx_conversion_identity (id,source,task_id,event_type,business_event_key_hash,canonical_event_id,create_time)
VALUES ('identity_demo_mvp_activate','sandbox-seed','task_demo_mvp','ACTIVATE','demo-business-key-001','raw_demo_mvp_activate',NOW())
ON DUPLICATE KEY UPDATE canonical_event_id=VALUES(canonical_event_id),create_time=NOW();

INSERT INTO scx_conversion_dedup_result (conversion_event_id,status,reason_code,canonical_event_id,processed_at)
VALUES ('raw_demo_mvp_activate','CANONICAL','FIRST_BUSINESS_EVENT','raw_demo_mvp_activate',NOW(3))
ON DUPLICATE KEY UPDATE status=VALUES(status),reason_code=VALUES(reason_code),canonical_event_id=VALUES(canonical_event_id),processed_at=NOW(3);

INSERT INTO scx_attribution_result (id,conversion_event_id,revision,click_event_id,rule_version,status,reason_code,over_limit,review_status,current_flag,attributed_at)
VALUES ('attr_demo_mvp_activate','raw_demo_mvp_activate',1,'raw_demo_mvp_click','last-click-v1','ATTRIBUTED','WITHIN_WINDOW',0,'PENDING',1,NOW(3))
ON DUPLICATE KEY UPDATE click_event_id=VALUES(click_event_id),status=VALUES(status),reason_code=VALUES(reason_code),over_limit=0,review_status='PENDING',current_flag=1,attributed_at=NOW(3);

INSERT INTO scx_callback_job (id,attribution_result_id,action_type,target_ref,status,attempts,next_retry_at,last_error_code,last_error_message,create_time,update_time)
VALUES ('callback_demo_mvp_activate','attr_demo_mvp_activate','ORIGINAL','sandbox','SUCCEEDED',1,NULL,NULL,NULL,NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE status='SUCCEEDED',attempts=1,next_retry_at=NULL,last_error_code=NULL,last_error_message=NULL,update_time=NOW(3);

INSERT INTO scx_event_outbox (id,aggregate_id,event_type,payload_json,status,attempts,next_retry_at,create_time,sent_at) VALUES
('outbox_demo_mvp_click','raw_demo_mvp_click','RAW_EVENT_INGESTED',JSON_OBJECT('seed','sandbox-demo','aggregateId','raw_demo_mvp_click'),'SENT',1,NULL,NOW(3),NOW(3)),
('outbox_demo_mvp_activate','raw_demo_mvp_activate','ATTRIBUTION_COMPLETED',JSON_OBJECT('seed','sandbox-demo','aggregateId','raw_demo_mvp_activate'),'SENT',1,NULL,NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE status='SENT',attempts=1,next_retry_at=NULL,sent_at=NOW(3);

INSERT INTO scx_report_run (id,report_date,timezone,input_cutoff_received_at,input_snapshot_hash,rule_version,status,started_at,finished_at)
VALUES ('report_run_demo_mvp',CURDATE(),'Asia/Shanghai',NOW(3),'sandbox-demo-report-v1','last-click-v1','SUCCEEDED',NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE report_date=CURDATE(),input_cutoff_received_at=NOW(3),status='SUCCEEDED',finished_at=NOW(3);

INSERT INTO scx_report_daily (id,report_run_id,report_date,timezone,media_id,partner_id,customer_id,product_id,link_id,document_id,task_id,channel_id,impression_count,unique_click_count,eligible_click_count,deduplicated_click_count,attributed_activate_count,attributed_register_count,attributed_pay_count,attributed_order_count,next_day_return_count,retention_3d_count,retention_7d_count,first_activate_count,first_order_count,reactivate_count,custom_count,credit_count,revision,calculated_at)
VALUES ('report_daily_demo_mvp','report_run_demo_mvp',CURDATE(),'Asia/Shanghai','media_sandbox','partner_demo_mvp','customer_demo_mvp','product_demo_mvp','link_demo_mvp','document_demo_mvp','task_demo_mvp','channel_demo_mvp',120,24,22,2,12,7,3,2,0,0,0,12,2,1,0,0,1,NOW(3))
ON DUPLICATE KEY UPDATE report_date=CURDATE(),impression_count=120,unique_click_count=24,eligible_click_count=22,deduplicated_click_count=2,attributed_activate_count=12,attributed_register_count=7,attributed_pay_count=3,attributed_order_count=2,first_activate_count=12,first_order_count=2,calculated_at=NOW(3);
