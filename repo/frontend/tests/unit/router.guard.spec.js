import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../src/stores/auth.js'

/**
 * Unit tests for the Vue Router navigation guards covering authentication
 * checks, role-based access, and permission-based route blocking.
 */

// Minimal route definitions matching the real router
const routes = [
  { path: '/login', name: 'Login', component: { template: '<div>Login</div>' }, meta: { requiresAuth: false } },
  { path: '/dashboard', name: 'Dashboard', component: { template: '<div>Dashboard</div>' }, meta: { requiresAuth: true } },
  { path: '/users', name: 'UserManagement', component: { template: '<div>Users</div>' }, meta: { requiresAuth: true, requiredPermissions: ['USER_MANAGE'] } },
  { path: '/rosters', name: 'RosterList', component: { template: '<div>Rosters</div>' }, meta: { requiresAuth: true, requiredPermissions: ['ROSTER_VIEW'] } },
  { path: '/notifications/inbox', name: 'InboxView', component: { template: '<div>Inbox</div>' }, meta: { requiresAuth: true, requiredRoles: ['STUDENT'] } },
  { path: '/compliance/reviews', name: 'ComplianceReviewQueue', component: { template: '<div>Compliance</div>' }, meta: { requiresAuth: true, requiredPermissions: ['COMPLIANCE_REVIEW'] } },
]

function createTestRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes,
  })

  router.beforeEach((to, from, next) => {
    const authStore = useAuthStore()

    if (!authStore.isAuthenticated) {
      authStore.loadFromStorage()
    }

    if (to.meta.requiresAuth === false) {
      if (authStore.isAuthenticated && to.name === 'Login') {
        return next({ name: 'Dashboard' })
      }
      return next()
    }

    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
      return next({ name: 'Login', query: { redirect: to.fullPath } })
    }

    const requiredPerms = to.meta.requiredPermissions || []
    for (const perm of requiredPerms) {
      if (!authStore.hasPermission(perm)) {
        return next({ name: 'Dashboard' })
      }
    }

    const requiredRoles = to.meta.requiredRoles || []
    if (requiredRoles.length > 0 && !requiredRoles.includes(authStore.role)) {
      if (authStore.role !== 'ADMIN') {
        return next({ name: 'Dashboard' })
      }
    }

    next()
  })

  return router
}

function setupAuthStore(userData) {
  const authStore = useAuthStore()
  authStore.user = userData
  authStore.sessionToken = 'test-token'
  authStore.signingKey = 'test-key'
  authStore.isAuthenticated = true
  return authStore
}

describe('Router Guard', () => {
  let router

  beforeEach(() => {
    setActivePinia(createPinia())
    router = createTestRouter()
  })

  it('testUnauthenticatedRedirectsToLogin', async () => {
    // No user logged in
    const authStore = useAuthStore()
    authStore.isAuthenticated = false

    await router.push('/dashboard')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('Login')
  })

  it('testStudentCannotAccessAdmin', async () => {
    setupAuthStore({
      id: 1,
      username: 'student1',
      role: 'STUDENT',
      permissions: ['SUBSCRIPTION_MANAGE', 'INBOX_VIEW']
    })

    await router.push('/users')
    await router.isReady()

    // STUDENT does not have USER_MANAGE permission -> redirected to Dashboard
    expect(router.currentRoute.value.name).toBe('Dashboard')
  })

  it('testAdminCanAccessAll', async () => {
    setupAuthStore({
      id: 99,
      username: 'admin1',
      role: 'ADMIN',
      permissions: [] // ADMIN role bypasses permission checks
    })

    await router.push('/users')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('UserManagement')
  })

  it('testTeacherCanAccessRosters', async () => {
    setupAuthStore({
      id: 2,
      username: 'teacher1',
      role: 'HOMEROOM_TEACHER',
      permissions: ['ROSTER_VIEW', 'ROSTER_EXPORT', 'SESSION_VIEW', 'NOTIFICATION_CREATE', 'VERSION_VIEW']
    })

    await router.push('/rosters')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('RosterList')
  })

  it('testStudentCanAccessInbox', async () => {
    setupAuthStore({
      id: 1,
      username: 'student1',
      role: 'STUDENT',
      permissions: ['SUBSCRIPTION_MANAGE', 'INBOX_VIEW']
    })

    await router.push('/notifications/inbox')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('InboxView')
  })

  it('testStudentCannotAccessCompliance', async () => {
    setupAuthStore({
      id: 1,
      username: 'student1',
      role: 'STUDENT',
      permissions: ['SUBSCRIPTION_MANAGE', 'INBOX_VIEW']
    })

    await router.push('/compliance/reviews')
    await router.isReady()

    // STUDENT does not have COMPLIANCE_REVIEW permission -> redirected to Dashboard
    expect(router.currentRoute.value.name).toBe('Dashboard')
  })
})
