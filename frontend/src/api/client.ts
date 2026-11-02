import { clearToken, getToken } from './token'

const BASE = import.meta.env.VITE_API_BASE ?? '/api'

/** 后端包络 { code, message, data } 的业务错误。 */
export class ApiError extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

let unauthorizedHandler: (() => void) | null = null

/** 由 main.ts 注册：401 时清 token 并跳登录。 */
export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  query?: Record<string, string | number | undefined>
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = BASE + path
  if (!query) return url
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') params.append(key, String(value))
  }
  const qs = params.toString()
  return qs ? url + '?' + qs : url
}

function handleUnauthorized(): void {
  clearToken()
  unauthorizedHandler?.()
}

/**
 * 统一请求入口：自动带 JWT、拆包络、401 清 token 并跳登录。
 * 成功返回 data，失败抛 ApiError。
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = 'Bearer ' + token
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(-1, '网络连接失败，请确认后端已启动')
  }

  const text = await response.text()
  let envelope: { code?: number; message?: string; data?: unknown } | null = null
  if (text) {
    try {
      envelope = JSON.parse(text)
    } catch {
      envelope = null
    }
  }

  if (response.status === 401 || envelope?.code === 2001) {
    handleUnauthorized()
    throw new ApiError(2001, envelope?.message ?? '未登录或登录已过期')
  }

  if (!response.ok) {
    throw new ApiError(envelope?.code ?? response.status, envelope?.message ?? '请求失败（HTTP ' + response.status + '）')
  }

  if (envelope === null) {
    throw new ApiError(-1, '响应不是合法的 JSON')
  }

  if (envelope.code !== 0) {
    throw new ApiError(envelope.code ?? -1, envelope.message ?? '请求失败')
  }

  return envelope.data as T
}
