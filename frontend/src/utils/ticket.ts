import type { TicketPriority, TicketStatus } from '@/types'

export const statusLabels: Record<TicketStatus, string> = {
  NEW: '待分派',
  ASSIGNED: '已分派',
  IN_PROGRESS: '处理中',
  RESOLVED: '待验证',
  VERIFIED: '已验证',
  CLOSED: '已关闭',
}

export const statusTypes: Record<TicketStatus, 'info' | 'primary' | 'warning' | 'success' | 'danger'> = {
  NEW: 'info',
  ASSIGNED: 'primary',
  IN_PROGRESS: 'warning',
  RESOLVED: 'warning',
  VERIFIED: 'success',
  CLOSED: 'info',
}

export const priorityLabels: Record<TicketPriority, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '紧急',
}

export const priorityTypes: Record<TicketPriority, 'info' | 'primary' | 'warning' | 'danger'> = {
  LOW: 'info',
  MEDIUM: 'primary',
  HIGH: 'warning',
  CRITICAL: 'danger',
}

export const transitionActionLabels: Record<string, string> = {
  CREATE: '创建',
  UPDATE: '更新',
  ASSIGN: '分派',
  START: '开始处理',
  RESOLVE: '提交解决',
  VERIFY_PASS: '验证通过',
  VERIFY_REJECT: '验证驳回',
  CLOSE: '关闭',
}

export const transitionActionTypes: Record<string, 'info' | 'primary' | 'warning' | 'success' | 'danger'> = {
  CREATE: 'primary',
  UPDATE: 'info',
  ASSIGN: 'primary',
  START: 'warning',
  RESOLVE: 'success',
  VERIFY_PASS: 'success',
  VERIFY_REJECT: 'danger',
  CLOSE: 'info',
}

export function transitionActionLabel(action: string) {
  return transitionActionLabels[action] || '流程操作'
}
