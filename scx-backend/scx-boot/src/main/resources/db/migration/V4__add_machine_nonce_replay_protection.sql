CREATE TABLE IF NOT EXISTS scx_machine_nonce (
  nonce VARCHAR(128) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (nonce),
  KEY idx_scx_machine_nonce_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='沙箱机器接口 nonce 防重放记录';
