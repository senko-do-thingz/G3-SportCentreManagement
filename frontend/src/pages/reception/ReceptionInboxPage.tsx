import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getErrorMessage } from '../../api/errors'
import { receptionInboxApi, type InboxItem } from '../../api/walletApi'
import { INBOX_CHANGED_EVENT, INBOX_POLL_MS } from '../../components/headerInboxData'
import { useResource } from '../../hooks/useResource'
import { Badge, Button, Card, EmptyState, ErrorState, Notice, PageHeader, Select, Spinner, buttonClassName } from '../../components/ui'
import { formatDateTime } from '../../utils/dateDisplay'
import { ReceptionCallout, ReceptionHero, ReceptionScreen, ReceptionStat, ReceptionStats } from './ReceptionConcept'

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

  const data = resource.status === 'ready' ? resource.data : null
  return (
    <ReceptionScreen className="rcp-inbox">
      <PageHeader eyebrow="Receptionist workspace" title="Action inbox" description="Tasks that need reception. Reading a notice does not complete the task." />
      <ReceptionHero
        kicker="Front desk operations"
        title="Action inbox."
        highlight="Open the right workflow."
        description="Payment exceptions and support requests that need reception. Each task is completed on its own screen."
      />
      {data && (
        <ReceptionStats label="Inbox summary">
          <ReceptionStat mark="O" label="Open tasks (newest 100 notices)" value={data.open} tone={data.open > 0 ? 'warning' : 'success'} />
          <ReceptionStat mark="U" label="Unread open (newest 100 notices)" value={data.unreadOpen} />
          <ReceptionStat mark="L" label="Items shown" value={data.items.length} />
          <ReceptionStat mark="R" label="Checks for new tasks" value={`${Math.round(INBOX_POLL_MS / 1000)}s`} />
        </ReceptionStats>
      )}
      <Card
        title={data ? `${data.open} open tasks, ${data.unreadOpen} unread` : 'Tasks'}
        actions={<Button variant="secondary" size="sm" onClick={reload}>Refresh</Button>}
      >
        <div className="rcp-toolbar">
          <Select label="Show" value={filter} onChange={(e) => setFilter(e.target.value)}
            options={[{ value: 'open', label: 'Open tasks' }, { value: 'all', label: 'All notices (including resolved)' }]} />
        </div>
        {error && <Notice tone="danger">{error}</Notice>}
        {resource.status === 'loading' && <Spinner label="Loading inbox" />}
        {resource.status === 'error' && <ErrorState title="We could not load the inbox" error={resource.error} onRetry={reload} />}
        {data && (data.items.length === 0
          ? <EmptyState title="Nothing to do" description="New payment exceptions and support requests appear here." />
          : (
            <ul className="rcp-list">
              {data.items.map((item) => (
                <li key={item.id} className={'rcp-row' + (!item.open ? ' rcp-row--muted' : item.read ? '' : ' rcp-row--attention')}>
                  <div>
                    <div className="rcp-row__meta">
                      <strong>{SOURCE_LABELS[item.sourceType]}</strong>
                      <span>{formatDateTime(item.createdAt)}</span>
                      {!item.read && <Badge tone="warning">Unread</Badge>}
                      <Badge tone={item.open ? 'warning' : 'success'}>{item.open ? `Open - ${item.sourceStatus}` : `Resolved - ${item.sourceStatus}`}</Badge>
                    </div>
                    <p className="rcp-row__title">{item.title}</p>
                    <p className="rcp-row__body">{item.content}</p>
                  </div>
                  <div className="rcp-row__actions">
                    <Link className={buttonClassName('primary', 'sm')} to={item.link}>Open task</Link>
                    {!item.read && <Button variant="secondary" size="sm" onClick={() => markRead(item.id)}>Mark as read</Button>}
                  </div>
                </li>
              ))}
            </ul>
          ))}
        <ReceptionCallout>
          <strong>Important:</strong> Mark as read only updates the notice. A payment or support request stays open until it is handled in its own workflow.
        </ReceptionCallout>
      </Card>
    </ReceptionScreen>
  )
}
