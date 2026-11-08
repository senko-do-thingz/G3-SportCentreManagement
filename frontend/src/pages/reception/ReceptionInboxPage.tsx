import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getErrorMessage } from '../../api/errors'
import { receptionInboxApi, type InboxItem } from '../../api/walletApi'
import { INBOX_CHANGED_EVENT, INBOX_POLL_MS } from '../../components/InboxBadge'
import { useResource } from '../../hooks/useResource'
import { Badge, Button, Card, EmptyState, ErrorState, Notice, PageHeader, Select, Spinner, buttonClassName } from '../../components/ui'
import { formatDateTime } from '../../utils/dateDisplay'

const SOURCE_LABELS: Record<InboxItem['sourceType'], string> = {
  WALLET_TOPUP: 'Wallet top-up',
  WALLET_WITHDRAWAL: 'Wallet withdrawal',
  PAYMENT_EXCEPTION: 'Payment exception',
  SUPPORT_REQUEST: 'Support request',
  UNKNOWN: 'Task',
}

/**
 * MW-07: actionable work for reception. "Mark as read" only changes this list; the task stays open until it is handled
 * on its own screen, and its state below is always read live from that task.
 */
export function ReceptionInboxPage() {
  const [filter, setFilter] = useState('open')
  const load = useCallback((signal: AbortSignal) => receptionInboxApi.get(filter === 'open', signal), [filter])
  const [resource, reload] = useResource(load)
  const [error, setError] = useState<string | null>(null)
  const loadedAt = resource.status === 'ready' ? resource.data : null
  useEffect(() => {
    if (loadedAt) window.dispatchEvent(new Event(INBOX_CHANGED_EVENT))
  }, [loadedAt])
  useEffect(() => {
    const timer = window.setInterval(reload, INBOX_POLL_MS)
    return () => window.clearInterval(timer)
  }, [reload])

  const markRead = async (id: number) => {
    setError(null)
    try { await receptionInboxApi.markRead(id); reload() } catch (failure) { setError(getErrorMessage(failure)) }
  }

  return (
    <div className="page">
      <PageHeader eyebrow="Front desk" title="Inbox" description="Tasks that need reception. Reading a notice does not complete the task." />
      <Card
        title={resource.status === 'ready' ? `${resource.data.open} open tasks, ${resource.data.unreadOpen} unread` : 'Tasks'}
        actions={<Button variant="secondary" size="sm" onClick={reload}>Refresh</Button>}
      >
        <Select label="Show" value={filter} onChange={(e) => setFilter(e.target.value)}
          options={[{ value: 'open', label: 'Open tasks' }, { value: 'all', label: 'All notices (including resolved)' }]} />
        {error && <Notice tone="danger">{error}</Notice>}
        {resource.status === 'loading' && <Spinner label="Loading inbox" />}
        {resource.status === 'error' && <ErrorState title="We could not load the inbox" error={resource.error} onRetry={reload} />}
        {resource.status === 'ready' && (resource.data.items.length === 0
          ? <EmptyState title="Nothing to do" description="New top-ups, withdrawals, payment exceptions and support requests appear here." />
          : (
            <ul className="inbox-list">
              {resource.data.items.map((item) => (
                <li key={item.id} className={'inbox-item' + (item.read ? '' : ' inbox-item--unread')}>
                  <div className="inbox-item__head">
                    <Badge tone="info">{SOURCE_LABELS[item.sourceType]}</Badge>
                    <strong>{item.title}</strong>
                    {!item.read && <Badge tone="warning">Unread</Badge>}
                    <Badge tone={item.open ? 'warning' : 'success'}>{item.open ? `Open - ${item.sourceStatus}` : `Resolved - ${item.sourceStatus}`}</Badge>
                  </div>
                  <p className="health-note">{item.content}</p>
                  <p className="health-meta">{formatDateTime(item.createdAt)}</p>
                  <div className="profile-form__actions">
                    <Link className={buttonClassName('primary', 'sm')} to={item.link}>Open task</Link>
                    {!item.read && <Button variant="secondary" size="sm" onClick={() => markRead(item.id)}>Mark as read</Button>}
                  </div>
                </li>
              ))}
            </ul>
          ))}
      </Card>
    </div>
  )
}
