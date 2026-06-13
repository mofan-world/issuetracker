<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Tickets, Plus, User, SwitchButton, Collection, FolderOpened } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useProjectStore } from '@/stores/project'
import { useAppI18n } from '@/i18n'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const projects = useProjectStore()
const { locale, t } = useAppI18n()

interface WorkspaceTab {
  path: string
  titleKey: string
  closable: boolean
}

const tabs = ref<WorkspaceTab[]>([])
const activeTab = ref(route.fullPath)

const activeMenu = computed(() => {
  if (route.path.startsWith('/admin/projects')) return '/admin/projects'
  if (route.path.startsWith('/admin/versions')) return '/admin/versions'
  if (route.path.startsWith('/admin')) return '/admin/users'
  if (route.path === '/tickets/new') return '/tickets/new'
  return '/tickets'
})

watch(
  () => route.fullPath,
  (path) => {
    activeTab.value = path
    if (tabs.value.some((tab) => tab.path === path)) return
    tabs.value.push({
      path,
      titleKey: route.meta.titleKey || 'app.workspace',
      closable: path !== '/tickets',
    })
  },
  { immediate: true },
)

function changeTab(path: string | number) {
  if (String(path) !== route.fullPath) router.push(String(path))
}

function removeTab(path: string | number) {
  const targetPath = String(path)
  const index = tabs.value.findIndex((tab) => tab.path === targetPath)
  if (index < 0 || !tabs.value[index].closable) return
  tabs.value.splice(index, 1)
  if (targetPath !== route.fullPath) return
  const nextTab = tabs.value[Math.min(index, tabs.value.length - 1)]
  router.push(nextTab?.path || '/tickets')
}

async function logout() {
  await auth.logout()
  projects.reset()
  await router.replace('/login')
}

async function changeProject(projectId: number) {
  projects.setCurrentProject(projectId)
  if (route.path !== '/tickets') await router.push('/tickets')
}

onMounted(() => projects.loadProjects())
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">IT</div>
        <div>
          <strong>{{ t('app.center') }}</strong>
          <span>Issue Tracker</span>
        </div>
      </div>

      <el-menu :default-active="activeMenu" router>
        <el-menu-item index="/tickets">
          <el-icon><Tickets /></el-icon>
          <span>{{ t('nav.tickets') }}</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('ticket:create')" index="/tickets/new">
          <el-icon><Plus /></el-icon>
          <span>{{ t('nav.createTicket') }}</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('user:manage')" index="/admin/users">
          <el-icon><User /></el-icon>
          <span>{{ t('nav.users') }}</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('version:manage')" index="/admin/versions">
          <el-icon><Collection /></el-icon>
          <span>{{ t('nav.versions') }}</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('project:manage')" index="/admin/projects">
          <el-icon><FolderOpened /></el-icon>
          <span>{{ t('nav.projects') }}</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-user">
        <el-avatar>{{ auth.user?.displayName?.slice(0, 1) }}</el-avatar>
        <div class="sidebar-user-info">
          <strong>{{ auth.user?.displayName }}</strong>
          <span>{{ auth.user?.roles.join(' / ') }}</span>
        </div>
        <el-button :icon="SwitchButton" circle text :title="t('app.logout')" @click="logout" />
      </div>
    </aside>

    <main class="content">
      <header class="topbar">
        <div>
          <span class="eyebrow">WORKSPACE</span>
          <h1>{{ route.meta.titleKey ? t(route.meta.titleKey) : t('app.workspace') }}</h1>
        </div>
        <div class="topbar-actions">
          <el-select
            v-model="projects.currentProjectId"
            class="project-switcher"
            :loading="projects.loading"
            :placeholder="t('project.selectCurrent')"
            @change="changeProject"
          >
            <el-option
              v-for="project in projects.projects"
              :key="project.id"
              :label="`${project.name} (${project.code})`"
              :value="project.id"
            />
          </el-select>
          <span class="topbar-date">{{ new Date().toLocaleDateString(locale === 'en' ? 'en-US' : 'zh-CN') }}</span>
        </div>
      </header>
      <el-tabs
        v-model="activeTab"
        class="workspace-tabs"
        type="card"
        @tab-change="changeTab"
        @tab-remove="removeTab"
      >
        <el-tab-pane
          v-for="tab in tabs"
          :key="tab.path"
          :name="tab.path"
          :label="t(tab.titleKey)"
          :closable="tab.closable"
        />
      </el-tabs>
      <div class="page-body">
        <router-view v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" :key="route.fullPath" />
          </keep-alive>
        </router-view>
      </div>
    </main>
  </div>
</template>
