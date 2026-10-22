import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { memberSessionsApi, type BrowseSession } from '../../api/memberSessionsApi'
import { Badge, Button, Card, EmptyState, Input, PageHeader, PageLoader, buttonClassName } from '../../components/ui'
import { BrowseError } from './BrowseError'
import { MemberScheduleNav } from './MemberScheduleNav'
import { browseAddDays, browseDate, browseDay, browseIsoWeek, browseMonday, browseMondayFromIsoWeek, browseToday, validBrowseDate } from './memberScheduleDates'
import { useMemberResource } from './useMemberResource'

export function MemberSchedulePage() {
  const [params, setParams] = useSearchParams()
  const [today] = useState(browseToday)
  const selectedDate = params.get('date') ?? today
  const from = browseMonday(validBrowseDate(selectedDate) ? selectedDate : today)
  const to = browseAddDays(from, 6)
  const currentWeek = browseMonday(today)
  const days = Array.from({ length: 7 }, (_, index) => browseAddDays(from, index))
  const [hasLoadedSchedule, setHasLoadedSchedule] = useState(false)
  const loader = useCallback(async (signal: AbortSignal) => {
    const sessions = await memberSessionsApi.all({ from, to }, signal)
    if (!signal.aborted) setHasLoadedSchedule(true)
    return sessions
  }, [from, to])
  const [resource, reload] = useMemberResource(loader)
  const returnTo = `/member/schedule?${new URLSearchParams({ date: from })}`
  const findClassesHref = `/member/classes?${new URLSearchParams({ from, to })}`

  function changeWeek(monday: string) {
    setParams({ date: monday }, { replace: true })
  }

  return (
    <div className="page browse-page member-schedule-theme">
      <MemberScheduleNav />
      <PageHeader
        eyebrow="Member / Classes & schedule"
        title="View Schedule"
        description="Browse the week and choose a session to view or book. All times use Vietnam time (UTC+7)."
        actions={<Link className={buttonClassName()} to={findClassesHref}>Find classes</Link>}
      />

      <Card title={`${browseDate(from)} - ${browseDate(to)}`}>
        <div className="schedule-controls">
          <Input
            className="schedule-week-input"
            label="Choose week"
            type="week"
            value={browseIsoWeek(from)}
            onChange={(event) => {
              const monday = browseMondayFromIsoWeek(event.target.value)
              if (monday) changeWeek(monday)
            }}
          />
          <div className="schedule-week-nav" role="group" aria-label="Week navigation">
            <Button variant="secondary" onClick={() => changeWeek(browseAddDays(from, -7))}>Previous week</Button>
            <Button variant={from === currentWeek ? 'primary' : 'secondary'} disabled={from === currentWeek} onClick={() => changeWeek(currentWeek)}>
              This week
            </Button>
            <Button variant="secondary" onClick={() => changeWeek(browseAddDays(from, 7))}>Next week</Button>
          </div>
        </div>

        <div className="schedule-content-frame" aria-busy={resource.status === 'loading'}>
          {resource.status === 'loading' ? (
            hasLoadedSchedule ? <ScheduleLoadingSkeleton from={from} to={to} /> : <PageLoader label="Loading schedule" />
          ) : resource.status === 'error' ? (
            <BrowseError error={resource.error} onRetry={reload} />
          ) : (
            <div className="schedule-content-frame__ready" key={from}>
              <ScheduleContents
                sessions={resource.data}
                days={days}
                today={today}
                returnTo={returnTo}
                findClassesHref={findClassesHref}
              />
            </div>
          )}
        </div>
      </Card>
    </div>
  )
}

function ScheduleContents({
  sessions,
  days,
  today,
  returnTo,
  findClassesHref,
}: {
  sessions: BrowseSession[]
  days: string[]
  today: string
  returnTo: string
  findClassesHref: string
}) {
  return sessions.length === 0 ? (
    <EmptyState
      title="No sessions this week"
      description="Choose another week or look for a class on a different date."
      action={<Link className={buttonClassName('secondary')} to={findClassesHref}>Find classes</Link>}
    />
  ) : (
    <WeeklyScheduleTable sessions={sessions} days={days} today={today} returnTo={returnTo} />
  )
}

function ScheduleLoadingSkeleton({ from, to }: { from: string; to: string }) {
  const weekLabel = `${browseDate(from)} - ${browseDate(to)}`
  return (
    <div className="schedule-skeleton" role="status" aria-label={`Loading schedule for ${weekLabel}`}>
      <div className="schedule-skeleton__label" aria-hidden="true">
        <span className="schedule-skeleton__dot" />
        <strong>Loading selected week</strong>
        <span>{weekLabel}</span>
      </div>
      <p className="schedule-scroll-hint" aria-hidden="true">Scroll horizontally to view all seven days.</p>
      <div className="schedule-skeleton__scroll" aria-hidden="true">
        <div className="schedule-skeleton__grid">
          <div className="schedule-skeleton__header">
            {Array.from({ length: 8 }, (_, index) => <span key={index} />)}
          </div>
          {Array.from({ length: 3 }, (_, row) => (
            <div className="schedule-skeleton__row" key={row}>
              <span className="schedule-skeleton__time" />
              {Array.from({ length: 7 }, (_, day) => (
                <span className="schedule-skeleton__cell" key={day}>
                  {(row + day * 2) % 5 === 0 && <i />}
                </span>
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function WeeklyScheduleTable({
  sessions,
  days,
  today,
  returnTo,
}: {
  sessions: BrowseSession[]
  days: string[]
  today: string
  returnTo: string
}) {
  const timeSlots = [...new Set(sessions.map((session) => session.startTime.slice(0, 5)))].sort()
  const sessionsByDayAndTime = new Map<string, BrowseSession[]>()
  for (const session of sessions) {
    const key = `${session.sessionDate}|${session.startTime.slice(0, 5)}`
    sessionsByDayAndTime.set(key, [...(sessionsByDayAndTime.get(key) ?? []), session])
  }

  return (
    <>
      <p className="schedule-scroll-hint">Scroll horizontally to view all seven days.</p>
      <div className="schedule-table-wrap" role="region" aria-label="Weekly session timetable" tabIndex={0}>
        <table className="schedule-table" aria-label="Weekly schedule">
          <thead>
            <tr>
              <th className="schedule-table__time-heading" scope="col">Time</th>
              {days.map((day) => (
                <th className={day === today ? 'schedule-table__today' : undefined} scope="col" key={day} aria-current={day === today ? 'date' : undefined}>
                  <span>{browseDay(day)}</span>
                  <time dateTime={day}>{browseDate(day)}</time>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {timeSlots.map((time) => (
              <tr key={time}>
                <th scope="row">{time}</th>
                {days.map((day) => {
                  const key = `${day}|${time}`
                  const daySessions = sessionsByDayAndTime.get(key) ?? []
                  return (
                    <td className={day === today ? 'schedule-table__today' : undefined} key={day}>
                      {daySessions.length > 0 ? (
                        <div className="schedule-table__entries">
                          {daySessions.map((session) => (
                            <ScheduleEntry session={session} returnTo={returnTo} key={session.id} />
                          ))}
                        </div>
                      ) : <span className="schedule-table__empty-cell" aria-label="No sessions">-</span>}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

function ScheduleEntry({ session, returnTo }: { session: BrowseSession; returnTo: string }) {
  const selfTraining = session.trainingFormat === 'SELF_TRAINING'
  const title = session.className || `${session.sportName} ${selfTraining ? 'Self-training' : 'Session'}`
  const format = selfTraining ? 'Self-training' : 'Coach-led'
  const details = `/member/sessions/${session.id}?${new URLSearchParams({ returnTo })}`

  return (
    <article className="schedule-entry">
      <Link className="schedule-entry__title" to={details}>{title}</Link>
      <p className="schedule-entry__meta">{session.startTime.slice(0, 5)} - {session.endTime.slice(0, 5)}</p>
      <p className="schedule-entry__meta">{session.sportName} - {format}{session.level ? ` / ${session.level[0] + session.level.slice(1).toLowerCase()}` : ''}</p>
      {!selfTraining && <p className="schedule-entry__meta">Coach: {session.coachName || 'Not provided'}</p>}
      <p className="schedule-entry__meta">{session.facilityName || 'Facility not provided'}</p>
      <Badge tone={session.full || session.freeSeats <= 0 ? 'warning' : 'success'}>
        {session.full || session.freeSeats <= 0 ? 'Fully booked' : `${session.freeSeats} ${session.freeSeats === 1 ? 'spot' : 'spots'} left`}
      </Badge>
    </article>
  )
}
