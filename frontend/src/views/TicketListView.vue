<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { Plus, Search, Setting } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { http, errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useAppI18n } from '@/i18n'
import type {
  PageResult,
  TicketPriority,
  TicketScope,
  TicketStatus,
  TicketSummary,
  UserSummary,
} from '@/types'
import { priorityTypes, statusTypes } from '@/utils/ticket'

type ColumnKey =
  | 'ticketNo'
  | 'title'
  | 'category'
  | 'priority'
  | 'status'
  | 'creator'
  | 'assignee'
  | 'affectedVersion'
  | 'resolvedVersion'
  | 'createdAt'
  | 'updatedAt'
  | 'resolvedAt'

const allColumnKeys: ColumnKey[] = [
  'ticketNo',
  'title',
  'category',
  'priority',
  'status',
  'creator',
  'assignee',
  'affectedVersion',
  'resolvedVersion',
  'createdAt',
  'updatedAt',
  'resolvedAt',
]
const defaultColumns: ColumnKey[] = [
  'ticketNo',
  'title',
  'priority',
  'status',
  'creator',
  'assignee',
  'affectedVersion',
  'resolvedVersion',
  'updatedAt',
  'resolvedAt',
]
const statusOptions: TicketStatus[] = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'VERIFIED', 'CLOSED']
const priorityOptions: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

const router = useRouter()
const auth = useAuthStore()
const { t } = useAppI18n()
const loading = ref(false)
const tickets = ref<TicketSummary[]>([])
const creators = ref<UserSummary[]>([])
const total = ref(0)
const storageKey = computed(() => `ticket-list-columns:${auth.user?.id || 'anonymous'}`)
const selectedColumns = ref<ColumnKey[]>(readColumns())
const query = reactive<{
  keyword: string
  status?: TicketStatus
  priority?: TicketPriority
  scope: TicketScope
  creatorId?: number
  page: number
  size: number
}>({
  keyword: '',
  scope: auth.hasPermission('ticket:read:all') ? 'ALL' : 'RELATED',
  page: 1,
  size: 20,
})

const scopeOptions = computed<TicketScope[]>(() =>
  auth.hasPermission('ticket:read:all')
    ? ['ALL', 'RELATED', 'MY_CREATED', 'CREATED_BY']
    : ['RELATED', 'MY_CREATED'],
)
const columnOptions = computed(() =>
  allColumnKeys.map((key) => ({ key, label: t(`ticket.column.${key}`) })),
)
const currentScopeLabel = computed(() => t(`ticket.scope.${query.scope}`))

function readColumns(): ColumnKey[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(storageKey.value) || '[]')
    if (!Array.isArray(parsed)) return [...defaultColumns]
    const valid = parsed.filter((item): item is ColumnKey => allColumnKeys.includes(item))
    return valid.length ? valid : [...defaultColumns]
  } catch {
    return [...defaultColumns]
  }
}

function isColumnVisible(key: ColumnKey) {
  return selectedColumns.value.includes(key)
}

function formatDate(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : t('ticket.empty')
}

async function load() {
  if (query.scope === 'CREATED_BY' && !query.creatorId) {
    tickets.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const { data } = await http.get<PageResult<TicketSummary>>('/api/tickets', {
      params: {
        keyword: query.keyword || undefined,
        status: query.status,
        priority: query.priority,
        scope: query.scope,
        creatorId: query.scope === 'CREATED_BY' ? query.creatorId : undefined,
        page: query.page - 1,
        size: query.size,
      },
    })
    tickets.value = data.content
    total.value = data.totalElements
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function loadCreators() {
  if (!auth.hasPermission('ticket:read:all')) return
  try {
    const { data } = await http.get<UserSummary[]>('/api/users/options')
    creators.value = data
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

function search() {
  query.page = 1
  load()
}

function changeScope() {
  query.creatorId = undefined
  search()
}

watch(selectedColumns, (columns) => {
  localStorage.setItem(storageKey.value, JSON.stringify(columns))
}, { deep: true })

onMounted(() => {
  load()
  loadCreators()
})
</script>

<template>
  <div>
    <section class="summary-strip">
      <div>
        <span>{{ t('ticket.currentView') }}</span>
        <strong>{{ currentScopeLabel }}</strong>
      </div>
      <div>
        <span>{{ t('ticket.resultCount') }}</span>
        <strong>{{ total }}</strong>
      </div>
      <el-button
        v-if="auth.hasPermission('ticket:create')"
        type="primary"
        :icon="Plus"
        @click="router.push('/tickets/new')"
      >
        {{ t('ticket.new') }}
      </el-button>
    </section>

    <section class="panel">
      <div class="filter-bar ticket-filter-bar">
        <el-input
          v-model="query.keyword"
          class="search-input"
          clearable
          :placeholder="t('ticket.searchPlaceholder')"
          @keyup.enter="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.scope" :placeholder="t('ticket.scope.label')" @change="changeScope">
          <el-option
            v-for="scope in scopeOptions"
            :key="scope"
            :label="t(`ticket.scope.${scope}`)"
            :value="scope"
          />
        </el-select>
        <el-select
          v-if="query.scope === 'CREATED_BY'"
          v-model="query.creatorId"
          filterable
          :placeholder="t('ticket.creatorPlaceholder')"
          @change="search"
        >
          <el-option
            v-for="creator in creators"
            :key="creator.id"
            :label="`${creator.displayName} (${creator.username})`"
            :value="creator.id"
          />
        </el-select>
        <el-select v-model="query.status" clearable :placeholder="t('ticket.allStatus')" @change="search">
          <el-option
            v-for="status in statusOptions"
            :key="status"
            :label="t(`ticket.status.${status}`)"
            :value="status"
          />
        </el-select>
        <el-select v-model="query.priority" clearable :placeholder="t('ticket.allPriority')" @change="search">
          <el-option
            v-for="priority in priorityOptions"
            :key="priority"
            :label="t(`ticket.priority.${priority}`)"
            :value="priority"
          />
        </el-select>
        <el-button @click="search">{{ t('ticket.search') }}</el-button>
        <el-popover placement="bottom-end" :width="220" trigger="click">
          <template #reference>
            <el-button class="column-settings" :icon="Setting">{{ t('ticket.columns') }}</el-button>
          </template>
          <el-checkbox-group v-model="selectedColumns" class="column-checkboxes">
            <el-checkbox
              v-for="column in columnOptions"
              :key="column.key"
              :label="column.key"
              :value="column.key"
            >
              {{ column.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-popover>
      </div>

      <el-table v-loading="loading" :data="tickets" row-key="id" @row-click="router.push(`/tickets/${$event.id}`)">
        <el-table-column v-if="isColumnVisible('ticketNo')" prop="ticketNo" :label="t('ticket.column.ticketNo')" width="205">
          <template #default="{ row }"><span class="ticket-no">{{ row.ticketNo }}</span></template>
        </el-table-column>
        <el-table-column
          v-if="isColumnVisible('title')"
          prop="title"
          :label="t('ticket.column.title')"
          min-width="240"
          show-overflow-tooltip
        />
        <el-table-column v-if="isColumnVisible('category')" prop="category" :label="t('ticket.column.category')" width="120" />
        <el-table-column v-if="isColumnVisible('priority')" :label="t('ticket.column.priority')" width="100">
          <template #default="{ row }">
            <el-tag :type="priorityTypes[row.priority as TicketPriority]" effect="light">
              {{ t(`ticket.priority.${row.priority}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('status')" :label="t('ticket.column.status')" width="130">
          <template #default="{ row }">
            <el-tag :type="statusTypes[row.status as TicketStatus]" effect="plain">
              {{ t(`ticket.status.${row.status}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('creator')" :label="t('ticket.column.creator')" width="130">
          <template #default="{ row }">{{ row.creator.displayName }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('assignee')" :label="t('ticket.column.assignee')" width="145">
          <template #default="{ row }">{{ row.assignee?.displayName || t('ticket.unassigned') }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('affectedVersion')" :label="t('ticket.column.affectedVersion')" width="145">
          <template #default="{ row }">{{ row.affectedVersion.versionNo }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('resolvedVersion')" :label="t('ticket.column.resolvedVersion')" width="145">
          <template #default="{ row }">{{ row.resolvedVersion?.versionNo || t('ticket.empty') }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('createdAt')" :label="t('ticket.column.createdAt')" width="175">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('updatedAt')" :label="t('ticket.column.updatedAt')" width="175">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('resolvedAt')" :label="t('ticket.column.resolvedAt')" width="175">
          <template #default="{ row }">{{ formatDate(row.resolvedAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @change="load"
        />
      </div>
    </section>
  </div>
</template>
