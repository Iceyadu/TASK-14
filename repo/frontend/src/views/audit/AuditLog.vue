<script setup>
import { ref, onMounted } from 'vue'
import { auditApi } from '../../api/client.js'
import DataTable from '../../components/shared/DataTable.vue'

const columns = [
  { key: 'timestamp', label: 'Timestamp' },
  { key: 'userId', label: 'User' },
  { key: 'action', label: 'Action' },
  { key: 'entityType', label: 'Entity Type' },
  { key: 'entityId', label: 'Entity ID' },
  { key: 'ipAddress', label: 'IP Address' }
]

const entries = ref([])
const loading = ref(false)
const error = ref(null)
const page = ref(1)
const totalPages = ref(1)
const totalItems = ref(0)

const filters = ref({
  userId: '',
  entityType: '',
  action: '',
  dateFrom: '',
  dateTo: ''
})

const entityTypes = [
  'USER', 'ROSTER', 'EXAM_SESSION', 'NOTIFICATION',
  'CAMPUS', 'ROOM', 'PROCTOR_ASSIGNMENT', 'JOB'
]

const actions = [
  'CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT',
  'PUBLISH', 'ARCHIVE', 'APPROVE', 'REJECT', 'IMPORT'
]

async function fetchAudit() {
  loading.value = true
  error.value = null
  try {
    const params = { page: page.value, limit: 30 }
    if (filters.value.userId) params.userId = filters.value.userId
    if (filters.value.entityType) params.entityType = filters.value.entityType
    if (filters.value.action) params.action = filters.value.action
    if (filters.value.dateFrom) params.dateFrom = filters.value.dateFrom
    if (filters.value.dateTo) params.dateTo = filters.value.dateTo

    const res = await auditApi.list(params)
    const data = res.data
    entries.value = (data.items || data).map(e => ({
      ...e,
      timestamp: e.timestamp ? new Date(e.timestamp).toLocaleString() : e.createdAt ? new Date(e.createdAt).toLocaleString() : '-'
    }))
    totalPages.value = data.totalPages || 1
    totalItems.value = data.totalItems || entries.value.length
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load audit log.'
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 1
  fetchAudit()
}

function clearFilters() {
  filters.value = { userId: '', entityType: '', action: '', dateFrom: '', dateTo: '' }
  page.value = 1
  fetchAudit()
}

function onPageChange(p) {
  page.value = p
  fetchAudit()
}

onMounted(fetchAudit)
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Audit Log</h1>
    </div>

    <div class="card filters">
      <div class="filter-grid">
        <div class="form-group">
          <label>User ID</label>
          <input v-model="filters.userId" placeholder="Filter by user..." />
        </div>
        <div class="form-group">
          <label>Entity Type</label>
          <select v-model="filters.entityType">
            <option value="">All</option>
            <option v-for="et in entityTypes" :key="et" :value="et">{{ et }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>Action</label>
          <select v-model="filters.action">
            <option value="">All</option>
            <option v-for="a in actions" :key="a" :value="a">{{ a }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>Date From</label>
          <input v-model="filters.dateFrom" type="date" />
        </div>
        <div class="form-group">
          <label>Date To</label>
          <input v-model="filters.dateTo" type="date" />
        </div>
      </div>
      <div class="filter-actions">
        <button class="btn-primary" @click="applyFilters">Apply Filters</button>
        <button class="btn-secondary" @click="clearFilters">Clear</button>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :rows="entries"
      :loading="loading"
      :error="error"
      :page="page"
      :total-pages="totalPages"
      :total-items="totalItems"
      empty-message="No audit entries found."
      @page-change="onPageChange"
      @retry="fetchAudit"
    />
  </div>
</template>

<style scoped>
.filters {
  margin-bottom: 16px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.filter-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
</style>
