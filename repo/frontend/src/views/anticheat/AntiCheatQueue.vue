<script setup>
import { ref, onMounted } from 'vue'
import { antiCheatApi } from '../../api/client.js'
import DataTable from '../../components/shared/DataTable.vue'
import StatusBadge from '../../components/shared/StatusBadge.vue'

const columns = [
  { key: 'ruleType', label: 'Rule Type' },
  { key: 'studentName', label: 'Student' },
  { key: 'sessionName', label: 'Session' },
  { key: 'flaggedAt', label: 'Flagged At' },
  { key: 'status', label: 'Status' },
  { key: 'actions', label: 'Actions', width: '160px' }
]

const flags = ref([])
const loading = ref(false)
const error = ref(null)
const page = ref(1)
const totalPages = ref(1)

const showReview = ref(false)
const reviewTarget = ref(null)
const decision = ref('DISMISSED')
const reviewComment = ref('')
const reviewError = ref('')
const reviewSaving = ref(false)

function maskName(name) {
  if (!name) return '****'
  const parts = name.split(' ')
  return parts.map(p => p.charAt(0) + '***').join(' ')
}

async function fetchFlags() {
  loading.value = true
  error.value = null
  try {
    const res = await antiCheatApi.listFlags({ page: page.value, limit: 20 })
    const data = res.data
    flags.value = (data.items || data).map(f => ({
      ...f,
      studentName: maskName(f.studentName || f.studentDisplayName),
      flaggedAt: f.flaggedAt ? new Date(f.flaggedAt).toLocaleString() : '-'
    }))
    totalPages.value = data.totalPages || 1
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load anti-cheat flags.'
  } finally {
    loading.value = false
  }
}

function openReview(flag) {
  reviewTarget.value = flag
  decision.value = 'DISMISSED'
  reviewComment.value = ''
  reviewError.value = ''
  showReview.value = true
}

async function submitReview() {
  if (!reviewComment.value.trim()) {
    reviewError.value = 'A comment is required.'
    return
  }
  reviewSaving.value = true
  reviewError.value = ''
  try {
    await antiCheatApi.reviewFlag(reviewTarget.value.id, {
      decision: decision.value,
      comment: reviewComment.value
    })
    showReview.value = false
    await fetchFlags()
  } catch (e) {
    reviewError.value = e.response?.data?.message || 'Review failed.'
  } finally {
    reviewSaving.value = false
  }
}

function onPageChange(p) {
  page.value = p
  fetchFlags()
}

onMounted(fetchFlags)
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Anti-Cheat Review Queue</h1>
    </div>

    <DataTable
      :columns="columns"
      :rows="flags"
      :loading="loading"
      :error="error"
      :page="page"
      :total-pages="totalPages"
      empty-message="No flags to review."
      @page-change="onPageChange"
      @retry="fetchFlags"
    >
      <template #cell-status="{ value }">
        <StatusBadge :status="value" />
      </template>
      <template #cell-actions="{ row }">
        <button
          v-if="row.status === 'PENDING' || row.status === 'FLAGGED'"
          class="btn-primary btn-sm"
          @click="openReview(row)"
        >
          Review
        </button>
        <span v-else style="color:var(--gray-500); font-size:13px;">Reviewed</span>
      </template>
    </DataTable>

    <Teleport to="body">
      <div v-if="showReview" class="dialog-overlay" @click.self="showReview = false">
        <div class="dialog-box">
          <h3>Review Anti-Cheat Flag</h3>
          <div class="review-info">
            <p><strong>Rule:</strong> {{ reviewTarget?.ruleType }}</p>
            <p><strong>Student:</strong> {{ reviewTarget?.studentName }}</p>
            <p><strong>Session:</strong> {{ reviewTarget?.sessionName }}</p>
            <p v-if="reviewTarget?.details"><strong>Details:</strong> {{ reviewTarget.details }}</p>
          </div>
          <div class="form-group">
            <label>Decision *</label>
            <select v-model="decision">
              <option value="DISMISSED">Dismissed (false positive)</option>
              <option value="CONFIRMED">Confirmed (violation)</option>
            </select>
          </div>
          <div class="form-group">
            <label>Comment *</label>
            <textarea v-model="reviewComment" rows="3" placeholder="Explain your decision..."></textarea>
          </div>
          <div v-if="reviewError" class="error-text">{{ reviewError }}</div>
          <div class="dialog-actions">
            <button class="btn-secondary" @click="showReview = false">Cancel</button>
            <button
              :class="decision === 'CONFIRMED' ? 'btn-danger' : 'btn-success'"
              :disabled="reviewSaving"
              @click="submitReview"
            >
              {{ reviewSaving ? 'Submitting...' : (decision === 'CONFIRMED' ? 'Confirm Violation' : 'Dismiss Flag') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.review-info {
  background: var(--gray-50);
  padding: 12px;
  border-radius: var(--radius);
  margin-bottom: 16px;
  font-size: 13px;
}

.review-info p {
  margin-bottom: 4px;
}

.dialog-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center; z-index: 1000;
}
.dialog-box {
  background: white; border-radius: 8px; padding: 24px;
  width: 90%; max-width: 500px; box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}
.dialog-box h3 { margin-bottom: 12px; }
.dialog-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px; }
</style>
