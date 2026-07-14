<script setup lang="ts">
import type { DrawerProps } from 'element-plus';

import { computed } from 'vue';

defineOptions({ name: 'UserInfoDetail' });

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    userId?: string | number;
    userInfo?: Record<string, any>;
    direction?: DrawerProps['direction'];
  }>(),
  {
    direction: 'rtl',
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});

const displayName = computed(() => props.userInfo?.nickname || props.userInfo?.username || '会员用户');
</script>

<template>
  <el-drawer v-model="visible" :direction="direction" size="520px" title="会员详情">
    <div class="detail-card">
      <el-avatar :size="72" :src="userInfo?.avatar" />
      <div>
        <h3>{{ displayName }}</h3>
        <p>ID：{{ userId || userInfo?.id || '-' }}</p>
      </div>
    </div>

    <el-descriptions :column="1" border>
      <el-descriptions-item label="手机号">{{ userInfo?.phone || '-' }}</el-descriptions-item>
      <el-descriptions-item label="会员等级">{{ userInfo?.levelName || '标准会员' }}</el-descriptions-item>
      <el-descriptions-item label="积分余额">{{ userInfo?.point || 0 }}</el-descriptions-item>
      <el-descriptions-item label="账户状态">{{ userInfo?.statusName || userInfo?.status || '-' }}</el-descriptions-item>
      <el-descriptions-item label="注册时间">{{ userInfo?.createTime || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-alert class="mt-4" show-icon type="info" title="纯净模板仅保留会员基础资料、等级、积分和通用订单扩展入口。" />
  </el-drawer>
</template>

<style scoped>
.detail-card {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding: 18px;
  border-radius: 16px;
  background: var(--el-fill-color-light);
}
.detail-card h3 {
  margin: 0 0 6px;
  font-size: 18px;
}
.detail-card p {
  margin: 0;
  color: var(--el-text-color-secondary);
}
</style>
