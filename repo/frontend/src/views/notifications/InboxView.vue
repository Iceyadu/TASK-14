<script setup>
import { ref, onMounted } from 'vue'
import { notificationApi } from '../../api/client.js'

const messages = ref([])
const loading = ref(false)
const error = ref(null)
const page = ref(1)
const totalPages = ref(1)

async function fetchInbox() {
  loading.value = true
  error.value = null
  try {
    const res = await notificationApi.getInbox({ page: page.value, limit: 20 })
    const data = res.data
    messages.value = data.items || data
    totalPages.value = data.totalPages || 1
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load inbox.'
  } finally {
    loading.value = false
  }
}

async function markAsRead(msg) {
  try {
    await notificationApi.markRead(msg.id)
    msg.read = true
    msg.status = 'READ'
  } catch (e) {
    alert(e.response?.data?.message || 'Failed to mark as read.')
  }
}

function onPageChange(p) {
  page.value = p
  fetchInbox()
}

onMounted(fetchInbox)
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Inbox</h1>
    </div>

    <div v-if="loading" class="card" style="text-align:center; padding:40px;">Loading...</div>
    <div v-else-if="error" class="card" style="color:var(--danger);">
      {{ error }}
      <button class="btn-primary" style="margin-top:8px;" @click="fetchInbox">Retry</button>
    </div>
    <div v-else-if="messages.length === 0" class="card" style="text-align:center; padding:40px; color:var(--gray-500);">
      Your inbox is empty.
    </div>

    <template v-else>
      <div
        v-for="msg in messages"
        :key="msg.id"
        :class="['message-card', { unread: !msg.read && msg.status !== 'READ' }]"
      >
        <div class="message-header">
          <h3>{{ msg.title }}</h3>
          <div class="message-meta">
            <span :class="['read-status', msg.read || msg.status === 'READ' ? 'read' : 'unread-badge']">
              {{ msg.read || msg.status === 'READ' ? 'Read' : 'Unread' }}
            </span>
            <span class="message-date">{{ msg.createdAt ? new Date(msg.createdAt).toLocaleDateString() : '' }}</span>
          </div>
        </div>
        <p class="message-content">{{ msg.content }}</p>
        <div v-if="msg.eventType" class="message-type">{{ msg.eventType.replace(/_/g, ' ') }}</div>
        <div class="message-actions">
          <button
            v-if="!msg.read && msg.status !== 'READ'"
            class="btn-primary btn-sm"
            @click="markAsRead(msg)"
          >
            Mark as Read
          </button>
          <span v-if="msg.deliveryStatus" class="delivery-status">
            Delivery: {{ msg.deliveryStatus }}
          </span>
        </div>
      </div>

      <div v-if="totalPages > 1" class="pagination">
        <button class="btn-secondary btn-sm" :disabled="page <= 1" @click="onPageChange(page - 1)">Previous</button>
        <span class="page-info">Page {{ page }} of {{ totalPages }}</span>
        <button class="btn-secondary btn-sm" :disabled="page >= totalPages" @click="onPageChange(page + 1)">Next</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.message-card {
  background: white;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  border-left: 4px solid transparent;
}

.message-card.unread {
  border-left-color: var(--primary);
  background: #f8faff;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.message-header h3 {
  font-size: 15px;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.read-status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 600;
}

.read-status.read {
  background: var(--gray-100);
  color: var(--gray-500);
}

.unread-badge {
  background: #dbeafe;
  color: var(--primary);
}

.message-date {
  font-size: 12px;
  color: var(--gray-500);
}

.message-content {
  font-size: 14px;
  color: var(--gray-700);
  margin-bottom: 8px;
}

.message-type {
  font-size: 11px;
  color: var(--gray-500);
  text-transform: uppercase;
  margin-bottom: 8px;
}

.message-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.delivery-status {
  font-size: 12px;
  color: var(--gray-500);
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 16px 0;
}

.page-info {
  font-size: 13px;
  color: var(--gray-500);
}
</style>
