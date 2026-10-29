import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '@/api'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const ready = ref(false)
  const loading = ref(false)

  const isLoggedIn = computed(() => !!user.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const displayName = computed(() => user.value?.nickname || user.value?.email?.split('@')[0] || '')

  async function bootstrap() {
    if (ready.value) return
    try {
      user.value = await api.me()
    } catch {
      user.value = null
    } finally {
      ready.value = true
    }
  }

  async function login(email: string, password: string) {
    loading.value = true
    try {
      user.value = await api.login(email, password)
      return user.value
    } finally {
      loading.value = false
    }
  }

  async function register(email: string, password: string, nickname: string) {
    loading.value = true
    try {
      user.value = await api.register(email, password, nickname)
      return user.value
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    await api.logout()
    user.value = null
  }

  async function resetPassword(email: string, code: string, newPassword: string) {
    loading.value = true
    try {
      user.value = await api.resetPassword(email, code, newPassword)
      return user.value
    } finally {
      loading.value = false
    }
  }

  function patch(p: Partial<User>) {
    if (user.value) user.value = { ...user.value, ...p }
  }

  return { user, ready, loading, isLoggedIn, isAdmin, displayName, bootstrap, login, register, logout, resetPassword, patch }
})
