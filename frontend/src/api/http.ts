import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiError, TokenResponse } from '@/types'

const baseURL = import.meta.env.VITE_API_BASE_URL || ''

export const http = axios.create({
  baseURL,
  timeout: 15000,
})

let refreshing: Promise<string> | null = null

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    const publicAuthRequest = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh']
      .some((path) => original?.url?.includes(path))
    if (error.response?.status !== 401 || !original || original._retried || publicAuthRequest) {
      return Promise.reject(error)
    }
    const refreshToken = localStorage.getItem('refreshToken')
    if (!refreshToken) {
      clearSession()
      return Promise.reject(error)
    }
    original._retried = true
    try {
      refreshing ??= axios
        .post<TokenResponse>(`${baseURL}/api/auth/refresh`, { refreshToken })
        .then(({ data }) => {
          storeTokens(data)
          return data.accessToken
        })
        .finally(() => {
          refreshing = null
        })
      const accessToken = await refreshing
      original.headers.Authorization = `Bearer ${accessToken}`
      return http(original)
    } catch (refreshError) {
      clearSession()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(refreshError)
    }
  },
)

export function storeTokens(data: TokenResponse) {
  localStorage.setItem('accessToken', data.accessToken)
  localStorage.setItem('refreshToken', data.refreshToken)
  localStorage.setItem('userProfile', JSON.stringify(data.user))
}

export function clearSession() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('userProfile')
}

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiError>(error)) {
    return error.response?.data?.message || error.message || '请求失败'
  }
  return error instanceof Error ? error.message : '请求失败'
}
