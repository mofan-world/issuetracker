export type TicketStatus =
  | 'NEW'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'VERIFIED'
  | 'CLOSED'

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface UserProfile {
  id: number
  username: string
  email: string
  displayName: string
  roles: string[]
  permissions: string[]
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  accessTokenExpiresAt: string
  user: UserProfile
}

export interface UserSummary {
  id: number
  username: string
  displayName: string
}

export interface TicketSummary {
  id: number
  ticketNo: string
  title: string
  category: string
  priority: TicketPriority
  status: TicketStatus
  creator: UserSummary
  assignee?: UserSummary
  version: number
  createdAt: string
  updatedAt: string
}

export interface TicketTransition {
  id: number
  fromStatus?: TicketStatus
  toStatus: TicketStatus
  action: string
  comment?: string
  operator: UserSummary
  createdAt: string
}

export interface TicketDetail extends TicketSummary {
  description: string
  resolution?: string
  resolvedAt?: string
  verifiedAt?: string
  closedAt?: string
  transitions: TicketTransition[]
}

export interface PageResult<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ApiError {
  code: string
  message: string
  fieldErrors?: Record<string, string>
}

