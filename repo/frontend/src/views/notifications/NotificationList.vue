<script setup>
import { ref, onMounted } from 'vue'
import { notificationApi } from '../../api/client.js'
import DataTable from '../../components/shared/DataTable.vue'
import StatusBadge from '../../components/shared/StatusBadge.vue'

const columns = [
  { key: 'title', label: 'Title' },
  { key: 'eventType', label: 'Event Type' },
  { key: 'targetType', label: 'Target' },
  { key: 'status', label: 'Status' },
  { key: 'createdAt', label: 'Created' },
  { key: 'actions', label: 'Actions', width: '200px' }
]

const notifications = ref([])
const loading = ref(false)
const error = ref(null)
const page = ref(1)
const totalPages = ref(1)
const statusFilter = ref('')

async function fetchNotifications() {
  loading.value = true
  error.value = null
  try {
    const params = { page: page.value, limit: 20 }
    if (statusFilter.value) params.status = statusFilter.value
    const res = await notificationApi.list(params)
    const data = res.data
    notifications.value = (data.items || data).map(n => ({
      ...n,
      createdAt: n.createdAt ? new Date(n.createdAt).toLocaleDateString() : '-'
    }))
    totalPages.value = data.totalPages || 1
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load notifications.'
  } finally {
    loading.value = false
  }
}

async function doAction(action, notif) {
  try {
    if (action === 'submit') {
      await notificationApi.submitReview(notif.id)
    } else if (action === 'publish') {
      const key = crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36)
      await notificationApi.publish(notif.id, key)
    } else if (action === 'cancel') {
      await notificationApi.cancel(notif.id)
    }
    await fetchNotifications()
  } catch (e) {
    alert(e.response?.data?.message || `Failed to ${action} notification.`)
  }
}

function onPageChange(p) {
  page.value = p
  fetchNotifications()
}

function onFilter() {
  page.value = 1
  fetchNotifications()
}

onMounted(fetchNotifications)
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Notifications</h1>
      <router-link to="/notifications/new" class="btn-primary" style="text-decoration:none;">
        Create Notification
      </router-link>
    </div>

    <div class="card" style="margin-bottom:16px;">
      <select v-model="statusFilter" @change="onFilter" style="max-width:200px;">
        <option value="">All Statuses</option>
        <option value="DRAFT">Draft</option>
        <option value="QUEUED">Queued</option>
        <option value="SENDING">Sending</option>
        <option value="DELIVERED">Delivered</option>
        <option value="FAILED">Failed</option>
        <option value="CANCELED">Canceled</option>
      </select>
    </div>

    <DataTable
      :columns="columns"
      :rows="notifications"
      :loading="loading"
      :error="error"
      :page="page"
      :total-pages="totalPages"
      empty-message="No notifications found."
      @page-change="onPageChange"
      @retry="fetchNotifications"
    >
      <template #cell-status="{ value }">
        <StatusBadge :status="value" />
      </template>
      <template #cell-actions="{ row }">
        <div class="action-buttons">
          <button
            v-if="row.status === 'DRAFT'"
            class="btn-primary btn-sm"
            @click="doAction('submit', row)"
          >Submit</button>
          <button
            v-if="row.status === 'DRAFT' && row.complianceApproved"
            class="btn-success btn-sm"
            @click="doAction('publish', row)"
          >Publish</button>
          <button
            v-if="['DRAFT', 'QUEUED'].includes(row.status)"
            class="btn-danger btn-sm"
            @click="doAction('cancel', row)"
          >Cancel</button>
        </div>
      </template>
    </DataTable>
  </div>
</template>

<style scoped>
.action-buttons { display: flex; gap: 4px; flex-wrap: wrap; }
</style>
