import { memberNotificationApi, staffNotificationApi, type OwnNotice } from '../api/notificationApi'
import type { Role } from '../api/types'
import { receptionInboxApi } from '../api/receptionInboxApi'

/** Header inbox refresh interval while a page is open. */
export const INBOX_POLL_MS = 30000
/** Fired by any inbox screen after it loads or changes, so the header badge never shows a stale count. */
export const INBOX_CHANGED_EVENT = 'sportify:reception-inbox-changed'

export interface InboxEntry { id: number; title: string; content: string; createdAt: string; read: boolean; link: string | null }
export interface InboxFeed { entries: InboxEntry[]; unread: number }

// Anh xa duong dan dieu huong tu loai thong bao hoac event_key cho Member, Coach va Manager
function noticeLink(role: Role, notice: OwnNotice): string | null {
  if (role === 'COACH') {
    if (notice.type === 'COACH_SESSION_ASSIGNED' || notice.type === 'COACH_SESSION_UNASSIGNED'
      || notice.type === 'SESSION_CHANGED' || notice.type === 'SESSION_CANCELLED') {
      const date = notice.currentSchedule?.date
      return date ? `/coach?week=${encodeURIComponent(date)}` : '/coach'
    }
  }
  if (role === 'MANAGER') {
    if (notice.type === 'PAYMENT_EXCEPTION' || notice.eventKey?.startsWith('PAYMENT_EXCEPTION:')) {
      return '/manager/transactions'
    }
  }
  return null
}

const fromNotices = (role: Role, notices: OwnNotice[]): InboxFeed => ({
  entries: notices.map(notice => ({
    id: notice.id,
    title: notice.title,
    content: notice.content,
    createdAt: notice.createdAt,
    read: notice.read,
    link: noticeLink(role, notice),
  })),
  unread: notices.filter(notice => !notice.read).length,
})

/** Each role reads only its own notification API; nothing is shared across roles. */
export function loadInbox(role: Role, signal?: AbortSignal): Promise<InboxFeed> {
  if (role === 'RECEPTIONIST') {
    return receptionInboxApi.get(true, signal).then(inbox => ({
      entries: inbox.items.map(item => ({ id: item.id, title: item.title, content: item.content, createdAt: item.createdAt, read: item.read, link: item.link || null })),
      unread: inbox.unreadOpen,
    }))
  }
  if (role === 'MEMBER') return memberNotificationApi.get(signal).then(inbox => fromNotices(role, inbox.notices ?? []))
  return staffNotificationApi.get(signal).then(notices => fromNotices(role, notices ?? []))
}

/** Presentation state only: marking read never resolves the underlying task. */
export function markInboxRead(role: Role, id: number): Promise<unknown> {
  if (role === 'RECEPTIONIST') return receptionInboxApi.markRead(id)
  if (role === 'MEMBER') return memberNotificationApi.markRead(id)
  return staffNotificationApi.markRead(id)
}

export const INBOX_PATHS: Record<Role, string> = {
  RECEPTIONIST: '/reception/inbox',
  MEMBER: '/member/notifications',
  MANAGER: '/manager/notifications',
  COACH: '/coach/notifications',
}
