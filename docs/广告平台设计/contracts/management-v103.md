# SCX OCPX 管理 API V103 增量契约

V103 继承 `management-v102.md` 的四实体业务字段，仅增加广告主开户和登录后的数据范围；不恢复任何旧字段别名或废弃操作。

## 广告主

- `POST /ocpx/advertisers`：`name`、`bidUrl`、`username`、`password` 必填，`credentialConfigKey`、`macroParams` 可选。
- `PUT /ocpx/advertisers/{id}`：只接受 `name`、`bidUrl`、`credentialConfigKey`、`macroParams`，不能修改或提交用户名、密码。
- 列表、详情和创建/编辑响应在 V102 字段上增加 nullable `username`；既有未开户广告主返回 `null`。响应永不包含 `accountUserId`、原始密码或密码摘要。
- 广告主开户原子创建 `sys_user.type=2` 用户并绑定 `ROLE_ADVERTISER`；失败不得留下半成品广告主、用户或角色关系。

## 数据范围

`sys_user.type=2` 的有效广告主 ID 恒为当前会话 `shopId`，客户端提交的其他 `advertiserId` 不能扩大范围。该约束覆盖广告主、推广链接、监测任务、关联媒体 options、Dashboard、报表、CSV、流程日志和详情接口；`shopId` 缺失时拒绝访问。系统管理员保持 V102 全量范围。

## 密码与 URL

- `POST /system/user/password` 仅修改当前会话账号，body 为 `password/newPassword/checkPassword`，不接收目标用户 ID；管理员重置其他账号仍使用独立受权接口。
- 密码为 6–32 位，不含空白字符，并同时包含字母、数字和特殊字符。原始密码只能进入 BCrypt 校验/编码，不写日志或响应。
- 管理面 URL 仅做必填、长度和首尾空白处理，不限制 scheme、主机、内网或环回地址。这是显式产品兼容决策，保留服务端出站 SSRF 风险，不代表 Engine 的其他网络边界被取消。
