<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import UiButton from '@/components/UiButton.vue'
import UiInput from '@/components/UiInput.vue'
import UiModal from '@/components/UiModal.vue'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'

import { useToastStore } from '@/stores/toast'
import { resetDB } from '@/mock/db'
import { fmtDate } from '@/utils/format'

const auth = useAuthStore()

const toast = useToastStore()
const router = useRouter()

const COLORS = ['#0071e3', '#5e5ce6', '#ff375f', '#30d158', '#ff9f0a', '#64d2ff', '#bf5af2', '#1d1d1f']
const PREFS_KEY = 'appreseal.prefs.v1'

const nickname = ref(auth.user?.nickname ?? '')
const avatarColor = ref(auth.user?.avatarColor ?? COLORS[0])
const savingProfile = ref(false)

const oldPwd = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const pwdError = ref('')
const savingPwd = ref(false)

const prefs = ref({ taskDone: true, refund: true, product: false, invoice: true })
const savingPrefs = ref(false)

const resetModal = ref(false)
const resetText = ref('')
const resetting = ref(false)

const initials = computed(() => (auth.displayName || 'U').trim().slice(0, 1).toUpperCase())
const dirty = computed(
  () => nickname.value.trim() !== (auth.user?.nickname ?? '') || avatarColor.value !== (auth.user?.avatarColor ?? '')
)

onMounted(async () => {
  try {
    const raw = localStorage.getItem(PREFS_KEY)
    if (raw) prefs.value = { ...prefs.value, ...JSON.parse(raw) }
  } catch { /* ignore */ }
  if (!auth.user) await auth.bootstrap()
  nickname.value = auth.user?.nickname ?? ''
  avatarColor.value = auth.user?.avatarColor ?? COLORS[0]
})

async function saveProfile() {
  if (!nickname.value.trim()) {
    toast.error('昵称不能为空')
    return
  }
  savingProfile.value = true
  try {
    const user = await api.updateProfile({ nickname: nickname.value, avatarColor: avatarColor.value })
    auth.patch(user)
    toast.success('资料已更新')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    savingProfile.value = false
  }
}

async function savePassword() {
  pwdError.value = ''
  if (!oldPwd.value) return (pwdError.value = '请输入当前密码')
  if (newPwd.value.length < 8) return (pwdError.value = '新密码至少 8 位')
  if (newPwd.value !== confirmPwd.value) return (pwdError.value = '两次输入的新密码不一致')
  savingPwd.value = true
  try {
    await api.changePassword(oldPwd.value, newPwd.value)
    oldPwd.value = ''
    newPwd.value = ''
    confirmPwd.value = ''
    toast.success('密码已更新，其他设备需要重新登录')
  } catch (e) {
    pwdError.value = e instanceof Error ? e.message : '修改失败'
  } finally {
    savingPwd.value = false
  }
}

function toggle(key: keyof typeof prefs.value) {
  prefs.value[key] = !prefs.value[key]
  savingPrefs.value = true
  localStorage.setItem(PREFS_KEY, JSON.stringify(prefs.value))
  window.setTimeout(() => {
    savingPrefs.value = false
  }, 260)
}

async function logout() {
  await auth.logout()
  router.push('/')
}

async function doReset() {
  if (resetText.value !== 'RESET') return
  resetting.value = true
  try {
    resetDB()
    await auth.logout()
    toast.success('演示数据已重置')
    router.push('/login')
  } finally {
    resetting.value = false
    resetModal.value = false
    resetText.value = ''
  }
}

const notifyItems = [
  { key: 'taskDone' as const, title: '任务完成通知', desc: '脱壳成功或失败时发送站内提醒' },
  { key: 'refund' as const, title: '任务结果', desc: '任务失败或取消时通知我' },
  { key: 'product' as const, title: '产品动态', desc: '新功能和成品库更新' },
  { key: 'invoice' as const, title: '账单与发票', desc: '月度账单汇总与发票状态更新' }
]
</script>

<template>
  <div class="settings">
    <div class="page-head">
      <div>
        <h2 class="page-title">账号设置</h2>
        <p class="muted small" style="margin-top: 4px">管理你的资料、密码与通知偏好。</p>
      </div>
    </div>

    <div class="settings__grid">
      <div class="stack gap-16">
        <section class="card">
          <div class="card__head">
            <span class="card__title">个人资料</span>
            <span v-if="dirty" class="pill pill--orange">未保存</span>
          </div>
          <div class="card__body">
            <div class="profile">
              <div class="profile__avatar" :style="{ background: avatarColor }">{{ initials }}</div>
              <div class="profile__meta">
                <div class="profile__name">{{ auth.user?.nickname }}</div>
                <div class="tiny dim">{{ auth.user?.email }}</div>
                <div class="tiny dim" style="margin-top: 2px">
                  注册于 {{ fmtDate(auth.user?.createdAt, false) }} ·
                  {{ auth.isAdmin ? '管理员' : '普通用户' }}
                </div>
              </div>
            </div>

            <div class="form-grid" style="margin-top: 22px">
              <UiInput v-model="nickname" label="昵称" placeholder="怎么称呼你" hint="最多 24 个字符，会显示在控制台与订单中" />
              <label class="field">
                <span class="field__label">邮箱</span>
                <input class="input" :value="auth.user?.email" disabled />
                <span class="field__hint">邮箱作为登录凭证，暂不支持自助修改</span>
              </label>
            </div>

            <div class="field" style="margin-top: 18px">
              <span class="field__label">头像颜色</span>
              <div class="colors">
                <button
                  v-for="c in COLORS"
                  :key="c"
                  class="colors__dot"
                  :class="{ 'is-active': avatarColor === c }"
                  :style="{ background: c }"
                  :aria-label="`选择颜色 ${c}`"
                  @click="avatarColor = c"
                >
                  <AppIcon v-if="avatarColor === c" name="check" :size="13" />
                </button>
              </div>
            </div>

            <div class="row gap-10" style="margin-top: 22px">
              <UiButton variant="primary" :loading="savingProfile" :disabled="!dirty" @click="saveProfile">
                保存资料
              </UiButton>
              <UiButton
                variant="ghost"
                :disabled="!dirty"
                @click="
                  nickname = auth.user?.nickname ?? '';
                  avatarColor = auth.user?.avatarColor ?? COLORS[0]
                "
              >
                重置
              </UiButton>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card__head">
            <span class="card__title">修改密码</span>
            <span class="tiny dim">更新后其他设备需要重新登录</span>
          </div>
          <div class="card__body">
            <div class="form-grid">
              <label class="field">
                <span class="field__label">当前密码</span>
                <input v-model="oldPwd" class="input" type="password" autocomplete="current-password" placeholder="输入当前密码" />
              </label>
              <div />
              <label class="field">
                <span class="field__label">新密码</span>
                <input v-model="newPwd" class="input" type="password" autocomplete="new-password" placeholder="至少 8 位" />
              </label>
              <label class="field">
                <span class="field__label">确认新密码</span>
                <input v-model="confirmPwd" class="input" type="password" autocomplete="new-password" placeholder="再输入一次" />
              </label>
            </div>
            <div v-if="pwdError" class="form-error">
              <AppIcon name="alert" :size="15" /> {{ pwdError }}
            </div>
            <div class="row gap-10" style="margin-top: 18px">
              <UiButton variant="secondary" :loading="savingPwd" @click="savePassword">更新密码</UiButton>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card__head">
            <span class="card__title">通知偏好</span>
            <span v-if="savingPrefs" class="tiny dim">保存中…</span>
          </div>
          <div class="card__body" style="padding-top: 6px; padding-bottom: 6px">
            <div v-for="n in notifyItems" :key="n.key" class="switch-row">
              <div>
                <div class="switch-row__title">{{ n.title }}</div>
                <div class="switch-row__desc">{{ n.desc }}</div>
              </div>
              <button
                class="switch"
                :class="{ 'is-on': prefs[n.key] }"
                role="switch"
                :aria-checked="prefs[n.key]"
                :aria-label="n.title"
                @click="toggle(n.key)"
              />
            </div>
          </div>
        </section>

        <section class="card danger">
          <div class="card__head">
            <span class="card__title" style="color: var(--red)">危险操作</span>
          </div>
          <div class="card__body">
            <div class="danger__row">
              <div>
                <div class="danger__title">重置演示数据</div>
                <div class="danger__desc">
                  清空本机 localStorage 中的示例任务，恢复初始种子数据。不会影响真实后端数据。
                </div>
              </div>
              <UiButton variant="danger" size="sm" @click="resetModal = true">重置</UiButton>
            </div>
            <div class="divider" style="margin: 16px 0" />
            <div class="danger__row">
              <div>
                <div class="danger__title">注销账号</div>
                <div class="danger__desc">注销后任务记录和产物将无法恢复。当前暂未开放。</div>
              </div>
              <UiButton variant="secondary" size="sm" disabled>注销</UiButton>
            </div>
          </div>
        </section>
      </div>

      <aside class="stack gap-16">
        <div class="card card--pad">
          <div class="card__title">账号概览</div>
          <div class="side-rows">
            <div class="side-row"><span>账户类型</span><strong>{{ auth.isAdmin ? '管理员' : '标准账号' }}</strong></div>
            <div class="side-row"><span>并发上限</span><strong>2 个任务</strong></div>
            <div class="side-row"><span>费用</span><strong>不扣费</strong></div>
            <div class="side-row"><span>存储保留</span><strong>30 天</strong></div>
            <div class="side-row"><span>最近登录</span><span class="tiny dim">{{ fmtDate(auth.user?.lastLoginAt) }}</span></div>
          </div>

        </div>

        <div class="card card--pad">
          <div class="card__title">当前会话</div>
          <div class="session">
            <span class="session__icon"><AppIcon name="shield" :size="18" /></span>
            <div>
              <div class="session__title">本机浏览器</div>
              <div class="tiny dim">演示登录 · JWT 接入后显示 IP 与设备</div>
            </div>
          </div>
          <UiButton variant="secondary" size="sm" block style="margin-top: 16px" @click="logout">
            <AppIcon name="logout" :size="14" /> 退出登录
          </UiButton>
        </div>

        <div class="card card--pad tip">
          <AppIcon name="help" :size="18" />
          <div>
            <div class="tip__title">需要发票或企业采购？</div>
            <p>单笔满 ¥200 或月累计满 ¥500 可开票，邮件联系支持。</p>
            <a href="mailto:support@appreseal.com">support@appreseal.com</a>
          </div>
        </div>
      </aside>
    </div>

    <UiModal
      :open="resetModal"
      title="重置演示数据"
      subtitle="此操作不可撤销"
      :closable="!resetting"
      @close="resetModal = false"
    >
      <p class="small muted">
        会清空本机的任务和账号改动，恢复到初始演示数据，并退出登录。
        请输入 <strong class="mono">RESET</strong> 确认。
      </p>
      <input v-model="resetText" class="input" style="margin-top: 14px" placeholder="输入 RESET" />
      <template #footer>
        <UiButton variant="secondary" :disabled="resetting" @click="resetModal = false">取消</UiButton>
        <UiButton variant="danger" :loading="resetting" :disabled="resetText !== 'RESET'" @click="doReset">
          确认重置
        </UiButton>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.page-title {
  font-size: 22px;
  letter-spacing: -0.03em;
}
.settings__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  margin-top: 22px;
  align-items: start;
}
.profile {
  display: flex;
  align-items: center;
  gap: 16px;
}
.profile__avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 24px;
  font-weight: 600;
  flex: none;
}
.profile__name {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.02em;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.colors {
  display: flex;
  gap: 9px;
  margin-top: 2px;
}
.colors__dot {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  border: 2px solid transparent;
  transition: transform 0.15s, box-shadow 0.15s;
}
.colors__dot:hover {
  transform: scale(1.08);
}
.colors__dot.is-active {
  box-shadow: 0 0 0 2px #fff, 0 0 0 4px currentColor;
}
.form-error {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: var(--red-soft);
  color: var(--red);
  font-size: 12.5px;
}
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 14px 0;
  border-bottom: 1px solid var(--line-soft);
}
.switch-row:last-child {
  border-bottom: 0;
}
.switch-row__title {
  font-size: 13.5px;
  font-weight: 500;
}
.switch-row__desc {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 2px;
}
.danger {
  border-color: #f3d6d9;
}
.danger__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.danger__title {
  font-size: 13.5px;
  font-weight: 500;
}
.danger__desc {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 3px;
  max-width: 520px;
  line-height: 1.6;
}
.side-rows {
  margin-top: 14px;
}
.side-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px;
  padding: 7px 0;
  font-size: 12.5px;
  color: var(--text-2);
}
.side-row strong {
  color: var(--text);
  font-weight: 600;
}
.side-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 10px;
  font-size: 12.5px;
}
.session {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 14px;
}
.session__icon {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  background: var(--blue-soft);
  color: var(--blue);
  flex: none;
}
.session__title {
  font-size: 13px;
  font-weight: 500;
}
.tip {
  display: flex;
  gap: 12px;
  background: linear-gradient(150deg, #f7fbff, #fff);
  border-color: #dcebfb;
  color: var(--blue);
}
.tip__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}
.tip p {
  font-size: 12px;
  color: var(--text-2);
  line-height: 1.6;
  margin: 4px 0 6px;
}
.tip a {
  font-size: 12px;
}
@media (max-width: 1000px) {
  .settings__grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 620px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
  .danger__row {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
}
</style>
