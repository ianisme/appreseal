<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import StatusPill from '@/components/StatusPill.vue'
import UiButton from '@/components/UiButton.vue'
import UiProgress from '@/components/UiProgress.vue'
import { api, apiMode } from '@/api'
import { useToastStore } from '@/stores/toast'

import type { Task } from '@/types'
import { fmtDate, fmtSize, money, shortSha } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const toast = useToastStore()


const task = ref<Task | null>(null)
const loading = ref(true)
const error = ref('')
const busy = ref(false)
const logBox = ref<HTMLElement | null>(null)
let timer: number | undefined

const RUNNING = ['QUEUED', 'DOWNLOADING', 'DECRYPTING', 'VERIFYING']
const running = computed(() => !!task.value && RUNNING.includes(task.value.status))

const STEPS = ['排队', '下载', '解密', '校验', '完成']
const stepIndex = computed(() => {
  const s = task.value?.status
  if (!s) return 0
  if (s === 'QUEUED') return 0
  if (s === 'DOWNLOADING') return 1
  if (s === 'DECRYPTING') return 2
  if (s === 'VERIFYING') return 3
  if (s === 'SUCCEEDED') return 4
  return 2
})

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    task.value = await api.getTask(String(route.params.id))
    error.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
    await nextTick()
    scrollLogs()
  }
}

function scrollLogs() {
  if (logBox.value) logBox.value.scrollTop = logBox.value.scrollHeight
}

watch(() => task.value?.status, (s) => {
  if (s && !RUNNING.includes(s)) {
    window.clearInterval(timer)

  }
})

onMounted(() => {
  load()
  timer = window.setInterval(() => {
    if (running.value) load(true)
  }, 900)
})
onBeforeUnmount(() => window.clearInterval(timer))

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    toast.success('已复制')
  } catch {
    toast.error('复制失败')
  }
}

async function retry() {
  if (!task.value) return
  busy.value = true
  try {
    await api.retryTask(task.value.id)

    toast.success('已重新排队')
    timer = window.setInterval(() => {
      if (running.value) load(true)
    }, 900)
    await load(true)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '重试失败')
  } finally {
    busy.value = false
  }
}

async function cancel() {
  if (!task.value) return
  busy.value = true
  try {
    await api.cancelTask(task.value.id)

    toast.success('已取消，费用已退回')
    await load(true)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '取消失败')
  } finally {
    busy.value = false
  }
}

async function downloadDemo() {
  if (!task.value) return
  try {
    await api.downloadTaskArtifact(task.value)
    toast.success(apiMode === 'real' ? '已开始下载' : '已下载产物清单（演示）')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '下载失败')
  }
}
</script>

<template>
  <div class="td">
    <router-link to="/app/tasks" class="td__back">
      <AppIcon name="arrow-left" :size="15" /> 返回任务列表
    </router-link>

    <div v-if="loading" class="card card--pad" style="margin-top: 14px">
      <div class="skeleton" style="width: 220px; height: 22px" />
      <div class="skeleton" style="width: 320px; height: 12px; margin-top: 12px" />
    </div>

    <div v-else-if="error" class="empty">
      <div class="empty__art"><AppIcon name="alert" :size="26" /></div>
      <div class="empty__title">{{ error }}</div>
      <router-link to="/app/tasks" class="btn btn--secondary btn--sm">返回任务列表</router-link>
    </div>

    <template v-else-if="task">
      <header class="td-head card">
        <div class="td-head__left">
          <div class="td-icon"><AppIcon name="package" :size="24" /></div>
          <div>
            <div class="row gap-10">
              <h2 class="td-head__name">{{ task.appName }}</h2>
              <StatusPill :status="task.status" />
            </div>
            <div class="td-head__meta mono">{{ task.bundleId }} · v{{ task.version }} · {{ task.source === 'STORE' ? 'App Store' : '上传' }}</div>
          </div>
        </div>
        <div class="row gap-8">
          <UiButton v-if="running" variant="secondary" size="sm" :loading="busy" @click="cancel">取消任务</UiButton>
          <UiButton v-if="task.status === 'FAILED'" variant="secondary" size="sm" :loading="busy" @click="retry">
            <AppIcon name="refresh" :size="14" /> 重试
          </UiButton>
          <UiButton v-if="task.status === 'SUCCEEDED' && !task.artifactPurged" variant="primary" size="sm" @click="downloadDemo">
            <AppIcon name="download" :size="15" /> 下载 IPA
          </UiButton>
          <span v-else-if="task.status === 'SUCCEEDED' && task.artifactPurged" class="pill pill--orange">产物已过期</span>
        </div>
      </header>

      <div class="td-grid">
        <div class="stack gap-16">
          <div class="card card--pad">
            <div class="spread" style="margin-bottom: 22px">
              <span class="card__title">任务进度</span>
              <span class="tiny dim">{{ task.stage }} · {{ task.progress }}%</span>
            </div>

            <div class="steps">
              <div
                v-for="(s, i) in STEPS"
                :key="s"
                class="step"
                :class="{
                  'is-done': i < stepIndex || (task.status === 'SUCCEEDED' && i <= stepIndex),
                  'is-current': i === stepIndex && task.status !== 'SUCCEEDED',
                  'is-failed': task.status === 'FAILED' && i === stepIndex
                }"
              >
                <div class="step__dot">
                  <AppIcon v-if="task.status === 'FAILED' && i === stepIndex" name="x" :size="13" />
                  <AppIcon v-else-if="i < stepIndex || task.status === 'SUCCEEDED'" name="check" :size="13" />
                  <span v-else>{{ i + 1 }}</span>
                </div>
                <span class="step__label">{{ s }}</span>
                <span v-if="i < STEPS.length - 1" class="step__line" />
              </div>
            </div>

            <div style="margin-top: 20px">
              <UiProgress
                :value="task.progress"
                :tone="task.status === 'FAILED' ? 'red' : task.status === 'SUCCEEDED' ? 'green' : 'blue'"
                :striped="running"
              />
            </div>
          </div>

          <div class="card logs-card">
            <div class="card__head">
              <span class="card__title row gap-8"><AppIcon name="terminal" :size="15" /> 运行日志</span>
              <button class="tiny dim" @click="copy(task.logs.map((l) => l.message).join('\n'))">复制</button>
            </div>
            <div ref="logBox" class="logs">
              <div v-for="(l, i) in task.logs" :key="i" class="log" :class="`log--${l.level.toLowerCase()}`">
                <span class="log__time">{{ new Date(l.at).toLocaleTimeString('zh-CN', { hour12: false }) }}</span>
                <span class="log__msg">{{ l.message }}</span>
              </div>
              <div v-if="running" class="log log--info">
                <span class="log__time">···</span>
                <span class="log__msg dim">等待下一步…</span>
              </div>
            </div>
          </div>
        </div>

        <aside class="stack gap-16">
          <div v-if="task.status === 'SUCCEEDED'" class="card result">
            <div class="result__head">
              <span class="result__icon"><AppIcon name="check" :size="20" /></span>
              <div>
                <div class="result__title">脱壳完成</div>
                <div class="tiny dim">cryptid 已归零，可下载</div>
              </div>
            </div>
            <div class="result__rows">
              <div><span>文件大小</span><strong>{{ fmtSize(task.sizeBytes ?? 0) }}</strong></div>
              <div><span>SHA256</span><code @click="copy(task.sha256 ?? '')">{{ shortSha(task.sha256) }}</code></div>
              <div><span>校验</span><span class="pill pill--green">cryptid=0</span></div>
              <div><span>产物</span><strong>{{ task.artifactPurged ? '已清理' : '可下载' }}</strong></div>
            </div>
            <UiButton v-if="!task.artifactPurged" variant="primary" block style="margin-top: 16px" @click="downloadDemo">
              <AppIcon name="download" :size="16" /> 下载 IPA
            </UiButton>
            <div v-else class="note-box" style="margin-top: 16px">
              <AppIcon name="info" :size="15" />
              <span>产物已超过保留期并自动清理。如需再次下载，请重新提交脱壳任务。</span>
            </div>
            <p v-if="!task.artifactPurged" class="tiny dim" style="margin-top: 10px; text-align: center">
              产物已通过 cryptid=0 校验，可以再下
            </p>
          </div>

          <div v-if="task.status === 'FAILED'" class="card result result--fail">
            <div class="result__head">
              <span class="result__icon result__icon--fail"><AppIcon name="alert" :size="20" /></span>
              <div>
                <div class="result__title">任务失败</div>
                <div class="tiny dim">没有扣费，可以直接重试</div>
              </div>
            </div>
            <p class="fail-msg">{{ task.error }}</p>
            <UiButton variant="secondary" block style="margin-top: 12px" @click="retry">重试一次</UiButton>
          </div>

          <div v-if="task.status === 'CANCELED'" class="card result">
            <div class="result__head">
              <span class="result__icon result__icon--fail"><AppIcon name="x" :size="20" /></span>
              <div>
                <div class="result__title">已取消</div>
                <div class="tiny dim">这次处理已停止</div>
              </div>
            </div>
          </div>

          <div class="card card--pad">
            <div class="card__title" style="margin-bottom: 12px">任务信息</div>
            <div class="meta-row"><span>任务 ID</span><code>{{ task.id }}</code></div>
            <div class="meta-row"><span>费用</span><strong>不扣费</strong></div>
            <div class="meta-row"><span>创建时间</span><span>{{ fmtDate(task.createdAt) }}</span></div>
            <div class="meta-row"><span>完成时间</span><span>{{ fmtDate(task.finishedAt) }}</span></div>
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.td__back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--text-2);
}
.td__back:hover {
  color: var(--text);
}
.td-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px;
  margin-top: 14px;
}
.td-head__left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.td-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: linear-gradient(150deg, #eef5ff, #e6eeff);
  color: var(--blue);
  flex: none;
}
.td-head__name {
  font-size: 19px;
  letter-spacing: -0.02em;
}
.td-head__meta {
  font-size: 11.5px;
  color: var(--text-3);
  margin-top: 3px;
}
.td-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 16px;
  margin-top: 16px;
  align-items: start;
}
.steps {
  display: flex;
  align-items: flex-start;
}
.step {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.step__dot {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 600;
  background: #f0f0f3;
  color: var(--text-3);
  border: 1px solid var(--line-soft);
  z-index: 1;
  transition: all 0.25s var(--ease);
}
.step__label {
  font-size: 11.5px;
  color: var(--text-3);
}
.step__line {
  position: absolute;
  top: 13px;
  left: calc(50% + 16px);
  right: calc(-50% + 16px);
  height: 1px;
  background: var(--line-soft);
}
.step.is-done .step__dot {
  background: var(--green-soft);
  border-color: transparent;
  color: var(--green);
}
.step.is-done .step__label {
  color: var(--text-2);
}
.step.is-current .step__dot {
  background: var(--blue);
  border-color: var(--blue);
  color: #fff;
  box-shadow: 0 0 0 4px rgba(0, 113, 227, 0.14);
}
.step.is-current .step__label {
  color: var(--text);
  font-weight: 500;
}
.step.is-failed .step__dot {
  background: var(--red);
  border-color: var(--red);
  color: #fff;
}
.logs-card {
  overflow: hidden;
}
.logs {
  background: #131315;
  padding: 14px 18px 18px;
  max-height: 320px;
  overflow-y: auto;
  font-family: var(--mono);
  font-size: 11.5px;
  line-height: 1.9;
}
.log {
  display: flex;
  gap: 14px;
}
.log__time {
  color: #5e5e63;
  flex: none;
}
.log__msg {
  color: #b8b8bf;
}
.log--ok .log__msg { color: #4ade80; }
.log--warn .log__msg { color: #fbbf24; }
.log--error .log__msg { color: #ff6961; }
.log--info .log__msg { color: #b8b8bf; }
.result {
  padding: 20px;
}
.result__head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.result__icon {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--green-soft);
  color: var(--green);
  flex: none;
}
.result__icon--fail {
  background: var(--red-soft);
  color: var(--red);
}
.result__title {
  font-size: 14px;
  font-weight: 600;
}
.result__rows {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line-soft);
}
.result__rows > div {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
  font-size: 12.5px;
  color: var(--text-2);
}
.result__rows code {
  font-size: 11px;
  cursor: pointer;
  color: var(--text);
}
.result__rows strong {
  color: var(--text);
}
.fail-msg {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: var(--red-soft);
  color: var(--red);
  font-size: 12px;
  line-height: 1.6;
}
.meta-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 0;
  font-size: 12.5px;
  color: var(--text-2);
}
.meta-row code {
  font-size: 11px;
  color: var(--text);
}
.meta-row strong {
  color: var(--text);
}
@media (max-width: 1000px) {
  .td-grid { grid-template-columns: 1fr; }
  .td-head { flex-direction: column; align-items: flex-start; }
}
</style>
