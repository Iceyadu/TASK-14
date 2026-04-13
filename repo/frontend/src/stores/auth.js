import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const sessionToken = ref(null)
  const signingKey = ref(null)
  const isAuthenticated = ref(false)

  const role = computed(() => user.value?.role || null)
  const userId = computed(() => user.value?.id || null)
  const permissions = computed(() => user.value?.permissions || [])

  function hasPermission(perm) {
    if (!user.value) return false
    if (user.value.role === 'ADMIN') return true
    return permissions.value.includes(perm)
  }

  function hasRole(r) {
    return role.value === r
  }

  async function login(credentials) {
    const { authApi } = await import('../api/client.js')
    const response = await authApi.login(credentials)
    const data = response.data
    user.value = data.user
    sessionToken.value = data.sessionToken
    signingKey.value = data.signingKey
    isAuthenticated.value = true
    persistToStorage()
    return data
  }

  async function logout() {
    try {
      const { authApi } = await import('../api/client.js')
      await authApi.logout()
    } catch {
      // ignore logout errors
    }
    clearSession()
  }

  async function refreshSession() {
    try {
      const { authApi } = await import('../api/client.js')
      const response = await authApi.getSession()
      if (response.data?.user) {
        user.value = response.data.user
        persistToStorage()
      }
    } catch {
      clearSession()
    }
  }

  function loadFromStorage() {
    try {
      const stored = localStorage.getItem('auth_session')
      if (stored) {
        const parsed = JSON.parse(stored)
        user.value = parsed.user
        sessionToken.value = parsed.sessionToken
        signingKey.value = parsed.signingKey
        isAuthenticated.value = true
      }
    } catch {
      clearSession()
    }
  }

  function persistToStorage() {
    localStorage.setItem('auth_session', JSON.stringify({
      user: user.value,
      sessionToken: sessionToken.value,
      signingKey: signingKey.value
    }))
  }

  function clearSession() {
    user.value = null
    sessionToken.value = null
    signingKey.value = null
    isAuthenticated.value = false
    localStorage.removeItem('auth_session')
  }

  return {
    user,
    sessionToken,
    signingKey,
    isAuthenticated,
    role,
    userId,
    permissions,
    hasPermission,
    hasRole,
    login,
    logout,
    refreshSession,
    loadFromStorage,
    clearSession
  }
})
