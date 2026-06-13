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
  TicketStatus,
  UserSummary,
  VersionOption,
} from '@/types'
import {
  priorityTypes,
  statusTypes,
  transitionActionTypes,
} from '@/utils/ticket'
import MarkdownImageEditor from '@/components/MarkdownImageEditor.vue'
import SafeMarkdownContent from '@/components/SafeMarkdownContent.vue'
import VersionTreeSelect from '@/components/VersionTreeSelect.vue'
import { useAppI18n } from '@/i18n'

const route = useRoute()
const auth = useAuthStore()
const { t } = useAppI18n()
const categoryOptions = [
  { key: 'FUNCTIONAL', value: '功能异常' },
  { key: 'PERFORMANCE', value: '性能问题' },
  { key: 'DATA', value: '数据问题' },
  { key: 'SECURITY', value: '安全问题' },
  { key: 'CONSULTING', value: '使用咨询' },
  { key: 'OTHER', value: '其他' },
]
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
const verifyVisible = ref(false)
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
const verifyForm = reactive({
  passed: true,
  comment: '',
})

const canAssign = computed(() =>
  auth.hasPermission('ticket:assign') && ['NEW', 'ASSIGNED'].includes(ticket.value?.status || ''),
)
const canEditMetadata = computed(() => {
  if (!ticket.value || ticket.value.status !== 'NEW') return false
  return auth.hasPermission('ticket:update:all')
    || (
      auth.hasPermission('ticket:update')
      && ticket.value.creator.id === auth.user?.id
    )
})
const canEdit = computed(() => {
  if (!ticket.value || ticket.value.status === 'CLOSED') return false
  const creatorCanSupplement = auth.hasPermission('ticket:update')
    && ticket.value.creator.id === auth.user?.id
  const assigneeCanSupplement = auth.hasPermission('ticket:process')
    && ticket.value.assignee?.id === auth.user?.id
  return canEditMetadata.value || creatorCanSupplement || assigneeCanSupplement
})
const canStart = computed(() =>
  auth.hasPermission('ticket:process')
  && ticket.value?.status === 'ASSIGNED'
  && ticket.value.assignee?.id === auth.user?.id,
)
const canUpload = computed(() => ticket.value?.status !== 'CLOSED' && (
  canEdit.value
  || auth.hasPermission('ticket:update:all')
))
const canResolve = computed(() =>
  auth.hasPermission('ticket:process')
  && ticket.value?.status === 'IN_PROGRESS'
  && ticket.value.assignee?.id === auth.user?.id,
)
const canVerify = computed(() => auth.hasPermission('ticket:verify') && ticket.value?.status === 'RESOLVED')
const canClose = computed(() => auth.hasPermission('ticket:close') && ticket.value?.status === 'VERIFIED')

function statusLabel(status: TicketStatus) {
  return t(`ticket.status.${status}`)
}

function priorityLabel(priority: TicketPriority) {
  return t(`ticket.priority.${priority}`)
}

function actionLabel(action: string) {
  const key = `ticket.transition.${action}`
  const label = t(key)
  return label === key ? action : label
}

function categoryLabel(category: string) {
  const option = categoryOptions.find((item) => item.value === category)
  return option ? t(`ticket.categories.${option.key}`) : category
}

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
  if (ticket.value?.status === 'CLOSED') return false
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

function openVerify(passed: boolean) {
  verifyForm.passed = passed
  verifyForm.comment = ''
  verifyVisible.value = true
}

async function submitVerification() {
  if (!ticket.value || !verifyForm.comment.trim()) {
    ElMessage.warning(t('ticket.detail.verifyCommentRequired'))
    return
  }
  await runAction(
    'verify',
    {
      version: ticket.value.version,
      passed: verifyForm.passed,
      comment: verifyForm.comment,
    },
    verifyForm.passed ? t('ticket.detail.verifyApproveTitle') : t('ticket.detail.verifyRejectTitle'),
  )
  verifyVisible.value = false
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
            <el-tag :type="statusTypes[ticket.status]" effect="plain">{{ statusLabel(ticket.status) }}</el-tag>
            <el-tag :type="priorityTypes[ticket.priority]" effect="light">{{ priorityLabel(ticket.priority) }}</el-tag>
          </div>
          <h2>{{ ticket.title }}</h2>
          <p>
            {{ t('ticket.detail.createdBy') }} {{ ticket.creator.displayName }}
            {{ t('ticket.detail.createdAt') }} {{ dayjs(ticket.createdAt).format('YYYY-MM-DD HH:mm') }}
          </p>
        </div>
        <div class="ticket-actions">
          <el-button v-if="canEdit" :icon="Edit" :disabled="acting" @click="openEdit">{{ t('ticket.detail.edit') }}</el-button>
          <el-button v-if="canAssign" :disabled="acting" @click="openAssign">{{ t('ticket.detail.assign') }}</el-button>
          <el-button v-if="canStart" type="primary" :loading="acting" @click="start">{{ t('ticket.detail.start') }}</el-button>
          <el-button v-if="canResolve" type="primary" :loading="acting" @click="openResolve">{{ t('ticket.detail.resolve') }}</el-button>
          <el-button v-if="canVerify" type="danger" plain :loading="acting" @click="openVerify(false)">{{ t('ticket.detail.reject') }}</el-button>
          <el-button v-if="canVerify" type="success" :loading="acting" @click="openVerify(true)">{{ t('ticket.detail.approve') }}</el-button>
          <el-button v-if="canClose" type="primary" :loading="acting" @click="closeTicket">{{ t('ticket.detail.close') }}</el-button>
        </div>
      </section>

      <div class="detail-grid">
        <section class="panel detail-main">
          <div class="content-block">
            <span class="block-label">{{ t('ticket.detail.description') }}</span>
            <SafeMarkdownContent :content="ticket.description" />
          </div>
          <div v-if="ticket.resolution" class="content-block resolution-block">
            <span class="block-label">{{ t('ticket.detail.resolution') }}</span>
            <p class="description">{{ ticket.resolution }}</p>
          </div>
          <div class="content-block">
            <div class="block-heading">
              <span class="block-label">{{ t('ticket.detail.attachments') }} ({{ ticket.attachments.length }})</span>
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
                  <el-button link type="primary" :icon="Download" @click="downloadAttachment(attachment)">{{ t('ticket.detail.download') }}</el-button>
                  <el-button
                    v-if="canDeleteAttachment(attachment)"
                    link
                    type="danger"
                    :icon="Delete"
                    @click="deleteAttachment(attachment)"
                  >
                    {{ t('common.delete') }}
                  </el-button>
                </div>
              </div>
            </div>
            <el-empty v-else :description="t('ticket.detail.noAttachments')" :image-size="70" />
            <div v-if="canUpload" class="attachment-upload">
              <el-upload
                v-model:file-list="uploadFiles"
                multiple
                :auto-upload="false"
                :limit="20"
                accept=".doc,.docx,.xls,.xlsx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.txt,.csv,.zip"
              >
                <el-button :icon="Upload">{{ t('ticket.detail.selectAttachments') }}</el-button>
              </el-upload>
              <el-button
                type="primary"
                plain
                :disabled="uploadFiles.length === 0"
                :loading="acting"
                @click="uploadAttachments"
              >
                {{ t('ticket.detail.uploadSelected') }}
              </el-button>
            </div>
          </div>
          <div class="content-block">
            <span class="block-label">{{ t('ticket.detail.transitions') }}</span>
            <el-timeline class="ticket-timeline">
              <el-timeline-item
                v-for="item in [...ticket.transitions].reverse()"
                :key="item.id"
                :timestamp="dayjs(item.createdAt).format('YYYY-MM-DD HH:mm:ss')"
                placement="top"
              >
                <div class="timeline-card">
                  <div class="timeline-heading">
                    <el-tag
                      :type="transitionActionTypes[item.action] || 'info'"
                      effect="light"
                      size="small"
                    >
                      {{ actionLabel(item.action) }}
                    </el-tag>
                    <strong>{{ item.operator.displayName }}</strong>
                    <span class="timeline-status">{{ t('ticket.detail.transitionedTo') }} {{ statusLabel(item.toStatus) }}</span>
                  </div>
                  <SafeMarkdownContent v-if="item.comment" :content="item.comment" />
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </section>

        <aside class="panel metadata-panel">
          <span class="block-label">{{ t('ticket.detail.basicInfo') }}</span>
          <dl>
            <div><dt>{{ t('ticket.detail.category') }}</dt><dd>{{ categoryLabel(ticket.category) }}</dd></div>
            <div><dt>{{ t('ticket.detail.affectedVersion') }}</dt><dd>{{ ticket.affectedVersion.versionNo }}</dd></div>
            <div><dt>{{ t('ticket.detail.resolvedVersion') }}</dt><dd>{{ ticket.resolvedVersion?.versionNo || '-' }}</dd></div>
            <div><dt>{{ t('ticket.detail.creator') }}</dt><dd>{{ ticket.creator.displayName }}</dd></div>
            <div><dt>{{ t('ticket.detail.assignee') }}</dt><dd>{{ ticket.assignee?.displayName || t('ticket.unassigned') }}</dd></div>
            <div><dt>{{ t('ticket.detail.updatedAt') }}</dt><dd>{{ dayjs(ticket.updatedAt).format('YYYY-MM-DD HH:mm') }}</dd></div>
            <div><dt>{{ t('ticket.detail.dataVersion') }}</dt><dd>v{{ ticket.version }}</dd></div>
          </dl>
        </aside>
      </div>
    </template>

    <el-dialog v-model="assignVisible" :title="t('ticket.detail.assignTitle')" width="460px">
      <el-select v-model="selectedAssignee" filterable class="full-width" :placeholder="t('ticket.detail.selectAssignee')">
        <el-option
          v-for="user in assignees"
          :key="user.id"
          :label="`${user.displayName} (${user.username})`"
          :value="user.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="acting" @click="assign">{{ t('ticket.detail.confirmAssign') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="editVisible"
      :title="canEditMetadata ? t('ticket.detail.editTitle') : t('ticket.detail.supplementTitle')"
      width="760px"
    >
      <el-form :model="editForm" label-position="top">
        <el-form-item :label="t('ticket.detail.title')" required>
          <el-input
            v-model="editForm.title"
            maxlength="200"
            show-word-limit
            :disabled="!canEditMetadata"
          />
        </el-form-item>
        <div class="form-grid">
          <el-form-item :label="t('ticket.detail.category')" required>
            <el-select v-model="editForm.category" class="full-width" :disabled="!canEditMetadata">
              <el-option
                v-for="category in categoryOptions"
                :key="category.key"
                :label="t(`ticket.categories.${category.key}`)"
                :value="category.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('ticket.detail.affectedVersion')" required>
            <VersionTreeSelect
              v-model="editForm.affectedVersionId"
              :options="versions"
              :disabled="!canEditMetadata"
              :placeholder="t('ticket.detail.affectedVersionPlaceholder')"
            />
          </el-form-item>
        </div>
        <el-form-item :label="t('ticket.column.priority')">
          <el-radio-group v-model="editForm.priority" :disabled="!canEditMetadata">
            <el-radio-button value="LOW">{{ t('ticket.priority.LOW') }}</el-radio-button>
            <el-radio-button value="MEDIUM">{{ t('ticket.priority.MEDIUM') }}</el-radio-button>
            <el-radio-button value="HIGH">{{ t('ticket.priority.HIGH') }}</el-radio-button>
            <el-radio-button value="CRITICAL">{{ t('ticket.priority.CRITICAL') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('ticket.detail.descriptionLabel')" required>
          <div v-if="!canEditMetadata" class="form-tip">
            {{ t('ticket.detail.supplementTip') }}
          </div>
          <MarkdownImageEditor
            v-model="editForm.description"
            :rows="7"
            :maxlength="20000"
            :placeholder="t('ticket.detail.descriptionPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('ticket.detail.appendAttachments')">
          <el-upload
            v-model:file-list="editFiles"
            multiple
            :auto-upload="false"
            :limit="20"
            accept=".doc,.docx,.xls,.xlsx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.txt,.csv,.zip"
          >
            <el-button>{{ t('common.selectFile') }}</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="acting" @click="updateTicket">{{ t('ticket.detail.saveChanges') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resolveVisible" :title="t('ticket.detail.resolveTitle')" width="620px">
      <el-form :model="resolveForm" label-position="top">
        <el-form-item :label="t('ticket.detail.resolvedVersion')" required>
          <VersionTreeSelect
            v-model="resolveForm.resolvedVersionId"
            :options="versions"
            exclude-archived
            :placeholder="t('ticket.detail.resolvedVersionPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('ticket.detail.resolutionLabel')" required>
          <el-input v-model="resolveForm.resolution" type="textarea" :rows="7" maxlength="20000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="acting" @click="resolveTicket">{{ t('ticket.detail.resolve') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="verifyVisible"
      :title="verifyForm.passed ? t('ticket.detail.verifyApproveTitle') : t('ticket.detail.verifyRejectTitle')"
      width="680px"
    >
      <el-form label-position="top">
        <el-form-item :label="t('ticket.detail.verifyComment')" required>
          <MarkdownImageEditor
            v-model="verifyForm.comment"
            :rows="6"
            :maxlength="1000"
            :placeholder="verifyForm.passed ? t('ticket.detail.verifyApproveHint') : t('ticket.detail.verifyRejectHint')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="verifyVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          :type="verifyForm.passed ? 'success' : 'danger'"
          :loading="acting"
          @click="submitVerification"
        >
          {{ verifyForm.passed ? t('ticket.detail.confirmApprove') : t('ticket.detail.confirmReject') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
