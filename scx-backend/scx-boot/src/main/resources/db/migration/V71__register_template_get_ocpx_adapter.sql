-- V71: register the generic macro based upstream adapter used for sgyz-ocpx
-- style show/click forwarding. Partner-specific signatures remain separate adapters.

INSERT INTO scx_ocpx_adapter
  (adapter_code,adapter_name,direction,status,capabilities_json,create_time,update_time)
VALUES
  ('TEMPLATE_GET','通用宏模板 GET','UPSTREAM','ACTIVE',
   JSON_OBJECT(
     'feedbackMethod','GET',
     'feedbackEvents',JSON_ARRAY('SHOW','CLICK'),
     'callbackMethod','GET',
     'callback',TRUE,
     'auth','NONE',
     'timeoutMs',3000,
     'compatibility','sgyz-ocpx-base-flow'
   ),NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE
  adapter_name=VALUES(adapter_name),
  direction=VALUES(direction),
  status=VALUES(status),
  capabilities_json=VALUES(capabilities_json),
  update_time=NOW(3);
