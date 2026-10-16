import { useCallback, useState } from 'react'
import { ApiError } from '../../api/errors'
import { ACTIVITY_ACTIONS, managerActivityLogsApi } from '../../api/managerActivityLogsApi'
import { Button, Card, Input, Modal, PageHeader, Select, Table, type TableColumn } from '../../components/ui'
import { humanizeCode } from '../../utils/format'
import type { ActivityLog, ManagerUser } from '../../types/manager'
import { ActorPickerModal } from './ActorPickerModal'
import { DetailList, ManagerError, PreviewNotice } from './ManagerShared'
import { formatManagerDateTime } from './managerFormat'
import { useDebounced, useResource } from './useManagerTools'

export function ActivityLogPage() {
  const [search, setSearch] = useState('')
  const [action, setAction] = useState('')
  const debounced = useDebounced(search)
  const [actor, setActor] = useState<ManagerUser | null>(null)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [picking, setPicking] = useState(false)
  const [selected, setSelected] = useState<ActivityLog | null>(null)
  const invalidRange = Boolean(from && to && from > to)
  const load = useCallback((signal: AbortSignal) => {
    if (from && to && from > to) return Promise.reject(new ApiError(400, 'End date must be on or after the start date.'))
    return managerActivityLogsApi.list({ page, size: 20, actorId: actor ? String(actor.id) : undefined, action, from, to, search: debounced.trim() }, signal)
  }, [page, actor, debounced, action, from, to])
  const [resource, reload] = useResource(load)
  const ready = resource.status === 'ready' && search === debounced
  const filtered = Boolean(search || action || actor || from || to)
  const columns: TableColumn<ActivityLog>[] = [
    { key: 'createdAt', header: 'Time', render: (log) => <span className="manager-nowrap">{formatManagerDateTime(log.createdAt)}</span> },
    { key: 'actorName', header: 'Performed by', render: (log) => <div className="manager-cell"><span className="manager-cell-primary">{log.actorName ?? 'Not provided'}</span><span>{log.actorRole ? humanizeCode(log.actorRole) : '-'}</span></div> },
    { key: 'action', header: 'Action', render: (log) => humanizeCode(log.action) },
    { key: 'entityId', header: 'Reference', render: (log) => <div className="manager-cell"><span className="manager-cell-primary">{log.entityType ? humanizeCode(log.entityType) : 'Record'}{log.entityId !== null ? ` #${log.entityId}` : ''}</span><span>{log.shortDetail || '-'}</span></div> },
    { key: 'details', header: 'Details', render: (log) => <Button size="sm" variant="secondary" onClick={() => setSelected(log)}>View details</Button> },
  ]
  return <div className="page manager-page">
    <PageHeader eyebrow="Center management" title="Activity Log" description="Review important changes and front desk activity." actions={<Button variant="secondary" onClick={reload}>Refresh</Button>} />
    <PreviewNotice />
    <Card title="Activity history">
      <div className="manager-filters manager-filters--activity">
        <Input label="Search activity" type="search" placeholder="Search actor, action or reference" value={search} onChange={(event) => { setSearch(event.target.value); setPage(0) }} />
        <div className="field"><span className="field__label" id="actor-label">Performed by</span><Button variant="secondary" aria-labelledby="actor-label actor-value" onClick={() => setPicking(true)}><span id="actor-value">{actor?.fullName ?? 'All actors'}</span></Button></div>
        <Select label="Action" placeholder="All actions" value={action} options={ACTIVITY_ACTIONS.map((code) => ({ value: code, label: humanizeCode(code) }))} onChange={(event) => { setAction(event.target.value); setPage(0) }} />
        <Input label="From date" type="date" value={from} onChange={(event) => { setFrom(event.target.value); setPage(0) }} />
        <Input label="To date" type="date" value={to} error={invalidRange ? 'End date must be on or after the start date.' : undefined} onChange={(event) => { setTo(event.target.value); setPage(0) }} />
        <Button variant="ghost" onClick={() => { setSearch(''); setAction(''); setActor(null); setFrom(''); setTo(''); setPage(0) }}>Clear filters</Button>
      </div>
      {invalidRange ? <p className="notice notice--warning">Correct the date range to load activity.</p> : resource.status === 'error' ? <ManagerError error={resource.error} onRetry={reload} /> : <Table columns={columns} rows={ready ? resource.data.content : []} rowKey={(log) => log.id} loading={!ready} caption="Activity history" emptyTitle={filtered ? 'No matching activity' : 'No activity yet'} emptyDescription={filtered ? 'Clear filters or choose a different date range.' : 'Recorded center activity will appear here.'} pagination={ready ? { page, pageSize: 20, totalItems: resource.data.totalElements, onPageChange: setPage } : undefined} />}
      <p className="manager-footnote">Activity records are read-only. Times use the center timezone (Asia/Ho_Chi_Minh). The action filter includes only codes supported by the activity service.</p>
    </Card>
    {picking && <ActorPickerModal onClose={() => setPicking(false)} onSelect={(selectedActor) => { setActor(selectedActor); setPicking(false); setPage(0) }} />}
    {selected && <Modal open title="Activity details" description="Read-only record from activity history." onClose={() => setSelected(null)} footer={<Button variant="secondary" onClick={() => setSelected(null)}>Close</Button>}>
      <DetailList items={[{ label: 'Time', value: formatManagerDateTime(selected.createdAt) }, { label: 'Performed by', value: selected.actorName }, { label: 'Role', value: selected.actorRole ? humanizeCode(selected.actorRole) : '-' }, { label: 'Action code', value: selected.action }, { label: 'Target type', value: selected.entityType }, { label: 'Target ID', value: selected.entityId }]} />
      <p className="manager-detail-text">{selected.shortDetail}</p>
    </Modal>}
  </div>
}
