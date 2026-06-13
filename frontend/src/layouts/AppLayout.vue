<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Tickets, Plus, User, SwitchButton, Collection } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useAppI18n } from '@/i18n'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { locale, t } = useAppI18n()

const activeMenu = computed(() => {
  if (route.path.startsWith('/admin/versions')) return '/admin/versions'
  if (route.path.startsWith('/admin')) return '/admin/users'
  if (route.path === '/tickets/new') return '/tickets/new'
  return '/tickets'
})

async function logout() {
  await auth.logout()
  await router.replace('/login')
}
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
        <span class="topbar-date">{{ new Date().toLocaleDateString(locale === 'en' ? 'en-US' : 'zh-CN') }}</span>
      </header>
      <div class="page-body">
        <router-view />
      </div>
    </main>
  </div>
</template>
