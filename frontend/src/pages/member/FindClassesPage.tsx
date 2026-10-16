import { useCallback, useEffect, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'
import { memberSessionsApi } from '../../api/memberSessionsApi'
import { packagesApi } from '../../api/packagesApi'
import { Button, Card, EmptyState, Input, PageHeader, PageLoader, Pagination, Select } from '../../components/ui'
import { BrowseError } from './BrowseError'
import { MemberScheduleNav } from './MemberScheduleNav'
import { SessionCard } from './SessionCard'
import { browseToday } from './memberScheduleDates'
import { BROWSE_FORMATS, BROWSE_LEVELS, browseFilters, browseRangeError, updateBrowseParams } from './scheduleFilters'
import { useMemberResource } from './useMemberResource'

const loadSports = (signal: AbortSignal) => packagesApi.sports({ signal })
export function FindClassesPage() {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const [today] = useState(browseToday)
  const { sportId, format, level, coachId, from = today, to = today, page = 0 } = browseFilters(params, today)
  const rangeError = browseRangeError(from, to)
  const [sports, retrySports] = useMemberResource(loadSports)
  const loader = useCallback((signal: AbortSignal) => rangeError ? Promise.resolve({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }) : memberSessionsApi.list({ sportId, from, to, format, level, coachId, page, size: 20 }, signal), [rangeError, sportId, from, to, format, level, coachId, page])
  const [resource, reload] = useMemberResource(loader)
  const coachesLoader = useCallback(async (signal: AbortSignal) => {
    if (rangeError || format === 'SELF_TRAINING') return []
    const rows = await memberSessionsApi.all({ sportId, from, to, format, level }, signal)
    return [...new Map(rows.filter((row) => row.coachId !== null).map((row) => [row.coachId!, { value: String(row.coachId), label: row.coachName || `Coach #${row.coachId}` }])).values()].sort((a, b) => a.label.localeCompare(b.label))
  }, [rangeError, sportId, from, to, format, level])
  const [coaches, retryCoaches] = useMemberResource(coachesLoader)
  const coachOptions = coaches.status === 'ready' ? coaches.data : []
  const options = coachId && !coachOptions.some((option) => option.value === String(coachId)) ? [{ value: String(coachId), label: `Coach #${coachId} (selected)` }, ...coachOptions] : coachOptions
  const returnTo = location.pathname + location.search
  function change(key: string, value: string) { setParams((previous) => updateBrowseParams(previous, key, value)) }
  useEffect(() => {
    if (!rangeError && resource.status === 'ready' && page > 0 && page >= resource.data.totalPages) setParams((previous) => updateBrowseParams(previous, 'page', String(Math.max(0, resource.data.totalPages - 1))), { replace: true })
  }, [rangeError, resource, page, setParams])
  return <div className="page browse-page">
    <MemberScheduleNav />
    <PageHeader eyebrow="Member / Classes & schedule" title="Find Classes" description="Explore sessions by sport, date, training format, level and coach." />
    <Card title="Find your next session">
      <div className="browse-filters">
        <Select label="Sport" placeholder="All sports" value={sportId ? String(sportId) : ''} options={sports.status === 'ready' ? sports.data.map((sport) => ({ value: String(sport.id), label: sport.name })) : []} onChange={(event) => change('sportId', event.target.value)} />
        <Input label="From date" type="date" value={from} onChange={(event) => change('from', event.target.value)} error={rangeError ?? undefined} />
        <Input label="To date" type="date" value={to} onChange={(event) => change('to', event.target.value)} />
        <Select label="Training format" placeholder="All formats" value={format ?? ''} options={BROWSE_FORMATS} onChange={(event) => change('format', event.target.value)} />
        <Select label="Level" placeholder="All levels" value={level ?? ''} options={BROWSE_LEVELS} onChange={(event) => change('level', event.target.value)} />
        <Select label="Coach" placeholder={coaches.status === 'loading' ? 'Loading coaches...' : 'All coaches'} value={coachId ? String(coachId) : ''} options={options} disabled={format === 'SELF_TRAINING'} hint="Coaches with sessions in the selected date range and filters." onChange={(event) => change('coachId', event.target.value)} />
      </div>
      <div className="browse-actions"><Button variant="secondary" onClick={() => { const next = new URLSearchParams(params); for (const key of ['sportId', 'from', 'to', 'format', 'level', 'coachId', 'page']) next.delete(key); setParams(next) }}>Clear filters</Button><Button variant="ghost" onClick={reload}>Refresh results</Button></div>
      {sports.status === 'error' && <BrowseError error={sports.error} onRetry={retrySports} />}
      {coaches.status === 'error' && <div><p className="browse-muted">Coach filter options are unavailable. Session results are still shown.</p><BrowseError error={coaches.error} onRetry={retryCoaches} /></div>}
      {!rangeError && (resource.status === 'loading' ? <PageLoader label="Finding sessions" /> : resource.status === 'error' ? <BrowseError error={resource.error} onRetry={reload} /> : <>
        <p className="browse-muted" role="status">{resource.data.totalElements} matching sessions</p>
        {!resource.data.content.length ? <EmptyState title="No matching sessions" description="Try different dates or clear your filters." /> : <div className="browse-grid">{resource.data.content.map((session) => <SessionCard session={session} returnTo={returnTo} key={session.id} />)}</div>}
        <Pagination page={resource.data.page} pageSize={resource.data.size} totalItems={resource.data.totalElements} onPageChange={(value) => change('page', String(value))} />
      </>)}
    </Card>
  </div>
}
