import { useCallback, useEffect, useState } from 'react'
import { getErrorMessage } from '../api/errors'
import type { Role } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { INBOX_CHANGED_EVENT, loadInbox, markInboxRead } from '../components/headerInboxData'
import { Button, EmptyState, ErrorState, Notice, PageHeader, Spinner } from '../components/ui'
import { useResource } from '../hooks/useResource'
import { MEMBER_AREA, STAFF_AREAS } from '../layouts/navigation'
import { formatDateTime } from '../utils/dateDisplay'

/** "View all" target of the header inbox for Member, Manager and Coach. Shows only the caller's own notifications. */
export function NotificationsPage() {
  const { role } = useAuth()
  if (!role || role === 'RECEPTIONIST') return null
  return <NotificationsList role={role} />
}

function NotificationsList({ role }: { role: Role }) {
  const load = useCallback((signal: AbortSignal) => loadInbox(role, signal), [role])
  const [resource, reload] = useResource(load)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    window.addEventListener(INBOX_CHANGED_EVENT, reload)
    return () => window.removeEventListener(INBOX_CHANGED_EVENT, reload)
  }, [reload])
  const markRead = async (id: number) => {
    setError(null)
    try { await markInboxRead(role, id); window.dispatchEvent(new Event(INBOX_CHANGED_EVENT)) } catch (failure) { setError(getErrorMessage(failure)) }
  }
  return (
    <div className="page notifications-page">
      <PageHeader
        eyebrow={(role === 'MEMBER' ? MEMBER_AREA : STAFF_AREAS[role]).workspaceLabel}
        title="Inbox"
        description="Your own notifications, newest first. Marking one as read does not complete any task."
      />
      {error && <Notice tone="danger">{error}</Notice>}
      {resource.status === 'loading' && <Spinner label="Loading notifications" />}
      {resource.status === 'error' && <ErrorState error={resource.error} onRetry={reload} />}
      {resource.status === 'ready' && (resource.data.entries.length === 0
        ? <EmptyState title="No notifications yet" />
        : <ul className="inbox-list">{resource.data.entries.map(entry => (
          <li key={entry.id} className={'inbox-item' + (entry.read ? '' : ' inbox-item--unread')}>
            <p><strong>{entry.title}</strong></p>
            <p>{entry.content}</p>
            <p className="inbox-item__meta">{formatDateTime(entry.createdAt)}{entry.read ? ' · Read' : ' · Unread'}</p>
            {!entry.read && <Button variant="secondary" size="sm" onClick={() => void markRead(entry.id)}>Mark as read</Button>}
          </li>
        ))}</ul>)}
    </div>
  )
}
