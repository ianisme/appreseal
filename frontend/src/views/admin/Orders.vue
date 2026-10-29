<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import StatusPill from '@/components/StatusPill.vue'
import UiButton from '@/components/UiButton.vue'
import UiModal from '@/components/UiModal.vue'
import UiTable from '@/components/UiTable.vue'
import UiTabs from '@/components/UiTabs.vue'
import { api } from '@/api'
import { useToastStore } from '@/stores/toast'
import type { RechargeOrder, Transaction } from '@/types'
import { fmtDate, money } from '@/utils/format'

type OrderRow = RechargeOrder & { email: string }
type TxRow = Transaction & { email: string }

const toast = useToastStore()
const orders = ref<OrderRow[]>([])
const txs = ref<TxRow[]>([])
const loading = ref(true)
const tab = ref('ORDERS')
const q = ref('')
const status = ref('ALL')

const refundModal = ref(false)
const target = ref<OrderRow | null>(null)
const refunding = ref(false)
const confirmText = ref('')

const tabs = computed(() => [
  { value: 'ORDERS', label: '充值订单', count: orders.value.length },
  { value: 'TXS', label: '消费流水', count: txs.value.length }
])

const stats = computed(() => {
  const paid = orders.value.filter((o) => o.status === 'PAID')
  return {
    paid: paid.reduce((s, o) => s + o.amountCents + o.bonusCents, 0),
    refunded: orders.value.filter((o) => o.status === 'REFUNDED').length,
    pending: orders.value.filter((o) => o.status === 'PENDING').length,
    spend: txs.value.filter((t) => t.type === 'DECRYPT' || t.type === 'DOWNLOAD').reduce((s, t) => s + Math.abs(t.amountCents), 0)
  }
})

const filteredOrders = computed(() => {
  const key = q.value.trim().toLowerCase()
  return orders.value.filter((o) => {
    const matchKey = !key || o.email.toLowerCase().includes(key) || o.id.toLowerCase().includes(key)
    const matchStatus = status.value === 'ALL' || o.status === status.value
    return matchKey && matchStatus
  })
})

const filteredTxs = computed(() => {
  const key = q.value.trim().toLowerCase()
  return txs.value.filter((t) => {
    const matchKey =
      !key || t.email.toLowerCase().includes(key) || t.remark.toLowerCase().includes(key) || (t.refId ?? '').toLowerCase().includes(key)
    const matchStatus = status.value === 'ALL' || t.type === status.value
    return matchKey && matchStatus
  })
})

const orderColumns = [
  { key: 'id', label: '订单号', width: '115px' },
  { key: 'email', label: '用户', width: '180px' },
  { key: 'amount', label: '金额', width: '140px', align: 'right' as const },
  { key: 'bonus', label: '赠送', width: '95px', align: 'right' as const },
  { key: 'method', label: '支付方式', width: '110px' },
  { key: 'status', label: '状态', width: '100px' },
  { key: 'createdAt', label: '创建时间', width: '145px' },
  { key: 'actions', label: '', width: '90px', align: 'right' as const }
]

const txColumns = [
  { key: 'createdAt', label: '时间', width: '145px' },
  { key: 'email', label: '用户', width: '175px' },
  { key: 'type', label: '类型', width: '95px' },
  { key: 'remark', label: '说明' },
  { key: 'amount', label: '金额', width: '105px', align: 'right' as const },
  { key: 'balanceAfter', label: '余额', width: '105px', align: 'right' as const }
]

async function load() {
  loading.value = true
  try {
    const res = await api.adminOrders()
    orders.value = res.orders
    txs.value = res.transactions
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openRefund(row: OrderRow) {
  target.value = row
  confirmText.value = ''
  refundModal.value = true
}

async function doRefund() {
  if (!target.value) return
  refunding.value = true
  try {
    await api.adminRefundOrder(target.value.id)
    toast.success(`订单 ${target.value.id} 已退款`)
    refundModal.value = false
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '退款失败')
  } finally {
    refunding.value = false
  }
}

function switchTab(v: string) {
  tab.value = v
  status.value = 'ALL'
  q.value = ''
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">订单与流水</h2>
        <p class="page-sub">充值订单、消费与退款流水。退款会回收对应余额并写入审计日志。</p>
      </div>
      <div class="head-actions">
        <UiButton variant="secondary" :loading="loading" @click="load()"><AppIcon name="refresh" :size="15" /> 刷新</UiButton>
      </div>
    </div>

    <div class="o-stats">
      <div class="card o-stat">
        <span>已支付总额</span>
        <strong class="mono">{{ money(stats.paid) }}</strong>
        <span class="tiny dim">含赠送金额</span>
      </div>
      <div class="card o-stat">
        <span>平台消费</span>
        <strong class="mono">{{ money(stats.spend) }}</strong>
        <span class="tiny dim">脱壳 + 下载</span>
      </div>
      <div class="card o-stat">
        <span>待支付</span>
        <strong>{{ stats.pending }}</strong>
        <span class="tiny dim">15 分钟内有效</span>
      </div>
      <div class="card o-stat">
        <span>已退款订单</span>
        <strong>{{ stats.refunded }}</strong>
        <span class="tiny dim">由管理员操作</span>
      </div>
    </div>

    <div class="card" style="margin-top: 16px">
      <div style="padding: 0 20px">
        <UiTabs
          :model-value="tab"
          :items="tabs"
          @update:model-value="switchTab"
        />
      </div>

      <div class="toolbar">
        <div class="toolbar__search">
          <span class="toolbar__icon"><AppIcon name="search" :size="16" /></span>
          <input
            v-model="q"
            class="input"
            :placeholder="tab === 'ORDERS' ? '搜索订单号或用户邮箱' : '搜索用户、说明或关联单号'"
          />
        </div>
        <select v-if="tab === 'ORDERS'" v-model="status" class="select toolbar__select" aria-label="订单状态">
          <option value="ALL">全部状态</option>
          <option value="PAID">已支付</option>
          <option value="PENDING">待支付</option>
          <option value="REFUNDED">已退款</option>
        </select>
        <select v-else v-model="status" class="select toolbar__select" aria-label="流水类型">
          <option value="ALL">全部类型</option>
          <option value="RECHARGE">充值</option>
          <option value="DECRYPT">脱壳</option>
          <option value="DOWNLOAD">下载</option>
          <option value="REFUND">退款</option>
          <option value="ADJUST">调整</option>
        </select>
        <div class="toolbar__spacer" />
        <span class="toolbar__count">
          {{ tab === 'ORDERS' ? filteredOrders.length : filteredTxs.length }} 条记录
        </span>
      </div>

      <template v-if="tab === 'ORDERS'">
        <UiTable
          :columns="orderColumns"
          :rows="filteredOrders"
          :loading="loading"
          empty-title="没有匹配的订单"
          empty-desc="充值订单会在这里出现。"
        >
          <template #cell-id="{ row }"><span class="mono tiny">{{ row.id }}</span></template>
          <template #cell-email="{ row }"><span class="tiny">{{ row.email }}</span></template>
          <template #cell-amount="{ row }"><span class="mono small">{{ money(row.amountCents) }}</span></template>
          <template #cell-bonus="{ row }">
            <span class="mono tiny" :style="{ color: row.bonusCents ? 'var(--green)' : 'var(--text-3)' }">
              {{ row.bonusCents ? '+' + money(row.bonusCents) : '—' }}
            </span>
          </template>
          <template #cell-method="{ row }">
            <span class="pay-method">
              <span class="pay-method__dot" :style="{ background: row.method === 'ALIPAY' ? '#1677ff' : '#07c160' }" />
              {{ row.method === 'ALIPAY' ? '支付宝' : '微信' }}
            </span>
          </template>
          <template #cell-status="{ row }"><StatusPill :status="row.status" /></template>
          <template #cell-createdAt="{ row }"><span class="tiny dim">{{ fmtDate(row.createdAt) }}</span></template>
          <template #cell-actions="{ row }">
            <UiButton v-if="row.status === 'PAID'" variant="ghost" size="sm" @click="openRefund(row)">退款</UiButton>
            <span v-else class="tiny dim">—</span>
          </template>
        </UiTable>
      </template>

      <template v-else>
        <UiTable
          :columns="txColumns"
          :rows="filteredTxs"
          :loading="loading"
          empty-title="没有匹配的流水"
          empty-desc="所有余额变动都会记录在这里。"
        >
          <template #cell-createdAt="{ row }"><span class="tiny dim">{{ fmtDate(row.createdAt) }}</span></template>
          <template #cell-email="{ row }"><span class="tiny">{{ row.email }}</span></template>
          <template #cell-type="{ row }"><StatusPill :status="row.type" /></template>
          <template #cell-remark="{ row }">
            <div class="stack">
              <span class="small">{{ row.remark }}</span>
              <span v-if="row.refId" class="tiny dim mono">{{ row.refId }}</span>
            </div>
          </template>
          <template #cell-amount="{ row }">
            <span class="mono small" :style="{ color: row.amountCents > 0 ? 'var(--green)' : 'var(--text)' }">
              {{ row.amountCents > 0 ? '+' : '' }}{{ money(row.amountCents) }}
            </span>
          </template>
          <template #cell-balanceAfter="{ row }">
            <span class="mono tiny dim">{{ money(row.balanceAfterCents) }}</span>
          </template>
        </UiTable>
      </template>
    </div>

    <UiModal
      :open="refundModal"
      title="确认退款"
      :subtitle="target ? `${target.id} · ${target.email}` : ''"
      :closable="!refunding"
      @close="refundModal = false"
    >
      <div class="refund-box">
        <div class="refund-row"><span>充值金额</span><strong>{{ money(target?.amountCents ?? 0) }}</strong></div>
        <div class="refund-row"><span>赠送金额</span><strong>{{ money(target?.bonusCents ?? 0) }}</strong></div>
        <div class="refund-row"><span>将回收余额</span><strong style="color: var(--red)">
          {{ money((target?.amountCents ?? 0) + (target?.bonusCents ?? 0)) }}
        </strong></div>
        <div class="refund-row"><span>支付方式</span><span>{{ target?.method === 'ALIPAY' ? '支付宝' : '微信' }}</span></div>
      </div>

      <div class="note-box note-box--warn" style="margin-top: 16px">
        <AppIcon name="alert" :size="15" />
        <span>
          演示环境为余额冲正：若用户已消费导致余额不足，退款会失败。真实接入支付渠道后退回原路。
          请输入订单号后四位确认：<strong class="mono">{{ target?.id.slice(-4) }}</strong>
        </span>
      </div>

      <input
        v-model="confirmText"
        class="input"
        style="margin-top: 12px"
        :placeholder="`输入 ${target?.id.slice(-4)} 确认`"
      />

      <template #footer>
        <UiButton variant="secondary" :disabled="refunding" @click="refundModal = false">取消</UiButton>
        <UiButton
          variant="danger"
          :loading="refunding"
          :disabled="confirmText !== target?.id.slice(-4)"
          @click="doRefund"
        >
          确认退款
        </UiButton>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.o-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 18px;
}
.o-stat {
  padding: 15px 18px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.o-stat > span:first-child {
  font-size: 12.5px;
  color: var(--text-2);
}
.o-stat strong {
  font-size: 21px;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}
.pay-method {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
}
.pay-method__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.refund-box {
  border: 1px solid var(--line-soft);
  border-radius: var(--r-sm);
  padding: 4px 14px;
}
.refund-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 0;
  font-size: 13px;
  border-bottom: 1px solid var(--line-soft);
}
.refund-row:last-child {
  border-bottom: 0;
}
.refund-row span {
  color: var(--text-2);
}
@media (max-width: 1000px) {
  .o-stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
