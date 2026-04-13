<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth.js'

const router = useRouter()
const auth = useAuthStore()

const menuItems = computed(() => {
  const items = []

  items.push({ label: 'Dashboard', path: '/dashboard', icon: 'H' })

  if (auth.hasPermission('USER_MANAGE')) {
    items.push({ label: 'Users', path: '/users', icon: 'U' })
  }

  if (auth.hasPermission('ROSTER_VIEW')) {
    items.push({ label: 'Rosters', path: '/rosters', icon: 'R' })
  }

  if (auth.hasPermission('ROSTER_IMPORT')) {
    items.push({ label: 'Import Rosters', path: '/rosters/import', icon: 'I' })
  }

  if (auth.hasPermission('SESSION_VIEW')) {
    items.push({ label: 'Exam Sessions', path: '/exam-sessions', icon: 'E' })
  }

  if (auth.hasPermission('ROOM_MANAGE')) {
    items.push({ label: 'Campuses & Rooms', path: '/campuses', icon: 'C' })
  }

  if (auth.hasPermission('PROCTOR_ASSIGN')) {
    items.push({ label: 'Proctor Assignments', path: '/proctor-assignments', icon: 'P' })
  }

  if (auth.hasPermission('NOTIFICATION_CREATE')) {
    items.push({ label: 'Notifications', path: '/notifications', icon: 'N' })
  }

  if (auth.role === 'STUDENT') {
    items.push({ label: 'Inbox', path: '/notifications/inbox', icon: 'B' })
    items.push({ label: 'Subscriptions', path: '/subscriptions', icon: 'S' })
  }

  if (auth.hasPermission('COMPLIANCE_REVIEW')) {
    items.push({ label: 'Compliance Reviews', path: '/compliance/reviews', icon: 'V' })
  }

  if (auth.hasPermission('JOB_MONITOR')) {
    items.push({ label: 'Job Monitor', path: '/jobs', icon: 'J' })
  }

  if (auth.hasPermission('ANTICHEAT_REVIEW')) {
    items.push({ label: 'Anti-Cheat', path: '/anticheat', icon: 'A' })
  }

  if (auth.hasPermission('AUDIT_VIEW')) {
    items.push({ label: 'Audit Log', path: '/audit', icon: 'L' })
  }

  return items
})

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-header">
      <h2>Exam System</h2>
      <div class="user-info" v-if="auth.user">
        <span class="user-name">{{ auth.user.displayName || auth.user.username }}</span>
        <span class="user-role">{{ auth.role }}</span>
      </div>
    </div>

    <nav class="sidebar-nav">
      <router-link
        v-for="item in menuItems"
        :key="item.path"
        :to="item.path"
        class="nav-item"
        active-class="nav-item--active"
      >
        <span class="nav-icon">{{ item.icon }}</span>
        <span class="nav-label">{{ item.label }}</span>
      </router-link>
    </nav>

    <div class="sidebar-footer">
      <button class="btn-logout" @click="handleLogout">Log Out</button>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: var(--sidebar-width);
  background: var(--gray-900);
  color: white;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  z-index: 100;
}

.sidebar-header {
  padding: 20px 16px;
  border-bottom: 1px solid var(--gray-700);
}

.sidebar-header h2 {
  font-size: 18px;
  margin-bottom: 8px;
}

.user-info {
  display: flex;
  flex-direction: column;
  font-size: 13px;
}

.user-name {
  color: var(--gray-200);
}

.user-role {
  color: var(--gray-500);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.sidebar-nav {
  flex: 1;
  padding: 8px 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  color: var(--gray-300);
  text-decoration: none;
  font-size: 14px;
  transition: background 0.15s, color 0.15s;
}

.nav-item:hover {
  background: var(--gray-800);
  color: white;
}

.nav-item--active {
  background: var(--primary);
  color: white;
}

.nav-icon {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255,255,255,0.1);
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid var(--gray-700);
}

.btn-logout {
  width: 100%;
  background: transparent;
  border: 1px solid var(--gray-600);
  color: var(--gray-300);
  padding: 8px;
  border-radius: var(--radius);
  font-size: 14px;
}
.btn-logout:hover {
  background: var(--gray-800);
  color: white;
}
</style>
