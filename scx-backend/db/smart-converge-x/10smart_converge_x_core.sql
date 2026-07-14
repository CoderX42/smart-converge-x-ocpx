-- Smart Converge X complete baseline core schema
-- Keeps system, member, payment, open-platform, generator and transaction infrastructure tables.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `gen_data_source`  (
                                    `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                    `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                    `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                    `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                    `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '名称',
                                    `url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'jdbc url',
                                    `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名',
                                    `password` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '密码',
                                    `db_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '数据库名称',
                                    `port` int NULL DEFAULT NULL COMMENT '端口',
                                    `host` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主机',
                                    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '数据源表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `gen_table`  (
                              `table_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                              `table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '表名称',
                              `table_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '表描述',
                              `sub_table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联父表的表名',
                              `sub_table_fk_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '本表关联父表的外键名',
                              `class_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '实体类名称(首字母大写)',
                              `tpl_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '使用的模板（crud单表操作 tree树表操作 sub主子表操作）',
                              `tpl_web_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '前端类型（element-ui模版 element-plus模版）',
                              `package_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '生成包路径',
                              `module_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '生成模块名',
                              `business_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '生成业务名',
                              `function_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '生成功能名',
                              `gen_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
                              `gen_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生成路径（不填默认项目路径）',
                              `options` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '其它生成选项',
                              `tree_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '树编码字段',
                              `tree_parent_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '树父编码字段',
                              `tree_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '树名称字段',
                              `parent_menu_id` bigint NULL DEFAULT NULL COMMENT '上级菜单ID字段',
                              `parent_menu_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上级菜单名称字段',
                              `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                              `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                              `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                              `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                              `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                              `ds_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '数据源名称',
                              PRIMARY KEY (`table_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '代码生成表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `gen_table_column`  (
                                     `column_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                     `table_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '归属表ID',
                                     `column_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列名称',
                                     `column_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列描述',
                                     `column_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列类型',
                                     `java_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'JAVA类型',
                                     `java_field` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'JAVA字段名',
                                     `is_pk` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否主键（1是）',
                                     `is_increment` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否自增（1是）',
                                     `is_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否必填（1是）',
                                     `is_insert` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否为插入字段（1是）',
                                     `is_edit` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否编辑字段（1是）',
                                     `is_list` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否列表字段（1是）',
                                     `is_query` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否查询字段（1是）',
                                     `query_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '查询方式（EQ等于、NE不等于、GT大于、LT小于、LIKE模糊、BETWEEN范围）',
                                     `html_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示类型（input、textarea、select、checkbox、radio、datetime、image、upload、editor）',
                                     `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '字典类型',
                                     `sort` int NULL DEFAULT NULL COMMENT '排序',
                                     `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                     `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                     `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                     `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                                     `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                     PRIMARY KEY (`column_id`) USING BTREE,
                                     INDEX `idx_table_id`(`table_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '代码生成业务字段表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `open_code_audit`  (
                                    `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                    `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '状态',
                                    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                    `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                    `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                    `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                    `audit_id` int NULL DEFAULT NULL COMMENT '审核 id',
                                    `template_info_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '模板库ID',
                                    `template_id` int NULL DEFAULT NULL COMMENT '微信模板ID',
                                    `upload_time` datetime NULL DEFAULT NULL COMMENT '模板上传时间（冗余）',
                                    `user_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '版本号（冗余）',
                                    `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '应用ID',
                                    `app_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '应用名称',
                                    `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '驳回原因',
                                    `screenshot` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '核失败的小程序截图示例',
                                    `audit_time` datetime NULL DEFAULT NULL COMMENT '审核时间',
                                    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '代码审核记录表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `open_component_info`  (
                                        `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                        `component_app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '三方平台AppId',
                                        `component_app_secret` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '三方平台AppSecret',
                                        `component_token` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息校验Token',
                                        `component_encoding_aes_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息加解密Key',
                                        `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '状态：0.正常；1.关闭；',
                                        `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                        `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                        `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                        `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                        `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                        `authorized_domains` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '域名白名单',
                                        PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '三方平台基础信息表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `open_mini_auth`  (
                                   `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '主键ID',
                                   `customer_type` tinyint NOT NULL COMMENT '主体类型：企业=1，个体工商户=12，个人=15',
                                   `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '认证任务id，打回重审调用reauth时必填',
                                   `contact_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '认证联系人姓名',
                                   `contact_email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '认证联系人邮箱',
                                   `contact_mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '认证联系人手机号，仅打回重填时可填',
                                   `invoice_type` tinyint NULL DEFAULT NULL COMMENT '发票类型 1:不开发票 2:电子发票 4:增值税专票(数电类型)',
                                   `electronic_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '电子发票-纳税识别号',
                                   `electronic_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '电子发票备注',
                                   `vat_enterprise_phone` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '增值税专票-企业电话',
                                   `vat_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '增值税专票-纳税识别号',
                                   `vat_enterprise_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '增值税专票-企业注册地址',
                                   `vat_bank_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '增值税专票-开户银行',
                                   `vat_bank_account` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '增值税专票-银行账号',
                                   `vat_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '增值税专票-备注',
                                   `qualification` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '主体资质材料 media_id（非个人必填）',
                                   `qualification_other` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '主体资质其他证明材料 media_id数组，最多10张',
                                   `account_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '小程序账号名称',
                                   `account_name_type` tinyint NOT NULL COMMENT '命名类型 1:自选词汇命名 2:商标命名',
                                   `account_supplemental` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '名称命中关键词补充材料 media_id 数组',
                                   `pay_type` tinyint NOT NULL COMMENT '支付方式 1:消耗服务商预购包 2:开发者自行支付',
                                   `auth_identification` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '认证类型为个人时的身份标识',
                                   `auth_ident_material` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '身份证明材料 media_id 数组',
                                   `third_party_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '第三方联系电话',
                                   `service_appid` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务市场appid，服务商代缴必填',
                                   `task_status` tinyint NULL DEFAULT NULL COMMENT '当前任务流程的状态码：0: 初始状态；1: 任务超时, 24小时内有效；2: 用户授权拒绝；3: 用户授权同意；4: 发起人脸流程；5: 人脸认证失败；6: 人脸认证ok；7: 人脸认证后，已经提交手机号码下发验证码；8: 手机验证失败；9: 手机验证成功；11: 创建认证审核单失败；12: 创建认证审核审核单成功；14: 验证失败；15: 等待支付',
                                   `auth_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '小程序管理员授权链接',
                                   `apply_status` tinyint NULL DEFAULT NULL COMMENT '审核单状态，创建认证审核审核单成功后该值有效。0：审核单不存在；1：待支付；2：审核中；3：打回重填；4：认证通过；5：认证最终失败（不能再修改）',
                                   `order_id` int NULL DEFAULT NULL COMMENT '小程序后台展示的认证订单号',
                                   `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '小程序appid',
                                   `refill_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '当审核单被打回重填(apply_status=3)时有效',
                                   `fail_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '审核最终失败的原因(apply_status=5)时有效',
                                   `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                   `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                   `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                   `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                   `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                   `auth_msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '提示信息',
                                   PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '微信开放平台小程序认证表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `open_mini_icp`  (
                                  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '备案人脸核身任务ID',
                                  `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '小程序ID',
                                  `subject_type` tinyint NULL DEFAULT NULL COMMENT '主体性质',
                                  `subject_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主办单位名称',
                                  `subject_province` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备案省份',
                                  `subject_city` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备案城市',
                                  `subject_district` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备案县区',
                                  `subject_address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '通讯地址',
                                  `subject_comment` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主体信息备注',
                                  `subject_record_number` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主体备案号',
                                  `applets_appid` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '小程序ID(备案用)',
                                  `applets_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '小程序名称',
                                  `applets_service_content_types` json NULL COMMENT '小程序服务内容类型(JSON数组)',
                                  `applets_comment` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '小程序实际经营内容',
                                  `applets_record_number` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '小程序备案号',
                                  `subject_json` json NULL COMMENT '主体其余信息JSON(包含personal_info, organize_info, principal_info, legal_person_info)',
                                  `applets_json` json NULL COMMENT '小程序其余信息JSON(包含nrlx_details, principal_info)',
                                  `materials_json` json NULL COMMENT '其他备案媒体材料JSON',
                                  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                  `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                  `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                  `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                  `face_status` tinyint NULL DEFAULT NULL COMMENT '人脸核身状态',
                                  `status` tinyint NULL DEFAULT NULL COMMENT '备案状态',
                                  `sms_verify_status` tinyint NULL DEFAULT NULL COMMENT '管局短信核验状态，仅当备案状态为 4（管局审核中）的时候才有效。1：等待核验中，2：核验完成，3：核验超时。',
                                  `fail_reason` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '驳回原因',
                                  `use_principal_info` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '使用主体负责人信息',
                                  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '微信小程序备案表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `open_miniapp_register`  (
                                          `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                          `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '企业名',
                                          `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '企业代码',
                                          `code_type` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '企业代码类型',
                                          `legal_persona_wechat` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '法人微信号',
                                          `legal_persona_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '法人姓名',
                                          `component_phone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '第三方联系电话',
                                          `status` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '状态',
                                          `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                          `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                          `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                          `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                          `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                          `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'appId',
                                          `type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型：1.注册企业小程序；2.注册试用小程序；3.试用小程序转正',
                                          `unique_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '唯一标识符(试用小程序使用)',
                                          `authorize_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户授权确认url(试用小程序使用)',
                                          `open_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户openId(试用小程序使用)',
                                          `msg_info` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息信息',
                                          `legal_persona_id_card` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '法人身份证号(试用转正使用)',
                                          PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '小程序注册表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `open_platform_app`  (
                                      `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用ID(appid)',
                                      `app_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '应用名称',
                                      `platform_type` char(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '平台类型',
                                      `app_secret` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '应用密钥',
                                      `token` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '令牌，可选',
                                      `aes_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'AES Key，可选',
                                      `extra_json` json NULL COMMENT '扩展信息',
                                      `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                      `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                      `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                      `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                      `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0显示，1隐藏',
                                      `shop_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '业务主体ID',
                                      `qrcode_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '二维码地址',
                                      `component_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '三方平台状态：0.未授权；1.已授权',
                                      PRIMARY KEY (`app_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '开放平台应用表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `open_platform_user`  (
                                       `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键ID',
                                       `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '系统统一用户ID',
                                       `platform_type` char(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '平台类型',
                                       `app_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用ID（小程序/公众号/appId）',
                                       `open_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '平台用户唯一ID（openid / alipay userId 等）',
                                       `union_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'unionId（微信体系）',
                                       `nickname` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '平台昵称',
                                       `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像',
                                       `session_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '会话密钥',
                                       `extra_json` json NULL COMMENT '平台扩展信息',
                                       `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                       `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                       `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏',
                                       `subscribe_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关注状态：0.未关注；1.已关注；',
                                       `subscribe_time` datetime NULL DEFAULT NULL COMMENT '关注时间',
                                       `unsubscribe_time` datetime NULL DEFAULT NULL COMMENT '取消关注时间',
                                       PRIMARY KEY (`id`) USING BTREE,
                                       UNIQUE INDEX `uk_platform_user`(`platform_type` ASC, `app_id` ASC, `open_id` ASC) USING BTREE,
                                       INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
                                       INDEX `idx_union_id`(`union_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '开放平台用户表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `open_template_draft`  (
                                        `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                        `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                        `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                        `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                        `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                        `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                        `upload_time` datetime NULL DEFAULT NULL COMMENT '上传时间',
                                        `user_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '版本号',
                                        `draft_id` int NULL DEFAULT NULL COMMENT '草稿 id',
                                        `source_miniprogram_appid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '开发小程序的appid',
                                        `source_miniprogram` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '开发小程序的名称',
                                        `developer` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作者微信昵称',
                                        PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模板库草稿表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `open_template_info`  (
                                       `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                       `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                       `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                       `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                       `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                       `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                       `upload_time` datetime NULL DEFAULT NULL COMMENT '上传时间',
                                       `user_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '版本号',
                                       `draft_id` int NULL DEFAULT NULL COMMENT '草稿 id',
                                       `user_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '版本描述 ',
                                       `template_id` int NULL DEFAULT NULL COMMENT '模板 id',
                                       `source_miniprogram_appid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '开发小程序的appid',
                                       `source_miniprogram` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '开发小程序的名称',
                                       `template_type` int NULL DEFAULT NULL COMMENT '0对应普通模板，1对应标准模板',
                                       `category_list` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '标准模板的类目信息；如果是普通模板则值为空的数组',
                                       `audit_scene` int NULL DEFAULT NULL COMMENT '标准模板的场景标签；普通模板不返回该值',
                                       `audit_status` int NULL DEFAULT NULL COMMENT '标准模板的审核状态；普通模板不返回该值',
                                       `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '标准模板的审核驳回的原因，；普通模板不返回该值',
                                       `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '状态',
                                       PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '模板库模板表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `pay_config`  (
                               `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                               `type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '支付类型：1.微信；2.支付宝；',
                               `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '应用Id',
                               `mch_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商户号',
                               `mch_key` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商户密钥',
                               `key_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'apiclient_cert.p12文件的绝对路径，或者如果放在项目中，请以classpath:开头指定.',
                               `cert_serial_no` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '证书序列号（废弃）',
                               `apiv3_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'apiV3秘钥',
                               `private_key_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'apiv3 商户apiclient_key.pem',
                               `private_cert_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'apiv3 商户apiclient_cert.pem',
                               `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                               `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                               `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                               `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                               `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                               `terminal_type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '支付端类型',
                               `mode` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '支付模式：0=普通商户；1=服务商',
                               `sub_mchid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '子商户号（用于服务商模式平台收款使用）',
                               `public_key_path` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '微信支付公钥',
                               `public_key_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '微信支付公钥ID',
                               PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '支付配置' ROW_FORMAT = DYNAMIC;

CREATE TABLE `pay_notify_record`  (
                                      `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                      `out_trade_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商户订单号',
                                      `channel_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道订单号',
                                      `request` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '请求报文',
                                      `response` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '响应报文',
                                      `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                      `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                      `type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '回调类型：1.支付回调；2.退款回调',
                                      `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                      PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '回调通知记录' ROW_FORMAT = DYNAMIC;

CREATE TABLE `pay_refund_order`  (
                                     `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                     `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                     `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                     `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                     `notify_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '通知地址',
                                     `channel_mch_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道号',
                                     `channel_refund_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道退款单号',
                                     `refund_trade_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商户退款单号',
                                     `refund_status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '退款状态：0.待退款；1.退款中；2.已退款；3.退款失败；',
                                     `extra` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '额外参数',
                                     `err_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道退款错误码',
                                     `err_msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道退款错误描述',
                                     `pay_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '支付总金额（元）',
                                     `refund_amount` decimal(10, 2) NOT NULL COMMENT '退款金额（元）',
                                     `out_trade_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商户支付订单号',
                                     `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                     `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                     `refund_success_time` datetime NULL DEFAULT NULL COMMENT '退款成功时间',
                                     `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户ID',
                                     `sub_mchid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '子商户号',
                                     `terminal_type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支付端类型',
                                     PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '退款订单' ROW_FORMAT = DYNAMIC;

CREATE TABLE `pay_trade_order`  (
                                    `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                    `channel_mch_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道号',
                                    `out_trade_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商户订单号',
                                    `description` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单描述',
                                    `trade_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '交易类型',
                                    `open_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户id（openid、支付宝buyerId）',
                                    `pay_status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '支付状态：0.待支付；1.已支付；',
                                    `channel_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道订单号',
                                    `extra` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '额外参数',
                                    `err_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道支付错误码',
                                    `err_msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渠道支付错误描述',
                                    `pay_success_time` datetime NULL DEFAULT NULL COMMENT '支付成功时间',
                                    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                    `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                    `notify_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '通知地址',
                                    `amount` decimal(10, 2) NOT NULL COMMENT '金额',
                                    `return_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                                    `quit_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
                                    `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                    `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                    `terminal_type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支付端类型',
                                    `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户ID',
                                    `pay_mode` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '支付模式：0=普通商户；1=服务商',
                                    `wx_sub_mchid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '微信子商户号',
                                    `alipay_sub_mchid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支付宝子商户号',
                                    `profit_sharing_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '分账状态：0=否，1=是',
                                    `wx_sub_appid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '子商户应用AppId',
                                    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '支付订单' ROW_FORMAT = DYNAMIC;

CREATE TABLE `sys_ad_launch`  (
                                  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                  `ad_img_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '广告图',
                                  `ad_to_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '跳转地址',
                                  `countdown_seconds` int NULL DEFAULT 3 COMMENT '启动广告倒计时（秒）',
                                  `countdown_position` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '倒计时按钮位置：top-left、top-right、bottom-left、bottom-right',
                                  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                                  `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                  `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                  `status` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '状态：0.正常；1.停用；',
                                  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '启动广告配置表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `sys_agreement`  (
                                  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                  `title` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标题',
                                  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '协议内容',
                                  `type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型：1-服务协议；2-隐私协议；3-推广协议；',
                                  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                                  `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                  `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统协议表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `sys_article`  (
                                `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文章内容',
                                `title` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标题',
                                `author` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '作者',
                                `cover_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '封面图',
                                `sort` int NULL DEFAULT NULL COMMENT '排序序号',
                                `category_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分类ID',
                                `status` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '状态：0.正常；1.停用；',
                                `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文章表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `sys_article_category`  (
                                         `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                                         `name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '分类名称',
                                         `sort` int NULL DEFAULT NULL COMMENT '排序序号',
                                         `status` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '状态：0.正常；1.停用；',
                                         `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                         `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                         `del_flag` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                                         `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                                         `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                                         PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文章分类表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `sys_config`  (
                               `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
                               `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置键',
                               `config_value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '配置值',
                               `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
                               `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
                               `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                               `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
                               `create_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
                               `update_by` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人',
                               PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '全局系统配置表' ROW_FORMAT = DYNAMIC;


-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO `sys_config` (`id`, `config_key`, `config_value`, `remark`, `del_flag`, `create_time`, `create_by`)
VALUES ('1', 'callback_domain', 'http://127.0.0.1:8080/api', '通用回调地址，请按部署环境修改', '0', NOW(), 'system');

CREATE TABLE `sys_copyright` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `logo_url` varchar(255) DEFAULT NULL COMMENT 'Logo 地址',
  `name` varchar(30) DEFAULT NULL COMMENT '名称',
  `copyright_desc` varchar(100) DEFAULT NULL COMMENT '版权信息',
  `copyright_link` varchar(100) DEFAULT NULL COMMENT '版权链接',
  `icp` varchar(100) DEFAULT NULL COMMENT 'ICP备案号',
  `icp_url` varchar(100) DEFAULT NULL COMMENT 'ICP 网站链接',
  `page_title` varchar(60) DEFAULT NULL COMMENT '页面标题',
  `page_desc` varchar(128) DEFAULT NULL COMMENT '页面副标题',
  `page_pic` varchar(255) DEFAULT NULL COMMENT '页面图片',
  `client_type` char(2) NOT NULL COMMENT '客户端类型',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统版权表';

CREATE TABLE `sys_dept` (
  `id` varchar(32) NOT NULL,
  `parent_id` varchar(32) DEFAULT '0',
  `dept_name` varchar(50) NOT NULL,
  `leader` varchar(50) DEFAULT NULL,
  `leader_phone` varchar(50) DEFAULT NULL,
  `sort` int DEFAULT 0,
  `status` varchar(2) DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sys_dept_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

CREATE TABLE `sys_dict` (
  `id` varchar(32) NOT NULL,
  `type` varchar(50) NOT NULL,
  `description` varchar(100) DEFAULT NULL,
  `status` varchar(2) DEFAULT '0',
  `remarks` varchar(100) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典表';

CREATE TABLE `sys_dict_value` (
  `id` varchar(32) NOT NULL,
  `dict_id` varchar(32) NOT NULL,
  `dict_label` varchar(50) DEFAULT NULL,
  `dict_value` varchar(100) DEFAULT NULL,
  `dict_type` varchar(50) DEFAULT NULL,
  `status` varchar(2) DEFAULT '0',
  `sort` int DEFAULT 0,
  `remarks` varchar(100) DEFAULT NULL,
  `show_class` varchar(100) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sys_dict_value_type` (`dict_type`),
  KEY `idx_sys_dict_value_dict` (`dict_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典键值表';

CREATE TABLE `sys_log` (
  `id` varchar(32) NOT NULL,
  `ip_addr` varchar(255) DEFAULT NULL,
  `title` varchar(255) DEFAULT '',
  `request_method` varchar(255) DEFAULT NULL,
  `request_uri` varchar(255) DEFAULT NULL,
  `request_params` text,
  `request_time` bigint DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `method` varchar(255) DEFAULT NULL,
  `user_name` varchar(255) DEFAULT NULL,
  `status` char(2) DEFAULT NULL,
  `ex_msg` text,
  `create_time` datetime DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sys_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

CREATE TABLE `sys_login_log` (
  `id` varchar(32) NOT NULL,
  `ip_addr` varchar(255) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `user_name` varchar(255) DEFAULT NULL,
  `status` char(2) DEFAULT NULL,
  `msg` varchar(255) DEFAULT NULL,
  `browser` varchar(255) DEFAULT NULL,
  `os` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sys_login_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

CREATE TABLE `sys_material` (
  `id` varchar(32) NOT NULL,
  `name` varchar(200) NOT NULL,
  `type` char(2) NOT NULL,
  `group_id` varchar(32) DEFAULT '-1',
  `url` varchar(500) DEFAULT '',
  `shop_id` varchar(32) DEFAULT NULL COMMENT '业务主体ID',
  `file_size` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(2) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sys_material_group` (`group_id`),
  KEY `idx_sys_material_shop` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='素材表';

CREATE TABLE `sys_material_group` (
  `id` varchar(32) NOT NULL,
  `name` varchar(50) NOT NULL,
  `type` char(2) NOT NULL DEFAULT '1',
  `sort` int NOT NULL DEFAULT 0,
  `shop_id` varchar(32) DEFAULT NULL COMMENT '业务主体ID',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(2) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sys_material_group_shop` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='素材分组';

CREATE TABLE `sys_menu` (
  `id` varchar(32) NOT NULL,
  `name` varchar(60) NOT NULL,
  `permission` varchar(100) DEFAULT NULL,
  `path` varchar(100) DEFAULT NULL,
  `redirect` varchar(100) DEFAULT NULL,
  `parent_id` varchar(32) DEFAULT '0',
  `icon` varchar(64) DEFAULT NULL,
  `component` varchar(500) DEFAULT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `type` char(1) DEFAULT '0' COMMENT '0菜单 1按钮',
  `outer_status` char(2) DEFAULT '0',
  `application_key` varchar(32) DEFAULT 'app_base',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(2) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sys_menu_parent` (`parent_id`),
  KEY `idx_sys_menu_permission` (`permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

CREATE TABLE `sys_region` (
  `id` varchar(32) NOT NULL,
  `code` varchar(32) NOT NULL,
  `name` varchar(100) NOT NULL,
  `parent_id` varchar(32) DEFAULT '0',
  `level` varchar(2) DEFAULT NULL,
  `sort` int DEFAULT 0,
  `status` varchar(2) DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_region_code` (`code`),
  KEY `idx_sys_region_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政区划';

CREATE TABLE `sys_role` (
  `id` varchar(32) NOT NULL,
  `role_name` varchar(50) NOT NULL,
  `role_code` varchar(50) NOT NULL,
  `role_desc` varchar(255) DEFAULT NULL,
  `shop_id` varchar(32) DEFAULT NULL COMMENT '业务主体ID',
  `type` char(1) DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE `sys_role_menu` (
  `id` varchar(32) NOT NULL,
  `role_id` varchar(32) NOT NULL,
  `menu_id` varchar(32) NOT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_menu` (`role_id`,`menu_id`),
  KEY `idx_sys_role_menu_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

CREATE TABLE `sys_sms_config` (
  `id` varchar(32) NOT NULL,
  `supplier` varchar(32) NOT NULL,
  `access_key_id` varchar(255) DEFAULT NULL,
  `access_key_secret` varchar(512) DEFAULT NULL,
  `signature` varchar(100) DEFAULT NULL,
  `template_id` varchar(100) DEFAULT NULL,
  `extra_params` text,
  `name` varchar(100) DEFAULT NULL,
  `sms_code` varchar(100) DEFAULT NULL,
  `status` varchar(2) DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sys_sms_config_code` (`sms_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信配置';

CREATE TABLE `sys_sms_send_record` (
  `id` varchar(32) NOT NULL,
  `phone` varchar(32) NOT NULL,
  `sms_code` varchar(100) DEFAULT NULL,
  `content` text,
  `send_status` varchar(2) DEFAULT '0',
  `send_time` datetime DEFAULT NULL,
  `error_msg` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sys_sms_record_phone` (`phone`),
  KEY `idx_sys_sms_record_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信发送记录';

CREATE TABLE `sys_social_account` (
  `id` varchar(32) NOT NULL,
  `type` varchar(32) NOT NULL,
  `app_id` varchar(100) DEFAULT NULL,
  `app_secret` varchar(512) DEFAULT NULL,
  `redirect_url` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_social_account_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方登录配置';

CREATE TABLE `sys_storage_config` (
  `id` varchar(32) NOT NULL,
  `type` varchar(20) NOT NULL,
  `access_key` varchar(255) DEFAULT NULL,
  `access_secret` varchar(512) DEFAULT NULL,
  `endpoint` varchar(255) DEFAULT NULL,
  `bucket` varchar(255) DEFAULT NULL,
  `dir` varchar(255) DEFAULT NULL,
  `domain` varchar(255) DEFAULT NULL,
  `style_access_enabled` char(1) DEFAULT '0',
  `status` varchar(2) DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件存储配置';

CREATE TABLE `sys_user` (
  `id` varchar(32) NOT NULL,
  `username` varchar(100) NOT NULL,
  `password` varchar(100) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `nickname` varchar(50) DEFAULT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `dept_id` varchar(32) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `status` varchar(2) DEFAULT '0',
  `type` char(1) DEFAULT '0',
  `shop_id` varchar(32) DEFAULT NULL COMMENT '业务主体ID',
  `wx_open_id` varchar(64) DEFAULT NULL,
  `wx_mini_open_id` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `create_by` varchar(60) DEFAULT NULL,
  `update_by` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_dept` (`dept_id`),
  KEY `idx_sys_user_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

CREATE TABLE `sys_user_role` (
  `id` varchar(32) NOT NULL,
  `user_id` varchar(32) NOT NULL,
  `role_id` varchar(32) NOT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role` (`user_id`,`role_id`),
  KEY `idx_sys_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 最小可运行种子数据。管理员初始密码为 123456，首次登录后必须修改。
INSERT INTO `sys_dept` (`id`,`parent_id`,`dept_name`,`sort`,`status`,`del_flag`,`create_time`,`create_by`)
VALUES ('1','0','Smart Converge X',1,'0','0',NOW(),'system');

INSERT INTO `sys_role` (`id`,`role_name`,`role_code`,`role_desc`,`type`,`del_flag`,`create_time`,`create_by`)
VALUES ('1','超级管理员','ROLE_ADMIN','系统内置超级管理员','0','0',NOW(),'system');

INSERT INTO `sys_user` (`id`,`username`,`password`,`nickname`,`dept_id`,`status`,`type`,`del_flag`,`create_time`,`create_by`)
VALUES ('1','system','$2a$10$9p1BH9TCqoxGG1BZe8.cZeUs7xytTbg6LUqjB5uSDbIYCeXDBYnR2','系统管理员','1','0','0','0',NOW(),'system');

INSERT INTO `sys_user_role` (`id`,`user_id`,`role_id`) VALUES ('1','1','1');

-- 后台基础路由。component 值与 scx-fronted/apps/web-ele/src/views 保持一致。
INSERT INTO `sys_menu` (`id`,`name`,`path`,`parent_id`,`icon`,`component`,`sort`,`type`,`application_key`,`create_time`,`del_flag`) VALUES
('menu_dashboard','工作台','/dashboard','0','carbon:dashboard','dashboard/analytics/index',100,'0','app_base',NOW(),'0'),
('menu_system','系统管理','/system','0','carbon:settings',NULL,90,'0','app_base',NOW(),'0'),
('menu_user','用户管理','/system/user','menu_system','carbon:user','system/user/index',90,'0','app_base',NOW(),'0'),
('menu_role','角色管理','/system/role','menu_system','carbon:user-role','system/role/index',80,'0','app_base',NOW(),'0'),
('menu_menu','菜单管理','/system/menu','menu_system','carbon:menu','system/menu/index',70,'0','app_base',NOW(),'0'),
('menu_dept','部门管理','/system/dept','menu_system','carbon:tree-view','system/dept/index',60,'0','app_base',NOW(),'0'),
('menu_dict','字典管理','/system/dict','menu_system','carbon:dictionary','system/dict/index',50,'0','app_base',NOW(),'0'),
('menu_storage','存储配置','/system/storage-config','menu_system','carbon:cloud-storage','system/storage-config/index',40,'0','app_base',NOW(),'0'),
('menu_log','操作日志','/system/log','menu_system','carbon:catalog','system/log/index',30,'0','app_base',NOW(),'0'),
('menu_login_log','登录日志','/system/login-log','menu_system','carbon:login','system/login-log/index',20,'0','app_base',NOW(),'0'),
('menu_pay','支付管理','/pay','0','carbon:currency-dollar',NULL,80,'0','app_base',NOW(),'0'),
('menu_pay_config','支付配置','/pay/config','menu_pay','carbon:settings','pay/config/index',40,'0','app_base',NOW(),'0'),
('menu_pay_trade','支付订单','/pay/trade-order','menu_pay','carbon:purchase','pay/trade-order/index',30,'0','app_base',NOW(),'0'),
('menu_pay_refund','退款订单','/pay/refund-order','menu_pay','carbon:refund','pay/refund-order/index',20,'0','app_base',NOW(),'0'),
('menu_pay_notify','回调记录','/pay/notify-record','menu_pay','carbon:webhook','pay/notify-record/index',10,'0','app_base',NOW(),'0'),
('menu_generator','代码生成','/generator','0','carbon:code','',70,'0','app_base',NOW(),'0'),
('menu_datasource','数据源管理','/generator/datasource','menu_generator','carbon:data-base','gen/datasource/index',20,'0','app_base',NOW(),'0'),
('menu_gen_table','数据表管理','/generator/table','menu_generator','carbon:table','gen/gen-table/index',10,'0','app_base',NOW(),'0');

-- 将代码中实际声明的权限码写入菜单表，保证初始化管理员可直接操作全部保留模块。
SET @permission_seq = 0;
INSERT INTO `sys_menu` (`id`,`name`,`permission`,`parent_id`,`sort`,`type`,`application_key`,`create_time`,`del_flag`)
SELECT CONCAT('permission_',LPAD((@permission_seq := @permission_seq + 1),3,'0')),
       permission_code, permission_code, parent_id, 0, '1', 'app_base', NOW(), '0'
FROM (
  SELECT 'gen:datasource:add' permission_code,'menu_generator' parent_id UNION ALL
  SELECT 'gen:datasource:del','menu_generator' UNION ALL SELECT 'gen:datasource:edit','menu_generator' UNION ALL SELECT 'gen:datasource:get','menu_generator' UNION ALL SELECT 'gen:datasource:page','menu_generator' UNION ALL SELECT 'gen:gen-table:page','menu_generator' UNION ALL
  SELECT 'pay:payconfig:add','menu_pay' UNION ALL SELECT 'pay:payconfig:del','menu_pay' UNION ALL SELECT 'pay:payconfig:edit','menu_pay' UNION ALL SELECT 'pay:payconfig:get','menu_pay' UNION ALL SELECT 'pay:payconfig:page','menu_pay' UNION ALL
  SELECT 'pay:paynotifyrecord:get','menu_pay' UNION ALL SELECT 'pay:paynotifyrecord:page','menu_pay' UNION ALL SELECT 'pay:payrefundorder:get','menu_pay' UNION ALL SELECT 'pay:payrefundorder:page','menu_pay' UNION ALL SELECT 'pay:paytradeorder:get','menu_pay' UNION ALL SELECT 'pay:paytradeorder:page','menu_pay' UNION ALL
  SELECT 'system:ad-launch:edit','menu_system' UNION ALL SELECT 'system:ad-launch:get','menu_system' UNION ALL
  SELECT 'system:agreement:add','menu_system' UNION ALL SELECT 'system:agreement:del','menu_system' UNION ALL SELECT 'system:agreement:edit','menu_system' UNION ALL SELECT 'system:agreement:get','menu_system' UNION ALL SELECT 'system:agreement:page','menu_system' UNION ALL
  SELECT 'system:article-category:add','menu_system' UNION ALL SELECT 'system:article-category:del','menu_system' UNION ALL SELECT 'system:article-category:edit','menu_system' UNION ALL SELECT 'system:article-category:get','menu_system' UNION ALL SELECT 'system:article-category:page','menu_system' UNION ALL
  SELECT 'system:article:add','menu_system' UNION ALL SELECT 'system:article:del','menu_system' UNION ALL SELECT 'system:article:edit','menu_system' UNION ALL SELECT 'system:article:get','menu_system' UNION ALL SELECT 'system:article:page','menu_system' UNION ALL
  SELECT 'system:config:add','menu_system' UNION ALL SELECT 'system:config:del','menu_system' UNION ALL SELECT 'system:config:edit','menu_system' UNION ALL SELECT 'system:config:get','menu_system' UNION ALL SELECT 'system:config:page','menu_system' UNION ALL
  SELECT 'system:copyright:add','menu_system' UNION ALL SELECT 'system:copyright:del','menu_system' UNION ALL SELECT 'system:copyright:edit','menu_system' UNION ALL SELECT 'system:copyright:get','menu_system' UNION ALL SELECT 'system:copyright:page','menu_system' UNION ALL
  SELECT 'system:material:add','menu_system' UNION ALL SELECT 'system:material:del','menu_system' UNION ALL SELECT 'system:material:edit','menu_system' UNION ALL SELECT 'system:material:page','menu_system' UNION ALL
  SELECT 'system:materialgroup:add','menu_system' UNION ALL SELECT 'system:materialgroup:del','menu_system' UNION ALL SELECT 'system:materialgroup:edit','menu_system' UNION ALL SELECT 'system:materialgroup:get','menu_system' UNION ALL SELECT 'system:materialgroup:page','menu_system' UNION ALL
  SELECT 'system:region:add','menu_system' UNION ALL SELECT 'system:region:del','menu_system' UNION ALL SELECT 'system:region:edit','menu_system' UNION ALL SELECT 'system:region:get','menu_system' UNION ALL SELECT 'system:region:page','menu_system' UNION ALL
  SELECT 'system:sms-config:add','menu_system' UNION ALL SELECT 'system:sms-config:del','menu_system' UNION ALL SELECT 'system:sms-config:edit','menu_system' UNION ALL SELECT 'system:sms-config:get','menu_system' UNION ALL SELECT 'system:sms-config:page','menu_system' UNION ALL
  SELECT 'system:sms-send-record:add','menu_system' UNION ALL SELECT 'system:sms-send-record:del','menu_system' UNION ALL SELECT 'system:sms-send-record:edit','menu_system' UNION ALL SELECT 'system:sms-send-record:get','menu_system' UNION ALL SELECT 'system:sms-send-record:page','menu_system' UNION ALL
  SELECT 'system:social-account:add','menu_system' UNION ALL SELECT 'system:social-account:del','menu_system' UNION ALL SELECT 'system:social-account:edit','menu_system' UNION ALL SELECT 'system:social-account:get','menu_system' UNION ALL SELECT 'system:social-account:page','menu_system' UNION ALL
  SELECT 'system:storageconfig:add','menu_system' UNION ALL SELECT 'system:storageconfig:del','menu_system' UNION ALL SELECT 'system:storageconfig:edit','menu_system' UNION ALL SELECT 'system:storageconfig:get','menu_system' UNION ALL SELECT 'system:storageconfig:page','menu_system' UNION ALL
  SELECT 'system:sysdept:add','menu_system' UNION ALL SELECT 'system:sysdept:del','menu_system' UNION ALL SELECT 'system:sysdept:edit','menu_system' UNION ALL SELECT 'system:sysdept:get','menu_system' UNION ALL SELECT 'system:sysdept:page','menu_system' UNION ALL
  SELECT 'system:sysdict:add','menu_system' UNION ALL SELECT 'system:sysdict:del','menu_system' UNION ALL SELECT 'system:sysdict:edit','menu_system' UNION ALL SELECT 'system:sysdict:get','menu_system' UNION ALL SELECT 'system:sysdict:page','menu_system' UNION ALL
  SELECT 'system:sysdictvalue:add','menu_system' UNION ALL SELECT 'system:sysdictvalue:del','menu_system' UNION ALL SELECT 'system:sysdictvalue:edit','menu_system' UNION ALL SELECT 'system:sysdictvalue:get','menu_system' UNION ALL SELECT 'system:sysdictvalue:page','menu_system' UNION ALL
  SELECT 'system:syslog:get','menu_system' UNION ALL SELECT 'system:syslog:page','menu_system' UNION ALL SELECT 'system:sysloginlog:page','menu_system' UNION ALL
  SELECT 'system:sysmenu:add','menu_system' UNION ALL SELECT 'system:sysmenu:del','menu_system' UNION ALL SELECT 'system:sysmenu:edit','menu_system' UNION ALL SELECT 'system:sysmenu:get','menu_system' UNION ALL SELECT 'system:sysmenu:page','menu_system' UNION ALL
  SELECT 'system:sysrole:add','menu_system' UNION ALL SELECT 'system:sysrole:del','menu_system' UNION ALL SELECT 'system:sysrole:edit','menu_system' UNION ALL SELECT 'system:sysrole:get','menu_system' UNION ALL SELECT 'system:sysrole:page','menu_system' UNION ALL SELECT 'system:sysrole:update','menu_system' UNION ALL
  SELECT 'system:sysserver:get','menu_system' UNION ALL
  SELECT 'system:sysuser:add','menu_system' UNION ALL SELECT 'system:sysuser:del','menu_system' UNION ALL SELECT 'system:sysuser:edit','menu_system' UNION ALL SELECT 'system:sysuser:get','menu_system' UNION ALL SELECT 'system:sysuser:page','menu_system' UNION ALL SELECT 'system:sysuser:password','menu_system'
) permission_list;

INSERT INTO `sys_role_menu` (`id`,`role_id`,`menu_id`)
SELECT CONCAT('role_menu_',LPAD((@role_menu_seq := COALESCE(@role_menu_seq,0) + 1),3,'0')), '1', `id`
FROM `sys_menu`;

INSERT INTO `sys_dict` (`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`create_by`) VALUES
('dict_status','status','通用状态','0','通用启停状态','0',NOW(),'system'),
('dict_menu_type','menu_type','菜单类型','0','菜单与按钮','0',NOW(),'system'),
('dict_pay_type','pay_type','支付类型','0','通用支付渠道','0',NOW(),'system'),
('dict_pay_status','pay_status','支付状态','0','支付订单状态','0',NOW(),'system'),
('dict_pay_terminal','pay_terminal_type','支付端类型','0','支付发起终端','0',NOW(),'system'),
('dict_pay_mode','pay_mode','支付模式','0','普通商户与服务商','0',NOW(),'system'),
('dict_sms_supplier','sms_supplier','短信厂商','0','短信服务商标识','0',NOW(),'system'),
('dict_sms_status','sms_send_status','短信发送状态','0','短信发送结果','0',NOW(),'system'),
('dict_storage_type','sys_storage_type','文件存储类型','0','文件存储配置类型','0',NOW(),'system');

INSERT INTO `sys_dict_value` (`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`sort`,`del_flag`,`create_time`,`create_by`) VALUES
('dv_status_0','dict_status','正常','0','status','0',1,'0',NOW(),'system'),('dv_status_1','dict_status','停用','1','status','0',2,'0',NOW(),'system'),
('dv_menu_0','dict_menu_type','菜单','0','menu_type','0',1,'0',NOW(),'system'),('dv_menu_1','dict_menu_type','按钮','1','menu_type','0',2,'0',NOW(),'system'),
('dv_pay_1','dict_pay_type','微信支付','1','pay_type','0',1,'0',NOW(),'system'),('dv_pay_2','dict_pay_type','支付宝支付','2','pay_type','0',2,'0',NOW(),'system'),
('dv_pay_status_0','dict_pay_status','未支付','0','pay_status','0',1,'0',NOW(),'system'),('dv_pay_status_1','dict_pay_status','已支付','1','pay_status','0',2,'0',NOW(),'system'),
('dv_terminal_0','dict_pay_terminal','小程序','0','pay_terminal_type','0',1,'0',NOW(),'system'),('dv_terminal_1','dict_pay_terminal','App','1','pay_terminal_type','0',2,'0',NOW(),'system'),('dv_terminal_2','dict_pay_terminal','H5','2','pay_terminal_type','0',3,'0',NOW(),'system'),('dv_terminal_3','dict_pay_terminal','PC','3','pay_terminal_type','0',4,'0',NOW(),'system'),('dv_terminal_4','dict_pay_terminal','公众号','4','pay_terminal_type','0',5,'0',NOW(),'system'),
('dv_mode_0','dict_pay_mode','普通商户','0','pay_mode','0',1,'0',NOW(),'system'),('dv_mode_1','dict_pay_mode','服务商','1','pay_mode','0',2,'0',NOW(),'system'),
('dv_sms_aliyun','dict_sms_supplier','阿里云','alibaba','sms_supplier','0',1,'0',NOW(),'system'),('dv_sms_tencent','dict_sms_supplier','腾讯云','tencent','sms_supplier','0',2,'0',NOW(),'system'),
('dv_sms_status_0','dict_sms_status','待发送','0','sms_send_status','0',1,'0',NOW(),'system'),('dv_sms_status_1','dict_sms_status','成功','1','sms_send_status','0',2,'0',NOW(),'system'),('dv_sms_status_2','dict_sms_status','失败','2','sms_send_status','0',3,'0',NOW(),'system'),
('dv_storage_local','dict_storage_type','本地存储','local','sys_storage_type','0',1,'0',NOW(),'system'),('dv_storage_minio','dict_storage_type','MinIO','minio','sys_storage_type','0',2,'0',NOW(),'system'),('dv_storage_oss','dict_storage_type','阿里云 OSS','aliyun','sys_storage_type','0',3,'0',NOW(),'system'),('dv_storage_cos','dict_storage_type','腾讯云 COS','tencent','sys_storage_type','0',4,'0',NOW(),'system');
