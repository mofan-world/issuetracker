<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { http, errorMessage } from '@/api/http'
import type { PageResult } from '@/types'

interface Role {
  id: number
  code: string
  name: string
  permissions: string[]
}

interface User {
  id: number
  username: string
  email: string
  displayName: string
  enabled: boolean
  roles: string[]
  createdAt: string
}

const loading = ref(false)
const users = ref<User[]>([])
const roles = ref<Role[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 20 })
const roleDialog = ref(false)
const editingUser = ref<User>()
const selectedRoles = ref<number[]>([])

async function load() {
  loading.value = true
  try {
    const [{ data: userPage }, { data: roleList }] = await Promise.all([
      http.get<PageResult<User>>('/api/admin/users', {
        params: { keyword: query.keyword || undefined, page: query.page - 1, size: query.size },
      }),
      http.get<Role[]>('/api/admin/roles'),
    ])
    users.value = userPage.content
    total.value = userPage.totalElements
    roles.value = roleList
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function editRoles(user: User) {
  editingUser.value = user
  selectedRoles.value = roles.value.filter((role) => user.roles.includes(role.code)).map((role) => role.id)
  roleDialog.value = true
}

async function saveRoles() {
  if (!editingUser.value || selectedRoles.value.length === 0) return
  try {
    await http.put(`/api/admin/users/${editingUser.value.id}/roles`, { roleIds: selectedRoles.value })
    ElMessage.success('角色已更新')
    roleDialog.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

async function toggleEnabled(user: User) {
  try {
    await http.patch(`/api/admin/users/${user.id}/enabled`, { enabled: user.enabled })
    ElMessage.success(user.enabled ? '用户已启用' : '用户已禁用')
  } catch (error) {
    user.enabled = !user.enabled
    ElMessage.error(errorMessage(error))
  }
}

function search() {
  query.page = 1
  load()
}

onMounted(load)
</script>

<template>
  <section class="panel">
    <div class="section-heading compact">
      <div>
        <span class="eyebrow">ACCESS CONTROL</span>
        <h2>用户与角色</h2>
      </div>
      <p>角色决定用户可以查看和执行的业务操作。</p>
    </div>
    <div class="filter-bar">
      <el-input v-model="query.keyword" class="search-input" clearable placeholder="搜索用户名或显示名称" @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <el-table v-loading="loading" :data="users">
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="displayName" label="显示名称" width="150" />
      <el-table-column prop="email" label="邮箱" min-width="220" />
      <el-table-column label="角色" min-width="240">
        <template #default="{ row }">
          <el-tag v-for="role in row.roles" :key="role" class="role-tag" effect="plain">{{ role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="175">
        <template #default="{ row }">{{ dayjs(row.createdAt).format('YYYY-MM-DD HH:mm') }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" @change="toggleEnabled(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }"><el-button link type="primary" @click="editRoles(row)">设置角色</el-button></template>
      </el-table-column>
    </el-table>
    <div class="pagination">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @change="load"
      />
    </div>

    <el-dialog v-model="roleDialog" :title="`设置角色 · ${editingUser?.displayName || ''}`" width="520px">
      <el-checkbox-group v-model="selectedRoles" class="role-options">
        <el-checkbox v-for="role in roles" :key="role.id" :value="role.id">
          <strong>{{ role.name }}</strong>
          <span>{{ role.code }}</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" :disabled="selectedRoles.length === 0" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

