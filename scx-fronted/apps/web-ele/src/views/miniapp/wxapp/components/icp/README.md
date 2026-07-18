# ICP备案组件拆分说明

## 概述

原来的 `IcpTab.vue` 文件过于庞大（1708行），包含了太多功能，不利于维护。现在已将其合理拆分为多个子组件，放在 `miniapp/wxapp/components/icp/` 目录下。

## 组件结构

```
icp/
├── index.ts              # 组件导出文件
├── types.ts              # 类型定义文件
├── FaceVerify.vue        # 人脸核身组件
├── SubjectInfo.vue       # 主体信息组件
├── PrincipalInfo.vue     # 负责人信息组件
├── AppletsInfo.vue       # 小程序信息组件
├── Materials.vue         # 材料上传组件
├── Upload.vue           # 通用上传组件
└── README.md            # 说明文档
```

## 组件说明

### 1. types.ts

- 定义所有ICP相关的TypeScript类型
- 包括表单数据、选项数据、组件Props等类型定义
- 提供统一的类型导出

### 2. FaceVerify.vue

- 处理第一步的人脸核身逻辑
- 显示人脸核身状态和操作按钮
- 发起人脸核身和状态检查
- 通过事件通知父组件核身成功

### 3. SubjectInfo.vue

- 处理主体基本信息
- 包括主体性质、主办单位名称、备案地区、通讯地址等
- 处理地区选择和主体性质变化逻辑

### 4. PrincipalInfo.vue

- 处理负责人信息（可复用用于主体负责人和小程序负责人）
- 通过 `isSubjectPrincipal` 属性区分是主体负责人还是小程序负责人
- 包含证件信息、联系方式等
- 主体负责人还包含法人信息相关逻辑

### 5. AppletsInfo.vue

- 处理小程序相关信息
- 包括小程序名称、ID、服务内容类型
- 处理前置审批项的增删改
- 包含小程序实际经营内容

### 6. Materials.vue

- 处理其他备案媒体材料
- 包含各种备案所需的材料上传功能
- 支持图片和视频上传
- 每个材料都有相应的上传限制和说明

### 7. Upload.vue

- 通用文件上传组件
- 支持图片和视频上传
- 支持单文件和多文件上传
- 包含文件格式和大小验证
- 自动处理上传成功和失败事件

### 8. index.ts

- 统一导出所有组件和类型
- 方便其他文件引用

## 主组件重构

重构后的 `IcpTab.vue`：

- 代码行数从1708行减少到约600行
- 主要负责步骤控制、数据管理和表单提交
- 通过组合子组件实现完整功能
- 保持原有的所有功能不变

## 优势

1. **可维护性**：每个组件职责单一，便于维护和调试
2. **可复用性**：子组件可以在其他地方复用
3. **可测试性**：每个组件可以独立测试
4. **代码清晰**：逻辑分离，代码结构更清晰
5. **团队协作**：不同开发者可以并行开发不同组件
6. **文件上传**：完整的文件上传功能，支持多种材料类型

## 使用方式

```vue
<template>
  <!-- 人脸核身 -->
  <FaceVerify
    :app-id="appId"
    :face-verify-task="faceVerifyTask"
    @face-verify-success="handleSuccess"
  />

  <!-- 主体信息 -->
  <SubjectInfo
    :form-data="formData"
    :subject-type-options="options"
    :is-disabled="disabled"
  />

  <!-- 其他组件... -->
</template>
```

## 注意事项

1. 所有子组件都通过props接收数据，通过事件通知父组件
2. 表单验证规则仍在主组件中定义
3. 数据流保持单向，便于调试和维护
4. 类型定义统一在types.ts中管理
