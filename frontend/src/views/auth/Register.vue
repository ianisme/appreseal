<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthShell from './AuthShell.vue'
import UiInput from '@/components/UiInput.vue'
import UiButton from '@/components/UiButton.vue'
import AppIcon from '@/components/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'

import { useToastStore } from '@/stores/toast'

const auth = useAuthStore()

const toast = useToastStore()
const route = useRoute()
const router = useRouter()

const nickname = ref('')
const email = ref('')
const password = ref('')
const confirm = ref('')
const agree = ref(false)
const error = ref('')

const strength = computed(() => {
  const v = password.value
  let s = 0
  if (v.length >= 8) s++
  if (/[A-Z]/.test(v) && /[a-z]/.test(v)) s++
  if (/\d/.test(v) || /[^\w\s]/.test(v)) s++
  return s
})
const strengthLabel = computed(() => ['太短', '较弱', '一般', '良好'][strength.value])

async function submit() {
  error.value = ''
  if (!email.value.trim()) return (error.value = '请填写邮箱')
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value)) return (error.value = '邮箱格式不正确')
  if (password.value.length < 8) return (error.value = '密码至少 8 位')
  if (password.value !== confirm.value) return (error.value = '两次输入的密码不一致')
  if (!agree.value) return (error.value = '请阅读并同意服务协议与隐私政策')

  try {
    const user = await auth.register(email.value, password.value, nickname.value)

    toast.success(`注册成功，欢迎 ${user.nickname}`)
    const pkg = typeof route.query.pkg === 'string' ? route.query.pkg : ''
    router.push(pkg ? { path: '/app/wallet', query: { pkg } } : '/app/dashboard')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '注册失败'
  }
}
</script>

<template>
  <AuthShell title="创建账号" subtitle="给自己或团队用。提交任务不扣费。">
    <form class="form" @submit.prevent="submit">
      <UiInput v-model="nickname" label="昵称（选填）" placeholder="怎么称呼你" icon="users" />

      <UiInput
        v-model="email"
        label="邮箱"
        placeholder="you@example.com"
        icon="mail"
        type="email"
        autocomplete="email"
        :error="email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? '邮箱格式不正确' : ''"
      />

      <div class="field">
        <span class="field__label">密码</span>
        <input
          v-model="password"
          class="input"
          type="password"
          placeholder="至少 8 位，建议字母 + 数字"
          autocomplete="new-password"
        />
        <div v-if="password" class="strength">
          <div class="strength__bars">
            <span v-for="i in 3" :key="i" :class="{ 'is-on': strength >= i }" />
          </div>
          <span class="tiny dim">强度：{{ strengthLabel }}</span>
        </div>
      </div>

      <UiInput v-model="confirm" label="确认密码" placeholder="再输入一次" type="password" autocomplete="new-password" />

      <label class="agree">
        <input v-model="agree" type="checkbox" />
        <span>
          我已阅读并同意
          <router-link to="/docs#compliance">服务协议</router-link>
          与
          <router-link to="/docs#compliance">隐私政策</router-link>
        </span>
      </label>

      <div v-if="error" class="form__error">
        <AppIcon name="alert" :size="15" /> {{ error }}
      </div>

      <UiButton type="submit" variant="primary" size="lg" block :loading="auth.loading">创建账号</UiButton>

      <div class="form__meta">
        <span class="tiny dim">已有账号？<router-link to="/login">直接登录</router-link></span>
      </div>
    </form>
  </AuthShell>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 26px;
}
.strength {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 2px;
}
.strength__bars {
  display: flex;
  gap: 4px;
  flex: 1;
}
.strength__bars span {
  flex: 1;
  height: 3px;
  border-radius: 3px;
  background: var(--line-soft);
  transition: background 0.2s;
}
.strength__bars span.is-on {
  background: var(--green);
}
.agree {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  font-size: 12.5px;
  color: var(--text-2);
  line-height: 1.6;
}
.agree input {
  margin-top: 3px;
  accent-color: var(--blue);
  width: 15px;
  height: 15px;
  flex: none;
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
.form__meta {
  text-align: center;
  margin-top: -2px;
}
</style>
