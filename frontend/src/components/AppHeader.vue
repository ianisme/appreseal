<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLogo from './AppLogo.vue'
import AppIcon from './AppIcon.vue'
import UiButton from './UiButton.vue'
import UserMenu from './UserMenu.vue'
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const scrolled = ref(false)
const mobile = ref(false)

const links = [
  { to: '/market', label: '成品库' },
  { to: '/docs', label: '文档' }
]

function onScroll() {
  scrolled.value = window.scrollY > 8
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })

})
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))

watch(() => route.fullPath, () => (mobile.value = false))

const initials = (s: string) => (s || 'U').trim().slice(0, 1).toUpperCase()
</script>

<template>
  <header class="header" :class="{ 'is-scrolled': scrolled }">
    <div class="container header__inner">
      <div class="header__left">
        <AppLogo />
        <nav class="header__nav hide-sm">
          <router-link
            v-for="l in links"
            :key="l.to"
            :to="l.to"
            class="header__link"
            :class="{ 'is-active': route.path.startsWith(l.to) }"
          >
            {{ l.label }}
          </router-link>
        </nav>
      </div>

      <div class="header__right">
        <template v-if="auth.isLoggedIn">
          <UiButton to="/app/new" class="hide-sm" variant="primary" size="sm">新建任务</UiButton>
          <UserMenu />
        </template>
        <template v-else>
          <router-link to="/login" class="header__link hide-sm">登录</router-link>
          <UiButton to="/register" class="hide-sm" variant="primary" size="sm">注册</UiButton>
        </template>
        <button class="icon-btn header__burger" aria-label="菜单" @click="mobile = !mobile">
          <AppIcon :name="mobile ? 'x' : 'menu'" :size="20" />
        </button>
      </div>
    </div>

    <Transition name="fade">
      <div v-if="mobile" class="mobile-menu">
        <router-link v-for="l in links" :key="l.to" :to="l.to" class="mobile-menu__link">{{ l.label }}</router-link>
        <div class="divider" style="margin: 8px 0" />
        <template v-if="!auth.isLoggedIn">
          <router-link to="/login" class="mobile-menu__link">登录</router-link>
          <router-link to="/register" class="mobile-menu__link">注册</router-link>
        </template>
        <template v-else>
          <router-link to="/app/dashboard" class="mobile-menu__link">控制台</router-link>
          <button class="mobile-menu__link" @click="auth.logout().then(() => router.push('/'))">退出登录</button>
        </template>
      </div>
    </Transition>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border-bottom: 1px solid transparent;
  transition: border-color 0.2s, box-shadow 0.2s;
  /* Safari 里带 backdrop-filter 的 sticky 头会把点击区域扩到整页。 */
  pointer-events: none;
}
.header__inner,
.mobile-menu {
  pointer-events: auto;
}
.header.is-scrolled {
  border-bottom-color: var(--line-soft);
}
.header__inner {
  height: var(--header-h);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.header__left {
  display: flex;
  align-items: center;
  gap: 38px;
}
.header__nav {
  display: flex;
  gap: 30px;
}
.header__link {
  font-size: 13.5px;
  color: var(--text-2);
  font-weight: 400;
  transition: color 0.15s;
}
.header__link:hover,
.header__link.is-active {
  color: var(--text);
}
.header__right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.header__balance {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border-radius: var(--r-pill);
  background: var(--bg-subtle);
  color: var(--text);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}
.header__balance:hover {
  background: #ececf0;
  color: var(--text);
}
.header__burger {
  display: none;
}
@media (max-width: 720px) {
  .header__burger {
    display: inline-flex;
  }
  .header__left {
    gap: 0;
  }
}
.mobile-menu {
  position: absolute;
  left: 0;
  right: 0;
  top: var(--header-h);
  background: #fff;
  border-bottom: 1px solid var(--line-soft);
  padding: 12px 18px 20px;
  box-shadow: var(--sh-sm);
}
.mobile-menu__link {
  display: block;
  width: 100%;
  text-align: left;
  padding: 11px 4px;
  font-size: 14.5px;
  color: var(--text);
}
</style>
