<script setup lang="ts">
defineOptions({ name: 'SelfOrder' });

const rows = [
  {
    no: 'ORD-20260703-0001',
    scene: '服务订阅',
    status: '待支付',
    amount: '299.00',
  },
  {
    no: 'ORD-20260702-0008',
    scene: '接口套餐',
    status: '已完成',
    amount: '1280.00',
  },
];
</script>

<template>
  <Page
    auto-content-height
    description="与具体业务无关的 C 端通用订单查询。"
    title="用户订单"
  >
    <el-card shadow="never">
      <el-table :data="rows" border>
        <el-table-column label="订单号" prop="no" min-width="180" />
        <el-table-column label="业务场景" prop="scene" min-width="120" />
        <el-table-column label="状态" prop="status" width="120" />
        <el-table-column label="金额" prop="amount" width="120" />
      </el-table>
    </el-card>
  </Page>
</template>
