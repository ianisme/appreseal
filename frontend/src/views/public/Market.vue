<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppCard from '@/components/AppCard.vue'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { api } from '@/api'
import type { AppItem } from '@/types'

const q = ref('')
const category = ref('全部')
const sort = ref('latest')
const apps = ref<AppItem[]>([])
const loading = ref(true)
const categories = ['全部', '社交', '影音', '工具', '效率']

let timer: number | undefined

async function load() {
  loading.value = true
  try {
    apps.value = await api.listApps({ q: q.value, category: category.value, sort: sort.value })
  } finally {
    loading.value = false
  }
}

watch(q, () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(load, 260)
})
watch([category, sort], load)
onMounted(load)

const resultText = computed(() =>
  loading.value ? '正在加载…' : `共 ${apps.value.length} 个应用`
)
</script>

<template>
  <div class="market">
    <section class="market__head">
      <div class="container">
        <div class="eyebrow">LIBRARY</div>
        <h1 class="h1 market__title">成品库</h1>
        <p class="lead market__lead">团队已经整理好的包。登录后可以直接下载，不另外计费。</p>

        <div class="market__toolbar">
          <div class="search-bar">
            <span class="search-bar__icon"><AppIcon name="search" :size="17" /></span>
            <input v-model="q" class="input" placeholder="搜索应用名称、开发者或 bundle id" />
          </div>
          <select v-model="sort" class="select market__sort" aria-label="排序">
            <option value="latest">最近更新</option>
            <option value="downloads">下载最多</option>

          </select>
        </div>

        <div class="market__chips">
          <button
            v-for="c in categories"
            :key="c"
            class="chip"
            :class="{ 'is-active': category === c }"
            @click="category = c"
          >
            {{ c }}
          </button>
          <span class="market__count">{{ resultText }}</span>
        </div>
      </div>
    </section>

    <section class="market__body">
      <div class="container">
        <div v-if="loading" class="app-grid">
          <div v-for="i in 8" :key="i" class="card card--pad" style="height: 236px">
            <div class="row gap-12">
              <div class="skeleton" style="width: 46px; height: 46px; border-radius: 12px" />
              <div style="flex: 1">
                <div class="skeleton" style="width: 55%; height: 13px" />
                <div class="skeleton" style="width: 38%; height: 10px; margin-top: 9px" />
              </div>
            </div>
            <div class="skeleton" style="width: 90%; height: 10px; margin-top: 22px" />
            <div class="skeleton" style="width: 72%; height: 10px; margin-top: 9px" />
            <div class="skeleton" style="width: 100%; height: 1px; margin-top: 26px" />
            <div class="skeleton" style="width: 30%; height: 12px; margin-top: 16px" />
          </div>
        </div>

        <template v-else>
          <div v-if="apps.length" class="app-grid">
            <AppCard v-for="app in apps" :key="app.id" :app="app" />
          </div>
          <EmptyState
            v-else
            icon="search"
            title="没有找到相关应用"
            :desc="q ? `没有与「${q}」匹配的结果，换个关键词，或直接发起脱壳。` : '该分类暂时没有上架的应用。'"
          >
            <router-link to="/app/new">
              <span class="btn btn--primary btn--sm">发起脱壳</span>
            </router-link>
          </EmptyState>
        </template>
      </div>
    </section>
  </div>
</template>

<style scoped>
.market__head {
  padding: 64px 0 30px;
  background: var(--bg-subtle);
  border-bottom: 1px solid var(--line-soft);
}
.market__title {
  margin-top: 12px;
  font-size: clamp(32px, 4vw, 44px);
}
.market__lead {
  margin-top: 12px;
  max-width: 620px;
}
.market__toolbar {
  display: flex;
  gap: 12px;
  margin-top: 30px;
  max-width: 720px;
}
.market__sort {
  width: 180px;
  flex: none;
  height: 40px;
  border-radius: var(--r-pill);
  border-color: transparent;
  background-color: #fff;
}
.market__chips {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 20px;
  flex-wrap: wrap;
}
.market__count {
  margin-left: auto;
  font-size: 12.5px;
  color: var(--text-3);
}
.market__body {
  padding: 36px 0 72px;
}
.app-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
}
@media (max-width: 1080px) {
  .app-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 820px) {
  .app-grid { grid-template-columns: repeat(2, 1fr); }
  .market__toolbar { flex-direction: column; }
  .market__sort { width: 100%; }
  .market__count { width: 100%; margin-left: 0; }
}
@media (max-width: 540px) {
  .app-grid { grid-template-columns: 1fr; }
}
</style>
