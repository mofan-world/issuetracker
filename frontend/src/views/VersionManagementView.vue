<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { errorMessage, http } from '@/api/http'
import type { ProductVersionStatus, VersionOption, VersionView } from '@/types'
import VersionTreeSelect from '@/components/VersionTreeSelect.vue'
import { buildVersionTree, filterVersionTree, flattenVersionTree } from '@/utils/versionTree'
import { useAppI18n } from '@/i18n'

const { t } = useAppI18n()
const loading = ref(false)
const saving = ref(false)
const statusOptions: ProductVersionStatus[] = ['PLANNED', 'ACTIVE', 'RELEASED', 'ARCHIVED']
const versions = ref<VersionView[]>([])
const parentOptions = ref<VersionOption[]>([])
const query = reactive({ keyword: '' })
const expandedRowKeys = ref<string[]>([])
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
const versionTree = computed(() => {
  const tree = buildVersionTree(versions.value)
  const keyword = query.keyword.trim().toLowerCase()
  if (!keyword) return tree
  return filterVersionTree(
    tree,
    (version) =>
      `${version.versionNo} ${version.name} ${version.pathLabel}`
        .toLowerCase()
        .includes(keyword),
  )
})
const parentDisabledIds = computed(() =>
  parentOptions.value
    .filter((option) => !canSelectAsParent(option))
    .map((option) => option.id),
)

function statusLabel(status: ProductVersionStatus) {
  return t(`version.status.${status}`)
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
      http.get<VersionView[]>('/api/versions/tree'),
      http.get<VersionOption[]>('/api/versions/options'),
    ])
    versions.value = data
    parentOptions.value = options
    applySearchExpansion()
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
  applySearchExpansion()
}

function applySearchExpansion() {
  expandedRowKeys.value = query.keyword.trim()
    ? flattenVersionTree(versionTree.value).map((item) => String(item.id))
    : []
}

function expandAll() {
  expandedRowKeys.value = flattenVersionTree(versionTree.value).map((item) => String(item.id))
}

function collapseAll() {
  expandedRowKeys.value = []
}

function handleExpandChange(version: VersionView, expanded: boolean) {
  const keys = new Set(expandedRowKeys.value)
  const rowKey = String(version.id)
  if (expanded) keys.add(rowKey)
  else keys.delete(rowKey)
  expandedRowKeys.value = [...keys]
}

onMounted(load)
</script>

<template>
  <section class="panel">
    <div class="section-heading compact">
      <div>
        <span class="eyebrow">RELEASE CONTROL</span>
        <h2>{{ t('version.title') }}</h2>
      </div>
      <el-button type="primary" :icon="Plus" @click="createVersion">{{ t('version.add') }}</el-button>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        class="search-input"
        clearable
        :placeholder="t('version.searchPlaceholder')"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button @click="search">{{ t('common.search') }}</el-button>
      <el-button @click="expandAll">{{ t('version.expandAll') }}</el-button>
      <el-button @click="collapseAll">{{ t('version.collapseAll') }}</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="versionTree"
      row-key="id"
      :tree-props="{ children: 'children' }"
      :expand-row-keys="expandedRowKeys"
      @expand-change="handleExpandChange"
    >
      <el-table-column prop="versionNo" :label="t('version.versionNo')" min-width="210">
        <template #default="{ row }"><span class="ticket-no">{{ row.versionNo }}</span></template>
      </el-table-column>
      <el-table-column prop="name" :label="t('version.name')" min-width="180" />
      <el-table-column prop="pathLabel" :label="t('version.path')" min-width="220" show-overflow-tooltip />
      <el-table-column :label="t('version.statusLabel')" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 'RELEASED' ? 'success' : row.status === 'ARCHIVED' ? 'info' : 'primary'">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('version.releaseDate')" width="130">
        <template #default="{ row }">{{ row.releaseDate || '-' }}</template>
      </el-table-column>
      <el-table-column :label="t('version.selectable')" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? t('common.enabled') : t('common.disabled') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('version.updatedAt')" width="175">
        <template #default="{ row }">{{ dayjs(row.updatedAt).format('YYYY-MM-DD HH:mm') }}</template>
      </el-table-column>
      <el-table-column :label="t('common.operation')" width="145" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="editVersion(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('version.edit') : t('version.add')"
      width="620px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item :label="t('version.versionNo')" prop="versionNo">
            <el-input v-model="form.versionNo" placeholder="例如 2.3.0" />
          </el-form-item>
          <el-form-item :label="t('version.name')" prop="name">
            <el-input v-model="form.name" placeholder="例如 夏季功能版本" />
          </el-form-item>
        </div>
        <el-form-item :label="t('version.parent')">
          <VersionTreeSelect
            v-model="form.parentId"
            :options="parentOptions"
            :disabled-ids="parentDisabledIds"
            :respect-enabled="false"
            clearable
            :placeholder="t('version.noParentPlaceholder')"
          />
          <div class="form-tip">{{ t('version.parentTip') }}</div>
        </el-form-item>
        <div class="form-grid">
          <el-form-item :label="t('version.statusLabel')" prop="status">
            <el-select v-model="form.status" class="full-width">
              <el-option
                v-for="value in statusOptions"
                :key="value"
                :label="statusLabel(value)"
                :value="value"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('version.releaseDate')">
            <el-date-picker v-model="form.releaseDate" type="date" value-format="YYYY-MM-DD" class="full-width" />
          </el-form-item>
        </div>
        <el-form-item :label="t('version.description')">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="5000" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('version.allowSelection')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>
