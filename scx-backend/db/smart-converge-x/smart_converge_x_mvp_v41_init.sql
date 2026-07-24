-- Smart Converge X MVP bootstrap through V41 (empty database only).
-- Run from this directory with the MySQL command-line client.
-- Includes the repeatable sandbox/demo seeds from V25 and V30.
-- Never use this aggregate entry to upgrade an existing database.

source smart_converge_x_mvp_v31_init.sql
source ../../scx-boot/src/main/resources/db/migration/V32__reorg_ocpx_menu_tree_for_t1.sql
source ../../scx-boot/src/main/resources/db/migration/V33__normalize_ocpx_menu_duplicate_names.sql
source ../../scx-boot/src/main/resources/db/migration/V34__split_master_data_pages.sql
source ../../scx-boot/src/main/resources/db/migration/V35__add_clk_end_user_pages.sql
source ../../scx-boot/src/main/resources/db/migration/V36__add_report_dimensions.sql
source ../../scx-boot/src/main/resources/db/migration/V37__add_toolbox_pages.sql
source ../../scx-boot/src/main/resources/db/migration/V38__add_queue_overview_menu.sql
source ../../scx-boot/src/main/resources/db/migration/V39__add_platform_credentials_menu.sql
source ../../scx-boot/src/main/resources/db/migration/V40__add_ocpx_channel_ingress.sql
source ../../scx-boot/src/main/resources/db/migration/V41__add_ocpx_channel_management.sql
