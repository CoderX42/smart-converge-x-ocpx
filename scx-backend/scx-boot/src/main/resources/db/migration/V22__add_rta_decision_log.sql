CREATE TABLE IF NOT EXISTS scx_rta_decision_log (
  id VARCHAR(64) NOT NULL,
  task_id VARCHAR(64) NOT NULL,
  endpoint_id VARCHAR(64) NOT NULL,
  request_id VARCHAR(128) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  decision VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  reason_code VARCHAR(64) NULL,
  latency_ms INT NULL,
  requested_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_rta_decision_task_request (task_id,request_id),
  KEY idx_scx_rta_decision_endpoint_time (endpoint_id,requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RTA 决策运行记录';
