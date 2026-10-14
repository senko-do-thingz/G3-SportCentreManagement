import { api } from './client'
import { ApiError } from './errors'
import { normalizePage } from './page'
import { isManagerPreviewSession } from './managerPreviewSession'
import type { PageResponse } from './types'
import type { ActivityLog, ActivityQuery } from '../types/manager'

export const ACTIVITY_DEPENDENCY = 'Activity history is waiting for the BE-04 API contract, including its actions and date filters.'

export const managerActivityLogsApi = {
  async list(query: ActivityQuery, signal?: AbortSignal): Promise<PageResponse<ActivityLog>> {
    if (!isManagerPreviewSession()) throw new ApiError(503, ACTIVITY_DEPENDENCY)
    return normalizePage(await api.get<PageResponse<ActivityLog>>('/manager/activity-logs', { query: { ...query }, signal }))
  },
}
