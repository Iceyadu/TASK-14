<script setup>
import { ref, onMounted } from 'vue'
import { userApi, authApi } from '../../api/client.js'
import DataTable from '../../components/shared/DataTable.vue'
import StatusBadge from '../../components/shared/StatusBadge.vue'

const columns = [
  { key: 'username', label: 'Username' },
  { key: 'displayName', label: 'Name' },
  { key: 'role', label: 'Role' },
  { key: 'status', label: 'Status' },
  { key: 'actions', label: 'Actions', width: '200px' }
]

const users = ref([])
const loading = ref(false)
const error = ref(null)
const page = ref(1)
const totalPages = ref(1)

const showDialog = ref(false)
const editingUser = ref(null)
const form = ref({ username: '', displayName: '', role: 'STUDENT', password: '' })
const formError = ref('')
const saving = ref(false)

const roles = ['ADMIN', 'ACADEMIC_COORDINATOR', 'TEACHER', 'STUDENT']

async function fetchUsers() {
  loading.value = true
  error.value = null
  try {
    const res = await userApi.list({ page: page.value, limit: 20 })
    const data = res.data
    users.value = data.items || data
    totalPages.value = data.totalPages || 1
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load users.'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingUser.value = null
  form.value = { username: '', displayName: '', role: 'STUDENT', password: '' }
  formError.value = ''
  showDialog.value = true
}

function openEdit(user) {
  editingUser.value = user
  form.value = { username: user.username, displayName: user.displayName, role: user.role, password: '' }
  formError.value = ''
  showDialog.value = true
}

async function saveUser() {
  formError.value = ''
  if (!form.value.username) {
    formError.value = 'Username is required.'
    return
  }
  saving.value = true
  try {
    if (editingUser.value) {
      await userApi.update(editingUser.value.id, form.value)
    } else {
      if (!form.value.password) {
        formError.value = 'Password is required for new users.'
        saving.value = false
        return
      }
      await userApi.create(form.value)
    }
    showDialog.value = false
    await fetchUsers()
  } catch (e) {
    formError.value = e.response?.data?.message || 'Failed to save user.'
  } finally {
    saving.value = false
  }
}

async function terminateSession(userId) {
  if (!confirm('Terminate all sessions for this user?')) return
  try {
    await authApi.terminateSession(userId)
    await fetchUsers()
  } catch (e) {
    alert(e.response?.data?.message || 'Failed to terminate session.')
  }
}

async function unlockAccount(userId) {
  try {
    await authApi.unlockAccount(userId)
    await fetchUsers()
  } catch (e) {
    alert(e.response?.data?.message || 'Failed to unlock account.')
  }
}

function onPageChange(p) {
  page.value = p
  fetchUsers()
}

onMounted(fetchUsers)
</script>

<template>
  <div>
    <div class="page-header">
      <h1>User Management</h1>
      <button class="btn-primary" @click="openCreate">Create User</button>
    </div>

    <DataTable
      :columns="columns"
      :rows="users"
      :loading="loading"
      :error="error"
      :page="page"
      :total-pages="totalPages"
      empty-message="No users found."
      @page-change="onPageChange"
      @retry="fetchUsers"
    >
      <template #cell-role="{ value }">
        <StatusBadge :status="value" />
      </template>
      <template #cell-status="{ value }">
        <StatusBadge :status="value || 'ACTIVE'" />
      </template>
      <template #cell-actions="{ row }">
        <div class="action-buttons">
          <button class="btn-secondary btn-sm" @click="openEdit(row)">Edit</button>
          <button class="btn-secondary btn-sm" @click="terminateSession(row.id)">End Session</button>
          <button
            v-if="row.status === 'LOCKED'"
            class="btn-success btn-sm"
            @click="unlockAccount(row.id)"
          >Unlock</button>
        </div>
      </template>
    </DataTable>

    <!-- Create/Edit Dialog -->
    <Teleport to="body">
      <div v-if="showDialog" class="dialog-overlay" @click.self="showDialog = false">
        <div class="dialog-box">
          <h3>{{ editingUser ? 'Edit User' : 'Create User' }}</h3>
          <form @submit.prevent="saveUser">
            <div class="form-group">
              <label>Username</label>
              <input v-model="form.username" :disabled="!!editingUser" />
            </div>
            <div class="form-group">
              <label>Display Name</label>
              <input v-model="form.displayName" />
            </div>
            <div class="form-group">
              <label>Role</label>
              <select v-model="form.role">
                <option v-for="r in roles" :key="r" :value="r">{{ r }}</option>
              </select>
            </div>
            <div class="form-group">
              <label>{{ editingUser ? 'New Password (leave blank to keep)' : 'Password' }}</label>
              <input v-model="form.password" type="password" />
            </div>
            <div v-if="formError" class="error-text">{{ formError }}</div>
            <div class="dialog-actions">
              <button type="button" class="btn-secondary" @click="showDialog = false">Cancel</button>
              <button type="submit" class="btn-primary" :disabled="saving">
                {{ saving ? 'Saving...' : 'Save' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.action-buttons {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog-box {
  background: white;
  border-radius: 8px;
  padding: 24px;
  width: 90%;
  max-width: 480px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}

.dialog-box h3 {
  margin-bottom: 16px;
}

.dialog-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
