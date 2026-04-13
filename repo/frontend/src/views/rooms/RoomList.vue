<script setup>
import { ref, onMounted } from 'vue'
import { roomApi } from '../../api/client.js'
import DataTable from '../../components/shared/DataTable.vue'

const columns = [
  { key: 'name', label: 'Room Name' },
  { key: 'campusName', label: 'Campus' },
  { key: 'building', label: 'Building' },
  { key: 'floor', label: 'Floor' },
  { key: 'capacity', label: 'Capacity' },
  { key: 'actions', label: 'Actions', width: '120px' }
]

const campuses = ref([])
const selectedCampus = ref('')
const rooms = ref([])
const loading = ref(false)
const error = ref(null)

const showEdit = ref(false)
const editRoom = ref(null)
const editForm = ref({ name: '', capacity: '', building: '', floor: '' })
const editError = ref('')
const editSaving = ref(false)

async function fetchCampuses() {
  try {
    const res = await roomApi.listCampuses()
    campuses.value = res.data?.items || res.data || []
    if (campuses.value.length > 0 && !selectedCampus.value) {
      selectedCampus.value = campuses.value[0].id
      await fetchRooms()
    }
  } catch { /* ignore */ }
}

async function fetchRooms() {
  if (!selectedCampus.value) {
    rooms.value = []
    return
  }
  loading.value = true
  error.value = null
  try {
    const res = await roomApi.listRooms(selectedCampus.value)
    const campus = campuses.value.find(c => c.id === selectedCampus.value)
    rooms.value = (res.data?.items || res.data || []).map(r => ({
      ...r,
      campusName: campus?.name || '-'
    }))
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load rooms.'
  } finally {
    loading.value = false
  }
}

function openEdit(room) {
  editRoom.value = room
  editForm.value = {
    name: room.name,
    capacity: room.capacity || '',
    building: room.building || '',
    floor: room.floor || ''
  }
  editError.value = ''
  showEdit.value = true
}

async function saveEdit() {
  if (!editForm.value.name.trim()) {
    editError.value = 'Room name is required.'
    return
  }
  editSaving.value = true
  editError.value = ''
  try {
    const data = {
      ...editForm.value,
      capacity: editForm.value.capacity ? parseInt(editForm.value.capacity) : null
    }
    await roomApi.updateRoom(editRoom.value.id, data)
    showEdit.value = false
    await fetchRooms()
  } catch (e) {
    editError.value = e.response?.data?.message || 'Failed to update room.'
  } finally {
    editSaving.value = false
  }
}

function onCampusChange() {
  fetchRooms()
}

onMounted(fetchCampuses)
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Room Management</h1>
    </div>

    <div class="card" style="margin-bottom:16px;">
      <div class="form-group" style="max-width:300px; margin-bottom:0;">
        <label>Select Campus</label>
        <select v-model="selectedCampus" @change="onCampusChange">
          <option value="">Choose a campus...</option>
          <option v-for="c in campuses" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :rows="rooms"
      :loading="loading"
      :error="error"
      empty-message="No rooms found for this campus."
      @retry="fetchRooms"
    >
      <template #cell-capacity="{ value }">
        {{ value || '-' }}
      </template>
      <template #cell-actions="{ row }">
        <button class="btn-secondary btn-sm" @click="openEdit(row)">Edit</button>
      </template>
    </DataTable>

    <Teleport to="body">
      <div v-if="showEdit" class="dialog-overlay" @click.self="showEdit = false">
        <div class="dialog-box">
          <h3>Edit Room</h3>
          <form @submit.prevent="saveEdit">
            <div class="form-group">
              <label>Name *</label>
              <input v-model="editForm.name" />
            </div>
            <div class="form-group">
              <label>Building</label>
              <input v-model="editForm.building" />
            </div>
            <div class="form-group">
              <label>Floor</label>
              <input v-model="editForm.floor" />
            </div>
            <div class="form-group">
              <label>Capacity</label>
              <input v-model="editForm.capacity" type="number" min="1" />
            </div>
            <div v-if="editError" class="error-text">{{ editError }}</div>
            <div class="dialog-actions">
              <button type="button" class="btn-secondary" @click="showEdit = false">Cancel</button>
              <button type="submit" class="btn-primary" :disabled="editSaving">
                {{ editSaving ? 'Saving...' : 'Save' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
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
