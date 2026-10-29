import type {
  AdminOverview, AppItem, AuditLog, AppleAccount, CleanupReport, LibraryItem, LogPage, MaintenanceInfo,
  PricingConfig, StoreMarket, StoreSearchResult, RechargeOrder, RechargePackage, Settings, Task,
  TaskPage, Transaction, UploadProgress, User, UserPage
} from '@/types'
import { advanceTasks, getDB, isoAgo, nextId, nowISO, saveDB, uid } from '@/mock/db'
import { STOREFRONTS } from '@/data/storefronts'
import { realApi } from './real'
import { saveBlob } from './http'

const SESSION_KEY = 'appreseal.session'
const resetCodes = new Map<string, { code: string; attempts: number }>()

const delay = (ms = 260) => new Promise<void>((r) => setTimeout(r, ms))

function session(): { userId: string } | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function setSession(userId: string) {
  localStorage.setItem(SESSION_KEY, JSON.stringify({ userId, at: nowISO() }))
}

function requireUser(): User {
  const s = session()
  const db = getDB()
  const user = s ? db.users.find((u) => u.id === s.userId) : undefined
  if (!user) throw new Error('登录已过期，请重新登录')
  if (user.status === 'BANNED') throw new Error('账号已被停用，请联系管理员')
  return user
}

function normalizeSettings(s: Settings): Settings {
  return {
    ...s,
    artifactRetentionDays: s.artifactRetentionDays ?? 30,
    uploadRetentionDays: s.uploadRetentionDays ?? 14,
    logRetentionDays: s.logRetentionDays ?? 90,
    autoCleanup: s.autoCleanup ?? true,
    diskWarnPercent: s.diskWarnPercent ?? 80
  }
}

function pageOf<T>(all: T[], page = 1, size = 20): { items: T[]; page: number; size: number; total: number; pages: number } {
  const p = Math.max(1, page || 1)
  const s = Math.min(100, Math.max(1, size || 20))
  const start = (p - 1) * s
  return {
    items: all.slice(start, start + s),
    page: p,
    size: s,
    total: all.length,
    pages: all.length === 0 ? 0 : Math.ceil(all.length / s)
  }
}

const RUNNING = ['QUEUED', 'DOWNLOADING', 'DECRYPTING', 'VERIFYING']

function taskPage<T extends Task>(all: T[], filtered: T[], page = 1, size = 20): TaskPage<T> {
  return {
    ...pageOf(filtered, page, size),
    running: all.filter((t) => RUNNING.includes(t.status)).length,
    succeeded: all.filter((t) => t.status === 'SUCCEEDED').length,
    failed: all.filter((t) => t.status === 'FAILED').length,
    canceled: all.filter((t) => t.status === 'CANCELED').length,
    revenueCents: all.filter((t) => t.status === 'SUCCEEDED').reduce((s, t) => s + t.priceCents, 0)
  }
}

function requireAdmin(): User {
  const user = requireUser()
  if (user.role !== 'ADMIN') throw new Error('没有管理员权限')
  return user
}

function publicUser(u: User): User {
  const { password, ...rest } = u
  return rest as User
}

function spend(_userId: string, _cents: number, _type: Transaction['type'], _remark: string, _refId?: string) {
  // 团队版不扣费。保留函数是为了不改动各处调用。
}

function saveText(filename: string, content: string) {
  saveBlob(new Blob([content], { type: 'text/plain;charset=utf-8' }), filename)
}

const mockApi = {
  /* ------------------------------ auth ------------------------------ */
  async login(email: string, password: string): Promise<User> {
    await delay(420)
    const db = getDB()
    const user = db.users.find((u) => u.email.toLowerCase() === email.trim().toLowerCase())
    if (!user || user.password !== password) throw new Error('邮箱或密码不正确')
    if (user.status === 'BANNED') throw new Error('账号已被停用，请联系管理员')
    user.lastLoginAt = nowISO()
    setSession(user.id)
    saveDB()
    return publicUser(user)
  },

  async register(email: string, password: string, nickname: string): Promise<User> {
    await delay(520)
    const db = getDB()
    if (db.users.some((u) => u.email.toLowerCase() === email.trim().toLowerCase())) {
      throw new Error('该邮箱已注册')
    }
    const user: User = {
      id: uid('u'), email: email.trim(), password, nickname: nickname.trim() || email.split('@')[0],
      role: 'USER', status: 'ACTIVE', avatarColor: '#0071e3',
      createdAt: nowISO(), lastLoginAt: nowISO()
    }
    db.users.push(user)
    db.wallets[user.id] = { balanceCents: 0, totalRechargeCents: 0, totalSpendCents: 0 }
    setSession(user.id)
    saveDB()
    return publicUser(user)
  },

  async me(): Promise<User | null> {
    await delay(120)
    const s = session()
    if (!s) return null
    const db = getDB()
    const user = db.users.find((u) => u.id === s.userId)
    return user ? publicUser(user) : null
  },

  async logout() {
    localStorage.removeItem(SESSION_KEY)
  },

  async updateProfile(patch: { nickname?: string; avatarColor?: string }): Promise<User> {
    await delay(280)
    const user = requireUser()
    const db = getDB()
    const target = db.users.find((u) => u.id === user.id)
    if (!target) throw new Error('用户不存在')
    if (patch.nickname !== undefined) {
      const nickname = patch.nickname.trim()
      if (!nickname) throw new Error('昵称不能为空')
      if (nickname.length > 24) throw new Error('昵称不能超过 24 个字符')
      target.nickname = nickname
    }
    if (patch.avatarColor) target.avatarColor = patch.avatarColor
    saveDB()
    return publicUser(target)
  },

  async changePassword(oldPassword: string, newPassword: string): Promise<void> {
    await delay(380)
    const user = requireUser()
    const db = getDB()
    const target = db.users.find((u) => u.id === user.id)
    if (!target) throw new Error('用户不存在')
    if (target.password !== oldPassword) throw new Error('当前密码不正确')
    if (newPassword.length < 8) throw new Error('新密码至少 8 位')
    if (newPassword === oldPassword) throw new Error('新密码不能与当前密码相同')
    target.password = newPassword
    saveDB()
  },

  async forgotPassword(email: string): Promise<{ message: string }> {
    await delay(280)
    const normalized = email.trim().toLowerCase()
    const user = getDB().users.find((u) => u.email.toLowerCase() === normalized && u.status === 'ACTIVE')
    if (user) resetCodes.set(normalized, { code: '123456', attempts: 0 })
    return { message: '如果该邮箱已注册，验证码已发送，请查收邮件。演示环境可试 123456。' }
  },

  async resetPassword(email: string, code: string, newPassword: string): Promise<User> {
    await delay(360)
    if (newPassword.length < 8) throw new Error('新密码至少 8 位')
    const normalized = email.trim().toLowerCase()
    const db = getDB()
    const target = db.users.find((u) => u.email.toLowerCase() === normalized)
    const pending = resetCodes.get(normalized)
    const bad = !target || target.status !== 'ACTIVE' || !pending || pending.code !== code.trim()
    if (bad) {
      if (pending) {
        pending.attempts += 1
        if (pending.attempts >= 3) resetCodes.delete(normalized)
      }
      throw new Error('验证码不正确或已过期')
    }
    target.password = newPassword
    target.lastLoginAt = nowISO()
    resetCodes.delete(normalized)
    setSession(target.id)
    saveDB()
    return publicUser(target)
  },

  /* ------------------------------ apps ------------------------------ */
  async listApps(params: { q?: string; category?: string; sort?: string } = {}): Promise<AppItem[]> {
    await delay(280)
    const db = getDB()
    let items = db.apps.filter((a) => a.status === 'ON_SALE')
    if (params.q) {
      const q = params.q.toLowerCase()
      items = items.filter(
        (a) =>
          a.name.toLowerCase().includes(q) ||
          a.bundleId.toLowerCase().includes(q) ||
          a.developer.toLowerCase().includes(q)
      )
    }
    if (params.category && params.category !== '全部') {
      items = items.filter((a) => a.category === params.category)
    }
    if (params.sort === 'downloads') items = [...items].sort((a, b) => b.downloads - a.downloads)
    else if (params.sort === 'price') items = [...items].sort((a, b) => a.priceDownloadCents - b.priceDownloadCents)
    else items = [...items].sort((a, b) => +new Date(b.updatedAt) - +new Date(a.updatedAt))
    return items
  },

  async getApp(bundleId: string): Promise<AppItem> {
    await delay(220)
    const db = getDB()
    const app = db.apps.find((a) => a.bundleId === bundleId)
    if (!app) throw new Error('应用不存在或已下架')
    return app
  },

  async storeMarkets(): Promise<StoreMarket[]> {
    await delay(120)
    requireUser()
    const db = getDB()
    const counts = new Map<string, number>()
    for (const account of db.appleAccounts) {
      if (!account.storefront || account.status === 'DISABLED') continue
      counts.set(account.storefront, (counts.get(account.storefront) || 0) + 1)
    }
    return STOREFRONTS.filter((item) => counts.has(item.code)).map((item) => ({
      code: item.code,
      name: item.name,
      accounts: counts.get(item.code) || 0
    }))
  },

  async searchStore(q: string, country = 'cn'): Promise<StoreSearchResult> {
    await delay(340)
    const db = getDB()
    const storefront = country || 'cn'
    const names: Record<string, string> = { cn: '中国大陆', us: '美国', jp: '日本', hk: '中国香港' }
    const key = q.trim().toLowerCase()
    const hits = key
      ? db.apps
          .filter((a) => a.name.toLowerCase().includes(key) || a.bundleId.toLowerCase().includes(key))
          .slice(0, 8)
          .map((a) => ({
            bundleId: a.bundleId, name: a.name, developer: a.developer, version: a.version,
            iconText: a.iconText, iconHue: a.iconHue,
            inLibrary: db.library.some((l) => l.bundleId === a.bundleId)
          }))
      : []
    return {
      storefront,
      storefrontName: names[storefront] || storefront,
      matchedAccounts: db.appleAccounts.filter((a) => a.storefront === storefront && a.status !== 'DISABLED').length,
      hits
    }
  },

  async downloadApp(bundleId: string): Promise<{ app: AppItem; balanceCents: number; libraryId?: string }> {
    await delay(560)
    const user = requireUser()
    const db = getDB()
    const app = db.apps.find((a) => a.bundleId === bundleId)
    const existing = db.library.find((l) => l.userId === user.id && l.bundleId === bundleId)
    const inFree = !!existing && new Date(existing.freeUntil) > new Date()
    if (!app || (app.status !== 'ON_SALE' && !inFree)) throw new Error('应用不存在或已下架')

    if (inFree && existing) {
      return { app, balanceCents: db.wallets[user.id].balanceCents, libraryId: existing.id }
    }

    spend(user.id, app.priceDownloadCents, 'DOWNLOAD', `下载成品 · ${app.name} ${app.version}`, app.id)
    app.downloads += 1
    if (existing) {
      existing.freeUntil = new Date(Date.now() + db.settings.freeDownloadDays * 86400000).toISOString()
      existing.acquiredAt = nowISO()
    } else {
      db.library.unshift({
        id: uid('lib'), userId: user.id, appId: app.id, bundleId: app.bundleId, name: app.name,
        version: app.version, source: 'PURCHASE', acquiredAt: nowISO(),
        freeUntil: new Date(Date.now() + db.settings.freeDownloadDays * 86400000).toISOString()
      })
    }
    saveDB()
    const item = existing ?? db.library.find((l) => l.userId === user.id && l.bundleId === bundleId)
    return { app, balanceCents: db.wallets[user.id].balanceCents, libraryId: item?.id }
  },

  async site() {
    await delay(80)
    const s = getDB().settings
    return {
      maintenance: s.maintenance,
      announcement: s.announcement,
      decryptPriceCents: s.decryptPriceCents,
      downloadPriceCents: s.downloadPriceCents
    }
  },

  /* ------------------------------ tasks ------------------------------ */
  async listTasks(status?: string, page = 1, size = 20): Promise<TaskPage> {
    await delay(280)
    const db = getDB()
    advanceTasks(db)
    const user = requireUser()
    const all = db.tasks.filter((t) => t.userId === user.id)
      .sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))
    let items = all
    if (status && status !== 'ALL') {
      items = status === 'RUNNING' ? items.filter((t) => RUNNING.includes(t.status)) : items.filter((t) => t.status === status)
    }
    return taskPage(all, items, page, size)
  },

  async getTask(id: string): Promise<Task> {
    await delay(160)
    const db = getDB()
    advanceTasks(db)
    const user = requireUser()
    const task = db.tasks.find((t) => t.id === id)
    if (!task) throw new Error('任务不存在')
    if (task.userId !== user.id && user.role !== 'ADMIN') throw new Error('无权查看该任务')
    return task
  },

  async createTask(input: { source: 'STORE' | 'UPLOAD'; bundleId: string; appName: string; version: string; priceCents: number; storefront?: string }): Promise<Task> {
    await delay(520)
    const user = requireUser()
    const db = getDB()
    if (db.settings.maintenance) throw new Error('系统维护中，暂时无法提交任务')
    if (!input.bundleId.trim()) throw new Error('缺少 bundle id')

    spend(user.id, input.priceCents, 'DECRYPT', `脱壳 · ${input.appName} ${input.version}`)

    const task: Task = {
      id: nextId('t'),
      userId: user.id,
      appName: input.appName || input.bundleId,
      bundleId: input.bundleId,
      version: input.version || 'latest',
      storefront: input.source === 'STORE' ? input.storefront || 'cn' : undefined,
      source: input.source,
      status: 'QUEUED',
      progress: 6,
      stage: '排队中',
      priceCents: input.priceCents,
      refundedCents: 0,
      createdAt: nowISO(),
      simulated: true,
      willFail: /fail/i.test(input.appName),
      logs: []
    }
    db.tasks.unshift(task)
    saveDB()
    return task
  },

  /** 演示环境的上传占位：按文件名生成任务，真实解析在后端完成。 */
  async uploadTask(file: File, onProgress?: (info: UploadProgress) => void): Promise<Task> {
    onProgress?.({ loaded: file.size, total: file.size, phase: 'verify' })
    await delay(700)
    const user = requireUser()
    const db = getDB()
    if (db.settings.maintenance) throw new Error('系统维护中，暂时无法提交任务')
    if (!file.name.toLowerCase().endsWith('.ipa')) throw new Error('只支持 .ipa 文件')

    const base = file.name.replace(/\.ipa$/i, '')
    const slug = base.toLowerCase().replace(/[^a-z0-9]+/g, '.').replace(/^\.+|\.+$/g, '') || 'upload'
    const appName = base.slice(0, 120) || slug
    const price = db.settings.decryptPriceCents

    spend(user.id, price, 'DECRYPT', `上传脱壳 · ${appName} latest`)

    const task: Task = {
      id: nextId('t'),
      userId: user.id,
      appName,
      bundleId: `com.upload.${slug}`.slice(0, 160),
      version: 'latest',
      source: 'UPLOAD',
      status: 'QUEUED',
      progress: 6,
      stage: '排队中',
      priceCents: price,
      refundedCents: 0,
      createdAt: nowISO(),
      simulated: true,
      willFail: /fail/i.test(appName),
      logs: []
    }
    db.tasks.unshift(task)
    saveDB()
    return task
  },

  async retryTask(id: string): Promise<Task> {
    await delay(420)
    const user = requireUser()
    const db = getDB()
    const task = db.tasks.find((t) => t.id === id)
    if (!task) throw new Error('任务不存在')
    if (task.userId !== user.id && user.role !== 'ADMIN') throw new Error('无权操作')
    if (task.status !== 'FAILED' && task.status !== 'CANCELED') throw new Error('只有失败或已取消的任务可以重试')

    spend(user.id, task.priceCents, 'DECRYPT', `重试脱壳 · ${task.appName} ${task.version}`, task.id)
    task.status = 'QUEUED'
    task.progress = 6
    task.stage = '排队中'
    task.error = undefined
    task.refundedCents = 0
    task.finishedAt = undefined
    task.createdAt = nowISO()
    task.simulated = true
    task.willFail = false
    task.logs = []
    saveDB()
    return task
  },

  async cancelTask(id: string): Promise<Task> {
    await delay(320)
    const db = getDB()
    const user = requireUser()
    const task = db.tasks.find((t) => t.id === id)
    if (!task) throw new Error('任务不存在')
    if (task.userId !== user.id && user.role !== 'ADMIN') throw new Error('无权操作')
    if (!['QUEUED', 'DOWNLOADING', 'DECRYPTING', 'VERIFYING'].includes(task.status)) {
      throw new Error('任务已结束，不能取消')
    }

    task.status = 'CANCELED'
    task.stage = '已取消'
    task.refundedCents = 0
    task.finishedAt = nowISO()
    saveDB()
    return task
  },

  /* ------------------------------ wallet ------------------------------ */
  async getWallet(): Promise<{ balanceCents: number; totalRechargeCents: number; totalSpendCents: number }> {
    await delay(160)
    const user = requireUser()
    return getDB().wallets[user.id]
  },

  async listTransactions(): Promise<Transaction[]> {
    await delay(240)
    const user = requireUser()
    return getDB().transactions.filter((t) => t.userId === user.id).sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))
  },

  async listPackages(): Promise<RechargePackage[]> {
    await delay(120)
    return getDB().packages
  },

  async createRecharge(packageId: string, method: 'ALIPAY' | 'WECHAT'): Promise<RechargeOrder> {
    await delay(380)
    const user = requireUser()
    const db = getDB()
    const pkg = db.packages.find((p) => p.id === packageId)
    if (!pkg) throw new Error('套餐不存在')
    const order: RechargeOrder = {
      id: nextId('ro'), userId: user.id, amountCents: pkg.amountCents, bonusCents: pkg.bonusCents,
      method, status: 'PENDING', createdAt: nowISO()
    }
    db.orders.unshift(order)
    saveDB()
    return order
  },

  async payRecharge(orderId: string): Promise<{ balanceCents: number }> {
    await delay(700)
    const user = requireUser()
    const db = getDB()
    const order = db.orders.find((o) => o.id === orderId)
    if (!order) throw new Error('订单不存在')
    if (order.status === 'PAID') return { balanceCents: db.wallets[user.id].balanceCents }
    if (order.status === 'REFUNDED') throw new Error('订单已退款')

    const wallet = db.wallets[user.id]
    const total = order.amountCents + order.bonusCents
    wallet.balanceCents += total
    wallet.totalRechargeCents += total
    order.status = 'PAID'
    order.paidAt = nowISO()
    db.transactions.unshift({
      id: uid('tx'), userId: user.id, type: 'RECHARGE', amountCents: total,
      balanceAfterCents: wallet.balanceCents,
      remark: `充值 ¥${(order.amountCents / 100).toFixed(0)}${order.bonusCents ? `（赠送 ¥${(order.bonusCents / 100).toFixed(0)}）` : ''}`,
      refId: order.id, createdAt: nowISO()
    })
    saveDB()
    return { balanceCents: wallet.balanceCents }
  },

  async getRecharge(orderId: string): Promise<RechargeOrder> {
    await delay(120)
    const db = getDB()
    const order = db.orders.find((o) => o.id === orderId)
    if (!order) throw new Error('订单不存在')
    return order
  },

  /* ------------------------------ library ------------------------------ */
  async listLibrary(): Promise<LibraryItem[]> {
    await delay(220)
    const user = requireUser()
    return getDB().library
      .filter((l) => l.userId === user.id)
      .sort((a, b) => +new Date(b.acquiredAt) - +new Date(a.acquiredAt))
  },

  /* ------------------------------ admin ------------------------------ */
  async adminOverview(): Promise<AdminOverview> {
    await delay(300)
    requireAdmin()
    const db = getDB()
    advanceTasks(db)
    const today = new Date()
    today.setHours(0, 0, 0, 0)

    const series = Array.from({ length: 14 }, (_, i) => {
      const d = new Date(today.getTime() - (13 - i) * 86400000)
      const base = [320, 410, 380, 520, 610, 480, 720, 860, 640, 910, 780, 1020, 1180, 1240][i]
      return { label: `${d.getMonth() + 1}/${d.getDate()}`, value: base }
    })

    const succeeded = db.tasks.filter((t) => t.status === 'SUCCEEDED').length
    const failed = db.tasks.filter((t) => t.status === 'FAILED').length
    const total = succeeded + failed

    return {
      revenueTodayCents: 124000,
      tasksToday: 28,
      successRate: total ? Number(((succeeded / total) * 100).toFixed(1)) : 99.2,
      activeUsers: 186,
      series,
      distribution: [
        { label: '成功', value: succeeded || 42, tone: 'green' },
        { label: '失败', value: failed || 3, tone: 'red' },
        { label: '进行中', value: db.tasks.filter((t) => ['QUEUED', 'DOWNLOADING', 'DECRYPTING', 'VERIFYING'].includes(t.status)).length || 2, tone: 'blue' },
        { label: '已取消', value: db.tasks.filter((t) => t.status === 'CANCELED').length || 1, tone: 'gray' }
      ],
      queue: {
        running: Math.min(db.settings.concurrency, db.tasks.filter((t) => t.status === 'DOWNLOADING' || t.status === 'DECRYPTING').length),
        queued: db.tasks.filter((t) => t.status === 'QUEUED').length,
        concurrency: db.settings.concurrency
      }
    }
  },

  async adminUsers(q = '', role = 'ALL', status = 'ALL', page = 1, size = 20): Promise<UserPage> {
    await delay(260)
    requireAdmin()
    const db = getDB()
    const key = q.trim().toLowerCase()
    const mapped = db.users.map((u) => ({
      ...publicUser(u),
      balanceCents: db.wallets[u.id]?.balanceCents ?? 0,
      tasks: db.tasks.filter((t) => t.userId === u.id).length
    }))
    const filtered = mapped.filter((u) => {
      const matchKey = !key || u.email.toLowerCase().includes(key) || u.nickname.toLowerCase().includes(key)
      const matchRole = role === 'ALL' || u.role === role
      const matchStatus = status === 'ALL' || u.status === status
      return matchKey && matchRole && matchStatus
    })
    return {
      ...pageOf(filtered, page, size),
      registered: mapped.length,
      active: mapped.filter((u) => u.status === 'ACTIVE').length,
      banned: mapped.filter((u) => u.status === 'BANNED').length,
      balanceCents: mapped.reduce((s, u) => s + u.balanceCents, 0)
    }
  },

  async adminUpdateUser(id: string, patch: { status?: User['status']; role?: User['role'] }): Promise<void> {
    await delay(260)
    requireAdmin()
    const db = getDB()
    const user = db.users.find((u) => u.id === id)
    if (!user) throw new Error('用户不存在')
    if (patch.status) user.status = patch.status
    if (patch.role) user.role = patch.role
    db.auditLogs.unshift({
      id: uid('al'), actor: '管理员', action: 'USER_UPDATE', target: user.email,
      detail: `status=${user.status} role=${user.role}`, ip: '10.0.2.14', createdAt: nowISO()
    })
    saveDB()
  },

  async adminAdjustBalance(id: string, deltaCents: number, remark: string): Promise<number> {
    await delay(320)
    requireAdmin()
    const db = getDB()
    const wallet = db.wallets[id]
    if (!wallet) throw new Error('钱包不存在')
    if (wallet.balanceCents + deltaCents < 0) throw new Error('调整后余额不能为负')
    wallet.balanceCents += deltaCents
    if (deltaCents > 0) wallet.totalRechargeCents += deltaCents
    else wallet.totalSpendCents += -deltaCents
    db.transactions.unshift({
      id: uid('tx'), userId: id, type: 'ADJUST', amountCents: deltaCents,
      balanceAfterCents: wallet.balanceCents, remark: remark || '管理员调整', createdAt: nowISO()
    })
    const user = db.users.find((u) => u.id === id)
    db.auditLogs.unshift({
      id: uid('al'), actor: '管理员', action: 'BALANCE_ADJUST', target: user?.email ?? id,
      detail: `${deltaCents > 0 ? '+' : ''}¥${(deltaCents / 100).toFixed(2)} ${remark}`, ip: '10.0.2.14', createdAt: nowISO()
    })
    saveDB()
    return wallet.balanceCents
  },

  async adminTasks(status = 'ALL', query = '', page = 1, size = 20): Promise<TaskPage<Task & { email: string }>> {
    await delay(260)
    requireAdmin()
    const db = getDB()
    advanceTasks(db)
    const all = [...db.tasks]
      .sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))
      .map((t) => ({ ...t, email: db.users.find((u) => u.id === t.userId)?.email ?? '—' }))
    const key = query.trim().toLowerCase()
    let items = all
    if (status !== 'ALL') {
      items = status === 'RUNNING' ? items.filter((t) => RUNNING.includes(t.status)) : items.filter((t) => t.status === status)
    }
    if (key) {
      items = items.filter((t) =>
        t.appName.toLowerCase().includes(key) || t.email.toLowerCase().includes(key)
        || t.bundleId.toLowerCase().includes(key) || t.id.toLowerCase().includes(key))
    }
    return taskPage(all, items, page, size)
  },

  async adminApps(): Promise<AppItem[]> {
    await delay(240)
    requireAdmin()
    return [...getDB().apps].sort((a, b) => +new Date(b.updatedAt) - +new Date(a.updatedAt))
  },

  async adminSaveApp(input: Partial<AppItem> & { id?: string }): Promise<AppItem> {
    await delay(380)
    requireAdmin()
    const db = getDB()
    if (input.id) {
      const app = db.apps.find((a) => a.id === input.id)
      if (!app) throw new Error('应用不存在')
      Object.assign(app, input, { updatedAt: nowISO() })
      saveDB()
      return app
    }
    const app: AppItem = {
      id: nextId('app'),
      bundleId: input.bundleId ?? '',
      name: input.name ?? '',
      developer: input.developer ?? '',
      category: input.category ?? '工具',
      iconText: input.iconText ?? (input.name ?? 'A').slice(0, 2),
      iconHue: input.iconHue ?? 215,
      version: input.version ?? '1.0',
      sizeBytes: input.sizeBytes ?? 100 * 1024 * 1024,
      minIOS: input.minIOS ?? '13.0',
      executable: input.executable ?? input.name ?? '',
      sha256: input.sha256 ?? uid('sha'),
      priceDownloadCents: input.priceDownloadCents ?? db.settings.downloadPriceCents,
      priceDecryptCents: input.priceDecryptCents ?? db.settings.decryptPriceCents,
      downloads: 0,
      status: input.status ?? 'ON_SALE',
      verified: input.verified ?? true,
      arch: input.arch ?? ['arm64'],
      description: input.description ?? '',
      updatedAt: nowISO()
    }
    db.apps.unshift(app)
    saveDB()
    return app
  },

  async adminSetShelf(id: string, status: AppItem['status']): Promise<void> {
    await delay(240)
    requireAdmin()
    const db = getDB()
    const app = db.apps.find((a) => a.id === id)
    if (!app) throw new Error('应用不存在')
    app.status = status
    app.updatedAt = nowISO()
    db.auditLogs.unshift({
      id: uid('al'), actor: '管理员', action: 'APP_SHELF', target: `${app.name} ${app.version}`,
      detail: status === 'ON_SALE' ? '上架' : '下架', ip: '10.0.2.14', createdAt: nowISO()
    })
    saveDB()
  },

  async adminOrders(): Promise<{ orders: (RechargeOrder & { email: string })[]; transactions: (Transaction & { email: string })[] }> {
    await delay(280)
    requireAdmin()
    const db = getDB()
    const email = (id: string) => db.users.find((u) => u.id === id)?.email ?? '—'
    return {
      orders: [...db.orders].sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt)).map((o) => ({ ...o, email: email(o.userId) })),
      transactions: [...db.transactions].sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt)).map((t) => ({ ...t, email: email(t.userId) }))
    }
  },

  async adminRefundOrder(id: string): Promise<void> {
    await delay(360)
    requireAdmin()
    const db = getDB()
    const order = db.orders.find((o) => o.id === id)
    if (!order) throw new Error('订单不存在')
    if (order.status !== 'PAID') throw new Error('只有已支付的订单可以退款')
    const wallet = db.wallets[order.userId]
    const total = order.amountCents + order.bonusCents
    if (wallet.balanceCents < total) throw new Error('用户余额不足，无法回收本次充值')
    wallet.balanceCents -= total
    wallet.totalRechargeCents -= total
    order.status = 'REFUNDED'
    db.transactions.unshift({
      id: uid('tx'), userId: order.userId, type: 'REFUND', amountCents: -total,
      balanceAfterCents: wallet.balanceCents, remark: `充值退款 · ${order.id}`, refId: order.id, createdAt: nowISO()
    })
    db.auditLogs.unshift({
      id: uid('al'), actor: '管理员', action: 'ORDER_REFUND', target: order.id,
      detail: `退款 ¥${(total / 100).toFixed(2)}`, ip: '10.0.2.14', createdAt: nowISO()
    })
    saveDB()
  },

  async adminRetryTask(id: string): Promise<Task> {
    await delay(360)
    const admin = requireAdmin()
    const db = getDB()
    const task = db.tasks.find((t) => t.id === id)
    if (!task) throw new Error('任务不存在')
    if (task.status !== 'FAILED' && task.status !== 'CANCELED') throw new Error('只有失败或已取消的任务可以重试')
    const wallet = db.wallets[task.userId]
    if (wallet && task.priceCents > 0 && task.refundedCents === 0) {
      wallet.balanceCents += task.priceCents
      db.transactions.unshift({
        id: uid('tx'), userId: task.userId, type: 'REFUND', amountCents: task.priceCents,
        balanceAfterCents: wallet.balanceCents, remark: `管理员取消并退款 · ${task.appName} ${task.version}`,
        refId: task.id, createdAt: nowISO()
      })
    }
    task.status = 'QUEUED'
    task.progress = 6
    task.stage = '排队中'
    task.error = undefined
    task.refundedCents = 0
    task.finishedAt = undefined
    task.createdAt = nowISO()
    task.simulated = true
    task.willFail = false
    task.logs = []
    db.auditLogs.unshift({
      id: uid('al'), actor: admin.nickname || admin.email, action: 'TASK_RETRY', target: task.id,
      detail: `重试 ${task.appName} ${task.version}（管理员补偿，不额外扣费）`, ip: '10.0.2.14', createdAt: nowISO()
    })
    saveDB()
    return task
  },

  async adminAppleLogin(email: string, password: string, authCode?: string, storefront?: string): Promise<{ status: 'READY' | 'NEED_2FA'; message: string; account?: AppleAccount }> {
    await delay(400)
    requireAdmin()
    if (password.length < 8) throw new Error('密码至少 8 位')
    if (!authCode && /2fa/i.test(email)) {
      return { status: 'NEED_2FA', message: 'Apple 要求双重验证码，请填写后再次提交' }
    }
    const db = getDB()
    let account = db.appleAccounts.find((a) => a.email.toLowerCase() === email.trim().toLowerCase())
    if (!account) {
      account = {
        id: uid('aa'), email: email.trim(), storefront: storefront || 'cn', priority: db.appleAccounts.length + 1,
        credentialOk: false, sessionOk: true, status: 'READY', boundApps: 0, lastUsedAt: nowISO()
      }
      db.appleAccounts.push(account)
    } else {
      if (storefront) account.storefront = storefront
      account.sessionOk = true
      if (account.status !== 'DISABLED') account.status = 'READY'
      account.lastUsedAt = nowISO()
    }
    saveDB()
    return { status: 'READY', message: '已登录并生成文件密钥（演示）', account }
  },

  async adminBindCredential(email: string, file: File, storefront?: string): Promise<AppleAccount> {
    await delay(280)
    requireAdmin()
    if (file.size !== 16) throw new Error('credential.bin 必须是 16 字节')
    const db = getDB()
    let account = db.appleAccounts.find((a) => a.email.toLowerCase() === email.trim().toLowerCase())
    if (!account) {
      account = {
        id: uid('aa'), email: email.trim(), storefront: storefront || 'cn', priority: db.appleAccounts.length + 1,
        credentialOk: true, sessionOk: false, status: 'NEED_LOGIN', boundApps: 0, lastUsedAt: nowISO()
      }
      db.appleAccounts.push(account)
    } else {
      if (storefront) account.storefront = storefront
      account.credentialOk = true
    }
    saveDB()
    return account
  },

  async adminAppleUpdate(id: string, patch: Partial<Pick<AppleAccount, 'status' | 'priority' | 'storefront'>>): Promise<AppleAccount> {
    await delay(240)
    const admin = requireAdmin()
    const db = getDB()
    const account = db.appleAccounts.find((a) => a.id === id)
    if (!account) throw new Error('账号不存在')
    if (patch.status) {
      account.status = patch.status
      if (patch.status === 'DISABLED') account.sessionOk = false
    }
    if (patch.priority !== undefined) account.priority = patch.priority
    if (patch.storefront) account.storefront = patch.storefront
    db.auditLogs.unshift({
      id: uid('al'), actor: admin.nickname || admin.email, action: 'APPLE_UPDATE', target: account.email,
      detail: `status=${account.status} priority=${account.priority}`, ip: '10.0.2.14', createdAt: nowISO()
    })
    saveDB()
    return account
  },

  async adminPricing(): Promise<PricingConfig> {
    await delay(200)
    requireAdmin()
    const db = getDB()
    return {
      decryptPriceCents: db.settings.decryptPriceCents,
      downloadPriceCents: db.settings.downloadPriceCents,
      concurrency: db.settings.concurrency,
      packages: db.packages
    }
  },

  async adminSavePricing(cfg: PricingConfig): Promise<void> {
    await delay(360)
    requireAdmin()
    const db = getDB()
    db.settings.decryptPriceCents = cfg.decryptPriceCents
    db.settings.downloadPriceCents = cfg.downloadPriceCents
    db.settings.concurrency = cfg.concurrency
    db.packages = cfg.packages
    db.auditLogs.unshift({
      id: uid('al'), actor: '管理员', action: 'PRICING_UPDATE', target: '定价配置',
      detail: `脱壳 ¥${(cfg.decryptPriceCents / 100).toFixed(2)} / 下载 ¥${(cfg.downloadPriceCents / 100).toFixed(2)}`,
      ip: '10.0.2.14', createdAt: nowISO()
    })
    saveDB()
  },

  async adminAppleAccounts(): Promise<AppleAccount[]> {
    await delay(240)
    requireAdmin()
    return getDB().appleAccounts
  },

  async adminSyncAppleAccounts(): Promise<AppleAccount[]> {
    await delay(900)
    requireAdmin()
    const db = getDB()
    db.appleAccounts = db.appleAccounts.map((a) => ({
      ...a,
      lastUsedAt: a.sessionOk ? isoAgo(Math.floor(Math.random() * 120) + 5) : a.lastUsedAt
    }))
    db.auditLogs.unshift({
      id: uid('al'), actor: '管理员', action: 'APPLE_SYNC', target: 'Apple ID 池',
      detail: `${db.appleAccounts.filter((a) => a.status === 'READY').length} 个可用 / ${db.appleAccounts.length} 个账号`,
      ip: '10.0.2.14', createdAt: nowISO()
    })
    saveDB()
    return db.appleAccounts
  },

  async adminSettings(): Promise<Settings> {
    await delay(180)
    requireAdmin()
    return normalizeSettings(getDB().settings)
  },

  async adminMaintenance(): Promise<MaintenanceInfo> {
    await delay(140)
    requireAdmin()
    const s = normalizeSettings(getDB().settings)
    const total = 100 * 1024 ** 3
    const used = Math.round(total * 0.62)
    return {
      disk: {
        totalBytes: total,
        usableBytes: total - used,
        usedBytes: used,
        usedPercent: 62,
        level: used * 100 / total >= 90 ? 'CRITICAL' : used * 100 / total >= s.diskWarnPercent ? 'WARN' : 'OK'
      },
      autoCleanup: s.autoCleanup,
      artifactRetentionDays: s.artifactRetentionDays,
      uploadRetentionDays: s.uploadRetentionDays,
      logRetentionDays: s.logRetentionDays,
      diskWarnPercent: s.diskWarnPercent,
      lastRun: {
        artifactsDeleted: 3, artifactBytesFreed: 412 * 1024 * 1024,
        uploadsDeleted: 1, uploadBytesFreed: 104 * 1024 * 1024,
        workDirsDeleted: 2, logsDeleted: 48,
        orphanFilesDeleted: 0, orphanBytesFreed: 0, errors: 0,
        startedAt: isoAgo(42), finishedAt: isoAgo(41)
      }
    }
  },

  async adminCleanup(): Promise<CleanupReport> {
    await delay(600)
    requireAdmin()
    const report: CleanupReport = {
      artifactsDeleted: 2, artifactBytesFreed: 268 * 1024 * 1024,
      uploadsDeleted: 0, uploadBytesFreed: 0,
      workDirsDeleted: 1, logsDeleted: 12,
      orphanFilesDeleted: 1, orphanBytesFreed: 18 * 1024 * 1024, errors: 0,
      startedAt: nowISO(), finishedAt: nowISO()
    }
    getDB().auditLogs.unshift({
      id: uid('al'), actor: '管理员', action: 'MAINTENANCE_CLEANUP', target: '存储',
      detail: `产物 ${report.artifactsDeleted} 个，日志 ${report.logsDeleted} 条`, ip: '10.0.2.14', createdAt: nowISO()
    })
    saveDB()
    return report
  },

  async adminSaveSettings(patch: Partial<Settings>): Promise<Settings> {
    await delay(300)
    requireAdmin()
    const db = getDB()
    db.settings = normalizeSettings({ ...db.settings, ...patch })
    db.auditLogs.unshift({
      id: uid('al'), actor: '管理员', action: 'SETTINGS_UPDATE', target: '系统设置',
      detail: JSON.stringify(patch).slice(0, 120), ip: '10.0.2.14', createdAt: nowISO()
    })
    saveDB()
    return db.settings
  },

  async adminLogs(action = 'ALL', query = '', page = 1, size = 20): Promise<LogPage> {
    await delay(220)
    requireAdmin()
    const logs = [...getDB().auditLogs].sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))
    const key = query.trim().toLowerCase()
    const today = new Date().toDateString()
    const filtered = logs.filter((l) => {
      const matchAction = action === 'ALL' || l.action === action
      const matchKey = !key || l.actor.toLowerCase().includes(key) || l.target.toLowerCase().includes(key)
        || l.detail.toLowerCase().includes(key) || l.ip.includes(key)
      return matchAction && matchKey
    })
    return {
      ...pageOf(filtered, page, size),
      totalAll: logs.length,
      today: logs.filter((l) => new Date(l.createdAt).toDateString() === today).length
    }
  },

  async downloadTaskArtifact(task: Pick<Task, 'id' | 'appName' | 'version' | 'bundleId' | 'sizeBytes' | 'sha256'>) {
    await delay(200)
    saveText(`${task.appName}-${task.version}-manifest.txt`, [
      'AppReseal · 演示产物清单',
      '==============================',
      `应用: ${task.appName}`,
      `bundle id: ${task.bundleId}`,
      `版本: ${task.version}`,
      `大小: ${task.sizeBytes ?? 0} bytes`,
      `SHA256: ${task.sha256 ?? '—'}`,
      `cryptid: 0（已脱壳）`,
      `任务: ${task.id}`,
      '',
      '演示环境不提供真实 IPA 文件。'
    ].join('\n'))
  },

  async downloadLibraryArtifact(item: Pick<LibraryItem, 'id' | 'name' | 'version' | 'bundleId'>) {
    await delay(200)
    saveText(`${item.name}-${item.version}-manifest.txt`, [
      'AppReseal · 成品清单',
      '==============================',
      `应用: ${item.name}`,
      `bundle id: ${item.bundleId}`,
      `版本: ${item.version}`,
      `记录: ${item.id}`,
      '',
      '演示环境不提供真实 IPA 文件。'
    ].join('\n'))
  }
}

export type Api = typeof mockApi
export const apiMode = import.meta.env.VITE_API_MODE === 'real' ? 'real' : 'mock'
export const api: Api = apiMode === 'real' ? realApi : mockApi
