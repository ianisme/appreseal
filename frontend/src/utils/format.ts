export function money(cents: number, withSymbol = true): string {
  const v = (cents / 100).toFixed(2)
  return withSymbol ? `¥${v}` : v
}

export function fmtNumber(n: number): string {
  return n.toLocaleString('en-US')
}

export function fmtSize(bytes: number): string {
  if (!bytes) return '—'
  const mb = bytes / 1024 / 1024
  if (mb >= 1024) return `${(mb / 1024).toFixed(2)} GB`
  return `${mb.toFixed(1)} MB`
}

export function fmtDate(iso?: string, withTime = true): string {
  if (!iso) return '—'
  const d = new Date(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  const date = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
  return withTime ? `${date} ${p(d.getHours())}:${p(d.getMinutes())}` : date
}

export function relTime(iso?: string): string {
  if (!iso) return '—'
  const diff = Date.now() - new Date(iso).getTime()
  const m = Math.floor(diff / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  const d = Math.floor(h / 24)
  if (d < 30) return `${d} 天前`
  return fmtDate(iso, false)
}

export function shortSha(sha?: string): string {
  if (!sha) return '—'
  return `${sha.slice(0, 12)}…${sha.slice(-8)}`
}

export function initials(name: string): string {
  return name.trim().slice(0, 1).toUpperCase()
}
