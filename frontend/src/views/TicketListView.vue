<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { Search, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { http, errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { PageResult, TicketPriority, TicketStatus, TicketSummary } from '@/types'
import { priorityLabels, priorityTypes, statusLabels, statusTypes } from '@/utils/ticket'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const tickets = ref<TicketSummary[]>([])
const total = ref(0)
const query = reactive<{
  keyword: string
  status?: TicketStatus
  priority?: TicketPriority
  page: number
  size: number
}>({ keyword: '', page: 1, size: 20 })

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<PageResult<TicketSummary>>('/api/tickets', {
      params: {
        keyword: query.keyword || undefined,
        status: query.status,
        priority: query.priority,
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

function search() {
  query.page = 1
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <section class="summary-strip">
      <div>
        <span>当前视图</span>
        <strong>{{ auth.hasPermission('ticket:read:all') ? '全部问题单' : '与我相关' }}</strong>
      </div>
      <div>
        <span>结果数量</span>
        <strong>{{ total }}</strong>
      </div>
      <el-button v-if="auth.hasPermission('ticket:create')" type="primary" :icon="Plus" @click="router.push('/tickets/new')">
        新建问题单
      </el-button>
    </section>

    <section class="panel">
      <div class="filter-bar">
        <el-input v-model="query.keyword" class="search-input" clearable placeholder="搜索编号、标题或描述" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.status" clearable placeholder="全部状态" @change="search">
          <el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="query.priority" clearable placeholder="全部优先级" @change="search">
          <el-option v-for="(label, value) in priorityLabels" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="tickets" row-key="id" @row-click="(row) => router.push(`/tickets/${row.id}`)">
        <el-table-column prop="ticketNo" label="编号" width="205">
          <template #default="{ row }"><span class="ticket-no">{{ row.ticketNo }}</span></template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column label="优先级" width="100">
          <template #default="{ row }">
            <el-tag :type="priorityTypes[row.priority]" effect="light">{{ priorityLabels[row.priority] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTypes[row.status]" effect="plain">{{ statusLabels[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理人" width="130">
          <template #default="{ row }">{{ row.assignee?.displayName || '未分派' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="175">
          <template #default="{ row }">{{ dayjs(row.updatedAt).format('YYYY-MM-DD HH:mm') }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @change="load"
        />
      </div>
    </section>
  </div>
</template>

