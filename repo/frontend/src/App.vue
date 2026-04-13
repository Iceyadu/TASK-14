<script setup>
import { computed } from 'vue'
import { useAuthStore } from './stores/auth.js'
import NavigationSidebar from './components/shared/NavigationSidebar.vue'

const authStore = useAuthStore()
authStore.loadFromStorage()

const showSidebar = computed(() => authStore.isAuthenticated)
</script>

<template>
  <div class="app-layout">
    <NavigationSidebar v-if="showSidebar" />
    <main :class="['app-main', { 'with-sidebar': showSidebar }]">
      <router-view />
    </main>
  </div>
</template>

<style>
:root {
  --sidebar-width: 250px;
  --primary: #2563eb;
  --primary-dark: #1d4ed8;
  --danger: #dc2626;
  --success: #16a34a;
  --warning: #d97706;
  --info: #0891b2;
  --gray-50: #f9fafb;
  --gray-100: #f3f4f6;
  --gray-200: #e5e7eb;
  --gray-300: #d1d5db;
  --gray-500: #6b7280;
  --gray-600: #4b5563;
  --gray-700: #374151;
  --gray-800: #1f2937;
  --gray-900: #111827;
  --radius: 6px;
}

body {
  background: var(--gray-50);
  color: var(--gray-900);
  line-height: 1.5;
}

.app-layout {
  display: flex;
  min-height: 100vh;
}

.app-main {
  flex: 1;
  padding: 24px;
  max-width: 100%;
}

.app-main.with-sidebar {
  margin-left: var(--sidebar-width);
}

button {
  cursor: pointer;
  border: none;
  border-radius: var(--radius);
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.15s;
}

.btn-primary {
  background: var(--primary);
  color: white;
}
.btn-primary:hover { background: var(--primary-dark); }
.btn-primary:disabled { background: var(--gray-300); cursor: not-allowed; }

.btn-danger {
  background: var(--danger);
  color: white;
}
.btn-danger:hover { background: #b91c1c; }

.btn-success {
  background: var(--success);
  color: white;
}
.btn-success:hover { background: #15803d; }

.btn-secondary {
  background: var(--gray-200);
  color: var(--gray-700);
}
.btn-secondary:hover { background: var(--gray-300); }

.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
}

input, select, textarea {
  border: 1px solid var(--gray-300);
  border-radius: var(--radius);
  padding: 8px 12px;
  font-size: 14px;
  width: 100%;
  outline: none;
  transition: border-color 0.15s;
}
input:focus, select:focus, textarea:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.form-group {
  margin-bottom: 16px;
}
.form-group label {
  display: block;
  margin-bottom: 4px;
  font-weight: 500;
  font-size: 14px;
  color: var(--gray-700);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.page-header h1 {
  font-size: 24px;
  font-weight: 700;
}

.card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  padding: 20px;
  margin-bottom: 16px;
}

.error-text {
  color: var(--danger);
  font-size: 13px;
  margin-top: 4px;
}

table {
  width: 100%;
  border-collapse: collapse;
}
th, td {
  text-align: left;
  padding: 10px 12px;
  border-bottom: 1px solid var(--gray-200);
}
th {
  background: var(--gray-50);
  font-weight: 600;
  font-size: 13px;
  color: var(--gray-600);
  text-transform: uppercase;
  letter-spacing: 0.03em;
}
tr:hover {
  background: var(--gray-50);
}
</style>
