<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import UiButton from '@/components/UiButton.vue'
import UiTabs from '@/components/UiTabs.vue'
import EmptyState from '@/components/EmptyState.vue'
import StatusPill from '@/components/StatusPill.vue'
import { api } from '@/api'
import { useToastStore } from '@/stores/toast'
import type { LibraryItem } from '@/types'
import { fmtDate, fmtSize } from '@/utils/format'

const toast = useToastStore()

const items = ref<LibraryItem[]>([])
const loading = ref(true)
const q = ref('')
const tab = ref('ALL')
const busy = ref('')

const tabs = computed(() => [
  { value: 'ALL', label: '全部', count: items.value.length },
  { value: 'DECRYPT', label: '自己脱壳', count: items.value.filter((i) => i.source === 'DECRYPT').length },
  { value: 'PURCHASE', label: '成品库', count: items.value.filter((i) => i.source === 'PURCHASE').length }
])

const filtered = computed(() => {
  const key = q.value.trim().toLowerCase()
  return items.value.filter((i) => {
    const matchTab = tab.value === 'ALL' || i.source === tab.value
    const matchKey = !key || i.name.toLowerCase().includes(key) || i.bundleId.toLowerCase().includes(key)
    return matchTab && matchKey
  })
})

function daysLeft(iso: string): number {
  const diff = new Date(iso).getTime() - Date.now()
  return Math.max(0, Math.ceil(diff / 86400000))
}

function freeText(iso: string): string {
  const d = daysLeft(iso)
  return d > 0 ? `剩 ${d} 天` : '已过期'
}

async function load() {
  loading.value = true
  try {
    items.value = await api.listLibrary()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function download(item: LibraryItem) {
  busy.value = item.id
  try {
    const res = await api.downloadApp(item.bundleId)
    await api.downloadLibraryArtifact(res.libraryId ? { ...item, id: res.libraryId } : item)
    toast.success(`${item.name} v${item.version} 已开始下载`)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '下载失败')
  } finally {
    busy.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="lib">
    <div class="page-head">
      <div>
        <h2 class="page-title">我的应用</h2>
        <p class="muted small" style="margin-top: 4px">
          自己提交的任务和从成品库拿过的包都在这里，可以再下载。
        </p>
      </div>
      <UiButton to="/market" variant="secondary"><AppIcon name="grid" :size="16" /> 去市场看看</UiButton>
    </div>

    <div class="lib__bar card">
      <div style="padding: 0 20px">
        <UiTabs v-model="tab" :items="tabs" />
      </div>
      <div class="lib__search">
        <div class="search-bar">
          <span class="search-bar__icon"><AppIcon name="search" :size="16" /></span>
          <input v-model="q" class="input" placeholder="搜索名称或 bundle id" />
        </div>
      </div>
    </div>

    <div v-if="loading" class="lib__grid">
      <div v-for="i in 4" :key="i" class="card lib-card">
        <div class="skeleton" style="width: 52px; height: 52px; border-radius: 14px" />
        <div style="flex: 1">
          <div class="skeleton" style="width: 46%; height: 13px" />
          <div class="skeleton" style="width: 70%; height: 10px; margin-top: 9px" />
        </div>
      </div>
    </div>

    <EmptyState
      v-else-if="!filtered.length"
      icon="box"
      :title="items.length ? '没有符合条件的应用' : '还没有任何成品'"
      :desc="
        items.length
          ? '换个关键词，或切换上面的筛选条件。'
          : '去成品库下载，或新建任务处理自己的包。'
      "
    >
      <div class="row gap-10">
        <router-link to="/market" class="btn btn--secondary btn--sm">看成品库</router-link>
        <router-link to="/app/new" class="btn btn--primary btn--sm">新建脱壳</router-link>
      </div>
    </EmptyState>

    <div v-else class="lib__grid">
      <div v-for="item in filtered" :key="item.id" class="card lib-card">
        <div class="lib-card__top">
          <div class="lib-icon">
            <AppIcon name="package" :size="22" />
          </div>
          <div class="lib-card__id">
            <div class="lib-card__name">{{ item.name }}</div>
            <div class="lib-card__bundle mono">{{ item.bundleId }}</div>
          </div>
          <span class="pill" :class="item.source === 'DECRYPT' ? 'pill--blue' : 'pill--purple'">
            {{ item.source === 'DECRYPT' ? '自己的任务' : '成品库' }}
          </span>
        </div>

        <div class="lib-card__meta">
          <span>v{{ item.version }}</span>
          <span class="dot">·</span>
          <span>{{ fmtDate(item.acquiredAt, false) }} 获得</span>
        </div>

        <div class="lib-card__free" :class="{ 'is-expired': daysLeft(item.freeUntil) === 0 }">
          <AppIcon name="clock" :size="14" />
          <span>可以再下载</span>
        </div>

        <div class="lib-card__foot">
          <router-link v-if="item.taskId" :to="`/app/tasks/${item.taskId}`" class="tiny dim row gap-4">
            查看任务 <AppIcon name="chevron-right" :size="12" />
          </router-link>
          <span v-else class="tiny dim">市场成品 · 无关联任务</span>
          <UiButton variant="primary" size="sm" :loading="busy === item.id" @click="download(item)">
            <AppIcon name="download" :size="14" /> 下载
          </UiButton>
        </div>
      </div>
    </div>

    <div class="lib__note">
      <AppIcon name="info" :size="15" />
      <span>
        记录会留着。产物过了保留期后需要重新提交任务。
      </span>
    </div>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.page-title {
  font-size: 22px;
  letter-spacing: -0.03em;
}
.lib__bar {
  margin-top: 20px;
  padding-bottom: 14px;
}
.lib__search {
  padding: 14px 22px 0;
  max-width: 420px;
}
.lib__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}
.lib-card {
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.lib-card__top {
  display: flex;
  align-items: center;
  gap: 12px;
}
.lib-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: linear-gradient(150deg, #eef5ff, #e6eeff);
  color: var(--blue);
  flex: none;
}
.lib-card__id {
  flex: 1;
  min-width: 0;
}
.lib-card__name {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.02em;
}
.lib-card__bundle {
  font-size: 11.5px;
  color: var(--text-3);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lib-card__meta {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--text-2);
}
.dot {
  opacity: 0.5;
}
.lib-card__free {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  padding: 5px 10px;
  border-radius: var(--r-pill);
  background: var(--green-soft);
  color: var(--green);
  font-size: 11.5px;
}
.lib-card__free.is-expired {
  background: var(--bg-subtle);
  color: var(--text-3);
}
.lib-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line-soft);
}
.lib__note {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 20px;
  font-size: 12px;
  color: var(--text-3);
}
@media (max-width: 880px) {
  .lib__grid {
    grid-template-columns: 1fr;
  }
}
</style>
