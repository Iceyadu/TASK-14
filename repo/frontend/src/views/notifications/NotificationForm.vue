<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { notificationApi, refApi, userApi } from '../../api/client.js'

const router = useRouter()

const form = ref({
  title: '',
  content: '',
  eventType: '',
  targetType: 'ALL_STUDENTS',
  targetId: ''
})

const errors = ref({})
const saving = ref(false)
const formError = ref('')

const eventTypes = [
  'SCHEDULE_CHANGE',
  'REVIEW_OUTCOME',
  'CHECK_IN_REMINDER',
  'RESULT_PUBLISHED',
  'GENERAL'
]

const targetTypeOptions = [
  { value: 'ALL_STUDENTS', label: 'All students' },
  { value: 'CLASS', label: 'Class' },
  { value: 'GRADE', label: 'Grade' },
  { value: 'INDIVIDUAL', label: 'Individual student' }
]

const targetOptions = ref([])
const targetLoading = ref(false)

const showTargetPicker = computed(() =>
  ['CLASS', 'GRADE', 'INDIVIDUAL'].includes(form.value.targetType)
)

const targetLabel = computed(() => {
  switch (form.value.targetType) {
    case 'CLASS': return 'Select Class'
    case 'GRADE': return 'Select Grade'
    case 'INDIVIDUAL': return 'Select Student'
    default: return 'Target'
  }
})

watch(() => form.value.targetType, async (type) => {
  form.value.targetId = ''
  targetOptions.value = []
  if (type === 'ALL_STUDENTS') return

  targetLoading.value = true
  try {
    if (type === 'CLASS') {
      const res = await refApi.classes()
      targetOptions.value = (res.data || []).map(c => ({ id: c.id, label: c.name }))
    } else if (type === 'GRADE') {
      const res = await refApi.grades()
      targetOptions.value = (res.data || []).map(g => ({ id: g.id || g.value, label: g.name || g.label }))
    } else if (type === 'INDIVIDUAL') {
      const res = await userApi.list({ role: 'STUDENT', limit: 100 })
      const users = res.data?.items || res.data || []
      targetOptions.value = users.map(u => ({ id: u.id, label: u.displayName || u.username }))
    }
  } catch { /* ignore */ }
  finally {
    targetLoading.value = false
  }
})

function validate() {
  const e = {}
  if (!form.value.title.trim()) e.title = 'Title is required.'
  if (!form.value.content.trim()) e.content = 'Content is required.'
  if (!form.value.eventType) e.eventType = 'Please select an event type.'
  if (showTargetPicker.value && !form.value.targetId) {
    e.targetId = 'Please select a target.'
  }
  errors.value = e
  return Object.keys(e).length === 0
}

function buildCreatePayload() {
  const t = form.value.targetType
  const targetIds =
    t === 'ALL_STUDENTS'
      ? []
      : form.value.targetId
        ? [Number(form.value.targetId)]
        : []
  return {
    title: form.value.title,
    content: form.value.content,
    eventType: form.value.eventType,
    targetType: t,
    targetIds
  }
}

async function handleSubmit() {
  if (!validate()) return
  saving.value = true
  formError.value = ''
  try {
    await notificationApi.create(buildCreatePayload())
    router.push('/notifications')
  } catch (e) {
    formError.value = e.response?.data?.message || 'Failed to create notification.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Create Notification</h1>
      <router-link to="/notifications" class="btn-secondary" style="text-decoration:none;">
        Cancel
      </router-link>
    </div>

    <form class="card" @submit.prevent="handleSubmit">
      <div class="form-group">
        <label>Title *</label>
        <input v-model="form.title" placeholder="Notification title" />
        <div v-if="errors.title" class="error-text">{{ errors.title }}</div>
      </div>

      <div class="form-group">
        <label>Content *</label>
        <textarea v-model="form.content" rows="5" placeholder="Notification content..."></textarea>
        <div v-if="errors.content" class="error-text">{{ errors.content }}</div>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>Event Type *</label>
          <select v-model="form.eventType">
            <option value="">Select event type...</option>
            <option v-for="t in eventTypes" :key="t" :value="t">{{ t.replace(/_/g, ' ') }}</option>
          </select>
          <div v-if="errors.eventType" class="error-text">{{ errors.eventType }}</div>
        </div>

        <div class="form-group">
          <label>Target Type</label>
          <select v-model="form.targetType">
            <option v-for="t in targetTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
        </div>
      </div>

      <div v-if="showTargetPicker" class="form-group">
        <label>{{ targetLabel }} *</label>
        <select v-model="form.targetId" :disabled="targetLoading">
          <option value="">
            {{ targetLoading ? 'Loading...' : 'Select...' }}
          </option>
          <option v-for="opt in targetOptions" :key="opt.id" :value="opt.id">
            {{ opt.label }}
          </option>
        </select>
        <div v-if="errors.targetId" class="error-text">{{ errors.targetId }}</div>
      </div>

      <div v-if="formError" class="error-text" style="margin-bottom:12px;">{{ formError }}</div>

      <div class="form-actions">
        <router-link to="/notifications" class="btn-secondary" style="text-decoration:none;">Cancel</router-link>
        <button type="submit" class="btn-primary" :disabled="saving">
          {{ saving ? 'Creating...' : 'Create Notification' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
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
