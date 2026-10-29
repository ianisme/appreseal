<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import UiButton from '@/components/UiButton.vue'
import { api } from '@/api'
import { useToastStore } from '@/stores/toast'
import type { MaintenanceInfo, Settings } from '@/types'

const toast = useToastStore()
const loading = ref(true)
const saving = ref(false)
const cleaning = ref(false)
const form = ref<Settings | null>(null)
const maintenance = ref<MaintenanceInfo | null>(null)
const snapshot = ref('')

const dirty = computed(() => !!form.value && JSON.stringify(form.value) !== snapshot.value)

const disk = computed(() => {
  const d = maintenance.value?.disk
  if (!d || !d.totalBytes) {
    return { text: '—', percent: 0, level: 'UNKNOWN' as const }
  }
  return {
    text: `${gb(d.usedBytes)} / ${gb(d.totalBytes)}`,
    percent: d.usedPercent,
    level: d.level
  }
})

const diskTone = computed(() => (disk.value.level === 'CRITICAL' ? 'var(--red)' : disk.value.level === 'WARN' ? 'var(--orange)' : 'var(--green)'))

const lastRunText = computed(() => {
  const r = maintenance.value?.lastRun
  if (!r?.finishedAt) return '还没有执行过清理'
  const at = new Date(r.finishedAt).toLocaleString('zh-CN', { hour12: false })
  return `${at} · 产物 ${r.artifactsDeleted} 个 · 上传包 ${r.uploadsDeleted} 个 · 日志 ${r.logsDeleted} 条`
})

function gb(bytes: number) {
  const value = bytes / 1024 ** 3
  return `${value >= 10 ? value.toFixed(0) : value.toFixed(1)} GB`
}

function human(bytes: number) {
  if (bytes < 1024 ** 2) return `${Math.round(bytes / 1024)} KB`
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(0)} MB`
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`
}

async function load() {
  loading.value = true
  try {
    const [s, m] = await Promise.all([api.adminSettings(), api.adminMaintenance()])
    form.value = { ...s }
    maintenance.value = m
    snapshot.value = JSON.stringify(form.value)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function cleanup() {
  cleaning.value = true
  try {
    const r = await api.adminCleanup()
    const freed = r.artifactBytesFreed + r.uploadBytesFreed + r.orphanBytesFreed
    toast.success(`清理完成：产物 ${r.artifactsDeleted} 个、上传包 ${r.uploadsDeleted} 个，释放 ${human(freed)}`)
    maintenance.value = await api.adminMaintenance()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '清理失败')
  } finally {
    cleaning.value = false
  }
}

onMounted(load)

async function save() {
  if (!form.value) return
  if (form.value.concurrency < 1 || form.value.concurrency > 8) {
    toast.error('并发上限需在 1–8 之间')
    return
  }
  if (form.value.freeDownloadDays < 0 || form.value.freeDownloadDays > 365) {
    toast.error('免费下载期需在 0–365 天之间')
    return
  }
  saving.value = true
  try {
    await api.adminSaveSettings({ ...form.value })
    snapshot.value = JSON.stringify(form.value)
    toast.success('系统设置已保存')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

const services = [
  { name: 'API 服务', desc: 'Spring Boot · /api/v1', status: '正常', ok: true, meta: '响应 42ms' },
  { name: 'MySQL', desc: '主库 · 8.0', status: '正常', ok: true, meta: '连接 12 / 100' },
  { name: '任务执行器', desc: '线程池 · 串行下载', status: '正常', ok: true, meta: '2 个 worker' },
  { name: 'Apple ID 池', desc: 'fairplay-tools', status: '3 / 4 可用', ok: true, meta: '1 个需登录' },
  { name: '对象存储', desc: '本地磁盘 / S3 兼容', status: '正常', ok: true, meta: '已用 18.4 GB' },
  { name: '计费', desc: '团队自用', status: '已关闭', ok: true, meta: '不扣费' }
]

</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">系统设置</h2>
        <p class="page-sub">维护模式、公告、并发与存储策略。保存后立即对前台生效。</p>
      </div>
      <div class="head-actions">
        <span v-if="dirty" class="pill pill--orange"><span class="pill__dot" /> 有未保存修改</span>
        <UiButton variant="primary" :loading="saving" :disabled="!dirty" @click="save">
          <AppIcon name="save" :size="15" /> 保存设置
        </UiButton>
      </div>
    </div>

    <div v-if="form?.maintenance" class="maint-banner">
      <AppIcon name="alert" :size="16" />
      <div>
        <strong>维护模式已开启</strong>
        <span>前台将显示公告条，用户无法提交新任务；管理后台不受影响。</span>
      </div>
    </div>

    <div class="s-grid">
      <div class="stack gap-16">
        <section class="card">
          <div class="card__head">
            <span class="card__title">运行状态</span>
            <span class="pill pill--green pill--live"><span class="pill__dot" /> 运行中</span>
          </div>
          <div class="card__body" style="padding-top: 8px; padding-bottom: 8px">
            <div class="switch-field">
              <div>
                <div class="switch-field__title">维护模式</div>
                <div class="switch-field__desc">
                  开启后前台显示维护公告，新建任务会被拒绝；已排队任务继续执行。
                </div>
              </div>
              <button
                v-if="form"
                class="switch"
                :class="{ 'is-on': form.maintenance }"
                role="switch"
                :aria-checked="form.maintenance"
                aria-label="维护模式"
                @click="form.maintenance = !form.maintenance"
              />
            </div>

            <div class="switch-field">
              <div>
                <div class="switch-field__title">允许新用户注册</div>
                <div class="switch-field__desc">关闭后注册接口返回 403，已有账号可正常登录。</div>
              </div>
              <button class="switch is-on" role="switch" aria-checked="true" aria-label="允许注册" />
            </div>


          </div>
        </section>

        <section class="card">
          <div class="card__head">
            <span class="card__title">公告与配额</span>
            <span class="tiny dim">前台展示</span>
          </div>
          <div class="card__body">
            <label v-if="form" class="field">
              <span class="field__label">全站公告</span>
              <textarea v-model="form.announcement" class="textarea" placeholder="例如：本周六 02:00–04:00 存储扩容……" />
              <span class="field__hint">留空则不显示公告条。支持纯文本，最多 200 字。</span>
            </label>

            <div class="form-grid-2" style="margin-top: 18px">
              <label v-if="form" class="field">
                <span class="field__label">全局并发上限</span>
                <input v-model.number="form.concurrency" class="input" type="number" min="1" max="8" />
                <span class="field__hint">同时执行的脱壳任务数（受机器资源限制）</span>
              </label>

            </div>

            <div class="note-box note-box--blue" style="margin-top: 18px">
              <AppIcon name="info" :size="15" />
              <span>团队版不提供钱包、定价和充值。任务提交后直接进入队列。</span>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card__head">
            <span class="card__title">存储与清理</span>
            <span class="tiny dim">实时</span>
          </div>
          <div class="card__body">
            <div class="disk">
              <div class="spread small">
                <span class="muted">数据卷</span>
                <span class="mono" :style="{ color: diskTone }">{{ disk.text }} · {{ disk.percent }}%</span>
              </div>
              <div class="bar-track" style="margin-top: 10px; height: 8px">
                <span :style="{ width: disk.percent + '%', background: diskTone }" />
              </div>
              <p v-if="disk.level === 'WARN' || disk.level === 'CRITICAL'" class="tiny" style="margin-top: 8px; color: var(--red)">
                已超过告警阈值 {{ maintenance?.diskWarnPercent }}%，建议立即清理或扩容。
              </p>
            </div>
            <div v-if="form" class="form-grid-3" style="margin-top: 18px">
              <label class="field">
                <span class="field__label">产物保留（天）</span>
                <select v-model.number="form.artifactRetentionDays" class="select">
                  <option :value="7">7 天</option>
                  <option :value="14">14 天</option>
                  <option :value="30">30 天</option>
                  <option :value="60">60 天</option>
                  <option :value="90">90 天</option>
                  <option :value="180">180 天</option>
                  <option :value="365">365 天</option>
                </select>
                <span class="field__hint">到期后删除 IPA，任务记录保留</span>
              </label>
              <label class="field">
                <span class="field__label">上传包保留（天）</span>
                <select v-model.number="form.uploadRetentionDays" class="select">
                  <option :value="3">3 天</option>
                  <option :value="7">7 天</option>
                  <option :value="14">14 天</option>
                  <option :value="30">30 天</option>
                  <option :value="60">60 天</option>
                </select>
                <span class="field__hint">失败任务可在此期间重试</span>
              </label>
              <label class="field">
                <span class="field__label">日志保留（天）</span>
                <select v-model.number="form.logRetentionDays" class="select">
                  <option :value="30">30 天</option>
                  <option :value="90">90 天</option>
                  <option :value="180">180 天</option>
                  <option :value="365">365 天</option>
                  <option :value="730">730 天</option>
                </select>
                <span class="field__hint">只清运行日志，审计日志不动</span>
              </label>
            </div>
            <div class="spread" style="margin-top: 18px; gap: 16px">
              <div class="switch-field" style="flex: 1">
                <div>
                  <div class="switch-field__title">自动清理</div>
                  <div class="switch-field__desc">每小时按保留期清理一次；关闭后仍可手动清理。</div>
                </div>
                <button
                  class="switch"
                  :class="{ 'is-on': form?.autoCleanup }"
                  role="switch"
                  :aria-checked="!!form?.autoCleanup"
                  aria-label="自动清理"
                  @click="form && (form.autoCleanup = !form.autoCleanup)"
                />
              </div>
              <UiButton variant="secondary" :loading="cleaning" @click="cleanup">
                <AppIcon name="refresh" :size="15" /> 立即清理
              </UiButton>
            </div>
            <div class="note-box" style="margin-top: 14px">
              <AppIcon name="info" :size="15" />
              <span>{{ lastRunText }}。清理只删除数据卷内、数据库记录指向的文件。</span>
            </div>
          </div>
        </section>
      </div>

      <aside class="stack gap-16">
        <div class="card">
          <div class="card__head card__head--tight">
            <span class="card__title">服务状态</span>
            <span class="tiny dim">刚刚检查</span>
          </div>
          <div class="svc-list">
            <div v-for="s in services" :key="s.name" class="svc">
              <span class="svc__dot" :class="{ 'is-warn': !s.ok }" />
              <div class="svc__info">
                <div class="svc__name">{{ s.name }}</div>
                <div class="tiny dim">{{ s.desc }}</div>
              </div>
              <div class="svc__right">
                <div class="svc__status" :class="{ 'is-warn': !s.ok }">{{ s.status }}</div>
                <div class="tiny dim">{{ s.meta }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="card card--pad">
          <div class="card__title">部署提示</div>
          <div class="deploy">
            <div><span>后端</span><code>Spring Boot 3.3 / Java 21</code></div>
            <div><span>数据库</span><code>MySQL 8 · Flyway</code></div>
            <div><span>前端</span><code>Vue 3 / Vite</code></div>
            <div><span>能力层</span><code>fairplay-tools</code></div>
          </div>
          <div class="note-box" style="margin-top: 14px">
            <AppIcon name="terminal" :size="15" />
            <span>
              执行器通过 <code class="mono">ProcessBuilder</code> 调用
              <code class="mono">./appget.sh get &lt;bundle&gt;</code>，逐行解析 stdout 更新阶段。
            </span>
          </div>
        </div>

        <div class="card card--pad">
          <div class="card__title">演示环境</div>
          <p class="tiny muted" style="margin-top: 8px; line-height: 1.7">
            当前是前端 Mock 环境，所有数据保存在浏览器 localStorage。重置数据请到
            <router-link to="/app/settings">账号设置 → 危险操作</router-link>。
          </p>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.maint-banner {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-top: 16px;
  padding: 14px 18px;
  border-radius: var(--r-md);
  background: var(--orange-soft);
  color: var(--orange);
  border: 1px solid #f5dfc0;
}
.maint-banner strong {
  display: block;
  font-size: 13px;
}
.maint-banner span {
  font-size: 12px;
  line-height: 1.6;
}
.s-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  margin-top: 18px;
  align-items: start;
}
.money-input {
  position: relative;
}
.money-input .input {
  padding-right: 42px;
}
.money-input .suffix {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 12px;
  color: var(--text-3);
}
.disk {
  padding: 14px 16px;
  border-radius: var(--r-sm);
  background: var(--bg-subtle);
}
.svc-list {
  display: flex;
  flex-direction: column;
}
.svc {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 18px;
  border-bottom: 1px solid var(--line-soft);
}
.svc:last-child {
  border-bottom: 0;
}
.svc__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--green);
  flex: none;
}
.svc__dot.is-warn {
  background: var(--orange);
}
.svc__info {
  flex: 1;
  min-width: 0;
}
.svc__name {
  font-size: 12.5px;
  font-weight: 500;
}
.svc__right {
  text-align: right;
}
.svc__status {
  font-size: 11.5px;
  color: var(--green);
  font-weight: 500;
}
.svc__status.is-warn {
  color: var(--orange);
}
.deploy {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}
.deploy div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 11.5px;
}
.deploy span {
  color: var(--text-3);
  flex: none;
}
.deploy code {
  font-size: 11px;
  color: var(--text-2);
  text-align: right;
}
@media (max-width: 1080px) {
  .s-grid {
    grid-template-columns: 1fr;
  }
}
</style>
