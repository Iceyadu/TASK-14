<script setup>
const props = defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: null },
  emptyMessage: { type: String, default: 'No data available.' },
  page: { type: Number, default: 1 },
  totalPages: { type: Number, default: 1 },
  totalItems: { type: Number, default: 0 }
})

const emit = defineEmits(['page-change', 'retry'])
</script>

<template>
  <div class="data-table-wrapper">
    <div v-if="loading" class="state-box">
      <div class="spinner"></div>
      <p>Loading...</p>
    </div>

    <div v-else-if="error" class="state-box state-error">
      <p>{{ error }}</p>
      <button class="btn-primary" @click="emit('retry')">Retry</button>
    </div>

    <div v-else-if="rows.length === 0" class="state-box state-empty">
      <p>{{ emptyMessage }}</p>
    </div>

    <template v-else>
      <table>
        <thead>
          <tr>
            <th v-for="col in columns" :key="col.key" :style="col.width ? { width: col.width } : {}">
              {{ col.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, idx) in rows" :key="row.id || idx">
            <td v-for="col in columns" :key="col.key">
              <slot :name="'cell-' + col.key" :row="row" :value="row[col.key]">
                {{ row[col.key] }}
              </slot>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" v-if="totalPages > 1">
        <button
          class="btn-secondary btn-sm"
          :disabled="page <= 1"
          @click="emit('page-change', page - 1)"
        >
          Previous
        </button>
        <span class="page-info">
          Page {{ page }} of {{ totalPages }}
          <template v-if="totalItems"> ({{ totalItems }} items)</template>
        </span>
        <button
          class="btn-secondary btn-sm"
          :disabled="page >= totalPages"
          @click="emit('page-change', page + 1)"
        >
          Next
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.data-table-wrapper {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  overflow: hidden;
}

.state-box {
  padding: 48px 24px;
  text-align: center;
  color: var(--gray-500);
}

.state-error {
  color: var(--danger);
}

.state-error button {
  margin-top: 12px;
}

.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--gray-200);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 12px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 12px 16px;
  border-top: 1px solid var(--gray-200);
}

.page-info {
  font-size: 13px;
  color: var(--gray-500);
}
</style>
