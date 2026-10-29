<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AuthShell from './AuthShell.vue'
import UiInput from '@/components/UiInput.vue'
import UiButton from '@/components/UiButton.vue'
import AppIcon from '@/components/AppIcon.vue'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'

import { useToastStore } from '@/stores/toast'

const auth = useAuthStore()

const toast = useToastStore()
const router = useRouter()

const step = ref<'email' | 'code'>('email')
const email = ref('')
const code = ref('')
const password = ref('')
const confirm = ref('')
const error = ref('')
const hint = ref('')
const sending = ref(false)

async function sendCode() {
  error.value = ''
  hint.value = ''
  if (!email.value.trim()) {
    error.value = '请输入邮箱'
    return
  }
  sending.value = true
  try {
    const res = await api.forgotPassword(email.value.trim())
    hint.value = res.message
    step.value = 'code'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '发送失败'
  } finally {
    sending.value = false
  }
}

async function reset() {
  error.value = ''
  if (!/^\d{6}$/.test(code.value.trim())) {
    error.value = '请输入 6 位验证码'
    return
  }
  if (password.value.length < 8) {
    error.value = '新密码至少 8 位'
    return
  }
  if (password.value !== confirm.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  try {
    const user = await auth.resetPassword(email.value.trim(), code.value.trim(), password.value)

    toast.success('密码已重置')
    router.push(user.role === 'ADMIN' ? '/admin/overview' : '/app/dashboard')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '重置失败'
  }
}
</script>

<template>
  <AuthShell title="找回密码" subtitle="验证码发到注册邮箱，10 分钟内有效。">
    <form v-if="step === 'email'" class="form" @submit.prevent="sendCode">
      <UiInput v-model="email" label="邮箱" placeholder="you@example.com" icon="mail" autocomplete="email" type="email" />
      <div v-if="error" class="form__error">
        <AppIcon name="alert" :size="15" /> {{ error }}
      </div>
      <UiButton type="submit" variant="primary" size="lg" block :loading="sending">发送验证码</UiButton>
      <div class="form__meta">
        <router-link to="/login" class="tiny dim">返回登录</router-link>
      </div>
    </form>

    <form v-else class="form" @submit.prevent="reset">
      <p v-if="hint" class="form__hint">{{ hint }}</p>
      <UiInput v-model="code" label="验证码" placeholder="6 位数字" icon="shield" autocomplete="one-time-code" />
      <UiInput v-model="password" label="新密码" placeholder="至少 8 位" type="password" icon="lock" autocomplete="new-password" />
      <UiInput v-model="confirm" label="确认新密码" placeholder="再输入一次" type="password" autocomplete="new-password" />
      <div v-if="error" class="form__error">
        <AppIcon name="alert" :size="15" /> {{ error }}
      </div>
      <UiButton type="submit" variant="primary" size="lg" block :loading="auth.loading">重置并登录</UiButton>
      <div class="form__meta">
        <button type="button" class="tiny dim linkish" @click="step = 'email'; error = ''">换一个邮箱</button>
        <router-link to="/login" class="tiny dim">返回登录</router-link>
      </div>
    </form>
  </AuthShell>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 28px;
}
.form__error {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: var(--red-soft);
  color: var(--red);
  font-size: 12.5px;
}
.form__hint {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: var(--bg-subtle);
  color: var(--text-2);
  font-size: 12.5px;
  line-height: 1.5;
}
.form__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.linkish {
  background: none;
  border: 0;
  padding: 0;
  cursor: pointer;
}
</style>
