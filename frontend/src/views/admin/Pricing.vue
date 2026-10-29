<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import UiButton from '@/components/UiButton.vue'
import { api } from '@/api'
import { useToastStore } from '@/stores/toast'
import type { AuditLog } from '@/types'
import { fmtDate, money } from '@/utils/format'

interface PkgForm {
  id: string
  amount: string
  bonus: string
  recommended: boolean
}

const toast = useToastStore()
const loading = ref(true)
const saving = ref(false)
const error = ref('')

const decrypt = ref('6.00')
const download = ref('3.00')
const concurrency = ref(2)
const packages = ref<PkgForm[]>([])
const logs = ref<AuditLog[]>([])

const snapshot = ref('')

const decryptCents = computed(() => Math.round(Number(decrypt.value || 0) * 100))
const downloadCents = computed(() => Math.round(Number(download.value || 0) * 100))
const packageTotal = computed(() => packages.value.length)

function signature() {
  return JSON.stringify({
    decrypt: decrypt.value,
    download: download.value,
    concurrency: concurrency.value,
    packages: packages.value
  })
}

const dirty = computed(() => signature() !== snapshot.value)

async function load() {
  loading.value = true
  try {
    const cfg = await api.adminPricing()
    decrypt.value = (cfg.decryptPriceCents / 100).toFixed(2)
    download.value = (cfg.downloadPriceCents / 100).toFixed(2)
    concurrency.value = cfg.concurrency
    packages.value = cfg.packages.map((p) => ({
      id: p.id,
      amount: (p.amountCents / 100).toFixed(2),
      bonus: (p.bonusCents / 100).toFixed(2),
      recommended: !!p.recommended
    }))
    snapshot.value = signature()
    logs.value = (await api.adminLogs('PRICING_UPDATE', '', 1, 4)).items
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function addPackage() {
  packages.value.push({
    id: `pkg_${Date.now().toString(36)}`,
    amount: '100.00',
    bonus: '0.00',
    recommended: false
  })
}

function removePackage(id: string) {
  packages.value = packages.value.filter((p) => p.id !== id)
}

function setRecommended(id: string) {
  packages.value = packages.value.map((p) => ({ ...p, recommended: p.id === id }))
}

async function save() {
  error.value = ''
  const d = Number(decrypt.value)
  const dl = Number(download.value)
  if (!Number.isFinite(d) || d < 0) return (error.value = '脱壳价格式不正确')
  if (!Number.isFinite(dl) || dl < 0) return (error.value = '下载价格式不正确')
  if (!Number.isFinite(concurrency.value) || concurrency.value < 1 || concurrency.value > 16) {
    return (error.value = '并发上限需在 1–16 之间')
  }
  if (!packages.value.length) return (error.value = '至少保留一个充值套餐')
  for (const p of packages.value) {
    const a = Number(p.amount)
    const b = Number(p.bonus)
    if (!Number.isFinite(a) || a <= 0) return (error.value = '套餐金额必须大于 0')
    if (!Number.isFinite(b) || b < 0) return (error.value = '赠送金额不能为负')
  }

  saving.value = true
  try {
    await api.adminSavePricing({
      decryptPriceCents: Math.round(d * 100),
      downloadPriceCents: Math.round(dl * 100),
      concurrency: Math.round(concurrency.value),
      packages: packages.value.map((p) => ({
        id: p.id,
        amountCents: Math.round(Number(p.amount) * 100),
        bonusCents: Math.round(Number(p.bonus) * 100),
        recommended: p.recommended
      }))
    })
    snapshot.value = signature()
    logs.value = (await api.adminLogs('PRICING_UPDATE', '', 1, 4)).items
    toast.success('定价已保存，立即生效')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

function reset() {
  load()
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">定价配置</h2>
        <p class="page-sub">默认价格对新建订单生效；已创建的任务按下单时的价格结算，不受影响。</p>
      </div>
      <div class="head-actions">
        <span v-if="dirty" class="pill pill--orange"><span class="pill__dot" /> 未保存</span>
        <UiButton v-else variant="secondary" size="sm" disabled>已是最新</UiButton>
      </div>
    </div>

    <div class="p-grid">
      <div class="stack gap-16">
        <section class="card">
          <div class="card__head">
            <span class="card__title">基础价格</span>
            <span class="tiny dim">单位：人民币元</span>
          </div>
          <div class="card__body">
            <div class="form-grid-3">
              <label class="field">
                <span class="field__label">默认脱壳价</span>
                <div class="money-input">
                  <span>¥</span>
                  <input v-model="decrypt" class="input" inputmode="decimal" />
                </div>
                <span class="field__hint">下单预扣，失败全额退</span>
              </label>
              <label class="field">
                <span class="field__label">默认成品下载价</span>
                <div class="money-input">
                  <span>¥</span>
                  <input v-model="download" class="input" inputmode="decimal" />
                </div>
                <span class="field__hint">购买后 30 天重复下载免费</span>
              </label>
              <label class="field">
                <span class="field__label">全局并发上限</span>
                <input v-model.number="concurrency" class="input" type="number" min="1" max="16" />
                <span class="field__hint">同时执行的脱壳任务数</span>
              </label>
            </div>

            <div class="note-box note-box--blue" style="margin-top: 18px">
              <AppIcon name="info" :size="15" />
              <span>
                单个 App 可在「应用库」中覆盖默认价格。默认价只影响没有单独定价的应用。
              </span>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card__head">
            <div>
              <span class="card__title">充值套餐</span>
              <div class="tiny dim" style="margin-top: 2px">共 {{ packageTotal }} 个套餐，用户端按此展示</div>
            </div>
            <UiButton variant="secondary" size="sm" @click="addPackage"><AppIcon name="plus" :size="14" /> 添加套餐</UiButton>
          </div>
          <div class="card__body" style="padding-top: 8px">
            <div v-for="(p, i) in packages" :key="p.id" class="pkg-row">
              <span class="pkg-index">{{ i + 1 }}</span>
              <label class="field" style="flex: 1">
                <span class="field__label">充值金额（元）</span>
                <input v-model="p.amount" class="input" inputmode="decimal" />
              </label>
              <label class="field" style="flex: 1">
                <span class="field__label">赠送金额（元）</span>
                <input v-model="p.bonus" class="input" inputmode="decimal" />
              </label>
              <div class="pkg-total">
                <span>到账</span>
                <strong>{{ money(Math.round((Number(p.amount) || 0) * 100) + Math.round((Number(p.bonus) || 0) * 100)) }}</strong>
              </div>
              <button
                class="rec-btn"
                :class="{ 'is-on': p.recommended }"
                @click="setRecommended(p.id)"
              >
                {{ p.recommended ? '推荐位' : '设为推荐' }}
              </button>
              <button class="icon-btn" aria-label="删除套餐" :disabled="packages.length <= 1" @click="removePackage(p.id)">
                <AppIcon name="trash" :size="16" />
              </button>
            </div>

            <div v-if="!packages.length" class="empty" style="padding: 30px 0">
              <div class="empty__title">还没有套餐</div>
              <div class="empty__desc">至少保留一个充值套餐。</div>
            </div>
          </div>
        </section>

        <div v-if="error" class="form-error">
          <AppIcon name="alert" :size="15" /> {{ error }}
        </div>

        <div class="row gap-10">
          <UiButton variant="primary" :loading="saving" :disabled="!dirty" @click="save">
            <AppIcon name="save" :size="15" /> 保存并立即生效
          </UiButton>
          <UiButton variant="ghost" :disabled="!dirty" @click="reset">放弃修改</UiButton>
        </div>
      </div>

      <aside class="stack gap-16">
        <div class="card preview">
          <div class="card__head card__head--tight">
            <span class="card__title">前台预览</span>
            <span class="pill">实时</span>
          </div>
          <div class="card__body">
            <div class="preview__item">
              <span>单次脱壳</span>
              <strong>{{ money(decryptCents) }}</strong>
            </div>
            <div class="preview__item">
              <span>成品下载</span>
              <strong>{{ money(downloadCents) }}</strong>
            </div>
            <div class="preview__item">
              <span>并发上限</span>
              <strong>{{ concurrency }} 个</strong>
            </div>
            <div class="divider" style="margin: 12px 0" />
            <div v-for="p in packages" :key="p.id" class="preview__pkg">
              <span>
                {{ money(Math.round((Number(p.amount) || 0) * 100)) }}
                <em v-if="Number(p.bonus) > 0">+{{ money(Math.round(Number(p.bonus) * 100)) }}</em>
              </span>
              <span v-if="p.recommended" class="pill pill--blue">推荐</span>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card__head card__head--tight">
            <span class="card__title">最近调价记录</span>
            <router-link to="/admin/logs" class="tiny">审计日志</router-link>
          </div>
          <div class="mini-list">
            <div v-for="l in logs" :key="l.id" class="mini-list__row">
              <span class="log-dot" />
              <div class="grow">
                <div class="ellipsis">{{ l.detail }}</div>
                <div class="tiny dim">{{ l.actor }} · {{ fmtDate(l.createdAt) }}</div>
              </div>
            </div>
            <div v-if="!logs.length" class="mini-list__row tiny dim">暂无调价记录</div>
          </div>
        </div>

        <div class="card card--pad">
          <div class="card__title">计费规则提醒</div>
          <ul class="rules">
            <li>余额单位存「分」，避免浮点误差。</li>
            <li>退款只退未消费余额，原路退回。</li>
            <li>改价不影响已下单任务与已购成品。</li>
            <li>赠送金额计入用户余额与充值流水。</li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.p-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 16px;
  margin-top: 18px;
  align-items: start;
}
.money-input {
  position: relative;
}
.money-input > span {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 13px;
  color: var(--text-3);
}
.money-input .input {
  padding-left: 30px;
  font-variant-numeric: tabular-nums;
}
.pkg-row {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line-soft);
}
.pkg-row:last-child {
  border-bottom: 0;
}
.pkg-index {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--bg-subtle);
  color: var(--text-3);
  font-size: 11px;
  display: grid;
  place-items: center;
  margin-bottom: 12px;
  flex: none;
}
.pkg-total {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 6px;
  min-width: 84px;
}
.pkg-total span {
  font-size: 10.5px;
  color: var(--text-3);
}
.pkg-total strong {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.rec-btn {
  height: 32px;
  padding: 0 11px;
  border-radius: var(--r-pill);
  border: 1px solid var(--line);
  font-size: 11.5px;
  color: var(--text-2);
  margin-bottom: 5px;
  white-space: nowrap;
  transition: all 0.15s;
}
.rec-btn.is-on {
  border-color: var(--blue);
  background: var(--blue-soft);
  color: var(--blue);
  font-weight: 500;
}
.pkg-row .icon-btn {
  margin-bottom: 5px;
}
.pkg-row .icon-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.preview__item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  font-size: 12.5px;
  color: var(--text-2);
}
.preview__item strong {
  font-size: 17px;
  color: var(--text);
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}
.preview__pkg {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 5px 0;
  font-size: 12.5px;
}
.preview__pkg em {
  font-style: normal;
  color: var(--green);
  font-size: 11.5px;
  margin-left: 3px;
}
.log-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--blue);
  flex: none;
}
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rules {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}
.rules li {
  position: relative;
  padding-left: 16px;
  font-size: 12px;
  line-height: 1.6;
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
.form-error {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: var(--red-soft);
  color: var(--red);
  font-size: 12.5px;
}
@media (max-width: 1080px) {
  .p-grid {
    grid-template-columns: 1fr;
  }
  .pkg-row {
    flex-wrap: wrap;
  }
}
</style>
