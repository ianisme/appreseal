<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import StatusPill from '@/components/StatusPill.vue'
import UiButton from '@/components/UiButton.vue'
import UiTable from '@/components/UiTable.vue'
import BarChart from '@/components/BarChart.vue'
import UiProgress from '@/components/UiProgress.vue'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { Task } from '@/types'
import { fmtDate } from '@/utils/format'

const auth = useAuthStore()
const tasks = ref<Task[]>([])
const loading = ref(true)

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const running = computed(() => tasks.value.filter((t) => ['QUEUED', 'DOWNLOADING', 'DECRYPTING', 'VERIFYING'].includes(t.status)).length)
const monthCount = computed(() => {
  const now = new Date()
  return tasks.value.filter((t) => {
    const d = new Date(t.createdAt)
    return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear()
  }).length
})

const series = computed(() => {
  const days: { label: string; value: number }[] = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date(Date.now() - i * 86400000)
    const key = d.toDateString()
    const value = tasks.value.filter((t) => new Date(t.createdAt).toDateString() === key).length
    days.push({ label: `${d.getMonth() + 1}/${d.getDate()}`, value })
  }
  return days
})

const recent = computed(() => tasks.value.slice(0, 5))

const columns = [
  { key: 'app', label: '应用' },
  { key: 'status', label: '状态', width: '110px' },
  { key: 'progress', label: '进度', width: '180px' },

  { key: 'createdAt', label: '创建时间', width: '150px' }
]

onMounted(async () => {
  try {
    const t = await api.listTasks(undefined, 1, 50)
    tasks.value = t.items
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="dash">
    <div class="dash__hello">
      <div>
        <h2 class="dash__title">{{ greeting }}，{{ auth.displayName }}</h2>
        <p class="muted small" style="margin-top: 4px">今天想解开哪个包？</p>
      </div>
      <UiButton to="/app/new" variant="primary">
        <AppIcon name="plus" :size="16" /> 新建脱壳
      </UiButton>
    </div>

    <div class="kpis">
      <div class="kpi card">
        <div class="kpi__label">使用方式</div>
        <div class="kpi__value" style="font-size: 22px">团队自用</div>
        <div class="kpi__hint">提交和下载都不扣费</div>
      </div>
      <div class="kpi card">
        <div class="kpi__label">进行中任务</div>
        <div class="kpi__value">{{ running }}</div>
        <router-link to="/app/tasks" class="kpi__link">查看任务 <AppIcon name="arrow-right" :size="13" /></router-link>
      </div>
      <div class="kpi card">
        <div class="kpi__label">本月脱壳</div>
        <div class="kpi__value">{{ monthCount }} <span>次</span></div>
        <div class="kpi__hint">本月已提交的任务</div>
      </div>
      <div class="kpi card kpi--accent">
        <div class="kpi__label">账户类型</div>
        <div class="kpi__value" style="font-size: 20px">{{ auth.isAdmin ? '管理员' : '标准账号' }}</div>
        <div class="kpi__hint">并发上限 2 个任务</div>
      </div>
    </div>

    <div class="dash__grid">
      <div class="card">
        <div class="card__head">
          <span class="card__title">最近任务</span>
          <router-link to="/app/tasks" class="tiny">全部任务 <AppIcon name="arrow-right" :size="12" /></router-link>
        </div>
        <UiTable :columns="columns" :rows="recent" :loading="loading" empty-title="还没有任务" empty-desc="选一个 App，几分钟后就能拿到明文包。">
          <template #cell-app="{ row }">
            <div class="row gap-10">
              <span class="task-icon"><AppIcon name="package" :size="16" /></span>
              <div class="stack">
                <span style="font-weight: 500">{{ row.appName }}</span>
                <span class="tiny dim">v{{ row.version }}</span>
              </div>
            </div>
          </template>
          <template #cell-status="{ row }"><StatusPill :status="row.status" /></template>
          <template #cell-progress="{ row }">
            <UiProgress :value="row.progress" :tone="row.status === 'FAILED' ? 'red' : row.status === 'SUCCEEDED' ? 'green' : 'blue'" />
          </template>

          <template #cell-createdAt="{ row }"><span class="tiny dim">{{ fmtDate(row.createdAt) }}</span></template>
          <template #empty-action>
            <router-link to="/app/new" class="btn btn--primary btn--sm" style="margin-top: 6px">新建脱壳</router-link>
          </template>
        </UiTable>
      </div>

      <div class="dash__side">
        <div class="card card--pad">
          <div class="spread">
            <span class="card__title">近 7 天任务</span>
            <span class="tiny dim">次数</span>
          </div>
          <div style="margin-top: 18px">
            <BarChart :data="series" :height="120" :format="(v: number) => `${v}`" />
          </div>
        </div>

        <div class="announce card">
          <span class="announce__icon"><AppIcon name="bell" :size="17" /></span>
          <div>
            <div class="announce__title">系统公告</div>
            <p>这是给自己和团队用的工具。任务不扣费，完成后可以再下载。</p>
          </div>
        </div>

        <div class="quick card">
          <router-link to="/market" class="quick__item">
            <span class="quick__icon"><AppIcon name="grid" :size="17" /></span>
            <div>
              <strong>成品库</strong>
              <span>下载已经整理好的包</span>
            </div>
            <AppIcon name="chevron-right" :size="15" />
          </router-link>
          <router-link to="/docs" class="quick__item">
            <span class="quick__icon"><AppIcon name="book" :size="17" /></span>
            <div>
              <strong>使用文档</strong>
              <span>上传、任务和常见错误</span>
            </div>
            <AppIcon name="chevron-right" :size="15" />
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dash__hello {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}
.dash__title {
  font-size: 24px;
  letter-spacing: -0.03em;
}
.kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.kpi {
  padding: 18px 20px;
}
.kpi--accent {
  background: linear-gradient(150deg, #f4f9ff, #fff);
  border-color: #dcebfb;
}
.kpi__label {
  font-size: 12.5px;
  color: var(--text-2);
}
.kpi__value {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.035em;
  margin: 6px 0 8px;
  font-variant-numeric: tabular-nums;
}
.kpi__value span {
  font-size: 13px;
  color: var(--text-3);
  font-weight: 500;
}
.kpi__link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}
.kpi__hint {
  font-size: 12px;
  color: var(--text-3);
}
.dash__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
  margin-top: 20px;
  align-items: start;
}
.dash__side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.task-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: var(--bg-subtle);
  color: var(--text-2);
  flex: none;
}
.announce {
  display: flex;
  gap: 12px;
  padding: 16px;
}
.announce__icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--orange-soft);
  color: var(--orange);
  flex: none;
}
.announce__title {
  font-size: 12.5px;
  font-weight: 600;
  margin-bottom: 3px;
}
.announce p {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-2);
}
.quick {
  padding: 6px;
}
.quick__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  color: var(--text);
}
.quick__item:hover {
  background: var(--bg-subtle);
  color: var(--text);
}
.quick__item > div {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.quick__item strong {
  font-size: 13px;
  font-weight: 500;
}
.quick__item span {
  font-size: 11.5px;
  color: var(--text-3);
}
.quick__icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: var(--blue-soft);
  color: var(--blue);
  flex: none;
}
.quick__item :deep(svg):last-child {
  color: var(--text-3);
}
@media (max-width: 1100px) {
  .kpis { grid-template-columns: repeat(2, 1fr); }
  .dash__grid { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 560px) {
  .dash__hello { flex-direction: column; align-items: flex-start; }
}
</style>
