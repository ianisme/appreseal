<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppLogo from '@/components/AppLogo.vue'
import AppIcon from '@/components/AppIcon.vue'
import UserMenu from '@/components/UserMenu.vue'
import SiteBanner from '@/components/SiteBanner.vue'

const route = useRoute()
const drawer = ref(false)

const nav = [
  { to: '/admin/overview', label: '概览', icon: 'chart' },
  { to: '/admin/users', label: '用户', icon: 'users' },
  { to: '/admin/tasks', label: '脱壳任务', icon: 'terminal' },
  { to: '/admin/apps', label: '应用库', icon: 'grid' },
  { to: '/admin/apple-ids', label: 'Apple ID 池', icon: 'key' },
  { to: '/admin/system', label: '系统', icon: 'server' },
  { to: '/admin/logs', label: '审计日志', icon: 'book' }
]

watch(() => route.fullPath, () => (drawer.value = false))
</script>

<template>
  <div class="admin">
    <div v-if="drawer" class="admin__scrim" @click="drawer = false" />

    <aside class="admin__aside" :class="{ 'is-open': drawer }">
      <div class="admin__brand">
        <AppLogo dark to="/admin/overview" />
        <span class="admin__tag">ADMIN</span>
      </div>

      <nav class="admin__nav">
        <router-link
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="admin__link"
          :class="{ 'is-active': route.path.startsWith(item.to) }"
        >
          <AppIcon :name="item.icon" :size="16.5" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="admin__foot">
        <router-link to="/" class="admin__back">
          <AppIcon name="arrow-left" :size="15" /> 返回前台
        </router-link>
        <div class="admin__hint">v1.0 · 演示环境</div>
      </div>
    </aside>

    <div class="admin__main">
      <SiteBanner />
      <header class="admin__top">
        <div class="row gap-10">
          <button class="icon-btn admin__burger" aria-label="菜单" @click="drawer = true">
            <AppIcon name="menu" :size="20" />
          </button>
          <div>
            <div class="admin__crumb">管理后台 / {{ route.meta.title || '' }}</div>
            <h1 class="admin__title">{{ route.meta.title || '管理后台' }}</h1>
          </div>
        </div>
        <div class="row gap-14">
          <span class="pill pill--green pill--live"><span class="pill__dot" /> 队列正常</span>
          <UserMenu />
        </div>
      </header>

      <main class="admin__content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin {
  display: flex;
  min-height: 100vh;
  background: var(--bg-subtle);
}
.admin__aside {
  width: var(--aside-w);
  flex: none;
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #141416;
  padding: 18px 14px;
  z-index: 40;
}
.admin__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px 24px;
}
.admin__tag {
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: #8e8e93;
  border: 1px solid #3a3a3f;
  border-radius: 5px;
  padding: 2px 5px;
}
.admin__nav {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.admin__link {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 11px;
  border-radius: 9px;
  font-size: 13.5px;
  color: #a1a1a6;
  transition: background 0.15s, color 0.15s;
}
.admin__link:hover {
  background: #232326;
  color: #f5f5f7;
}
.admin__link.is-active {
  background: #2a2a2e;
  color: #fff;
  font-weight: 500;
}
.admin__link.is-active :deep(svg) {
  color: #4da3ff;
}
.admin__foot {
  margin-top: auto;
  padding: 8px 4px 0;
}
.admin__back {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 8px;
  border-radius: 8px;
  font-size: 13px;
  color: #a1a1a6;
}
.admin__back:hover {
  background: #232326;
  color: #fff;
}
.admin__hint {
  margin-top: 8px;
  font-size: 11px;
  color: #5a5a60;
  padding-left: 8px;
}
.admin__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.admin__top {
  position: sticky;
  top: 0;
  z-index: 30;
  min-height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 32px;
  background: rgba(245, 245, 247, 0.88);
  backdrop-filter: saturate(180%) blur(16px);
  border-bottom: 1px solid var(--line-soft);
}
.admin__crumb {
  font-size: 11.5px;
  color: var(--text-3);
}
.admin__title {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.02em;
  margin-top: 1px;
}
.admin__burger {
  display: none;
}
.admin__content {
  padding: 28px 32px 56px;
  max-width: 1280px;
  width: 100%;
}
.admin__scrim {
  display: none;
}
@media (max-width: 900px) {
  .admin__aside {
    position: fixed;
    left: 0;
    top: 0;
    transform: translateX(-100%);
    transition: transform 0.25s var(--ease);
  }
  .admin__aside.is-open {
    transform: translateX(0);
  }
  .admin__scrim {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.4);
    z-index: 35;
  }
  .admin__burger {
    display: inline-flex;
  }
  .admin__top {
    padding: 10px 18px;
  }
  .admin__content {
    padding: 20px 18px 48px;
  }
}
</style>
