import { useCallback, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { memberSessionsApi } from '../../api/memberSessionsApi'
import { packagesApi } from '../../api/packagesApi'
import { Button, Card, EmptyState, Input, PageHeader, PageLoader, Select, buttonClassName } from '../../components/ui'
import { BrowseError } from './BrowseError'
import { MemberScheduleNav } from './MemberScheduleNav'
import { SessionCard } from './SessionCard'
import { BROWSE_FORMATS, browseFilters, updateBrowseParams } from './scheduleFilters'
import { browseAddDays, browseDate, browseDay, browseMonday, browseToday, validBrowseDate } from './memberScheduleDates'
import { useMemberResource } from './useMemberResource'

const loadSports = (signal: AbortSignal) => packagesApi.sports({ signal })
export function MemberSchedulePage() {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const [today] = useState(browseToday)
  const dateValue = params.get('date') ?? today
  const date = validBrowseDate(dateValue) ? dateValue : today
  const daily = params.get('view') === 'day'
  const from = daily ? date : browseMonday(date)
  const to = daily ? date : browseAddDays(from, 6)
  const { sportId, format } = browseFilters(params, today)
  const [sports, retrySports] = useMemberResource(loadSports)
  const loader = useCallback((signal: AbortSignal) => memberSessionsApi.all({ from, to, sportId, format }, signal), [from, to, sportId, format])
  const [resource, reload] = useMemberResource(loader)
  const returnTo = location.pathname + location.search
  const dates = Array.from({ length: daily ? 1 : 7 }, (_, index) => browseAddDays(from, index))
  function change(key: string, value: string) { setParams((previous) => updateBrowseParams(previous, key, value)) }
  return <div className="page browse-page">
    <MemberScheduleNav />
    <PageHeader eyebrow="Member / Classes & schedule" title="View Schedule" description="Find a session that fits your week. All times are Vietnam time (UTC+7)." actions={<Link className={buttonClassName()} to={`/member/classes?${new URLSearchParams({ from, to, ...(sportId ? { sportId: String(sportId) } : {}), ...(format ? { format } : {}) })}`}>Find classes</Link>} />
    <Card title={daily ? browseDate(date) : `${browseDate(from)} - ${browseDate(to)}`}>
      <div className="browse-filters">
        <Select label="Sport" placeholder="All sports" value={sportId ? String(sportId) : ''} options={sports.status === 'ready' ? sports.data.map((sport) => ({ value: String(sport.id), label: sport.name })) : []} onChange={(event) => change('sportId', event.target.value)} />
        <Select label="Training format" placeholder="All formats" value={format ?? ''} options={BROWSE_FORMATS} onChange={(event) => change('format', event.target.value)} />
        <Input label="Selected date" type="date" value={date} onChange={(event) => change('date', event.target.value)} />
        <Select label="Schedule view" options={[{ value: 'week', label: 'Week' }, { value: 'day', label: 'Day' }]} value={daily ? 'day' : 'week'} onChange={(event) => change('view', event.target.value)} />
      </div>
      {sports.status === 'error' && <BrowseError error={sports.error} onRetry={retrySports} />}
      <div className="browse-actions"><Button variant="secondary" onClick={() => change('date', browseAddDays(date, daily ? -1 : -7))}>{daily ? 'Previous day' : 'Previous week'}</Button><Button variant="secondary" onClick={() => change('date', today)}>Today</Button><Button variant="secondary" onClick={() => change('date', browseAddDays(date, daily ? 1 : 7))}>{daily ? 'Next day' : 'Next week'}</Button><Button variant="ghost" onClick={reload}>Refresh schedule</Button></div>
      {resource.status === 'loading' ? <PageLoader label="Loading schedule" /> : resource.status === 'error' ? <BrowseError error={resource.error} onRetry={reload} /> : <>
        {!resource.data.length && <EmptyState title="No sessions in this range" description="Try another date, clear filters or find classes." action={<Button variant="secondary" onClick={() => { setParams({ date: today, view: daily ? 'day' : 'week' }) }}>Clear filters</Button>} />}
        <div className={`browse-calendar${daily ? ' browse-calendar--day' : ''}`}>{dates.map((day) => <section className="browse-day" aria-label={`${browseDay(day)} ${browseDate(day)}`} key={day}><h2>{browseDay(day)} {browseDate(day)}</h2>{resource.data.filter((session) => session.sessionDate === day).map((session) => <SessionCard session={session} returnTo={returnTo} key={session.id} />)}{!resource.data.some((session) => session.sessionDate === day) && <p className="browse-muted">No sessions</p>}</section>)}</div>
      </>}
    </Card>
  </div>
}
