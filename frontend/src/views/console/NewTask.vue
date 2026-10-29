<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import UiButton from '@/components/UiButton.vue'
import UiProgress from '@/components/UiProgress.vue'
import UiSegmented from '@/components/UiSegmented.vue'
import { api } from '@/api'

import { useToastStore } from '@/stores/toast'
import type { AppItem, StoreHit, StoreMarket, Task } from '@/types'
import { storefrontName } from '@/data/storefronts'
import { fmtSize } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const toast = useToastStore()

const source = ref<'STORE' | 'UPLOAD'>('STORE')
const markets = ref<StoreMarket[]>([])
const country = ref('')
const q = ref('')
const hits = ref<StoreHit[]>([])
const searching = ref(false)
const matchedAccounts = ref(0)
const target = ref<{ bundleId: string; name: string; version: string; priceCents: number; iconText?: string; iconHue?: number; iconUrl?: string } | null>(null)
const advanced = ref(false)
const versionId = ref('')
const platform = ref('iphone')
const submitting = ref(false)
const uploadPct = ref<number | null>(null)
const uploadPhase = ref<'upload' | 'verify'>('upload')
const uploadLoaded = ref(0)

const file = ref<File | null>(null)
const dragOver = ref(false)

let timer: number | undefined
function scheduleSearch() {
  window.clearTimeout(timer)
  if (!country.value) {
    hits.value = []
    matchedAccounts.value = 0
    searching.value = false
    return
  }
  timer = window.setTimeout(async () => {
    searching.value = !!q.value.trim()
    try {
      const res = await api.searchStore(q.value, country.value)
      hits.value = res.hits
      matchedAccounts.value = res.matchedAccounts
    } catch (e) {
      hits.value = []
      if (q.value.trim()) toast.error(e instanceof Error ? e.message : '搜索失败')
    } finally {
      searching.value = false
    }
  }, q.value.trim() ? 280 : 0)
}

watch([q, country], () => {
  target.value = null
  scheduleSearch()
})

async function pick(hit: StoreHit) {
  try {
    const app: AppItem = await api.getApp(hit.bundleId)
    target.value = {
      bundleId: app.bundleId,
      name: app.name,
      version: app.version,
      priceCents: app.priceDecryptCents,
      iconText: app.iconText,
      iconHue: app.iconHue,
      iconUrl: hit.iconUrl
    }
  } catch {
    target.value = {
      bundleId: hit.bundleId, name: hit.name, version: hit.version,
      priceCents: 600, iconText: hit.iconText, iconHue: hit.iconHue, iconUrl: hit.iconUrl
    }
  }
}

function onFile(f: File | null | undefined) {
  if (!f) return
  if (!f.name.toLowerCase().endsWith('.ipa')) {
    toast.error('请选择 .ipa 文件')
    return
  }
  if (f.size > 2 * 1024 * 1024 * 1024) {
    toast.error('IPA 不能超过 2 GB')
    return
  }
  if (f.size === 0) {
    toast.error('文件是空的，请重新选择')
    return
  }
  file.value = f
}

function onDrop(e: DragEvent) {
  dragOver.value = false
  onFile(e.dataTransfer?.files?.[0])
}

async function submit() {
  submitting.value = true
  try {
    let task: Task
    if (source.value === 'UPLOAD') {
      if (!file.value) return
      uploadPct.value = 0
      uploadLoaded.value = 0
      uploadPhase.value = 'upload'
      task = await api.uploadTask(file.value, (info) => {
        uploadLoaded.value = info.loaded
        uploadPhase.value = info.phase
        uploadPct.value = info.total ? Math.min(100, Math.round((info.loaded / info.total) * 100)) : 0
      })
    } else {
      if (!target.value) return
      task = await api.createTask({
        source: 'STORE',
        bundleId: target.value.bundleId,
        appName: target.value.name,
        version: versionId.value || target.value.version,
        priceCents: target.value.priceCents,
        storefront: country.value
      })
    }
    toast.success('任务已创建，进入队列')
    router.push(`/app/tasks/${task.id}`)
  } catch (e) {
    const message = e instanceof Error ? e.message : '创建任务失败'
    const partial = source.value === 'UPLOAD' && !!file.value && uploadLoaded.value > 0 && uploadLoaded.value < file.value.size
    toast.error(partial ? `${message}。再次提交会从断点继续` : message)

  } finally {
    submitting.value = false
    uploadPct.value = null
  }
}

const canSubmit = computed(() => source.value === 'UPLOAD' ? !!file.value : !!target.value)

onMounted(async () => {
  try {
    markets.value = await api.storeMarkets()
  } catch {
    markets.value = []
  }
  if (!markets.value.some((m) => m.code === country.value)) {
    country.value = markets.value[0]?.code || ''
  } else {
    scheduleSearch()
  }
  const bid = typeof route.query.bundleId === 'string' ? route.query.bundleId : ''
  if (bid) {
    try {
      const app = await api.getApp(bid)
      target.value = {
        bundleId: app.bundleId, name: app.name, version: app.version,
        priceCents: app.priceDecryptCents, iconText: app.iconText, iconHue: app.iconHue
      }
      q.value = app.name
    } catch { /* ignore */ }
  }
})
</script>

<template>
  <div class="new">
    <div class="new__head">
      <div>
        <h2 class="new__title">新建脱壳</h2>
        <p class="muted small" style="margin-top: 4px">选一个 App Store 应用，或上传你自己的加密包。</p>
      </div>
      <router-link to="/market" class="row gap-6 tiny">
        没找到？去市场看看 <AppIcon name="arrow-right" :size="13" />
      </router-link>
    </div>

    <div class="new__grid">
      <div class="stack gap-16">
        <div class="card card--pad">
          <UiSegmented
            v-model="source"
            :items="[
              { value: 'STORE', label: 'App Store 应用' },
              { value: 'UPLOAD', label: '上传 IPA' }
            ]"
          />

          <template v-if="source === 'STORE'">
            <p v-if="!markets.length" class="tiny dim" style="margin-top: 20px">
              还没有开放的市场。先在 Apple ID 池给账号标记市场。
            </p>
            <template v-else>
              <div class="store-search">
                <select v-model="country" class="select store-search__market" aria-label="App Store 市场">
                  <option v-for="m in markets" :key="m.code" :value="m.code">{{ m.name }}</option>
                </select>
                <div class="search-bar">
                  <span class="search-bar__icon"><AppIcon name="search" :size="17" /></span>
                  <input v-model="q" class="input" placeholder="搜索 App 名称或 bundle id" />
                </div>
              </div>
              <p class="tiny dim store-search__match">
                {{ storefrontName(country) }} · {{ matchedAccounts }} 个账号
              </p>
            </template>

            <div v-if="searching" class="hits">
              <div v-for="i in 3" :key="i" class="hit">
                <div class="skeleton" style="width: 38px; height: 38px; border-radius: 10px" />
                <div style="flex: 1">
                  <div class="skeleton" style="width: 40%; height: 12px" />
                  <div class="skeleton" style="width: 60%; height: 9px; margin-top: 7px" />
                </div>
              </div>
            </div>

            <div v-else-if="hits.length" class="hits">
              <button
                v-for="h in hits"
                :key="h.bundleId"
                class="hit"
                :class="{ 'is-selected': target?.bundleId === h.bundleId }"
                @click="pick(h)"
              >
                <img v-if="h.iconUrl" class="hit__icon" :src="h.iconUrl" alt="" />
                <div
                  v-else
                  class="app-icon"
                  :style="{
                    width: '38px', height: '38px', fontSize: '14px',
                    background: `linear-gradient(155deg, hsl(${h.iconHue} 82% 62%), hsl(${h.iconHue + 16} 76% 42%))`
                  }"
                >{{ h.iconText }}</div>
                <div class="hit__info">
                  <div class="hit__name">{{ h.name }}</div>
                  <div class="hit__meta">{{ h.developer }} · v{{ h.version }}</div>
                </div>
                <span v-if="h.inLibrary" class="pill pill--green">已拥有</span>
                <span v-if="target?.bundleId === h.bundleId" class="hit__check"><AppIcon name="check" :size="16" /></span>
              </button>
            </div>

            <p v-else-if="q && !searching" class="tiny dim" style="margin-top: 16px">
              没有找到「{{ q }}」。可以直接粘贴 bundle id 继续，或
              <router-link to="/market">从成品库下载</router-link>。
            </p>

            <div v-if="q && !hits.length && !searching && q.includes('.')" style="margin-top: 12px">
              <button
                class="btn btn--secondary btn--sm"
                @click="target = { bundleId: q.trim(), name: q.trim(), version: 'latest', priceCents: 600 }"
              >
                以 {{ q.trim() }} 创建任务
              </button>
            </div>
          </template>

          <template v-else>
            <label
              class="drop"
              :class="{ 'is-over': dragOver, 'has-file': !!file }"
              @dragover.prevent="dragOver = true"
              @dragleave.prevent="dragOver = false"
              @drop.prevent="onDrop"
            >
              <input type="file" accept=".ipa" hidden @change="onFile(($event.target as HTMLInputElement).files?.[0])" />
              <template v-if="!file">
                <span class="drop__icon"><AppIcon name="upload" :size="24" /></span>
                <strong>把 .ipa 拖到这里</strong>
                <span class="tiny dim">或点击选择文件 · 最大 2 GB</span>
              </template>
              <template v-else>
                <span class="drop__icon" style="background: var(--green-soft); color: var(--green)">
                  <AppIcon name="file" :size="22" />
                </span>
                <strong>{{ file.name }}</strong>
                <span class="tiny dim">{{ fmtSize(file.size) }} · 点击可重新选择</span>
              </template>
            </label>

            <div v-if="file" class="parsed">
              <div class="parsed__row"><span>文件名</span><code>{{ file.name }}</code></div>
              <div class="parsed__row"><span>大小</span><code>{{ fmtSize(file.size) }}</code></div>
              <div class="parsed__row"><span>校验</span><code class="ok">提交时读取 Info.plist 与加密段 ✓</code></div>
              <p class="tiny dim" style="margin-top: 10px">
                bundle id、版本和费用以服务端解析结果为准；如果包已脱壳或不是有效 IPA，会直接提示且不扣费。
              </p>
            </div>
          </template>
        </div>

        <div class="card">
          <button v-if="source === 'STORE'" class="adv" @click="advanced = !advanced">
            <span class="row gap-8"><AppIcon name="settings" :size="15" /> 高级选项</span>
            <AppIcon name="chevron-down" :size="16" :style="{ transform: advanced ? 'rotate(180deg)' : '' }" />
          </button>
          <div v-if="advanced" class="adv__body">
            <div class="adv__grid">
              <label class="field">
                <span class="field__label">version id（可选）</span>
                <input v-model="versionId" class="input" placeholder="例如 882193704" />
                <span class="field__hint">留空则下载最新版本</span>
              </label>
              <label class="field">
                <span class="field__label">设备平台</span>
                <select v-model="platform" class="select">
                  <option value="iphone">iPhone</option>
                  <option value="ipad">iPad</option>
                </select>
              </label>
            </div>
          </div>
        </div>
      </div>

      <aside class="summary card">
        <div class="summary__title">任务摘要</div>

        <div v-if="source === 'STORE' && target" class="sum-app">
          <div
            class="app-icon"
            :style="{
              width: '52px', height: '52px', fontSize: '19px',
              background: `linear-gradient(155deg, hsl(${target.iconHue ?? 210} 82% 62%), hsl(${(target.iconHue ?? 210) + 16} 76% 42%))`
            }"
          >{{ target.iconText || target.name.slice(0, 2) }}</div>
          <div>
            <div class="sum-app__name">{{ target.name }}</div>
            <div class="sum-app__meta">v{{ versionId || target.version }} · {{ storefrontName(country) }}</div>
          </div>
        </div>
        <div v-else-if="source === 'UPLOAD' && file" class="sum-app">
          <div
            class="app-icon"
            style="width: 52px; height: 52px; background: linear-gradient(155deg, hsl(210 82% 62%), hsl(226 76% 42%))"
          ><AppIcon name="file" :size="20" /></div>
          <div style="min-width: 0">
            <div class="sum-app__name" style="word-break: break-all">{{ file.name }}</div>
            <div class="sum-app__meta">{{ fmtSize(file.size) }} · 本地上传</div>
          </div>
        </div>
        <div v-else class="sum-empty">
          <AppIcon name="package" :size="22" />
          <span>{{ source === 'UPLOAD' ? '先选择一个 .ipa 文件' : '先选择一个应用' }}</span>
        </div>

        <div class="summary__rows">
          <div class="summary__row"><span>费用</span><strong>不扣费</strong></div>
          <div class="summary__row"><span>预计耗时</span><span>通常几分钟</span></div>
          <div class="summary__row"><span>下载</span><span>完成后可再下</span></div>
        </div>

        <div v-if="source === 'UPLOAD' && uploadPct != null" style="margin-top: 14px">
          <UiProgress
            :value="uploadPct"
            :striped="uploadPhase === 'upload'"
            :label="uploadPhase === 'verify' ? '正在校验 IPA' : `上传中 ${uploadPct}%`"
          />
          <p class="tiny dim" style="margin-top: 8px">
            {{ uploadPhase === 'verify' ? '文件已传完，正在读取加密段' : `${fmtSize(uploadLoaded)} / ${file ? fmtSize(file.size) : ''}` }}
            · 中断后可继续
          </p>
        </div>

        <UiButton
          variant="primary"
          size="lg"
          block
          :disabled="!canSubmit"
          :loading="submitting"
          style="margin-top: 16px"
          @click="submit()"
        >
          {{ uploadPhase === 'verify' && submitting ? '校验中' : '提交任务' }}
        </UiButton>

        <p class="tiny dim" style="margin-top: 12px; text-align: center">
          排队中和正在处理时都可以取消
        </p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.new__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.new__title {
  font-size: 22px;
  letter-spacing: -0.03em;
}
.new__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
  margin-top: 22px;
  align-items: start;
}
.store-search {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}
.store-search__market {
  width: 132px;
  flex: none;
  height: 40px;
  border-radius: var(--r-pill);
  background: var(--bg-subtle);
  border-color: transparent;
}
.store-search__match {
  margin-top: 8px;
}
.hit__icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  object-fit: cover;
  flex: none;
}
.hits {
  margin-top: 14px;
  border: 1px solid var(--line-soft);
  border-radius: var(--r-md);
  overflow: hidden;
}
.hit {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 11px 14px;
  text-align: left;
  border-bottom: 1px solid var(--line-soft);
  transition: background 0.15s;
}
.hit:last-child {
  border-bottom: 0;
}
.hit:hover {
  background: var(--bg-muted);
}
.hit.is-selected {
  background: var(--blue-soft);
}
.hit__info {
  flex: 1;
  min-width: 0;
}
.hit__name {
  font-size: 13.5px;
  font-weight: 500;
}
.hit__meta {
  font-size: 11.5px;
  color: var(--text-3);
  margin-top: 2px;
}
.hit__check {
  color: var(--blue);
}
.drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 20px;
  padding: 40px 20px;
  border: 1.5px dashed var(--line);
  border-radius: var(--r-md);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}
.drop:hover,
.drop.is-over {
  border-color: var(--blue);
  background: #f7fbff;
}
.drop.has-file {
  border-style: solid;
  border-color: var(--line-soft);
  background: #fff;
}
.drop__icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: var(--blue-soft);
  color: var(--blue);
  margin-bottom: 4px;
}
.drop strong {
  font-size: 14px;
  font-weight: 600;
}
.parsed {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: var(--r-sm);
  background: var(--bg-subtle);
}
.parsed__row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  font-size: 12px;
  padding: 4px 0;
  color: var(--text-2);
}
.parsed__row code {
  font-size: 11.5px;
  color: var(--text);
}
.parsed__row .ok {
  color: var(--green);
}
.adv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 16px 20px;
  font-size: 13.5px;
  color: var(--text-2);
}
.adv:hover {
  color: var(--text);
}
.adv__body {
  padding: 0 20px 20px;
}
.adv__grid {
  display: grid;
  grid-template-columns: 1fr 180px;
  gap: 16px;
}
.summary {
  padding: 22px;
  position: sticky;
  top: 88px;
}
.summary__title {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.01em;
}
.sum-app {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line-soft);
}
.sum-app__name {
  font-size: 14px;
  font-weight: 600;
}
.sum-app__meta {
  font-size: 11.5px;
  color: var(--text-3);
  margin-top: 2px;
}
.sum-empty {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding: 20px 14px;
  border-radius: var(--r-sm);
  background: var(--bg-subtle);
  color: var(--text-3);
  font-size: 12.5px;
}
.summary__rows {
  padding: 14px 0;
}
.summary__row {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  padding: 5px 0;
  color: var(--text-2);
}
.summary__row strong {
  color: var(--text);
  font-weight: 600;
}
.summary__after {
  display: flex;
  justify-content: space-between;
  padding: 12px 0 0;
  border-top: 1px solid var(--line-soft);
  font-size: 13px;
}
.summary__after strong {
  font-size: 15px;
}
@media (max-width: 1000px) {
  .new__grid { grid-template-columns: 1fr; }
  .summary { position: static; }
  .store-search { flex-direction: column; }
  .store-search__market { width: 100%; }
}
</style>
