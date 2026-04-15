import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import router from '../../frontend/src/router/index.js'
import { useAuthStore } from '../../frontend/src/stores/auth.js'

/**
 * Router guard tests using the real router definition (routes + beforeEach).
 * This avoids test-only route copies drifting from production guard logic.
 */

function setAuthenticatedUser({ id, username, role, permissions }) {
  const authStore = useAuthStore()
  authStore.user = { id, username, role, permissions }
  authStore.sessionToken = 'test-token'
  authStore.signingKey = 'test-signing-key'
  authStore.isAuthenticated = true
  return authStore
}

async function resetAuthAndRoute() {
  const authStore = useAuthStore()
  authStore.clearSession()
  localStorage.removeItem('auth_session')
  await router.replace('/login')
}

describe('Router Guard (Real Router)', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    await resetAuthAndRoute()
  })

  it('redirects unauthenticated users to login', async () => {
    await router.push('/dashboard')
    expect(router.currentRoute.value.name).toBe('Login')
    expect(router.currentRoute.value.query.redirect).toBe('/dashboard')
  })

  it('redirects authenticated users away from login', async () => {
    setAuthenticatedUser({
      id: 99,
      username: 'admin1',
      role: 'ADMIN',
      permissions: []
    })

    await router.push('/dashboard')
    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('Dashboard')
  })

  it('allows admin to access user management', async () => {
    setAuthenticatedUser({
      id: 99,
      username: 'admin1',
      role: 'ADMIN',
      permissions: []
    })

    await router.push('/users')
    expect(router.currentRoute.value.name).toBe('UserManagement')
  })

  it('blocks student from admin-only pages', async () => {
    setAuthenticatedUser({
      id: 1,
      username: 'student1',
      role: 'STUDENT',
      permissions: ['INBOX_VIEW', 'SUBSCRIPTION_MANAGE']
    })

    await router.push('/users')
    expect(router.currentRoute.value.name).toBe('Dashboard')
  })

  it('allows student inbox route and blocks compliance queue', async () => {
    setAuthenticatedUser({
      id: 1,
      username: 'student1',
      role: 'STUDENT',
      permissions: ['INBOX_VIEW', 'SUBSCRIPTION_MANAGE']
    })

    await router.push('/notifications/inbox')
    expect(router.currentRoute.value.name).toBe('InboxView')

    await router.push('/compliance/reviews')
    expect(router.currentRoute.value.name).toBe('Dashboard')
  })
})
