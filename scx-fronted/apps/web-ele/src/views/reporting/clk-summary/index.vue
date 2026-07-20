<script lang="ts" setup>
import DimensionSummary from '../components/DimensionSummary.vue';

defineOptions({ name: 'AdClkSummary' });
</script>

<template>
  <DimensionSummary dimension="TASK" event-type="CLICK" />
</template>
