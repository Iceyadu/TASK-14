import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import NotificationList from '../../frontend/src/views/notifications/NotificationList.vue'

const mockList = vi.fn()
const mockSubmitReview = vi.fn()
const mockPublish = vi.fn()
const mockCancel = vi.fn()

vi.mock('../../frontend/src/api/client.js', () => ({
  notificationApi: {
    list: (...args) => mockList(...args),
    submitReview: (...args) => mockSubmitReview(...args),
    publish: (...args) => mockPublish(...args),
    cancel: (...args) => mockCancel(...args)
  }
}))

const DataTableScopedStub = {
  name: 'DataTable',
  props: ['rows'],
  template: `
    <div>
      <div v-for="row in rows" :key="row.id" class="row-slot">
        <slot name="cell-actions" :row="row"></slot>
      </div>
    </div>
  `
}

describe('NotificationList view', () => {
  beforeEach(() => {
    mockSubmitReview.mockResolvedValue({})
    mockPublish.mockResolvedValue({})
    mockCancel.mockResolvedValue({})
    mockList.mockResolvedValue({
      data: {
        items: [
          { id: 1, title: 'A', status: 'DRAFT', complianceApproved: true, createdAt: '2026-01-01T00:00:00Z' },
          { id: 2, title: 'B', status: 'DRAFT', complianceApproved: false, createdAt: '2026-01-01T00:00:00Z' }
        ],
        totalPages: 1
      }
    })
  })

  it('shows publish only for DRAFT + complianceApproved rows', async () => {
    const wrapper = mount(NotificationList, {
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
          StatusBadge: true,
          DataTable: DataTableScopedStub
        }
      }
    })

    await flushPromises()

    const buttons = wrapper.findAll('button').map((b) => b.text())
    const publishCount = buttons.filter((t) => t === 'Publish').length
    const submitCount = buttons.filter((t) => t === 'Submit').length

    expect(submitCount).toBe(2)
    expect(publishCount).toBe(1)
  })
})
