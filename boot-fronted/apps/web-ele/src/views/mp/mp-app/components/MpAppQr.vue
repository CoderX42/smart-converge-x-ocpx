<script setup lang="ts">
import { useQRCode } from '@vueuse/integrations/useQRCode';

const props = defineProps<{ url: string }>();
const qrcode = useQRCode(() => props.url, {
  errorCorrectionLevel: 'H',
  margin: 2,
});
</script>

<template>
  <img v-if="qrcode" :src="qrcode" class="qr-code" />
</template>
