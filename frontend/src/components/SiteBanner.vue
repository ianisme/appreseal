<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '@/api'

const text = ref('')
const maintenance = ref(false)

onMounted(async () => {
  try {
    const site = await api.site()
    maintenance.value = site.maintenance
    text.value = site.announcement || (site.maintenance ? '系统维护中，暂时无法提交新的脱壳任务。' : '')
  } catch {
    /* 公告加载失败不影响页面 */
  }
})
</script>

<template>
  <div v-if="text" class="site-banner" :class="{ 'is-maint': maintenance }" role="status">
    {{ text }}
  </div>
</template>

<style scoped>
.site-banner {
  margin: 0;
  padding: 10px 16px;
  background: #f5f5f7;
  color: #1d1d1f;
  font-size: 13px;
  line-height: 1.45;
  text-align: center;
}
.site-banner.is-maint {
  background: #fff4e5;
  color: #7a4b00;
}
</style>
