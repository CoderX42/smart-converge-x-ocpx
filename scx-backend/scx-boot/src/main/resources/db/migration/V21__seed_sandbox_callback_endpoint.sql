INSERT INTO scx_ad_platform_config (id,media_id,config_key,config_value,description,status,version,create_time,update_time)
VALUES ('config_callback_sandbox','media_sandbox','callback.endpoint.sandbox','http://callback-mock:8080/callback','沙箱媒体回传 HTTP 目标','ACTIVE',0,NOW(),NOW())
ON DUPLICATE KEY UPDATE config_value=VALUES(config_value),description=VALUES(description),status='ACTIVE';
