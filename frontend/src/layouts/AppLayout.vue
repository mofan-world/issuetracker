<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Tickets, Plus, User, SwitchButton, Collection } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

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
          <strong>问题单中心</strong>
          <span>Issue Tracker</span>
        </div>
      </div>

      <el-menu :default-active="activeMenu" router>
        <el-menu-item index="/tickets">
          <el-icon><Tickets /></el-icon>
          <span>问题单列表</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('ticket:create')" index="/tickets/new">
          <el-icon><Plus /></el-icon>
          <span>创建问题单</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('user:manage')" index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户与权限</span>
        </el-menu-item>
        <el-menu-item v-if="auth.hasPermission('version:manage')" index="/admin/versions">
          <el-icon><Collection /></el-icon>
          <span>版本管理</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-user">
        <el-avatar>{{ auth.user?.displayName?.slice(0, 1) }}</el-avatar>
        <div class="sidebar-user-info">
          <strong>{{ auth.user?.displayName }}</strong>
          <span>{{ auth.user?.roles.join(' / ') }}</span>
        </div>
        <el-button :icon="SwitchButton" circle text title="退出登录" @click="logout" />
      </div>
    </aside>

    <main class="content">
      <header class="topbar">
        <div>
          <span class="eyebrow">WORKSPACE</span>
          <h1>{{ route.meta.title }}</h1>
        </div>
        <span class="topbar-date">{{ new Date().toLocaleDateString('zh-CN') }}</span>
      </header>
      <div class="page-body">
        <router-view />
      </div>
    </main>
  </div>
</template>
