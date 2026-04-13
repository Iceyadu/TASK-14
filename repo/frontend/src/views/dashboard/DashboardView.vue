<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '../../stores/auth.js'
import { examSessionApi, notificationApi, complianceApi, jobApi } from '../../api/client.js'

const auth = useAuthStore()
const loading = ref(true)

const stats = ref({
  upcomingSessions: 0,
  pendingReviews: 0,
  activeJobs: 0,
  unreadNotifications: 0,
  nextExam: null,
  recentSessions: [],
  mySchedule: []
})

const isAdmin = computed(() => auth.role === 'ADMIN')
const isCoordinator = computed(() => auth.role === 'ACADEMIC_COORDINATOR')
const isTeacher = computed(() => auth.role === 'TEACHER')
const isStudent = computed(() => auth.role === 'STUDENT')

async function fetchDashboard() {
  loading.value = true
  try {
    if (isAdmin.value || isCoordinator.value) {
      const [sessions, reviews, jobs] = await Promise.allSettled([
        examSessionApi.list({ status: 'PUBLISHED', limit: 5 }),
        complianceApi.listReviews({ status: 'PENDING', limit: 5 }),
        jobApi.list({ status: 'RUNNING', limit: 5 })
      ])
      if (sessions.status === 'fulfilled') {
        stats.value.recentSessions = sessions.value.data?.items || sessions.value.data || []
        stats.value.upcomingSessions = stats.value.recentSessions.length
      }
      if (reviews.status === 'fulfilled') {
        const reviewData = reviews.value.data?.items || reviews.value.data || []
        stats.value.pendingReviews = reviewData.length
      }
      if (jobs.status === 'fulfilled') {
        const jobData = jobs.value.data?.items || jobs.value.data || []
        stats.value.activeJobs = jobData.length
      }
    }

    if (isTeacher.value) {
      try {
        const res = await examSessionApi.list({ limit: 10 })
        stats.value.recentSessions = res.data?.items || res.data || []
        stats.value.upcomingSessions = stats.value.recentSessions.length
      } catch { /* ignore */ }
    }

    if (isStudent.value) {
      const [schedule, inbox] = await Promise.allSettled([
        examSessionApi.getStudentSchedule(),
        notificationApi.getInbox({ unreadOnly: true })
      ])
      if (schedule.status === 'fulfilled') {
        stats.value.mySchedule = schedule.value.data || []
        stats.value.nextExam = stats.value.mySchedule[0] || null
      }
      if (inbox.status === 'fulfilled') {
        const inboxData = inbox.value.data?.items || inbox.value.data || []
        stats.value.unreadNotifications = inboxData.length
      }
    }
  } catch {
    // dashboard is best-effort
  } finally {
    loading.value = false
  }
}

onMounted(fetchDashboard)
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Dashboard</h1>
      <span class="welcome">Welcome, {{ auth.user?.displayName || auth.user?.username || 'User' }}</span>
    </div>

    <div v-if="loading" class="card" style="text-align:center; padding:40px;">Loading dashboard...</div>

    <template v-else>
      <!-- Admin / Coordinator Dashboard -->
      <template v-if="isAdmin || isCoordinator">
        <div class="stat-grid">
          <div class="stat-card">
            <div class="stat-number">{{ stats.upcomingSessions }}</div>
            <div class="stat-label">Upcoming Sessions</div>
          </div>
          <div class="stat-card">
            <div class="stat-number">{{ stats.pendingReviews }}</div>
            <div class="stat-label">Pending Reviews</div>
          </div>
          <div class="stat-card">
            <div class="stat-number">{{ stats.activeJobs }}</div>
            <div class="stat-label">Active Jobs</div>
          </div>
        </div>

        <div class="card">
          <h3>Recent Exam Sessions</h3>
          <table v-if="stats.recentSessions.length">
            <thead><tr><th>Name</th><th>Status</th><th>Date</th></tr></thead>
            <tbody>
              <tr v-for="s in stats.recentSessions" :key="s.id">
                <td>
                  <router-link :to="'/exam-sessions/' + s.id">{{ s.name }}</router-link>
                </td>
                <td>{{ s.status }}</td>
                <td>{{ s.examDate || '-' }}</td>
              </tr>
            </tbody>
          </table>
          <p v-else style="color:var(--gray-500); padding:12px 0;">No recent sessions.</p>
        </div>
      </template>

      <!-- Teacher Dashboard -->
      <template v-if="isTeacher">
        <div class="stat-grid">
          <div class="stat-card">
            <div class="stat-number">{{ stats.upcomingSessions }}</div>
            <div class="stat-label">Your Upcoming Sessions</div>
          </div>
        </div>

        <div class="card">
          <h3>Your Sessions</h3>
          <table v-if="stats.recentSessions.length">
            <thead><tr><th>Name</th><th>Status</th><th>Date</th></tr></thead>
            <tbody>
              <tr v-for="s in stats.recentSessions" :key="s.id">
                <td>
                  <router-link :to="'/exam-sessions/' + s.id">{{ s.name }}</router-link>
                </td>
                <td>{{ s.status }}</td>
                <td>{{ s.examDate || '-' }}</td>
              </tr>
            </tbody>
          </table>
          <p v-else style="color:var(--gray-500); padding:12px 0;">No upcoming sessions.</p>
        </div>
      </template>

      <!-- Student Dashboard -->
      <template v-if="isStudent">
        <div class="stat-grid">
          <div class="stat-card">
            <div class="stat-number">{{ stats.mySchedule.length }}</div>
            <div class="stat-label">Upcoming Exams</div>
          </div>
          <div class="stat-card">
            <div class="stat-number">{{ stats.unreadNotifications }}</div>
            <div class="stat-label">Unread Messages</div>
          </div>
        </div>

        <div v-if="stats.nextExam" class="card next-exam">
          <h3>Next Exam</h3>
          <p class="next-exam-name">{{ stats.nextExam.name }}</p>
          <p>{{ stats.nextExam.examDate }} at {{ stats.nextExam.startTime }} - {{ stats.nextExam.endTime }}</p>
          <p v-if="stats.nextExam.roomName">Room: {{ stats.nextExam.roomName }}</p>
        </div>

        <div class="card">
          <h3>My Exam Schedule</h3>
          <table v-if="stats.mySchedule.length">
            <thead><tr><th>Exam</th><th>Date</th><th>Time</th><th>Room</th></tr></thead>
            <tbody>
              <tr v-for="exam in stats.mySchedule" :key="exam.id">
                <td>{{ exam.name }}</td>
                <td>{{ exam.examDate }}</td>
                <td>{{ exam.startTime }} - {{ exam.endTime }}</td>
                <td>{{ exam.roomName || '-' }}</td>
              </tr>
            </tbody>
          </table>
          <p v-else style="color:var(--gray-500); padding:12px 0;">No scheduled exams.</p>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.welcome {
  font-size: 14px;
  color: var(--gray-500);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  text-align: center;
}

.stat-number {
  font-size: 36px;
  font-weight: 700;
  color: var(--primary);
}

.stat-label {
  font-size: 14px;
  color: var(--gray-500);
  margin-top: 4px;
}

.card h3 {
  margin-bottom: 12px;
  font-size: 16px;
}

.next-exam {
  border-left: 4px solid var(--primary);
}

.next-exam-name {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 4px;
}

a {
  color: var(--primary);
  text-decoration: none;
}
a:hover { text-decoration: underline; }
</style>
