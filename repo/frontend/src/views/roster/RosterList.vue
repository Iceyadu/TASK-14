<script setup>
import { ref, onMounted } from 'vue'
import { rosterApi, refApi } from '../../api/client.js'
import DataTable from '../../components/shared/DataTable.vue'

const columns = [
  { key: 'studentName', label: 'Student Name' },
  { key: 'studentId', label: 'Student ID' },
  { key: 'className', label: 'Class' },
  { key: 'grade', label: 'Grade' },
  { key: 'term', label: 'Term' },
  { key: 'actions', label: 'Actions', width: '100px' }
]

const rosters = ref([])
const loading = ref(false)
const error = ref(null)
const page = ref(1)
const totalPages = ref(1)
const totalItems = ref(0)

const search = ref('')
const classFilter = ref('')
const termFilter = ref('')
const terms = ref([])
const classes = ref([])

function maskSensitive(value) {
  if (!value || value.length < 4) return '****'
  return value.substring(0, 2) + '****' + value.substring(value.length - 2)
}

async function fetchFilters() {
  try {
    const [t, c] = await Promise.allSettled([refApi.terms(), refApi.classes()])
    if (t.status === 'fulfilled') terms.value = t.value.data || []
    if (c.status === 'fulfilled') classes.value = c.value.data || []
  } catch { /* ignore */ }
}

async function fetchRosters() {
  loading.value = true
  error.value = null
  try {
    const params = { page: page.value, limit: 20 }
    if (search.value) params.search = search.value
    if (classFilter.value) params.classId = classFilter.value
    if (termFilter.value) params.termId = termFilter.value

    const res = await rosterApi.list(params)
    const data = res.data
    rosters.value = (data.items || data).map(r => ({
      ...r,
      studentId: maskSensitive(r.studentId),
      studentName: r.studentName || (r.firstName + ' ' + r.lastName)
    }))
    totalPages.value = data.totalPages || 1
    totalItems.value = data.totalItems || rosters.value.length
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load rosters.'
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  try {
    const params = {}
    if (classFilter.value) params.classId = classFilter.value
    if (termFilter.value) params.termId = termFilter.value
    const res = await rosterApi.export(params)
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', 'roster-export.csv')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    alert('Export failed: ' + (e.response?.data?.message || e.message))
  }
}

function handleSearch() {
  page.value = 1
  fetchRosters()
}

function onPageChange(p) {
  page.value = p
  fetchRosters()
}

onMounted(() => {
  fetchFilters()
  fetchRosters()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Rosters</h1>
      <div class="header-actions">
        <router-link to="/rosters/import" class="btn-primary" style="text-decoration:none; display:inline-block;">
          Import
        </router-link>
        <button class="btn-secondary" @click="handleExport">Export CSV</button>
      </div>
    </div>

    <div class="filters card">
      <div class="filter-row">
        <input
          v-model="search"
          placeholder="Search by name or ID..."
          @keyup.enter="handleSearch"
        />
        <select v-model="termFilter" @change="handleSearch">
          <option value="">All Terms</option>
          <option v-for="t in terms" :key="t.id" :value="t.id">{{ t.name }}</option>
        </select>
        <select v-model="classFilter" @change="handleSearch">
          <option value="">All Classes</option>
          <option v-for="c in classes" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <button class="btn-primary" @click="handleSearch">Search</button>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :rows="rosters"
      :loading="loading"
      :error="error"
      :page="page"
      :total-pages="totalPages"
      :total-items="totalItems"
      empty-message="No roster records found."
      @page-change="onPageChange"
      @retry="fetchRosters"
    >
      <template #cell-actions="{ row }">
        <router-link
          v-if="row.id"
          :to="'/versions/RosterEntry/' + row.id"
          class="btn-secondary btn-sm"
          style="text-decoration:none;"
        >
          History
        </router-link>
      </template>
    </DataTable>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
  gap: 8px;
}

.filters {
  margin-bottom: 16px;
}

.filter-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.filter-row input {
  max-width: 300px;
}

.filter-row select {
  max-width: 200px;
}
</style>
