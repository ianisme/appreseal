export type Role = 'USER' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'BANNED'

export interface User {
  id: string
  email: string
  nickname: string
  role: Role
  status: UserStatus
  avatarColor: string
  createdAt: string
  lastLoginAt: string
  password?: string
}

export type AppStatus = 'ON_SALE' | 'OFF_SHELF' | 'DRAFT'

export interface AppItem {
  id: string
  bundleId: string
  name: string
  developer: string
  category: string
  iconText: string
  iconHue: number
  version: string
  sizeBytes: number
  minIOS: string
  executable: string
  sha256: string
  priceDownloadCents: number
  priceDecryptCents: number
  downloads: number
  status: AppStatus
  verified: boolean
  arch: string[]
  description: string
  updatedAt: string
}

export type TaskStatus =
  | 'QUEUED'
  | 'DOWNLOADING'
  | 'DECRYPTING'
  | 'VERIFYING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELED'

export interface TaskLog {
  at: string
  level: 'INFO' | 'WARN' | 'ERROR' | 'OK'
  message: string
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
  pages: number
}

export interface TaskPage<T = Task> extends Page<T> {
  running: number
  succeeded: number
  failed: number
  canceled: number
  revenueCents: number
}

export interface UploadProgress {
  loaded: number
  total: number
  phase: 'upload' | 'verify'
}

export interface UploadStatus {
  id: string
  filename: string
  size: number
  received: number
  chunkSize: number
  expiresAt: string
}

export interface Task {
  id: string
  userId: string
  appName: string
  bundleId: string
  version: string
  storefront?: string
  source: 'STORE' | 'UPLOAD'
  status: TaskStatus
  progress: number
  stage: string
  priceCents: number
  refundedCents: number
  createdAt: string
  finishedAt?: string
  error?: string
  sizeBytes?: number
  sha256?: string
  simulated: boolean
  artifactPurged?: boolean
  willFail?: boolean
  logs: TaskLog[]
}

export type TxType = 'RECHARGE' | 'DECRYPT' | 'DOWNLOAD' | 'REFUND' | 'ADJUST'

export interface Transaction {
  id: string
  userId: string
  type: TxType
  amountCents: number
  balanceAfterCents: number
  remark: string
  refId?: string
  createdAt: string
}

export interface RechargeOrder {
  id: string
  userId: string
  amountCents: number
  bonusCents: number
  method: 'ALIPAY' | 'WECHAT'
  status: 'PENDING' | 'PAID' | 'REFUNDED'
  createdAt: string
  paidAt?: string
}

export interface RechargePackage {
  id: string
  amountCents: number
  bonusCents: number
  recommended?: boolean
}

export interface StoreHit {
  bundleId: string
  name: string
  developer: string
  version: string
  iconText: string
  iconHue: number
  inLibrary: boolean
  iconUrl?: string
  trackId?: number
}

export interface StoreMarket {
  code: string
  name: string
  accounts: number
}

export interface StoreSearchResult {
  storefront: string
  storefrontName: string
  matchedAccounts: number
  hits: StoreHit[]
}

export interface AppleAccount {
  id: string
  email: string
  storefront?: string
  priority: number
  credentialOk: boolean
  sessionOk: boolean
  status: 'READY' | 'NEED_LOGIN' | 'DISABLED'
  boundApps: number
  lastUsedAt: string
}

export interface LibraryItem {
  id: string
  userId: string
  appId: string
  bundleId: string
  name: string
  version: string
  source: 'DECRYPT' | 'PURCHASE'
  acquiredAt: string
  freeUntil: string
  taskId?: string
}

export interface AuditLog {
  id: string
  actor: string
  action: string
  target: string
  detail: string
  ip: string
  createdAt: string
}

export interface UserPage extends Page<User & { balanceCents: number; tasks: number }> {
  registered: number
  active: number
  banned: number
  balanceCents: number
}

export interface LogPage extends Page<AuditLog> {
  totalAll: number
  today: number
}

export interface TableColumn {
  key: string
  label: string
  width?: string
  align?: 'left' | 'right' | 'center'
  mono?: boolean
}

export interface Settings {
  decryptPriceCents: number
  downloadPriceCents: number
  concurrency: number
  maintenance: boolean
  announcement: string
  freeDownloadDays: number
  artifactRetentionDays: number
  uploadRetentionDays: number
  logRetentionDays: number
  autoCleanup: boolean
  diskWarnPercent: number
}

export interface DiskInfo {
  totalBytes: number
  usableBytes: number
  usedBytes: number
  usedPercent: number
  level: 'OK' | 'WARN' | 'CRITICAL' | 'UNKNOWN'
}

export interface CleanupReport {
  artifactsDeleted: number
  artifactBytesFreed: number
  uploadsDeleted: number
  uploadBytesFreed: number
  workDirsDeleted: number
  logsDeleted: number
  orphanFilesDeleted: number
  orphanBytesFreed: number
  errors: number
  startedAt: string | null
  finishedAt: string | null
}

export interface MaintenanceInfo {
  disk: DiskInfo
  autoCleanup: boolean
  artifactRetentionDays: number
  uploadRetentionDays: number
  logRetentionDays: number
  diskWarnPercent: number
  lastRun: CleanupReport
}

export interface PricingConfig {
  decryptPriceCents: number
  downloadPriceCents: number
  concurrency: number
  packages: RechargePackage[]
}

export interface AdminOverview {
  revenueTodayCents: number
  tasksToday: number
  successRate: number
  activeUsers: number
  series: { label: string; value: number }[]
  distribution: { label: string; value: number; tone: string }[]
  queue: { running: number; queued: number; concurrency: number }
}
