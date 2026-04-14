import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SubscriptionSettings from '../../frontend/src/views/notifications/SubscriptionSettings.vue'

/**
 * Unit tests for the SubscriptionSettings component covering event type rendering,
 * toggle behavior, DND time validation, and save API calls.
 */

// Mock the API client
const mockGetSubscriptions = vi.fn()
const mockUpdateSubscriptions = vi.fn()

vi.mock('../../frontend/src/api/client.js', () => ({
  notificationApi: {
    getSubscriptions: (...args) => mockGetSubscriptions(...args),
    updateSubscriptions: (...args) => mockUpdateSubscriptions(...args),
  }
}))

describe('SubscriptionSettings', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()

    mockGetSubscriptions.mockResolvedValue({
      data: {
        subscriptions: {
          SCHEDULE_CHANGE: true,
          REVIEW_OUTCOME: true,
          CHECK_IN_REMINDER: false,
          RESULT_PUBLISHED: true,
          GENERAL: true,
        },
        dndStartTime: '22:00',
        dndEndTime: '07:00'
      }
    })
  })

  it('testRendersEventTypes', async () => {
    const wrapper = mount(SubscriptionSettings)
    await flushPromises()

    const toggleItems = wrapper.findAll('.toggle-item')
    expect(toggleItems.length).toBe(5)

    const labels = wrapper.findAll('.toggle-label span')
    const labelTexts = labels.map(l => l.text())
    expect(labelTexts).toContain('Schedule changes')
    expect(labelTexts).toContain('Check-in reminders')
    expect(labelTexts).toContain('General announcements')
  })

  it('testToggleEventType', async () => {
    const wrapper = mount(SubscriptionSettings)
    await flushPromises()

    // Find checkboxes
    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    expect(checkboxes.length).toBeGreaterThan(0)

    // CHECK_IN_REMINDER is false in mock data (third toggle)
    const cancelledCheckbox = checkboxes[2]
    expect(cancelledCheckbox.element.checked).toBe(false)

    // Toggle it on
    await cancelledCheckbox.setValue(true)
    expect(cancelledCheckbox.element.checked).toBe(true)
  })

  it('testDndTimeValidation', async () => {
    const wrapper = mount(SubscriptionSettings)
    await flushPromises()

    // Find time inputs
    const timeInputs = wrapper.findAll('input[type="time"]')
    expect(timeInputs.length).toBe(2)

    // DND start and end should be populated from API
    expect(timeInputs[0].element.value).toBe('22:00')
    expect(timeInputs[1].element.value).toBe('07:00')
  })

  it('testSaveCallsApi', async () => {
    mockUpdateSubscriptions.mockResolvedValue({ data: {} })

    const wrapper = mount(SubscriptionSettings)
    await flushPromises()

    // Click the save button
    const saveButton = wrapper.find('button.btn-primary')
    expect(saveButton.exists()).toBe(true)
    expect(saveButton.text()).toContain('Save')

    await saveButton.trigger('click')
    await flushPromises()

    expect(mockUpdateSubscriptions).toHaveBeenCalledTimes(1)
    expect(mockUpdateSubscriptions).toHaveBeenCalledWith(
      expect.objectContaining({
        subscriptions: expect.any(Object),
      })
    )
  })
})
