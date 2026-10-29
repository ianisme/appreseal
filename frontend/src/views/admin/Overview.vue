<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import BarChart from '@/components/BarChart.vue'
import StatusPill from '@/components/StatusPill.vue'
import UiButton from '@/components/UiButton.vue'
import UiTable from '@/components/UiTable.vue'
import UiProgress from '@/components/UiProgress.vue'
import { api } from '@/api'
import { useToastStore } from '@/stores/toast'
import type { AdminOverview, RechargeOrder, Task } from '@/types'
import { fmtDate, money, relTime } from '@/utils/format'

const toast = useToastStore()

const data = ref<AdminOverview | null>(null)
const tasks = ref<(Task & { email: string })[]>([])
const failedTasks = ref<(Task & { email: string })[]>([])
const orders = ref<(RechargeOrder & { email: string })[]>([])
const loading = ref(true)
const refreshing = ref(false)

const series = computed(() =>
  (data.value?.series ?? []).map((s) => ({ label: s.label, value: s.value }))
)
const totalSeries = computed(() => series.value.reduce((s, d) => s + d.value, 0))
const recentTasks = computed(() => tasks.value)
const recentOrders = computed(() => orders.value.filter((o) => o.status === 'PAID').slice(0, 5))

const columns = [
  { key: 'app', label: '应用' },
  { key: 'email', label: '用户', width: '180px' },
  { key: 'status', label: '状态', width: '110px' },
  { key: 'progress', label: '进度', width: '160px' },
  { key: 'createdAt', label: '创建时间', width: '150px' }
]

async function load(silent = false) {
  if (!silent) loading.value = true
  refreshing.value = true
  try {
    const [o, t, failed] = await Promise.all([
      api.adminOverview(),
      api.adminTasks('ALL', '', 1, 6),
      api.adminTasks('FAILED', '', 1, 4)
    ])
    data.value = o
    tasks.value = t.items
    failedTasks.value = failed.items
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(() => load())

const kpis = computed(() => [
  {
    label: '今日任务',
    value: `${data.value?.tasksToday ?? 0} 次`,
    foot: '不扣费',
    icon: 'terminal'
  },
  {
    label: '今日脱壳',
    value: `${data.value?.tasksToday ?? 0} 次`,
    foot: '队列中 ' + (data.value?.queue.queued ?? 0) + ' 个',
    icon: 'terminal'
  },
  {
    label: '成功率（7 天）',
    value: `${data.value?.successRate ?? 0}%`,
    foot: '目标 ≥ 99%',
    up: (data.value?.successRate ?? 0) >= 99,
    icon: 'shield'
  },
  {
    label: '活跃用户',
    value: `${data.value?.activeUsers ?? 0}`,
    foot: '近 7 天登录过',
    icon: 'users'
  }
])

const queuePct = computed(() => {
  const q = data.value?.queue
  if (!q || !q.concurrency) return 0
  return Math.min(100, Math.round((q.running / q.concurrency) * 100))
})
</script>

<template>
  <div class="ov">
    <div class="page-head">
      <div>
        <h2 class="page-title">团队概览</h2>
        <p class="page-sub">队列、任务和异常。没有收入和充值。</p>
      </div>
      <div class="head-actions">
        <span class="tiny dim">最后更新：刚刚</span>
        <UiButton variant="secondary" :loading="refreshing" @click="load(true)">
          <AppIcon name="refresh" :size="15" /> 刷新
        </UiButton>
      </div>
    </div>

    <div class="kpi-grid" style="margin-top: 18px">
      <div v-for="k in kpis" :key="k.label" class="card kpi-card">
        <div class="kpi-card__top">
          <span class="kpi-card__label">{{ k.label }}</span>
          <span class="kpi-card__icon"><AppIcon :name="k.icon" :size="15" /></span>
        </div>
        <div class="kpi-card__value">
          <template v-if="loading"><span class="skeleton" style="display: inline-block; width: 110px; height: 24px" /></template>
          <template v-else>{{ k.value }}</template>
        </div>
        <div class="kpi-card__foot">
          <span :class="{ up: k.up, down: k.up === false && k.foot?.includes('%') }">{{ k.foot }}</span>
        </div>
      </div>
    </div>

    <div class="content-grid content-grid--2">
      <div class="card">
        <div class="card__head">
          <div>
            <span class="card__title">任务趋势</span>
            <div class="tiny dim" style="margin-top: 2px">最近 14 天 · 共 {{ totalSeries }} 次</div>
          </div>
          <span class="pill pill--green"><span class="pill__dot" /> 不扣费</span>
        </div>
        <div class="card__body">
          <BarChart :data="series" :height="180" :format="(v: number) => `${v}`" />
        </div>
      </div>

      <div class="stack gap-16">
        <div class="card">
          <div class="card__head card__head--tight">
            <span class="card__title">任务队列</span>
            <router-link to="/admin/tasks" class="tiny">全部任务 <AppIcon name="arrow-right" :size="12" /></router-link>
          </div>
          <div class="card__body" style="padding: 16px 18px">
            <div class="spread small">
              <span class="muted">并发占用</span>
              <span class="mono">{{ data?.queue.running ?? 0 }} / {{ data?.queue.concurrency ?? 0 }}</span>
            </div>
            <div class="bar-track" style="margin-top: 9px">
              <span :style="{ width: queuePct + '%' }" />
            </div>
            <div class="queue-stats">
              <div><strong>{{ data?.queue.running ?? 0 }}</strong><span>运行中</span></div>
              <div><strong>{{ data?.queue.queued ?? 0 }}</strong><span>排队中</span></div>
              <div><strong>{{ data?.queue.concurrency ?? 0 }}</strong><span>并发上限</span></div>
            </div>
            <router-link to="/admin/system" class="tiny" style="display: inline-block; margin-top: 10px">
              调整并发与系统参数 <AppIcon name="chevron-right" :size="12" />
            </router-link>
          </div>
        </div>

        <div class="card">
          <div class="card__head card__head--tight">
            <span class="card__title">任务状态分布</span>
            <span class="tiny dim">近 7 天</span>
          </div>
          <div class="card__body" style="padding: 14px 18px 18px">
            <div v-for="d in data?.distribution ?? []" :key="d.label" class="dist-row">
              <span class="dist-dot" :style="{
                background:
                  d.tone === 'green' ? 'var(--green)' :
                  d.tone === 'red' ? 'var(--red)' :
                  d.tone === 'blue' ? 'var(--blue)' : 'var(--text-3)'
              }" />
              <span class="grow small">{{ d.label }}</span>
              <span class="mono small">{{ d.value }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="content-grid content-grid--2">
      <div class="card">
        <div class="card__head">
          <span class="card__title">最近任务</span>
          <router-link to="/admin/tasks" class="tiny">查看全部 <AppIcon name="arrow-right" :size="12" /></router-link>
        </div>
        <UiTable :columns="columns" :rows="recentTasks" :loading="loading" empty-title="还没有任务">
          <template #cell-app="{ row }">
            <div class="row gap-9">
              <span class="mini-icon"><AppIcon name="package" :size="14" /></span>
              <div class="stack">
                <span style="font-weight: 500">{{ row.appName }}</span>
                <span class="tiny dim mono">v{{ row.version }}</span>
              </div>
            </div>
          </template>
          <template #cell-email="{ row }"><span class="tiny dim">{{ row.email }}</span></template>
          <template #cell-status="{ row }"><StatusPill :status="row.status" /></template>
          <template #cell-progress="{ row }">
            <UiProgress
              :value="row.progress"
              :tone="row.status === 'FAILED' ? 'red' : row.status === 'SUCCEEDED' ? 'green' : 'blue'"
            />
          </template>
          <template #cell-createdAt="{ row }"><span class="tiny dim">{{ fmtDate(row.createdAt) }}</span></template>
        </UiTable>
      </div>

      <div class="stack gap-16">
        <div class="card">
          <div class="card__head card__head--tight">
            <span class="card__title">需要关注</span>
            <span class="pill" :class="failedTasks.length ? 'pill--red' : 'pill--green'">
              {{ failedTasks.length ? `${failedTasks.length} 个失败` : '无异常' }}
            </span>
          </div>
          <div class="mini-list">
            <router-link
              v-for="t in failedTasks"
              :key="t.id"
              :to="`/app/tasks/${t.id}`"
              class="mini-list__row fail-row"
            >
              <span class="fail-icon"><AppIcon name="alert" :size="13" /></span>
              <div class="grow">
                <div class="ellipsis">{{ t.appName }} <span class="tiny dim">v{{ t.version }}</span></div>
                <div class="tiny dim ellipsis">{{ t.error ?? '解密失败' }}</div>
              </div>
              <AppIcon name="chevron-right" :size="14" class="dim" />
            </router-link>
            <div v-if="!failedTasks.length" class="mini-list__row tiny dim">
              最近没有失败任务，Apple ID 池状态良好。
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.queue-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line-soft);
}
.queue-stats div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.queue-stats strong {
  font-size: 19px;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}
.queue-stats span {
  font-size: 11px;
  color: var(--text-3);
}
.dist-row {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 6px 0;
}
.dist-row .grow {
  flex: 1;
}
.dist-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: none;
}
.mini-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--bg-subtle);
  color: var(--text-2);
  flex: none;
}
.pay-dot {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 10.5px;
  font-weight: 600;
  flex: none;
}
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fail-row {
  color: var(--text);
}
.fail-row:hover {
  background: var(--bg-muted);
  color: var(--text);
}
.fail-icon {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--red-soft);
  color: var(--red);
  flex: none;
}
</style>
