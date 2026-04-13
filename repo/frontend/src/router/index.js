import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/auth/LoginView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/dashboard/DashboardView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/users',
    name: 'UserManagement',
    component: () => import('../views/users/UserManagement.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['USER_MANAGE'] }
  },
  {
    path: '/rosters',
    name: 'RosterList',
    component: () => import('../views/roster/RosterList.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['ROSTER_VIEW'] }
  },
  {
    path: '/rosters/import',
    name: 'RosterImport',
    component: () => import('../views/roster/RosterImport.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['ROSTER_IMPORT'] }
  },
  {
    path: '/exam-sessions',
    name: 'ExamSessionList',
    component: () => import('../views/scheduling/ExamSessionList.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['SESSION_VIEW'] }
  },
  {
    path: '/exam-sessions/new',
    name: 'ExamSessionCreate',
    component: () => import('../views/scheduling/ExamSessionForm.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['SESSION_CREATE'] }
  },
  {
    path: '/exam-sessions/:id',
    name: 'ExamSessionDetail',
    component: () => import('../views/scheduling/ExamSessionDetail.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['SESSION_VIEW'] }
  },
  {
    path: '/campuses',
    name: 'CampusList',
    component: () => import('../views/rooms/CampusList.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['ROOM_MANAGE'] }
  },
  {
    path: '/rooms',
    name: 'RoomList',
    component: () => import('../views/rooms/RoomList.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['ROOM_MANAGE'] }
  },
  {
    path: '/proctor-assignments',
    name: 'ProctorList',
    component: () => import('../views/proctors/ProctorList.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['PROCTOR_ASSIGN'] }
  },
  {
    path: '/notifications',
    name: 'NotificationList',
    component: () => import('../views/notifications/NotificationList.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['NOTIFICATION_CREATE'] }
  },
  {
    path: '/notifications/new',
    name: 'NotificationForm',
    component: () => import('../views/notifications/NotificationForm.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['NOTIFICATION_CREATE'] }
  },
  {
    path: '/notifications/inbox',
    name: 'InboxView',
    component: () => import('../views/notifications/InboxView.vue'),
    meta: { requiresAuth: true, requiredRoles: ['STUDENT'] }
  },
  {
    path: '/subscriptions',
    name: 'SubscriptionSettings',
    component: () => import('../views/notifications/SubscriptionSettings.vue'),
    meta: { requiresAuth: true, requiredRoles: ['STUDENT'] }
  },
  {
    path: '/compliance/reviews',
    name: 'ComplianceReviewQueue',
    component: () => import('../views/compliance/ComplianceReviewQueue.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['COMPLIANCE_REVIEW'] }
  },
  {
    path: '/versions/:entityType/:entityId',
    name: 'VersionHistory',
    component: () => import('../components/versioning/VersionHistory.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/jobs',
    name: 'JobMonitor',
    component: () => import('../views/jobs/JobMonitor.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['JOB_MONITOR'] }
  },
  {
    path: '/anticheat',
    name: 'AntiCheatQueue',
    component: () => import('../views/anticheat/AntiCheatQueue.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['ANTICHEAT_REVIEW'] }
  },
  {
    path: '/audit',
    name: 'AuditLog',
    component: () => import('../views/audit/AuditLog.vue'),
    meta: { requiresAuth: true, requiredPermissions: ['AUDIT_VIEW'] }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
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

export default router
