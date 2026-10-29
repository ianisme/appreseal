<script setup lang="ts">
import { ref } from 'vue'
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

const email = ref('')
const password = ref('')
const error = ref('')
const showPwd = ref(false)

function fill(kind: 'user' | 'admin') {
  email.value = kind === 'user' ? 'demo@appreseal.com' : 'admin@appreseal.com'
  password.value = kind === 'user' ? 'demo1234' : 'admin1234'
  error.value = ''
}

async function submit() {
  error.value = ''
  if (!email.value.trim() || !password.value) {
    error.value = '请填写邮箱和密码'
    return
  }
  try {
    const user = await auth.login(email.value, password.value)
    toast.success(`欢迎回来，${user.nickname}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    if (redirect) router.push(redirect)
    else router.push(user.role === 'ADMIN' ? '/admin/overview' : '/app/dashboard')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败'
  }
}
</script>

<template>
  <AuthShell title="登录 AppReseal" subtitle="继续你的脱壳任务与下载记录。">
    <form class="form" @submit.prevent="submit">
      <UiInput
        v-model="email"
        label="邮箱"
        placeholder="you@example.com"
        icon="mail"
        autocomplete="email"
        type="email"
      />

      <div class="field">
        <span class="field__label">密码</span>
        <div class="pwd">
          <input
            v-model="password"
            class="input"
            :type="showPwd ? 'text' : 'password'"
            placeholder="输入密码"
            autocomplete="current-password"
            style="padding-right: 42px"
          />
          <button type="button" class="pwd__toggle" :aria-label="showPwd ? '隐藏密码' : '显示密码'" @click="showPwd = !showPwd">
            <AppIcon :name="showPwd ? 'eye-off' : 'eye'" :size="17" />
          </button>
        </div>
      </div>

      <div v-if="error" class="form__error">
        <AppIcon name="alert" :size="15" /> {{ error }}
      </div>

      <UiButton type="submit" variant="primary" size="lg" block :loading="auth.loading">登录</UiButton>

      <div class="form__meta">
        <span class="tiny dim">还没有账号？<router-link to="/register">注册</router-link></span>
        <router-link to="/forgot" class="tiny dim">忘记密码？</router-link>
      </div>

      <div class="demo">
        <div class="demo__title">演示账号</div>
        <div class="demo__row">
          <button type="button" class="demo__btn" @click="fill('user')">
            <AppIcon name="user-check" :size="15" />
            <span>普通用户</span>
            <code>demo@appreseal.com</code>
          </button>
          <button type="button" class="demo__btn" @click="fill('admin')">
            <AppIcon name="shield" :size="15" />
            <span>管理员</span>
            <code>admin@appreseal.com</code>
          </button>
        </div>
        <p class="tiny dim" style="margin-top: 8px">点击自动填充，密码分别为 demo1234 / admin1234。</p>
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
.pwd {
  position: relative;
}
.pwd__toggle {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-3);
  display: grid;
  place-items: center;
  padding: 4px;
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: -2px;
}
.demo {
  margin-top: 12px;
  padding: 16px;
  border-radius: var(--r-md);
  background: var(--bg-subtle);
}
.demo__title {
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-3);
  margin-bottom: 10px;
}
.demo__row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.demo__btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 11px;
  border-radius: var(--r-sm);
  background: #fff;
  border: 1px solid var(--line-soft);
  font-size: 12.5px;
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.demo__btn:hover {
  border-color: var(--blue);
  box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.1);
}
.demo__btn code {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-3);
}
</style>
