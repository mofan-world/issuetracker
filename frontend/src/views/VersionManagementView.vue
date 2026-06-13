<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { errorMessage, http } from '@/api/http'
import type { PageResult, ProductVersionStatus, VersionOption } from '@/types'

interface VersionView {
  id: number
  versionNo: string
  name: string
  description?: string
  status: ProductVersionStatus
  releaseDate?: string
  enabled: boolean
  parentId?: number
  parentVersionNo?: string
  depth: number
  pathLabel: string
  createdAt: string
  updatedAt: string
}

const statusLabels: Record<ProductVersionStatus, string> = {
  PLANNED: '计划中',
  ACTIVE: '开发中',
  RELEASED: '已发布',
  ARCHIVED: '已归档',
}

const loading = ref(false)
const saving = ref(false)
const versions = ref<VersionView[]>([])
const parentOptions = ref<VersionOption[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 20 })
const dialogVisible = ref(false)
const editingId = ref<number>()
const formRef = ref<FormInstance>()
const form = reactive({
  versionNo: '',
  name: '',
  description: '',
  status: 'PLANNED' as ProductVersionStatus,
  releaseDate: '',
  enabled: true,
  parentId: undefined as number | undefined,
})
const rules: FormRules = {
  versionNo: [
    { required: true, message: '请输入版本号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9._-]+$/, message: '只能包含字母、数字、点、下划线和短横线', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入版本名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择版本状态', trigger: 'change' }],
}

function statusLabel(status: ProductVersionStatus) {
  return statusLabels[status]
}

function canSelectAsParent(option: VersionOption) {
  if (option.depth >= 5 || option.id === editingId.value) return false
  if (!editingId.value) return true
  let parentId = option.parentId
  const visited = new Set<number>()
  while (parentId && !visited.has(parentId)) {
    if (parentId === editingId.value) return false
    visited.add(parentId)
    parentId = parentOptions.value.find((item) => item.id === parentId)?.parentId
  }
  return true
}

async function load() {
  loading.value = true
  try {
    const [{ data }, { data: options }] = await Promise.all([
      http.get<PageResult<VersionView>>('/api/versions', {
        params: { keyword: query.keyword || undefined, page: query.page - 1, size: query.size },
      }),
      http.get<VersionOption[]>('/api/versions/options'),
    ])
    versions.value = data.content
    total.value = data.totalElements
    parentOptions.value = options
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = undefined
  Object.assign(form, {
    versionNo: '',
    name: '',
    description: '',
    status: 'PLANNED',
    releaseDate: '',
    enabled: true,
    parentId: undefined,
  })
  formRef.value?.clearValidate()
}

function createVersion() {
  resetForm()
  dialogVisible.value = true
}

function editVersion(version: VersionView) {
  editingId.value = version.id
  Object.assign(form, {
    versionNo: version.versionNo,
    name: version.name,
    description: version.description || '',
    status: version.status,
    releaseDate: version.releaseDate || '',
    enabled: version.enabled,
    parentId: version.parentId,
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = {
      ...form,
      releaseDate: form.releaseDate || null,
      parentId: form.parentId || null,
    }
    if (editingId.value) {
      await http.put(`/api/versions/${editingId.value}`, payload)
    } else {
      await http.post('/api/versions', payload)
    }
    ElMessage.success(editingId.value ? '版本已更新' : '版本已创建')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function remove(version: VersionView) {
  try {
    await ElMessageBox.confirm(
      `确认删除版本 ${version.versionNo}？已被问题单引用的版本不能删除。`,
      '删除版本',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    await http.delete(`/api/versions/${version.id}`)
    ElMessage.success('版本已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
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
        <span class="eyebrow">RELEASE CONTROL</span>
        <h2>产品版本</h2>
      </div>
      <el-button type="primary" :icon="Plus" @click="createVersion">新增版本</el-button>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        class="search-input"
        clearable
        placeholder="搜索版本号或版本名称"
        @keyup.enter="search"
      />
      <el-button @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="versions">
      <el-table-column prop="versionNo" label="版本号" width="150">
        <template #default="{ row }"><span class="ticket-no">{{ row.versionNo }}</span></template>
      </el-table-column>
      <el-table-column prop="name" label="版本名称" min-width="180" />
      <el-table-column label="层级路径" min-width="220">
        <template #default="{ row }">
          <span :style="{ paddingLeft: `${(row.depth - 1) * 14}px` }">{{ row.pathLabel }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 'RELEASED' ? 'success' : row.status === 'ARCHIVED' ? 'info' : 'primary'">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布日期" width="130">
        <template #default="{ row }">{{ row.releaseDate || '-' }}</template>
      </el-table-column>
      <el-table-column label="可选" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="175">
        <template #default="{ row }">{{ dayjs(row.updatedAt).format('YYYY-MM-DD HH:mm') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="145" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="editVersion(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
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

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑版本' : '新增版本'"
      width="620px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="版本号" prop="versionNo">
            <el-input v-model="form.versionNo" placeholder="例如 2.3.0" />
          </el-form-item>
          <el-form-item label="版本名称" prop="name">
            <el-input v-model="form.name" placeholder="例如 夏季功能版本" />
          </el-form-item>
        </div>
        <el-form-item label="父版本">
          <el-select v-model="form.parentId" clearable filterable class="full-width" placeholder="无父版本（一级）">
            <el-option
              v-for="option in parentOptions.filter(canSelectAsParent)"
              :key="option.id"
              :label="`${'— '.repeat(option.depth - 1)}${option.pathLabel}`"
              :value="option.id"
            />
          </el-select>
          <div class="form-tip">版本最多支持 5 层，不能选择自身或自己的子版本。</div>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="状态" prop="status">
            <el-select v-model="form.status" class="full-width">
              <el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item label="发布日期">
            <el-date-picker v-model="form.releaseDate" type="date" value-format="YYYY-MM-DD" class="full-width" />
          </el-form-item>
        </div>
        <el-form-item label="版本说明">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="5000" show-word-limit />
        </el-form-item>
        <el-form-item label="允许问题单选择">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
