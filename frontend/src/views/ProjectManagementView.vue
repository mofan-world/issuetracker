<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
  type UploadFile,
} from 'element-plus'
import { Plus, Upload } from '@element-plus/icons-vue'
import { errorMessage, http } from '@/api/http'
import { useAppI18n } from '@/i18n'
import { useProjectStore } from '@/stores/project'
import type {
  PageResult,
  ProjectImportResult,
  ProjectMember,
  ProjectView,
  UserSummary,
} from '@/types'

const { t } = useAppI18n()
const projectStore = useProjectStore()
const loading = ref(false)
const saving = ref(false)
const projects = ref<ProjectView[]>([])
const dialogVisible = ref(false)
const editingId = ref<number>()
const formRef = ref<FormInstance>()
const form = reactive({
  code: '',
  name: '',
  description: '',
  enabled: true,
})
const rules: FormRules = {
  code: [
    { required: true, message: '请输入项目编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]{2,50}$/, message: '项目编码格式不正确', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
}

const membersVisible = ref(false)
const currentProject = ref<ProjectView>()
const members = ref<ProjectMember[]>([])
const membersLoading = ref(false)
const memberQuery = reactive({ keyword: '', page: 1, size: 20 })
const memberTotal = ref(0)

const addMembersVisible = ref(false)
const userOptions = ref<UserSummary[]>([])
const selectedUserIds = ref<number[]>([])
const userSearchLoading = ref(false)

const copyVisible = ref(false)
const sourceProjectId = ref<number>()
const sourceProjects = computed(() =>
  projects.value.filter((project) => project.id !== currentProject.value?.id),
)

async function loadProjects() {
  loading.value = true
  try {
    const { data } = await http.get<ProjectView[]>('/api/admin/projects')
    projects.value = data
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = undefined
  Object.assign(form, { code: '', name: '', description: '', enabled: true })
  formRef.value?.clearValidate()
}

function createProject() {
  resetForm()
  dialogVisible.value = true
}

function editProject(project: ProjectView) {
  editingId.value = project.id
  Object.assign(form, {
    code: project.code,
    name: project.name,
    description: project.description || '',
    enabled: project.enabled,
  })
  dialogVisible.value = true
}

async function saveProject() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingId.value) {
      await http.put(`/api/admin/projects/${editingId.value}`, form)
    } else {
      await http.post('/api/admin/projects', form)
    }
    ElMessage.success(t('project.saved'))
    dialogVisible.value = false
    await Promise.all([loadProjects(), projectStore.loadProjects(true)])
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function openMembers(project: ProjectView) {
  currentProject.value = project
  memberQuery.keyword = ''
  memberQuery.page = 1
  membersVisible.value = true
  await loadMembers()
}

async function loadMembers() {
  if (!currentProject.value) return
  membersLoading.value = true
  try {
    const { data } = await http.get<PageResult<ProjectMember>>(
      `/api/admin/projects/${currentProject.value.id}/members`,
      {
        params: {
          keyword: memberQuery.keyword || undefined,
          page: memberQuery.page - 1,
          size: memberQuery.size,
        },
      },
    )
    members.value = data.content
    memberTotal.value = data.totalElements
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    membersLoading.value = false
  }
}

async function searchUserOptions(keyword = '') {
  userSearchLoading.value = true
  try {
    const { data } = await http.get<UserSummary[]>('/api/users/options', {
      params: { keyword: keyword || undefined },
    })
    userOptions.value = data
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    userSearchLoading.value = false
  }
}

async function openAddMembers() {
  selectedUserIds.value = []
  addMembersVisible.value = true
  await searchUserOptions()
}

async function addMembers() {
  if (!currentProject.value || selectedUserIds.value.length === 0) {
    ElMessage.warning(t('project.selectMembers'))
    return
  }
  saving.value = true
  try {
    const { data } = await http.post<ProjectImportResult>(
      `/api/admin/projects/${currentProject.value.id}/members`,
      { userIds: selectedUserIds.value },
    )
    showImportResult(data)
    addMembersVisible.value = false
    await Promise.all([loadMembers(), loadProjects(), projectStore.loadProjects(true)])
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function removeMember(member: ProjectMember) {
  if (!currentProject.value) return
  try {
    await ElMessageBox.confirm(
      t('project.removeMemberConfirm').replace('{name}', member.displayName),
      t('project.removeMember'),
      { type: 'warning' },
    )
    await http.delete(`/api/admin/projects/${currentProject.value.id}/members/${member.id}`)
    ElMessage.success(t('project.memberRemoved'))
    await Promise.all([loadMembers(), loadProjects(), projectStore.loadProjects(true)])
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  }
}

function openCopyMembers() {
  sourceProjectId.value = undefined
  copyVisible.value = true
}

async function copyMembers() {
  if (!currentProject.value || !sourceProjectId.value) {
    ElMessage.warning(t('project.selectSource'))
    return
  }
  saving.value = true
  try {
    const { data } = await http.post<ProjectImportResult>(
      `/api/admin/projects/${currentProject.value.id}/members/copy`,
      { sourceProjectId: sourceProjectId.value },
    )
    showImportResult(data)
    copyVisible.value = false
    await Promise.all([loadMembers(), loadProjects(), projectStore.loadProjects(true)])
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function importExcel(uploadFile: UploadFile) {
  if (!currentProject.value || !uploadFile.raw) return
  const body = new FormData()
  body.append('file', uploadFile.raw, uploadFile.name)
  saving.value = true
  try {
    const { data } = await http.post<ProjectImportResult>(
      `/api/admin/projects/${currentProject.value.id}/members/import`,
      body,
    )
    showImportResult(data)
    await Promise.all([loadMembers(), loadProjects(), projectStore.loadProjects(true)])
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

function showImportResult(result: ProjectImportResult) {
  const notFound = result.notFound.length
    ? `${t('project.notFound')}: ${result.notFound.join(', ')}`
    : ''
  ElMessage.success(
    `${t('project.added')} ${result.addedCount}, ${t('project.ignored')} ${result.ignoredCount}${notFound ? `; ${notFound}` : ''}`,
  )
}

onMounted(loadProjects)
</script>

<template>
  <section class="panel">
    <div class="section-heading compact">
      <div>
        <span class="eyebrow">PROJECT CONTROL</span>
        <h2>{{ t('project.management') }}</h2>
      </div>
      <el-button type="primary" :icon="Plus" @click="createProject">{{ t('project.add') }}</el-button>
    </div>

    <el-table v-loading="loading" :data="projects" row-key="id">
      <el-table-column prop="code" :label="t('project.code')" width="150">
        <template #default="{ row }"><span class="ticket-no">{{ row.code }}</span></template>
      </el-table-column>
      <el-table-column prop="name" :label="t('project.name')" min-width="180" />
      <el-table-column prop="description" :label="t('project.description')" min-width="260" show-overflow-tooltip />
      <el-table-column prop="memberCount" :label="t('project.memberCount')" width="110" />
      <el-table-column :label="t('project.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? t('common.enabled') : t('common.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('project.updatedAt')" width="175">
        <template #default="{ row }">{{ dayjs(row.updatedAt).format('YYYY-MM-DD HH:mm') }}</template>
      </el-table-column>
      <el-table-column :label="t('common.operation')" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="editProject(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="primary" @click="openMembers(row)">{{ t('project.members') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('project.edit') : t('project.add')"
      width="620px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item :label="t('project.code')" prop="code">
            <el-input v-model="form.code" maxlength="50" placeholder="PROJECT_A" />
          </el-form-item>
          <el-form-item :label="t('project.name')" prop="name">
            <el-input v-model="form.name" maxlength="100" />
          </el-form-item>
        </div>
        <el-form-item :label="t('project.description')">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="5000" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('project.status')">
          <el-switch
            v-model="form.enabled"
            :active-text="t('common.enabled')"
            :inactive-text="t('common.disabled')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveProject">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="membersVisible"
      :title="`${t('project.members')} - ${currentProject?.name || ''}`"
      width="1000px"
    >
      <div class="filter-bar project-member-toolbar">
        <el-input
          v-model="memberQuery.keyword"
          class="search-input"
          clearable
          :placeholder="t('project.searchMember')"
          @keyup.enter="memberQuery.page = 1; loadMembers()"
        />
        <el-button @click="memberQuery.page = 1; loadMembers()">{{ t('common.search') }}</el-button>
        <el-button type="primary" :icon="Plus" @click="openAddMembers">{{ t('project.addMembers') }}</el-button>
        <el-button @click="openCopyMembers">{{ t('project.copyMembers') }}</el-button>
        <el-upload
          :show-file-list="false"
          :auto-upload="false"
          accept=".xlsx"
          :on-change="importExcel"
        >
          <el-button :icon="Upload" :loading="saving">{{ t('project.importExcel') }}</el-button>
        </el-upload>
      </div>
      <p class="form-tip">{{ t('project.excelTip') }}</p>
      <el-table v-loading="membersLoading" :data="members">
        <el-table-column prop="username" :label="t('user.username')" width="145" />
        <el-table-column prop="displayName" :label="t('user.displayName')" width="145" />
        <el-table-column prop="email" :label="t('user.email')" min-width="220" />
        <el-table-column :label="t('user.roles')" min-width="210">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles" :key="role" class="role-tag" effect="plain">{{ role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('project.joinedAt')" width="175">
          <template #default="{ row }">{{ dayjs(row.joinedAt).format('YYYY-MM-DD HH:mm') }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="100">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeMember(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="memberQuery.page"
          v-model:page-size="memberQuery.size"
          :total="memberTotal"
          layout="total, prev, pager, next"
          @change="loadMembers"
        />
      </div>
    </el-dialog>

    <el-dialog v-model="addMembersVisible" :title="t('project.addMembers')" width="620px">
      <el-select
        v-model="selectedUserIds"
        multiple
        filterable
        remote
        :remote-method="searchUserOptions"
        :loading="userSearchLoading"
        class="full-width"
        :placeholder="t('project.selectMembers')"
      >
        <el-option
          v-for="user in userOptions"
          :key="user.id"
          :label="`${user.displayName} (${user.username})`"
          :value="user.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="addMembersVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="addMembers">{{ t('project.addMembers') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="copyVisible" :title="t('project.copyMembers')" width="520px">
      <el-select
        v-model="sourceProjectId"
        class="full-width"
        :placeholder="t('project.selectSource')"
      >
        <el-option
          v-for="project in sourceProjects"
          :key="project.id"
          :label="`${project.name} (${project.code})`"
          :value="project.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="copyVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="copyMembers">{{ t('project.copyMembers') }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>
