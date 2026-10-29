<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
withDefaults(defineProps<{ dark?: boolean }>(), { dark: false })

const auth = useAuthStore()
const router = useRouter()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

function onDocClick(e: MouseEvent) {
  if (root.value && !root.value.contains(e.target as Node)) open.value = false
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

async function logout() {
  open.value = false
  await auth.logout()
  router.push('/')
}

const initials = (s: string) => (s || 'U').trim().slice(0, 1).toUpperCase()
</script>

<template>
  <div ref="root" class="usermenu">
    <button class="usermenu__trigger" @click="open = !open">
      <span class="avatar" :style="{ background: auth.user?.avatarColor }">{{ initials(auth.displayName) }}</span>
      <span class="usermenu__name hide-sm">{{ auth.displayName }}</span>
      <AppIcon name="chevron-down" :size="14" class="usermenu__caret" />
    </button>

    <Transition name="fade">
      <div v-if="open" class="usermenu__panel" :class="{ 'usermenu__panel--dark': dark }">
        <div class="usermenu__head">
          <div class="usermenu__full">{{ auth.user?.email }}</div>
          <div class="tiny dim">{{ auth.isAdmin ? '管理员账号' : '普通用户' }}</div>
        </div>
        <router-link to="/app/dashboard" class="usermenu__item" @click="open = false">
          <AppIcon name="home" :size="16" /> 控制台
        </router-link>
        <router-link to="/app/library" class="usermenu__item" @click="open = false">
          <AppIcon name="box" :size="16" /> 我的应用
        </router-link>
        <router-link v-if="auth.isAdmin" to="/admin/overview" class="usermenu__item" @click="open = false">
          <AppIcon name="shield" :size="16" /> 管理后台
        </router-link>
        <div class="divider" style="margin: 6px 0" />
        <button class="usermenu__item usermenu__item--danger" @click="logout">
          <AppIcon name="logout" :size="16" /> 退出登录
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.usermenu {
  position: relative;
}
.usermenu__trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 8px 3px 3px;
  border-radius: var(--r-pill);
  transition: background 0.15s;
}
.usermenu__trigger:hover {
  background: var(--bg-subtle);
}
.usermenu__name {
  font-size: 13.5px;
  font-weight: 500;
}
.usermenu__caret {
  color: var(--text-3);
}
.usermenu__panel {
  position: absolute;
  right: 0;
  top: calc(100% + 10px);
  width: 232px;
  padding: 8px;
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: var(--r-md);
  box-shadow: var(--sh-md);
  z-index: 60;
}
.usermenu__head {
  padding: 8px 10px 10px;
  border-bottom: 1px solid var(--line-soft);
  margin-bottom: 6px;
}
.usermenu__full {
  font-size: 12.5px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
}
.usermenu__item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 9px 10px;
  border-radius: 8px;
  font-size: 13.5px;
  color: var(--text);
  text-align: left;
  transition: background 0.15s;
}
.usermenu__item:hover {
  background: var(--bg-subtle);
  color: var(--text);
}
.usermenu__item--danger {
  color: var(--red);
}
.usermenu__item--danger:hover {
  background: var(--red-soft);
  color: var(--red);
}
.usermenu__bal {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}
</style>
