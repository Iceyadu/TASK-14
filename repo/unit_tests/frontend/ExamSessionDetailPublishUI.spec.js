import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ExamSessionDetail from '../../frontend/src/views/scheduling/ExamSessionDetail.vue'

const mockRoute = { params: { id: '1' }, query: {} }
const mockRouter = { push: vi.fn() }
const mockGet = vi.fn()

const mockStore = {
  role: 'ACADEMIC_COORDINATOR',
  hasPermission: vi.fn(() => true)
}

vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  useRouter: () => mockRouter
}))

vi.mock('../../frontend/src/stores/auth.js', () => ({
  useAuthStore: () => mockStore
}))

vi.mock('../../frontend/src/api/client.js', () => ({
  examSessionApi: {
    get: (...args) => mockGet(...args),
    submitReview: vi.fn(() => Promise.resolve({ data: {} })),
    publish: vi.fn(() => Promise.resolve({ data: {} })),
    unpublish: vi.fn(() => Promise.resolve({ data: {} })),
    archive: vi.fn(() => Promise.resolve({ data: {} }))
  }
}))

function mountDetail() {
  return mount(ExamSessionDetail, {
    global: {
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        StatusBadge: true,
        ConfirmDialog: true
      }
    }
  })
}

describe('ExamSessionDetail real publish UI rules', () => {
  beforeEach(() => {
    mockStore.role = 'ACADEMIC_COORDINATOR'
    mockStore.hasPermission.mockReturnValue(true)
  })

  it('shows Publish only for APPROVED sessions', async () => {
    mockGet.mockResolvedValueOnce({
      data: { id: 1, name: 'Session 1', status: 'APPROVED', classIds: [] }
    })

    const wrapper = mountDetail()
    await flushPromises()

    const texts = wrapper.findAll('button').map((b) => b.text())
    expect(texts).toContain('Publish')
    expect(texts).not.toContain('Unpublish')
  })

  it('shows Submit for DRAFT and hides Publish', async () => {
    mockGet.mockResolvedValueOnce({
      data: { id: 1, name: 'Session 1', status: 'DRAFT', classIds: [] }
    })

    const wrapper = mountDetail()
    await flushPromises()

    const texts = wrapper.findAll('button').map((b) => b.text())
    expect(texts).toContain('Submit for Review')
    expect(texts).not.toContain('Publish')
  })
})
