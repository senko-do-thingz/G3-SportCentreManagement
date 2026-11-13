import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../api/errors'
import { ACTIVITY_ACTIONS, managerActivityLogsApi } from '../../api/managerActivityLogsApi'
import { Button, CalendarDateField, Card, Input, Modal, Select, Table, type TableColumn, StatIcon } from '../../components/ui'
import { humanizeCode } from '../../utils/format'
import type { ActivityLog, ManagerUser } from '../../types/manager'
import { ActorPickerModal } from './ActorPickerModal'
import { DetailList, ManagerError, PreviewNotice } from './ManagerShared'
import { formatManagerDateTime } from './managerFormat'
import { useDebounced, useResource } from './useManagerTools'
import '../../styles/managerActivity.css'

const ACTIVITY_PAGE_SIZE = 20

export function ActivityLogPage() {
  const [search, setSearch] = useState('')
  const [action, setAction] = useState('')
  const debounced = useDebounced(search)
  const [actor, setActor] = useState<ManagerUser | null>(null)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [fromValid, setFromValid] = useState(true)
  const [toValid, setToValid] = useState(true)
  const [page, setPage] = useState(0)
  const [picking, setPicking] = useState(false)
  const [selected, setSelected] = useState<ActivityLog | null>(null)
  const [filtersOpen, setFiltersOpen] = useState(() => typeof window === 'undefined' || !window.matchMedia
    ? true
    : window.matchMedia('(min-width: 901px)').matches)
  const invalidRange = Boolean(from && to && from > to)
  const invalidDateFilter = !fromValid || !toValid
  useEffect(() => {
    const media = window.matchMedia?.('(min-width: 901px)')
    if (!media) return
    const sync = () => setFiltersOpen(media.matches)
    sync()
    media.addEventListener('change', sync)
    return () => media.removeEventListener('change', sync)
  }, [])
  const load = useCallback((signal: AbortSignal) => {
    if (from && to && from > to) return Promise.reject(new ApiError(400, 'End date must be on or after the start date.'))
    return managerActivityLogsApi.list({ page, size: ACTIVITY_PAGE_SIZE, actorId: actor ? String(actor.id) : undefined, action, from, to, search: debounced.trim() }, signal)
  }, [page, actor, debounced, action, from, to])
  const [resource, reload] = useResource(load)
  const lastValidPage = resource.status === 'ready'
    ? Math.max(0, Math.ceil(resource.data.totalElements / ACTIVITY_PAGE_SIZE) - 1)
    : page
  const pageOutOfRange = resource.status === 'ready' && page > lastValidPage
  if (pageOutOfRange) setPage(lastValidPage)
  const ready = resource.status === 'ready' && search === debounced && !invalidRange && !invalidDateFilter && !pageOutOfRange

  const totalEvents = ready ? String(resource.data.totalElements).padStart(2, '0') : '--'
  const pageEvents = ready ? String(resource.data.content.length).padStart(2, '0') : '--'
  const activeActors = ready ? String(new Set(resource.data.content.map((c) => c.actorName)).size).padStart(2, '0') : '--'

  const activeFilterCount = [search.trim(), action, actor, from, to].filter(Boolean).length
  const filterScopeLabel = activeFilterCount > 0 ? `${activeFilterCount} Active` : 'All activity'
  const filtered = activeFilterCount > 0
  const clearFilters = () => { setSearch(''); setAction(''); setActor(null); setFrom(''); setTo(''); setPage(0) }
  const columns: TableColumn<ActivityLog>[] = [
    { key: 'createdAt', header: 'When', width: '19%', render: (log) => <div className="manager-activity__cell"><time dateTime={log.createdAt}>{formatManagerDateTime(log.createdAt)}</time></div> },
    { key: 'action', header: 'Activity', width: '27%', render: (log) => <div className="manager-activity__cell"><span className="manager-activity__primary">{humanizeCode(log.action)}</span><span className="manager-activity__secondary">{log.actorName || 'System / unavailable actor'}{log.actorRole ? ', ' + humanizeCode(log.actorRole) : ''}</span></div> },
    { key: 'entityId', header: 'Target', width: '33%', render: (log) => <div className="manager-activity__cell"><span className="manager-activity__primary">{log.entityType ? humanizeCode(log.entityType) : 'Record'}{log.entityId !== null ? ' #' + log.entityId : ''}</span><span className="manager-activity__secondary">{log.shortDetail || '-'}</span></div> },
    { key: 'details', header: 'Details', width: '21%', render: (log) => <Button size="sm" variant="secondary" aria-label={'View details for ' + humanizeCode(log.action) + ' by ' + (log.actorName || 'system')} onClick={() => setSelected(log)}>View details</Button> },
  ]
  return <div className="page manager-page manager-activity manager-concept-page">
    <div className="concept-header">
      <div className="concept-header__main">
        <div className="concept-eyebrow">MANAGER WORKSPACE</div>
        <h1 className="concept-title">Activity Log</h1>
        <p className="concept-sub">See who changed what and when across your center with complete audit integrity.</p>
      </div>
    </div>

    <div className="concept-hero">
      <div>
        <small>AUDIT TRAIL</small>
        <h2>TRACEABLE ACTIONS. <span className="concept-goldtext">TOTAL ACCOUNTABILITY.</span></h2>
        <p>Monitor changes across users, packages, classes, and schedules.</p>
      </div>
      <Button variant="secondary" className="concept-btn concept-btn--gold" disabled={invalidDateFilter} onClick={reload}>Refresh</Button>
    </div>

    <div className="concept-stats">
      <div className="concept-stat">
        <StatIcon name="list" />
        <div>
          <strong>{totalEvents}</strong>
          <small>Matching events</small>
        </div>
      </div>
      <div className="concept-stat">
        <StatIcon name="users" />
        <div>
          <strong>{activeActors}</strong>
          <small>Actors on this page</small>
        </div>
      </div>
      <div className="concept-stat">
        <StatIcon name="rows" />
        <div>
          <strong>{pageEvents}</strong>
          <small>Events on this page</small>
        </div>
      </div>
      <div className="concept-stat">
        <StatIcon name="filter" />
        <div>
          <strong>{filterScopeLabel}</strong>
          <small>Filter scope</small>
        </div>
      </div>
    </div>

    <PreviewNotice />
    <Card className="manager-activity__card" title="Activity history" actions={ready ? <span className="manager-activity__count">{resource.data.totalElements.toLocaleString()} {resource.data.totalElements === 1 ? 'event' : 'events'}</span> : undefined}>
      <div className="manager-activity__toolbar">
        <Input className="manager-activity__search" label="Search activity" type="search" maxLength={200} placeholder="Actor, action or target" value={search} disabled={invalidDateFilter} onChange={(event) => { setSearch(event.target.value); setPage(0) }} />
        <div className="manager-activity__filter-actions">
          <Button variant="secondary" aria-controls="manager-activity-filters" aria-expanded={filtersOpen} onClick={() => setFiltersOpen((open) => !open)}>
            Filters{activeFilterCount > 0 && <span className="manager-activity__filter-count">{activeFilterCount}</span>}
          </Button>
          {filtered && <Button variant="ghost" onClick={clearFilters}>Clear filters</Button>}
        </div>
      </div>
      <div id="manager-activity-filters" className="manager-activity__filters" hidden={!filtersOpen}>
        <div className="manager-activity__filters-grid">
          <div className="field">
            <div className="field__label-row">
              <span className="field__label" id="actor-label">Performed by</span>
            </div>
            <Button variant="secondary" aria-labelledby="actor-label actor-value" disabled={invalidDateFilter} onClick={() => setPicking(true)}>
              <span id="actor-value">{actor?.fullName ?? 'All actors'}</span>
            </Button>
          </div>
          <Select label="Action" placeholder="All actions" value={action} options={ACTIVITY_ACTIONS.map((code) => ({ value: code, label: humanizeCode(code) }))} disabled={invalidDateFilter} onChange={(event) => { setAction(event.target.value); setPage(0) }} />
          <CalendarDateField label="From date" value={from} max={to || undefined} onValidityChange={setFromValid} onChange={(value, valid) => { if (valid === false) return; setFrom(value); setPage(0) }} />
          <CalendarDateField label="To date" value={to} min={from || undefined} error={invalidRange ? 'End date must be on or after the start date.' : undefined} onValidityChange={setToValid} onChange={(value, valid) => { if (valid === false) return; setTo(value); setPage(0) }} />
        </div>
      </div>
      {!invalidDateFilter && (resource.status === 'error' && search === debounced && !invalidRange ? <ManagerError error={resource.error} onRetry={reload} /> : !invalidRange && <div className="manager-activity__table"><Table columns={columns} rows={ready ? resource.data.content : []} rowKey={(log) => log.id} loading={!ready} caption="Activity history" scrollLabel="Activity history table. Scroll horizontally to view all columns." emptyTitle={filtered ? 'No matching activity' : 'No activity yet'} emptyDescription={filtered ? 'Clear filters or choose a different date range.' : 'Recorded center activity will appear here.'} pagination={ready ? { page, pageSize: ACTIVITY_PAGE_SIZE, totalItems: resource.data.totalElements, onPageChange: setPage } : undefined} /></div>)}
      <p className="manager-activity__footnote">Activity records are read-only. Times use the center timezone (Asia/Ho_Chi_Minh). Only the safe fields returned by the activity service are shown.</p>
    </Card>
    {picking && <ActorPickerModal onClose={() => setPicking(false)} onSelect={(selectedActor) => { setActor(selectedActor); setPicking(false); setPage(0) }} />}
    {selected && <Modal open title="Activity details" description="Read-only audit event details from verified safe fields." onClose={() => setSelected(null)} footer={<Button variant="secondary" onClick={() => setSelected(null)}>Close</Button>}>
      <div className="manager-activity__detail">
        <h3>Recorded event</h3>
        <p className="manager-detail-text">{selected.shortDetail || humanizeCode(selected.action)}. This action was recorded in the Sportify audit trail.</p>
        <DetailList items={[
          { label: 'Audit event ID', value: selected.id },
          { label: 'Recorded at', value: formatManagerDateTime(selected.createdAt) },
          { label: 'Performed by', value: selected.actorName || 'System / unavailable actor' },
          { label: 'Actor account ID', value: selected.actorId ?? 'System / unavailable' },
          { label: 'Actor role', value: selected.actorRole ? humanizeCode(selected.actorRole) : 'Not recorded' },
          { label: 'Action', value: humanizeCode(selected.action) },
          { label: 'Audit action code', value: selected.action },
          { label: 'Target record type', value: selected.entityType ? humanizeCode(selected.entityType) : 'Not recorded' },
          { label: 'Target record ID', value: selected.entityId ?? 'Not recorded' },
        ]} />
        <p className="manager-activity__footnote">Sensitive audit payloads and unverified before/after values are intentionally excluded. These are all the verified details available for this event.</p>
      </div>
    </Modal>}
  </div>
}
