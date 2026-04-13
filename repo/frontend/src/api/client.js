import axios from 'axios'
import CryptoJS from 'crypto-js'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  headers: {
    'Content-Type': 'application/json'
  }
})

function generateNonce() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

function computeSignature(signingKey, method, path, timestamp, nonce, body) {
  const bodyHash = CryptoJS.SHA256(body || '').toString()
  const message = [method.toUpperCase(), path, timestamp, nonce, bodyHash].join('\n')
  return CryptoJS.HmacSHA256(message, signingKey).toString()
}

apiClient.interceptors.request.use((config) => {
  const stored = localStorage.getItem('auth_session')
  if (stored) {
    try {
      const session = JSON.parse(stored)
      if (session.sessionToken) {
        config.headers['Authorization'] = `Bearer ${session.sessionToken}`
      }
      if (session.signingKey) {
        const timestamp = Math.floor(Date.now() / 1000).toString()
        const nonce = generateNonce()
        const url = new URL(config.url, config.baseURL || window.location.origin)
        const path = url.pathname + (url.search || '')
        const body = config.data ? (typeof config.data === 'string' ? config.data : JSON.stringify(config.data)) : ''
        const signature = computeSignature(session.signingKey, config.method, path, timestamp, nonce, body)

        config.headers['X-Timestamp'] = timestamp
        config.headers['X-Nonce'] = nonce
        config.headers['X-Signature'] = signature
      }
    } catch {
      // skip signing if parse fails
    }
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response
    }
    const payload = response.data
    if (payload && typeof payload === 'object' && payload.status === 'success') {
      const adapted = { ...response, data: payload.data }
      if (payload.pagination) {
        adapted.pagination = payload.pagination
      }
      return adapted
    }
    return response
  },
  (error) => {
    if (error.response) {
      if (error.response.status === 401) {
        localStorage.removeItem('auth_session')
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
      }
      if (error.response.status === 429) {
        const retryAfter = error.response.headers['retry-after'] || 'a few'
        error.rateLimitMessage = `Rate limit exceeded. Please try again in ${retryAfter} seconds.`
      }
    }
    return Promise.reject(error)
  }
)

export const authApi = {
  login: (data) => apiClient.post('/auth/login', data),
  logout: () => apiClient.post('/auth/logout'),
  getSession: () => apiClient.get('/auth/session'),
  registerDevice: (data) => apiClient.post('/auth/devices', data),
  listDevices: () => apiClient.get('/auth/devices'),
  removeDevice: (id) => apiClient.delete(`/auth/devices/${id}`),
  terminateSession: (userId) => apiClient.post(`/auth/sessions/${userId}/terminate`),
  unlockAccount: (userId) => apiClient.post(`/auth/users/${userId}/unlock`)
}

export const userApi = {
  list: (params) => apiClient.get('/users', { params }),
  create: (data) => apiClient.post('/users', data),
  get: (id) => apiClient.get(`/users/${id}`),
  update: (id, data) => apiClient.put(`/users/${id}`, data),
  updateScope: (id, data) => apiClient.put(`/users/${id}/scope`, data),
  toggleConcurrent: (id, data) => apiClient.put(`/users/${id}/concurrent-sessions`, data)
}

export const rosterApi = {
  list: (params) => apiClient.get('/rosters', { params }),
  create: (data) => apiClient.post('/rosters', data),
  get: (id) => apiClient.get(`/rosters/${id}`),
  update: (id, data) => apiClient.put(`/rosters/${id}`, data),
  delete: (id) => apiClient.delete(`/rosters/${id}`),
  uploadImport: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return apiClient.post('/rosters/import/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  commitImport: (jobId, key) =>
    apiClient.post(`/rosters/import/${jobId}/commit`, null, {
      params: key ? { idempotencyKey: key } : {}
    }),
  getImportErrors: (jobId) => apiClient.get(`/rosters/import/${jobId}/errors`),
  rollbackImport: (jobId) => apiClient.post(`/rosters/import/${jobId}/rollback`),
  export: (params) => apiClient.get('/rosters/export', { params, responseType: 'blob' })
}

export const examSessionApi = {
  list: (params) => apiClient.get('/exam-sessions', { params }),
  create: (data) => apiClient.post('/exam-sessions', data),
  get: (id) => apiClient.get(`/exam-sessions/${id}`),
  update: (id, data) => apiClient.put(`/exam-sessions/${id}`, data),
  submitReview: (id) => apiClient.post(`/exam-sessions/${id}/submit-review`),
  publish: (id, key) =>
    apiClient.post(`/exam-sessions/${id}/publish`, null, {
      headers: key ? { 'Idempotency-Key': key } : {}
    }),
  unpublish: (id) => apiClient.post(`/exam-sessions/${id}/unpublish`),
  archive: (id) => apiClient.post(`/exam-sessions/${id}/archive`),
  getStudentSchedule: () => apiClient.get('/exam-sessions/student/schedule')
}

export const roomApi = {
  listCampuses: (params) => apiClient.get('/campuses', { params }),
  createCampus: (data) => apiClient.post('/campuses', data),
  listRooms: (campusId) => apiClient.get('/rooms', { params: { campusId } }),
  createRoom: (campusId, data) => apiClient.post('/rooms', { ...data, campusId }),
  updateRoom: (id, data) => apiClient.put(`/rooms/${id}`, data)
}

export const proctorApi = {
  list: (params) => apiClient.get('/proctor-assignments', { params }),
  create: (data) => apiClient.post('/proctor-assignments', data),
  remove: (id) => apiClient.delete(`/proctor-assignments/${id}`)
}

export const notificationApi = {
  list: (params) => apiClient.get('/notifications', { params }),
  create: (data) => apiClient.post('/notifications', data),
  submitReview: (id) => apiClient.post(`/notifications/${id}/submit-review`),
  publish: (id, key) =>
    apiClient.post(`/notifications/${id}/publish`, null, {
      params: key ? { idempotencyKey: key } : {}
    }),
  cancel: (id) => apiClient.post(`/notifications/${id}/cancel`),
  getInbox: (params) => apiClient.get('/notifications/inbox', { params }),
  markRead: (id) => apiClient.post(`/notifications/inbox/${id}/read`),
  getDeliveryStatus: (notificationId) =>
    apiClient.get('/notifications/delivery-status', {
      params: { notificationId: typeof notificationId === 'object' ? notificationId?.notificationId : notificationId }
    }),
  getSubscriptions: () => apiClient.get('/notifications/subscriptions'),
  updateSubscriptions: (data) => apiClient.put('/notifications/subscriptions', data)
}

export const complianceApi = {
  listReviews: (params) => apiClient.get('/compliance/reviews', { params }),
  getReview: (id) => apiClient.get(`/compliance/reviews/${id}`),
  approve: (id, data) => apiClient.post(`/compliance/reviews/${id}/approve`, data),
  reject: (id, data) => apiClient.post(`/compliance/reviews/${id}/reject`, data)
}

export const versionApi = {
  list: (entityType, entityId) => apiClient.get(`/versions/${entityType}/${entityId}`),
  get: (entityType, entityId, version) => apiClient.get(`/versions/${entityType}/${entityId}/${version}`),
  compare: (entityType, entityId, from, to) =>
    apiClient.get(`/versions/${entityType}/${entityId}/compare`, { params: { from, to } }),
  restore: (entityType, entityId, targetVersion, idempotencyKey) =>
    apiClient.post(`/versions/${entityType}/${entityId}/restore`, null, {
      params: {
        targetVersion,
        ...(idempotencyKey ? { idempotencyKey } : {})
      }
    })
}

export const jobApi = {
  list: (params) => apiClient.get('/jobs', { params }),
  get: (id) => apiClient.get(`/jobs/${id}`),
  rerun: (id, key) =>
    apiClient.post(`/jobs/${id}/rerun`, null, {
      params: key ? { idempotencyKey: key } : {}
    }),
  cancel: (id) => apiClient.post(`/jobs/${id}/cancel`)
}

export const antiCheatApi = {
  listFlags: (params) => apiClient.get('/anticheat/flags', { params }),
  reviewFlag: (id, data) => apiClient.post(`/anticheat/flags/${id}/review`, data)
}

export const auditApi = {
  list: (params) => apiClient.get('/audit', { params })
}

export const refApi = {
  terms: () => apiClient.get('/terms'),
  grades: () => apiClient.get('/grades'),
  classes: (params) => apiClient.get('/classes', { params }),
  courses: (params) => apiClient.get('/courses', { params })
}

export default apiClient
