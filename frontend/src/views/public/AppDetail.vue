<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppCard from '@/components/AppCard.vue'
import AppIcon from '@/components/AppIcon.vue'
import UiButton from '@/components/UiButton.vue'

import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import type { AppItem } from '@/types'
import { fmtDate, fmtNumber, fmtSize, shortSha } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToastStore()

const app = ref<AppItem | null>(null)
const related = ref<AppItem[]>([])
const loading = ref(true)
const error = ref('')

const downloading = ref(false)
const owned = ref(false)
const iconStyle = computed(() => {
  if (!app.value) return {}
  return {
    width: '84px',
    height: '84px',
    fontSize: '30px',
    background: `linear-gradient(155deg, hsl(${app.value.iconHue} 82% 62%), hsl(${app.value.iconHue + 16} 76% 42%))`
  }
})

onMounted(async () => {
  try {
    app.value = await api.getApp(String(route.params.bundleId))
    const all = await api.listApps({ category: app.value.category })
    related.value = all.filter((a) => a.bundleId !== app.value?.bundleId).slice(0, 3)
    if (auth.isLoggedIn) {
      const lib = await api.listLibrary()
      owned.value = lib.some((l) => l.bundleId === app.value?.bundleId)
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
})

async function copy(text: string, label = '已复制') {
  try {
    await navigator.clipboard.writeText(text)
    toast.success(label)
  } catch {
    toast.error('复制失败，请手动选择')
  }
}

async function download() {
  if (!app.value) return
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  downloading.value = true
  try {
    const res = await api.downloadApp(app.value.bundleId)
    owned.value = true
    if (res.libraryId) {
      await api.downloadLibraryArtifact({
        id: res.libraryId, name: app.value.name, version: app.value.version, bundleId: app.value.bundleId
      })
    }
    toast.success('已开始下载')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '下载失败')
  } finally {
    downloading.value = false
  }
}

function goDecrypt() {
  router.push({ path: '/app/new', query: { bundleId: app.value?.bundleId } })
}
</script>

<template>
  <div class="detail">
    <div class="container">
      <div v-if="loading" class="detail__loading">
        <div class="skeleton" style="width: 84px; height: 84px; border-radius: 20px" />
        <div style="flex: 1">
          <div class="skeleton" style="width: 220px; height: 24px" />
          <div class="skeleton" style="width: 160px; height: 12px; margin-top: 12px" />
        </div>
      </div>

      <div v-else-if="error" class="empty">
        <div class="empty__art"><AppIcon name="alert" :size="26" /></div>
        <div class="empty__title">{{ error }}</div>
        <router-link to="/market" class="btn btn--secondary btn--sm">返回市场</router-link>
      </div>

      <template v-else-if="app">
        <nav class="crumb">
          <router-link to="/market">应用市场</router-link>
          <AppIcon name="chevron-right" :size="13" />
          <span>{{ app.category }}</span>
          <AppIcon name="chevron-right" :size="13" />
          <span class="dim">{{ app.name }}</span>
        </nav>

        <div class="detail__grid">
          <div class="detail__main">
            <header class="app-head">
              <div class="app-icon" :style="iconStyle">{{ app.iconText }}</div>
              <div class="app-head__info">
                <h1 class="app-head__name">{{ app.name }}</h1>
                <div class="app-head__dev">{{ app.developer }}</div>
                <div class="app-head__pills">
                  <span class="pill pill--green"><span class="pill__dot" /> 已脱壳 · cryptid=0</span>
                  <span class="pill">v{{ app.version }}</span>
                  <span class="pill">{{ fmtSize(app.sizeBytes) }}</span>
                  <span class="pill">iOS {{ app.minIOS }}+</span>
                  <span class="pill pill--purple">{{ app.arch.join(' / ') }}</span>
                </div>
              </div>
            </header>

            <section class="block">
              <h2 class="block__title">简介</h2>
              <p class="block__text">{{ app.description }}</p>
            </section>

            <section class="block">
              <h2 class="block__title">截图</h2>
              <div class="shots">
                <div
                  v-for="i in 3"
                  :key="i"
                  class="shot"
                  :style="{
                    background: `linear-gradient(160deg, hsl(${app.iconHue} 90% 96%), hsl(${app.iconHue + 20} 80% 88%))`
                  }"
                >
                  <div class="shot__bar">{{ app.name }}</div>
                  <div class="shot__body">
                    <div class="shot__line" style="width: 70%" />
                    <div class="shot__line" style="width: 46%" />
                    <div class="shot__card" />
                    <div class="shot__line" style="width: 58%" />
                  </div>
                </div>
              </div>
            </section>

            <section class="block">
              <h2 class="block__title">技术信息</h2>
              <dl class="spec">
                <div class="spec__row">
                  <dt>bundle id</dt>
                  <dd class="mono">
                    {{ app.bundleId }}
                    <button class="icon-btn" style="width: 24px; height: 24px" @click="copy(app.bundleId)">
                      <AppIcon name="copy" :size="14" />
                    </button>
                  </dd>
                </div>
                <div class="spec__row"><dt>版本</dt><dd>v{{ app.version }}（{{ app.minIOS }}+）</dd></div>
                <div class="spec__row"><dt>可执行文件</dt><dd class="mono">{{ app.executable }}</dd></div>
                <div class="spec__row"><dt>包体大小</dt><dd>{{ fmtSize(app.sizeBytes) }}</dd></div>
                <div class="spec__row"><dt>架构</dt><dd>{{ app.arch.join(', ') }}</dd></div>
                <div class="spec__row">
                  <dt>SHA256</dt>
                  <dd class="mono">{{ shortSha(app.sha256) }}</dd>
                </div>
                <div class="spec__row"><dt>脱壳时间</dt><dd>{{ fmtDate(app.updatedAt) }}</dd></div>
                <div class="spec__row"><dt>下载次数</dt><dd>{{ fmtNumber(app.downloads) }}</dd></div>
                <div class="spec__row"><dt>校验</dt><dd class="row gap-6"><span class="pill pill--green">cryptid=0</span> 主二进制已还原</dd></div>
              </dl>
            </section>

            <section v-if="related.length" class="block">
              <h2 class="block__title">相关推荐</h2>
              <div class="related">
                <AppCard v-for="r in related" :key="r.id" :app="r" />
              </div>
            </section>
          </div>

          <aside class="detail__side">
            <div class="buy card">
              <div class="buy__label">成品下载</div>
              <div class="buy__price" style="font-size: 28px">团队可下</div>
              <div class="buy__meta">
                <span v-if="owned" class="pill pill--green">已在我的应用</span>
                <span v-else>登录后直接下载，不扣费</span>
              </div>
              <UiButton variant="primary" block size="lg" style="margin-top: 18px" :loading="downloading" @click="download">
                {{ owned ? '再次下载' : '下载' }}
              </UiButton>
              <div class="buy__balance">{{ auth.isLoggedIn ? '下载后会出现在「我的应用」' : '需要先登录' }}</div>

              <div class="buy__divider"><span>或</span></div>

              <div class="buy__alt">
                <div>
                  <div class="buy__alt-title">需要其他版本？</div>
                  <div class="buy__alt-desc">提交一次新的任务</div>
                </div>
                <UiButton variant="secondary" size="sm" @click="goDecrypt">新建任务</UiButton>
              </div>

              <ul class="buy__points">
                <li><AppIcon name="check" :size="14" /> 已通过 cryptid=0 校验</li>
                <li><AppIcon name="check" :size="14" /> 下载链接 15 分钟有效</li>
                <li><AppIcon name="check" :size="14" /> 仅用于安全研究</li>
              </ul>
            </div>
          </aside>
        </div>
      </template>
    </div>


  </div>
</template>

<style scoped>
.detail {
  padding: 28px 0 80px;
}
.detail__loading {
  display: flex;
  gap: 20px;
  padding: 40px 0;
  align-items: center;
}
.crumb {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--text-2);
  margin-bottom: 26px;
}
.crumb a {
  color: var(--text-2);
}
.crumb a:hover {
  color: var(--blue);
}
.detail__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 328px;
  gap: 48px;
  align-items: start;
}
.detail__main {
  min-width: 0;
}
.app-head {
  display: flex;
  gap: 22px;
  align-items: center;
}
.app-head__name {
  font-size: 34px;
  letter-spacing: -0.035em;
}
.app-head__dev {
  font-size: 13.5px;
  color: var(--text-2);
  margin-top: 4px;
}
.app-head__pills {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 12px;
}
.block {
  margin-top: 42px;
}
.block__title {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.02em;
  margin-bottom: 14px;
}
.block__text {
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-2);
  max-width: 680px;
}
.shots {
  display: flex;
  gap: 14px;
  overflow-x: auto;
  padding-bottom: 8px;
}
.shot {
  width: 168px;
  height: 300px;
  flex: none;
  border-radius: 18px;
  border: 1px solid var(--line-soft);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.shot__bar {
  font-size: 11px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.55);
  text-align: center;
}
.shot__body {
  flex: 1;
  background: rgba(255, 255, 255, 0.72);
  border-radius: 12px;
  padding: 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 9px;
}
.shot__line {
  height: 7px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.1);
}
.shot__card {
  height: 74px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(0, 0, 0, 0.05);
  margin: 4px 0;
}
.spec {
  border-top: 1px solid var(--line-soft);
  margin: 0;
}
.spec__row {
  display: flex;
  gap: 24px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line-soft);
  font-size: 13px;
}
.spec__row dt {
  width: 120px;
  flex: none;
  color: var(--text-3);
}
.spec__row dd {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  word-break: break-all;
}
.related {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.detail__side {
  position: sticky;
  top: 84px;
}
.buy {
  padding: 24px;
}
.buy__label {
  font-size: 12.5px;
  color: var(--text-2);
}
.buy__price {
  font-size: 36px;
  font-weight: 700;
  letter-spacing: -0.04em;
  margin: 6px 0 8px;
}
.buy__meta {
  font-size: 12.5px;
  color: var(--text-3);
}
.buy__balance {
  margin-top: 12px;
  font-size: 12.5px;
  color: var(--text-3);
  text-align: center;
}
.buy__balance strong {
  color: var(--text);
}
.buy__divider {
  position: relative;
  text-align: center;
  margin: 22px 0;
}
.buy__divider::before {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: 1px;
  background: var(--line-soft);
}
.buy__divider span {
  position: relative;
  background: #fff;
  padding: 0 10px;
  font-size: 11.5px;
  color: var(--text-3);
}
.buy__alt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.buy__alt-title {
  font-size: 13px;
  font-weight: 500;
}
.buy__alt-desc {
  font-size: 11.5px;
  color: var(--text-3);
  margin-top: 2px;
}
.buy__points {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--line-soft);
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  color: var(--text-2);
}
.buy__points li {
  display: flex;
  align-items: center;
  gap: 7px;
}
.buy__points :deep(svg) {
  color: var(--green);
}
.pay-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  font-size: 13.5px;
  border-bottom: 1px solid var(--line-soft);
}
.pay-row:last-of-type {
  border-bottom: 0;
}
.pay-row span {
  color: var(--text-2);
}
.paying {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 0;
  justify-content: center;
  color: var(--text-2);
  font-size: 13.5px;
}
.recharge-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: var(--r-sm);
  background: var(--blue-soft);
  color: var(--blue);
  font-size: 12.5px;
}
.success {
  display: flex;
  gap: 14px;
  align-items: center;
}
.success__icon {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--green-soft);
  color: var(--green);
  flex: none;
}
.success__title {
  font-size: 14.5px;
  font-weight: 600;
}
@media (max-width: 980px) {
  .detail__grid { grid-template-columns: minmax(0, 1fr); gap: 32px; }
  .detail__side { position: static; }
  .related { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 560px) {
  .app-head { align-items: flex-start; }
  .app-head__name { font-size: 26px; }
  .spec__row { flex-direction: column; gap: 4px; }
}
</style>
