import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

import LoginView from '../../frontend/src/views/auth/LoginView.vue'
import NotificationForm from '../../frontend/src/views/notifications/NotificationForm.vue'
import NotificationList from '../../frontend/src/views/notifications/NotificationList.vue'
import UserManagement from '../../frontend/src/views/users/UserManagement.vue'
import RosterList from '../../frontend/src/views/roster/RosterList.vue'
import ExamSessionList from '../../frontend/src/views/scheduling/ExamSessionList.vue'
import ExamSessionForm from '../../frontend/src/views/scheduling/ExamSessionForm.vue'
import ExamSessionDetail from '../../frontend/src/views/scheduling/ExamSessionDetail.vue'
import ComplianceReviewQueue from '../../frontend/src/views/compliance/ComplianceReviewQueue.vue'
import JobMonitor from '../../frontend/src/views/jobs/JobMonitor.vue'
import AntiCheatQueue from '../../frontend/src/views/anticheat/AntiCheatQueue.vue'
import AuditLog from '../../frontend/src/views/audit/AuditLog.vue'
import RoomList from '../../frontend/src/views/rooms/RoomList.vue'
import CampusList from '../../frontend/src/views/rooms/CampusList.vue'
import ProctorList from '../../frontend/src/views/proctors/ProctorList.vue'

const fixtures = vi.hoisted(() => {
  const makeList = () => Promise.resolve({ data: { items: [], totalPages: 1, totalItems: 0 } })

  const mockRoute = { params: {}, query: {} }
  const mockRouter = { push: vi.fn() }
  const mockAuthStore = {
    role: 'ADMIN',
    isAuthenticated: true,
    hasPermission: vi.fn(() => true),
    login: vi.fn().mockResolvedValue(undefined),
    loadFromStorage: vi.fn()
  }

  const mockApis = {
    examSessionApi: {
      list: vi.fn(makeList),
      get: vi.fn(() => Promise.resolve({
        data: { id: 1, name: 'Session 1', status: 'DRAFT', termId: 1, campusId: 1, roomId: 1, classIds: [] }
      })),
      create: vi.fn(() => Promise.resolve({ data: {} })),
      update: vi.fn(() => Promise.resolve({ data: {} })),
      submitReview: vi.fn(() => Promise.resolve({ data: {} })),
      publish: vi.fn(() => Promise.resolve({ data: {} })),
      unpublish: vi.fn(() => Promise.resolve({ data: {} })),
      archive: vi.fn(() => Promise.resolve({ data: {} })),
      getStudentSchedule: vi.fn(() => Promise.resolve({ data: [] }))
    },
    notificationApi: {
      list: vi.fn(makeList),
      create: vi.fn(() => Promise.resolve({ data: {} })),
      submitReview: vi.fn(() => Promise.resolve({ data: {} })),
      publish: vi.fn(() => Promise.resolve({ data: {} })),
      cancel: vi.fn(() => Promise.resolve({ data: {} })),
      getInbox: vi.fn(makeList),
      markRead: vi.fn(() => Promise.resolve({ data: {} })),
      getSubscriptions: vi.fn(() => Promise.resolve({ data: { subscriptions: {} } })),
      updateSubscriptions: vi.fn(() => Promise.resolve({ data: {} }))
    },
    userApi: {
      list: vi.fn(makeList),
      create: vi.fn(() => Promise.resolve({ data: {} })),
      update: vi.fn(() => Promise.resolve({ data: {} })),
      get: vi.fn(() => Promise.resolve({ data: {} }))
    },
    rosterApi: {
      list: vi.fn(makeList),
      export: vi.fn(() => Promise.resolve({ data: 'csv-content' }))
    },
    refApi: {
      terms: vi.fn(() => Promise.resolve({ data: [] })),
      classes: vi.fn(() => Promise.resolve({ data: [] })),
      courses: vi.fn(() => Promise.resolve({ data: [] })),
      grades: vi.fn(() => Promise.resolve({ data: [] }))
    },
    roomApi: {
      listCampuses: vi.fn(() => Promise.resolve({ data: [] })),
      listRooms: vi.fn(() => Promise.resolve({ data: [] })),
      createCampus: vi.fn(() => Promise.resolve({ data: {} })),
      createRoom: vi.fn(() => Promise.resolve({ data: {} })),
      updateRoom: vi.fn(() => Promise.resolve({ data: {} }))
    },
    proctorApi: {
      list: vi.fn(makeList),
      create: vi.fn(() => Promise.resolve({ data: {} })),
      remove: vi.fn(() => Promise.resolve({ data: {} }))
    },
    complianceApi: {
      listReviews: vi.fn(makeList),
      approve: vi.fn(() => Promise.resolve({ data: {} })),
      reject: vi.fn(() => Promise.resolve({ data: {} }))
    },
    jobApi: {
      list: vi.fn(makeList),
      rerun: vi.fn(() => Promise.resolve({ data: {} })),
      cancel: vi.fn(() => Promise.resolve({ data: {} }))
    },
    antiCheatApi: {
      listFlags: vi.fn(makeList),
      reviewFlag: vi.fn(() => Promise.resolve({ data: {} }))
    },
    auditApi: {
      list: vi.fn(makeList)
    },
    authApi: {
      terminateSession: vi.fn(() => Promise.resolve({ data: {} })),
      unlockAccount: vi.fn(() => Promise.resolve({ data: {} }))
    }
  }

  return { mockRoute, mockRouter, mockAuthStore, mockApis }
})

vi.mock('vue-router', () => ({
  useRoute: () => fixtures.mockRoute,
  useRouter: () => fixtures.mockRouter
}))

vi.mock('../../frontend/src/stores/auth.js', () => ({
  useAuthStore: () => fixtures.mockAuthStore
}))

vi.mock('../../frontend/src/api/client.js', () => fixtures.mockApis)

function mountView(component, params = {}) {
  fixtures.mockRoute.params = params
  return shallowMount(component, {
    global: {
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        Teleport: true,
        DataTable: true,
        StatusBadge: true,
        ConfirmDialog: true
      }
    }
  })
}

describe('Major View Smoke Coverage (real imports, low-depth)', () => {
  beforeEach(() => {
    Object.values(fixtures.mockApis).forEach((obj) => {
      Object.values(obj).forEach((fn) => fn.mockClear())
    })
    fixtures.mockRouter.push.mockReset()
    fixtures.mockAuthStore.hasPermission.mockReturnValue(true)
    fixtures.mockAuthStore.login.mockResolvedValue(undefined)
    vi.spyOn(window, 'alert').mockImplementation(() => {})
    vi.spyOn(window, 'confirm').mockImplementation(() => true)
  })

  it('smoke-checks core auth / notification / user views', async () => {
    const login = mountView(LoginView)
    const notifForm = mountView(NotificationForm)
    const notifList = mountView(NotificationList)
    const users = mountView(UserManagement)

    await flushPromises()

    expect(login.text()).toContain('Exam Scheduling System')
    expect(notifForm.text()).toContain('Create Notification')
    expect(notifList.text()).toContain('Notifications')
    expect(users.text()).toContain('User Management')
    expect(fixtures.mockApis.notificationApi.list).toHaveBeenCalled()
    expect(fixtures.mockApis.userApi.list).toHaveBeenCalled()
  })

  it('smoke-checks roster and scheduling views', async () => {
    const roster = mountView(RosterList)
    const list = mountView(ExamSessionList)
    const form = mountView(ExamSessionForm)
    const detail = mountView(ExamSessionDetail, { id: '1' })

    await flushPromises()

    expect(roster.text()).toContain('Rosters')
    expect(list.text()).toContain('Exam Sessions')
    expect(form.text()).toContain('New Exam Session')
    expect(detail.text()).toContain('Exam Session Detail')
    expect(fixtures.mockApis.rosterApi.list).toHaveBeenCalled()
    expect(fixtures.mockApis.examSessionApi.list).toHaveBeenCalled()
    expect(fixtures.mockApis.examSessionApi.get).toHaveBeenCalledWith('1')
  })

  it('smoke-checks compliance, jobs, anti-cheat, and audit views', async () => {
    const compliance = mountView(ComplianceReviewQueue)
    const jobs = mountView(JobMonitor)
    const antiCheat = mountView(AntiCheatQueue)
    const audit = mountView(AuditLog)

    await flushPromises()

    expect(compliance.text()).toContain('Compliance Review Queue')
    expect(jobs.text()).toContain('Job Monitor')
    expect(antiCheat.text()).toContain('Anti-Cheat Review Queue')
    expect(audit.text()).toContain('Audit Log')
    expect(fixtures.mockApis.complianceApi.listReviews).toHaveBeenCalled()
    expect(fixtures.mockApis.jobApi.list).toHaveBeenCalled()
    expect(fixtures.mockApis.antiCheatApi.listFlags).toHaveBeenCalled()
    expect(fixtures.mockApis.auditApi.list).toHaveBeenCalled()
  })

  it('smoke-checks room/campus and proctor assignment views', async () => {
    const rooms = mountView(RoomList)
    const campuses = mountView(CampusList)
    const proctors = mountView(ProctorList)

    await flushPromises()

    expect(rooms.text()).toContain('Room Management')
    expect(campuses.text()).toContain('Campuses & Rooms')
    expect(proctors.text()).toContain('Proctor Assignments')
    expect(fixtures.mockApis.roomApi.listCampuses).toHaveBeenCalled()
    expect(fixtures.mockApis.proctorApi.list).toHaveBeenCalled()
  })
})
