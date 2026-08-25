-- V122: remove the retired OCPX full wire audit introduced by V121.
-- Run only after deploying an Engine build that no longer captures or replays
-- wire audits. MySQL DDL commits implicitly, so take a backup before execution.
-- V121 remains immutable as historical release evidence.

DELETE rm
FROM sys_role_menu rm
LEFT JOIN sys_menu m ON m.id = rm.menu_id
WHERE rm.menu_id IN ('perm_ocpx_flow_log_raw','perm_ocpx_flow_log_raw_export')
   OR m.permission IN ('ocpx:flow-log:raw','ocpx:flow-log:raw:export');

DELETE FROM sys_menu
WHERE id IN ('perm_ocpx_flow_log_raw','perm_ocpx_flow_log_raw_export')
   OR permission IN ('ocpx:flow-log:raw','ocpx:flow-log:raw:export');

DROP TABLE IF EXISTS scx_ocpx_wire_audit_payload;
DROP TABLE IF EXISTS scx_ocpx_wire_audit;
