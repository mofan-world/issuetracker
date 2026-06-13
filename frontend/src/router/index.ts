import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    permission?: string
    title?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true, title: '注册' },
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    redirect: '/tickets',
    children: [
      {
        path: 'tickets',
        component: () => import('@/views/TicketListView.vue'),
        meta: { title: '问题单列表', permission: 'ticket:read:own' },
      },
      {
        path: 'tickets/new',
        component: () => import('@/views/TicketCreateView.vue'),
        meta: { title: '创建问题单', permission: 'ticket:create' },
      },
      {
        path: 'tickets/:id',
        component: () => import('@/views/TicketDetailView.vue'),
        meta: { title: '问题单详情', permission: 'ticket:read:own' },
      },
      {
        path: 'admin/users',
        component: () => import('@/views/UserManagementView.vue'),
        meta: { title: '用户与权限', permission: 'user:manage' },
      },
      {
        path: 'admin/versions',
        component: () => import('@/views/VersionManagementView.vue'),
        meta: { title: '版本管理', permission: 'version:manage' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  document.title = `${to.meta.title || '工作台'} - 问题单跟踪系统`
  const auth = useAuthStore()
  if (to.meta.public) {
    return auth.authenticated && (to.path === '/login' || to.path === '/register') ? '/' : true
  }
  if (!auth.authenticated) return { path: '/login', query: { redirect: to.fullPath } }
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      return '/login'
    }
  }
  const permission = to.meta.permission
  if (permission && !auth.hasPermission(permission)) {
    if (permission === 'ticket:read:own' && auth.hasPermission('ticket:read:all')) return true
    return '/'
  }
  return true
})

export default router
