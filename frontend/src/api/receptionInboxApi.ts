import { api } from './client'

/** Một tác vụ của lễ tân luôn thuộc payment cần kiểm tra hoặc yêu cầu hỗ trợ. */
export type InboxSourceType = 'PAYMENT_EXCEPTION' | 'SUPPORT_REQUEST' | 'UNKNOWN'
export interface InboxItem {
  id: number
  title: string
  content: string
  createdAt: string
  read: boolean
  sourceType: InboxSourceType
  sourceId: number
  sourceStatus: string
  open: boolean
  link: string
}

export interface ReceptionInbox { items: InboxItem[]; unreadOpen: number; open: number }

/** Trạng thái đọc và trạng thái hoàn tất công việc là hai thông tin độc lập. */
export const receptionInboxApi = {
  get: (openOnly: boolean, signal?: AbortSignal) => api.get<ReceptionInbox>('/reception/inbox', { signal, query: { openOnly } }),
  markRead: (id: number) => api.put<InboxItem>(`/reception/inbox/${id}/read`),
}
