import { api } from './client'

export interface NoticeSchedule {
  id: number
  code: string
  className: string | null
  date: string
  startTime: string
  endTime: string
  coachName: string | null
  facilityName: string | null
  status: string
  revision: number
}

/** One notice from the signed-in user's own notification rows. `read` is presentation state only. */
export interface OwnNotice {
  id: number
  title: string
  content: string
  type: string
  read: boolean
  createdAt: string
  eventKey?: string | null
  originRevision?: number | null
  currentSchedule?: NoticeSchedule | null
}

export interface MemberInbox { notices: OwnNotice[] }

/** Member: existing operational notification inbox. */
export const memberNotificationApi = {
  get: (signal?: AbortSignal) => api.get<MemberInbox>('/member/notifications', { signal }),
  markRead: (id: number) => api.put<void>(`/member/notifications/${id}/read`),
}

/** Manager and Coach: the caller's own notifications only. */
export const staffNotificationApi = {
  get: (signal?: AbortSignal) => api.get<OwnNotice[]>('/staff/notifications', { signal }),
  markRead: (id: number) => api.put<void>(`/staff/notifications/${id}/read`),
}
