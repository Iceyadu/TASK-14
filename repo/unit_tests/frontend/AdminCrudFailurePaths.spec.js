import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

import NotificationForm from '../../frontend/src/views/notifications/NotificationForm.vue'
import ExamSessionForm from '../../frontend/src/views/scheduling/ExamSessionForm.vue'
import UserManagement from '../../frontend/src/views/users/UserManagement.vue'

const fixtures = vi.hoisted(() => {
  const router = { push: vi.fn() }
  const route = { params: {}, query: {} }
  const notificationApi = { create: vi.fn(() => Promise.resolve({ data: {} })) }
  const refApi = {
    classes: vi.fn(() => Promise.resolve({ data: [{ id: 1, name: 'Class 1' }] })),
    grades: vi.fn(() => Promise.resolve({ data: [{ id: 1, name: 'Grade 1' }] })),
    terms: vi.fn(() => Promise.resolve({ data: [{ id: 1, name: 'Term 1' }] })),
    courses: vi.fn(() => Promise.resolve({ data: [{ id: 1, name: 'Math' }] }))
  }
  const roomApi = {
    listCampuses: vi.fn(() => Promise.resolve({ data: [{ id: 10, name: 'Main Campus' }] })),
    listRooms: vi.fn(() => Promise.resolve({ data: [{ id: 20, name: 'Room A', capacity: 30 }] }))
  }
  const examSessionApi = {
    create: vi.fn(() => Promise.resolve({ data: {} })),
    get: vi.fn(),
    update: vi.fn()
  }
  const userApi = {
    list: vi.fn(() => Promise.resolve({ data: { items: [], totalPages: 1 } })),
    create: vi.fn(() => Promise.resolve({ data: {} })),
    update: vi.fn(() => Promise.resolve({ data: {} }))
  }
  const authApi = {
    terminateSession: vi.fn(() => Promise.resolve({ data: {} })),
    unlockAccount: vi.fn(() => Promise.resolve({ data: {} }))
  }
  return { router, route, notificationApi, refApi, roomApi, examSessionApi, userApi, authApi }
})

vi.mock('vue-router', () => ({
  useRouter: () => fixtures.router,
  useRoute: () => fixtures.route
}))

vi.mock('../../frontend/src/api/client.js', () => ({
  notificationApi: fixtures.notificationApi,
  refApi: fixtures.refApi,
  roomApi: fixtures.roomApi,
  examSessionApi: fixtures.examSessionApi,
  userApi: fixtures.userApi,
  authApi: fixtures.authApi
}))

describe('Admin CRUD failure-path validations', () => {
  it('NotificationForm blocks submit when required fields are missing', async () => {
    const wrapper = mount(NotificationForm, {
      global: { stubs: { RouterLink: true } }
    })
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Title is required.')
    expect(wrapper.text()).toContain('Content is required.')
    expect(wrapper.text()).toContain('Please select an event type.')
    expect(fixtures.notificationApi.create).not.toHaveBeenCalled()
  })

  it('ExamSessionForm rejects invalid time range before API call', async () => {
    const wrapper = mount(ExamSessionForm, {
      global: { stubs: { RouterLink: true } }
    })
    await flushPromises()

    await wrapper.find('input[placeholder="e.g. Math Final Exam"]').setValue('Session X')
    const selects = wrapper.findAll('select')
    await selects[0].setValue('1')
    await selects[2].setValue('10')
    await flushPromises()
    await selects[3].setValue('20')

    await wrapper.find('input[type="date"]').setValue('2030-12-01')
    const times = wrapper.findAll('input[type="time"]')
    await times[0].setValue('11:00')
    await times[1].setValue('10:00')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('End time must be after start time.')
    expect(fixtures.examSessionApi.create).not.toHaveBeenCalled()
  })

  it('UserManagement create flow requires password for new users', async () => {
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

    await wrapper.get('button.btn-primary').trigger('click')
    const inputs = wrapper.findAll('.dialog-box input')
    await inputs[0].setValue('new_user_no_pw')
    await inputs[1].setValue('No Password User')
    await wrapper.get('.dialog-box form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Password is required for new users.')
    expect(fixtures.userApi.create).not.toHaveBeenCalled()
  })
})
