import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import VersionHistory from '../../frontend/src/components/versioning/VersionHistory.vue'
import ConfirmDialog from '../../frontend/src/components/shared/ConfirmDialog.vue'

/**
 * Unit tests for the VersionHistory (compare) component covering side-by-side
 * display, change highlighting, restore confirmation dialog, and API calls.
 */

const mockList = vi.fn()
const mockCompare = vi.fn()
const mockRestore = vi.fn()

vi.mock('../../frontend/src/api/client.js', () => ({
  versionApi: {
    list: (...args) => mockList(...args),
    compare: (...args) => mockCompare(...args),
    restore: (...args) => mockRestore(...args),
  }
}))

describe('VersionCompare', () => {
  let router

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()

    router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/versions/:entityType/:entityId', name: 'VersionHistory', component: VersionHistory }
      ]
    })

    mockList.mockResolvedValue({
      data: [
        { version: 3, changedBy: 'admin', changedAt: '2026-04-10T10:00:00' },
        { version: 2, changedBy: 'coordinator1', changedAt: '2026-04-09T14:00:00' },
        { version: 1, changedBy: 'coordinator1', changedAt: '2026-04-08T09:00:00' },
      ]
    })
  })

  async function mountComponent() {
    router.push('/versions/ExamSession/1')
    await router.isReady()

    const wrapper = mount(VersionHistory, {
      global: {
        plugins: [router],
        components: { ConfirmDialog }
      }
    })
    await flushPromises()
    return wrapper
  }

  it('testTwoVersionsDisplayed', async () => {
    mockCompare.mockResolvedValue({
      data: {
        from: { version: 1, snapshot: { name: 'Midterm v1', status: 'DRAFT' } },
        to: { version: 2, snapshot: { name: 'Midterm v2', status: 'APPROVED' } },
        changes: [
          { field: 'name', oldValue: 'Midterm v1', newValue: 'Midterm v2' },
          { field: 'status', oldValue: 'DRAFT', newValue: 'APPROVED' }
        ]
      }
    })

    const wrapper = await mountComponent()

    // Select two versions for comparison
    const fromRadios = wrapper.findAll('input[name="from"]')
    const toRadios = wrapper.findAll('input[name="to"]')
    expect(fromRadios.length).toBe(3)
    expect(toRadios.length).toBe(3)

    // Select v1 as from, v2 as to
    await fromRadios[2].setValue() // v1 (last in list since ordered desc)
    await toRadios[1].setValue()   // v2

    // Click compare
    const compareBtn = wrapper.find('button.btn-primary')
    await compareBtn.trigger('click')
    await flushPromises()

    // Both version snapshots should be displayed in the diff container
    const diffSides = wrapper.findAll('.diff-side')
    expect(diffSides.length).toBe(2)
  })

  it('testChangesHighlighted', async () => {
    mockCompare.mockResolvedValue({
      data: {
        from: { version: 1, snapshot: { name: 'v1' } },
        to: { version: 2, snapshot: { name: 'v2' } },
        changes: [
          { field: 'name', oldValue: 'v1', newValue: 'v2' }
        ]
      }
    })

    const wrapper = await mountComponent()

    // Trigger comparison
    const fromRadios = wrapper.findAll('input[name="from"]')
    const toRadios = wrapper.findAll('input[name="to"]')
    await fromRadios[2].setValue()
    await toRadios[1].setValue()
    await wrapper.find('button.btn-primary').trigger('click')
    await flushPromises()

    // Changes list should show highlighted old/new values
    const oldVals = wrapper.findAll('.old-val')
    const newVals = wrapper.findAll('.new-val')
    expect(oldVals.length).toBeGreaterThan(0)
    expect(newVals.length).toBeGreaterThan(0)
  })

  it('testRestoreConfirmation', async () => {
    const wrapper = await mountComponent()

    // Click restore button on v2
    const restoreButtons = wrapper.findAll('button.btn-sm')
    expect(restoreButtons.length).toBe(3) // one per version

    await restoreButtons[1].trigger('click')

    // ConfirmDialog should now be visible
    // The component sets showRestoreDialog = true
    expect(wrapper.vm.showRestoreDialog).toBe(true)
  })

  it('testRestoreCallsApi', async () => {
    mockRestore.mockResolvedValue({ data: {} })
    // Keep version rows available so restore button exists before confirmation.
    mockList.mockResolvedValue({
      data: [
        { version: 3, changedBy: 'admin', changedAt: '2026-04-10T10:00:00' },
        { version: 2, changedBy: 'coordinator1', changedAt: '2026-04-09T14:00:00' },
        { version: 1, changedBy: 'coordinator1', changedAt: '2026-04-08T09:00:00' },
      ]
    })

    const wrapper = await mountComponent()

    // Click restore for v1
    const restoreButtons = wrapper.findAll('button.btn-sm')
    await restoreButtons[2].trigger('click')

    // Simulate confirming the dialog
    await wrapper.vm.confirmRestore()
    await flushPromises()

    expect(mockRestore).toHaveBeenCalledWith('ExamSession', 1, 1, null)
  })
})
