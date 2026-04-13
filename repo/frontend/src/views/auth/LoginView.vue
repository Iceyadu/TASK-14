<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../../stores/auth.js'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const errorMsg = ref('')
const submitting = ref(false)

function generateFingerprint() {
  const nav = window.navigator
  const parts = [
    nav.userAgent,
    nav.language,
    nav.platform,
    screen.width + 'x' + screen.height,
    new Date().getTimezoneOffset()
  ]
  let hash = 0
  const str = parts.join('|')
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash
  }
  return Math.abs(hash).toString(36)
}

async function handleLogin() {
  errorMsg.value = ''
  if (!username.value || !password.value) {
    errorMsg.value = 'Please enter both username and password.'
    return
  }

  submitting.value = true
  try {
    await auth.login({
      username: username.value,
      password: password.value,
      deviceFingerprint: generateFingerprint()
    })
    const redirect = route.query.redirect || '/dashboard'
    router.push(redirect)
  } catch (e) {
    const status = e.response?.status
    const data = e.response?.data
    if (status === 423) {
      errorMsg.value = 'Your account has been locked due to too many failed attempts. Please contact an administrator.'
    } else if (status === 409) {
      errorMsg.value = 'A concurrent session is already active. Please terminate it first or contact an administrator.'
    } else if (status === 401) {
      errorMsg.value = data?.message || 'Invalid username or password.'
    } else {
      errorMsg.value = data?.message || 'An unexpected error occurred. Please try again.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h1>Exam Scheduling System</h1>
      <p class="login-subtitle">Sign in to your account</p>

      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label for="username">Username</label>
          <input
            id="username"
            v-model="username"
            type="text"
            autocomplete="username"
            placeholder="Enter your username"
            :disabled="submitting"
          />
        </div>

        <div class="form-group">
          <label for="password">Password</label>
          <input
            id="password"
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="Enter your password"
            :disabled="submitting"
          />
        </div>

        <div v-if="errorMsg" class="error-alert">
          {{ errorMsg }}
        </div>

        <button type="submit" class="btn-primary login-btn" :disabled="submitting">
          {{ submitting ? 'Signing in...' : 'Sign In' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #1e3a5f 0%, #2563eb 100%);
}

.login-card {
  background: white;
  padding: 40px;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.2);
  width: 100%;
  max-width: 400px;
}

.login-card h1 {
  font-size: 22px;
  text-align: center;
  margin-bottom: 4px;
}

.login-subtitle {
  text-align: center;
  color: var(--gray-500);
  font-size: 14px;
  margin-bottom: 28px;
}

.error-alert {
  background: #fef2f2;
  color: var(--danger);
  border: 1px solid #fca5a5;
  border-radius: var(--radius);
  padding: 10px 14px;
  font-size: 13px;
  margin-bottom: 16px;
}

.login-btn {
  width: 100%;
  padding: 12px;
  font-size: 15px;
}
</style>
