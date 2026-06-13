import { readonly, ref } from 'vue'

export type AppLocale = 'zh-CN' | 'en'

const storedLocale = localStorage.getItem('issue-tracker-locale')
const initialLocale: AppLocale = storedLocale === 'en' ? 'en' : 'zh-CN'

const messages = {
  'zh-CN': {
    app: {
      name: '问题单跟踪系统',
      center: '问题单中心',
      workspace: '工作台',
      logout: '退出登录',
      language: 'English',
    },
    nav: {
      tickets: '问题单列表',
      createTicket: '创建问题单',
      ticketDetail: '问题单详情',
      users: '用户与权限',
      versions: '版本管理',
      login: '登录',
      register: '注册',
    },
    ticket: {
      currentView: '当前视图',
      resultCount: '结果数量',
      new: '新建问题单',
      searchPlaceholder: '搜索编号、标题或描述',
      allStatus: '全部状态',
      allPriority: '全部优先级',
      search: '查询',
      columns: '列设置',
      scope: {
        label: '展示范围',
        ALL: '全部问题单',
        RELATED: '与我相关',
        MY_CREATED: '我创建的',
        CREATED_BY: '指定创建人',
      },
      creatorPlaceholder: '选择创建人',
      unassigned: '未分派',
      empty: '-',
      column: {
        ticketNo: '编号',
        title: '标题',
        category: '分类',
        priority: '优先级',
        status: '状态',
        creator: '创建人',
        assignee: '当前处理人',
        affectedVersion: '发现问题版本',
        resolvedVersion: '解决问题版本',
        createdAt: '创建时间',
        updatedAt: '更新时间',
        resolvedAt: '解决时间',
      },
      status: {
        NEW: '新建',
        ASSIGNED: '已分派',
        IN_PROGRESS: '处理中',
        RESOLVED: '待验证',
        VERIFIED: '已验证',
        CLOSED: '已关闭',
      },
      priority: {
        LOW: '低',
        MEDIUM: '中',
        HIGH: '高',
        CRITICAL: '紧急',
      },
    },
  },
  en: {
    app: {
      name: 'Issue Tracking System',
      center: 'Issue Center',
      workspace: 'Workspace',
      logout: 'Sign out',
      language: '中文',
    },
    nav: {
      tickets: 'Tickets',
      createTicket: 'Create Ticket',
      ticketDetail: 'Ticket Details',
      users: 'Users & Access',
      versions: 'Version Management',
      login: 'Sign In',
      register: 'Register',
    },
    ticket: {
      currentView: 'Current View',
      resultCount: 'Results',
      new: 'New Ticket',
      searchPlaceholder: 'Search ID, title, or description',
      allStatus: 'All statuses',
      allPriority: 'All priorities',
      search: 'Search',
      columns: 'Columns',
      scope: {
        label: 'Scope',
        ALL: 'All tickets',
        RELATED: 'Related to me',
        MY_CREATED: 'Created by me',
        CREATED_BY: 'Created by user',
      },
      creatorPlaceholder: 'Select creator',
      unassigned: 'Unassigned',
      empty: '-',
      column: {
        ticketNo: 'ID',
        title: 'Title',
        category: 'Category',
        priority: 'Priority',
        status: 'Status',
        creator: 'Creator',
        assignee: 'Current Assignee',
        affectedVersion: 'Affected Version',
        resolvedVersion: 'Resolved Version',
        createdAt: 'Created At',
        updatedAt: 'Updated At',
        resolvedAt: 'Resolved At',
      },
      status: {
        NEW: 'New',
        ASSIGNED: 'Assigned',
        IN_PROGRESS: 'In Progress',
        RESOLVED: 'Pending Verification',
        VERIFIED: 'Verified',
        CLOSED: 'Closed',
      },
      priority: {
        LOW: 'Low',
        MEDIUM: 'Medium',
        HIGH: 'High',
        CRITICAL: 'Critical',
      },
    },
  },
} as const

const locale = ref<AppLocale>(initialLocale)

function resolveMessage(targetLocale: AppLocale, path: string): string | undefined {
  let current: unknown = messages[targetLocale]
  for (const key of path.split('.')) {
    if (!current || typeof current !== 'object' || !(key in current)) return undefined
    current = (current as Record<string, unknown>)[key]
  }
  return typeof current === 'string' ? current : undefined
}

export function useAppI18n() {
  function t(path: string) {
    return resolveMessage(locale.value, path)
      || resolveMessage('zh-CN', path)
      || path
  }
  return { locale: readonly(locale), t }
}

export function setAppLocale(locale: AppLocale) {
  currentLocale(locale)
}

function currentLocale(value: AppLocale) {
  locale.value = value
  localStorage.setItem('issue-tracker-locale', value)
  document.documentElement.lang = value
}

document.documentElement.lang = initialLocale
