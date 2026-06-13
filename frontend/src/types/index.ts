export type TicketStatus =
  | 'NEW'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'VERIFIED'
  | 'CLOSED'

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ProductVersionStatus = 'PLANNED' | 'ACTIVE' | 'RELEASED' | 'ARCHIVED'
export type TicketScope = 'ALL' | 'RELATED' | 'MY_CREATED' | 'CREATED_BY'

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

export interface VersionOption {
  id: number
  versionNo: string
  name: string
  status: ProductVersionStatus
  parentId?: number | null
  enabled: boolean
  depth: number
  pathLabel: string
}

export interface VersionView extends VersionOption {
  description?: string
  parentVersionNo?: string
  releaseDate?: string
  createdAt: string
  updatedAt: string
}

export interface VersionSummary {
  id: number
  versionNo: string
  name: string
}

export interface TicketAttachment {
  id: number
  originalName: string
  contentType?: string
  fileSize: number
  uploader: UserSummary
  createdAt: string
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
  affectedVersion: VersionSummary
  resolvedVersion?: VersionSummary
  version: number
  createdAt: string
  updatedAt: string
  resolvedAt?: string
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
  attachments: TicketAttachment[]
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
