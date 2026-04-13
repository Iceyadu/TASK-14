<script setup>
import { ref, onMounted } from 'vue'
import { proctorApi, examSessionApi, userApi } from '../../api/client.js'
import DataTable from '../../components/shared/DataTable.vue'
import ConfirmDialog from '../../components/shared/ConfirmDialog.vue'

const columns = [
  { key: 'proctorName', label: 'Proctor' },
  { key: 'sessionName', label: 'Exam Session' },
  { key: 'examDate', label: 'Date' },
  { key: 'startTime', label: 'Start' },
  { key: 'endTime', label: 'End' },
  { key: 'actions', label: 'Actions', width: '100px' }
]

const assignments = ref([])
const loading = ref(false)
const error = ref(null)
const page = ref(1)
const totalPages = ref(1)

const showCreate = ref(false)
const form = ref({ userId: '', sessionId: '' })
const formError = ref('')
const saving = ref(false)

const sessions = ref([])
const users = ref([])

const removeTarget = ref(null)
const showRemoveDialog = ref(false)

async function fetchAssignments() {
  loading.value = true
  error.value = null
  try {
    const res = await proctorApi.list({ page: page.value, limit: 20 })
    const data = res.data
    assignments.value = data.items || data
    totalPages.value = data.totalPages || 1
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load proctor assignments.'
  } finally {
    loading.value = false
  }
}

async function fetchOptions() {
  try {
    const [s, u] = await Promise.allSettled([
      examSessionApi.list({ limit: 100 }),
      userApi.list({ role: 'TEACHER', limit: 100 })
    ])
    if (s.status === 'fulfilled') sessions.value = s.value.data?.items || s.value.data || []
    if (u.status === 'fulfilled') users.value = u.value.data?.items || u.value.data || []
  } catch { /* ignore */ }
}

function openCreate() {
  form.value = { userId: '', sessionId: '' }
  formError.value = ''
  showCreate.value = true
  fetchOptions()
}

async function saveAssignment() {
  if (!form.value.userId || !form.value.sessionId) {
    formError.value = 'Please select both a proctor and a session.'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    await proctorApi.create(form.value)
    showCreate.value = false
    await fetchAssignments()
  } catch (e) {
    formError.value = e.response?.data?.message || 'Failed to create assignment.'
  } finally {
    saving.value = false
  }
}

function promptRemove(assignment) {
  removeTarget.value = assignment
  showRemoveDialog.value = true
}

async function confirmRemove() {
  try {
    await proctorApi.remove(removeTarget.value.id)
    showRemoveDialog.value = false
    await fetchAssignments()
  } catch (e) {
    alert(e.response?.data?.message || 'Failed to remove assignment.')
  }
}

function onPageChange(p) {
  page.value = p
  fetchAssignments()
}

onMounted(fetchAssignments)
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Proctor Assignments</h1>
      <button class="btn-primary" @click="openCreate">Assign Proctor</button>
    </div>

    <DataTable
      :columns="columns"
      :rows="assignments"
      :loading="loading"
      :error="error"
      :page="page"
      :total-pages="totalPages"
      empty-message="No proctor assignments found."
      @page-change="onPageChange"
      @retry="fetchAssignments"
    >
      <template #cell-actions="{ row }">
        <button class="btn-danger btn-sm" @click="promptRemove(row)">Remove</button>
      </template>
    </DataTable>

    <Teleport to="body">
      <div v-if="showCreate" class="dialog-overlay" @click.self="showCreate = false">
        <div class="dialog-box">
          <h3>Assign Proctor</h3>
          <form @submit.prevent="saveAssignment">
            <div class="form-group">
              <label>Proctor (Teacher) *</label>
              <select v-model="form.userId">
                <option value="">Select a teacher...</option>
                <option v-for="u in users" :key="u.id" :value="u.id">
                  {{ u.displayName || u.username }}
                </option>
              </select>
            </div>
            <div class="form-group">
              <label>Exam Session *</label>
              <select v-model="form.sessionId">
                <option value="">Select a session...</option>
                <option v-for="s in sessions" :key="s.id" :value="s.id">
                  {{ s.name }}
                </option>
              </select>
            </div>
            <div v-if="formError" class="error-text">{{ formError }}</div>
            <div class="dialog-actions">
              <button type="button" class="btn-secondary" @click="showCreate = false">Cancel</button>
              <button type="submit" class="btn-primary" :disabled="saving">
                {{ saving ? 'Assigning...' : 'Assign' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>

    <ConfirmDialog
      :visible="showRemoveDialog"
      title="Remove Assignment"
      message="Are you sure you want to remove this proctor assignment?"
      confirm-text="Remove"
      :danger="true"
      @confirm="confirmRemove"
      @cancel="showRemoveDialog = false"
    />
  </div>
</template>

<style scoped>
.dialog-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center; z-index: 1000;
}
.dialog-box {
  background: white; border-radius: 8px; padding: 24px;
  width: 90%; max-width: 440px; box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}
.dialog-box h3 { margin-bottom: 16px; }
.dialog-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px; }
</style>
