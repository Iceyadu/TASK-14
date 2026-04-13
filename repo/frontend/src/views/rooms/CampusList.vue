<script setup>
import { ref, onMounted } from 'vue'
import { roomApi } from '../../api/client.js'
import DataTable from '../../components/shared/DataTable.vue'

const columns = [
  { key: 'name', label: 'Campus Name' },
  { key: 'address', label: 'Address' },
  { key: 'roomCount', label: 'Rooms' },
  { key: 'actions', label: 'Actions', width: '180px' }
]

const campuses = ref([])
const loading = ref(false)
const error = ref(null)

const showDialog = ref(false)
const form = ref({ name: '', address: '' })
const formError = ref('')
const saving = ref(false)

const expandedCampus = ref(null)
const campusRooms = ref([])
const roomsLoading = ref(false)

const showRoomDialog = ref(false)
const roomForm = ref({ name: '', capacity: '', building: '', floor: '' })
const roomFormError = ref('')
const roomSaving = ref(false)

async function fetchCampuses() {
  loading.value = true
  error.value = null
  try {
    const res = await roomApi.listCampuses()
    campuses.value = res.data?.items || res.data || []
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load campuses.'
  } finally {
    loading.value = false
  }
}

async function toggleRooms(campus) {
  if (expandedCampus.value === campus.id) {
    expandedCampus.value = null
    return
  }
  expandedCampus.value = campus.id
  roomsLoading.value = true
  try {
    const res = await roomApi.listRooms(campus.id)
    campusRooms.value = res.data?.items || res.data || []
  } catch {
    campusRooms.value = []
  } finally {
    roomsLoading.value = false
  }
}

function openCreate() {
  form.value = { name: '', address: '' }
  formError.value = ''
  showDialog.value = true
}

async function saveCampus() {
  if (!form.value.name.trim()) {
    formError.value = 'Name is required.'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    await roomApi.createCampus(form.value)
    showDialog.value = false
    await fetchCampuses()
  } catch (e) {
    formError.value = e.response?.data?.message || 'Failed to create campus.'
  } finally {
    saving.value = false
  }
}

function openAddRoom() {
  roomForm.value = { name: '', capacity: '', building: '', floor: '' }
  roomFormError.value = ''
  showRoomDialog.value = true
}

async function saveRoom() {
  if (!roomForm.value.name.trim()) {
    roomFormError.value = 'Room name is required.'
    return
  }
  roomSaving.value = true
  roomFormError.value = ''
  try {
    const data = {
      ...roomForm.value,
      capacity: roomForm.value.capacity ? parseInt(roomForm.value.capacity) : null
    }
    await roomApi.createRoom(expandedCampus.value, data)
    showRoomDialog.value = false
    const res = await roomApi.listRooms(expandedCampus.value)
    campusRooms.value = res.data?.items || res.data || []
    await fetchCampuses()
  } catch (e) {
    roomFormError.value = e.response?.data?.message || 'Failed to create room.'
  } finally {
    roomSaving.value = false
  }
}

onMounted(fetchCampuses)
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Campuses & Rooms</h1>
      <button class="btn-primary" @click="openCreate">Add Campus</button>
    </div>

    <DataTable
      :columns="columns"
      :rows="campuses"
      :loading="loading"
      :error="error"
      empty-message="No campuses found."
      @retry="fetchCampuses"
    >
      <template #cell-actions="{ row }">
        <div class="action-buttons">
          <button class="btn-secondary btn-sm" @click="toggleRooms(row)">
            {{ expandedCampus === row.id ? 'Hide Rooms' : 'Show Rooms' }}
          </button>
        </div>
      </template>
    </DataTable>

    <div v-if="expandedCampus" class="card rooms-panel">
      <div class="rooms-header">
        <h3>Rooms</h3>
        <button class="btn-primary btn-sm" @click="openAddRoom">Add Room</button>
      </div>
      <div v-if="roomsLoading" style="padding:20px; text-align:center; color:var(--gray-500);">
        Loading rooms...
      </div>
      <table v-else-if="campusRooms.length">
        <thead>
          <tr><th>Name</th><th>Building</th><th>Floor</th><th>Capacity</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in campusRooms" :key="r.id">
            <td>{{ r.name }}</td>
            <td>{{ r.building || '-' }}</td>
            <td>{{ r.floor || '-' }}</td>
            <td>{{ r.capacity || '-' }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else style="padding:16px; color:var(--gray-500);">No rooms in this campus.</p>
    </div>

    <!-- Campus Dialog -->
    <Teleport to="body">
      <div v-if="showDialog" class="dialog-overlay" @click.self="showDialog = false">
        <div class="dialog-box">
          <h3>Add Campus</h3>
          <form @submit.prevent="saveCampus">
            <div class="form-group">
              <label>Name *</label>
              <input v-model="form.name" />
            </div>
            <div class="form-group">
              <label>Address</label>
              <input v-model="form.address" />
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

    <!-- Room Dialog -->
    <Teleport to="body">
      <div v-if="showRoomDialog" class="dialog-overlay" @click.self="showRoomDialog = false">
        <div class="dialog-box">
          <h3>Add Room</h3>
          <form @submit.prevent="saveRoom">
            <div class="form-group">
              <label>Room Name *</label>
              <input v-model="roomForm.name" />
            </div>
            <div class="form-group">
              <label>Building</label>
              <input v-model="roomForm.building" />
            </div>
            <div class="form-group">
              <label>Floor</label>
              <input v-model="roomForm.floor" />
            </div>
            <div class="form-group">
              <label>Capacity</label>
              <input v-model="roomForm.capacity" type="number" min="1" />
            </div>
            <div v-if="roomFormError" class="error-text">{{ roomFormError }}</div>
            <div class="dialog-actions">
              <button type="button" class="btn-secondary" @click="showRoomDialog = false">Cancel</button>
              <button type="submit" class="btn-primary" :disabled="roomSaving">
                {{ roomSaving ? 'Saving...' : 'Save' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.action-buttons { display: flex; gap: 4px; }

.rooms-panel { margin-top: 16px; }
.rooms-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.rooms-header h3 { font-size: 16px; }

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
