<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import StatusPill from '@/components/StatusPill.vue'
import UiButton from '@/components/UiButton.vue'
import UiModal from '@/components/UiModal.vue'
import UiTable from '@/components/UiTable.vue'
import { api } from '@/api'
import { useToastStore } from '@/stores/toast'
import type { AppItem } from '@/types'
import { fmtDate, fmtNumber, fmtSize, money } from '@/utils/format'

const toast = useToastStore()
const rows = ref<AppItem[]>([])
const loading = ref(true)
const q = ref('')
const status = ref('ALL')
const busy = ref('')

const modal = ref(false)
const editing = ref<AppItem | null>(null)
const saving = ref(false)
const formError = ref('')

const CATEGORIES = ['社交', '影音', '工具', '效率', '开发', '游戏', '其他']

const blank = () => ({
  name: '',
  bundleId: '',
  developer: '',
  category: '工具',
  version: '1.0.0',
  executable: '',
  minIOS: '13.0',
  sizeMb: '',
  priceDownload: '3.00',
  priceDecrypt: '6.00',
  iconText: '',
  iconHue: 212,
  status: 'DRAFT' as AppItem['status'],
  description: ''
})

const form = ref(blank())

const filtered = computed(() => {
  const key = q.value.trim().toLowerCase()
  return rows.value.filter((r) => {
    const matchKey =
      !key || r.name.toLowerCase().includes(key) || r.bundleId.toLowerCase().includes(key) || r.developer.toLowerCase().includes(key)
    const matchStatus = status.value === 'ALL' || r.status === status.value
    return matchKey && matchStatus
  })
})

const stats = computed(() => ({
  total: rows.value.length,
  onSale: rows.value.filter((r) => r.status === 'ON_SALE').length,
  downloads: rows.value.reduce((s, r) => s + r.downloads, 0)
}))

const columns = [
  { key: 'app', label: '应用' },
  { key: 'category', label: '分类', width: '90px' },
  { key: 'version', label: '版本', width: '90px' },
  { key: 'size', label: '大小', width: '95px' },
  { key: 'price', label: '下载价', width: '95px', align: 'right' as const },
  { key: 'downloads', label: '下载', width: '85px', align: 'right' as const },
  { key: 'status', label: '状态', width: '95px' },
  { key: 'updatedAt', label: '更新时间', width: '125px' },
  { key: 'actions', label: '', width: '150px', align: 'right' as const }
]

const iconStyle = (hue: number, size = 38, fontSize = 14) => ({
  width: size + 'px',
  height: size + 'px',
  fontSize: fontSize + 'px',
  background: `linear-gradient(155deg, hsl(${hue} 82% 62%), hsl(${hue + 16} 76% 42%))`
})

async function load() {
  loading.value = true
  try {
    rows.value = await api.adminApps()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  editing.value = null
  form.value = blank()
  formError.value = ''
  modal.value = true
}

function openEdit(app: AppItem) {
  editing.value = app
  formError.value = ''
  form.value = {
    name: app.name,
    bundleId: app.bundleId,
    developer: app.developer,
    category: app.category,
    version: app.version,
    executable: app.executable,
    minIOS: app.minIOS,
    sizeMb: (app.sizeBytes / 1024 / 1024).toFixed(1),
    priceDownload: (app.priceDownloadCents / 100).toFixed(2),
    priceDecrypt: (app.priceDecryptCents / 100).toFixed(2),
    iconText: app.iconText,
    iconHue: app.iconHue,
    status: app.status,
    description: app.description
  }
  modal.value = true
}

async function save() {
  formError.value = ''
  const f = form.value
  if (!f.name.trim()) return (formError.value = '请填写应用名称')
  if (!f.bundleId.trim()) return (formError.value = '请填写 bundle id')
  if (!/^[A-Za-z0-9.-]+$/.test(f.bundleId.trim())) return (formError.value = 'bundle id 只能包含字母、数字、点与连字符')
  if (!f.developer.trim()) return (formError.value = '请填写开发者')
  if (!f.version.trim()) return (formError.value = '请填写版本号')
  const sizeMb = Number(f.sizeMb)
  if (!Number.isFinite(sizeMb) || sizeMb <= 0) return (formError.value = '请填写包体大小（MB）')

  saving.value = true
  try {
    await api.adminSaveApp({
      id: editing.value?.id,
      name: f.name.trim(),
      bundleId: f.bundleId.trim(),
      developer: f.developer.trim(),
      category: f.category,
      version: f.version.trim(),
      executable: f.executable.trim() || f.name.trim(),
      minIOS: f.minIOS.trim() || '13.0',
      sizeBytes: Math.round(sizeMb * 1024 * 1024),
      priceDownloadCents: 0,
      priceDecryptCents: 0,
      iconText: (f.iconText || f.name).trim().slice(0, 2),
      iconHue: f.iconHue,
      status: f.status,
      description: f.description.trim()
    })
    toast.success(editing.value ? '应用已更新' : '应用已创建')
    modal.value = false
    await load()
  } catch (e) {
    formError.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function toggleShelf(app: AppItem) {
  busy.value = app.id
  const next = app.status === 'ON_SALE' ? 'OFF_SHELF' : 'ON_SALE'
  try {
    await api.adminSetShelf(app.id, next)
    toast.success(next === 'ON_SALE' ? `${app.name} 已上架` : `${app.name} 已下架`)
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    busy.value = ''
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">应用库</h2>
        <p class="page-sub">管理成品库里的包。下架后前台不可见。</p>
      </div>
      <div class="head-actions">
        <UiButton variant="primary" @click="openCreate"><AppIcon name="plus" :size="16" /> 新建应用</UiButton>
      </div>
    </div>

    <div class="a-stats">
      <div class="card a-stat"><span>应用总数</span><strong>{{ stats.total }}</strong></div>
      <div class="card a-stat"><span>已上架</span><strong style="color: var(--green)">{{ stats.onSale }}</strong></div>
      <div class="card a-stat"><span>累计下载</span><strong>{{ fmtNumber(stats.downloads) }}</strong></div>
    </div>

    <div class="card" style="margin-top: 16px">
      <div class="toolbar">
        <div class="toolbar__search">
          <span class="toolbar__icon"><AppIcon name="search" :size="16" /></span>
          <input v-model="q" class="input" placeholder="搜索名称、开发者或 bundle id" />
        </div>
        <select v-model="status" class="select toolbar__select" aria-label="状态">
          <option value="ALL">全部状态</option>
          <option value="ON_SALE">在售</option>
          <option value="OFF_SHELF">已下架</option>
          <option value="DRAFT">草稿</option>
        </select>
        <div class="toolbar__spacer" />
        <span class="toolbar__count">{{ filtered.length }} 个应用</span>
      </div>

      <UiTable
        :columns="columns"
        :rows="filtered"
        :loading="loading"
        empty-title="还没有上架应用"
        empty-desc="新建应用，或从已完成任务把产物登记进应用库。"
      >
        <template #cell-app="{ row }">
          <div class="row gap-10">
            <div class="app-icon" :style="iconStyle(row.iconHue)">{{ row.iconText }}</div>
            <div class="stack" style="min-width: 0">
              <span style="font-weight: 500">{{ row.name }}</span>
              <span class="tiny dim mono ellipsis">{{ row.bundleId }}</span>
            </div>
          </div>
        </template>
        <template #cell-category="{ row }"><span class="pill">{{ row.category }}</span></template>
        <template #cell-version="{ row }"><span class="mono tiny">v{{ row.version }}</span></template>
        <template #cell-size="{ row }"><span class="tiny dim">{{ fmtSize(row.sizeBytes) }}</span></template>
        <template #cell-price="{ row }"><span class="mono small">{{ money(row.priceDownloadCents) }}</span></template>
        <template #cell-downloads="{ row }"><span class="mono small">{{ fmtNumber(row.downloads) }}</span></template>
        <template #cell-status="{ row }"><StatusPill :status="row.status" /></template>
        <template #cell-updatedAt="{ row }"><span class="tiny dim">{{ fmtDate(row.updatedAt, false) }}</span></template>
        <template #cell-actions="{ row }">
          <div class="table__actions">
            <UiButton variant="ghost" size="sm" @click="openEdit(row)">编辑</UiButton>
            <button
              class="shelf-btn"
              :class="{ 'is-off': row.status === 'ON_SALE' }"
              :disabled="busy === row.id"
              @click="toggleShelf(row)"
            >
              {{ row.status === 'ON_SALE' ? '下架' : '上架' }}
            </button>
          </div>
        </template>
      </UiTable>
    </div>

    <UiModal
      :open="modal"
      :title="editing ? '编辑应用' : '新建应用'"
      :subtitle="editing ? editing.bundleId : '手工上架一个已脱壳的成品'"
      :width="640"
      :closable="!saving"
      @close="modal = false"
    >
      <div class="app-preview">
        <div class="app-icon" :style="iconStyle(form.iconHue, 52, 19)">
          {{ (form.iconText || form.name || 'A').slice(0, 2) }}
        </div>
        <div>
          <div style="font-weight: 600; font-size: 15px">{{ form.name || '应用名称' }}</div>
          <div class="tiny dim">{{ form.developer || '开发者' }} · v{{ form.version || '1.0' }}</div>
        </div>
        <span class="pill" style="margin-left: auto">{{ form.status === 'ON_SALE' ? '上架' : form.status === 'DRAFT' ? '草稿' : '下架' }}</span>
      </div>

      <div class="form-grid-2" style="margin-top: 18px">
        <label class="field">
          <span class="field__label">应用名称 *</span>
          <input v-model="form.name" class="input" placeholder="例如 青笺" />
        </label>
        <label class="field">
          <span class="field__label">开发者 *</span>
          <input v-model="form.developer" class="input" placeholder="例如 青屿工作室" />
        </label>
        <label class="field">
          <span class="field__label">bundle id *</span>
          <input v-model="form.bundleId" class="input mono" placeholder="com.example.app" />
        </label>
        <label class="field">
          <span class="field__label">可执行文件名</span>
          <input v-model="form.executable" class="input mono" placeholder="默认与应用名一致" />
        </label>
        <label class="field">
          <span class="field__label">分类</span>
          <select v-model="form.category" class="select">
            <option v-for="c in CATEGORIES" :key="c" :value="c">{{ c }}</option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">版本 *</span>
          <input v-model="form.version" class="input" placeholder="8.0.54" />
        </label>
        <label class="field">
          <span class="field__label">最低 iOS</span>
          <input v-model="form.minIOS" class="input" placeholder="13.0" />
        </label>
        <label class="field">
          <span class="field__label">包体大小（MB）*</span>
          <input v-model="form.sizeMb" class="input" inputmode="decimal" placeholder="268" />
        </label>

        <label class="field">
          <span class="field__label">状态</span>
          <select v-model="form.status" class="select">
            <option value="ON_SALE">已上架</option>
            <option value="DRAFT">草稿</option>
            <option value="OFF_SHELF">已下架</option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">图标文字</span>
          <input v-model="form.iconText" class="input" maxlength="2" placeholder="取名称前两字" />
        </label>
      </div>

      <label class="field" style="margin-top: 16px">
        <span class="field__label">图标色相（{{ form.iconHue }}°）</span>
        <input v-model.number="form.iconHue" class="range" type="range" min="0" max="360" step="1" />
      </label>

      <label class="field" style="margin-top: 14px">
        <span class="field__label">简介</span>
        <textarea v-model="form.description" class="textarea" placeholder="一句话介绍这个 App 与适合分析的方向" />
      </label>

      <div v-if="formError" class="form-error">
        <AppIcon name="alert" :size="15" /> {{ formError }}
      </div>

      <template #footer>
        <UiButton variant="secondary" :disabled="saving" @click="modal = false">取消</UiButton>
        <UiButton variant="primary" :loading="saving" @click="save">{{ editing ? '保存修改' : '创建应用' }}</UiButton>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.a-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 18px;
}
.a-stat {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 15px 18px;
}
.a-stat span {
  font-size: 12.5px;
  color: var(--text-2);
}
.a-stat strong {
  font-size: 21px;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.shelf-btn {
  height: 32px;
  padding: 0 12px;
  border-radius: var(--r-pill);
  font-size: 13px;
  color: var(--blue);
  transition: background 0.15s;
}
.shelf-btn:hover {
  background: var(--blue-soft);
}
.shelf-btn.is-off {
  color: var(--text-2);
}
.shelf-btn.is-off:hover {
  background: var(--bg-subtle);
  color: var(--text);
}
.shelf-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.app-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: var(--r-md);
  background: var(--bg-subtle);
}
.range {
  width: 100%;
  accent-color: var(--blue);
  margin-top: 4px;
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
@media (max-width: 1000px) {
  .a-stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
