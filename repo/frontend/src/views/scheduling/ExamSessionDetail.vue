<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { examSessionApi } from '../../api/client.js'
import { useAuthStore } from '../../stores/auth.js'
import StatusBadge from '../../components/shared/StatusBadge.vue'
import ConfirmDialog from '../../components/shared/ConfirmDialog.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const session = ref(null)
const loading = ref(true)
const error = ref(null)

const confirmAction = ref(null)
const showConfirm = ref(false)
const actionInProgress = ref(false)

const isStudent = computed(() => auth.role === 'STUDENT')

const transitions = computed(() => {
  if (!session.value || isStudent.value) return []
  const status = session.value.status
  const t = []
  if (status === 'DRAFT' || status === 'REJECTED') {
    t.push({ action: 'submit', label: 'Submit for Review', style: 'btn-primary' })
  }
  if (status === 'APPROVED') {
    t.push({ action: 'publish', label: 'Publish', style: 'btn-success' })
  }
  if (status === 'PUBLISHED') {
    t.push({ action: 'unpublish', label: 'Unpublish', style: 'btn-secondary' })
  }
  if (status === 'UNPUBLISHED') {
    t.push({ action: 'archive', label: 'Archive', style: 'btn-secondary' })
  }
  return t
})

async function fetchSession() {
  loading.value = true
  error.value = null
  try {
    const res = await examSessionApi.get(route.params.id)
    session.value = res.data
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load session.'
  } finally {
    loading.value = false
  }
}

function promptAction(action) {
  confirmAction.value = action
  showConfirm.value = true
}

async function executeAction() {
  actionInProgress.value = true
  try {
    const action = confirmAction.value
    if (action === 'submit') {
      await examSessionApi.submitReview(session.value.id)
    } else if (action === 'publish') {
      const key = crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36)
      await examSessionApi.publish(session.value.id, key)
    } else if (action === 'unpublish') {
      await examSessionApi.unpublish(session.value.id)
    } else if (action === 'archive') {
      await examSessionApi.archive(session.value.id)
    }
    showConfirm.value = false
    await fetchSession()
  } catch (e) {
    alert(e.response?.data?.message || 'Action failed.')
  } finally {
    actionInProgress.value = false
  }
}

onMounted(fetchSession)
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Exam Session Detail</h1>
      <router-link to="/exam-sessions" class="btn-secondary" style="text-decoration:none;">
        Back to List
      </router-link>
    </div>

    <div v-if="loading" class="card" style="text-align:center; padding:40px;">Loading...</div>
    <div v-else-if="error" class="card" style="color:var(--danger);">{{ error }}</div>

    <template v-else-if="session">
      <div class="card">
        <div class="detail-header">
          <h2>{{ session.name }}</h2>
          <StatusBadge :status="session.status" />
        </div>

        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-label">Exam Date</span>
            <span>{{ session.examDate || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Time</span>
            <span>{{ session.startTime || '-' }} - {{ session.endTime || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Campus</span>
            <span>{{ session.campusName || session.campusId || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Room</span>
            <span>{{ session.roomName || session.roomId || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Term</span>
            <span>{{ session.termName || session.termId || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Course</span>
            <span>{{ session.courseName || session.courseId || '-' }}</span>
          </div>
          <div class="detail-item" v-if="session.classIds?.length">
            <span class="detail-label">Classes</span>
            <span>{{ session.classNames?.join(', ') || session.classIds.join(', ') }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Created By</span>
            <span>{{ session.createdBy || '-' }}</span>
          </div>
        </div>

        <div v-if="transitions.length > 0" class="action-bar">
          <router-link
            v-if="(session.status === 'DRAFT' || session.status === 'REJECTED') && auth.hasPermission('SESSION_CREATE')"
            :to="'/exam-sessions/new?edit=' + session.id"
            class="btn-secondary"
            style="text-decoration:none;"
          >
            Edit
          </router-link>
          <button
            v-for="t in transitions"
            :key="t.action"
            :class="[t.style]"
            @click="promptAction(t.action)"
          >
            {{ t.label }}
          </button>
        </div>

        <div class="version-link">
          <router-link :to="'/versions/ExamSession/' + session.id">
            View Version History
          </router-link>
        </div>
      </div>
    </template>

    <ConfirmDialog
      :visible="showConfirm"
      :title="'Confirm ' + (confirmAction || '')"
      :message="'Are you sure you want to ' + (confirmAction || '') + ' this exam session?'"
      :confirm-text="actionInProgress ? 'Processing...' : 'Confirm'"
      @confirm="executeAction"
      @cancel="showConfirm = false"
    />
  </div>
</template>

<style scoped>
.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.detail-header h2 {
  font-size: 20px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.detail-item {
  display: flex;
  flex-direction: column;
}

.detail-label {
  font-size: 12px;
  text-transform: uppercase;
  color: var(--gray-500);
  font-weight: 600;
  letter-spacing: 0.03em;
  margin-bottom: 2px;
}

.action-bar {
  display: flex;
  gap: 8px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--gray-200);
}

.version-link {
  margin-top: 16px;
  font-size: 13px;
}

.version-link a {
  color: var(--primary);
  text-decoration: none;
}
.version-link a:hover { text-decoration: underline; }
</style>
