import { useCallback, useMemo, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { memberSessionsApi, type BrowseFormat, type BrowseSession } from '../../api/memberSessionsApi'
import { packagesApi } from '../../api/packagesApi'
import trainingGroundImage from '../../assets/home/hero.jpg'
import { Button, CalendarDateField, Card, EmptyState, PageLoader, Select } from '../../components/ui'
import { BrowseError } from './BrowseError'
import { FindClassesPage } from './FindClassesPage'
import { MemberBookingsPage } from './MemberBookingsPages'
import { MemberScheduleNav, type MemberScheduleTab } from './MemberScheduleNav'
import { MemberSchedulePage } from './MemberSchedulePage'
import { ScheduleWorkspaceHero } from './ScheduleWorkspaceHero'
import { SessionCard } from './SessionCard'
import { browseAddDays, browseDate, browseMonday, browseToday, validBrowseDate } from './memberScheduleDates'
import { sportImageFor } from './sportImages'
import { useMemberResource } from './useMemberResource'

const TAB_DETAILS: Record<MemberScheduleTab, { title: string; description: string; imageSrc: string; imageAlt: string; fadeImage?: boolean }> = {
  center: {
    title: 'Center Schedule',
    description: 'Explore the center-wide timetable, compare availability, and open a session before booking.',
    imageSrc: sportImageFor('football'),
    imageAlt: 'Football training on the center field',
    fadeImage: true,
  },
  'my-schedule': {
    title: 'My Schedule',
    description: 'Your confirmed bookings only, organized week by week in Vietnam time (UTC+7).',
    imageSrc: sportImageFor('tennis'),
    imageAlt: 'Tennis practice on a clay court',
    fadeImage: true,
  },
  'find-classes': {
    title: 'Find Classes',
    description: 'Filter live Sportify sessions by sport, level, format, coach, and date.',
    imageSrc: trainingGroundImage,
    imageAlt: 'Badminton practice on an indoor training court',
    fadeImage: true,
  },
  bookings: {
    title: 'My Bookings',
    description: 'Review your upcoming reservations and booking history using the existing cancellation rules.',
    imageSrc: sportImageFor('basketball'),
    imageAlt: 'Basketball training at the sports center',
    fadeImage: true,
  },
}

function routeTab(pathname: string, value: string | null): MemberScheduleTab {
  if (pathname === '/member/schedule') return 'my-schedule'
  if (pathname === '/member/classes') return 'find-classes'
  if (pathname === '/member/bookings') return 'bookings'
  return value === 'my-schedule' || value === 'find-classes' || value === 'bookings' ? value : 'center'
}

export function MemberSchedulesWorkspacePage({ tab: forcedTab }: { tab?: MemberScheduleTab }) {
  const location = useLocation()
  const [params] = useSearchParams()
  const activeTab = forcedTab ?? routeTab(location.pathname, params.get('tab'))
  const details = TAB_DETAILS[activeTab]

  return (
    <div className="page browse-page member-schedule-theme schedule-workspace">
      <MemberScheduleNav activeTab={activeTab} />
      <ScheduleWorkspaceHero
        title={details.title}
        description={details.description}
        imageSrc={details.imageSrc}
        imageAlt={details.imageAlt}
        fadeImage={details.fadeImage}
      />
      <div className="schedule-workspace__content">
        {activeTab === 'center' && <CenterSchedule />}
        {activeTab === 'my-schedule' && <MemberSchedulePage embedded />}
        {activeTab === 'find-classes' && <FindClassesPage embedded />}
        {activeTab === 'bookings' && <MemberBookingsPage embedded />}
      </div>
    </div>
  )
}

type Availability = 'ALL' | 'AVAILABLE' | 'FULL'

function CenterSchedule() {
  const [params, setParams] = useSearchParams()
  const [today] = useState(browseToday)
  const requestedDate = params.get('date') ?? today
  const from = browseMonday(validBrowseDate(requestedDate) ? requestedDate : today)
  const to = browseAddDays(from, 6)
  const sportIdValue = Number(params.get('sportId'))
  const sportId = Number.isSafeInteger(sportIdValue) && sportIdValue > 0 ? sportIdValue : undefined
  const format = (['COACH_LED', 'SELF_TRAINING'].includes(params.get('format') ?? '') ? params.get('format') : undefined) as BrowseFormat | undefined
  const availability = (['AVAILABLE', 'FULL'].includes(params.get('availability') ?? '') ? params.get('availability') : 'ALL') as Availability
  const status = params.get('status') || 'ALL'
  const loader = useCallback((signal: AbortSignal) => memberSessionsApi.all({ sportId, from, to, format }, signal), [sportId, from, to, format])
  const [resource, reload] = useMemberResource(loader)
  const sportsLoader = useCallback((signal: AbortSignal) => packagesApi.sports({ signal }), [])
  const [sports, retrySports] = useMemberResource(sportsLoader)
  const returnTo = `/member/schedules?${params.toString()}`

  const rows = useMemo(() => resource.status === 'ready'
    ? resource.data.filter((session) => {
      if (availability === 'AVAILABLE' && session.full) return false
      if (availability === 'FULL' && !session.full) return false
      return status === 'ALL' || session.status === status
    })
    : [], [availability, resource, status])

  const grouped = useMemo(() => {
    const groups = new Map<string, BrowseSession[]>()
    for (const session of rows) groups.set(session.sessionDate, [...(groups.get(session.sessionDate) ?? []), session])
    return [...groups.entries()].sort(([left], [right]) => left.localeCompare(right))
  }, [rows])

  function change(key: string, value: string) {
    setParams((previous) => {
      const next = new URLSearchParams(previous)
      if (value) next.set(key, value)
      else next.delete(key)
      next.set('tab', 'center')
      return next
    })
  }

  function moveWeek(days: number) { change('date', browseAddDays(from, days)) }

  return (
    <Card className="center-schedule" title={`${browseDate(from)} - ${browseDate(to)}`}>
      <div className="center-schedule__toolbar">
        <div className="center-schedule__week-group">
          <div className="schedule-week-nav" role="group" aria-label="Center schedule week navigation">
            <Button variant="secondary" onClick={() => moveWeek(-7)}>Previous week</Button>
            <Button variant={from === browseMonday(today) ? 'primary' : 'secondary'} onClick={() => change('date', today)}>This week</Button>
            <Button variant="secondary" onClick={() => moveWeek(7)}>Next week</Button>
          </div>
          <CalendarDateField label="Week containing" value={from} onChange={(value, valid) => { if (valid !== false) change('date', value) }} />
        </div>
        <div className="center-schedule__toolbar-actions">
          <Link className="btn btn--primary btn--md" to="/member/schedules?tab=find-classes">Find a class</Link>
        </div>
      </div>
      <div className="center-schedule__filters" aria-label="Center schedule filters">
        <Select label="Sport" placeholder="All sports" value={sportId ? String(sportId) : ''} options={sports.status === 'ready' ? sports.data.map((sport) => ({ value: String(sport.id), label: sport.name })) : []} onChange={(event) => change('sportId', event.target.value)} />
        <Select label="Training format" placeholder="All formats" value={format ?? ''} options={[{ value: 'COACH_LED', label: 'Coach-led' }, { value: 'SELF_TRAINING', label: 'Self-training' }]} onChange={(event) => change('format', event.target.value)} />
        <Select label="Availability" value={availability} options={[{ value: 'ALL', label: 'All availability' }, { value: 'AVAILABLE', label: 'Places available' }, { value: 'FULL', label: 'Full' }]} onChange={(event) => change('availability', event.target.value === 'ALL' ? '' : event.target.value)} />
        <Select label="Session status" value={status} options={[{ value: 'ALL', label: 'All visible statuses' }, { value: 'PUBLISHED', label: 'Published' }, { value: 'COMPLETED', label: 'Completed' }, { value: 'CANCELLED', label: 'Cancelled' }]} onChange={(event) => change('status', event.target.value === 'ALL' ? '' : event.target.value)} />
      </div>
      <div className="browse-actions center-schedule__actions">
        <Button variant="secondary" onClick={() => setParams({ tab: 'center', date: today })}>Clear filters</Button>
        <Button variant="ghost" onClick={reload}>Refresh schedule</Button>
        {resource.status === 'ready' && <span className="browse-muted" role="status">{rows.length} sessions shown</span>}
      </div>
      {sports.status === 'error' && <BrowseError error={sports.error} onRetry={retrySports} />}
      {resource.status === 'loading' ? <PageLoader label="Loading center schedule" /> : resource.status === 'error' ? <BrowseError error={resource.error} onRetry={reload} /> : grouped.length === 0 ? (
        <EmptyState title="No center sessions match this week" description="Try another week or clear the schedule filters." />
      ) : (
        <div className="center-schedule__days">
          {grouped.map(([date, sessions]) => <section className="center-schedule__day" key={date} aria-labelledby={`center-day-${date}`}>
            <div className="center-schedule__day-heading"><h2 id={`center-day-${date}`}>{browseDate(date)}</h2><span>{sessions.length} session{sessions.length === 1 ? '' : 's'}</span></div>
            <div className="browse-grid">{sessions.map((session) => <SessionCard session={session} returnTo={returnTo} key={session.id} />)}</div>
          </section>)}
        </div>
      )}
    </Card>
  )
}
