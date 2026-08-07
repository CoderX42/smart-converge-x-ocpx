-- V73: register the generic macro-based downstream GET callback adapter.
INSERT INTO scx_ocpx_adapter
  (adapter_code,adapter_name,direction,status,capabilities_json,create_time,update_time)
VALUES
  ('TEMPLATE_GET_DOWNSTREAM','通用下游 GET 回传','DOWNSTREAM','ACTIVE',
   JSON_OBJECT('feedbackMethod','POST','callbackMethod','GET','auth','HMAC','callback',TRUE,'timeoutMs',3000),
   NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE adapter_name=VALUES(adapter_name),direction=VALUES(direction),status=VALUES(status),
  capabilities_json=VALUES(capabilities_json),update_time=NOW(3);
