import type {
  AdminOverview, AppItem, AppleAccount, AuditLog, CleanupReport, LibraryItem, LogPage, MaintenanceInfo,
  PricingConfig, RechargeOrder, RechargePackage, Settings, StoreMarket, StoreSearchResult, Task,
  TaskPage, Transaction, UploadProgress, UploadStatus, User, UserPage
} from '@/types'
import { request, setToken, triggerDownload, uploadBytes, ApiError } from './http'

type AuthBody = { token: string; user: User }
type DownloadBody = { app: AppItem; balanceCents: number; libraryId?: string }

function q(params: Record<string, string | undefined>) {
  const usp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v) usp.set(k, v)
  }
  const s = usp.toString()
  return s ? `?${s}` : ''
}

async function session(body: AuthBody): Promise<User> {
  setToken(body.token)
  return body.user
}

/** 与 mock `api` 同签名，走 `/api/v1`。页面不需要知道当前是哪种模式。 */
export const realApi = {
  async login(email: string, password: string) {
    return session(await request<AuthBody>('/auth/login', {
      method: 'POST', body: JSON.stringify({ email, password })
    }, false))
  },

  async register(email: string, password: string, nickname: string) {
    return session(await request<AuthBody>('/auth/register', {
      method: 'POST', body: JSON.stringify({ email, password, nickname })
    }, false))
  },

  async me(): Promise<User | null> {
    try {
      return await request<User>('/auth/me')
    } catch (e) {
      if (e instanceof Error && 'status' in e && (e as { status: number }).status === 401) return null
      if (e instanceof Error && e.message.includes('登录')) return null
      throw e
    }
  },

  async logout() {
    setToken(null)
  },

  updateProfile(patch: { nickname?: string; avatarColor?: string }) {
    return request<User>('/auth/profile', { method: 'PATCH', body: JSON.stringify(patch) })
  },

  async changePassword(oldPassword: string, newPassword: string) {
    const body = await request<AuthBody>('/auth/password', {
      method: 'POST', body: JSON.stringify({ oldPassword, newPassword })
    })
    if (body?.token) setToken(body.token)
  },

  forgotPassword(email: string) {
    return request<{ message: string }>('/auth/forgot-password', {
      method: 'POST', body: JSON.stringify({ email })
    }, false)
  },

  async resetPassword(email: string, code: string, newPassword: string) {
    return session(await request<AuthBody>('/auth/reset-password', {
      method: 'POST', body: JSON.stringify({ email, code, newPassword })
    }, false))
  },

  site() {
    return request<{ maintenance: boolean; announcement: string; decryptPriceCents: number; downloadPriceCents: number }>(
      '/site', {}, false)
  },

  listApps(params: { q?: string; category?: string; sort?: string } = {}) {
    return request<AppItem[]>('/apps' + q(params), {}, false)
  },

  getApp(bundleId: string) {
    return request<AppItem>('/apps/' + encodeURIComponent(bundleId), {}, false)
  },

  storeMarkets() {
    return request<StoreMarket[]>('/store/markets')
  },

  searchStore(query: string, country = 'cn') {
    return request<StoreSearchResult>('/store/search' + q({ q: query, country }))
  },

  downloadApp(bundleId: string) {
    return request<DownloadBody>('/apps/' + encodeURIComponent(bundleId) + '/download', { method: 'POST' })
  },

  listTasks(status?: string, page = 1, size = 20) {
    return request<TaskPage>('/tasks' + q({
      status: status && status !== 'ALL' ? status : undefined,
      page: String(page),
      size: String(size)
    }))
  },

  getTask(id: string) {
    return request<Task>('/tasks/' + encodeURIComponent(id))
  },

  createTask(input: { source: 'STORE' | 'UPLOAD'; bundleId: string; appName: string; version: string; priceCents: number; storefront?: string }) {
    return request<Task>('/tasks', {
      method: 'POST',
      body: JSON.stringify({
        source: input.source,
        bundleId: input.bundleId,
        appName: input.appName,
        version: input.version,
        storefront: input.storefront
      })
    })
  },

  /** 分片上传加密 IPA。中断后用同一文件再次提交，会从服务端已收到的偏移继续。 */
  async uploadTask(file: File, onProgress?: (info: UploadProgress) => void) {
    const chunkSize = 4 * 1024 * 1024
    const key = `appreseal.upload.${file.name}.${file.size}.${file.lastModified}`
    let id = sessionStorage.getItem(key)
    let status: UploadStatus | null = null
    if (id) {
      try {
        status = await request<UploadStatus>('/uploads/' + encodeURIComponent(id))
        if (status.size !== file.size) status = null
      } catch {
        status = null
      }
    }
    if (!status) {
      status = await request<UploadStatus>('/uploads', {
        method: 'POST',
        body: JSON.stringify({ filename: file.name, size: file.size })
      })
      id = status.id
      sessionStorage.setItem(key, id)
    }
    while (status.received < file.size) {
      const start = status.received
      const end = Math.min(file.size, start + chunkSize)
      const res = await uploadBytes('/uploads/' + encodeURIComponent(status.id), file.slice(start, end), start, (loaded) => {
        onProgress?.({ loaded, total: file.size, phase: 'upload' })
      })
      const body = await res.json().catch(() => ({}))
      if (res.status === 409 && typeof body.received === 'number') {
        status = { ...status, received: body.received }
        continue
      }
      if (!res.ok) {
        if (res.status === 401) setToken(null)
        throw new ApiError(res.status, body.message || '上传失败', body.received)
      }
      status = body as UploadStatus
    }
    onProgress?.({ loaded: file.size, total: file.size, phase: 'verify' })
    const task = await request<Task>('/uploads/' + encodeURIComponent(status.id) + '/complete', { method: 'POST' })
    sessionStorage.removeItem(key)
    return task
  },

  retryTask(id: string) {
    return request<Task>('/tasks/' + encodeURIComponent(id) + '/retry', { method: 'POST' })
  },

  cancelTask(id: string) {
    return request<Task>('/tasks/' + encodeURIComponent(id) + '/cancel', { method: 'POST' })
  },

  getWallet() {
    return request<{ balanceCents: number; totalRechargeCents: number; totalSpendCents: number }>('/wallet')
  },

  listTransactions() {
    return request<Transaction[]>('/transactions')
  },

  listPackages() {
    return request<RechargePackage[]>('/packages', {}, false)
  },

  createRecharge(packageId: string, method: 'ALIPAY' | 'WECHAT') {
    return request<RechargeOrder>('/recharges', { method: 'POST', body: JSON.stringify({ packageId, method }) })
  },

  payRecharge(orderId: string) {
    return request<{ balanceCents: number }>('/recharges/' + encodeURIComponent(orderId) + '/pay', { method: 'POST' })
  },

  getRecharge(orderId: string) {
    return request<RechargeOrder>('/recharges/' + encodeURIComponent(orderId))
  },

  listLibrary() {
    return request<LibraryItem[]>('/library')
  },

  async downloadTaskArtifact(task: Pick<Task, 'id' | 'appName' | 'version'>) {
    const link = await request<{ url: string }>('/tasks/' + encodeURIComponent(task.id) + '/download-link', {
      method: 'POST'
    })
    triggerDownload(link.url)
  },

  async downloadLibraryArtifact(item: Pick<LibraryItem, 'id' | 'name' | 'version'>) {
    const link = await request<{ url: string }>('/library/' + encodeURIComponent(item.id) + '/download-link', {
      method: 'POST'
    })
    triggerDownload(link.url)
  },

  adminOverview() {
    return request<AdminOverview>('/admin/overview')
  },

  adminUsers(query = '', role = 'ALL', status = 'ALL', page = 1, size = 20) {
    return request<UserPage>('/admin/users' + q({
      q: query || undefined,
      role: role === 'ALL' ? undefined : role,
      status: status === 'ALL' ? undefined : status,
      page: String(page),
      size: String(size)
    }))
  },

  async adminUpdateUser(id: string, patch: { status?: User['status']; role?: User['role'] }) {
    await request('/admin/users/' + encodeURIComponent(id), { method: 'PATCH', body: JSON.stringify(patch) })
  },

  async adminAdjustBalance(id: string, deltaCents: number, remark: string) {
    const res = await request<{ balanceCents: number }>('/admin/users/' + encodeURIComponent(id) + '/balance', {
      method: 'POST', body: JSON.stringify({ deltaCents, remark })
    })
    return res.balanceCents
  },

  adminTasks(status = 'ALL', query = '', page = 1, size = 20) {
    return request<TaskPage<Task & { email: string }>>('/admin/tasks' + q({
      status: status === 'ALL' ? undefined : status,
      q: query || undefined,
      page: String(page),
      size: String(size)
    }))
  },

  adminApps() {
    return request<AppItem[]>('/admin/apps')
  },

  adminSaveApp(input: Partial<AppItem> & { id?: string }) {
    return request<AppItem>('/admin/apps', { method: 'POST', body: JSON.stringify(input) })
  },

  async adminSetShelf(id: string, status: AppItem['status']) {
    await request('/admin/apps/' + encodeURIComponent(id) + '/shelf', {
      method: 'PATCH', body: JSON.stringify({ status })
    })
  },

  adminOrders() {
    return request<{ orders: (RechargeOrder & { email: string })[]; transactions: (Transaction & { email: string })[] }>('/admin/orders')
  },

  async adminRefundOrder(id: string) {
    await request('/admin/orders/' + encodeURIComponent(id) + '/refund', { method: 'POST' })
  },

  adminRetryTask(id: string) {
    return request<Task>('/admin/tasks/' + encodeURIComponent(id) + '/retry', { method: 'POST' })
  },

  adminAppleLogin(email: string, password: string, authCode?: string, storefront?: string) {
    return request<{ status: 'READY' | 'NEED_2FA'; message: string; account?: AppleAccount }>('/admin/apple-accounts/login', {
      method: 'POST',
      body: JSON.stringify({ email, password, authCode: authCode || undefined, storefront })
    })
  },

  adminBindCredential(email: string, file: File, storefront?: string) {
    const body = new FormData()
    body.append('email', email)
    if (storefront) body.append('storefront', storefront)
    body.append('file', file)
    return request<AppleAccount>('/admin/apple-accounts/credential', { method: 'POST', body })
  },

  adminAppleUpdate(id: string, patch: Partial<Pick<AppleAccount, 'status' | 'priority' | 'storefront'>>) {
    return request<AppleAccount>('/admin/apple-accounts/' + encodeURIComponent(id), {
      method: 'PATCH', body: JSON.stringify(patch)
    })
  },

  adminPricing() {
    return request<PricingConfig>('/admin/pricing')
  },

  async adminSavePricing(cfg: PricingConfig) {
    await request('/admin/pricing', { method: 'PUT', body: JSON.stringify(cfg) })
  },

  adminAppleAccounts() {
    return request<AppleAccount[]>('/admin/apple-accounts')
  },

  adminSyncAppleAccounts() {
    return request<AppleAccount[]>('/admin/apple-accounts/sync', { method: 'POST' })
  },

  adminSettings() {
    return request<Settings>('/admin/settings')
  },

  adminSaveSettings(patch: Partial<Settings>) {
    return request<Settings>('/admin/settings', { method: 'PATCH', body: JSON.stringify(patch) })
  },

  adminMaintenance() {
    return request<MaintenanceInfo>('/admin/maintenance')
  },

  adminCleanup() {
    return request<CleanupReport>('/admin/maintenance/cleanup', { method: 'POST' })
  },

  adminLogs(action = 'ALL', query = '', page = 1, size = 20) {
    return request<LogPage>('/admin/logs' + q({
      action: action === 'ALL' ? undefined : action,
      q: query || undefined,
      page: String(page),
      size: String(size)
    }))
  }
}
