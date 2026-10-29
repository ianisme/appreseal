const TOKEN_KEY = 'appreseal.token'

export const apiBase = (import.meta.env.VITE_API_BASE || '/api/v1').replace(/\/$/, '')

export function token(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(value: string | null) {
  if (value) localStorage.setItem(TOKEN_KEY, value)
  else localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  status: number
  received?: number
  constructor(status: number, message: string, received?: number) {
    super(message)
    this.status = status
    this.received = received
  }
}

export async function request<T>(path: string, init: RequestInit = {}, auth = true): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body != null && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (auth) {
    const t = token()
    if (t) headers.set('Authorization', `Bearer ${t}`)
  }
  let res: Response
  try {
    res = await fetch(apiBase + path, { ...init, headers })
  } catch {
    throw new ApiError(0, '无法连接服务器')
  }
  if (res.status === 401) {
    setToken(null)
  }
  if (!res.ok) {
    let message = res.status === 401 ? '登录已过期，请重新登录' : '请求失败'
    try {
      const body = await res.json()
      if (body && typeof body.message === 'string' && body.message) message = body.message
    } catch { /* 非 JSON */ }
    throw new ApiError(res.status, message)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  if (!text) return undefined as T
  return JSON.parse(text) as T
}

/**
 * 直接交给浏览器下载：服务端返回 Content-Disposition，文件名由服务端决定。
 * 不走 fetch/blob，避免大文件先全量读进内存；断点续传、暂停恢复由浏览器自己做。
 */
export function triggerDownload(url: string) {
  let href = url
  if (!/^https?:\/\//i.test(url) && /^https?:\/\//i.test(apiBase)) {
    href = new URL(url, apiBase).toString()
  }
  const a = document.createElement('a')
  a.href = href
  a.rel = 'noopener'
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

/** 分片上传用：带进度，409 时把服务端已收到的偏移放在 error.received。 */
export function uploadBytes(path: string, body: Blob, offset: number, onProgress?: (loaded: number) => void): Promise<Response> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', apiBase + path)
    xhr.setRequestHeader('Content-Type', 'application/octet-stream')
    xhr.setRequestHeader('X-Upload-Offset', String(offset))
    const t = token()
    if (t) xhr.setRequestHeader('Authorization', `Bearer ${t}`)
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress?.(offset + event.loaded)
    }
    xhr.onload = () => {
      resolve(new Response(xhr.responseText, { status: xhr.status, headers: { 'Content-Type': xhr.getResponseHeader('Content-Type') || 'application/json' } }))
    }
    xhr.onerror = () => reject(new ApiError(0, '无法连接服务器'))
    xhr.onabort = () => reject(new ApiError(0, '上传已取消'))
    xhr.send(body)
  })
}

export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export async function download(path: string, fallbackName: string) {
  const headers = new Headers()
  const t = token()
  if (t) headers.set('Authorization', `Bearer ${t}`)
  let res: Response
  try {
    res = await fetch(apiBase + path, { headers })
  } catch {
    throw new ApiError(0, '无法连接服务器')
  }
  if (!res.ok) {
    let message = '下载失败'
    try {
      const body = await res.json()
      if (body?.message) message = body.message
    } catch { /* 二进制错误页 */ }
    if (res.status === 401) setToken(null)
    throw new ApiError(res.status, message)
  }
  const blob = await res.blob()
  const cd = res.headers.get('Content-Disposition') || ''
  const star = /filename\*=UTF-8''([^;]+)/i.exec(cd)
  const plain = /filename="([^"]+)"/i.exec(cd)
  const name = star ? decodeURIComponent(star[1]) : plain ? plain[1] : fallbackName
  saveBlob(blob, name)
}
