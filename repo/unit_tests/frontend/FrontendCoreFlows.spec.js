import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

import LoginView from '../../frontend/src/views/auth/LoginView.vue'
import NotificationForm from '../../frontend/src/views/notifications/NotificationForm.vue'
import UserManagement from '../../frontend/src/views/users/UserManagement.vue'
import RosterList from '../../frontend/src/views/roster/RosterList.vue'

const fixtures = vi.hoisted(() => {
  const route = { params: {}, query: { redirect: '/notifications' } }
  const router = { push: vi.fn() }

  const authStore = {
    login: vi.fn(() => Promise.resolve({})),
    role: 'ADMIN',
    hasPermission: vi.fn(() => true),
    isAuthenticated: true,
    loadFromStorage: vi.fn()
  }

  const notificationApi = {
    create: vi.fn(() => Promise.resolve({ data: { id: 1 } }))
  }
  const userApi = {
    list: vi.fn(() => Promise.resolve({ data: { items: [], totalPages: 1 } })),
    create: vi.fn(() => Promise.resolve({ data: { id: 1 } })),
    update: vi.fn(() => Promise.resolve({ data: {} }))
  }
  const authApi = {
    terminateSession: vi.fn(() => Promise.resolve({ data: {} })),
    unlockAccount: vi.fn(() => Promise.resolve({ data: {} }))
  }
  const rosterApi = {
    list: vi.fn(() => Promise.resolve({ data: { items: [], totalPages: 1, totalItems: 0 } })),
    export: vi.fn(() => Promise.resolve({ data: 'csv' }))
  }
  const refApi = {
    classes: vi.fn(() => Promise.resolve({ data: [{ id: 10, name: 'Class A' }] })),
    grades: vi.fn(() => Promise.resolve({ data: [] }))
  }

  return { route, router, authStore, notificationApi, userApi, authApi, rosterApi, refApi }
})

vi.mock('vue-router', () => ({
  useRoute: () => fixtures.route,
  useRouter: () => fixtures.router
}))

vi.mock('../../frontend/src/stores/auth.js', () => ({
  useAuthStore: () => fixtures.authStore
}))

vi.mock('../../frontend/src/api/client.js', () => ({
  notificationApi: fixtures.notificationApi,
  userApi: fixtures.userApi,
  authApi: fixtures.authApi,
  rosterApi: fixtures.rosterApi,
  refApi: fixtures.refApi
}))

describe('Frontend core flows (real view components)', () => {
  beforeEach(() => {
    fixtures.authStore.login.mockClear()
    fixtures.router.push.mockClear()
    fixtures.notificationApi.create.mockClear()
    fixtures.userApi.create.mockClear()
    fixtures.userApi.list.mockClear()
    fixtures.rosterApi.export.mockClear()

    global.alert = vi.fn()
    global.confirm = vi.fn(() => true)

    global.URL.createObjectURL = vi.fn(() => 'blob:test')
    global.URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  it('LoginView submits credentials and redirects', async () => {
    const wrapper = mount(LoginView)

    await wrapper.find('#username').setValue('admin')
    await wrapper.find('#password').setValue('Test@12345678')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(fixtures.authStore.login).toHaveBeenCalledTimes(1)
    expect(fixtures.authStore.login.mock.calls[0][0]).toMatchObject({
      username: 'admin',
      password: 'Test@12345678'
    })
    expect(fixtures.router.push).toHaveBeenCalledWith('/notifications')
  })

  it('NotificationForm builds target payload and calls create', async () => {
    const wrapper = mount(NotificationForm, {
      global: { stubs: { RouterLink: true } }
    })

    await wrapper.find('input[placeholder="Notification title"]').setValue('Exam update')
    await wrapper.find('textarea').setValue('Bring your ID card')
    await wrapper.find('select').setValue('CHECK_IN_REMINDER')
    await wrapper.findAll('select')[1].setValue('CLASS')
    await flushPromises()

    const targetSelect = wrapper.findAll('select')[2]
    await targetSelect.setValue('10')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(fixtures.notificationApi.create).toHaveBeenCalledWith(expect.objectContaining({
      title: 'Exam update',
      eventType: 'CHECK_IN_REMINDER',
      targetType: 'CLASS',
      targetIds: [10]
    }))
  })

  it('UserManagement create-user flow calls userApi.create', async () => {
    const wrapper = mount(UserManagement, {
      attachTo: document.body,
      global: {
        stubs: {
          DataTable: true,
          StatusBadge: true,
          Teleport: false
        }
      }
    })
    await flushPromises()

    await wrapper.find('button.btn-primary').trigger('click')
    await flushPromises()

    const dialog = document.body.querySelector('.dialog-box')
    expect(dialog).toBeTruthy()
    const inputs = dialog.querySelectorAll('input')
    expect(inputs.length).toBeGreaterThanOrEqual(3)
    inputs[0].value = 'teacher_new'
    inputs[0].dispatchEvent(new Event('input', { bubbles: true }))
    inputs[1].value = 'Teacher New'
    inputs[1].dispatchEvent(new Event('input', { bubbles: true }))
    inputs[2].value = 'Pass@12345678'
    inputs[2].dispatchEvent(new Event('input', { bubbles: true }))
    dialog.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()

    expect(fixtures.userApi.create).toHaveBeenCalledTimes(1)
    expect(fixtures.userApi.create.mock.calls[0][0]).toMatchObject({
      username: 'teacher_new',
      displayName: 'Teacher New'
    })
    wrapper.unmount()
  })

  it('RosterList export flow calls rosterApi.export', async () => {
    const wrapper = mount(RosterList, {
      attachTo: document.body,
      global: {
        stubs: {
          RouterLink: true,
          DataTable: true
        }
      }
    })
    await flushPromises()

    const exportBtn = wrapper.findAll('button').find((b) => b.text() === 'Export CSV')
    expect(exportBtn).toBeTruthy()
    await exportBtn.trigger('click')
    await flushPromises()

    expect(fixtures.rosterApi.export).toHaveBeenCalledTimes(1)
    expect(URL.createObjectURL).toHaveBeenCalled()
  })
})
