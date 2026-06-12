<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http, errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { TicketDetail, UserSummary } from '@/types'
import { priorityLabels, priorityTypes, statusLabels, statusTypes } from '@/utils/ticket'

const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const acting = ref(false)
const ticket = ref<TicketDetail>()
const assignVisible = ref(false)
const assignees = ref<UserSummary[]>([])
const selectedAssignee = ref<number>()

const canAssign = computed(() =>
  auth.hasPermission('ticket:assign') && ['NEW', 'ASSIGNED'].includes(ticket.value?.status || ''),
)
const canStart = computed(() =>
  auth.hasPermission('ticket:process')
  && ticket.value?.status === 'ASSIGNED'
  && ticket.value.assignee?.id === auth.user?.id,
)
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
    const { data } = await http.get<TicketDetail>(`/api/tickets/${route.params.id}`)
    ticket.value = data
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

async function start() {
  if (!ticket.value) return
  await runAction('start', { version: ticket.value.version, comment: '开始处理' }, '已开始处理')
}

async function resolve() {
  if (!ticket.value) return
  try {
    const { value } = await ElMessageBox.prompt('请填写解决方案或处理结果', '解决问题单', {
      inputType: 'textarea',
      inputValidator: (text) => Boolean(text?.trim()) || '解决方案不能为空',
      confirmButtonText: '提交解决',
      cancelButtonText: '取消',
    })
    await runAction('resolve', { version: ticket.value.version, resolution: value }, '已提交解决方案')
  } catch {
    // Dialog cancellation needs no feedback.
  }
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
          <el-button v-if="canAssign" :disabled="acting" @click="openAssign">分派</el-button>
          <el-button v-if="canStart" type="primary" :loading="acting" @click="start">开始处理</el-button>
          <el-button v-if="canResolve" type="primary" :loading="acting" @click="resolve">提交解决</el-button>
          <el-button v-if="canVerify" type="danger" plain :loading="acting" @click="verify(false)">验证驳回</el-button>
          <el-button v-if="canVerify" type="success" :loading="acting" @click="verify(true)">验证通过</el-button>
          <el-button v-if="canClose" type="primary" :loading="acting" @click="closeTicket">关闭问题单</el-button>
        </div>
      </section>

      <div class="detail-grid">
        <section class="panel detail-main">
          <div class="content-block">
            <span class="block-label">问题描述</span>
            <p class="description">{{ ticket.description }}</p>
          </div>
          <div v-if="ticket.resolution" class="content-block resolution-block">
            <span class="block-label">解决方案</span>
            <p class="description">{{ ticket.resolution }}</p>
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
            <div><dt>创建人</dt><dd>{{ ticket.creator.displayName }}</dd></div>
            <div><dt>当前处理人</dt><dd>{{ ticket.assignee?.displayName || '未分派' }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ dayjs(ticket.updatedAt).format('YYYY-MM-DD HH:mm') }}</dd></div>
            <div><dt>版本</dt><dd>v{{ ticket.version }}</dd></div>
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
  </div>
</template>
