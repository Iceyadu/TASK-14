<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, required: true }
})

const colorMap = {
  DRAFT: { bg: '#f3f4f6', color: '#4b5563' },
  SUBMITTED_FOR_COMPLIANCE_REVIEW: { bg: '#fef3c7', color: '#92400e' },
  APPROVED: { bg: '#d1fae5', color: '#065f46' },
  PUBLISHED: { bg: '#dbeafe', color: '#1e40af' },
  UNPUBLISHED: { bg: '#e5e7eb', color: '#374151' },
  ARCHIVED: { bg: '#e5e7eb', color: '#374151' },
  CANCELED: { bg: '#fee2e2', color: '#991b1b' },
  REJECTED: { bg: '#fee2e2', color: '#991b1b' },
  RESTORED: { bg: '#d1fae5', color: '#065f46' },
  ACTIVE: { bg: '#d1fae5', color: '#065f46' },
  COMPLETED: { bg: '#dbeafe', color: '#1e40af' },
  SUCCEEDED: { bg: '#d1fae5', color: '#065f46' },
  FAILED: { bg: '#fee2e2', color: '#991b1b' },
  RUNNING: { bg: '#fef3c7', color: '#92400e' },
  RETRYING: { bg: '#fef3c7', color: '#92400e' },
  QUEUED: { bg: '#f3f4f6', color: '#4b5563' },
  SENDING: { bg: '#fef3c7', color: '#92400e' },
  DELIVERED: { bg: '#d1fae5', color: '#065f46' },
  EXPIRED: { bg: '#e5e7eb', color: '#374151' },
  FALLBACK_TO_IN_APP: { bg: '#fef3c7', color: '#92400e' },
  CONFIRMED_FOR_INVESTIGATION: { bg: '#fee2e2', color: '#991b1b' },
  DISMISSED: { bg: '#d1fae5', color: '#065f46' },
  UNREAD: { bg: '#fef3c7', color: '#92400e' },
  READ: { bg: '#e5e7eb', color: '#374151' },
  LOCKED: { bg: '#fee2e2', color: '#991b1b' },
  MANUALLY_RERUN: { bg: '#dbeafe', color: '#1e40af' }
}

const style = computed(() => {
  const s = colorMap[props.status] || { bg: '#f3f4f6', color: '#4b5563' }
  return { background: s.bg, color: s.color }
})
</script>

<template>
  <span class="status-badge" :style="style">{{ status }}</span>
</template>

<style scoped>
.status-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  white-space: nowrap;
}
</style>
