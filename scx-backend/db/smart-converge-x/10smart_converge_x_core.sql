-- Smart Converge X Core Tables
-- 系统、认证、权限、代码生成及当前保留业务表
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
  `type`            CHAR(1)      DEFAULT '0' COMMENT '账号类型：0.系统主账户；1.店铺账号；2.广告主账号',
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
  `request_method`  VARCHAR(255)  DEFAULT NULL COMMENT '请求方式',
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

-- ============================================================
-- 最小可运行种子数据（INSERT IGNORE 保证幂等）
-- 包含：系统配置、部门、管理员、角色、标准系统菜单权限、字典
-- ============================================================

INSERT IGNORE INTO `sys_config` (`id`, `config_key`, `config_value`, `remark`, `del_flag`, `create_time`, `create_by`)
VALUES ('1', 'callback_domain', 'http://127.0.0.1:8080/api', '通用回调地址，请按部署环境修改', '0', NOW(), 'system');

INSERT IGNORE INTO `sys_dept` (`id`,`parent_id`,`dept_name`,`sort`,`status`,`del_flag`,`create_time`,`create_by`)
VALUES ('1','0','Smart Converge X',1,'0','0',NOW(),'system');

INSERT IGNORE INTO `sys_role` (`id`,`role_name`,`role_code`,`role_desc`,`type`,`del_flag`,`create_time`,`create_by`)
VALUES ('1','超级管理员','ROLE_ADMIN','系统内置超级管理员','0','0',NOW(),'system');

INSERT IGNORE INTO `sys_user` (`id`,`username`,`password`,`nickname`,`dept_id`,`status`,`type`,`del_flag`,`create_time`,`create_by`)
VALUES ('1','system','$2a$10$9p1BH9TCqoxGG1BZe8.cZeUs7xytTbg6LUqjB5uSDbIYCeXDBYnR2','系统管理员','1','0','0','0',NOW(),'system');

INSERT IGNORE INTO `sys_user_role` (`id`,`user_id`,`role_id`) VALUES ('1','1','1');

-- 后台基础路由。component 值与 scx-fronted/apps/web-ele/src/views 保持一致。
INSERT IGNORE INTO `sys_menu` (`id`,`name`,`path`,`parent_id`,`icon`,`component`,`sort`,`type`,`application_key`,`create_time`,`del_flag`) VALUES
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

-- 将代码中实际声明的权限码写入菜单表
SET @permission_seq = 0;
SET @permission_seq = (SELECT COALESCE(MAX(CAST(SUBSTRING(id, 12) AS UNSIGNED)), 0) FROM sys_menu WHERE id LIKE 'permission_%');

INSERT IGNORE INTO `sys_menu` (`id`,`name`,`permission`,`parent_id`,`sort`,`type`,`application_key`,`create_time`,`del_flag`)
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
) permission_list
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = permission_code);

SET @role_menu_seq = (SELECT COALESCE(MAX(CAST(SUBSTRING(id, 11) AS UNSIGNED)), 0) FROM sys_role_menu WHERE id LIKE 'role_menu_%');

INSERT IGNORE INTO `sys_role_menu` (`id`,`role_id`,`menu_id`)
SELECT CONCAT('role_menu_',LPAD((@role_menu_seq := @role_menu_seq + 1),3,'0')), '1', `id`
FROM `sys_menu`
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_menu srm WHERE srm.role_id = '1' AND srm.menu_id = sys_menu.id
);

INSERT IGNORE INTO `sys_dict` (`id`,`type`,`description`,`status`,`remarks`,`del_flag`,`create_time`,`create_by`) VALUES
('dict_status','status','通用状态','0','通用启停状态','0',NOW(),'system'),
('dict_menu_type','menu_type','菜单类型','0','菜单与按钮','0',NOW(),'system'),
('dict_pay_type','pay_type','支付类型','0','通用支付渠道','0',NOW(),'system'),
('dict_pay_status','pay_status','支付状态','0','支付订单状态','0',NOW(),'system'),
('dict_pay_terminal','pay_terminal_type','支付端类型','0','支付发起终端','0',NOW(),'system'),
('dict_pay_mode','pay_mode','支付模式','0','普通商户与服务商','0',NOW(),'system'),
('dict_sms_supplier','sms_supplier','短信厂商','0','短信服务商标识','0',NOW(),'system'),
('dict_sms_status','sms_send_status','短信发送状态','0','短信发送结果','0',NOW(),'system'),
('dict_storage_type','sys_storage_type','文件存储类型','0','文件存储配置类型','0',NOW(),'system');

INSERT IGNORE INTO `sys_dict_value` (`id`,`dict_id`,`dict_label`,`dict_value`,`dict_type`,`status`,`sort`,`del_flag`,`create_time`,`create_by`) VALUES
('dv_status_0','dict_status','正常','0','status','0',1,'0',NOW(),'system'),('dv_status_1','dict_status','停用','1','status','0',2,'0',NOW(),'system'),
('dv_menu_0','dict_menu_type','菜单','0','menu_type','0',1,'0',NOW(),'system'),('dv_menu_1','dict_menu_type','按钮','1','menu_type','0',2,'0',NOW(),'system'),
('dv_pay_1','dict_pay_type','微信支付','1','pay_type','0',1,'0',NOW(),'system'),('dv_pay_2','dict_pay_type','支付宝支付','2','pay_type','0',2,'0',NOW(),'system'),
('dv_pay_status_0','dict_pay_status','未支付','0','pay_status','0',1,'0',NOW(),'system'),('dv_pay_status_1','dict_pay_status','已支付','1','pay_status','0',2,'0',NOW(),'system'),
('dv_terminal_0','dict_pay_terminal','小程序','0','pay_terminal_type','0',1,'0',NOW(),'system'),('dv_terminal_1','dict_pay_terminal','App','1','pay_terminal_type','0',2,'0',NOW(),'system'),('dv_terminal_2','dict_pay_terminal','H5','2','pay_terminal_type','0',3,'0',NOW(),'system'),('dv_terminal_3','dict_pay_terminal','PC','3','pay_terminal_type','0',4,'0',NOW(),'system'),('dv_terminal_4','dict_pay_terminal','公众号','4','pay_terminal_type','0',5,'0',NOW(),'system'),
('dv_mode_0','dict_pay_mode','普通商户','0','pay_mode','0',1,'0',NOW(),'system'),('dv_mode_1','dict_pay_mode','服务商','1','pay_mode','0',2,'0',NOW(),'system'),
('dv_sms_aliyun','dict_sms_supplier','阿里云','alibaba','sms_supplier','0',1,'0',NOW(),'system'),('dv_sms_tencent','dict_sms_supplier','腾讯云','tencent','sms_supplier','0',2,'0',NOW(),'system'),
('dv_sms_status_0','dict_sms_status','待发送','0','sms_send_status','0',1,'0',NOW(),'system'),('dv_sms_status_1','dict_sms_status','成功','1','sms_send_status','0',2,'0',NOW(),'system'),('dv_sms_status_2','dict_sms_status','失败','2','sms_send_status','0',3,'0',NOW(),'system'),
('dv_storage_local','dict_storage_type','本地存储','local','sys_storage_type','0',1,'0',NOW(),'system'),('dv_storage_minio','dict_storage_type','MinIO','minio','sys_storage_type','0',2,'0',NOW(),'system'),('dv_storage_oss','dict_storage_type','阿里云 OSS','aliyun','sys_storage_type','0',3,'0',NOW(),'system'),('dv_storage_cos','dict_storage_type','腾讯云 COS','tencent','sys_storage_type','0',4,'0',NOW(),'system');
