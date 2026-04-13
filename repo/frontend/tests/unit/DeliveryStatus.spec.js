import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { h } from 'vue'

/**
 * Unit tests for delivery status display covering per-channel status rendering,
 * color coding for FAILED/DELIVERED, and fallback indicator.
 */

// Lightweight delivery status component for testing the display logic
// This mirrors the real rendering pattern used in NotificationList.vue
const DeliveryStatusDisplay = {
  name: 'DeliveryStatusDisplay',
  props: {
    statuses: { type: Array, default: () => [] }
  },
  template: `
    <div class="delivery-status-list">
      <div v-for="status in statuses" :key="status.channel" class="delivery-status-item">
        <span class="channel-name">{{ status.channel }}</span>
        <span
          class="status-badge"
          :class="{
            'status-delivered': status.status === 'DELIVERED',
            'status-failed': status.status === 'FAILED',
            'status-fallback': status.status === 'FALLBACK_TO_IN_APP'
          }"
        >
          {{ status.status }}
        </span>
        <span v-if="status.status === 'FALLBACK_TO_IN_APP'" class="fallback-badge">Fallback</span>
      </div>
    </div>
  `
}

describe('DeliveryStatus', () => {
  const statuses = [
    { channel: 'WECHAT', status: 'DELIVERED', studentUserId: 10 },
    { channel: 'EMAIL', status: 'FAILED', studentUserId: 10, failureReason: 'SMTP timeout' },
    { channel: 'IN_APP', status: 'DELIVERED', studentUserId: 10 },
    { channel: 'SMS', status: 'FALLBACK_TO_IN_APP', studentUserId: 20 },
  ]

  it('testPerChannelStatus', () => {
    const wrapper = mount(DeliveryStatusDisplay, {
      props: { statuses }
    })

    const items = wrapper.findAll('.delivery-status-item')
    expect(items.length).toBe(4)

    const channels = wrapper.findAll('.channel-name')
    const channelTexts = channels.map(c => c.text())
    expect(channelTexts).toContain('WECHAT')
    expect(channelTexts).toContain('EMAIL')
    expect(channelTexts).toContain('IN_APP')
    expect(channelTexts).toContain('SMS')
  })

  it('testFailedStatusRed', () => {
    const wrapper = mount(DeliveryStatusDisplay, {
      props: { statuses }
    })

    const failedBadges = wrapper.findAll('.status-failed')
    expect(failedBadges.length).toBe(1)
    expect(failedBadges[0].text()).toBe('FAILED')
  })

  it('testDeliveredStatusGreen', () => {
    const wrapper = mount(DeliveryStatusDisplay, {
      props: { statuses }
    })

    const deliveredBadges = wrapper.findAll('.status-delivered')
    expect(deliveredBadges.length).toBe(2) // WECHAT and IN_APP
  })

  it('testFallbackIndicator', () => {
    const wrapper = mount(DeliveryStatusDisplay, {
      props: { statuses }
    })

    const fallbackBadges = wrapper.findAll('.fallback-badge')
    expect(fallbackBadges.length).toBe(1)
    expect(fallbackBadges[0].text()).toBe('Fallback')

    const statusFallback = wrapper.findAll('.status-fallback')
    expect(statusFallback.length).toBe(1)
    expect(statusFallback[0].text()).toBe('FALLBACK_TO_IN_APP')
  })
})
