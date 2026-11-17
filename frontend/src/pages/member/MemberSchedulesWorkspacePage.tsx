import { useCallback, useMemo, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { memberSessionsApi, type BrowseFormat, type BrowseSession } from '../../api/memberSessionsApi'
import { packagesApi } from '../../api/packagesApi'
import { sessionIdText, sessionRouteReference } from '../../api/sessionIdentity'
import '../../styles/managerSchedule.css'
import { HOME_PHOTOS } from '../public/homeContent'
import trainingGroundImage from '../../assets/home/hero.jpg'
import { Button, CalendarDateField, Card, EmptyState, PageLoader, Select, StatusBadge } from '../../components/ui'
import { BrowseError } from './BrowseError'
import { FindClassesPage } from './FindClassesPage'
import { MemberBookingsPage } from './MemberBookingsPages'
import { MemberScheduleNav, type MemberScheduleTab } from './MemberScheduleNav'
import { MemberSchedulePage } from './MemberSchedulePage'
import { ScheduleWorkspaceHero } from './ScheduleWorkspaceHero'
import { browseAddDays, browseDate, browseDay, browseMonday, browseToday, validBrowseDate } from './memberScheduleDates'
import { sportPhotoFor } from './sportImages'
import { useMemberResource } from './useMemberResource'

const HERO_PHOTOS = {
  center: sportPhotoFor('football', 2200),
  schedule: sportPhotoFor('tennis', 2200),
  bookings: sportPhotoFor('basketball', 2200),
}

const TAB_DETAILS: Record<MemberScheduleTab, { title: string; description: string; imageSrc: string; imageFallback: string; imageAlt: string; fadeImage?: boolean }> = {
  center: {
    title: 'Center Schedule',
    description: 'Explore the center-wide timetable, compare availability, and open a session before booking.',
    imageSrc: HERO_PHOTOS.center.src,
    imageFallback: HERO_PHOTOS.center.fallback,
    imageAlt: 'Football training on the center field',
    fadeImage: true,
  },
  'my-schedule': {
    title: 'My Schedule',
    description: 'Your confirmed bookings only, organized week by week in Vietnam time (UTC+7).',
    imageSrc: HERO_PHOTOS.schedule.src,
    imageFallback: HERO_PHOTOS.schedule.fallback,
    imageAlt: 'Tennis practice on a clay court',
    fadeImage: true,
  },
  'find-classes': {
    title: 'Find Classes',
    description: 'Filter live Sportify sessions by sport, level, format, coach, and date.',
    imageSrc: HOME_PHOTOS.hero,
    imageFallback: trainingGroundImage,
    imageAlt: 'Badminton practice on an indoor training court',
    fadeImage: true,
  },
  bookings: {
    title: 'My Bookings',
    description: 'Review your upcoming reservations and booking history using the existing cancellation rules.',
    imageSrc: HERO_PHOTOS.bookings.src,
    imageFallback: HERO_PHOTOS.bookings.fallback,
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
        imageFallback={details.imageFallback}
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

  const dates = useMemo(() => Array.from({ length: 7 }, (_, i) => browseAddDays(from, i)), [from])

  const rows = useMemo(() => resource.status === 'ready'
    ? resource.data.filter((session) => {
      if (availability === 'AVAILABLE' && session.full) return false
      if (availability === 'FULL' && !session.full) return false
      return status === 'ALL' || session.status === status
    })
    : [], [availability, resource, status])

  const timeSlots = useMemo(() => Array.from(new Set(rows.map((s) => s.startTime.slice(0, 5)))).sort(), [rows])
  const sessionsByDateAndTime = useMemo(() => {
    const map = new Map<string, BrowseSession[]>()
    for (const session of rows) {
      const key = `${session.sessionDate}|${session.startTime.slice(0, 5)}`
      map.set(key, [...(map.get(key) ?? []), session])
    }
    return map
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
    <Card className="center-schedule" title="Weekly schedule">
      <div className="manager-filters center-schedule__filters" aria-label="Center schedule filters">
        <Select label="Sport" placeholder="All sports" value={sportId ? String(sportId) : ''} options={sports.status === 'ready' ? sports.data.map((sport) => ({ value: String(sport.id), label: sport.name })) : []} onChange={(event) => change('sportId', event.target.value)} />
        <Select label="Training format" placeholder="All formats" value={format ?? ''} options={[{ value: 'COACH_LED', label: 'Coach-led' }, { value: 'SELF_TRAINING', label: 'Self-training' }]} onChange={(event) => change('format', event.target.value)} />
        <Select label="Availability" value={availability} options={[{ value: 'ALL', label: 'All availability' }, { value: 'AVAILABLE', label: 'Places available' }, { value: 'FULL', label: 'Full' }]} onChange={(event) => change('availability', event.target.value === 'ALL' ? '' : event.target.value)} />
        <Select label="Session status" value={status} options={[{ value: 'ALL', label: 'All visible statuses' }, { value: 'PUBLISHED', label: 'Published' }, { value: 'COMPLETED', label: 'Completed' }, { value: 'CANCELLED', label: 'Cancelled' }]} onChange={(event) => change('status', event.target.value === 'ALL' ? '' : event.target.value)} />
        <div className="center-schedule__clear-field">
          <Button variant="secondary" onClick={() => setParams({ tab: 'center', date: today })}>Clear filters</Button>
        </div>
      </div>
      <div className="manager-row-actions center-schedule__toolbar">
        <div className="schedule-week-nav" role="group" aria-label="Center schedule week navigation">
          <Button variant="secondary" onClick={() => moveWeek(-7)}>Previous week</Button>
          <Button variant={from === browseMonday(today) ? 'primary' : 'secondary'} onClick={() => change('date', today)}>This week</Button>
          <Button variant="secondary" onClick={() => moveWeek(7)}>Next week</Button>
        </div>
        <strong>{browseDate(from)} - {browseDate(to)}</strong>
        <Button variant="ghost" onClick={reload}>Refresh schedule</Button>
        {resource.status === 'ready' && <span className="browse-muted" role="status">{rows.length} sessions shown</span>}
        <CalendarDateField label="Week containing" value={from} onChange={(value, valid) => { if (valid !== false) change('date', value) }} />
        <div className="center-schedule__toolbar-actions" style={{ marginLeft: 'auto' }}>
          <Link className="btn btn--secondary btn--md" to="/member/schedules?tab=find-classes">Find a class</Link>
        </div>
      </div>
      {sports.status === 'error' && <BrowseError error={sports.error} onRetry={retrySports} />}
      {resource.status === 'loading' ? <PageLoader label="Loading center schedule" /> : resource.status === 'error' ? <BrowseError error={resource.error} onRetry={reload} /> : rows.length === 0 ? (
        <EmptyState title="No center sessions match this week" description="Try another week or clear the schedule filters." />
      ) : (
        <>
          <p id="center-schedule-grouping" className="f2-schedule-table__guidance">Sessions are grouped by start time. Each card shows its full time range; separate rows can overlap.</p>
          <div className="f2-schedule-table-wrap" role="region" aria-label="Center weekly schedule" tabIndex={0}>
            <table className="f2-schedule-table" aria-label="Center weekly timetable" aria-describedby="center-schedule-grouping">
              <thead>
                <tr>
                  <th className="f2-schedule-table__time-heading" scope="col">Starts at</th>
                  {dates.map((date) => {
                    const isToday = date === today
                    return (
                      <th className={isToday ? 'f2-schedule-table__today' : undefined} scope="col" key={date} aria-current={isToday ? 'date' : undefined}>
                        <span>{browseDay(date)}</span>
                        <time dateTime={date}>{browseDate(date)}</time>
                      </th>
                    )
                  })}
                </tr>
              </thead>
              <tbody>
                {timeSlots.map((time) => (
                  <tr key={time}>
                    <th scope="row">{time}</th>
                    {dates.map((date) => {
                      const cellSessions = sessionsByDateAndTime.get(`${date}|${time}`) ?? []
                      const isToday = date === today
                      return (
                        <td className={isToday ? 'f2-schedule-table__today' : undefined} key={date}>
                          {cellSessions.length ? (
                            <div className="f2-schedule-table__entries">
                              {cellSessions.map((session) => (
                                <CenterScheduleEntry
                                  key={session.id}
                                  session={session}
                                  returnTo={returnTo}
                                />
                              ))}
                            </div>
                          ) : (
                            <span className="f2-schedule-table__empty-cell" aria-label="No sessions">-</span>
                          )}
                        </td>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </Card>
  )
}

function CenterScheduleEntry({ session, returnTo }: { session: BrowseSession; returnTo: string }) {
  const [expanded, setExpanded] = useState(false)
  const self = session.trainingFormat === 'SELF_TRAINING'
  const isCancelled = session.status === 'CANCELLED'
  const isCompleted = session.status === 'COMPLETED'
  const stateClass = isCancelled ? ' f2-session--cancelled' : isCompleted ? ' f2-session--completed' : ''
  const details = `/member/sessions/${sessionRouteReference(session)}?${new URLSearchParams({ returnTo })}`

  return (
    <article
      className={`f2-session${stateClass}`}
      role="button"
      tabIndex={0}
      aria-label={`Session ${session.className || `${session.sportName} Session`}`}
      onClick={() => setExpanded((prev) => !prev)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          setExpanded((prev) => !prev)
        }
      }}
    >
      <div className="f2-session__header">
        <span className="session-identity"><code>{sessionIdText(session.sessionId)}</code></span>
      </div>
      <strong className="f2-session__title">{session.className || `${session.sportName} ${self ? 'Self-training' : 'Session'}`}</strong>
      <div className="f2-session__timing">
        <span>{session.startTime.slice(0, 5)} - {session.endTime.slice(0, 5)}</span>
        <StatusBadge status={session.status} />
      </div>
      <p className="f2-session__meta f2-session__booked">
        {session.capacity - session.freeSeats} / {session.capacity} booked
      </p>

      <div className={`f2-session__details ${expanded ? 'f2-session__details--expanded' : 'f2-session__details--collapsed'}`}>
        <p className="f2-session__meta">Sport: {session.sportName}</p>
        <p className="f2-session__meta">{self ? 'Self-training - No assigned coach' : `Coach: ${session.coachName || 'Not assigned'}`}</p>
        <p className="f2-session__meta">Facility: {session.facilityName || 'Facility not assigned'}</p>
        <p className="f2-session__meta">Type: {self ? 'Self-training' : 'Coach-led'}</p>
      </div>

      <div className="f2-session__actions f2-session__actions--member" onClick={(e) => e.stopPropagation()}>
        <Link className="btn btn--secondary btn--md f2-session__view-btn" to={details}>
          View session
        </Link>
      </div>
    </article>
  )
}
