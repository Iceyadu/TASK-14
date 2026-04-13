<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { examSessionApi, refApi, roomApi } from '../../api/client.js'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const saving = ref(false)
const formError = ref('')

const form = ref({
  name: '',
  termId: '',
  courseId: '',
  campusId: '',
  roomId: '',
  examDate: '',
  startTime: '',
  endTime: '',
  classIds: []
})

const errors = ref({})

const terms = ref([])
const courses = ref([])
const campuses = ref([])
const rooms = ref([])
const classes = ref([])

function validate() {
  const e = {}
  if (!form.value.name.trim()) e.name = 'Name is required.'
  if (!form.value.termId) e.termId = 'Please select a term.'
  if (!form.value.examDate) e.examDate = 'Exam date is required.'
  if (!form.value.startTime) e.startTime = 'Start time is required.'
  if (!form.value.endTime) e.endTime = 'End time is required.'
  if (form.value.startTime && form.value.endTime && form.value.startTime >= form.value.endTime) {
    e.endTime = 'End time must be after start time.'
  }
  if (!form.value.campusId) e.campusId = 'Please select a campus.'
  if (!form.value.roomId) e.roomId = 'Please select a room.'
  errors.value = e
  return Object.keys(e).length === 0
}

async function fetchRefData() {
  const [t, co, ca, cl] = await Promise.allSettled([
    refApi.terms(),
    refApi.courses(),
    roomApi.listCampuses(),
    refApi.classes()
  ])
  if (t.status === 'fulfilled') terms.value = t.value.data || []
  if (co.status === 'fulfilled') courses.value = co.value.data || []
  if (ca.status === 'fulfilled') campuses.value = ca.value.data?.items || ca.value.data || []
  if (cl.status === 'fulfilled') classes.value = cl.value.data || []
}

async function fetchRooms() {
  if (!form.value.campusId) {
    rooms.value = []
    return
  }
  try {
    const res = await roomApi.listRooms(form.value.campusId)
    rooms.value = res.data?.items || res.data || []
  } catch {
    rooms.value = []
  }
}

watch(() => form.value.campusId, fetchRooms)

async function loadSession() {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await examSessionApi.get(route.params.id)
    const s = res.data
    form.value = {
      name: s.name || '',
      termId: s.termId || '',
      courseId: s.courseId || '',
      campusId: s.campusId || '',
      roomId: s.roomId || '',
      examDate: s.examDate || '',
      startTime: s.startTime || '',
      endTime: s.endTime || '',
      classIds: s.classIds || []
    }
  } catch (e) {
    formError.value = e.response?.data?.message || 'Failed to load session.'
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!validate()) return
  saving.value = true
  formError.value = ''
  try {
    if (isEdit.value) {
      await examSessionApi.update(route.params.id, form.value)
    } else {
      await examSessionApi.create(form.value)
    }
    router.push('/exam-sessions')
  } catch (e) {
    formError.value = e.response?.data?.message || 'Failed to save session.'
  } finally {
    saving.value = false
  }
}

function toggleClass(classId) {
  const idx = form.value.classIds.indexOf(classId)
  if (idx >= 0) {
    form.value.classIds.splice(idx, 1)
  } else {
    form.value.classIds.push(classId)
  }
}

onMounted(async () => {
  await fetchRefData()
  await loadSession()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h1>{{ isEdit ? 'Edit Exam Session' : 'New Exam Session' }}</h1>
      <router-link to="/exam-sessions" class="btn-secondary" style="text-decoration:none;">
        Cancel
      </router-link>
    </div>

    <div v-if="loading" class="card" style="text-align:center; padding:40px;">Loading...</div>

    <form v-else class="card" @submit.prevent="handleSubmit">
      <div class="form-grid">
        <div class="form-group">
          <label>Session Name *</label>
          <input v-model="form.name" placeholder="e.g. Math Final Exam" />
          <div v-if="errors.name" class="error-text">{{ errors.name }}</div>
        </div>

        <div class="form-group">
          <label>Term *</label>
          <select v-model="form.termId">
            <option value="">Select term...</option>
            <option v-for="t in terms" :key="t.id" :value="t.id">{{ t.name }}</option>
          </select>
          <div v-if="errors.termId" class="error-text">{{ errors.termId }}</div>
        </div>

        <div class="form-group">
          <label>Course</label>
          <select v-model="form.courseId">
            <option value="">Select course (optional)...</option>
            <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </div>

        <div class="form-group">
          <label>Campus *</label>
          <select v-model="form.campusId">
            <option value="">Select campus...</option>
            <option v-for="c in campuses" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <div v-if="errors.campusId" class="error-text">{{ errors.campusId }}</div>
        </div>

        <div class="form-group">
          <label>Room *</label>
          <select v-model="form.roomId" :disabled="!form.campusId">
            <option value="">{{ form.campusId ? 'Select room...' : 'Select a campus first' }}</option>
            <option v-for="r in rooms" :key="r.id" :value="r.id">
              {{ r.name }} (capacity: {{ r.capacity || '?' }})
            </option>
          </select>
          <div v-if="errors.roomId" class="error-text">{{ errors.roomId }}</div>
        </div>

        <div class="form-group">
          <label>Exam Date *</label>
          <input v-model="form.examDate" type="date" />
          <div v-if="errors.examDate" class="error-text">{{ errors.examDate }}</div>
        </div>

        <div class="form-group">
          <label>Start Time *</label>
          <input v-model="form.startTime" type="time" />
          <div v-if="errors.startTime" class="error-text">{{ errors.startTime }}</div>
        </div>

        <div class="form-group">
          <label>End Time *</label>
          <input v-model="form.endTime" type="time" />
          <div v-if="errors.endTime" class="error-text">{{ errors.endTime }}</div>
        </div>
      </div>

      <div class="form-group">
        <label>Classes</label>
        <div class="class-picker">
          <label
            v-for="c in classes"
            :key="c.id"
            class="class-option"
          >
            <input
              type="checkbox"
              :checked="form.classIds.includes(c.id)"
              @change="toggleClass(c.id)"
            />
            <span>{{ c.name }}</span>
          </label>
          <p v-if="classes.length === 0" style="color:var(--gray-500); font-size:13px;">
            No classes available.
          </p>
        </div>
      </div>

      <div v-if="formError" class="error-text" style="margin-bottom:12px;">{{ formError }}</div>

      <div class="form-actions">
        <router-link to="/exam-sessions" class="btn-secondary" style="text-decoration:none;">Cancel</router-link>
        <button type="submit" class="btn-primary" :disabled="saving">
          {{ saving ? 'Saving...' : (isEdit ? 'Update Session' : 'Create Session') }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 24px;
}

.class-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
  padding: 8px;
  border: 1px solid var(--gray-200);
  border-radius: var(--radius);
}

.class-option {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: var(--gray-50);
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
}

.class-option input {
  width: auto;
}

.form-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--gray-200);
}
</style>
