import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { clearSession, http, storeTokens } from '@/api/http'
import type { TokenResponse, UserProfile } from '@/types'

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string')
}

function deserializeProfile(raw: string): UserProfile | null {
  const value: unknown = JSON.parse(raw)
  if (!value || typeof value !== 'object') return null
  const profile = value as Record<string, unknown>
  if (
    typeof profile.id !== 'number'
    || typeof profile.username !== 'string'
    || typeof profile.email !== 'string'
    || typeof profile.displayName !== 'string'
    || !isStringArray(profile.roles)
    || !isStringArray(profile.permissions)
  ) {
    return null
  }
  return {
    id: profile.id,
    username: profile.username,
    email: profile.email,
    displayName: profile.displayName,
    roles: profile.roles,
    permissions: profile.permissions,
  }
}

function initialProfile(): UserProfile | null {
  const raw = localStorage.getItem('userProfile')
  if (!raw) return null
  try {
    const profile = deserializeProfile(raw)
    if (!profile) clearSession()
    return profile
  } catch {
    clearSession()
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserProfile | null>(initialProfile())
  const authenticated = computed(() => Boolean(user.value && localStorage.getItem('accessToken')))

  async function login(payload: { username: string; password: string }) {
    const { data } = await http.post<TokenResponse>('/api/auth/login', payload)
    storeTokens(data)
    user.value = data.user
  }

  async function register(payload: {
    username: string
    email: string
    password: string
    displayName: string
  }) {
    const { data } = await http.post<TokenResponse>('/api/auth/register', payload)
    storeTokens(data)
    user.value = data.user
  }

  async function fetchMe() {
    const { data } = await http.get<UserProfile>('/api/auth/me')
    user.value = data
    localStorage.setItem('userProfile', JSON.stringify(data))
  }

  async function logout() {
    const refreshToken = localStorage.getItem('refreshToken')
    try {
      if (refreshToken) await http.post('/api/auth/logout', { refreshToken })
    } finally {
      clearSession()
      user.value = null
    }
  }

  function hasPermission(permission: string) {
    return user.value?.permissions.includes(permission) ?? false
  }

  return { user, authenticated, login, register, fetchMe, logout, hasPermission }
})
