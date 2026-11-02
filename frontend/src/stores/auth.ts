import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '../api'
import type { UserView } from '../api'
import { clearToken, getToken, setToken } from '../api/token'

/** 登录态：token 存 localStorage，用户信息放内存。 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const user = ref<UserView | null>(null)
  const isLoggedIn = computed(() => token.value !== null)

  async function login(username: string, password: string): Promise<void> {
    const data = await authApi.login({ username, password })
    setToken(data.token)
    token.value = data.token
    user.value = data.user
  }

  async function register(username: string, password: string, nickname: string): Promise<void> {
    await authApi.register({ username, password, nickname })
  }

  /** 刷新页面后用已有 token 恢复用户信息；失败即视为未登录。 */
  async function restore(): Promise<void> {
    if (!token.value) return
    try {
      user.value = await authApi.me()
    } catch {
      logout()
    }
  }

  function logout(): void {
    clearToken()
    token.value = null
    user.value = null
  }

  return { token, user, isLoggedIn, login, register, restore, logout }
})
