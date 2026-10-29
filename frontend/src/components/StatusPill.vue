<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string; dot?: boolean }>()

const MAP: Record<string, { label: string; tone: string; live?: boolean }> = {
  QUEUED: { label: '排队中', tone: 'dark' },
  DOWNLOADING: { label: '下载中', tone: 'blue', live: true },
  DECRYPTING: { label: '解密中', tone: 'orange', live: true },
  VERIFYING: { label: '校验中', tone: 'purple', live: true },
  SUCCEEDED: { label: '已完成', tone: 'green' },
  FAILED: { label: '已失败', tone: 'red' },
  CANCELED: { label: '已取消', tone: 'dark' },
  PENDING: { label: '待支付', tone: 'orange' },
  PAID: { label: '已支付', tone: 'green' },
  REFUNDED: { label: '已退款', tone: 'dark' },
  ON_SALE: { label: '在售', tone: 'green' },
  OFF_SHELF: { label: '已下架', tone: 'dark' },
  DRAFT: { label: '草稿', tone: 'orange' },
  ACTIVE: { label: '正常', tone: 'green' },
  BANNED: { label: '已封禁', tone: 'red' },
  READY: { label: '可用', tone: 'green' },
  NEED_LOGIN: { label: '需登录', tone: 'orange' },
  DISABLED: { label: '禁用', tone: 'red' },
  ADMIN: { label: '管理员', tone: 'purple' },
  USER: { label: '用户', tone: 'dark' },
  RECHARGE: { label: '充值', tone: 'green' },
  DECRYPT: { label: '脱壳', tone: 'blue' },
  DOWNLOAD: { label: '下载', tone: 'purple' },
  REFUND: { label: '退款', tone: 'green' },
  ADJUST: { label: '调整', tone: 'orange' }
}

const meta = computed(() => MAP[props.status] ?? { label: props.status, tone: 'dark' })
</script>

<template>
  <span class="pill" :class="[`pill--${meta.tone}`, meta.live && 'pill--live']">
    <span v-if="dot !== false" class="pill__dot" />
    {{ meta.label }}
  </span>
</template>
