<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppLogo from '@/components/AppLogo.vue'
import AppIcon from '@/components/AppIcon.vue'
import UserMenu from '@/components/UserMenu.vue'
import SiteBanner from '@/components/SiteBanner.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const drawer = ref(false)

const nav = [
  { to: '/app/dashboard', label: '概览', icon: 'home' },
  { to: '/app/new', label: '新建脱壳', icon: 'plus' },
  { to: '/app/tasks', label: '任务', icon: 'list' },
  { to: '/app/library', label: '我的应用', icon: 'box' },
  { to: '/app/settings', label: '设置', icon: 'settings' }
]

const isActive = (to: string) => route.path === to || (to === '/app/tasks' && route.path.startsWith('/app/tasks'))

watch(() => route.fullPath, () => {
  drawer.value = false
})
</script>

<template>
  <div class="console">
    <div v-if="drawer" class="console__scrim" @click="drawer = false" />

    <aside class="console__aside" :class="{ 'is-open': drawer }">
      <div class="console__brand">
        <AppLogo to="/app/dashboard" />
      </div>

      <nav class="console__nav">
        <router-link
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="console__link"
          :class="{ 'is-active': isActive(item.to) }"
        >
          <AppIcon :name="item.icon" :size="17" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="console__aside-foot">
        <div class="console__balance">
          <div class="tiny dim">使用方式</div>
          <div class="console__balance-num" style="font-size: 16px">团队自用</div>
          <div class="tiny dim">提交和下载都不扣费</div>
        </div>
        <router-link v-if="auth.isAdmin" to="/admin/overview" class="console__admin">
          <AppIcon name="shield" :size="15" /> 管理后台
        </router-link>
      </div>
    </aside>

    <div class="console__main">
      <SiteBanner />
      <header class="console__top">
        <div class="row gap-10">
          <button class="icon-btn console__burger" aria-label="菜单" @click="drawer = true">
            <AppIcon name="menu" :size="20" />
          </button>
          <h1 class="console__title">{{ route.meta.title || '控制台' }}</h1>
        </div>
        <div class="row gap-14">
          <UserMenu />
        </div>
      </header>

      <main class="console__content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.console {
  display: flex;
  min-height: 100vh;
  background: var(--bg-subtle);
}
.console__aside {
  width: var(--aside-w);
  flex: none;
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid var(--line-soft);
  padding: 18px 14px;
  z-index: 40;
}
.console__brand {
  padding: 6px 8px 22px;
}
.console__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.console__link {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 11px;
  border-radius: 9px;
  font-size: 13.5px;
  color: var(--text-2);
  transition: background 0.15s, color 0.15s;
}
.console__link:hover {
  background: var(--bg-subtle);
  color: var(--text);
}
.console__link.is-active {
  background: var(--bg-subtle);
  color: var(--text);
  font-weight: 550;
}
.console__link.is-active :deep(svg) {
  color: var(--blue);
}
.console__aside-foot {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.console__balance {
  padding: 14px;
  border-radius: var(--r-md);
  background: linear-gradient(150deg, #f2f7ff, #eef1ff);
  border: 1px solid #e2ebfa;
}
.console__balance-num {
  font-size: 21px;
  font-weight: 700;
  letter-spacing: -0.03em;
  margin: 2px 0 8px;
  font-variant-numeric: tabular-nums;
}
.console__recharge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  font-weight: 500;
}
.console__admin {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 11px;
  border-radius: 9px;
  font-size: 13px;
  color: var(--text-2);
}
.console__admin:hover {
  background: var(--bg-subtle);
  color: var(--text);
}
.console__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.console__top {
  position: sticky;
  top: 0;
  z-index: 30;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 32px;
  background: rgba(245, 245, 247, 0.86);
  backdrop-filter: saturate(180%) blur(16px);
  border-bottom: 1px solid var(--line-soft);
}
.console__title {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.02em;
}
.console__burger {
  display: none;
}
.console__bal-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border-radius: var(--r-pill);
  background: #fff;
  border: 1px solid var(--line-soft);
  font-size: 12.5px;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}
.console__content {
  padding: 28px 32px 56px;
  max-width: 1220px;
  width: 100%;
}
.console__scrim {
  display: none;
}

@media (max-width: 900px) {
  .console__aside {
    position: fixed;
    left: 0;
    top: 0;
    transform: translateX(-100%);
    transition: transform 0.25s var(--ease);
    box-shadow: var(--sh-md);
  }
  .console__aside.is-open {
    transform: translateX(0);
  }
  .console__scrim {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.3);
    z-index: 35;
  }
  .console__burger {
    display: inline-flex;
  }
  .console__top {
    padding: 0 18px;
  }
  .console__content {
    padding: 20px 18px 48px;
  }
}
</style>
