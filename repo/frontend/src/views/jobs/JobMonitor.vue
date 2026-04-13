<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { jobApi } from '../../api/client.js'
import DataTable from '../../components/shared/DataTable.vue'
import StatusBadge from '../../components/shared/StatusBadge.vue'

const columns = [
  { key: 'type', label: 'Type' },
  { key: 'status', label: 'Status' },
  { key: 'progress', label: 'Progress' },
  { key: 'createdAt', label: 'Created' },
  { key: 'updatedAt', label: 'Updated' },
  { key: 'actions', label: 'Actions', width: '180px' }
]

const jobs = ref([])
const loading = ref(false)
const error = ref(null)
const page = ref(1)
const totalPages = ref(1)

const autoRefresh = ref(false)
let refreshInterval = null

const expandedJob = ref(null)

async function fetchJobs() {
  loading.value = true
  error.value = null
  try {
    const res = await jobApi.list({ page: page.value, limit: 20 })
    const data = res.data
    jobs.value = (data.items || data).map(j => ({
      ...j,
      createdAt: j.createdAt ? new Date(j.createdAt).toLocaleString() : '-',
      updatedAt: j.updatedAt ? new Date(j.updatedAt).toLocaleString() : '-',
      progress: j.progress != null ? `${j.progress}%` : '-'
    }))
    totalPages.value = data.totalPages || 1
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load jobs.'
  } finally {
    loading.value = false
  }
}

function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value
  if (autoRefresh.value) {
    refreshInterval = setInterval(fetchJobs, 5000)
  } else {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
}

function toggleExpand(job) {
  expandedJob.value = expandedJob.value === job.id ? null : job.id
}

async function rerunJob(job) {
  try {
    const key = crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36)
    await jobApi.rerun(job.id, key)
    await fetchJobs()
  } catch (e) {
    alert(e.response?.data?.message || 'Failed to rerun job.')
  }
}

async function cancelJob(job) {
  try {
    await jobApi.cancel(job.id)
    await fetchJobs()
  } catch (e) {
    alert(e.response?.data?.message || 'Failed to cancel job.')
  }
}

function onPageChange(p) {
  page.value = p
  fetchJobs()
}

onMounted(fetchJobs)

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
})
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Job Monitor</h1>
      <div class="header-actions">
        <button
          :class="autoRefresh ? 'btn-success' : 'btn-secondary'"
          @click="toggleAutoRefresh"
        >
          Auto-refresh: {{ autoRefresh ? 'ON' : 'OFF' }}
        </button>
        <button class="btn-secondary" @click="fetchJobs">Refresh</button>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :rows="jobs"
      :loading="loading"
      :error="error"
      :page="page"
      :total-pages="totalPages"
      empty-message="No jobs found."
      @page-change="onPageChange"
      @retry="fetchJobs"
    >
      <template #cell-status="{ value }">
        <StatusBadge :status="value" />
      </template>
      <template #cell-actions="{ row }">
        <div class="action-buttons">
          <button
            v-if="row.failureReason || row.errorDetails"
            class="btn-secondary btn-sm"
            @click="toggleExpand(row)"
          >
            {{ expandedJob === row.id ? 'Hide' : 'Details' }}
          </button>
          <button
            v-if="row.status === 'FAILED'"
            class="btn-primary btn-sm"
            @click="rerunJob(row)"
          >Rerun</button>
          <button
            v-if="['QUEUED', 'RUNNING'].includes(row.status)"
            class="btn-danger btn-sm"
            @click="cancelJob(row)"
          >Cancel</button>
        </div>
      </template>
    </DataTable>

    <div v-if="expandedJob" class="card detail-panel">
      <h3>Job Details</h3>
      <pre class="detail-content">{{ JSON.stringify(
        jobs.find(j => j.id === expandedJob),
        null, 2
      ) }}</pre>
    </div>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
  gap: 8px;
}
.action-buttons { display: flex; gap: 4px; flex-wrap: wrap; }

.detail-panel {
  margin-top: 16px;
}

.detail-panel h3 {
  margin-bottom: 8px;
}

.detail-content {
  background: var(--gray-50);
  padding: 16px;
  border-radius: var(--radius);
  font-size: 12px;
  overflow-x: auto;
  max-height: 400px;
}
</style>
