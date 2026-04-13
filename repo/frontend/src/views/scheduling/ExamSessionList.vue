<script setup>
import { ref, onMounted } from 'vue'
import { examSessionApi, refApi } from '../../api/client.js'
import DataTable from '../../components/shared/DataTable.vue'
import StatusBadge from '../../components/shared/StatusBadge.vue'
import { useAuthStore } from '../../stores/auth.js'

const auth = useAuthStore()

const columns = [
  { key: 'name', label: 'Name' },
  { key: 'examDate', label: 'Exam Date' },
  { key: 'startTime', label: 'Start' },
  { key: 'endTime', label: 'End' },
  { key: 'campusName', label: 'Campus' },
  { key: 'status', label: 'Status' },
  { key: 'actions', label: 'Actions', width: '220px' }
]

const sessions = ref([])
const loading = ref(false)
const error = ref(null)
const page = ref(1)
const totalPages = ref(1)

const statusFilter = ref('')
const termFilter = ref('')
const terms = ref([])

const statusActions = {
  DRAFT: ['submit'],
  SUBMITTED_FOR_COMPLIANCE_REVIEW: [],
  APPROVED: ['publish'],
  PUBLISHED: ['unpublish'],
  UNPUBLISHED: ['archive'],
  ARCHIVED: [],
  REJECTED: ['submit'],
  RESTORED: []
}

async function fetchTerms() {
  try {
    const res = await refApi.terms()
    terms.value = res.data || []
  } catch { /* ignore */ }
}

async function fetchSessions() {
  loading.value = true
  error.value = null
  try {
    const params = { page: page.value, limit: 20 }
    if (statusFilter.value) params.status = statusFilter.value
    if (termFilter.value) params.termId = termFilter.value
    const res = await examSessionApi.list(params)
    const data = res.data
    sessions.value = data.items || data
    totalPages.value = data.totalPages || 1
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load exam sessions.'
  } finally {
    loading.value = false
  }
}

async function doAction(action, session) {
  try {
    if (action === 'submit') {
      await examSessionApi.submitReview(session.id)
    } else if (action === 'publish') {
      const key = crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36)
      await examSessionApi.publish(session.id, key)
    } else if (action === 'unpublish') {
      await examSessionApi.unpublish(session.id)
    } else if (action === 'archive') {
      await examSessionApi.archive(session.id)
    }
    await fetchSessions()
  } catch (e) {
    alert(e.response?.data?.message || `Failed to ${action} session.`)
  }
}

function getActions(status) {
  return statusActions[status] || []
}

function onPageChange(p) {
  page.value = p
  fetchSessions()
}

function onFilter() {
  page.value = 1
  fetchSessions()
}

onMounted(() => {
  fetchTerms()
  fetchSessions()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Exam Sessions</h1>
      <router-link
        v-if="auth.hasPermission('SESSION_CREATE')"
        to="/exam-sessions/new"
        class="btn-primary"
        style="text-decoration:none;"
      >
        New Session
      </router-link>
    </div>

    <div class="filters card">
      <div class="filter-row">
        <select v-model="statusFilter" @change="onFilter">
          <option value="">All Statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="SUBMITTED_FOR_COMPLIANCE_REVIEW">Pending Review</option>
          <option value="APPROVED">Approved</option>
          <option value="PUBLISHED">Published</option>
          <option value="UNPUBLISHED">Unpublished</option>
          <option value="ARCHIVED">Archived</option>
          <option value="REJECTED">Rejected</option>
        </select>
        <select v-model="termFilter" @change="onFilter">
          <option value="">All Terms</option>
          <option v-for="t in terms" :key="t.id" :value="t.id">{{ t.name }}</option>
        </select>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :rows="sessions"
      :loading="loading"
      :error="error"
      :page="page"
      :total-pages="totalPages"
      empty-message="No exam sessions found."
      @page-change="onPageChange"
      @retry="fetchSessions"
    >
      <template #cell-name="{ row }">
        <router-link :to="'/exam-sessions/' + row.id" style="color:var(--primary); text-decoration:none;">
          {{ row.name }}
        </router-link>
      </template>
      <template #cell-status="{ value }">
        <StatusBadge :status="value" />
      </template>
      <template #cell-actions="{ row }">
        <div class="action-buttons">
          <router-link
            :to="'/exam-sessions/' + row.id"
            class="btn-secondary btn-sm"
            style="text-decoration:none;"
          >
            View
          </router-link>
          <button
            v-for="action in getActions(row.status)"
            :key="action"
            class="btn-primary btn-sm"
            @click="doAction(action, row)"
          >
            {{ action.charAt(0).toUpperCase() + action.slice(1) }}
          </button>
        </div>
      </template>
    </DataTable>
  </div>
</template>

<style scoped>
.filters { margin-bottom: 16px; }
.filter-row { display: flex; gap: 12px; }
.filter-row select { max-width: 200px; }
.action-buttons { display: flex; gap: 4px; flex-wrap: wrap; }
</style>
