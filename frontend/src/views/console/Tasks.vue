<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import StatusPill from '@/components/StatusPill.vue'
import UiButton from '@/components/UiButton.vue'
import UiTable from '@/components/UiTable.vue'
import UiTabs from '@/components/UiTabs.vue'
import UiPager from '@/components/UiPager.vue'
import UiProgress from '@/components/UiProgress.vue'
import { api } from '@/api'
import { useToastStore } from '@/stores/toast'

import type { Task } from '@/types'
import { fmtDate, money } from '@/utils/format'

const router = useRouter()
const toast = useToastStore()

const tab = ref('ALL')
const page = ref(1)
const pages = ref(0)
const total = ref(0)
const counts = ref({ all: 0, running: 0, succeeded: 0, failed: 0 })
const tasks = ref<Task[]>([])
const loading = ref(true)
const busy = ref('')
let timer: number | undefined

const RUNNING = ['QUEUED', 'DOWNLOADING', 'DECRYPTING', 'VERIFYING']
const isRunning = (t: Task) => RUNNING.includes(t.status)

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = await api.listTasks(tab.value === 'ALL' ? undefined : tab.value, page.value, 20)
    tasks.value = res.items
    pages.value = res.pages
    total.value = res.total
    counts.value = {
      all: res.running + res.succeeded + res.failed + res.canceled,
      running: res.running,
      succeeded: res.succeeded,
      failed: res.failed
    }
  } finally {
    loading.value = false
  }
}

const tabs = computed(() => [
  { value: 'ALL', label: '全部', count: counts.value.all },
  { value: 'RUNNING', label: '进行中', count: counts.value.running },
  { value: 'SUCCEEDED', label: '成功', count: counts.value.succeeded },
  { value: 'FAILED', label: '失败', count: counts.value.failed }
])

const columns = [
  { key: 'app', label: '应用' },
  { key: 'status', label: '状态', width: '112px' },
  { key: 'progress', label: '进度', width: '200px' },

  { key: 'createdAt', label: '创建时间', width: '152px' },
  { key: 'actions', label: '', width: '150px', align: 'right' as const }
]

watch(tab, () => {
  page.value = 1
  load()
})

onMounted(() => {
  load()
  timer = window.setInterval(() => {
    if (tasks.value.some(isRunning)) load(true)
  }, 1800)
})
onBeforeUnmount(() => window.clearInterval(timer))

async function retry(t: Task) {
  busy.value = t.id
  try {
    await api.retryTask(t.id)

    toast.success('已重新排队')
    router.push(`/app/tasks/${t.id}`)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '重试失败')
  } finally {
    busy.value = ''
  }
}

async function cancel(t: Task) {
  busy.value = t.id
  try {
    await api.cancelTask(t.id)

    toast.success('已取消')
    load(true)
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
        <h2 class="page-title">任务</h2>
        <p class="muted small" style="margin-top: 4px">所有脱壳任务的实时状态与产物。</p>
      </div>
      <UiButton to="/app/new" variant="primary"><AppIcon name="plus" :size="16" /> 新建脱壳</UiButton>
    </div>

    <div class="card" style="margin-top: 20px">
      <div style="padding: 0 22px">
        <UiTabs v-model="tab" :items="tabs" />
      </div>

      <UiTable
        :columns="columns"
        :rows="tasks"
        :loading="loading"
        empty-title="这里还没有任务"
        empty-desc="创建第一个脱壳任务，完成后产物会出现在「我的应用」。"
      >
        <template #cell-app="{ row }">
          <div class="row gap-10">
            <span class="task-icon"><AppIcon name="package" :size="16" /></span>
            <div class="stack">
              <span style="font-weight: 500">{{ row.appName }}</span>
              <span class="tiny dim mono">{{ row.bundleId }} · v{{ row.version }}</span>
            </div>
          </div>
        </template>
        <template #cell-status="{ row }"><StatusPill :status="row.status" /></template>
        <template #cell-progress="{ row }">
          <div v-if="row.status === 'FAILED'" class="tiny" style="color: var(--red)">
            {{ row.error?.slice(0, 18) }}…
          </div>
          <UiProgress
            v-else
            :value="row.progress"
            :label="row.status === 'SUCCEEDED' ? '完成' : row.stage"
            :striped="isRunning(row)"
            :tone="row.status === 'SUCCEEDED' ? 'green' : row.status === 'CANCELED' ? 'red' : 'blue'"
          />
        </template>

        <template #cell-createdAt="{ row }"><span class="tiny dim">{{ fmtDate(row.createdAt) }}</span></template>
        <template #cell-actions="{ row }">
          <div class="table__actions">
            <router-link :to="`/app/tasks/${row.id}`" class="btn btn--secondary btn--sm">查看</router-link>
            <UiButton
              v-if="row.status === 'FAILED'"
              variant="ghost"
              size="sm"
              :loading="busy === row.id"
              @click="retry(row)"
            >重试</UiButton>
            <UiButton
              v-if="isRunning(row)"
              variant="ghost"
              size="sm"
              :loading="busy === row.id"
              @click="cancel(row)"
            >取消</UiButton>
          </div>
        </template>
        <template #empty-action>
          <router-link to="/app/new" class="btn btn--primary btn--sm" style="margin-top: 6px">新建脱壳</router-link>
        </template>
      </UiTable>
      <UiPager :page="page" :pages="pages" :total="total" @update:page="(n) => { page = n; load() }" />
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
</style>
