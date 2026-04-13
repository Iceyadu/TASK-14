<script setup>
import { computed } from 'vue'

const props = defineProps({
  columns: { type: Array, default: () => [] },
  validRows: { type: Array, default: () => [] },
  invalidRows: { type: Array, default: () => [] }
})

const totalRows = computed(() => props.validRows.length + props.invalidRows.length)

function getFieldError(row, field) {
  if (!row.errors) return null
  const err = row.errors.find(e => e.field === field)
  return err ? err.reason : null
}
</script>

<template>
  <div class="import-preview">
    <div class="preview-summary">
      <span class="summary-total">{{ totalRows }} total rows</span>
      <span class="summary-valid">{{ validRows.length }} valid</span>
      <span class="summary-invalid">{{ invalidRows.length }} invalid</span>
    </div>

    <div v-if="invalidRows.length > 0" class="preview-section">
      <h4 class="section-title section-invalid">Invalid Rows ({{ invalidRows.length }})</h4>
      <div class="table-scroll">
        <table class="preview-table invalid-table">
          <thead>
            <tr>
              <th>Row #</th>
              <th v-for="col in columns" :key="col">{{ col }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in invalidRows" :key="row.rowNumber">
              <td>{{ row.rowNumber }}</td>
              <td v-for="col in columns" :key="col" :class="{ 'cell-error': getFieldError(row, col) }">
                <span>{{ row.data?.[col] ?? '' }}</span>
                <span v-if="getFieldError(row, col)" class="error-badge">
                  {{ getFieldError(row, col) }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="validRows.length > 0" class="preview-section">
      <h4 class="section-title section-valid">Valid Rows ({{ validRows.length }})</h4>
      <div class="table-scroll">
        <table class="preview-table valid-table">
          <thead>
            <tr>
              <th>Row #</th>
              <th v-for="col in columns" :key="col">{{ col }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in validRows" :key="row.rowNumber">
              <td>{{ row.rowNumber }}</td>
              <td v-for="col in columns" :key="col">{{ row.data?.[col] ?? '' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.import-preview {
  margin-top: 16px;
}

.preview-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  font-size: 14px;
  font-weight: 600;
}
.summary-total { color: var(--gray-700); }
.summary-valid { color: var(--success); }
.summary-invalid { color: var(--danger); }

.preview-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 14px;
  margin-bottom: 8px;
  padding: 8px 12px;
  border-radius: var(--radius);
}
.section-invalid { background: #fef2f2; color: var(--danger); border-left: 4px solid var(--danger); }
.section-valid { background: #f0fdf4; color: var(--success); border-left: 4px solid var(--success); }

.table-scroll {
  overflow-x: auto;
}

.preview-table {
  font-size: 13px;
}

.invalid-table {
  border: 2px solid #fca5a5;
  border-radius: var(--radius);
}

.valid-table {
  border: 2px solid #86efac;
  border-radius: var(--radius);
}

.cell-error {
  background: #fef2f2;
  position: relative;
}

.error-badge {
  display: block;
  font-size: 11px;
  color: var(--danger);
  font-weight: 600;
  margin-top: 2px;
}
</style>
