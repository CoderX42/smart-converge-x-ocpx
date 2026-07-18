-- Smart Converge X Core Tables
-- 系统、认证、权限、支付、代码生成及当前保留业务表
-- 所有 DDL 使用 IF NOT EXISTS 保证幂等可重复执行

USE `smart_converge_x`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- System Module (scx-system)
-- ============================================================

-- 系统用户
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`              VARCHAR(64)  NOT NULL COMMENT '主键',
  `username`        VARCHAR(100) NOT NULL COMMENT '用户名',
  `password`        VARCHAR(255) NOT NULL COMMENT '密码',
  `email`           VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `nickname`        VARCHAR(100) DEFAULT NULL COMMENT '用户昵称',
  `avatar`          VARCHAR(255) DEFAULT NULL COMMENT '头像',
  `dept_id`         VARCHAR(64)  DEFAULT NULL COMMENT '部门ID',
  `phone`           VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  `status`          CHAR(1)      DEFAULT '0' COMMENT '状态：0.正常；1.停用；',
  `type`            CHAR(1)      DEFAULT '0' COMMENT '账号类型：0.系统主账户；1.店铺账号',
  `shop_id`         VARCHAR(64)  DEFAULT NULL COMMENT '店铺ID',
  `wx_open_id`      VARCHAR(100) DEFAULT NULL COMMENT '微信openId',
  `wx_mini_open_id` VARCHAR(100) DEFAULT NULL COMMENT '微信小程序openId',
  `create_by`       VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`       VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time`     DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`     DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`        CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户';

-- 系统角色
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '主键',
  `role_name`   VARCHAR(100) NOT NULL COMMENT '角色名称',
  `role_code`   VARCHAR(100) NOT NULL COMMENT '角色编码',
  `role_desc`   VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
  `type`        CHAR(1)      DEFAULT NULL COMMENT '类型',
  `shop_id`     VARCHAR(64)  DEFAULT NULL COMMENT '店铺ID',
  `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`    CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统角色';

-- 系统菜单
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id`              VARCHAR(64)  NOT NULL COMMENT '主键',
  `name`            VARCHAR(100) NOT NULL COMMENT '菜单名称',
  `permission`      VARCHAR(200) DEFAULT NULL COMMENT '菜单权限',
  `path`            VARCHAR(255) DEFAULT NULL COMMENT 'URL',
  `redirect`        VARCHAR(255) DEFAULT NULL COMMENT '重定向url',
  `parent_id`       VARCHAR(64)  DEFAULT NULL COMMENT '父菜单ID',
  `icon`            VARCHAR(100) DEFAULT NULL COMMENT '图标',
  `component`       VARCHAR(255) DEFAULT NULL COMMENT '页面地址',
  `sort`            INT(11)      DEFAULT 0 COMMENT '排序',
  `type`            CHAR(1)      DEFAULT '0' COMMENT '类型: 0.菜单; 1.按钮;',
  `outer_status`    CHAR(1)      DEFAULT '0' COMMENT '外链状态：0.否；1.是；',
  `application_key` VARCHAR(100) DEFAULT NULL COMMENT '应用key',
  `create_by`       VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`       VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time`     DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`     DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`        CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统菜单';

-- 部门管理
CREATE TABLE IF NOT EXISTS `sys_dept` (
  `id`           VARCHAR(64)  NOT NULL COMMENT '主键',
  `parent_id`    VARCHAR(64)  DEFAULT NULL COMMENT '父级ID',
  `dept_name`    VARCHAR(100) NOT NULL COMMENT '部门名称',
  `leader`       VARCHAR(64)  DEFAULT NULL COMMENT '负责人',
  `leader_phone` VARCHAR(20)  DEFAULT NULL COMMENT '负责人联系电话',
  `sort`         INT(11)      DEFAULT 0 COMMENT '排序序号',
  `status`       CHAR(1)      DEFAULT '0' COMMENT '状态：0.正常；1.停用；',
  `create_by`    VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`    VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time`  DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`  DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`     CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门管理';

-- 全局系统配置表
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id`           VARCHAR(64)  NOT NULL COMMENT '主键',
  `config_key`   VARCHAR(200) DEFAULT NULL COMMENT '配置键',
  `config_value` TEXT         DEFAULT NULL COMMENT '配置值',
  `remark`       VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_by`    VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`    VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time`  DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`  DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`     CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='全局系统配置表';

-- 字典
CREATE TABLE IF NOT EXISTS `sys_dict` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '主键',
  `type`        VARCHAR(100) NOT NULL COMMENT '类型',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '描述',
  `status`      CHAR(1)      DEFAULT '0' COMMENT '状态：0.正常；1.停用；',
  `remarks`     VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`    CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典';

-- 字典键值
CREATE TABLE IF NOT EXISTS `sys_dict_value` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '主键',
  `dict_id`     VARCHAR(64)  NOT NULL COMMENT '字典主键',
  `dict_label`  VARCHAR(100) NOT NULL COMMENT '字典标签',
  `dict_value`  VARCHAR(100) NOT NULL COMMENT '字典键值',
  `dict_type`   VARCHAR(100) DEFAULT NULL COMMENT '字典类型',
  `status`      CHAR(1)      DEFAULT '0' COMMENT '状态：0.正常；1.停用；',
  `sort`        INT(11)      DEFAULT 0 COMMENT '排序',
  `remarks`     VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `show_class`  VARCHAR(100) DEFAULT NULL COMMENT '回显样式',
  `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`    CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_dict_id` (`dict_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典键值';

-- 操作日志
CREATE TABLE IF NOT EXISTS `sys_log` (
  `id`              VARCHAR(64)  NOT NULL COMMENT '主键',
  `ip_addr`         VARCHAR(50)  DEFAULT NULL COMMENT 'ip地址',
  `title`           VARCHAR(200) DEFAULT NULL COMMENT '请求标题',
  `request_method`  VARCHAR(10)  DEFAULT NULL COMMENT '请求方式',
  `request_uri`     VARCHAR(255) DEFAULT NULL COMMENT '请求URI',
  `request_params`  TEXT         DEFAULT NULL COMMENT '请求数据',
  `request_time`    BIGINT(20)   DEFAULT NULL COMMENT '请求时长',
  `location`        VARCHAR(255) DEFAULT NULL COMMENT '操作地点',
  `method`          VARCHAR(200) DEFAULT NULL COMMENT '操作方法',
  `user_name`       VARCHAR(100) DEFAULT NULL COMMENT '操作用户',
  `status`          CHAR(1)      DEFAULT '0' COMMENT '状态：0.失败；1.成功；',
  `ex_msg`          TEXT         DEFAULT NULL COMMENT '异常信息',
  `create_by`       VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `create_time`     DATETIME     DEFAULT NULL COMMENT '创建时间',
  `del_flag`        CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志';

-- 登录日志
CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '主键',
  `ip_addr`     VARCHAR(50)  DEFAULT NULL COMMENT 'ip地址',
  `location`    VARCHAR(255) DEFAULT NULL COMMENT '登录地点',
  `user_name`   VARCHAR(100) DEFAULT NULL COMMENT '登录用户',
  `status`      CHAR(1)      DEFAULT '0' COMMENT '状态：0.失败；1.成功；',
  `msg`         VARCHAR(255) DEFAULT NULL COMMENT '信息',
  `browser`     VARCHAR(100) DEFAULT NULL COMMENT '浏览器',
  `os`          VARCHAR(100) DEFAULT NULL COMMENT '操作系统',
  `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  `del_flag`    CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='登录日志';

-- 素材
CREATE TABLE IF NOT EXISTS `sys_material` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '主键',
  `name`        VARCHAR(200) NOT NULL COMMENT '素材名',
  `type`        CHAR(1)      DEFAULT NULL COMMENT '类型：1.图片；2.视频；',
  `group_id`    VARCHAR(64)  DEFAULT NULL COMMENT '分组ID',
  `url`         VARCHAR(500) DEFAULT NULL COMMENT '素材链接',
  `shop_id`     VARCHAR(64)  DEFAULT NULL COMMENT '店铺ID',
  `file_size`   BIGINT(20)   DEFAULT NULL COMMENT '素材大小',
  `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`    CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='素材';

-- 素材分组
CREATE TABLE IF NOT EXISTS `sys_material_group` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '主键',
  `name`        VARCHAR(100) NOT NULL COMMENT '分组名',
  `type`        CHAR(1)      DEFAULT NULL COMMENT '分组类型：1.图片；2.视频；',
  `sort`        INT(11)      DEFAULT 0 COMMENT '排序',
  `shop_id`     VARCHAR(64)  DEFAULT NULL COMMENT '店铺ID',
  `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`    CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='素材分组';

-- 行政区划
CREATE TABLE IF NOT EXISTS `sys_region` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '主键',
  `code`        VARCHAR(20)  NOT NULL COMMENT '行政区划代码',
  `name`        VARCHAR(100) NOT NULL COMMENT '区域名称',
  `parent_id`   VARCHAR(64)  DEFAULT NULL COMMENT '父级ID',
  `level`       CHAR(1)      DEFAULT NULL COMMENT '层级：1-省，2-市，3-区/县',
  `sort`        INT(11)      DEFAULT 0 COMMENT '排序值',
  `status`      CHAR(1)      DEFAULT '0' COMMENT '状态',
  `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`    CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_region_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='行政区划';

-- 角色关联菜单
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `id`          VARCHAR(64) NOT NULL COMMENT '主键',
  `role_id`     VARCHAR(64) NOT NULL COMMENT '角色ID',
  `menu_id`     VARCHAR(64) NOT NULL COMMENT '菜单ID',
  `create_time` DATETIME    DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色关联菜单';

-- 短信配置
CREATE TABLE IF NOT EXISTS `sys_sms_config` (
  `id`               VARCHAR(64)  NOT NULL COMMENT '主键',
  `supplier`         VARCHAR(50)  DEFAULT NULL COMMENT '厂商标识',
  `access_key_id`    VARCHAR(200) DEFAULT NULL COMMENT 'accessKeyId',
  `access_key_secret` VARCHAR(200) DEFAULT NULL COMMENT 'accessKeySecret',
  `signature`        VARCHAR(100) DEFAULT NULL COMMENT '短信签名',
  `template_id`      VARCHAR(100) DEFAULT NULL COMMENT '模板ID',
  `extra_params`     TEXT         DEFAULT NULL COMMENT '额外参数',
  `name`             VARCHAR(100) DEFAULT NULL COMMENT '短信名称',
  `sms_code`         VARCHAR(100) DEFAULT NULL COMMENT '短信编码',
  `status`           CHAR(1)      DEFAULT '0' COMMENT '状态：0.正常；1.停用；',
  `create_by`        VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`        VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time`      DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`      DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`         CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='短信配置';

-- 短信发送记录
CREATE TABLE IF NOT EXISTS `sys_sms_send_record` (
  `id`           VARCHAR(64)  NOT NULL COMMENT '主键',
  `phone`        VARCHAR(20)  DEFAULT NULL COMMENT '接收手机号',
  `sms_code`     VARCHAR(100) DEFAULT NULL COMMENT '业务用途标识',
  `content`      TEXT         DEFAULT NULL COMMENT '短信内容',
  `send_status`  CHAR(1)      DEFAULT '0' COMMENT '发送状态：0待发 1成功 2失败',
  `send_time`    DATETIME     DEFAULT NULL COMMENT '发送时间',
  `error_msg`    VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  `create_by`    VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`    VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time`  DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`  DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`     CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_phone` (`phone`),
  KEY `idx_send_time` (`send_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='短信发送记录';

-- 三方平台账号
CREATE TABLE IF NOT EXISTS `sys_social_account` (
  `id`            VARCHAR(64)  NOT NULL COMMENT '主键',
  `type`          VARCHAR(50)  DEFAULT NULL COMMENT '账号类型',
  `app_id`        VARCHAR(200) DEFAULT NULL COMMENT '账号',
  `app_secret`    VARCHAR(500) DEFAULT NULL COMMENT '密钥',
  `redirect_url`  VARCHAR(500) DEFAULT NULL COMMENT '回调地址',
  `create_by`     VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`     VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time`   DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`   DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`      CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='三方平台账号';

-- 系统文件存储配置
CREATE TABLE IF NOT EXISTS `sys_storage_config` (
  `id`                  VARCHAR(64)  NOT NULL COMMENT '主键',
  `type`                VARCHAR(50)  DEFAULT NULL COMMENT '存储类型：1.阿里OSS；2.七牛云；3.腾讯云',
  `access_key`          VARCHAR(200) DEFAULT NULL COMMENT 'access_key',
  `access_secret`       VARCHAR(200) DEFAULT NULL COMMENT 'access_secret',
  `endpoint`            VARCHAR(200) DEFAULT NULL COMMENT '地域节点',
  `bucket`              VARCHAR(200) DEFAULT NULL COMMENT '域名',
  `dir`                 VARCHAR(200) DEFAULT NULL COMMENT '指定文件夹',
  `status`              CHAR(1)      DEFAULT '0' COMMENT '状态：0.正常；1.禁用；',
  `domain`              VARCHAR(200) DEFAULT NULL COMMENT 'domain 自定义域名',
  `style_access_enabled` CHAR(1)     DEFAULT '0' COMMENT '是否 path-style',
  `create_by`           VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`           VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time`         DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`         DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`            CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统文件存储配置';

-- 系统用户关联角色
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id`          VARCHAR(64) NOT NULL COMMENT '主键',
  `user_id`     VARCHAR(64) NOT NULL COMMENT '用户ID',
  `role_id`     VARCHAR(64) NOT NULL COMMENT '角色ID',
  `create_time` DATETIME    DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户关联角色';

-- 文章
CREATE TABLE IF NOT EXISTS `sys_article` (
  `id`           VARCHAR(64)  NOT NULL COMMENT '主键',
  `title`        VARCHAR(200) NOT NULL COMMENT '标题',
  `content`      LONGTEXT     DEFAULT NULL COMMENT '文章内容',
  `author`       VARCHAR(100) DEFAULT NULL COMMENT '作者',
  `cover_url`    VARCHAR(500) DEFAULT NULL COMMENT '封面图',
  `sort`         INT(11)      DEFAULT 0 COMMENT '排序序号',
  `category_id`  VARCHAR(64)  DEFAULT NULL COMMENT '分类ID',
  `status`       CHAR(1)      DEFAULT '0' COMMENT '状态：0.正常；1.停用；',
  `create_by`    VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`    VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time`  DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`  DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`     CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文章';

-- 文章分类
CREATE TABLE IF NOT EXISTS `sys_article_category` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '主键',
  `name`        VARCHAR(100) NOT NULL COMMENT '分类名称',
  `sort`        INT(11)      DEFAULT 0 COMMENT '排序序号',
  `status`      CHAR(1)      DEFAULT '0' COMMENT '状态：0.正常；1.停用；',
  `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`    CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文章分类';

-- 系统协议
CREATE TABLE IF NOT EXISTS `sys_agreement` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '主键',
  `title`       VARCHAR(200) NOT NULL COMMENT '标题',
  `content`     LONGTEXT     DEFAULT NULL COMMENT '协议内容',
  `type`        CHAR(1)      DEFAULT NULL COMMENT '类型：1-服务协议；2-隐私协议；3-分销协议；',
  `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`    CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统协议';

-- 启动广告配置
CREATE TABLE IF NOT EXISTS `sys_ad_launch` (
  `id`                  VARCHAR(64)  NOT NULL COMMENT '主键',
  `ad_img_url`          VARCHAR(500) DEFAULT NULL COMMENT '广告图',
  `ad_to_url`           VARCHAR(500) DEFAULT NULL COMMENT '跳转地址',
  `countdown_seconds`   INT(11)      DEFAULT NULL COMMENT '启动广告倒计时（秒）',
  `countdown_position`  VARCHAR(50)  DEFAULT NULL COMMENT '倒计时按钮位置：top-left、top-right、bottom-left、bottom-right',
  `status`              CHAR(1)      DEFAULT '0' COMMENT '状态：0.正常；1.停用；',
  `create_by`           VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`           VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time`         DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`         DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`            CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='启动广告配置';

-- ============================================================
-- Payment Module (scx-pay)
-- ============================================================

-- 支付配置
CREATE TABLE IF NOT EXISTS `pay_config` (
  `id`               VARCHAR(64)  NOT NULL COMMENT '主键',
  `type`             CHAR(1)      NOT NULL COMMENT '支付类型：1.微信；2.支付宝；',
  `app_id`           VARCHAR(100) DEFAULT NULL COMMENT '应用ID',
  `mch_id`           VARCHAR(100) DEFAULT NULL COMMENT '商户号',
  `mch_key`          VARCHAR(500) DEFAULT NULL COMMENT '微信支付商户密钥',
  `key_path`         VARCHAR(500) DEFAULT NULL COMMENT 'apiclient_cert.p12文件的绝对路径',
  `cert_serial_no`   VARCHAR(200) DEFAULT NULL COMMENT '证书序列号',
  `apiv3_key`        VARCHAR(500) DEFAULT NULL COMMENT 'apiV3秘钥',
  `private_key_path` VARCHAR(500) DEFAULT NULL COMMENT 'apiv3 商户apiclient_key.pem',
  `private_cert_path` VARCHAR(500) DEFAULT NULL COMMENT 'apiv3 商户apiclient_cert.pem',
  `terminal_type`    CHAR(1)      DEFAULT NULL COMMENT '支付端类型：0-小程序，1-App，2-H5，3-PC，4-公众号',
  `mode`             CHAR(1)      DEFAULT '0' COMMENT '0=普通商户；1=服务商',
  `sub_mchid`        VARCHAR(100) DEFAULT NULL COMMENT '子商户号',
  `public_key_path`  VARCHAR(500) DEFAULT NULL COMMENT '微信支付公钥',
  `public_key_id`    VARCHAR(200) DEFAULT NULL COMMENT '微信支付公钥ID',
  `create_by`        VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`        VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time`      DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`      DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`         CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付配置';

-- 支付订单
CREATE TABLE IF NOT EXISTS `pay_trade_order` (
  `id`                    VARCHAR(64)   NOT NULL COMMENT '主键',
  `channel_mch_id`        VARCHAR(100)  DEFAULT NULL COMMENT '渠道号',
  `out_trade_no`          VARCHAR(100)  NOT NULL COMMENT '商户订单号',
  `description`           VARCHAR(255)  DEFAULT NULL COMMENT '订单描述',
  `trade_type`            VARCHAR(50)   DEFAULT NULL COMMENT '交易类型',
  `open_id`               VARCHAR(200)  DEFAULT NULL COMMENT '用户id（openid、支付宝buyerId）',
  `pay_status`            CHAR(1)       DEFAULT '0' COMMENT '支付状态：0.待支付；1.已支付；2.已取消',
  `channel_order_no`      VARCHAR(200)  DEFAULT NULL COMMENT '渠道订单号',
  `extra`                 TEXT          DEFAULT NULL COMMENT '额外参数',
  `err_code`              VARCHAR(100)  DEFAULT NULL COMMENT '渠道支付错误码',
  `err_msg`               VARCHAR(500)  DEFAULT NULL COMMENT '渠道支付错误描述',
  `pay_success_time`      DATETIME      DEFAULT NULL COMMENT '支付成功时间',
  `notify_url`            VARCHAR(500)  DEFAULT NULL COMMENT '通知地址',
  `amount`                DECIMAL(30,6) DEFAULT NULL COMMENT '金额',
  `return_url`            VARCHAR(500)  DEFAULT NULL COMMENT '同步跳转地址',
  `quit_url`              VARCHAR(500)  DEFAULT NULL COMMENT '用户付款中途退出返回商户网站的地址',
  `terminal_type`         CHAR(1)       DEFAULT NULL COMMENT '支付端类型：0-小程序，1-App，2-H5，3-PC，4-公众号',
  `user_id`               VARCHAR(64)   DEFAULT NULL COMMENT '用户Id',
  `pay_mode`              CHAR(1)       DEFAULT '0' COMMENT '支付模式：0=普通商户；1=服务商',
  `wx_sub_mchid`          VARCHAR(100)  DEFAULT NULL COMMENT '微信子商户号',
  `alipay_sub_mchid`      VARCHAR(100)  DEFAULT NULL COMMENT '支付宝子商户号',
  `profit_sharing_status` CHAR(1)       DEFAULT '0' COMMENT '分账状态：0=否，1=是',
  `wx_sub_appid`          VARCHAR(100)  DEFAULT NULL COMMENT '微信子商户号',
  `create_by`             VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `update_by`             VARCHAR(64)   DEFAULT NULL COMMENT '修改人',
  `create_time`           DATETIME      DEFAULT NULL COMMENT '创建时间',
  `update_time`           DATETIME      DEFAULT NULL COMMENT '修改时间',
  `del_flag`              CHAR(1)       DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_out_trade_no` (`out_trade_no`),
  KEY `idx_channel_order_no` (`channel_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付订单';

-- 退款订单
CREATE TABLE IF NOT EXISTS `pay_refund_order` (
  `id`                 VARCHAR(64)   NOT NULL COMMENT '主键',
  `channel_mch_id`     VARCHAR(100)  DEFAULT NULL COMMENT '渠道号',
  `channel_refund_no`  VARCHAR(200)  DEFAULT NULL COMMENT '渠道退款单号',
  `refund_status`      CHAR(1)       DEFAULT '0' COMMENT '退款状态：0.待退款；1.退款中；2.已退款；3.退款失败；4.待确认',
  `refund_trade_no`    VARCHAR(100)  NOT NULL COMMENT '商户退款单号',
  `extra`              TEXT          DEFAULT NULL COMMENT '额外参数',
  `err_code`           VARCHAR(100)  DEFAULT NULL COMMENT '渠道退款错误码',
  `err_msg`            VARCHAR(500)  DEFAULT NULL COMMENT '渠道退款错误描述',
  `notify_url`         VARCHAR(500)  DEFAULT NULL COMMENT '通知地址',
  `pay_amount`         DECIMAL(30,6) DEFAULT NULL COMMENT '支付金额',
  `refund_amount`      DECIMAL(30,6) DEFAULT NULL COMMENT '退款金额（元）',
  `out_trade_no`       VARCHAR(100)  DEFAULT NULL COMMENT '商户支付订单号',
  `refund_success_time` DATETIME     DEFAULT NULL COMMENT '退款成功时间',
  `user_id`            VARCHAR(64)   DEFAULT NULL COMMENT '用户ID',
  `sub_mchid`          VARCHAR(100)  DEFAULT NULL COMMENT '子商户号',
  `terminal_type`      CHAR(1)       DEFAULT NULL COMMENT '支付端类型(冗余)',
  `create_by`          VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
  `update_by`          VARCHAR(64)   DEFAULT NULL COMMENT '修改人',
  `create_time`        DATETIME      DEFAULT NULL COMMENT '创建时间',
  `update_time`        DATETIME      DEFAULT NULL COMMENT '修改时间',
  `del_flag`           CHAR(1)       DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_trade_no` (`refund_trade_no`),
  KEY `idx_out_trade_no` (`out_trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='退款订单';

-- 回调通知记录
CREATE TABLE IF NOT EXISTS `pay_notify_record` (
  `id`               VARCHAR(64)  NOT NULL COMMENT '主键',
  `out_trade_no`     VARCHAR(100) DEFAULT NULL COMMENT '商户订单号',
  `channel_order_no` VARCHAR(200) DEFAULT NULL COMMENT '渠道订单号',
  `request`          TEXT         DEFAULT NULL COMMENT '请求报文',
  `response`         TEXT         DEFAULT NULL COMMENT '响应报文',
  `type`             CHAR(1)      DEFAULT NULL COMMENT '回调类型：1.支付回调；2.退款回调',
  `create_by`        VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `create_time`      DATETIME     DEFAULT NULL COMMENT '创建时间',
  `del_flag`         CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`),
  KEY `idx_out_trade_no` (`out_trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='回调通知记录';

-- ============================================================
-- Code Generator Module (scx-generator)
-- ============================================================

-- 数据源
CREATE TABLE IF NOT EXISTS `gen_data_source` (
  `id`          VARCHAR(64)  NOT NULL COMMENT '主键',
  `name`        VARCHAR(100) NOT NULL COMMENT '名称',
  `url`         VARCHAR(500) DEFAULT NULL COMMENT 'jdbc url',
  `username`    VARCHAR(100) DEFAULT NULL COMMENT '用户名',
  `password`    VARCHAR(500) DEFAULT NULL COMMENT '密码',
  `db_name`     VARCHAR(100) DEFAULT NULL COMMENT '数据库名称',
  `port`        VARCHAR(10)  DEFAULT NULL COMMENT '端口',
  `host`        VARCHAR(200) DEFAULT NULL COMMENT '主机',
  `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`    CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成-数据源';

-- 代码生成表
CREATE TABLE IF NOT EXISTS `gen_table` (
  `table_id`        VARCHAR(64)  NOT NULL COMMENT '编号',
  `table_name`      VARCHAR(200) NOT NULL COMMENT '表名称',
  `table_comment`   VARCHAR(500) NOT NULL COMMENT '表描述',
  `sub_table_name`  VARCHAR(200) DEFAULT NULL COMMENT '关联父表的表名',
  `sub_table_fk_name` VARCHAR(200) DEFAULT NULL COMMENT '本表关联父表的外键名',
  `class_name`      VARCHAR(100) NOT NULL COMMENT '实体类名称(首字母大写)',
  `tpl_category`    VARCHAR(50)  DEFAULT 'crud' COMMENT '使用的模板（crud单表操作 tree树表操作 sub主子表操作）',
  `tpl_web_type`    VARCHAR(50)  DEFAULT NULL COMMENT '前端类型（element-ui模版 element-plus模版）',
  `package_name`    VARCHAR(200) NOT NULL COMMENT '生成包路径',
  `module_name`     VARCHAR(50)  NOT NULL COMMENT '生成模块名',
  `business_name`   VARCHAR(50)  NOT NULL COMMENT '生成业务名',
  `function_name`   VARCHAR(200) NOT NULL COMMENT '生成功能名',
  `gen_type`        CHAR(1)      DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
  `gen_path`        VARCHAR(500) DEFAULT NULL COMMENT '生成路径（不填默认项目路径）',
  `options`         TEXT         DEFAULT NULL COMMENT '其它生成选项',
  `tree_code`       VARCHAR(100) DEFAULT NULL COMMENT '树编码字段',
  `tree_parent_code` VARCHAR(100) DEFAULT NULL COMMENT '树父编码字段',
  `tree_name`       VARCHAR(100) DEFAULT NULL COMMENT '树名称字段',
  `parent_menu_id`  BIGINT(20)   DEFAULT NULL COMMENT '上级菜单ID字段',
  `parent_menu_name` VARCHAR(100) DEFAULT NULL COMMENT '上级菜单名称字段',
  `ds_name`         VARCHAR(100) DEFAULT NULL COMMENT '数据源名称',
  `create_by`       VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`       VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time`     DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`     DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`        CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成-表';

-- 代码生成业务字段
CREATE TABLE IF NOT EXISTS `gen_table_column` (
  `column_id`      VARCHAR(64)  NOT NULL COMMENT '主键',
  `table_id`       VARCHAR(64)  NOT NULL COMMENT '归属表ID',
  `column_name`    VARCHAR(200) NOT NULL COMMENT '列名称',
  `column_comment` VARCHAR(500) DEFAULT NULL COMMENT '列描述',
  `column_type`    VARCHAR(100) DEFAULT NULL COMMENT '列类型',
  `java_type`      VARCHAR(100) DEFAULT NULL COMMENT 'JAVA类型',
  `java_field`     VARCHAR(200) NOT NULL COMMENT 'JAVA字段名',
  `is_pk`          CHAR(1)      DEFAULT '0' COMMENT '是否主键（1是）',
  `is_increment`   CHAR(1)      DEFAULT '0' COMMENT '是否自增（1是）',
  `is_required`    CHAR(1)      DEFAULT '0' COMMENT '是否必填（1是）',
  `is_insert`      CHAR(1)      DEFAULT '1' COMMENT '是否为插入字段（1是）',
  `is_edit`        CHAR(1)      DEFAULT '1' COMMENT '是否编辑字段（1是）',
  `is_list`        CHAR(1)      DEFAULT '1' COMMENT '是否列表字段（1是）',
  `is_query`       CHAR(1)      DEFAULT '0' COMMENT '是否查询字段（1是）',
  `query_type`     VARCHAR(50)  DEFAULT 'EQ' COMMENT '查询方式（EQ等于、NE不等于、GT大于、LT小于、LIKE模糊、BETWEEN范围）',
  `html_type`      VARCHAR(50)  DEFAULT NULL COMMENT '显示类型（input文本框、textarea文本域、select下拉框、checkbox复选框、radio单选框、datetime日期控件、image图片上传控件、upload文件上传控件、editor富文本控件）',
  `dict_type`      VARCHAR(200) DEFAULT NULL COMMENT '字典类型',
  `sort`           INT(11)      DEFAULT 0 COMMENT '排序',
  `create_by`      VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `update_by`      VARCHAR(64)  DEFAULT NULL COMMENT '修改人',
  `create_time`    DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_time`    DATETIME     DEFAULT NULL COMMENT '修改时间',
  `del_flag`       CHAR(1)      DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
  PRIMARY KEY (`column_id`),
  KEY `idx_table_id` (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成-业务字段';

SET FOREIGN_KEY_CHECKS = 1;