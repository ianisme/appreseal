<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import UiButton from '@/components/UiButton.vue'
import UiModal from '@/components/UiModal.vue'
import UiTabs from '@/components/UiTabs.vue'
import UiTable from '@/components/UiTable.vue'
import StatusPill from '@/components/StatusPill.vue'
import { api } from '@/api'
import { useToastStore } from '@/stores/toast'
import { useWalletStore } from '@/stores/wallet'
import type { RechargeOrder, RechargePackage, Transaction } from '@/types'
import { fmtDate, money } from '@/utils/format'

const toast = useToastStore()
const wallet = useWalletStore()
const route = useRoute()

const packages = ref<RechargePackage[]>([])
const txs = ref<Transaction[]>([])
const loading = ref(true)
const tab = ref('ALL')
const busy = ref('')

const modal = ref(false)
const selected = ref<RechargePackage | null>(null)
const method = ref<'ALIPAY' | 'WECHAT'>('ALIPAY')
const order = ref<RechargeOrder | null>(null)
const paying = ref(false)
const paid = ref(false)
const countdown = ref(0)
let cdTimer: number | undefined

const tabs = computed(() => [
  { value: 'ALL', label: '全部', count: txs.value.length },
  { value: 'RECEIPT', label: '充值', count: txs.value.filter((t) => t.type === 'RECHARGE').length },
  { value: 'SPEND', label: '消费', count: txs.value.filter((t) => t.type === 'DECRYPT' || t.type === 'DOWNLOAD').length },
  { value: 'REFUND', label: '退款', count: txs.value.filter((t) => t.type === 'REFUND').length }
])

const filtered = computed(() => {
  if (tab.value === 'ALL') return txs.value
  if (tab.value === 'RECEIPT') return txs.value.filter((t) => t.type === 'RECHARGE')
  if (tab.value === 'SPEND') return txs.value.filter((t) => t.type === 'DECRYPT' || t.type === 'DOWNLOAD')
  return txs.value.filter((t) => t.type === 'REFUND')
})

const columns = [
  { key: 'createdAt', label: '时间', width: '150px' },
  { key: 'type', label: '类型', width: '90px' },
  { key: 'remark', label: '说明' },
  { key: 'amount', label: '金额', width: '110px', align: 'right' as const },
  { key: 'balance', label: '余额', width: '110px', align: 'right' as const }
]

onMounted(async () => {
  try {
    const [p, t] = await Promise.all([api.listPackages(), api.listTransactions()])
    packages.value = p
    txs.value = t
    await wallet.refresh()
    const pkgId = typeof route.query.pkg === 'string' ? route.query.pkg : ''
    const auto = p.find((x) => x.id === pkgId)
    if (auto) openRecharge(auto)
  } finally {
    loading.value = false
  }
})

function openRecharge(pkg: RechargePackage) {
  selected.value = pkg
  order.value = null
  paid.value = false
  paying.value = false
  method.value = 'ALIPAY'
  modal.value = true
}

async function createOrder() {
  if (!selected.value) return
  paying.value = true
  try {
    order.value = await api.createRecharge(selected.value.id, method.value)
    countdown.value = 300
    window.clearInterval(cdTimer)
    cdTimer = window.setInterval(() => {
      countdown.value = Math.max(0, countdown.value - 1)
      if (countdown.value === 0) window.clearInterval(cdTimer)
    }, 1000)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '创建订单失败')
  } finally {
    paying.value = false
  }
}

async function mockPay() {
  if (!order.value) return
  paying.value = true
  try {
    const res = await api.payRecharge(order.value.id)
    wallet.setBalance(res.balanceCents)
    paid.value = true
    toast.success(`充值成功，余额 ${money(res.balanceCents)}`)
    txs.value = await api.listTransactions()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '支付失败')
  } finally {
    paying.value = false
  }
}

function closeModal() {
  modal.value = false
  window.clearInterval(cdTimer)
}

const cdText = computed(() => {
  const m = Math.floor(countdown.value / 60)
  const s = String(countdown.value % 60).padStart(2, '0')
  return `${m}:${s}`
})
</script>

<template>
  <div class="wallet">
    <div class="page-head">
      <div>
        <h2 class="page-title">钱包</h2>
        <p class="muted small" style="margin-top: 4px">充值、消费与退款流水，全部可追溯。</p>
      </div>
      <UiButton variant="primary" @click="packages[0] && openRecharge(packages[0])">
        <AppIcon name="plus" :size="16" /> 充值
      </UiButton>
    </div>

    <div class="wallet__hero">
      <div class="wallet__hero-main">
        <div class="wallet__label">可用余额</div>
        <div class="wallet__balance">{{ money(wallet.balanceCents) }}</div>
        <div class="wallet__hint">
          余额长期有效 · 失败自动退回 · 可用于脱壳与成品下载
        </div>
        <div class="wallet__actions">
          <UiButton variant="primary" @click="packages[1] && openRecharge(packages[1])">立即充值</UiButton>
          <UiButton to="/market" variant="secondary">去市场消费</UiButton>
        </div>
      </div>
      <div class="wallet__hero-stats">
        <div class="wallet__stat">
          <span>累计充值</span>
          <strong>{{ money(wallet.totalRechargeCents) }}</strong>
        </div>
        <div class="wallet__stat">
          <span>累计消费</span>
          <strong>{{ money(wallet.totalSpendCents) }}</strong>
        </div>
        <div class="wallet__stat">
          <span>可脱壳次数</span>
          <strong>约 {{ Math.floor(wallet.balanceCents / 600) }} 次</strong>
        </div>
      </div>
    </div>

    <div class="section-title">
      <div>
        <h3 class="h3">充值套餐</h3>
        <p class="tiny dim" style="margin-top: 4px">充值金额与赠送金额立即到账，可用于所有服务。</p>
      </div>
    </div>

    <div class="pkg-grid">
      <button
        v-for="p in packages"
        :key="p.id"
        class="pkg card"
        :class="{ 'pkg--rec': p.recommended }"
        @click="openRecharge(p)"
      >
        <span v-if="p.recommended" class="pkg__badge">推荐</span>
        <div class="pkg__amount">{{ money(p.amountCents) }}</div>
        <div class="pkg__bonus">{{ p.bonusCents ? `送 ${money(p.bonusCents)}` : '无赠送' }}</div>
        <div class="pkg__total">到账 <strong>{{ money(p.amountCents + p.bonusCents) }}</strong></div>
      </button>
    </div>

    <div class="card" style="margin-top: 22px">
      <div style="padding: 0 22px">
        <UiTabs v-model="tab" :items="tabs" />
      </div>
      <UiTable
        :columns="columns"
        :rows="filtered"
        :loading="loading"
        empty-title="还没有流水"
        empty-desc="充值或发起一次脱壳后，明细会出现在这里。"
      >
        <template #cell-createdAt="{ row }">
          <span class="tiny dim">{{ fmtDate(row.createdAt) }}</span>
        </template>
        <template #cell-type="{ row }">
          <StatusPill :status="row.type" />
        </template>
        <template #cell-remark="{ row }">
          <div class="stack">
            <span class="small">{{ row.remark }}</span>
            <span v-if="row.refId" class="tiny dim mono">{{ row.refId }}</span>
          </div>
        </template>
        <template #cell-amount="{ row }">
          <span
            class="mono small"
            :style="{ color: row.amountCents > 0 ? 'var(--green)' : 'var(--text)' }"
          >
            {{ row.amountCents > 0 ? '+' : '' }}{{ money(row.amountCents) }}
          </span>
        </template>
        <template #cell-balance="{ row }">
          <span class="mono tiny dim">{{ money(row.balanceAfterCents) }}</span>
        </template>
      </UiTable>
    </div>

    <UiModal
      :open="modal"
      :title="paid ? '充值成功' : order ? '扫码支付' : '确认充值'"
      :subtitle="selected ? `${money(selected.amountCents)}${selected.bonusCents ? ` + 赠送 ${money(selected.bonusCents)}` : ''}` : ''"
      :closable="!paying"
      @close="closeModal"
    >
      <template v-if="paid">
        <div class="paid">
          <div class="paid__icon"><AppIcon name="check" :size="28" /></div>
          <div>
            <div class="paid__title">已到账 {{ money((order?.amountCents ?? 0) + (order?.bonusCents ?? 0)) }}</div>
            <div class="tiny dim">当前余额 {{ money(wallet.balanceCents) }}，可继续下单。</div>
          </div>
        </div>
      </template>

      <template v-else-if="!order">
        <div class="method">
          <button
            v-for="m in [
              { id: 'ALIPAY', label: '支付宝', tone: '#1677ff' },
              { id: 'WECHAT', label: '微信支付', tone: '#07c160' }
            ]"
            :key="m.id"
            class="method__item"
            :class="{ 'is-active': method === m.id }"
            @click="method = m.id as 'ALIPAY' | 'WECHAT'"
          >
            <span class="method__logo" :style="{ background: m.tone }">{{ m.label.slice(0, 1) }}</span>
            <span>{{ m.label }}</span>
            <span v-if="method === m.id" class="method__check"><AppIcon name="check" :size="15" /></span>
          </button>
        </div>
        <div class="pay-row"><span>充值金额</span><strong>{{ money(selected?.amountCents ?? 0) }}</strong></div>
        <div class="pay-row"><span>赠送金额</span><strong style="color: var(--green)">+{{ money(selected?.bonusCents ?? 0) }}</strong></div>
        <div class="pay-row"><span>实际到账</span><strong>{{ money((selected?.amountCents ?? 0) + (selected?.bonusCents ?? 0)) }}</strong></div>
        <p class="tiny dim" style="margin-top: 12px">演示环境不会发起真实扣款，点击下一步生成模拟订单。</p>
      </template>

      <template v-else>
        <div class="qr">
          <div class="qr__box">
            <div class="qr__grid">
              <span v-for="i in 121" :key="i" :class="{ 'is-on': (i * 7 + Math.floor(i / 11) * 3) % 5 < 2 }" />
            </div>
            <span class="qr__logo" :style="{ background: method === 'ALIPAY' ? '#1677ff' : '#07c160' }">
              {{ method === 'ALIPAY' ? '支' : '微' }}
            </span>
          </div>
          <div class="qr__amount">{{ money((order.amountCents ?? 0) + (order.bonusCents ?? 0)) }}</div>
          <div class="tiny dim">订单 {{ order.id }} · 有效期 {{ cdText }}</div>
          <p class="tiny dim" style="margin-top: 8px; text-align: center">
            请使用{{ method === 'ALIPAY' ? '支付宝' : '微信' }}扫码。演示环境可直接点击下方「模拟支付成功」。
          </p>
        </div>
      </template>

      <template #footer>
        <template v-if="paid">
          <UiButton variant="primary" @click="closeModal">完成</UiButton>
        </template>
        <template v-else-if="!order">
          <UiButton variant="secondary" @click="closeModal">取消</UiButton>
          <UiButton variant="primary" :loading="paying" @click="createOrder">生成支付订单</UiButton>
        </template>
        <template v-else>
          <UiButton variant="secondary" :disabled="paying" @click="closeModal">关闭</UiButton>
          <UiButton variant="primary" :loading="paying" @click="mockPay">模拟支付成功</UiButton>
        </template>
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
.wallet__hero {
  display: grid;
  grid-template-columns: 1.25fr 1fr;
  gap: 24px;
  margin-top: 20px;
  padding: 28px 30px;
  border-radius: var(--r-lg);
  background: linear-gradient(135deg, #101b2b 0%, #16233a 55%, #1c2c46 100%);
  color: #fff;
  position: relative;
  overflow: hidden;
}
.wallet__hero::after {
  content: "";
  position: absolute;
  width: 420px;
  height: 420px;
  right: -120px;
  top: -220px;
  background: radial-gradient(circle, rgba(0, 113, 227, 0.42), transparent 65%);
}
.wallet__label {
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.55);
}
.wallet__balance {
  font-size: 44px;
  font-weight: 700;
  letter-spacing: -0.045em;
  margin: 8px 0 6px;
  font-variant-numeric: tabular-nums;
}
.wallet__hint {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
}
.wallet__actions {
  display: flex;
  gap: 10px;
  margin-top: 22px;
  position: relative;
}
.wallet__actions :deep(.btn--secondary) {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.22);
  color: #fff;
}
.wallet__actions :deep(.btn--secondary:hover) {
  background: rgba(255, 255, 255, 0.16);
}
.wallet__hero-stats {
  display: flex;
  flex-direction: column;
  gap: 12px;
  justify-content: center;
  position: relative;
  padding-left: 24px;
  border-left: 1px solid rgba(255, 255, 255, 0.1);
}
.wallet__stat {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.55);
}
.wallet__stat strong {
  font-size: 17px;
  color: #fff;
  font-weight: 600;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}
.section-title {
  margin-top: 30px;
  margin-bottom: 14px;
}
.pkg-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.pkg {
  position: relative;
  padding: 20px 18px;
  text-align: left;
  transition: transform 0.2s var(--ease), box-shadow 0.2s var(--ease), border-color 0.2s;
}
.pkg:hover {
  transform: translateY(-2px);
  box-shadow: var(--sh-md);
  border-color: transparent;
}
.pkg--rec {
  border-color: var(--blue);
  box-shadow: 0 0 0 1px var(--blue);
}
.pkg__badge {
  position: absolute;
  top: -9px;
  right: 14px;
  background: var(--blue);
  color: #fff;
  font-size: 10.5px;
  font-weight: 600;
  padding: 2px 9px;
  border-radius: var(--r-pill);
}
.pkg__amount {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.03em;
}
.pkg__bonus {
  font-size: 12px;
  color: var(--green);
  min-height: 18px;
  margin-top: 2px;
}
.pkg__total {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line-soft);
  font-size: 11.5px;
  color: var(--text-3);
}
.pkg__total strong {
  color: var(--text);
}
.paid {
  display: flex;
  align-items: center;
  gap: 16px;
}
.paid__icon {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--green-soft);
  color: var(--green);
  flex: none;
}
.paid__title {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.02em;
}
.method {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 16px;
}
.method__item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  font-size: 13.5px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.method__item:hover {
  border-color: #b8b8bd;
}
.method__item.is-active {
  border-color: var(--blue);
  box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.1);
}
.method__logo {
  width: 24px;
  height: 24px;
  border-radius: 7px;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}
.method__check {
  margin-left: auto;
  color: var(--blue);
}
.pay-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 0;
  font-size: 13.5px;
  border-bottom: 1px solid var(--line-soft);
}
.pay-row:last-of-type {
  border-bottom: 0;
}
.pay-row span {
  color: var(--text-2);
}
.qr {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.qr__box {
  position: relative;
  width: 168px;
  height: 168px;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: #fff;
}
.qr__grid {
  display: grid;
  grid-template-columns: repeat(11, 1fr);
  gap: 3px;
  width: 100%;
  height: 100%;
}
.qr__grid span {
  border-radius: 1.5px;
  background: transparent;
}
.qr__grid span.is-on {
  background: #1d1d1f;
}
.qr__logo {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  border: 3px solid #fff;
}
.qr__amount {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.03em;
  margin-top: 4px;
}
@media (max-width: 980px) {
  .wallet__hero {
    grid-template-columns: 1fr;
    padding: 24px;
  }
  .wallet__hero-stats {
    padding-left: 0;
    padding-top: 18px;
    border-left: 0;
    border-top: 1px solid rgba(255, 255, 255, 0.1);
  }
  .pkg-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 520px) {
  .pkg-grid {
    grid-template-columns: 1fr;
  }
  .method {
    grid-template-columns: 1fr;
  }
}
</style>
