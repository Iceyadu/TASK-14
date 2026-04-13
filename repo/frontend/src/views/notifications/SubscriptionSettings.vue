<script setup>
import { ref, onMounted } from 'vue'
import { notificationApi } from '../../api/client.js'

const loading = ref(true)
const saving = ref(false)
const error = ref(null)
const saveSuccess = ref(false)

// Keys match backend `subscription_settings.event_type` / `NotificationEventType` enum values.
const eventTypes = [
  { key: 'SCHEDULE_CHANGE', label: 'Schedule changes' },
  { key: 'REVIEW_OUTCOME', label: 'Review outcomes' },
  { key: 'CHECK_IN_REMINDER', label: 'Check-in reminders' },
  { key: 'RESULT_PUBLISHED', label: 'Published results' },
  { key: 'GENERAL', label: 'General announcements' }
]

const subscriptions = ref({})
const dndStart = ref('')
const dndEnd = ref('')

async function fetchSubscriptions() {
  loading.value = true
  error.value = null
  try {
    const res = await notificationApi.getSubscriptions()
    const data = res.data
    subscriptions.value = data.subscriptions || {}
    dndStart.value = data.dndStartTime || ''
    dndEnd.value = data.dndEndTime || ''

    // initialize missing keys
    for (const et of eventTypes) {
      if (subscriptions.value[et.key] === undefined) {
        subscriptions.value[et.key] = true
      }
    }
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load subscription settings.'
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  saving.value = true
  saveSuccess.value = false
  error.value = null
  try {
    await notificationApi.updateSubscriptions({
      subscriptions: subscriptions.value,
      dndStartTime: dndStart.value || null,
      dndEndTime: dndEnd.value || null
    })
    saveSuccess.value = true
    setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to save settings.'
  } finally {
    saving.value = false
  }
}

onMounted(fetchSubscriptions)
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Notification Preferences</h1>
    </div>

    <div v-if="loading" class="card" style="text-align:center; padding:40px;">Loading...</div>

    <template v-else>
      <div class="card">
        <h3>Event Subscriptions</h3>
        <p style="color:var(--gray-500); font-size:13px; margin-bottom:16px;">
          Choose which notification types you want to receive.
        </p>

        <div class="toggle-list">
          <div v-for="et in eventTypes" :key="et.key" class="toggle-item">
            <label class="toggle-label">
              <span>{{ et.label }}</span>
              <label class="switch">
                <input type="checkbox" v-model="subscriptions[et.key]" />
                <span class="slider"></span>
              </label>
            </label>
          </div>
        </div>
      </div>

      <div class="card">
        <h3>Do Not Disturb Window</h3>
        <p style="color:var(--gray-500); font-size:13px; margin-bottom:16px;">
          Set a time window during which you will not receive notification alerts.
        </p>
        <div class="dnd-row">
          <div class="form-group">
            <label>Start Time</label>
            <input type="time" v-model="dndStart" />
          </div>
          <div class="form-group">
            <label>End Time</label>
            <input type="time" v-model="dndEnd" />
          </div>
        </div>
      </div>

      <div v-if="error" class="error-text" style="margin-bottom:12px;">{{ error }}</div>
      <div v-if="saveSuccess" class="success-msg">Settings saved successfully.</div>

      <button class="btn-primary" :disabled="saving" @click="saveSettings">
        {{ saving ? 'Saving...' : 'Save Preferences' }}
      </button>
    </template>
  </div>
</template>

<style scoped>
.toggle-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.toggle-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--gray-100);
}

.toggle-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  font-size: 14px;
}

.switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background: var(--gray-300);
  border-radius: 24px;
  transition: 0.3s;
}

.slider::before {
  content: '';
  position: absolute;
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background: white;
  border-radius: 50%;
  transition: 0.3s;
}

.switch input:checked + .slider {
  background: var(--primary);
}

.switch input:checked + .slider::before {
  transform: translateX(20px);
}

.dnd-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  max-width: 400px;
}

.success-msg {
  color: var(--success);
  font-size: 14px;
  margin-bottom: 12px;
  font-weight: 500;
}
</style>
