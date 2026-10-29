<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import UiButton from '@/components/UiButton.vue'
import UiPager from '@/components/UiPager.vue'
import UiTable from '@/components/UiTable.vue'
import { api } from '@/api'
import { useToastStore } from '@/stores/toast'
import type { AuditLog } from '@/types'
import { fmtDate, relTime } from '@/utils/format'

const toast = useToastStore()
const rows = ref<AuditLog[]>([])
const loading = ref(true)
const action = ref('ALL')
const q = ref('')
const page = ref(1)
const pages = ref(0)
const total = ref(0)
const totalAll = ref(0)
const todayCount = ref(0)
let searchTimer: number | undefined

const ACTIONS: Record<string, { label: string; tone: string }> = {
  PRICING_UPDATE: { label: '调整定价', tone: 'blue' },
  APP_SHELF: { label: '应用上下架', tone: 'purple' },
  BALANCE_ADJUST: { label: '调整余额', tone: 'orange' },
  TASK_RETRY: { label: '重试任务', tone: 'blue' },
  ORDER_REFUND: { label: '订单退款', tone: 'red' },
  USER_BAN: { label: '封禁用户', tone: 'red' },
  USER_UPDATE: { label: '修改用户', tone: 'orange' },
  APPLE_SYNC: { label: '同步 Apple ID', tone: 'purple' },
  APPLE_UPDATE: { label: 'Apple ID 操作', tone: 'purple' },
  SETTINGS_UPDATE: { label: '修改系统设置', tone: 'orange' },
  MAINTENANCE_CLEANUP: { label: '清理存储', tone: 'orange' },
  PASSWORD_CHANGE: { label: '修改密码', tone: 'blue' },
  PASSWORD_RESET: { label: '重置密码', tone: 'blue' }
}

const columns = [
  { key: 'createdAt', label: '时间', width: '160px' },
  { key: 'actor', label: '操作者', width: '110px' },
  { key: 'action', label: '动作', width: '140px' },
  { key: 'target', label: '对象', width: '190px' },
  { key: 'detail', label: '详情' },
  { key: 'ip', label: 'IP', width: '115px' }
]

const actionOptions = [
  { value: 'ALL', label: '全部动作' },
  ...Object.entries(ACTIONS).map(([value, meta]) => ({ value, label: meta.label }))
]

async function load() {
  loading.value = true
  try {
    const res = await api.adminLogs(action.value, q.value.trim(), page.value, 20)
    rows.value = res.items
    pages.value = res.pages
    total.value = res.total
    totalAll.value = res.totalAll
    todayCount.value = res.today
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

watch(action, () => {
  page.value = 1
  load()
})
watch(q, () => {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    page.value = 1
    load()
  }, 250)
})

onMounted(load)
onBeforeUnmount(() => window.clearTimeout(searchTimer))

async function exportCsv() {
  const all: AuditLog[] = []
  let current = 1
  while (current <= 20) {
    const res = await api.adminLogs(action.value, q.value.trim(), current, 100)
    all.push(...res.items)
    if (current >= res.pages) break
    current++
  }
  const header = ['时间', '操作者', '动作', '对象', '详情', 'IP']
  const lines = all.map((r) =>
    [fmtDate(r.createdAt), r.actor, r.action, r.target, r.detail, r.ip]
      .map((v) => `"${String(v).replace(/"/g, '""')}"`)
      .join(',')
  )
  const blob = new Blob(['\uFEFF' + [header.join(','), ...lines].join('\n')], {
    type: 'text/csv;charset=utf-8'
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `appreseal-audit-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  toast.success(`已导出 ${all.length} 条日志`)
}

function meta(a: string) {
  return ACTIONS[a] ?? { label: a, tone: 'dark' }
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">审计日志</h2>
        <p class="page-sub">管理员与系统的关键操作留痕，用于对账与追责。日志不可编辑。</p>
      </div>
      <div class="head-actions">
        <UiButton variant="secondary" @click="exportCsv"><AppIcon name="download" :size="15" /> 导出 CSV</UiButton>
        <UiButton variant="secondary" :loading="loading" @click="load()"><AppIcon name="refresh" :size="15" /> 刷新</UiButton>
      </div>
    </div>

    <div class="l-stats">
      <div class="card l-stat"><span>日志总数</span><strong>{{ totalAll }}</strong></div>
      <div class="card l-stat"><span>今日操作</span><strong>{{ todayCount }}</strong></div>
      <div class="card l-stat"><span>当前筛选</span><strong>{{ total }}</strong></div>
      <div class="card l-stat"><span>保留周期</span><strong style="font-size: 18px">180 天</strong></div>
    </div>

    <div class="card" style="margin-top: 16px">
      <div class="toolbar">
        <div class="toolbar__search">
          <span class="toolbar__icon"><AppIcon name="search" :size="16" /></span>
          <input v-model="q" class="input" placeholder="搜索操作者、对象、详情或 IP" />
        </div>
        <select v-model="action" class="select toolbar__select" aria-label="动作">
          <option v-for="o in actionOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
        <div class="toolbar__spacer" />
        <span class="toolbar__count">按时间倒序</span>
      </div>

      <UiTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty-title="没有匹配的日志"
        empty-desc="换个动作或关键词筛选。"
      >
        <template #cell-createdAt="{ row }">
          <div class="stack">
            <span class="tiny">{{ fmtDate(row.createdAt) }}</span>
            <span class="tiny dim">{{ relTime(row.createdAt) }}</span>
          </div>
        </template>
        <template #cell-actor="{ row }">
          <span class="actor">
            <span class="actor__dot" :style="{ background: row.actor === '系统' ? 'var(--text-3)' : 'var(--purple)' }" />
            {{ row.actor }}
          </span>
        </template>
        <template #cell-action="{ row }">
          <span class="pill" :class="`pill--${meta(row.action).tone}`">{{ meta(row.action).label }}</span>
        </template>
        <template #cell-target="{ row }"><span class="small">{{ row.target }}</span></template>
        <template #cell-detail="{ row }">
          <span class="small muted detail">{{ row.detail }}</span>
        </template>
        <template #cell-ip="{ row }"><span class="mono tiny dim">{{ row.ip }}</span></template>
      </UiTable>
      <UiPager :page="page" :pages="pages" :total="total" @update:page="(n) => { page = n; load() }" />
    </div>

    <div class="note-box" style="margin-top: 16px">
      <AppIcon name="shield" :size="15" />
      <span>
        审计日志仅追加、不提供删除接口；导出操作本身也会在接入后端后写入一条
        <code class="mono">LOG_EXPORT</code> 记录。
      </span>
    </div>
  </div>
</template>

<style scoped>
.l-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 18px;
}
.l-stat {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 15px 18px;
}
.l-stat span {
  font-size: 12.5px;
  color: var(--text-2);
}
.l-stat strong {
  font-size: 21px;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}
.actor {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
}
.actor__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.detail {
  display: block;
  max-width: 520px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 1000px) {
  .l-stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .detail {
    max-width: 240px;
  }
}
</style>
