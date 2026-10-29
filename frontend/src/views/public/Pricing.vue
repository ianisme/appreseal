<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import UiButton from '@/components/UiButton.vue'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { RechargePackage } from '@/types'
import { money } from '@/utils/format'

const auth = useAuthStore()
const router = useRouter()
const packages = ref<RechargePackage[]>([])

onMounted(async () => {
  packages.value = await api.listPackages()
})

function choose(pkg: RechargePackage) {
  if (!auth.isLoggedIn) {
    router.push({ path: '/register', query: { pkg: pkg.id } })
    return
  }
  router.push({ path: '/app/wallet', query: { pkg: pkg.id } })
}

const fees = [
  { item: '单次脱壳', price: '¥6.00 起', note: '按 App 单独定价；下单预扣，失败或取消全额退回' },
  { item: '成品下载', price: '¥3.00 起', note: '购买后 30 天内可重复下载，不再扣费' },
  { item: '脱壳首下', price: '免费', note: '任务成功后 24 小时内首次下载不扣费' },
  { item: '并发任务', price: '2 个', note: '普通账号同时最多 2 个；企业可申请提升' },
  { item: '存储保留', price: '30 天', note: '到期前请自行转存，过期产物会被清理' }
]

const faqs = [
  { q: '余额会过期吗？', a: '不会。充值余额长期有效，可用于脱壳与下载，也支持按未消费余额申请退款。' },
  { q: '失败怎么退款？', a: '任务失败，或你在排队、下载、解密过程中取消，系统都会把预扣金额退回余额，并在「钱包 → 流水」与任务详情中留下记录。' },
  { q: '可以开发票吗？', a: '可以。单笔充值满 ¥200 或月度累计满 ¥500，可联系 support@appreseal.com 开具增值税普通发票。' },
  { q: '下载过的 App 重新下载还收费吗？', a: '不收费。购买任一成品后，30 天内可在「我的应用」免费重复下载；超过 30 天按当前价格重新购买。' }
]
</script>

<template>
  <div class="pricing">
    <section class="pricing__head">
      <div class="container">
        <div class="eyebrow">PRICING</div>
        <h1 class="h1 pricing__title">按次计费，充多少用多少。</h1>
        <p class="lead pricing__lead">
          没有订阅、没有最低消费。失败自动退回，余额永久有效。
        </p>
      </div>
    </section>

    <section class="section--tight">
      <div class="container">
        <div class="pkgs">
          <button
            v-for="p in packages"
            :key="p.id"
            class="pkg card"
            :class="{ 'pkg--rec': p.recommended }"
            @click="choose(p)"
          >
            <span v-if="p.recommended" class="pkg__badge">最受欢迎</span>
            <div class="pkg__amount">{{ money(p.amountCents) }}</div>
            <div class="pkg__bonus">
              {{ p.bonusCents ? `赠送 ${money(p.bonusCents)}` : '无赠送' }}
            </div>
            <div class="pkg__total">到账 {{ money(p.amountCents + p.bonusCents) }}</div>
            <div class="pkg__cta">选择此套餐</div>
          </button>
        </div>
        <p class="tiny dim" style="text-align: center; margin-top: 18px">
          支持支付宝 / 微信扫码；企业采购请联系 support@appreseal.com
        </p>
      </div>
    </section>

    <section class="section section--gray">
      <div class="container">
        <div class="section-head">
          <div>
            <div class="eyebrow">BILLING</div>
            <h2 class="h2" style="margin-top: 10px">计费明细</h2>
          </div>
        </div>
        <div class="card fee-table">
          <table class="table">
            <thead>
              <tr><th>项目</th><th>价格</th><th>说明</th></tr>
            </thead>
            <tbody>
              <tr v-for="f in fees" :key="f.item">
                <td style="font-weight: 500">{{ f.item }}</td>
                <td class="mono" style="white-space: nowrap">{{ f.price }}</td>
                <td class="muted">{{ f.note }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <section class="section" id="refund">
      <div class="container rules">
        <div>
          <div class="eyebrow">RULES</div>
          <h2 class="h2" style="margin-top: 10px">扣费与退款规则</h2>
          <p class="lead" style="margin-top: 14px; max-width: 560px">
            规则尽量简单：下单预扣，失败退回，成功计费。
          </p>
          <div class="rule-list">
            <div class="rule">
              <span class="rule__icon"><AppIcon name="credit-card" :size="18" /></span>
              <div>
                <strong>下单预扣</strong>
                <p>创建任务时扣除该 App 的脱壳价，余额不足会引导充值，不会产生欠费。</p>
              </div>
            </div>
            <div class="rule">
              <span class="rule__icon"><AppIcon name="rotate-ccw" :size="18" /></span>
              <div>
                <strong>失败全额退</strong>
                <p>下载失败、凭证不匹配、超时，或你在处理过程中取消，都会把预扣金额退回余额。</p>
              </div>
            </div>
            <div class="rule">
              <span class="rule__icon"><AppIcon name="download" :size="18" /></span>
              <div>
                <strong>下载计费</strong>
                <p>市场成品按 App 定价，购买后 30 天重复下载免费；自己脱壳的产物 24 小时内首下免费。</p>
              </div>
            </div>
            <div class="rule">
              <span class="rule__icon"><AppIcon name="shield" :size="18" /></span>
              <div>
                <strong>余额退款</strong>
                <p>未消费余额可申请原路退回，3 个工作日内处理；已消费部分不支持退款。</p>
              </div>
            </div>
          </div>
        </div>

        <div class="faq-box card card--pad">
          <h3 class="h3">常见问题</h3>
          <details v-for="f in faqs" :key="f.q" class="mini-faq">
            <summary>{{ f.q }}<AppIcon name="chevron-down" :size="16" /></summary>
            <p>{{ f.a }}</p>
          </details>
        </div>
      </div>
    </section>

    <section class="section section--gray">
      <div class="container">
        <div class="cta-row">
          <div>
            <h2 class="h2">准备好解第一个包了？</h2>
            <p class="muted" style="margin-top: 10px">注册后充值任意金额即可开始，失败不花钱。</p>
          </div>
          <div class="row gap-12">
            <UiButton to="/register" variant="primary" size="lg">免费注册</UiButton>
            <UiButton to="/market" variant="secondary" size="lg">浏览市场</UiButton>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.pricing__head {
  padding: 72px 0 20px;
  text-align: center;
}
.pricing__title {
  margin-top: 14px;
  font-size: clamp(32px, 4.4vw, 50px);
}
.pricing__lead {
  margin: 16px auto 0;
  max-width: 540px;
}
.pkgs {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.pkg {
  position: relative;
  padding: 30px 22px 24px;
  text-align: center;
  cursor: pointer;
  transition: transform 0.2s var(--ease), box-shadow 0.2s var(--ease);
}
.pkg:hover {
  transform: translateY(-3px);
  box-shadow: var(--sh-md);
}
.pkg--rec {
  border-color: var(--blue);
  box-shadow: 0 0 0 1px var(--blue), var(--sh-sm);
}
.pkg__badge {
  position: absolute;
  top: -11px;
  left: 50%;
  transform: translateX(-50%);
  background: var(--blue);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: var(--r-pill);
  white-space: nowrap;
}
.pkg__amount {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.04em;
}
.pkg__bonus {
  font-size: 12.5px;
  color: var(--green);
  margin-top: 6px;
  min-height: 20px;
}
.pkg__total {
  font-size: 12.5px;
  color: var(--text-2);
  margin-top: 6px;
  padding-top: 14px;
  border-top: 1px solid var(--line-soft);
}
.pkg__cta {
  margin-top: 16px;
  font-size: 12.5px;
  color: var(--blue);
  font-weight: 500;
}
.fee-table {
  overflow: hidden;
}
.fee-table .table th,
.fee-table .table td {
  padding-left: 24px;
  padding-right: 24px;
}
.rules {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 64px;
  align-items: start;
}
.rule-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 26px;
  margin-top: 34px;
}
.rule {
  display: flex;
  gap: 14px;
}
.rule__icon {
  width: 38px;
  height: 38px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: var(--blue-soft);
  color: var(--blue);
}
.rule strong {
  font-size: 14px;
  font-weight: 600;
}
.rule p {
  margin-top: 5px;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--text-2);
}
.faq-box {
  position: sticky;
  top: 88px;
}
.mini-faq {
  border-bottom: 1px solid var(--line-soft);
}
.mini-faq:last-child {
  border-bottom: 0;
}
.mini-faq summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  list-style: none;
}
.mini-faq summary::-webkit-details-marker {
  display: none;
}
.mini-faq[open] summary :deep(svg) {
  transform: rotate(180deg);
}
.mini-faq p {
  padding-bottom: 14px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text-2);
}
.cta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}
@media (max-width: 1000px) {
  .pkgs { grid-template-columns: repeat(2, 1fr); }
  .rules { grid-template-columns: 1fr; gap: 36px; }
  .faq-box { position: static; }
}
@media (max-width: 560px) {
  .pkgs { grid-template-columns: 1fr; }
  .rule-list { grid-template-columns: 1fr; }
}
</style>
