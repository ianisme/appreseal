<script setup lang="ts">
import { computed } from 'vue'
import type { AppItem } from '@/types'
import { fmtNumber, fmtSize } from '@/utils/format'

const props = defineProps<{ app: AppItem }>()

const iconStyle = computed(() => ({
  width: '46px',
  height: '46px',
  fontSize: '17px',
  background: `linear-gradient(155deg, hsl(${props.app.iconHue} 82% 62%), hsl(${props.app.iconHue + 16} 76% 42%))`
}))
</script>

<template>
  <router-link :to="`/market/${app.bundleId}`" class="app-card card card--hover">
    <div class="app-card__top">
      <div class="app-icon" :style="iconStyle">{{ app.iconText }}</div>
      <div class="app-card__id">
        <div class="app-card__name">{{ app.name }}</div>
        <div class="app-card__dev">{{ app.developer }}</div>
      </div>
      <span v-if="app.verified" class="pill pill--green">已校验</span>
    </div>

    <p class="app-card__desc">{{ app.description }}</p>

    <div class="app-card__meta">
      <span>v{{ app.version }}</span>
      <span class="dot">·</span>
      <span>{{ fmtSize(app.sizeBytes) }}</span>
      <span class="dot">·</span>
      <span>{{ app.category }}</span>
    </div>

    <div class="app-card__foot">
      <span class="app-card__price">团队可下</span>
      <span class="app-card__dl">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 4v11m0 0 4.2-4.2M12 15 7.8 10.8M4 19.5h16" />
        </svg>
        {{ fmtNumber(app.downloads) }}
      </span>
    </div>
  </router-link>
</template>

<style scoped>
.app-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
  color: var(--text);
}
.app-card:hover {
  color: var(--text);
}
.app-card__top {
  display: flex;
  align-items: center;
  gap: 12px;
}
.app-card__id {
  min-width: 0;
  flex: 1;
}
.app-card__name {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.02em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.app-card__dev {
  font-size: 12.5px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.app-card__desc {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
}
.app-card__meta {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--text-3);
}
.dot {
  opacity: 0.5;
}
.app-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 14px;
  border-top: 1px solid var(--line-soft);
}
.app-card__price {
  font-size: 15px;
  font-weight: 650;
  color: var(--blue);
  letter-spacing: -0.02em;
}
.app-card__dl {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  color: var(--text-3);
}
</style>
