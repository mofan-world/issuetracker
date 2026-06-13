import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    permission?: string
    titleKey?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, titleKey: 'nav.login' },
  },
  {
    path: '/register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true, titleKey: 'nav.register' },
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    redirect: '/tickets',
    children: [
      {
        path: 'tickets',
        component: () => import('@/views/TicketListView.vue'),
        meta: { titleKey: 'nav.tickets', permission: 'ticket:read:own' },
      },
      {
        path: 'tickets/new',
        component: () => import('@/views/TicketCreateView.vue'),
        meta: { titleKey: 'nav.createTicket', permission: 'ticket:create' },
      },
      {
        path: 'tickets/:id',
        component: () => import('@/views/TicketDetailView.vue'),
        meta: { titleKey: 'nav.ticketDetail', permission: 'ticket:read:own' },
      },
      {
        path: 'admin/users',
        component: () => import('@/views/UserManagementView.vue'),
        meta: { titleKey: 'nav.users', permission: 'user:manage' },
      },
      {
        path: 'admin/versions',
        component: () => import('@/views/VersionManagementView.vue'),
        meta: { titleKey: 'nav.versions', permission: 'version:manage' },
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
