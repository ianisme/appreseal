import type {
  AppItem, AppleAccount, AuditLog, LibraryItem, RechargeOrder, RechargePackage,
  Settings, Task, TaskLog, Transaction, User
} from '@/types'

const KEY = 'appreseal.db.v1'

export interface Wallet {
  balanceCents: number
  totalRechargeCents: number
  totalSpendCents: number
}

export interface DB {
  users: User[]
  wallets: Record<string, Wallet>
  apps: AppItem[]
  tasks: Task[]
  transactions: Transaction[]
  orders: RechargeOrder[]
  library: LibraryItem[]
  appleAccounts: AppleAccount[]
  packages: RechargePackage[]
  settings: Settings
  auditLogs: AuditLog[]
  counter: number
}

export const uid = (prefix: string) => `${prefix}_${Math.random().toString(36).slice(2, 10)}`
export const nowISO = () => new Date().toISOString()
export const isoAgo = (minutes: number) => new Date(Date.now() - minutes * 60000).toISOString()

const sha = (seed: string) => {
  let h = 0
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0
  return Array.from({ length: 8 }, (_, i) => (((h * (i + 7)) >>> 0) % 0xfffffff).toString(16).padStart(7, '0')).join('')
}

const appSeed: Omit<AppItem, 'id' | 'sha256' | 'updatedAt' | 'status' | 'verified'>[] = [
  {
    bundleId: 'com.example.qingjian', name: '青笺', developer: '青屿工作室', category: '社交',
    iconText: '笺', iconHue: 145, version: '1.4.2', sizeBytes: 86_016_000, minIOS: '15.0',
    executable: 'QingJian', priceDownloadCents: 0, priceDecryptCents: 0, downloads: 128,
    arch: ['arm64'],
    description: '虚构的消息样本，只用于把界面跑起来。不对应任何已上架应用。'
  },
  {
    bundleId: 'com.example.ripple', name: '短波', developer: '北岸实验室', category: '影音',
    iconText: '波', iconHue: 330, version: '3.2.1', sizeBytes: 120_586_240, minIOS: '15.0',
    executable: 'Ripple', priceDownloadCents: 0, priceDecryptCents: 0, downloads: 96,
    arch: ['arm64'],
    description: '虚构的短视频样本。名称、开发者和包名都是编的。'
  },
  {
    bundleId: 'com.example.stargaze', name: '观星', developer: '灯塔软件', category: '影音',
    iconText: '星', iconHue: 18, version: '2.1.4', sizeBytes: 98_304_000, minIOS: '15.0',
    executable: 'StarGaze', priceDownloadCents: 0, priceDecryptCents: 0, downloads: 74,
    arch: ['arm64'],
    description: '虚构的视频样本，用来演示成品库。'
  },
  {
    bundleId: 'com.example.paperbird', name: '纸鹤', developer: '南风通讯', category: '社交',
    iconText: '鹤', iconHue: 200, version: '1.1.3', sizeBytes: 64_225_280, minIOS: '15.0',
    executable: 'PaperBird', priceDownloadCents: 0, priceDecryptCents: 0, downloads: 52,
    arch: ['arm64'],
    description: '虚构的通讯样本。'
  },
  {
    bundleId: 'com.example.melodybox', name: '旋律盒', developer: '弦月音频', category: '影音',
    iconText: '律', iconHue: 120, version: '4.0.8', sizeBytes: 77_594_624, minIOS: '15.0',
    executable: 'MelodyBox', priceDownloadCents: 0, priceDecryptCents: 0, downloads: 43,
    arch: ['arm64'],
    description: '虚构的音频样本。'
  },
  {
    bundleId: 'com.example.noteboard', name: '便签坞', developer: '格子工坊', category: '效率',
    iconText: '坞', iconHue: 30, version: '2.2.5', sizeBytes: 54_525_952, minIOS: '15.0',
    executable: 'NoteBoard', priceDownloadCents: 0, priceDecryptCents: 0, downloads: 31,
    arch: ['arm64'],
    description: '虚构的笔记样本。'
  },
  {
    bundleId: 'com.example.askstone', name: '问石', developer: '山海计算', category: '工具',
    iconText: '石', iconHue: 225, version: '0.9.4', sizeBytes: 48_234_496, minIOS: '15.0',
    executable: 'AskStone', priceDownloadCents: 0, priceDecryptCents: 0, downloads: 68,
    arch: ['arm64'],
    description: '虚构的对话样本。'
  },
  {
    bundleId: 'com.example.lamplang', name: '灯语', developer: '湖畔计算', category: '工具',
    iconText: '灯', iconHue: 20, version: '1.3.0', sizeBytes: 70_123_520, minIOS: '15.0',
    executable: 'LampLang', priceDownloadCents: 0, priceDecryptCents: 0, downloads: 25,
    arch: ['arm64'],
    description: '虚构的助手样本。'
  },
  {
    bundleId: 'com.example.framebox', name: '画框', developer: '镜湖影像', category: '社交',
    iconText: '框', iconHue: 320, version: '3.5.2', sizeBytes: 91_750_400, minIOS: '15.0',
    executable: 'FrameBox', priceDownloadCents: 0, priceDecryptCents: 0, downloads: 40,
    arch: ['arm64'],
    description: '虚构的图片社区样本。'
  },
  {
    bundleId: 'com.example.brief', name: '简讯', developer: '西窗网络', category: '社交',
    iconText: '讯', iconHue: 210, version: '1.0.6', sizeBytes: 61_865_984, minIOS: '15.0',
    executable: 'Brief', priceDownloadCents: 0, priceDecryptCents: 0, downloads: 23,
    arch: ['arm64'],
    description: '虚构的动态时间线样本。'
  },
  {
    bundleId: 'com.example.waymark', name: '路标', developer: '途中地图', category: '工具',
    iconText: '路', iconHue: 205, version: '1.5.2', sizeBytes: 110_100_480, minIOS: '15.0',
    executable: 'WayMark', priceDownloadCents: 0, priceDecryptCents: 0, downloads: 19,
    arch: ['arm64'],
    description: '虚构的地图样本。'
  },
  {
    bundleId: 'com.example.journal', name: '手帐', developer: '纸页文化', category: '社交',
    iconText: '帐', iconHue: 350, version: '2.8.0', sizeBytes: 83_886_080, minIOS: '15.0',
    executable: 'Journal', priceDownloadCents: 0, priceDecryptCents: 0, downloads: 35,
    arch: ['arm64'],
    description: '虚构的图文样本。当前为下架状态，用来演示后台上下架。'
  }
]

function makeApps(): AppItem[] {
  return appSeed.map((a, i) => ({
    ...a,
    id: `app_${i + 1}`,
    sha256: sha(a.bundleId + a.version),
    status: i === 11 ? 'OFF_SHELF' : 'ON_SALE',
    verified: true,
    updatedAt: isoAgo(60 * 24 * (i + 1) + 130)
  }))
}

function seededLogs(task: Partial<Task>, fail: boolean): TaskLog[] {
  if (task.status === 'FAILED') {
    return [
      { at: task.createdAt!, level: 'INFO', message: '任务已创建，进入队列' },
      { at: task.createdAt!, level: 'INFO', message: '分配 Apple ID 池账号 id-01' },
      { at: task.createdAt!, level: 'INFO', message: '开始下载加密包…' },
      { at: task.createdAt!, level: 'ERROR', message: '✘ credential does not decode this SINF priv record' },
      { at: task.createdAt!, level: 'WARN', message: `已自动退款 ${((task.priceCents ?? 0) / 100).toFixed(2)} 元` }
    ]
  }
  return [
    { at: task.createdAt!, level: 'INFO', message: '任务已创建，进入队列' },
    { at: task.createdAt!, level: 'INFO', message: '分配 Apple ID 池账号 id-01（优先级 1）' },
    { at: task.createdAt!, level: 'INFO', message: '开始下载加密包…' },
    { at: task.createdAt!, level: 'OK', message: '下载完成，开始解析 SINF / SUPF' },
    { at: task.createdAt!, level: 'INFO', message: 'FairPlay 解密中，cryptsize=4096' },
    { at: task.createdAt!, level: 'OK', message: 'Mach-O 加密页已还原' },
    { at: task.createdAt!, level: 'OK', message: '校验通过：cryptid=0' },
    { at: task.createdAt!, level: 'OK', message: '脱壳完成，产物已入库' }
  ]
}

function seed(): DB {
  const users: User[] = [
    {
      id: 'u_demo', email: 'demo@appreseal.com', password: 'demo1234', nickname: 'Yin',
      role: 'USER', status: 'ACTIVE', avatarColor: '#0071e3',
      createdAt: isoAgo(60 * 24 * 92), lastLoginAt: isoAgo(38)
    },
    {
      id: 'u_admin', email: 'admin@appreseal.com', password: 'admin1234', nickname: '管理员',
      role: 'ADMIN', status: 'ACTIVE', avatarColor: '#1d1d1f',
      createdAt: isoAgo(60 * 24 * 180), lastLoginAt: isoAgo(12)
    },
    ...[
      ['lin@example.com', '林深', 12800, 'USER', '#ff375f'],
      ['zhou@example.com', '周予安', 4600, 'USER', '#30d158'],
      ['chen@example.com', '陈默', 0, 'USER', '#ff9f0a'],
      ['ada@example.com', 'Ada', 21300, 'USER', '#5e5ce6'],
      ['wang@example.com', '王野', 900, 'USER', '#64d2ff'],
      ['zhao@example.com', '赵四', 3200, 'USER', '#bf5af2'],
      ['ken@example.com', 'Ken', 0, 'USER', '#ff453a']
    ].map(([email, nickname, balance, role, color], i) => ({
      id: `u_${i + 10}`,
      email: email as string,
      password: 'demo1234',
      nickname: nickname as string,
      role: role as User['role'],
      status: (i === 6 ? 'BANNED' : 'ACTIVE') as User['status'],
      avatarColor: color as string,
      createdAt: isoAgo(60 * 24 * (40 - i * 3)),
      lastLoginAt: isoAgo(90 * (i + 1))
    }))
  ]

  const wallets: Record<string, Wallet> = {
    u_demo: { balanceCents: 4200, totalRechargeCents: 30000, totalSpendCents: 25800 },
    u_admin: { balanceCents: 0, totalRechargeCents: 0, totalSpendCents: 0 }
  }
  users.slice(2).forEach((u, i) => {
    const bal = [12800, 4600, 0, 21300, 900, 3200, 0][i] ?? 0
    wallets[u.id] = { balanceCents: bal, totalRechargeCents: bal + i * 1200, totalSpendCents: i * 1200 }
  })

  const apps = makeApps()

  const t = (
    id: string, app: AppItem, status: Task['status'], minutesAgo: number,
    extra: Partial<Task> = {}
  ): Task => ({
    id, userId: 'u_demo', appName: app.name, bundleId: app.bundleId, version: app.version,
    source: 'STORE', status, progress: status === 'SUCCEEDED' ? 100 : 0,
    stage: status === 'SUCCEEDED' ? '已完成' : '已失败',
    priceCents: app.priceDecryptCents, refundedCents: status === 'FAILED' ? app.priceDecryptCents : 0,
    createdAt: isoAgo(minutesAgo), finishedAt: isoAgo(minutesAgo - 3),
    simulated: false,
    sizeBytes: status === 'SUCCEEDED' ? app.sizeBytes : undefined,
    sha256: status === 'SUCCEEDED' ? app.sha256 : undefined,
    logs: [],
    ...extra
  })

  const tasks: Task[] = [
    t('t_seed_1', apps[0], 'SUCCEEDED', 60 * 26),
    t('t_seed_2', apps[7], 'SUCCEEDED', 60 * 24 * 4),
    t('t_seed_3', apps[6], 'FAILED', 60 * 30, { error: '凭证不匹配：credential does not decode this SINF priv record' }),
    t('t_seed_4', apps[2], 'SUCCEEDED', 60 * 24 * 9),
    t('t_seed_5', apps[4], 'CANCELED', 60 * 24 * 12, { stage: '已取消' })
  ]
  tasks.forEach((task) => { task.logs = seededLogs(task, task.status === 'FAILED') })

  const transactions: Transaction[] = [
    { id: 'tx_1', userId: 'u_demo', type: 'RECHARGE', amountCents: 10000, balanceAfterCents: 10000, remark: '充值 ¥100（赠送 ¥5）', createdAt: isoAgo(60 * 24 * 20) },
    { id: 'tx_2', userId: 'u_demo', type: 'DECRYPT', amountCents: -600, balanceAfterCents: 9400, remark: '任务 · 青笺 1.4.2', refId: 't_seed_1', createdAt: isoAgo(60 * 26) },
    { id: 'tx_3', userId: 'u_demo', type: 'DOWNLOAD', amountCents: -300, balanceAfterCents: 9100, remark: '下载成品 · 观星 2.1.4', createdAt: isoAgo(60 * 24 * 6) },
    { id: 'tx_4', userId: 'u_demo', type: 'REFUND', amountCents: 800, balanceAfterCents: 9900, remark: '任务失败 · 问石 0.9.4', refId: 't_seed_3', createdAt: isoAgo(60 * 29) },
    { id: 'tx_5', userId: 'u_demo', type: 'DECRYPT', amountCents: -600, balanceAfterCents: 9300, remark: '任务 · 灯语 1.3.0', refId: 't_seed_2', createdAt: isoAgo(60 * 24 * 4) },
    { id: 'tx_6', userId: 'u_demo', type: 'RECHARGE', amountCents: 20000, balanceAfterCents: 29300, remark: '充值 ¥200', createdAt: isoAgo(60 * 24 * 3) },
    { id: 'tx_7', userId: 'u_demo', type: 'DECRYPT', amountCents: -800, balanceAfterCents: 28500, remark: '任务 · 问石 0.9.4', refId: 't_seed_3', createdAt: isoAgo(60 * 30) },
    { id: 'tx_8', userId: 'u_demo', type: 'DOWNLOAD', amountCents: -300, balanceAfterCents: 28200, remark: '下载成品 · 纸鹤 1.1.3', createdAt: isoAgo(60 * 24 * 2) },
    { id: 'tx_9', userId: 'u_demo', type: 'DOWNLOAD', amountCents: -300, balanceAfterCents: 27900, remark: '下载成品 · 便签坞 2.2.5', createdAt: isoAgo(60 * 20) }
  ].reverse() as Transaction[]

  const orders: RechargeOrder[] = [
    { id: 'ro_1', userId: 'u_demo', amountCents: 20000, bonusCents: 0, method: 'ALIPAY', status: 'PAID', createdAt: isoAgo(60 * 24 * 3), paidAt: isoAgo(60 * 24 * 3) },
    { id: 'ro_2', userId: 'u_demo', amountCents: 10000, bonusCents: 500, method: 'WECHAT', status: 'PAID', createdAt: isoAgo(60 * 24 * 20), paidAt: isoAgo(60 * 24 * 20) },
    { id: 'ro_3', userId: 'u_10', amountCents: 30000, bonusCents: 2000, method: 'ALIPAY', status: 'PAID', createdAt: isoAgo(60 * 5) },
    { id: 'ro_4', userId: 'u_13', amountCents: 50000, bonusCents: 5000, method: 'WECHAT', status: 'PAID', createdAt: isoAgo(60 * 9) }
  ]

  const library: LibraryItem[] = [
    {
      id: 'lib_1', userId: 'u_demo', appId: apps[0].id, bundleId: apps[0].bundleId, name: apps[0].name,
      version: apps[0].version, source: 'DECRYPT', acquiredAt: isoAgo(60 * 26),
      freeUntil: isoAgo(60 * 26 - 60 * 24 * 30), taskId: 't_seed_1'
    },
    {
      id: 'lib_2', userId: 'u_demo', appId: apps[2].id, bundleId: apps[2].bundleId, name: apps[2].name,
      version: apps[2].version, source: 'PURCHASE', acquiredAt: isoAgo(60 * 24 * 6),
      freeUntil: isoAgo(60 * 24 * 6 - 60 * 24 * 30)
    },
    {
      id: 'lib_3', userId: 'u_demo', appId: apps[7].id, bundleId: apps[7].bundleId, name: apps[7].name,
      version: apps[7].version, source: 'DECRYPT', acquiredAt: isoAgo(60 * 24 * 4),
      freeUntil: isoAgo(60 * 24 * 4 - 60 * 24 * 30), taskId: 't_seed_2'
    }
  ]

  const appleAccounts: AppleAccount[] = [
    { id: 'aa_1', email: 'id-01@appreseal.com', storefront: 'cn', priority: 1, credentialOk: true, sessionOk: true, status: 'READY', boundApps: 7, lastUsedAt: isoAgo(18) },
    { id: 'aa_2', email: 'id-02@appreseal.com', storefront: 'us', priority: 2, credentialOk: true, sessionOk: true, status: 'READY', boundApps: 4, lastUsedAt: isoAgo(60 * 7) },
    { id: 'aa_3', email: 'id-03@appreseal.com', storefront: 'jp', priority: 3, credentialOk: true, sessionOk: false, status: 'NEED_LOGIN', boundApps: 1, lastUsedAt: isoAgo(60 * 26) },
    { id: 'aa_4', email: 'id-04@appreseal.com', storefront: 'hk', priority: 4, credentialOk: false, sessionOk: false, status: 'DISABLED', boundApps: 0, lastUsedAt: isoAgo(60 * 24 * 40) }
  ]

  const packages: RechargePackage[] = [
    { id: 'pkg_50', amountCents: 5000, bonusCents: 0 },
    { id: 'pkg_100', amountCents: 10000, bonusCents: 500, recommended: true },
    { id: 'pkg_300', amountCents: 30000, bonusCents: 2000 },
    { id: 'pkg_500', amountCents: 50000, bonusCents: 5000 }
  ]

  const settings: Settings = {
    decryptPriceCents: 600,
    downloadPriceCents: 300,
    concurrency: 2,
    maintenance: false,
    announcement: '本周六 02:00–04:00 进行存储扩容，期间任务排队时间可能延长，已提交的任务不受影响。',
    freeDownloadDays: 30,
    artifactRetentionDays: 30,
    uploadRetentionDays: 14,
    logRetentionDays: 90,
    autoCleanup: true,
    diskWarnPercent: 80
  }

  const auditLogs: AuditLog[] = [
    { id: 'al_1', actor: '管理员', action: 'PRICING_UPDATE', target: '定价配置', detail: '脱壳 ¥5.00 → ¥6.00', ip: '10.0.2.14', createdAt: isoAgo(60 * 6) },
    { id: 'al_2', actor: '管理员', action: 'APP_SHELF', target: '手帐 2.8.0', detail: '下架：等待重新校验', ip: '10.0.2.14', createdAt: isoAgo(60 * 11) },
    { id: 'al_3', actor: '管理员', action: 'BALANCE_ADJUST', target: 'zhao@example.com', detail: '+¥20.00 补偿', ip: '10.0.2.14', createdAt: isoAgo(60 * 22) },
    { id: 'al_4', actor: '管理员', action: 'TASK_RETRY', target: 't_seed_3', detail: '问石 0.9.4 重试', ip: '10.0.2.14', createdAt: isoAgo(60 * 28) },
    { id: 'al_5', actor: '管理员', action: 'USER_BAN', target: 'ken@example.com', detail: '异常下载频率', ip: '10.0.2.14', createdAt: isoAgo(60 * 40) },
    { id: 'al_6', actor: '系统', action: 'APPLE_SYNC', target: 'Apple ID 池', detail: '4 个账号，1 个需重新登录', ip: '127.0.0.1', createdAt: isoAgo(60 * 2) }
  ]

  return {
    users, wallets, apps, tasks, transactions, orders, library, appleAccounts,
    packages, settings, auditLogs, counter: 100
  }
}

let cache: DB | null = null

export function getDB(): DB {
  if (cache) return cache
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) {
      cache = JSON.parse(raw) as DB
      return cache
    }
  } catch { /* ignore */ }
  cache = seed()
  saveDB()
  return cache
}

export function saveDB() {
  if (!cache) return
  try {
    localStorage.setItem(KEY, JSON.stringify(cache))
  } catch { /* quota */ }
}

export function resetDB() {
  cache = seed()
  saveDB()
}

const STATUS_STAGE: Record<string, string> = {
  QUEUED: '排队中',
  DOWNLOADING: '下载中',
  DECRYPTING: '解密中',
  VERIFYING: '校验中',
  SUCCEEDED: '已完成',
  FAILED: '已失败',
  CANCELED: '已取消'
}

/** 依据创建时间推进“演示任务”的状态，读取时调用。 */
export function advanceTasks(db: DB) {
  const now = Date.now()
  let changed = false
  for (const task of db.tasks) {
    if (!task.simulated) continue
    if (task.status === 'SUCCEEDED' || task.status === 'FAILED' || task.status === 'CANCELED') continue

    const elapsed = (now - new Date(task.createdAt).getTime()) / 1000
    let status: Task['status'] = 'QUEUED'
    let progress = 6
    if (elapsed >= 3) { status = 'DOWNLOADING'; progress = 18 + Math.min(32, (elapsed - 3) * 8) }
    if (elapsed >= 7) { status = 'DECRYPTING'; progress = 56 + Math.min(28, (elapsed - 7) * 9) }
    if (elapsed >= 11) { status = 'VERIFYING'; progress = 92 }
    if (elapsed >= 13.5) {
      if (task.willFail) {
        status = 'FAILED'
        progress = 100
        task.error = '该 App 不属于当前 Apple ID（凭证不匹配），已自动退款'
        task.refundedCents = task.priceCents
        const wallet = db.wallets[task.userId]
        if (wallet) {
          wallet.balanceCents += task.priceCents
          db.transactions.unshift({
            id: uid('tx'), userId: task.userId, type: 'REFUND', amountCents: task.priceCents,
            balanceAfterCents: wallet.balanceCents, remark: `任务失败退款 · ${task.appName} ${task.version}`,
            refId: task.id, createdAt: nowISO()
          })
        }
      } else {
        status = 'SUCCEEDED'
        progress = 100
        const app = db.apps.find((a) => a.bundleId === task.bundleId)
        task.sizeBytes = app?.sizeBytes ?? 140 * 1024 * 1024
        task.sha256 = app?.sha256 ?? sha(task.id)
        task.finishedAt = nowISO()
        if (app) {
          db.library.unshift({
            id: uid('lib'), userId: task.userId, appId: app.id, bundleId: app.bundleId,
            name: app.name, version: task.version, source: 'DECRYPT', acquiredAt: nowISO(),
            freeUntil: new Date(Date.now() + db.settings.freeDownloadDays * 86400000).toISOString(),
            taskId: task.id
          })
        }
      }
    }

    if (task.status !== status) {
      task.status = status
      task.stage = STATUS_STAGE[status]
      changed = true
    }
    task.progress = Math.round(progress)
    task.logs = timelineLogs(task)
    if (status === 'SUCCEEDED' || status === 'FAILED') {
      task.finishedAt = task.finishedAt ?? nowISO()
    }
  }
  if (changed) saveDB()
}

function timelineLogs(task: Task): TaskLog[] {
  const base = new Date(task.createdAt).getTime()
  const at = (s: number) => new Date(base + s * 1000).toISOString()
  const logs: TaskLog[] = [
    { at: at(0), level: 'INFO', message: '任务已创建，进入队列' },
    { at: at(1), level: 'INFO', message: '分配 Apple ID 池账号 id-01（优先级 1）' },
    { at: at(3), level: 'INFO', message: '开始下载加密包（ipatool download --purchase）…' },
    { at: at(6.5), level: 'OK', message: '下载完成，解析 SINF / SUPF' },
    { at: at(7), level: 'INFO', message: 'FairPlay 解密中，cryptsize=4096（1 页）' },
    { at: at(10.5), level: 'OK', message: 'Mach-O 加密页已还原，cryptid 1 → 0' }
  ]
  if (task.status === 'VERIFYING' || task.status === 'SUCCEEDED' || task.status === 'FAILED') {
    logs.push({ at: at(11.5), level: 'INFO', message: '校验产物完整性…' })
  }
  if (task.status === 'FAILED') {
    logs.push({ at: at(13), level: 'ERROR', message: `✘ ${task.error ?? '解密失败'}` })
    logs.push({ at: at(13.2), level: 'WARN', message: `已自动退款 ¥${(task.priceCents / 100).toFixed(2)}` })
  }
  if (task.status === 'SUCCEEDED') {
    logs.push({ at: at(13), level: 'OK', message: '校验通过：cryptid=0' })
    logs.push({ at: at(13.4), level: 'OK', message: '脱壳完成，产物已入库' })
  }
  return logs
}

export function nextId(prefix: string): string {
  const db = getDB()
  db.counter += 1
  return `${prefix}_${db.counter}`
}
