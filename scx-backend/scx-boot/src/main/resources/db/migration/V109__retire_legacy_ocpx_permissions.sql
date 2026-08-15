-- Forward-only cleanup for four legacy OCPX permissions that remained active
-- in environments upgraded through older migration paths.
--
-- This migration intentionally preserves the sys_menu rows as soft-deleted
-- history. Back up and preview the target database before applying it.

USE `smart_converge_x`;
SET NAMES utf8mb4;

START TRANSACTION;

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id
WHERE m.permission IN (
  'ocpx:api-debug:read',
  'ocpx:monitor:publish',
  'ocpx:monitor:read',
  'ocpx:monitor:write'
);

UPDATE sys_menu
SET del_flag='1',update_time=NOW()
WHERE permission IN (
  'ocpx:api-debug:read',
  'ocpx:monitor:publish',
  'ocpx:monitor:read',
  'ocpx:monitor:write'
);

COMMIT;
