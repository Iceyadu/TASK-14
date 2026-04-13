<script setup>
import { ref, onMounted } from 'vue'
import { complianceApi } from '../../api/client.js'
import DataTable from '../../components/shared/DataTable.vue'
import StatusBadge from '../../components/shared/StatusBadge.vue'

const columns = [
  { key: 'entityType', label: 'Type' },
  { key: 'entityName', label: 'Name' },
  { key: 'submittedBy', label: 'Submitted By' },
  { key: 'submittedAt', label: 'Submitted' },
  { key: 'status', label: 'Status' },
  { key: 'actions', label: 'Actions', width: '200px' }
]

const reviews = ref([])
const loading = ref(false)
const error = ref(null)
const page = ref(1)
const totalPages = ref(1)
const statusFilter = ref('PENDING')

const showActionDialog = ref(false)
const actionType = ref('')
const actionTarget = ref(null)
const comment = ref('')
const commentError = ref('')
const actionSaving = ref(false)

async function fetchReviews() {
  loading.value = true
  error.value = null
  try {
    const params = { page: page.value, limit: 20 }
    if (statusFilter.value) params.status = statusFilter.value
    const res = await complianceApi.listReviews(params)
    const data = res.data
    reviews.value = (data.items || data).map(r => ({
      ...r,
      submittedAt: r.submittedAt ? new Date(r.submittedAt).toLocaleDateString() : '-'
    }))
    totalPages.value = data.totalPages || 1
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load reviews.'
  } finally {
    loading.value = false
  }
}

function openAction(type, review) {
  actionType.value = type
  actionTarget.value = review
  comment.value = ''
  commentError.value = ''
  showActionDialog.value = true
}

async function submitAction() {
  if (actionType.value === 'reject' && !comment.value.trim()) {
    commentError.value = 'A comment is required when rejecting.'
    return
  }
  actionSaving.value = true
  commentError.value = ''
  try {
    const data = { comment: comment.value }
    if (actionType.value === 'approve') {
      await complianceApi.approve(actionTarget.value.id, data)
    } else {
      await complianceApi.reject(actionTarget.value.id, data)
    }
    showActionDialog.value = false
    await fetchReviews()
  } catch (e) {
    commentError.value = e.response?.data?.message || 'Action failed.'
  } finally {
    actionSaving.value = false
  }
}

function onPageChange(p) {
  page.value = p
  fetchReviews()
}

function onFilter() {
  page.value = 1
  fetchReviews()
}

onMounted(fetchReviews)
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Compliance Review Queue</h1>
    </div>

    <div class="card" style="margin-bottom:16px;">
      <select v-model="statusFilter" @change="onFilter" style="max-width:200px;">
        <option value="">All</option>
        <option value="PENDING">Pending</option>
        <option value="APPROVED">Approved</option>
        <option value="REJECTED">Rejected</option>
      </select>
    </div>

    <DataTable
      :columns="columns"
      :rows="reviews"
      :loading="loading"
      :error="error"
      :page="page"
      :total-pages="totalPages"
      empty-message="No reviews in queue."
      @page-change="onPageChange"
      @retry="fetchReviews"
    >
      <template #cell-status="{ value }">
        <StatusBadge :status="value" />
      </template>
      <template #cell-actions="{ row }">
        <div v-if="row.status === 'PENDING' || row.status === 'PENDING_REVIEW'" class="action-buttons">
          <button class="btn-success btn-sm" @click="openAction('approve', row)">Approve</button>
          <button class="btn-danger btn-sm" @click="openAction('reject', row)">Reject</button>
        </div>
        <span v-else style="color:var(--gray-500); font-size:13px;">-</span>
      </template>
    </DataTable>

    <!-- Action Dialog -->
    <Teleport to="body">
      <div v-if="showActionDialog" class="dialog-overlay" @click.self="showActionDialog = false">
        <div class="dialog-box">
          <h3>{{ actionType === 'approve' ? 'Approve' : 'Reject' }} Review</h3>
          <p style="color:var(--gray-600); font-size:14px; margin-bottom:16px;">
            {{ actionTarget?.entityName || 'this item' }}
          </p>
          <div class="form-group">
            <label>Comment {{ actionType === 'reject' ? '*' : '(optional)' }}</label>
            <textarea v-model="comment" rows="3" placeholder="Add a comment..."></textarea>
            <div v-if="commentError" class="error-text">{{ commentError }}</div>
          </div>
          <div class="dialog-actions">
            <button class="btn-secondary" @click="showActionDialog = false">Cancel</button>
            <button
              :class="actionType === 'approve' ? 'btn-success' : 'btn-danger'"
              :disabled="actionSaving"
              @click="submitAction"
            >
              {{ actionSaving ? 'Processing...' : (actionType === 'approve' ? 'Approve' : 'Reject') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.action-buttons { display: flex; gap: 4px; }
.dialog-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center; z-index: 1000;
}
.dialog-box {
  background: white; border-radius: 8px; padding: 24px;
  width: 90%; max-width: 480px; box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}
.dialog-box h3 { margin-bottom: 8px; }
.dialog-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px; }
</style>
