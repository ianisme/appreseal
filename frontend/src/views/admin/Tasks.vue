<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import StatusPill from '@/components/StatusPill.vue'
import UiButton from '@/components/UiButton.vue'
import UiProgress from '@/components/UiProgress.vue'
import UiTable from '@/components/UiTable.vue'
import UiPager from '@/components/UiPager.vue'
import UiTabs from '@/components/UiTabs.vue'
import { api } from '@/api'
import { useToastStore } from '@/stores/toast'
import type { Task } from '@/types'
import { fmtDate, money } from '@/utils/format'

type Row = Task & { email: string }

const toast = useToastStore()
const rows = ref<Row[]>([])
const loading = ref(true)
const tab = ref('ALL')
const q = ref('')
const page = ref(1)
const pages = ref(0)
const total = ref(0)
const counts = ref({ all: 0, running: 0, succeeded: 0, failed: 0, canceled: 0, revenue: 0 })
const busy = ref('')
let timer: number | undefined
let searchTimer: number | undefined

const RUNNING = ['QUEUED', 'DOWNLOADING', 'DECRYPTING', 'VERIFYING']
const isRunning = (t: Task) => RUNNING.includes(t.status)

const tabs = computed(() => [
  { value: 'ALL', label: '全部', count: counts.value.all },
  { value: 'RUNNING', label: '进行中', count: counts.value.running },
  { value: 'SUCCEEDED', label: '成功', count: counts.value.succeeded },
  { value: 'FAILED', label: '失败', count: counts.value.failed },
  { value: 'CANCELED', label: '已取消', count: counts.value.canceled }
])

const stats = computed(() => {
  const finished = counts.value.succeeded + counts.value.failed
  return {
    running: counts.value.running,
    rate: finished ? ((counts.value.succeeded / finished) * 100).toFixed(1) : '—',
    failed: counts.value.failed,
    canceled: counts.value.canceled,
    revenue: counts.value.revenue
  }
})

const failureGroups = computed(() => {
  const map = new Map<string, number>()
  for (const r of rows.value.filter((x) => x.status === 'FAILED')) {
    const msg = r.error ?? '未知错误'
    const key = msg.length > 34 ? msg.slice(0, 34) + '…' : msg
    map.set(key, (map.get(key) ?? 0) + 1)
  }
  return [...map.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5)
})

const columns = [
  { key: 'app', label: '任务 / 应用' },
  { key: 'email', label: '用户', width: '170px' },
  { key: 'status', label: '状态', width: '104px' },
  { key: 'progress', label: '进度', width: '150px' },

  { key: 'createdAt', label: '创建时间', width: '140px' },
  { key: 'actions', label: '', width: '170px', align: 'right' as const }
]

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = await api.adminTasks(tab.value, q.value.trim(), page.value, 20)
    rows.value = res.items
    pages.value = res.pages
    total.value = res.total
    counts.value = {
      all: res.running + res.succeeded + res.failed + res.canceled,
      running: res.running,
      succeeded: res.succeeded,
      failed: res.failed,
      canceled: res.canceled,
      revenue: res.revenueCents
    }
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

watch([tab], () => {
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

onMounted(() => {
  load()
  timer = window.setInterval(() => {
    if (rows.value.some(isRunning)) load(true)
  }, 2200)
})
onBeforeUnmount(() => {
  window.clearInterval(timer)
  window.clearTimeout(searchTimer)
})

async function retry(row: Row) {
  busy.value = row.id
  try {
    await api.adminRetryTask(row.id)
    toast.success('已重新排队（管理员补偿，不额外扣费）')
    await load(true)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '重试失败')
  } finally {
    busy.value = ''
  }
}

async function cancel(row: Row) {
  busy.value = row.id
  try {
    await api.cancelTask(row.id)
    toast.success('已取消')
    await load(true)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '取消失败')
  } finally {
    busy.value = ''
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">脱壳任务</h2>
        <p class="page-sub">全量任务视图，可重试失败任务、取消正在处理的任务并查看产物。</p>
      </div>
      <div class="head-actions">
        <UiButton variant="secondary" :loading="loading" @click="load()">
          <AppIcon name="refresh" :size="15" /> 刷新
        </UiButton>
      </div>
    </div>

    <div class="t-stats">
      <div class="card t-stat">
        <span class="t-stat__label">进行中</span>
        <strong>{{ stats.running }}</strong>
        <span class="t-stat__foot">含排队与执行</span>
      </div>
      <div class="card t-stat">
        <span class="t-stat__label">成功率</span>
        <strong :style="{ color: Number(stats.rate) >= 99 ? 'var(--green)' : 'var(--orange)' }">
          {{ stats.rate }}<small v-if="stats.rate !== '—'">%</small>
        </strong>
        <span class="t-stat__foot">已完成任务口径</span>
      </div>
      <div class="card t-stat">
        <span class="t-stat__label">失败待处理</span>
        <strong :style="{ color: stats.failed ? 'var(--red)' : '' }">{{ stats.failed }}</strong>
        <span class="t-stat__foot">建议 2 小时内重试</span>
      </div>
      <div class="card t-stat">
        <span class="t-stat__label">已取消</span>
        <strong>{{ stats.canceled }}</strong>
        <span class="t-stat__foot">不会产生退款</span>
      </div>
    </div>

    <div v-if="failureGroups.length" class="card fail-summary">
      <div class="fail-summary__title"><AppIcon name="alert" :size="15" /> 失败原因聚合</div>
      <div class="fail-chips">
        <span v-for="[msg, count] in failureGroups" :key="msg" class="fail-chip">
          <span class="fail-chip__count">{{ count }}</span>
          {{ msg }}
        </span>
      </div>
    </div>

    <div class="card" style="margin-top: 16px">
      <div class="task-toolbar">
        <div style="padding: 0 20px">
          <UiTabs v-model="tab" :items="tabs" />
        </div>
        <div class="toolbar" style="border-bottom: 0; padding-top: 10px">
          <div class="toolbar__search">
            <span class="toolbar__icon"><AppIcon name="search" :size="16" /></span>
            <input v-model="q" class="input" placeholder="搜索应用、用户、bundle id 或任务 ID" />
          </div>
          <div class="toolbar__spacer" />
          <span class="toolbar__count">共 {{ total }} 条</span>
        </div>
      </div>

      <UiTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty-title="没有匹配的任务"
        empty-desc="换个筛选条件试试，或等待用户下单。"
      >
        <template #cell-app="{ row }">
          <div class="row gap-10">
            <span class="row-icon"><AppIcon name="package" :size="15" /></span>
            <div class="stack">
              <span style="font-weight: 500">{{ row.appName }} <span class="tiny dim">v{{ row.version }}</span></span>
              <span class="tiny dim mono">{{ row.id }} · {{ row.bundleId }}</span>
            </div>
          </div>
        </template>
        <template #cell-email="{ row }"><span class="tiny dim">{{ row.email }}</span></template>
        <template #cell-status="{ row }"><StatusPill :status="row.status" /></template>
        <template #cell-progress="{ row }">
          <div v-if="row.status === 'FAILED'" class="tiny fail-text">{{ row.error?.slice(0, 22) }}…</div>
          <UiProgress
            v-else
            :value="row.progress"
            :label="row.stage"
            :striped="isRunning(row)"
            :tone="row.status === 'SUCCEEDED' ? 'green' : row.status === 'CANCELED' ? 'red' : 'blue'"
          />
        </template>

        <template #cell-createdAt="{ row }"><span class="tiny dim">{{ fmtDate(row.createdAt) }}</span></template>
        <template #cell-actions="{ row }">
          <div class="table__actions">
            <router-link :to="`/app/tasks/${row.id}`" class="btn btn--secondary btn--sm">查看</router-link>
            <UiButton
              v-if="row.status === 'FAILED' || row.status === 'CANCELED'"
              variant="ghost"
              size="sm"
              :loading="busy === row.id"
              @click="retry(row)"
            >
              重试
            </UiButton>
            <UiButton
              v-if="isRunning(row)"
              variant="ghost"
              size="sm"
              :loading="busy === row.id"
              @click="cancel(row)"
            >
              取消
            </UiButton>
          </div>
        </template>
      </UiTable>
      <UiPager :page="page" :pages="pages" :total="total" @update:page="(n) => { page = n; load() }" />
    </div>
  </div>
</template>

<style scoped>
.t-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 18px;
}
.t-stat {
  padding: 15px 18px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.t-stat__label {
  font-size: 12.5px;
  color: var(--text-2);
}
.t-stat strong {
  font-size: 24px;
  letter-spacing: -0.035em;
  font-variant-numeric: tabular-nums;
}
.t-stat strong small {
  font-size: 13px;
  color: var(--text-3);
}
.t-stat__foot {
  font-size: 11px;
  color: var(--text-3);
}
.fail-summary {
  margin-top: 14px;
  padding: 14px 18px;
}
.fail-summary__title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--red);
}
.fail-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.fail-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 11px;
  border-radius: var(--r-pill);
  background: var(--red-soft);
  color: #a3000f;
  font-size: 11.5px;
  max-width: 100%;
}
.fail-chip__count {
  display: inline-grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #fff;
  font-size: 10.5px;
  font-weight: 600;
}
.row-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--bg-subtle);
  color: var(--text-2);
  flex: none;
}
.fail-text {
  color: var(--red);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
@media (max-width: 1000px) {
  .t-stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
