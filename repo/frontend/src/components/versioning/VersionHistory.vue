<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { versionApi } from '../../api/client.js'
import ConfirmDialog from '../shared/ConfirmDialog.vue'

const route = useRoute()
const entityType = ref(route.params.entityType)
const entityId = ref(route.params.entityId)

const versions = ref([])
const loading = ref(false)
const error = ref(null)

const selectedFrom = ref(null)
const selectedTo = ref(null)
const comparison = ref(null)
const comparing = ref(false)

const showRestoreDialog = ref(false)
const restoreVersion = ref(null)
const restoring = ref(false)

async function fetchVersions() {
  loading.value = true
  error.value = null
  try {
    const res = await versionApi.list(entityType.value, entityId.value)
    versions.value = (res.data || []).map((row) => ({
      ...row,
      version: row.versionNumber ?? row.version
    }))
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load version history.'
  } finally {
    loading.value = false
  }
}

async function compareVersions() {
  if (!selectedFrom.value || !selectedTo.value) return
  comparing.value = true
  comparison.value = null
  try {
    const res = await versionApi.compare(
      entityType.value, entityId.value,
      selectedFrom.value, selectedTo.value
    )
    comparison.value = res.data
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to compare versions.'
  } finally {
    comparing.value = false
  }
}

function promptRestore(version) {
  restoreVersion.value = version
  showRestoreDialog.value = true
}

async function confirmRestore() {
  restoring.value = true
  try {
    await versionApi.restore(
      entityType.value,
      Number(entityId.value),
      restoreVersion.value,
      null
    )
    showRestoreDialog.value = false
    await fetchVersions()
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to restore version.'
  } finally {
    restoring.value = false
  }
}

onMounted(fetchVersions)
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Version History</h1>
      <span class="entity-info">{{ entityType }} / {{ entityId }}</span>
    </div>

    <div v-if="loading" class="card" style="text-align:center; padding:40px;">Loading...</div>
    <div v-else-if="error" class="card" style="color:var(--danger);">{{ error }}</div>

    <template v-else>
      <div class="card">
        <h3>Versions</h3>
        <table>
          <thead>
            <tr>
              <th>From</th>
              <th>To</th>
              <th>Version</th>
              <th>Changed By</th>
              <th>Changed At</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="v in versions" :key="v.version">
              <td><input type="radio" :value="v.version" v-model="selectedFrom" name="from" /></td>
              <td><input type="radio" :value="v.version" v-model="selectedTo" name="to" /></td>
              <td>v{{ v.version }}</td>
              <td>{{ v.changedBy || '-' }}</td>
              <td>{{ v.changedAt ? new Date(v.changedAt).toLocaleString() : '-' }}</td>
              <td>
                <button class="btn-secondary btn-sm" @click="promptRestore(v.version)">Restore</button>
              </td>
            </tr>
          </tbody>
        </table>

        <div style="margin-top:12px;">
          <button
            class="btn-primary"
            :disabled="!selectedFrom || !selectedTo || comparing"
            @click="compareVersions"
          >
            {{ comparing ? 'Comparing...' : 'Compare Selected' }}
          </button>
        </div>
      </div>

      <div v-if="comparison" class="card">
        <h3>Comparison: v{{ selectedFrom }} vs v{{ selectedTo }}</h3>
        <div class="diff-container">
          <div class="diff-side">
            <h4>v{{ selectedFrom }}</h4>
            <pre class="diff-content">{{ JSON.stringify(comparison.from, null, 2) }}</pre>
          </div>
          <div class="diff-side">
            <h4>v{{ selectedTo }}</h4>
            <pre class="diff-content">{{ JSON.stringify(comparison.to, null, 2) }}</pre>
          </div>
        </div>
        <div v-if="comparison.changes" class="changes-list">
          <h4>Changes</h4>
          <ul>
            <li v-for="(change, i) in comparison.changes" :key="i">
              <strong>{{ change.field }}:</strong>
              <span class="old-val">{{ change.oldValue }}</span> &rarr;
              <span class="new-val">{{ change.newValue }}</span>
            </li>
          </ul>
        </div>
      </div>
    </template>

    <ConfirmDialog
      :visible="showRestoreDialog"
      title="Restore Version"
      :message="'Restore to version ' + restoreVersion + '? This will create a new version with the restored content.'"
      confirm-text="Restore"
      :danger="true"
      @confirm="confirmRestore"
      @cancel="showRestoreDialog = false"
    />
  </div>
</template>

<style scoped>
.entity-info {
  font-size: 14px;
  color: var(--gray-500);
}

.diff-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 12px;
}

.diff-side h4 {
  font-size: 13px;
  margin-bottom: 8px;
  color: var(--gray-600);
}

.diff-content {
  background: var(--gray-50);
  padding: 12px;
  border-radius: var(--radius);
  font-size: 12px;
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
}

.changes-list {
  margin-top: 16px;
}

.changes-list h4 {
  margin-bottom: 8px;
}

.changes-list li {
  font-size: 13px;
  margin-bottom: 4px;
  list-style: none;
  padding: 4px 0;
}

.old-val { color: var(--danger); text-decoration: line-through; }
.new-val { color: var(--success); }
</style>
