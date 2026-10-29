<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import StatusPill from '@/components/StatusPill.vue'
import UiButton from '@/components/UiButton.vue'
import UiInput from '@/components/UiInput.vue'
import UiModal from '@/components/UiModal.vue'
import UiTable from '@/components/UiTable.vue'
import { api } from '@/api'
import { useToastStore } from '@/stores/toast'
import type { AppleAccount } from '@/types'
import { STOREFRONTS, storefrontName } from '@/data/storefronts'
import { relTime } from '@/utils/format'

const toast = useToastStore()
const rows = ref<AppleAccount[]>([])
const loading = ref(true)
const syncing = ref(false)
const busy = ref('')
const loginOpen = ref(false)
const loginEmail = ref('')
const loginPassword = ref('')
const loginCode = ref('')
const needCode = ref(false)
const loggingIn = ref(false)
const loginError = ref('')
const loginMarket = ref('cn')
const credOpen = ref(false)
const credEmail = ref('')
const credFile = ref<File | null>(null)
const credError = ref('')
const credMarket = ref('cn')
const credBusy = ref(false)

const stats = computed(() => ({
  total: rows.value.length,
  ready: rows.value.filter((r) => r.status === 'READY').length,
  needLogin: rows.value.filter((r) => r.status === 'NEED_LOGIN').length,
  disabled: rows.value.filter((r) => r.status === 'DISABLED').length,
  unmarked: rows.value.filter((r) => !r.storefront).length
}))

const columns = [
  { key: 'email', label: 'Apple ID' },
  { key: 'storefront', label: '市场', width: '148px' },
  { key: 'priority', label: '优先级', width: '130px' },
  { key: 'credentialOk', label: '凭证', width: '95px' },
  { key: 'sessionOk', label: '会话', width: '95px' },
  { key: 'status', label: '状态', width: '100px' },
  { key: 'boundApps', label: '绑定 App', width: '95px', align: 'right' as const },
  { key: 'lastUsedAt', label: '最近使用', width: '120px' },
  { key: 'actions', label: '', width: '230px', align: 'right' as const }
]

const COMMANDS = [
  { label: '查看账号池状态', cmd: './appget.sh account list' },
  { label: '刷新指定账号会话', cmd: './appget.sh account login apple@example.com' },
  { label: '保存当前会话快照', cmd: './appget.sh account save' },
  { label: '调整账号优先级', cmd: './appget.sh account priority apple@example.com 1' }
]

async function load() {
  loading.value = true
  try {
    rows.value = await api.adminAppleAccounts()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function sync() {
  syncing.value = true
  try {
    rows.value = await api.adminSyncAppleAccounts()
    toast.success(`同步完成：${rows.value.filter((r) => r.status === 'READY').length} / ${rows.value.length} 可用`)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '同步失败')
  } finally {
    syncing.value = false
  }
}

async function toggle(row: AppleAccount) {
  busy.value = row.id
  const next = row.status === 'DISABLED' ? 'READY' : 'DISABLED'
  try {
    await api.adminAppleUpdate(row.id, { status: next })
    toast.success(next === 'DISABLED' ? `已禁用 ${row.email}` : `已启用 ${row.email}`)
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    busy.value = ''
  }
}

async function move(row: AppleAccount, delta: number) {
  const next = Math.max(1, row.priority + delta)
  busy.value = row.id
  try {
    await api.adminAppleUpdate(row.id, { priority: next })
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '调整失败')
  } finally {
    busy.value = ''
  }
}

function openLogin(row?: AppleAccount) {
  loginEmail.value = row?.email ?? ''
  loginMarket.value = row?.storefront || 'cn'
  loginPassword.value = ''
  loginCode.value = ''
  needCode.value = false
  loginError.value = ''
  loginOpen.value = true
}

function closeLogin() {
  loginOpen.value = false
  loginPassword.value = ''
  loginCode.value = ''
}

async function submitLogin() {
  loginError.value = ''
  if (!loginEmail.value.trim()) {
    loginError.value = '请输入 Apple ID'
    return
  }
  if (!loginPassword.value) {
    loginError.value = '请输入密码'
    return
  }
  if (needCode.value && !/^\d{6}$/.test(loginCode.value.trim())) {
    loginError.value = '双重验证码必须是 6 位数字'
    return
  }
  loggingIn.value = true
  try {
    const res = await api.adminAppleLogin(loginEmail.value.trim(), loginPassword.value, loginCode.value.trim() || undefined, loginMarket.value)
    if (res.status === 'NEED_2FA') {
      needCode.value = true
      loginError.value = res.message
      return
    }
    toast.success(res.message)
    closeLogin()
    await load()
  } catch (e) {
    loginError.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loggingIn.value = false
  }
}

function openCredential(row?: AppleAccount) {
  credEmail.value = row?.email ?? ''
  credMarket.value = row?.storefront || 'cn'
  credFile.value = null
  credError.value = ''
  credOpen.value = true
}

function onCredFile(e: Event) {
  const input = e.target as HTMLInputElement
  credFile.value = input.files?.[0] ?? null
  credError.value = ''
}

async function submitCredential() {
  credError.value = ''
  if (!credEmail.value.trim()) {
    credError.value = '请输入 Apple ID'
    return
  }
  if (!credFile.value) {
    credError.value = '请选择 credential.bin'
    return
  }
  if (credFile.value.size !== 16) {
    credError.value = 'credential.bin 必须是 16 字节'
    return
  }
  credBusy.value = true
  try {
    await api.adminBindCredential(credEmail.value.trim(), credFile.value, credMarket.value)
    toast.success('凭证已绑定')
    credOpen.value = false
    credFile.value = null
    await load()
  } catch (e) {
    credError.value = e instanceof Error ? e.message : '绑定失败'
  } finally {
    credBusy.value = false
  }
}

async function setMarket(row: AppleAccount, e: Event) {
  const storefront = (e.target as HTMLSelectElement).value
  if (!storefront || storefront === row.storefront) return
  busy.value = row.id
  try {
    await api.adminAppleUpdate(row.id, { storefront })
    toast.success(`已标记为${storefrontName(storefront)}`)
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '标记失败')
  } finally {
    busy.value = ''
  }
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    toast.success('命令已复制')
  } catch {
    toast.error('复制失败')
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">Apple ID 池</h2>
        <p class="page-sub">
          登录时标记 App Store 市场。新建任务选同一个市场，才会匹配到这个账号。
        </p>
      </div>
      <div class="head-actions">
        <UiButton variant="secondary" @click="openCredential()">
          <AppIcon name="upload" :size="15" /> 导入凭证
        </UiButton>
        <UiButton variant="primary" @click="openLogin()">
          <AppIcon name="key" :size="15" /> 登录 Apple ID
        </UiButton>
        <UiButton variant="secondary" :loading="syncing" @click="sync">
          <AppIcon name="refresh" :size="15" /> 同步状态
        </UiButton>
      </div>
    </div>

    <div class="aa-stats">
      <div class="card aa-stat">
        <span class="aa-stat__label">账号总数</span>
        <strong>{{ stats.total }}</strong>
      </div>
      <div class="card aa-stat">
        <span class="aa-stat__label">可用</span>
        <strong style="color: var(--green)">{{ stats.ready }}</strong>
      </div>
      <div class="card aa-stat">
        <span class="aa-stat__label">需重新登录</span>
        <strong :style="{ color: stats.needLogin ? 'var(--orange)' : '' }">{{ stats.needLogin }}</strong>
      </div>
      <div class="card aa-stat">
        <span class="aa-stat__label">已禁用</span>
        <strong class="dim">{{ stats.disabled }}</strong>
      </div>
    </div>

    <div v-if="stats.unmarked" class="note-box note-box--warn" style="margin-top: 14px">
      <AppIcon name="alert" :size="16" />
      <span>有 {{ stats.unmarked }} 个账号还没标记市场，新建任务时匹配不上。</span>
    </div>

    <div v-if="stats.needLogin" class="note-box note-box--warn" style="margin-top: 14px">
      <AppIcon name="alert" :size="16" />
      <span>
        有 {{ stats.needLogin }} 个账号还没有文件密钥。点「登录」填写密码和双重验证码，后台会在容器里生成密钥。
      </span>
    </div>

    <div class="card" style="margin-top: 16px">
      <div class="card__head">
        <span class="card__title">账号列表</span>
        <span class="tiny dim">优先级 1 最高 · 调度时按顺序尝试</span>
      </div>
      <UiTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty-title="账号池为空"
        empty-desc="用 fairplay-tools 添加 Apple ID 后再同步。"
      >
        <template #cell-email="{ row }">
          <div class="row gap-10">
            <span class="aa-icon"><AppIcon name="key" :size="15" /></span>
            <div class="stack">
              <span style="font-weight: 500">{{ row.email }}</span>
              <span class="tiny dim mono">{{ row.id }}</span>
            </div>
          </div>
        </template>
        <template #cell-storefront="{ row }">
          <select
            class="select market-select"
            :value="row.storefront || ''"
            :disabled="busy === row.id"
            aria-label="市场"
            @change="setMarket(row, $event)"
          >
            <option value="" disabled>未标记</option>
            <option v-for="m in STOREFRONTS" :key="m.code" :value="m.code">{{ m.name }}</option>
          </select>
        </template>
        <template #cell-priority="{ row }">
          <div class="priority">
            <span class="priority__value">{{ row.priority }}</span>
            <button class="icon-btn" aria-label="提高优先级" :disabled="busy === row.id || row.priority <= 1" @click="move(row, -1)">
              <AppIcon name="arrow-left" :size="13" style="transform: rotate(90deg)" />
            </button>
            <button class="icon-btn" aria-label="降低优先级" :disabled="busy === row.id" @click="move(row, 1)">
              <AppIcon name="arrow-right" :size="13" style="transform: rotate(90deg)" />
            </button>
          </div>
        </template>
        <template #cell-credentialOk="{ row }">
          <span class="pill" :class="row.credentialOk ? 'pill--green' : 'pill--red'">
            <span class="pill__dot" />{{ row.credentialOk ? '有效' : '缺失' }}
          </span>
        </template>
        <template #cell-sessionOk="{ row }">
          <span class="pill" :class="row.sessionOk ? 'pill--green' : 'pill--orange'">
            <span class="pill__dot" />{{ row.sessionOk ? '已登录' : '失效' }}
          </span>
        </template>
        <template #cell-status="{ row }"><StatusPill :status="row.status" /></template>
        <template #cell-boundApps="{ row }"><span class="mono small">{{ row.boundApps }}</span></template>
        <template #cell-lastUsedAt="{ row }"><span class="tiny dim">{{ relTime(row.lastUsedAt) }}</span></template>
        <template #cell-actions="{ row }">
          <div class="table__actions">
            <UiButton variant="ghost" size="sm" @click="openCredential(row)">凭证</UiButton>
            <UiButton variant="ghost" size="sm" @click="openLogin(row)">登录</UiButton>
            <UiButton
              variant="ghost"
              size="sm"
              :loading="busy === row.id"
              @click="toggle(row)"
            >
              {{ row.status === 'DISABLED' ? '启用' : '禁用' }}
            </UiButton>
          </div>
        </template>
      </UiTable>
    </div>

    <div class="content-grid content-grid--2">
      <div class="card">
        <div class="card__head card__head--tight">
          <span class="card__title">运维命令</span>
          <span class="tiny dim">在服务器 fairplay-tools 目录执行</span>
        </div>
        <div class="cmd-list">
          <div v-for="c in COMMANDS" :key="c.cmd" class="cmd-row">
            <div class="cmd-row__label">{{ c.label }}</div>
            <div class="cmd-row__line">
              <code>{{ c.cmd }}</code>
              <button class="icon-btn" aria-label="复制" @click="copy(c.cmd)"><AppIcon name="copy" :size="14" /></button>
            </div>
          </div>
        </div>
      </div>

      <div class="card card--pad">
        <div class="card__title">调度规则</div>
        <ul class="rules">
          <li><strong>全局串行：</strong>切换 Apple ID 会话有互斥锁，同一时刻只有一个任务能切换账号。</li>
          <li><strong>失败降级：</strong>当前账号下载失败会按优先级尝试下一个，全部失败才判定任务失败。</li>
          <li><strong>凭证绑定：</strong>credential.bin 必须与下载包所属 Apple ID 匹配，否则解密会报 SINF 错误。</li>
          <li><strong>文件密钥：</strong>后台登录成功后写到数据卷的 <code class="mono">.ipatool/account</code>，口令在 <code class="mono">.ipatool-passphrase</code>。密码不保存。</li>
        </ul>
        <div class="note-box" style="margin-top: 14px">
          <AppIcon name="shield" :size="15" />
          <span>真实凭证不落库。后台只记录路径与状态，避免把密码写进数据库。</span>
        </div>
      </div>
    </div>

    <UiModal :open="loginOpen" title="登录 Apple ID" subtitle="生成 Linux 文件密钥，供容器内下载使用" :width="440" @close="closeLogin">
      <form class="stack gap-14" @submit.prevent="submitLogin">
        <UiInput v-model="loginEmail" label="Apple ID" placeholder="name@example.com" autocomplete="off" icon="mail" />
        <label class="field">
          <span class="field__label">App Store 市场</span>
          <select v-model="loginMarket" class="select">
            <option v-for="m in STOREFRONTS" :key="m.code" :value="m.code">{{ m.name }}</option>
          </select>
        </label>
        <UiInput v-model="loginPassword" label="密码" type="password" placeholder="仅本次使用，不保存" autocomplete="new-password" icon="lock" />
        <UiInput
          v-if="needCode"
          v-model="loginCode"
          label="双重验证码"
          placeholder="6 位数字"
          autocomplete="one-time-code"
          icon="shield"
        />
        <p v-if="loginError" class="form-error">{{ loginError }}</p>
        <p class="tiny dim">登录只生成下载用的文件密钥。解密凭证用「导入凭证」单独绑定。</p>
        <div class="row gap-10" style="justify-content: flex-end">
          <UiButton type="button" variant="secondary" @click="closeLogin">取消</UiButton>
          <UiButton type="submit" variant="primary" :loading="loggingIn">{{ needCode ? '提交验证码' : '登录并生成密钥' }}</UiButton>
        </div>
      </form>
    </UiModal>

    <UiModal :open="credOpen" title="导入 credential.bin" subtitle="绑定到 Apple ID。只接受 16 字节文件，内容不会显示" :width="440" @close="credOpen = false">
      <form class="stack gap-14" @submit.prevent="submitCredential">
        <UiInput v-model="credEmail" label="Apple ID" placeholder="name@example.com" autocomplete="off" icon="mail" />
        <label class="field">
          <span class="field__label">App Store 市场</span>
          <select v-model="credMarket" class="select">
            <option v-for="m in STOREFRONTS" :key="m.code" :value="m.code">{{ m.name }}</option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">credential.bin</span>
          <input class="input" type="file" accept=".bin,application/octet-stream" @change="onCredFile" />
          <span class="tiny dim">{{ credFile ? `${credFile.name} · ${credFile.size} 字节` : '必须正好 16 字节' }}</span>
        </label>
        <p v-if="credError" class="form-error">{{ credError }}</p>
        <div class="row gap-10" style="justify-content: flex-end">
          <UiButton type="button" variant="secondary" @click="credOpen = false">取消</UiButton>
          <UiButton type="submit" variant="primary" :loading="credBusy">绑定</UiButton>
        </div>
      </form>
    </UiModal>
  </div>
</template>

<style scoped>
.aa-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 18px;
}
.aa-stat {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 15px 18px;
}
.aa-stat__label {
  font-size: 12.5px;
  color: var(--text-2);
}
.aa-stat strong {
  font-size: 22px;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}
.market-select {
  height: 32px;
  min-width: 124px;
  padding: 0 28px 0 10px;
  font-size: 13px;
}
.aa-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: var(--bg-subtle);
  color: var(--text-2);
  flex: none;
}
.priority {
  display: flex;
  align-items: center;
  gap: 2px;
}
.priority__value {
  width: 20px;
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  font-weight: 600;
}
.form-error {
  margin: 0;
  color: var(--red);
  font-size: 13px;
}
.priority .icon-btn {
  width: 24px;
  height: 24px;
}
.priority .icon-btn:disabled {
  opacity: 0.25;
  cursor: not-allowed;
}
.cmd-list {
  padding: 6px 18px 16px;
}
.cmd-row {
  padding: 11px 0;
  border-bottom: 1px solid var(--line-soft);
}
.cmd-row:last-child {
  border-bottom: 0;
}
.cmd-row__label {
  font-size: 11.5px;
  color: var(--text-3);
  margin-bottom: 5px;
}
.cmd-row__line {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: var(--r-xs);
  background: var(--bg-subtle);
}
.cmd-row__line code {
  flex: 1;
  font-size: 11.5px;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rules {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin-top: 12px;
}
.rules li {
  position: relative;
  padding-left: 16px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--text-2);
}
.rules li::before {
  content: "";
  position: absolute;
  left: 3px;
  top: 8px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--text-3);
}
.rules code {
  font-size: 11px;
  background: var(--bg-subtle);
  padding: 1px 4px;
  border-radius: 4px;
}
@media (max-width: 1000px) {
  .aa-stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
