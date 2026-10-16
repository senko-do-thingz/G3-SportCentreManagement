import { api } from './client'
import { normalizePage } from './page'
import type { PageResponse } from './types'
import type { ActivityLog, ActivityQuery } from '../types/manager'

export const ACTIVITY_ACTIONS = [
  'REGISTER',
  'USER_CREATED',
  'USER_UPDATED',
  'USER_STATUS_CHANGED',
  'ROLE_UPDATED',
  'ROLE_PERMISSIONS_UPDATED',
  'LOGIN',
  'LOGIN_FAILED',
  'REFRESH_TOKEN',
  'PASSWORD_CHANGED',
  'PASSWORD_RESET_REQUESTED',
  'PASSWORD_RESET_COMPLETED',
  'TOKEN_REUSE_DETECTED',
  'MEMBERSHIP_REGISTERED',
  'MEMBERSHIP_RENEWED',
  'MEMBERSHIP_CANCELLED',
  'MEMBERSHIP_ACTIVATED',
  'MEMBERSHIP_ACTIVATED_BY_JOB',
  'MEMBERSHIP_EXPIRED_BY_JOB',
  'MEMBERSHIP_AUTO_CANCELLED',
  'MEMBER_CHECKED_IN',
  'PACKAGE_CREATED',
  'PACKAGE_UPDATED',
  'CARD_TIER_UPDATED',
  'CLASS_CREATED',
  'CLASS_UPDATED',
  'CLASS_STATUS_CHANGED',
  'SESSION_CREATED',
  'SESSION_UPDATED',
  'SESSION_CANCELLED',
  'BOOKING_CREATED',
  'BOOKING_CANCELLED',
  'PAYMENT_CONFIRMED',
  'PAYMENT_FAILED',
  'PAYMENT_AMOUNT_MISMATCH',
  'REFUND_COMPLETED',
] as const

export const managerActivityLogsApi = {
  async list(query: ActivityQuery, signal?: AbortSignal): Promise<PageResponse<ActivityLog>> {
    return normalizePage(await api.get<PageResponse<ActivityLog>>('/manager/activity-logs', { query: { ...query }, signal }))
  },
}
