<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElMessage,
  type FormInstance,
  type FormRules,
  type UploadInstance,
  type UploadUserFile,
} from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { http, errorMessage } from '@/api/http'
import type { TicketDetail, TicketPriority, VersionOption } from '@/types'
import MarkdownImageEditor from '@/components/MarkdownImageEditor.vue'
import VersionTreeSelect from '@/components/VersionTreeSelect.vue'
import { useAppI18n } from '@/i18n'
import { useProjectStore } from '@/stores/project'

const router = useRouter()
const { t } = useAppI18n()
const projectStore = useProjectStore()
const categoryOptions = [
  { key: 'FUNCTIONAL', value: '功能异常' },
  { key: 'PERFORMANCE', value: '性能问题' },
  { key: 'DATA', value: '数据问题' },
  { key: 'SECURITY', value: '安全问题' },
  { key: 'CONSULTING', value: '使用咨询' },
  { key: 'OTHER', value: '其他' },
]
const formRef = ref<FormInstance>()
const uploadRef = ref<UploadInstance>()
const loading = ref(false)
const versions = ref<VersionOption[]>([])
const files = ref<UploadUserFile[]>([])
const form = reactive({
  title: '',
  description: '',
  category: '',
  priority: 'MEDIUM' as TicketPriority,
  affectedVersionId: undefined as number | undefined,
  projectId: undefined as number | undefined,
})
const rules: FormRules = {
  title: [{ required: true, message: '请输入问题标题', trigger: 'blur' }],
  description: [{ required: true, message: '请描述问题现象与影响', trigger: 'blur' }],
  category: [{ required: true, message: '请选择问题分类', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  affectedVersionId: [{ required: true, message: '请选择问题所在版本', trigger: 'change' }],
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
}

async function loadVersions() {
  try {
    const { data } = await http.get<VersionOption[]>('/api/versions/options')
    versions.value = data
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

function validateFiles() {
  const allowed = new Set(['doc', 'docx', 'xls', 'xlsx', 'pdf', 'png', 'jpg', 'jpeg', 'gif', 'webp', 'txt', 'csv', 'zip'])
  for (const item of files.value) {
    const raw = item.raw
    if (!raw) continue
    const extension = raw.name.split('.').pop()?.toLowerCase() || ''
    if (!allowed.has(extension)) {
      ElMessage.error(`不支持的附件类型：${raw.name}`)
      return false
    }
    if (raw.size > 20 * 1024 * 1024) {
      ElMessage.error(`附件不能超过 20MB：${raw.name}`)
      return false
    }
  }
  return true
}

async function submit() {
  await formRef.value?.validate()
  if (!validateFiles()) return
  loading.value = true
  try {
    const body = new FormData()
    body.append('request', new Blob([JSON.stringify(form)], { type: 'application/json' }))
    files.value.forEach((item) => {
      if (item.raw) body.append('files', item.raw, item.raw.name)
    })
    const { data } = await http.post<TicketDetail>('/api/tickets', body)
    projectStore.setCurrentProject(form.projectId)
    ElMessage.success(`问题单 ${data.ticketNo} 已创建`)
    await router.replace(`/tickets/${data.id}`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadVersions(), projectStore.loadProjects()])
  form.projectId = projectStore.currentProjectId
})
</script>

<template>
  <section class="panel form-panel">
    <div class="section-heading">
      <div>
        <span class="eyebrow">NEW ISSUE</span>
        <h2>{{ t('ticket.create.heading') }}</h2>
      </div>
      <p>{{ t('ticket.create.intro') }}</p>
    </div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item :label="t('ticket.create.project')" prop="projectId" required>
        <el-select
          v-model="form.projectId"
          size="large"
          class="full-width"
          :placeholder="t('ticket.create.projectPlaceholder')"
        >
          <el-option
            v-for="project in projectStore.projects"
            :key="project.id"
            :label="`${project.name} (${project.code})`"
            :value="project.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('ticket.create.title')" prop="title">
        <el-input v-model="form.title" size="large" maxlength="200" show-word-limit :placeholder="t('ticket.create.titlePlaceholder')" />
      </el-form-item>
      <div class="form-grid">
        <el-form-item :label="t('ticket.create.category')" prop="category">
          <el-select v-model="form.category" size="large" :placeholder="t('ticket.create.select')" class="full-width">
            <el-option
              v-for="category in categoryOptions"
              :key="category.key"
              :label="t(`ticket.categories.${category.key}`)"
              :value="category.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('ticket.create.affectedVersion')" prop="affectedVersionId">
          <VersionTreeSelect
            v-model="form.affectedVersionId"
            :options="versions"
            size="large"
            :placeholder="t('ticket.create.affectedVersionPlaceholder')"
          />
        </el-form-item>
      </div>
      <el-form-item :label="t('ticket.create.priority')" prop="priority">
        <el-radio-group v-model="form.priority" size="large">
          <el-radio-button value="LOW">{{ t('ticket.priority.LOW') }}</el-radio-button>
          <el-radio-button value="MEDIUM">{{ t('ticket.priority.MEDIUM') }}</el-radio-button>
          <el-radio-button value="HIGH">{{ t('ticket.priority.HIGH') }}</el-radio-button>
          <el-radio-button value="CRITICAL">{{ t('ticket.priority.CRITICAL') }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="t('ticket.create.description')" prop="description">
        <MarkdownImageEditor
          v-model="form.description"
          :rows="10"
          :maxlength="20000"
          :placeholder="t('ticket.create.descriptionPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('ticket.create.attachments')">
        <el-upload
          ref="uploadRef"
          v-model:file-list="files"
          drag
          multiple
          :auto-upload="false"
          :limit="20"
          accept=".doc,.docx,.xls,.xlsx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.txt,.csv,.zip"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">{{ t('ticket.create.dragFiles') }} <em>{{ t('ticket.create.clickSelect') }}</em></div>
          <template #tip>
            <div class="el-upload__tip">{{ t('ticket.create.attachmentTip') }}</div>
          </template>
        </el-upload>
      </el-form-item>
      <div class="form-actions">
        <el-button size="large" @click="router.back()">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" size="large" :loading="loading" @click="submit">{{ t('ticket.create.submit') }}</el-button>
      </div>
    </el-form>
  </section>
</template>
