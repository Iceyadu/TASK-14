import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import router from '../../frontend/src/router/index.js'
import { useAuthStore } from '../../frontend/src/stores/auth.js'

/**
 * Tests that confirm the router guard redirects users back to /login
 * when their session is cleared (simulating session expiry or token invalidation).
 * These complement the lockout E2E tests with fast unit-level coverage of the
 * guard logic itself.
 */

function setAuthenticatedUser({ id, username, role, permissions }) {
  const authStore = useAuthStore()
  authStore.user = { id, username, role, permissions }
  authStore.sessionToken = 'valid-token'
  authStore.signingKey = 'signing-key'
  authStore.isAuthenticated = true
}

describe('Session expiry router guard behaviour', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    const authStore = useAuthStore()
    authStore.clearSession()
    localStorage.removeItem('auth_session')
    await router.replace('/login')
  })

  it('redirects to login with redirect param when session is not active', async () => {
    await router.push('/notifications')
    expect(router.currentRoute.value.name).toBe('Login')
    expect(router.currentRoute.value.query.redirect).toBe('/notifications')
  })

  it('redirects protected admin route to login after session cleared', async () => {
    setAuthenticatedUser({ id: 1, username: 'admin', role: 'ADMIN', permissions: [] })
    await router.push('/users')
    expect(router.currentRoute.value.name).toBe('UserManagement')

    // Simulate session expiry by clearing state
    const authStore = useAuthStore()
    authStore.clearSession()

    await router.push('/audit')
    expect(router.currentRoute.value.name).toBe('Login')
  })

  it('redirects protected student route to login after session cleared', async () => {
    setAuthenticatedUser({ id: 5, username: 'student', role: 'STUDENT',
      permissions: ['INBOX_VIEW', 'SUBSCRIPTION_MANAGE'] })
    await router.push('/notifications/inbox')
    expect(router.currentRoute.value.name).toBe('InboxView')

    const authStore = useAuthStore()
    authStore.clearSession()

    await router.push('/notifications/inbox')
    expect(router.currentRoute.value.name).toBe('Login')
  })

  it('allows re-login after session cleared and sets redirect correctly', async () => {
    // Simulate navigating to protected page with no session
    await router.push('/rosters')
    expect(router.currentRoute.value.name).toBe('Login')
    expect(router.currentRoute.value.query.redirect).toBe('/rosters')

    // Re-authenticate and follow redirect
    setAuthenticatedUser({ id: 1, username: 'admin', role: 'ADMIN', permissions: [] })
    await router.push('/rosters')
    expect(router.currentRoute.value.name).toBe('RosterList')
  })

  it('blocks access to all admin routes when not authenticated', async () => {
    const adminRoutes = ['/users', '/audit', '/jobs', '/campuses', '/rooms', '/proctor-assignments']
    for (const route of adminRoutes) {
      await router.push(route)
      expect(router.currentRoute.value.name).toBe('Login',
        `Expected redirect to Login for ${route}`)
    }
  })

  it('redirects authenticated-then-expired user for teacher-level route', async () => {
    setAuthenticatedUser({ id: 10, username: 'teacher', role: 'HOMEROOM_TEACHER',
      permissions: ['NOTIFICATION_VIEW', 'ROSTER_VIEW', 'EXAM_SESSION_VIEW', 'EXAM_SESSION_MANAGE'] })
    await router.push('/exam-sessions')
    expect(router.currentRoute.value.name).toBe('ExamSessionList')

    const authStore = useAuthStore()
    authStore.clearSession()

    await router.push('/exam-sessions')
    expect(router.currentRoute.value.name).toBe('Login')
  })
})
