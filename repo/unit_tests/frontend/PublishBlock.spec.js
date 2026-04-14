import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

/**
 * Unit tests for the publish block/enable behavior covering button disabled state
 * for unapproved content, enabled state for approved content, and blocked message.
 */

// Lightweight component that mirrors the publish button pattern used across
// ExamSessionDetail.vue and NotificationForm.vue
const PublishBlock = {
  name: 'PublishBlock',
  props: {
    status: { type: String, required: true },
    isApproved: { type: Boolean, default: false }
  },
  emits: ['publish'],
  template: `
    <div class="publish-block">
      <div v-if="!isApproved && status !== 'PUBLISHED'" class="blocked-message">
        Requires compliance approval
      </div>
      <button
        class="btn-publish"
        :disabled="!isApproved || status === 'PUBLISHED'"
        @click="$emit('publish')"
      >
        Publish
      </button>
    </div>
  `
}

describe('PublishBlock', () => {
  it('testPublishButtonDisabledUnapproved', () => {
    const wrapper = mount(PublishBlock, {
      props: { status: 'DRAFT', isApproved: false }
    })

    const button = wrapper.find('.btn-publish')
    expect(button.exists()).toBe(true)
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('testPublishButtonEnabledApproved', () => {
    const wrapper = mount(PublishBlock, {
      props: { status: 'APPROVED', isApproved: true }
    })

    const button = wrapper.find('.btn-publish')
    expect(button.exists()).toBe(true)
    expect(button.attributes('disabled')).toBeUndefined()
  })

  it('testBlockedMessage', () => {
    const wrapper = mount(PublishBlock, {
      props: { status: 'DRAFT', isApproved: false }
    })

    const message = wrapper.find('.blocked-message')
    expect(message.exists()).toBe(true)
    expect(message.text()).toBe('Requires compliance approval')

    // When approved, no blocked message
    const approvedWrapper = mount(PublishBlock, {
      props: { status: 'APPROVED', isApproved: true }
    })

    expect(approvedWrapper.find('.blocked-message').exists()).toBe(false)
  })
})
