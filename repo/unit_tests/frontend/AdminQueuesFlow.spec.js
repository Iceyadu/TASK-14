import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

import ComplianceReviewQueue from '../../frontend/src/views/compliance/ComplianceReviewQueue.vue'
import JobMonitor from '../../frontend/src/views/jobs/JobMonitor.vue'
import AntiCheatQueue from '../../frontend/src/views/anticheat/AntiCheatQueue.vue'

const fixtures = vi.hoisted(() => {
  const complianceApi = {
    listReviews: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn()
  }
  const jobApi = {
    list: vi.fn(),
    rerun: vi.fn(),
    cancel: vi.fn()
  }
  const antiCheatApi = {
    listFlags: vi.fn(),
    reviewFlag: vi.fn()
  }
  return { complianceApi, jobApi, antiCheatApi }
})

vi.mock('../../frontend/src/api/client.js', () => ({
  complianceApi: fixtures.complianceApi,
  jobApi: fixtures.jobApi,
  antiCheatApi: fixtures.antiCheatApi
}))

const DataTableScopedStub = {
  name: 'DataTable',
  props: ['rows'],
  emits: ['page-change', 'retry'],
  template: `
    <div>
      <div v-for="row in rows" :key="row.id" class="row-slot">
        <slot name="cell-actions" :row="row"></slot>
      </div>
    </div>
  `
}

describe('Admin queue view flows', () => {
  beforeEach(() => {
    fixtures.complianceApi.listReviews.mockResolvedValue({
      data: {
        items: [
          { id: 10, entityName: 'Session A', status: 'PENDING', submittedAt: '2026-01-01T00:00:00Z' }
        ],
        totalPages: 1
      }
    })
    fixtures.complianceApi.approve.mockResolvedValue({ data: {} })
    fixtures.complianceApi.reject.mockResolvedValue({ data: {} })

    fixtures.jobApi.list.mockResolvedValue({
      data: {
        items: [
          { id: 21, status: 'FAILED', failureReason: 'x', progress: 0 },
          { id: 22, status: 'QUEUED', progress: 0 }
        ],
        totalPages: 1
      }
    })
    fixtures.jobApi.rerun.mockResolvedValue({ data: {} })
    fixtures.jobApi.cancel.mockResolvedValue({ data: {} })

    fixtures.antiCheatApi.listFlags.mockResolvedValue({
      data: {
        items: [{ id: 31, status: 'PENDING', ruleType: 'BURST', studentName: 'Student A' }],
        totalPages: 1
      }
    })
    fixtures.antiCheatApi.reviewFlag.mockResolvedValue({ data: {} })
  })

  it('ComplianceReviewQueue reject path requires comment then calls API', async () => {
    const wrapper = mount(ComplianceReviewQueue, {
      attachTo: document.body,
      global: {
        stubs: {
          DataTable: DataTableScopedStub,
          StatusBadge: true,
          Teleport: false
        }
      }
    })
    await flushPromises()

    await wrapper.get('button.btn-danger').trigger('click')
    await wrapper.get('.dialog-actions .btn-danger').trigger('click')
    expect(wrapper.text()).toContain('A comment is required when rejecting.')

    await wrapper.get('textarea').setValue('Rejecting per policy')
    await wrapper.get('.dialog-actions .btn-danger').trigger('click')
    await flushPromises()

    expect(fixtures.complianceApi.reject).toHaveBeenCalledWith(10, { comment: 'Rejecting per policy' })
  })

  it('JobMonitor rerun and cancel actions call APIs', async () => {
    const wrapper = mount(JobMonitor, {
      attachTo: document.body,
      global: {
        stubs: {
          DataTable: DataTableScopedStub,
          StatusBadge: true
        }
      }
    })
    await flushPromises()

    let buttons = wrapper.findAll('button')
    await buttons.find((b) => b.text() === 'Rerun').trigger('click')
    await flushPromises()
    expect(fixtures.jobApi.rerun).toHaveBeenCalledWith(21, expect.any(String))

    buttons = wrapper.findAll('button')
    await buttons.find((b) => b.text() === 'Cancel').trigger('click')
    await flushPromises()
    expect(fixtures.jobApi.cancel).toHaveBeenCalledWith(22)
  })

  it('AntiCheatQueue review requires comment and submits decision', async () => {
    const wrapper = mount(AntiCheatQueue, {
      attachTo: document.body,
      global: {
        stubs: {
          DataTable: DataTableScopedStub,
          StatusBadge: true,
          Teleport: false
        }
      }
    })
    await flushPromises()

    await wrapper.get('button.btn-primary').trigger('click')
    await wrapper.get('.dialog-actions .btn-success').trigger('click')
    expect(wrapper.text()).toContain('A comment is required.')

    await wrapper.get('textarea').setValue('Dismissed after manual review')
    await wrapper.get('.dialog-actions .btn-success').trigger('click')
    await flushPromises()

    expect(fixtures.antiCheatApi.reviewFlag).toHaveBeenCalledWith(31, {
      decision: 'DISMISSED',
      comment: 'Dismissed after manual review'
    })
  })
})
