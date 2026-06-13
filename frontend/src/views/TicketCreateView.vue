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

const router = useRouter()
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
})
const rules: FormRules = {
  title: [{ required: true, message: '请输入问题标题', trigger: 'blur' }],
  description: [{ required: true, message: '请描述问题现象与影响', trigger: 'blur' }],
  category: [{ required: true, message: '请选择问题分类', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  affectedVersionId: [{ required: true, message: '请选择问题所在版本', trigger: 'change' }],
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
    ElMessage.success(`问题单 ${data.ticketNo} 已创建`)
    await router.replace(`/tickets/${data.id}`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

onMounted(loadVersions)
</script>

<template>
  <section class="panel form-panel">
    <div class="section-heading">
      <div>
        <span class="eyebrow">NEW ISSUE</span>
        <h2>描述需要解决的问题</h2>
      </div>
      <p>请选择问题所在版本，并提供清晰的复现步骤、影响范围和相关附件。</p>
    </div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="问题标题" prop="title">
        <el-input v-model="form.title" size="large" maxlength="200" show-word-limit placeholder="用一句话概括问题" />
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="问题分类" prop="category">
          <el-select v-model="form.category" size="large" placeholder="请选择" class="full-width">
            <el-option label="功能异常" value="功能异常" />
            <el-option label="性能问题" value="性能问题" />
            <el-option label="数据问题" value="数据问题" />
            <el-option label="安全问题" value="安全问题" />
            <el-option label="使用咨询" value="使用咨询" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题所在版本" prop="affectedVersionId">
          <el-select
            v-model="form.affectedVersionId"
            size="large"
            filterable
            placeholder="请选择版本"
            class="full-width"
          >
            <el-option
              v-for="version in versions"
              :key="version.id"
              :label="`${version.versionNo} · ${version.name}`"
              :value="version.id"
            />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="优先级" prop="priority">
        <el-radio-group v-model="form.priority" size="large">
          <el-radio-button value="LOW">低</el-radio-button>
          <el-radio-button value="MEDIUM">中</el-radio-button>
          <el-radio-button value="HIGH">高</el-radio-button>
          <el-radio-button value="CRITICAL">紧急</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="详细描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="10"
          maxlength="20000"
          show-word-limit
          placeholder="建议包括：问题现象、复现步骤、影响范围、期望结果"
        />
      </el-form-item>
      <el-form-item label="附件">
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
          <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 Word、Excel、PDF、图片等类型，单个文件不超过 20MB。</div>
          </template>
        </el-upload>
      </el-form-item>
      <div class="form-actions">
        <el-button size="large" @click="router.back()">取消</el-button>
        <el-button type="primary" size="large" :loading="loading" @click="submit">提交问题单</el-button>
      </div>
    </el-form>
  </section>
</template>

