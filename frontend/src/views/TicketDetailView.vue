<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox, type UploadUserFile } from 'element-plus'
import { Download, Delete, Edit, Upload } from '@element-plus/icons-vue'
import { http, errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type {
  TicketAttachment,
  TicketDetail,
  TicketPriority,
  UserSummary,
  VersionOption,
} from '@/types'
import { priorityLabels, priorityTypes, statusLabels, statusTypes } from '@/utils/ticket'
import MarkdownImageEditor from '@/components/MarkdownImageEditor.vue'
import SafeMarkdownContent from '@/components/SafeMarkdownContent.vue'
import VersionTreeSelect from '@/components/VersionTreeSelect.vue'

const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const acting = ref(false)
const ticket = ref<TicketDetail>()
const versions = ref<VersionOption[]>([])
const assignVisible = ref(false)
const assignees = ref<UserSummary[]>([])
const selectedAssignee = ref<number>()
const editVisible = ref(false)
const editFiles = ref<UploadUserFile[]>([])
const uploadFiles = ref<UploadUserFile[]>([])
const resolveVisible = ref(false)
const editForm = reactive({
  title: '',
  description: '',
  category: '',
  priority: 'MEDIUM' as TicketPriority,
  affectedVersionId: undefined as number | undefined,
})
const resolveForm = reactive({
  resolution: '',
  resolvedVersionId: undefined as number | undefined,
})

const canAssign = computed(() =>
  auth.hasPermission('ticket:assign') && ['NEW', 'ASSIGNED'].includes(ticket.value?.status || ''),
)
const canEdit = computed(() => {
  if (!ticket.value || ticket.value.status === 'CLOSED') return false
  if (auth.hasPermission('ticket:update:all')) return true
  return auth.hasPermission('ticket:update')
    && ticket.value.creator.id === auth.user?.id
    && ['NEW', 'ASSIGNED'].includes(ticket.value.status)
})
const canStart = computed(() =>
  auth.hasPermission('ticket:process')
  && ticket.value?.status === 'ASSIGNED'
  && ticket.value.assignee?.id === auth.user?.id,
)
const canUpload = computed(() => canEdit.value || (
  auth.hasPermission('ticket:process')
  && ticket.value?.assignee?.id === auth.user?.id
  && ['ASSIGNED', 'IN_PROGRESS'].includes(ticket.value?.status || '')
))
const canResolve = computed(() =>
  auth.hasPermission('ticket:process')
  && ticket.value?.status === 'IN_PROGRESS'
  && ticket.value.assignee?.id === auth.user?.id,
)
const canVerify = computed(() => auth.hasPermission('ticket:verify') && ticket.value?.status === 'RESOLVED')
const canClose = computed(() => auth.hasPermission('ticket:close') && ticket.value?.status === 'VERIFIED')

async function load() {
  loading.value = true
  try {
    const [{ data: detail }, { data: options }] = await Promise.all([
      http.get<TicketDetail>(`/api/tickets/${route.params.id}`),
      http.get<VersionOption[]>('/api/versions/options'),
    ])
    ticket.value = detail
    versions.value = options
    if (!options.some((item) => item.id === detail.affectedVersion.id)) {
      versions.value = [
        ...options,
        {
          ...detail.affectedVersion,
          status: 'ARCHIVED',
          enabled: false,
          depth: 1,
          pathLabel: detail.affectedVersion.versionNo,
        },
      ]
    }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function runAction(path: string, payload: Record<string, unknown>, message: string) {
  if (!ticket.value) return
  acting.value = true
  try {
    const { data } = await http.post<TicketDetail>(`/api/tickets/${ticket.value.id}/${path}`, payload)
    ticket.value = data
    ElMessage.success(message)
  } catch (error) {
    ElMessage.error(errorMessage(error))
    if ((error as { response?: { status?: number } }).response?.status === 409) await load()
  } finally {
    acting.value = false
  }
}

function validateFiles(filesToCheck: UploadUserFile[]) {
  const allowed = new Set(['doc', 'docx', 'xls', 'xlsx', 'pdf', 'png', 'jpg', 'jpeg', 'gif', 'webp', 'txt', 'csv', 'zip'])
  for (const item of filesToCheck) {
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

async function openAssign() {
  try {
    const { data } = await http.get<UserSummary[]>('/api/users/assignees')
    assignees.value = data
    selectedAssignee.value = ticket.value?.assignee?.id
    assignVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

async function assign() {
  if (!ticket.value || !selectedAssignee.value) {
    ElMessage.warning('请选择处理人')
    return
  }
  await runAction('assign', {
    assigneeId: selectedAssignee.value,
    version: ticket.value.version,
    comment: '问题单已分派',
  }, '分派成功')
  assignVisible.value = false
}

function openEdit() {
  if (!ticket.value) return
  Object.assign(editForm, {
    title: ticket.value.title,
    description: ticket.value.description,
    category: ticket.value.category,
    priority: ticket.value.priority,
    affectedVersionId: ticket.value.affectedVersion.id,
  })
  editFiles.value = []
  editVisible.value = true
}

async function updateTicket() {
  if (!ticket.value || !editForm.title.trim() || !editForm.description.trim() || !editForm.affectedVersionId) {
    ElMessage.warning('请完整填写问题单信息')
    return
  }
  if (!validateFiles(editFiles.value)) return
  acting.value = true
  try {
    const body = new FormData()
    body.append('request', new Blob([JSON.stringify({
      ...editForm,
      version: ticket.value.version,
    })], { type: 'application/json' }))
    editFiles.value.forEach((item) => {
      if (item.raw) body.append('files', item.raw, item.raw.name)
    })
    const { data } = await http.put<TicketDetail>(`/api/tickets/${ticket.value.id}`, body)
    ticket.value = data
    editVisible.value = false
    ElMessage.success('问题单已更新')
  } catch (error) {
    ElMessage.error(errorMessage(error))
    if ((error as { response?: { status?: number } }).response?.status === 409) await load()
  } finally {
    acting.value = false
  }
}

async function uploadAttachments() {
  if (!ticket.value || uploadFiles.value.length === 0) return
  if (!validateFiles(uploadFiles.value)) return
  acting.value = true
  try {
    const body = new FormData()
    uploadFiles.value.forEach((item) => {
      if (item.raw) body.append('files', item.raw, item.raw.name)
    })
    const { data } = await http.post<TicketAttachment[]>(
      `/api/tickets/${ticket.value.id}/attachments`,
      body,
    )
    ticket.value.attachments = data
    uploadFiles.value = []
    ElMessage.success('附件已上传')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    acting.value = false
  }
}

async function downloadAttachment(attachment: TicketAttachment) {
  try {
    const { data } = await http.get<Blob>(`/api/tickets/attachments/${attachment.id}`, {
      responseType: 'blob',
    })
    const url = URL.createObjectURL(data)
    const link = document.createElement('a')
    link.href = url
    link.download = attachment.originalName
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

function canDeleteAttachment(attachment: TicketAttachment) {
  return auth.hasPermission('attachment:delete:all')
    || attachment.uploader.id === auth.user?.id
    || ticket.value?.creator.id === auth.user?.id
}

async function deleteAttachment(attachment: TicketAttachment) {
  try {
    await ElMessageBox.confirm(`确认删除附件 ${attachment.originalName}？`, '删除附件', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await http.delete(`/api/tickets/attachments/${attachment.id}`)
    if (ticket.value) {
      ticket.value.attachments = ticket.value.attachments.filter((item) => item.id !== attachment.id)
    }
    ElMessage.success('附件已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  }
}

async function start() {
  if (!ticket.value) return
  await runAction('start', { version: ticket.value.version, comment: '开始处理' }, '已开始处理')
}

function openResolve() {
  resolveForm.resolution = ticket.value?.resolution || ''
  resolveForm.resolvedVersionId = ticket.value?.resolvedVersion?.id
  resolveVisible.value = true
}

async function resolveTicket() {
  if (!ticket.value || !resolveForm.resolution.trim() || !resolveForm.resolvedVersionId) {
    ElMessage.warning('请填写解决方案并选择解决版本')
    return
  }
  await runAction('resolve', {
    version: ticket.value.version,
    resolution: resolveForm.resolution,
    resolvedVersionId: resolveForm.resolvedVersionId,
  }, '已提交解决方案')
  resolveVisible.value = false
}

async function verify(passed: boolean) {
  if (!ticket.value) return
  try {
    const { value } = await ElMessageBox.prompt(
      passed ? '请填写验证说明' : '请填写驳回原因，问题单将退回处理中',
      passed ? '验证通过' : '验证驳回',
      {
        inputType: 'textarea',
        inputValidator: (text) => Boolean(text?.trim()) || '验证说明不能为空',
        confirmButtonText: passed ? '确认通过' : '确认驳回',
        cancelButtonText: '取消',
      },
    )
    await runAction('verify', { version: ticket.value.version, passed, comment: value }, passed ? '验证通过' : '已退回处理')
  } catch {
    // Dialog cancellation needs no feedback.
  }
}

async function closeTicket() {
  if (!ticket.value) return
  try {
    await ElMessageBox.confirm('关闭后问题单流程结束，确认关闭吗？', '关闭问题单', {
      type: 'warning',
      confirmButtonText: '确认关闭',
      cancelButtonText: '取消',
    })
    await runAction('close', { version: ticket.value.version, comment: '验证完成，关闭问题单' }, '问题单已关闭')
  } catch {
    // Dialog cancellation needs no feedback.
  }
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <template v-if="ticket">
      <section class="ticket-header">
        <div>
          <div class="ticket-kicker">
            <span class="ticket-no">{{ ticket.ticketNo }}</span>
            <el-tag :type="statusTypes[ticket.status]" effect="plain">{{ statusLabels[ticket.status] }}</el-tag>
            <el-tag :type="priorityTypes[ticket.priority]" effect="light">{{ priorityLabels[ticket.priority] }}</el-tag>
          </div>
          <h2>{{ ticket.title }}</h2>
          <p>由 {{ ticket.creator.displayName }} 创建于 {{ dayjs(ticket.createdAt).format('YYYY-MM-DD HH:mm') }}</p>
        </div>
        <div class="ticket-actions">
          <el-button v-if="canEdit" :icon="Edit" :disabled="acting" @click="openEdit">编辑</el-button>
          <el-button v-if="canAssign" :disabled="acting" @click="openAssign">分派</el-button>
          <el-button v-if="canStart" type="primary" :loading="acting" @click="start">开始处理</el-button>
          <el-button v-if="canResolve" type="primary" :loading="acting" @click="openResolve">提交解决</el-button>
          <el-button v-if="canVerify" type="danger" plain :loading="acting" @click="verify(false)">验证驳回</el-button>
          <el-button v-if="canVerify" type="success" :loading="acting" @click="verify(true)">验证通过</el-button>
          <el-button v-if="canClose" type="primary" :loading="acting" @click="closeTicket">关闭问题单</el-button>
        </div>
      </section>

      <div class="detail-grid">
        <section class="panel detail-main">
          <div class="content-block">
            <span class="block-label">问题描述</span>
            <SafeMarkdownContent :content="ticket.description" />
          </div>
          <div v-if="ticket.resolution" class="content-block resolution-block">
            <span class="block-label">解决方案</span>
            <p class="description">{{ ticket.resolution }}</p>
          </div>
          <div class="content-block">
            <div class="block-heading">
              <span class="block-label">附件（{{ ticket.attachments.length }}）</span>
            </div>
            <div v-if="ticket.attachments.length" class="attachment-list">
              <div v-for="attachment in ticket.attachments" :key="attachment.id" class="attachment-item">
                <div>
                  <strong>{{ attachment.originalName }}</strong>
                  <span>
                    {{ formatSize(attachment.fileSize) }} · {{ attachment.uploader.displayName }} ·
                    {{ dayjs(attachment.createdAt).format('YYYY-MM-DD HH:mm') }}
                  </span>
                </div>
                <div>
                  <el-button link type="primary" :icon="Download" @click="downloadAttachment(attachment)">下载</el-button>
                  <el-button
                    v-if="canDeleteAttachment(attachment)"
                    link
                    type="danger"
                    :icon="Delete"
                    @click="deleteAttachment(attachment)"
                  >
                    删除
                  </el-button>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无附件" :image-size="70" />
            <div v-if="canUpload" class="attachment-upload">
              <el-upload
                v-model:file-list="uploadFiles"
                multiple
                :auto-upload="false"
                :limit="20"
                accept=".doc,.docx,.xls,.xlsx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.txt,.csv,.zip"
              >
                <el-button :icon="Upload">选择附件</el-button>
              </el-upload>
              <el-button
                type="primary"
                plain
                :disabled="uploadFiles.length === 0"
                :loading="acting"
                @click="uploadAttachments"
              >
                上传所选文件
              </el-button>
            </div>
          </div>
          <div class="content-block">
            <span class="block-label">流转记录</span>
            <el-timeline class="ticket-timeline">
              <el-timeline-item
                v-for="item in [...ticket.transitions].reverse()"
                :key="item.id"
                :timestamp="dayjs(item.createdAt).format('YYYY-MM-DD HH:mm:ss')"
                placement="top"
              >
                <div class="timeline-card">
                  <strong>{{ item.operator.displayName }} · {{ statusLabels[item.toStatus] }}</strong>
                  <span>{{ item.action }}</span>
                  <p v-if="item.comment">{{ item.comment }}</p>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </section>

        <aside class="panel metadata-panel">
          <span class="block-label">基本信息</span>
          <dl>
            <div><dt>分类</dt><dd>{{ ticket.category }}</dd></div>
            <div><dt>问题所在版本</dt><dd>{{ ticket.affectedVersion.versionNo }}</dd></div>
            <div><dt>解决版本</dt><dd>{{ ticket.resolvedVersion?.versionNo || '-' }}</dd></div>
            <div><dt>创建人</dt><dd>{{ ticket.creator.displayName }}</dd></div>
            <div><dt>当前处理人</dt><dd>{{ ticket.assignee?.displayName || '未分派' }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ dayjs(ticket.updatedAt).format('YYYY-MM-DD HH:mm') }}</dd></div>
            <div><dt>数据版本</dt><dd>v{{ ticket.version }}</dd></div>
          </dl>
        </aside>
      </div>
    </template>

    <el-dialog v-model="assignVisible" title="分派问题单" width="460px">
      <el-select v-model="selectedAssignee" filterable class="full-width" placeholder="请选择处理人">
        <el-option
          v-for="user in assignees"
          :key="user.id"
          :label="`${user.displayName} (${user.username})`"
          :value="user.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="assign">确认分派</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑问题单" width="760px">
      <el-form :model="editForm" label-position="top">
        <el-form-item label="问题标题" required>
          <el-input v-model="editForm.title" maxlength="200" show-word-limit />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="问题分类" required>
            <el-select v-model="editForm.category" class="full-width">
              <el-option label="功能异常" value="功能异常" />
              <el-option label="性能问题" value="性能问题" />
              <el-option label="数据问题" value="数据问题" />
              <el-option label="安全问题" value="安全问题" />
              <el-option label="使用咨询" value="使用咨询" />
              <el-option label="其他" value="其他" />
            </el-select>
          </el-form-item>
          <el-form-item label="问题所在版本" required>
            <VersionTreeSelect
              v-model="editForm.affectedVersionId"
              :options="versions"
              placeholder="请选择或搜索问题所在版本"
            />
          </el-form-item>
        </div>
        <el-form-item label="优先级">
          <el-radio-group v-model="editForm.priority">
            <el-radio-button value="LOW">低</el-radio-button>
            <el-radio-button value="MEDIUM">中</el-radio-button>
            <el-radio-button value="HIGH">高</el-radio-button>
            <el-radio-button value="CRITICAL">紧急</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="详细描述" required>
          <MarkdownImageEditor
            v-model="editForm.description"
            :rows="7"
            :maxlength="20000"
            placeholder="描述问题，可直接粘贴图片"
          />
        </el-form-item>
        <el-form-item label="追加附件">
          <el-upload
            v-model:file-list="editFiles"
            multiple
            :auto-upload="false"
            :limit="20"
            accept=".doc,.docx,.xls,.xlsx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.txt,.csv,.zip"
          >
            <el-button>选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="updateTicket">保存修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resolveVisible" title="提交解决方案" width="620px">
      <el-form :model="resolveForm" label-position="top">
        <el-form-item label="解决版本" required>
          <VersionTreeSelect
            v-model="resolveForm.resolvedVersionId"
            :options="versions"
            exclude-archived
            placeholder="请选择或搜索解决版本"
          />
        </el-form-item>
        <el-form-item label="解决方案或处理结果" required>
          <el-input v-model="resolveForm.resolution" type="textarea" :rows="7" maxlength="20000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="resolveTicket">提交解决</el-button>
      </template>
    </el-dialog>
  </div>
</template>
