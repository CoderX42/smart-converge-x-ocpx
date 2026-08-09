INSERT INTO scx_adx_channel
  (id,channel_code,channel_name,adapter_code,auth_type,key_version,uri,secret_key,macro_params,
   callback_host_allowlist,status,config_version,deleted,create_time,update_time)
VALUES
  ('perf_channel_id','perf-channel','JMeter 隔离渠道','STANDARD','HMAC',1,
   'http://127.0.0.1:19999/ocpx/open-api/ocpx/v1/channels/perf-channel/feedback',
   @scx_perf_channel_secret,'{}','127.0.0.1','ACTIVE',1,0,NOW(3),NOW(3));

INSERT INTO scx_adx_advertiser
  (id,advertiser_name,adapter_code,bid_url,secret_key,macro_params,upstream_host_allowlist,account,
   password_hash,balance,status,config_version,deleted,create_time,update_time)
VALUES
  ('perf_advertiser_id','JMeter 隔离广告主','TEMPLATE_GET',
   'http://127.0.0.1:18081/upstream?event_id=__EVENT_ID__&callback=__CALLBACK__',
   NULL,'__EVENT_ID__,__CALLBACK__','127.0.0.1','perf-advertiser','not-a-login-password',0.000000,'ACTIVE',1,0,NOW(3),NOW(3));

INSERT INTO scx_adx_app_link
  (id,advertiser_id,link_platform,link_name,link_channel,link_url,deleted,create_time,update_time)
VALUES
  ('perf_app_link_id','perf_advertiser_id',3,'JMeter 隔离应用','local',
   'http://127.0.0.1:18081/app',0,NOW(3),NOW(3));

INSERT INTO scx_adx_monitor_link
  (id,channel_id,advertiser_id,app_link_id,monitor_name,biz_id,unit_price,remark,show_url,click_url,
   upstream_feedback_url,published_upstream_feedback_url,downstream_callback_url,route_status,
   config_version,rta_mode,billing_mode,click_dedup_window_seconds,published_at,deleted,create_time,update_time)
VALUES
  ('perf_monitor_id','perf_channel_id','perf_advertiser_id','perf_app_link_id','JMeter 隔离监测链接',
   'perf-task',0.000000,'disposable local JMeter route',NULL,NULL,
   'http://127.0.0.1:18081/upstream?event_id=__EVENT_ID__&callback=__CALLBACK__',
   'http://127.0.0.1:18081/upstream?event_id=__EVENT_ID__&callback=__CALLBACK__',
   'http://127.0.0.1:18081/postback','PUBLISHED',1,'OFF','OFF',2,NOW(3),0,NOW(3),NOW(3));

INSERT INTO scx_adx_deduction_rule
  (id,channel_id,app_link_id,ratio,config_version,deleted,create_time,update_time)
VALUES
  ('perf_deduction_id','perf_channel_id','perf_app_link_id',1.0000,1,0,NOW(3),NOW(3));
