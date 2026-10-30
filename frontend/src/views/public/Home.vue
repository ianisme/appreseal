<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import AppCard from '@/components/AppCard.vue'
import UiButton from '@/components/UiButton.vue'
import { api } from '@/api'
import type { AppItem } from '@/types'


const apps = ref<AppItem[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const all = await api.listApps({ sort: 'downloads' })
    apps.value = all.slice(0, 4)
  } finally {
    loading.value = false
  }
})

const stats = [
  { value: '自动下载', label: '从 App Store 拉取加密包' },
  { value: '自动脱壳', label: '服务端解开后再交付' },
  { value: '不收费', label: '给自己或团队使用' },
  { value: '再下', label: '明文包可以反复拿' }
]

const steps = [
  { n: '01', icon: 'zap', title: '选一个苹果应用', desc: '从 App Store 搜索，或上传自己已经下载的加密 IPA。' },
  { n: '02', icon: 'clock', title: '自动下载并脱壳', desc: '服务端自动下载加密包，再自动脱壳。进度在页面上看得清。' },
  { n: '03', icon: 'download', title: '下载明文包', desc: '完成后直接下载。自己的任务和成品库都可以再拿。' }
]

const faqs = [
  {
    q: '这是给谁用的？',
    a: '给自己或小团队用。登录后提交任务、看进度、下载结果。没有钱包、价格和充值。'
  },
  {
    q: '我需要自己准备什么吗？',
    a: '使用者不用准备设备。管理员需要在后台准备 Apple ID 和 16 字节凭证，任务才能真正解开。'
  },
  {
    q: '已经有现成的包吗？',
    a: '成品库里已上架的包，登录后可以直接下载，不另外计费。'
  },
  {
    q: '可以取消吗？',
    a: '排队中和正在处理的任务都可以取消。取消只是停掉这次处理，没有退款，因为本来也不扣费。'
  },
  {
    q: '适合什么时候用？',
    a: '只用于学习研究，并且只处理你有权分析的安装包。'
  }
]

const openFaq = ref(0)
</script>

<template>
  <div>
    <!-- hero -->
    <section class="hero">
      <div class="container hero__grid">
        <div class="hero__copy">
          <div class="eyebrow">苹果应用脱壳</div>
          <h1 class="h1 hero__title">选一个应用，<br />自动下载，<br />自动脱壳。</h1>
          <p class="lead hero__lead">
            给自己和团队用的苹果应用脱壳工具。从 App Store 选好应用后，服务端会自动下载加密包并完成脱壳，做好后直接下载明文包。
          </p>
          <div class="hero__actions">
            <UiButton to="/app/new" variant="primary" size="lg">新建任务</UiButton>
            <UiButton to="/market" variant="secondary" size="lg">
              看成品库
              <AppIcon name="arrow-right" :size="16" />
            </UiButton>
          </div>
          <div class="hero__trust">
            <span><AppIcon name="download" :size="14" /> 自动下载</span>
            <span><AppIcon name="zap" :size="14" /> 自动脱壳</span>
            <span><AppIcon name="lock" :size="14" /> 仅供学习研究</span>
          </div>
        </div>

        <div class="hero__visual">
          <div class="term">
            <div class="term__bar">
              <span class="term__dot" style="background: #ff5f57" />
              <span class="term__dot" style="background: #febc2e" />
              <span class="term__dot" style="background: #28c840" />
              <span class="term__name">appreseal</span>
            </div>
            <div class="term__body">
              <div><span class="c-prompt">●</span> <span class="c-cmd">观星</span></div>
              <div class="c-dim">已选择 · 可以开始</div>
              <div class="c-dim">处理中 … <span class="c-white">马上好</span></div>
              <div class="c-ok">✔ 完成，可以下载</div>
              <div class="c-dim">用时 2 分 41 秒 · 可重复获取</div>
              <div class="term__cursor"><span class="c-prompt">●</span> <i /></div>
            </div>
          </div>
          <div class="hero__badge">
            <span class="pill pill--green"><span class="pill__dot" /> 已完成</span>
            <div class="hero__badge-text">
              <strong>现在就能下载</strong>
              <span>团队成员可以直接拿</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- stats -->
    <section class="stats">
      <div class="container stats__grid">
        <div v-for="s in stats" :key="s.label" class="stats__item">
          <div class="stats__value">{{ s.value }}</div>
          <div class="stats__label">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <!-- steps -->
    <section class="section">
      <div class="container">
        <div class="section-head">
          <div>
            <div class="eyebrow">WHY APPRESEAL</div>
            <h2 class="h2" style="margin-top: 10px">下载和脱壳都自动完成。</h2>
            <p>选好苹果应用，剩下的不用自己操作。</p>
          </div>
          <router-link to="/docs" class="row gap-6 small">
            使用文档 <AppIcon name="arrow-right" :size="14" />
          </router-link>
        </div>

        <div class="steps">
          <div v-for="s in steps" :key="s.n" class="step card card--pad">
            <div class="step__top">
              <span class="step__n">{{ s.n }}</span>
              <span class="step__icon"><AppIcon :name="s.icon" :size="20" /></span>
            </div>
            <h3 class="h3" style="margin-top: 26px">{{ s.title }}</h3>
            <p class="step__desc">{{ s.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- market -->
    <section class="section section--gray">
      <div class="container">
        <div class="section-head">
          <div>
            <div class="eyebrow">LIBRARY</div>
            <h2 class="h2" style="margin-top: 10px">团队成品</h2>
            <p>已经整理好的包。登录后可以直接下载，不另外计费。</p>
          </div>
          <router-link to="/market" class="row gap-6 small">
            查看全部 <AppIcon name="arrow-right" :size="14" />
          </router-link>
        </div>

        <div class="app-grid">
          <template v-if="loading">
            <div v-for="i in 4" :key="i" class="card card--pad" style="height: 232px">
              <div class="skeleton" style="width: 46px; height: 46px; border-radius: 12px" />
              <div class="skeleton" style="width: 60%; height: 14px; margin-top: 18px" />
              <div class="skeleton" style="width: 85%; height: 11px; margin-top: 12px" />
              <div class="skeleton" style="width: 70%; height: 11px; margin-top: 8px" />
            </div>
          </template>
          <AppCard v-for="app in apps" v-else :key="app.id" :app="app" />
        </div>
      </div>
    </section>

    <!-- faq -->
    <section class="section section--gray">
      <div class="container faq-wrap">
        <div class="faq-head">
          <div class="eyebrow">FAQ</div>
          <h2 class="h2" style="margin-top: 10px">常见问题</h2>
          <p class="muted" style="margin-top: 12px">没找到答案？<a href="mailto:support@appreseal.com">联系支持</a></p>
        </div>
        <div class="faq-list">
          <div v-for="(f, i) in faqs" :key="f.q" class="faq-item" :class="{ 'is-open': openFaq === i }">
            <button class="faq-item__q" @click="openFaq = openFaq === i ? -1 : i">
              <span>{{ f.q }}</span>
              <AppIcon name="chevron-down" :size="18" class="faq-item__icon" />
            </button>
            <div v-if="openFaq === i" class="faq-item__a">{{ f.a }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- cta -->
    <section class="section">
      <div class="container">
        <div class="cta">
          <div class="cta__glow" />
          <h2 class="h2" style="color: #fff; position: relative">现在就开始。</h2>
          <p style="color: rgba(255, 255, 255, 0.62); margin-top: 12px; position: relative">
            注册后就能提交任务。演示库里的应用都是虚构的。
          </p>
          <div class="cta__actions">
            <UiButton to="/register" variant="primary" size="lg">注册</UiButton>
            <UiButton to="/market" variant="secondary" size="lg" style="background: rgba(255,255,255,.1); color: #fff; border-color: rgba(255,255,255,.24)">看成品库</UiButton>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.hero {
  position: relative;
  padding: 84px 0 76px;
  overflow: hidden;
}
.hero::before {
  content: "";
  position: absolute;
  width: 720px;
  height: 720px;
  right: -220px;
  top: -260px;
  background: radial-gradient(circle, rgba(0, 113, 227, 0.12), transparent 62%);
  pointer-events: none;
}
.hero__grid {
  display: grid;
  grid-template-columns: 1.04fr 0.96fr;
  gap: 60px;
  align-items: center;
}
.hero__title {
  margin-top: 16px;
  font-size: clamp(36px, 4.6vw, 58px);
}
.hero__lead {
  margin-top: 22px;
  max-width: 520px;
}
.hero__lead code {
  font-size: 0.86em;
  background: var(--bg-subtle);
  padding: 2px 6px;
  border-radius: 6px;
}
.hero__actions {
  position: relative;
  z-index: 2;
  display: flex;
  gap: 12px;
  margin-top: 30px;
  flex-wrap: wrap;
}
.hero__trust {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 28px;
  font-size: 12.5px;
  color: var(--text-3);
}
.hero__trust span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.hero__visual {
  position: relative;
  z-index: 1;
  /* 3D 示意图只是装饰。Safari 会把 transform 的点击区域扩到按钮上。 */
  pointer-events: none;
}
.term {
  background: #131315;
  border-radius: 18px;
  box-shadow: 0 34px 90px rgba(16, 16, 20, 0.32), inset 0 1px 0 rgba(255, 255, 255, 0.06);
  overflow: hidden;
  transform: perspective(1400px) rotateY(-4deg) rotateX(1.5deg);
  transition: transform 0.4s var(--ease);
}
.term:hover {
  transform: perspective(1400px) rotateY(-1deg);
}
.term__bar {
  height: 38px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 14px;
  background: #1c1c1f;
  border-bottom: 1px solid #26262a;
}
.term__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}
.term__name {
  margin-left: 10px;
  font-family: var(--mono);
  font-size: 11px;
  color: #6e6e73;
}
.term__body {
  padding: 20px 22px 24px;
  font-family: var(--mono);
  font-size: 12px;
  line-height: 2.05;
  color: #a1a1a6;
}
.c-prompt { color: #4da3ff; }
.c-cmd { color: #f5f5f7; }
.c-dim { color: #8e8e93; }
.c-white { color: #f5f5f7; }
.c-ok { color: #4ade80; }
.term__cursor {
  display: flex;
  align-items: center;
  gap: 8px;
}
.term__cursor i {
  display: inline-block;
  width: 7px;
  height: 14px;
  background: #4da3ff;
  animation: blink 1.1s steps(2, start) infinite;
}
@keyframes blink {
  to { visibility: hidden; }
}
.hero__badge {
  position: absolute;
  right: -18px;
  bottom: -22px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--sh-lg);
  border: 1px solid var(--line-soft);
}
.hero__badge-text {
  display: flex;
  flex-direction: column;
}
.hero__badge-text strong {
  font-size: 13px;
  letter-spacing: -0.01em;
}
.hero__badge-text span {
  font-size: 11.5px;
  color: var(--text-3);
}

.stats {
  border-top: 1px solid var(--line-soft);
  border-bottom: 1px solid var(--line-soft);
  background: var(--bg-subtle);
}
.stats__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  padding: 26px 0;
}
.stats__item {
  text-align: center;
  border-right: 1px solid var(--line);
}
.stats__item:last-child {
  border-right: 0;
}
.stats__value {
  font-size: 25px;
  font-weight: 700;
  letter-spacing: -0.03em;
}
.stats__label {
  font-size: 12.5px;
  color: var(--text-2);
  margin-top: 2px;
}

.steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.step__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.step__n {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--text-3);
  letter-spacing: 0.08em;
}
.step__icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--blue-soft);
  color: var(--blue);
}
.step__desc {
  margin-top: 10px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--text-2);
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
}

.pricing-band {
  display: grid;
  grid-template-columns: 1fr 0.85fr;
  gap: 64px;
  align-items: center;
}
.pricing-band__points {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 26px;
  font-size: 13.5px;
  color: var(--text-2);
}
.pricing-band__points div {
  display: flex;
  align-items: center;
  gap: 9px;
}
.pricing-band__points :deep(svg) {
  color: var(--green);
}
.price-cards {
  display: grid;
  gap: 16px;
}
.price-card {
  padding: 24px;
}
.price-card--accent {
  background: linear-gradient(150deg, #f4f9ff, #fff);
  border-color: #d9e9fb;
}
.price-card__label {
  font-size: 12.5px;
  color: var(--text-2);
  font-weight: 500;
}
.price-card__value {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.04em;
  margin: 8px 0 6px;
}
.price-card__value span {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-3);
  letter-spacing: 0;
}
.price-card p {
  font-size: 13px;
  color: var(--text-2);
}
.price-card__meta {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line-soft);
  font-size: 12px;
  color: var(--text-3);
}

.faq-wrap {
  display: grid;
  grid-template-columns: 0.6fr 1fr;
  gap: 64px;
}
.faq-list {
  border-top: 1px solid var(--line);
}
.faq-item {
  border-bottom: 1px solid var(--line);
}
.faq-item__q {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 0;
  font-size: 14.5px;
  font-weight: 500;
  text-align: left;
}
.faq-item__icon {
  color: var(--text-3);
  transition: transform 0.2s var(--ease);
  flex: none;
}
.faq-item.is-open .faq-item__icon {
  transform: rotate(180deg);
}
.faq-item__a {
  padding: 0 32px 20px 0;
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--text-2);
  animation: fade-up 0.2s var(--ease);
}
@keyframes fade-up {
  from { opacity: 0; transform: translateY(-4px); }
}

.cta {
  position: relative;
  border-radius: 28px;
  padding: 60px 48px;
  background: linear-gradient(135deg, #17171a 0%, #1d2430 100%);
  overflow: hidden;
}
.cta__glow {
  position: absolute;
  width: 560px;
  height: 560px;
  right: -120px;
  top: -220px;
  background: radial-gradient(circle, rgba(0, 113, 227, 0.34), transparent 62%);
  pointer-events: none;
}
.cta__actions {
  display: flex;
  gap: 12px;
  margin-top: 28px;
  position: relative;
}

@media (max-width: 980px) {
  .hero__grid { grid-template-columns: 1fr; gap: 44px; }
  .hero__visual { max-width: 560px; }
  .app-grid { grid-template-columns: repeat(2, 1fr); }
  .steps { grid-template-columns: 1fr; }
  .pricing-band { grid-template-columns: 1fr; gap: 32px; }
  .faq-wrap { grid-template-columns: 1fr; gap: 24px; }
  .stats__grid { grid-template-columns: repeat(2, 1fr); gap: 18px; }
  .stats__item { border-right: 0; }
}
@media (max-width: 560px) {
  .hero { padding: 52px 0 48px; }
  .hero__badge { right: 8px; }
  .app-grid { grid-template-columns: 1fr; }
  .cta { padding: 40px 24px; }
}
</style>
