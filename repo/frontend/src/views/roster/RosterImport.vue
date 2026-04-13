<script setup>
import { ref, computed } from 'vue'
import { rosterApi } from '../../api/client.js'
import ImportPreview from '../../components/import/ImportPreview.vue'

const step = ref(1)
const file = ref(null)
const uploading = ref(false)
const uploadError = ref('')
const dragging = ref(false)

const jobId = ref(null)
const previewData = ref(null)
const previewColumns = ref([])

const committing = ref(false)
const commitError = ref('')
const commitSuccess = ref(false)

const validRows = computed(() => previewData.value?.validRows || [])
const invalidRows = computed(() => previewData.value?.invalidRows || [])
const canCommit = computed(() => validRows.value.length > 0)

function handleDragOver(e) {
  e.preventDefault()
  dragging.value = true
}

function handleDragLeave() {
  dragging.value = false
}

function handleDrop(e) {
  e.preventDefault()
  dragging.value = false
  const files = e.dataTransfer.files
  if (files.length > 0) {
    file.value = files[0]
  }
}

function handleFileSelect(e) {
  file.value = e.target.files[0]
}

async function uploadFile() {
  if (!file.value) {
    uploadError.value = 'Please select a file.'
    return
  }

  const ext = file.value.name.split('.').pop().toLowerCase()
  if (!['csv', 'xlsx', 'xls'].includes(ext)) {
    uploadError.value = 'Only CSV and XLSX files are supported.'
    return
  }

  uploading.value = true
  uploadError.value = ''
  try {
    const res = await rosterApi.uploadImport(file.value)
    jobId.value = res.data.jobId
    previewData.value = res.data
    previewColumns.value = res.data.columns || ['firstName', 'lastName', 'studentId', 'grade', 'className']
    step.value = 2
  } catch (e) {
    uploadError.value = e.response?.data?.message || 'Upload failed. Please check the file format.'
  } finally {
    uploading.value = false
  }
}

async function commitImport() {
  committing.value = true
  commitError.value = ''
  try {
    const key = crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36)
    await rosterApi.commitImport(jobId.value, key)
    commitSuccess.value = true
  } catch (e) {
    commitError.value = e.response?.data?.message || 'Failed to commit import.'
  } finally {
    committing.value = false
  }
}

async function downloadErrors() {
  try {
    const res = await rosterApi.getImportErrors(jobId.value)
    const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'import-errors.json'
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    alert('Failed to download errors.')
  }
}

function startOver() {
  step.value = 1
  file.value = null
  previewData.value = null
  jobId.value = null
  commitSuccess.value = false
  commitError.value = ''
  uploadError.value = ''
}
</script>

<template>
  <div>
    <div class="page-header">
      <h1>Import Rosters</h1>
      <router-link to="/rosters" class="btn-secondary" style="text-decoration:none;">
        Back to Rosters
      </router-link>
    </div>

    <div class="steps">
      <div :class="['step', { active: step === 1, done: step > 1 }]">1. Upload File</div>
      <div :class="['step', { active: step === 2 }]">2. Review & Commit</div>
    </div>

    <!-- Step 1: Upload -->
    <div v-if="step === 1" class="card">
      <h3>Upload Roster File</h3>
      <p style="color:var(--gray-500); margin-bottom:16px;">
        Upload a CSV or XLSX file with student roster data.
      </p>

      <div
        :class="['drop-zone', { dragging }]"
        @dragover="handleDragOver"
        @dragleave="handleDragLeave"
        @drop="handleDrop"
        @click="$refs.fileInput.click()"
      >
        <input
          ref="fileInput"
          type="file"
          accept=".csv,.xlsx,.xls"
          style="display:none"
          @change="handleFileSelect"
        />
        <div v-if="file" class="file-info">
          <strong>{{ file.name }}</strong>
          <span>({{ (file.size / 1024).toFixed(1) }} KB)</span>
        </div>
        <div v-else>
          <p class="drop-text">Drag & drop a file here, or click to browse</p>
          <p class="drop-hint">CSV or XLSX files accepted</p>
        </div>
      </div>

      <div v-if="uploadError" class="error-text" style="margin-top:12px;">{{ uploadError }}</div>

      <button
        class="btn-primary"
        style="margin-top:16px;"
        :disabled="!file || uploading"
        @click="uploadFile"
      >
        {{ uploading ? 'Uploading...' : 'Upload & Preview' }}
      </button>
    </div>

    <!-- Step 2: Preview & Commit -->
    <div v-if="step === 2">
      <div v-if="commitSuccess" class="card" style="border-left:4px solid var(--success);">
        <h3 style="color:var(--success);">Import Successful</h3>
        <p>{{ validRows.length }} roster records have been imported successfully.</p>
        <div style="margin-top:12px; display:flex; gap:8px;">
          <router-link to="/rosters" class="btn-primary" style="text-decoration:none;">
            View Rosters
          </router-link>
          <button class="btn-secondary" @click="startOver">Import Another</button>
        </div>
      </div>

      <template v-else>
        <ImportPreview
          :columns="previewColumns"
          :valid-rows="validRows"
          :invalid-rows="invalidRows"
        />

        <div class="card" style="margin-top:16px;">
          <div v-if="commitError" class="error-text" style="margin-bottom:12px;">{{ commitError }}</div>
          <div class="commit-actions">
            <button class="btn-secondary" @click="startOver">Cancel</button>
            <button
              v-if="invalidRows.length > 0"
              class="btn-secondary"
              @click="downloadErrors"
            >
              Download Errors
            </button>
            <button
              class="btn-primary"
              :disabled="!canCommit || committing"
              @click="commitImport"
            >
              {{ committing ? 'Committing...' : `Commit ${validRows.length} Valid Rows` }}
            </button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.steps {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
}

.step {
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  background: var(--gray-100);
  color: var(--gray-500);
}

.step.active {
  background: var(--primary);
  color: white;
}

.step.done {
  background: var(--success);
  color: white;
}

.drop-zone {
  border: 2px dashed var(--gray-300);
  border-radius: 8px;
  padding: 40px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.drop-zone:hover, .drop-zone.dragging {
  border-color: var(--primary);
  background: #eff6ff;
}

.drop-text {
  font-size: 16px;
  font-weight: 500;
  color: var(--gray-700);
}

.drop-hint {
  font-size: 13px;
  color: var(--gray-500);
  margin-top: 4px;
}

.file-info {
  font-size: 14px;
}

.file-info span {
  color: var(--gray-500);
  margin-left: 8px;
}

.commit-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
