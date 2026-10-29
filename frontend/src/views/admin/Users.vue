<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import StatusPill from '@/components/StatusPill.vue'
import UiButton from '@/components/UiButton.vue'
import UiModal from '@/components/UiModal.vue'
import UiPager from '@/components/UiPager.vue'
import UiTable from '@/components/UiTable.vue'
import { api } from '@/api'
import { useToastStore } from '@/stores/toast'
import type { User } from '@/types'
import { fmtDate, money, relTime } from '@/utils/format'

type Row = User & { balanceCents: number; tasks: number }

const toast = useToastStore()
const rows = ref<Row[]>([])
const loading = ref(true)
const q = ref('')
const role = ref('ALL')
const status = ref('ALL')
const page = ref(1)
const pages = ref(0)
const total = ref(0)
const summary = ref({ registered: 0, active: 0, banned: 0, balance: 0 })
const busy = ref('')
let searchTimer: number | undefined

const modal = ref(false)
const target = ref<Row | null>(null)
const mode = ref<'add' | 'sub'>('add')
const amount = ref('')
const remark = ref('')
const saving = ref(false)

const stats = computed(() => ({
  total: summary.value.registered,
  active: summary.value.active,
  banned: summary.value.banned,
  balance: summary.value.balance
}))

const columns = [
  { key: 'user', label: '用户' },
  { key: 'role', label: '角色', width: '100px' },
  { key: 'status', label: '状态', width: '100px' },

  { key: 'tasks', label: '任务', width: '80px', align: 'right' as const },
  { key: 'createdAt', label: '注册时间', width: '130px' },
  { key: 'lastLoginAt', label: '最近登录', width: '110px' },
  { key: 'actions', label: '', width: '232px', align: 'right' as const }
]

async function load() {
  loading.value = true
  try {
    const res = await api.adminUsers(q.value.trim(), role.value, status.value, page.value, 20)
    rows.value = res.items
    pages.value = res.pages
    total.value = res.total
    summary.value = {
      registered: res.registered,
      active: res.active,
      banned: res.banned,
      balance: res.balanceCents
    }
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

watch([role, status], () => {
  page.value = 1
  load()
})
watch(q, () => {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    page.value = 1
    load()
  }, 250)
})

onMounted(load)

function openAdjust(row: Row, m: 'add' | 'sub' = 'add') {
  target.value = row
  mode.value = m
  amount.value = ''
  remark.value = ''
  modal.value = true
}

async function saveAdjust() {
  if (!target.value) return
  const yuan = Number(amount.value)
  if (!Number.isFinite(yuan) || yuan <= 0) {
    toast.error('请输入大于 0 的金额')
    return
  }
  if (!remark.value.trim()) {
    toast.error('请填写调整原因')
    return
  }
  const cents = Math.round(yuan * 100) * (mode.value === 'add' ? 1 : -1)
  saving.value = true
  try {
    const balance = await api.adminAdjustBalance(target.value.id, cents, remark.value.trim())
    toast.success(`余额已调整为 ${money(balance)}`)
    modal.value = false
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '调整失败')
  } finally {
    saving.value = false
  }
}

async function toggleBan(row: Row) {
  busy.value = row.id
  const next = row.status === 'ACTIVE' ? 'BANNED' : 'ACTIVE'
  try {
    await api.adminUpdateUser(row.id, { status: next })
    toast.success(next === 'BANNED' ? `已封禁 ${row.email}` : `已解封 ${row.email}`)
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    busy.value = ''
  }
}

async function toggleRole(row: Row) {
  busy.value = row.id
  const next = row.role === 'ADMIN' ? 'USER' : 'ADMIN'
  try {
    await api.adminUpdateUser(row.id, { role: next })
    toast.success(next === 'ADMIN' ? `${row.email} 已设为管理员` : `${row.email} 已取消管理员`)
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    busy.value = ''
  }
}

const initials = (s: string) => (s || 'U').trim().slice(0, 1).toUpperCase()
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="page-sub">查询团队成员、调整角色和封禁。所有操作写入审计日志。</p>
      </div>
      <div class="head-actions">
        <span class="tiny dim">{{ total }} / {{ stats.total }} 位用户</span>
      </div>
    </div>

    <div class="mini-stats">
      <div class="card mini-stat"><span>注册用户</span><strong>{{ stats.total }}</strong></div>
      <div class="card mini-stat"><span>正常</span><strong style="color: var(--green)">{{ stats.active }}</strong></div>
      <div class="card mini-stat"><span>已封禁</span><strong style="color: var(--red)">{{ stats.banned }}</strong></div>

    </div>

    <div class="card" style="margin-top: 16px">
      <div class="toolbar">
        <div class="toolbar__search">
          <span class="toolbar__icon"><AppIcon name="search" :size="16" /></span>
          <input v-model="q" class="input" placeholder="搜索邮箱或昵称" />
        </div>
        <select v-model="role" class="select toolbar__select" aria-label="角色">
          <option value="ALL">全部角色</option>
          <option value="USER">普通用户</option>
          <option value="ADMIN">管理员</option>
        </select>
        <select v-model="status" class="select toolbar__select" aria-label="状态">
          <option value="ALL">全部状态</option>
          <option value="ACTIVE">正常</option>
          <option value="BANNED">已封禁</option>
        </select>
        <div class="toolbar__spacer" />

      </div>

      <UiTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty-title="没有匹配的用户"
        empty-desc="试试更换关键词或筛选条件。"
      >
        <template #cell-user="{ row }">
          <div class="row gap-10">
            <span class="avatar" :style="{ background: row.avatarColor }">{{ initials(row.nickname || row.email) }}</span>
            <div class="stack">
              <span style="font-weight: 500">{{ row.nickname }}</span>
              <span class="tiny dim">{{ row.email }}</span>
            </div>
          </div>
        </template>
        <template #cell-role="{ row }"><StatusPill :status="row.role" /></template>
        <template #cell-status="{ row }"><StatusPill :status="row.status" /></template>

        <template #cell-tasks="{ row }"><span class="mono small">{{ row.tasks }}</span></template>
        <template #cell-createdAt="{ row }"><span class="tiny dim">{{ fmtDate(row.createdAt, false) }}</span></template>
        <template #cell-lastLoginAt="{ row }"><span class="tiny dim">{{ relTime(row.lastLoginAt) }}</span></template>
        <template #cell-actions="{ row }">
          <div class="table__actions">

            <UiButton variant="ghost" size="sm" :loading="busy === row.id" @click="toggleRole(row)">
              {{ row.role === 'ADMIN' ? '降为用户' : '设为管理员' }}
            </UiButton>
            <button
              class="row-action"
              :class="{ 'is-danger': row.status === 'ACTIVE' }"
              :disabled="busy === row.id"
              @click="toggleBan(row)"
            >
              <AppIcon :name="row.status === 'ACTIVE' ? 'ban' : 'user-check'" :size="14" />
              {{ row.status === 'ACTIVE' ? '封禁' : '解封' }}
            </button>
          </div>
        </template>
      </UiTable>
      <UiPager :page="page" :pages="pages" :total="total" @update:page="(n) => { page = n; load() }" />
    </div>

    <UiModal
      :open="modal"
      title="调整余额"
      :subtitle="target ? `${target.nickname} · ${target.email}` : ''"
      @close="modal = false"
    >
      <div class="adj-current">
        <span>当前余额</span>
        <strong>{{ money(target?.balanceCents ?? 0) }}</strong>
      </div>

      <div class="segmented" style="margin-top: 14px">
        <button class="segmented__item" :class="{ 'is-active': mode === 'add' }" @click="mode = 'add'">增加余额</button>
        <button class="segmented__item" :class="{ 'is-active': mode === 'sub' }" @click="mode = 'sub'">扣减余额</button>
      </div>

      <label class="field" style="margin-top: 16px">
        <span class="field__label">金额（元）</span>
        <input v-model="amount" class="input" inputmode="decimal" placeholder="例如 20.00" />
        <div class="quick-amounts">
          <button v-for="v in [10, 20, 50, 100]" :key="v" class="chip" @click="amount = v.toFixed(2)">
            {{ mode === 'add' ? '+' : '-' }}{{ v }}
          </button>
        </div>
      </label>

      <label class="field" style="margin-top: 16px">
        <span class="field__label">调整原因（必填）</span>
        <input v-model="remark" class="input" placeholder="例如：任务失败补偿 / 线下转账充值" />
        <span class="field__hint">会写入审计日志，请描述清楚。</span>
      </label>

      <div class="note-box note-box--warn" style="margin-top: 16px">
        <AppIcon name="alert" :size="15" />
        <span>此操作直接修改用户余额，务必先核对线下收款或补偿依据。</span>
      </div>

      <template #footer>
        <UiButton variant="secondary" @click="modal = false">取消</UiButton>
        <UiButton
          :variant="mode === 'add' ? 'primary' : 'danger'"
          :loading="saving"
          :disabled="!amount || !remark.trim()"
          @click="saveAdjust"
        >
          确认{{ mode === 'add' ? '增加' : '扣减' }}
        </UiButton>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.mini-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 18px;
}
.mini-stat {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 18px;
}
.mini-stat span {
  font-size: 12.5px;
  color: var(--text-2);
}
.mini-stat strong {
  font-size: 21px;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}
.row-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 10px;
  border-radius: var(--r-pill);
  font-size: 13px;
  color: var(--text-2);
  white-space: nowrap;
  transition: background 0.15s, color 0.15s;
}
.row-action:hover {
  background: var(--bg-subtle);
  color: var(--text);
}
.row-action.is-danger:hover {
  background: var(--red-soft);
  color: var(--red);
}
.row-action:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.adj-current {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 14px 16px;
  border-radius: var(--r-sm);
  background: var(--bg-subtle);
  font-size: 13px;
  color: var(--text-2);
}
.adj-current strong {
  font-size: 22px;
  color: var(--text);
  letter-spacing: -0.03em;
}
.quick-amounts {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}
.quick-amounts .chip {
  height: 28px;
  font-size: 12px;
  padding: 0 12px;
}
@media (max-width: 900px) {
  .mini-stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
