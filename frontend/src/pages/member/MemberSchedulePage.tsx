import { sessionIdText, sessionRouteReference } from '../../api/sessionIdentity'
import '../../components/sessionContext.css'
import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { memberBookingsApi, type MemberBookingRecord } from '../../api/memberBookingsApi'
import { Badge, Button, Card, EmptyState, Input, PageHeader, PageLoader, buttonClassName } from '../../components/ui'
import { BrowseError } from './BrowseError'
import { MemberScheduleNav } from './MemberScheduleNav'
import { browseAddDays, browseDate, browseDay, browseIsoWeek, browseMonday, browseMondayFromIsoWeek, browseToday, validBrowseDate } from './memberScheduleDates'
import { useMemberResource } from './useMemberResource'

export const NO_SCHEDULE_YET = "You don't have any scheduled sessions yet."
export const NO_SESSIONS_THIS_WEEK = 'No sessions scheduled for this week.'
const FIND_CLASSES = '/member/classes'
const ASSISTANT = '/member/assistant'

/** My Schedule shows only the sessions the signed-in member holds a CONFIRMED booking for. */
export function MemberSchedulePage() {
  const [params, setParams] = useSearchParams()
  const [today] = useState(browseToday)
  const selectedDate = params.get('date') ?? today
  const from = browseMonday(validBrowseDate(selectedDate) ? selectedDate : today)
  const to = browseAddDays(from, 6)
  const currentWeek = browseMonday(today)
  const days = Array.from({ length: 7 }, (_, index) => browseAddDays(from, index))
  const loader = useCallback(async (signal: AbortSignal) => {
    const bookings = await memberBookingsApi.mine(signal)
    return bookings.filter((booking) => booking.status === 'CONFIRMED')
  }, [])
  const [resource, reload] = useMemberResource(loader)
  const returnTo = `/member/schedule?${new URLSearchParams({ date: from })}`

  function changeWeek(monday: string) {
    setParams({ date: monday }, { replace: true })
  }

  return (
    <div className="page browse-page member-schedule-theme">
      <MemberScheduleNav />
      <PageHeader
        eyebrow="Member / Classes & schedule"
        title="My Schedule"
        description="The sessions you have booked, week by week. All times use Vietnam time (UTC+7)."
        actions={<Link className={buttonClassName()} to={FIND_CLASSES}>Find Classes</Link>}
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
            <PageLoader label="Loading your schedule" />
          ) : resource.status === 'error' ? (
            <BrowseError error={resource.error} onRetry={reload} />
          ) : (
            <div className="schedule-content-frame__ready" key={from}>
              <ScheduleContents bookings={resource.data} days={days} today={today} returnTo={returnTo} />
            </div>
          )}
        </div>
      </Card>
    </div>
  )
}

function ScheduleContents({ bookings, days, today, returnTo }: {
  bookings: MemberBookingRecord[]
  days: string[]
  today: string
  returnTo: string
}) {
  if (bookings.length === 0) {
    return (
      <EmptyState
        title={NO_SCHEDULE_YET}
        description="Book a class or a self-training session and it will appear here."
        action={<div className="browse-actions">
          <Link className={buttonClassName()} to={FIND_CLASSES}>Find Classes</Link>
          <Link className={buttonClassName('secondary')} to={ASSISTANT}>Get Help Finding Classes</Link>
        </div>}
      />
    )
  }
  const week = bookings.filter((booking) => days.includes(booking.sessionDate))
  if (week.length === 0) {
    return (
      <EmptyState
        title={NO_SESSIONS_THIS_WEEK}
        description="Choose another week to see your other booked sessions."
        action={<Link className={buttonClassName('secondary')} to={FIND_CLASSES}>Find Classes</Link>}
      />
    )
  }
  return <WeeklyScheduleTable bookings={week} days={days} today={today} returnTo={returnTo} />
}

function slot(time: string | null) {
  return time ? time.slice(0, 5) : '--:--'
}

function WeeklyScheduleTable({ bookings, days, today, returnTo }: {
  bookings: MemberBookingRecord[]
  days: string[]
  today: string
  returnTo: string
}) {
  const timeSlots = [...new Set(bookings.map((booking) => slot(booking.startTime)))].sort()
  const byDayAndTime = new Map<string, MemberBookingRecord[]>()
  for (const booking of bookings) {
    const key = `${booking.sessionDate}|${slot(booking.startTime)}`
    byDayAndTime.set(key, [...(byDayAndTime.get(key) ?? []), booking])
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
                  const entries = byDayAndTime.get(`${day}|${time}`) ?? []
                  return (
                    <td className={day === today ? 'schedule-table__today' : undefined} key={day}>
                      {entries.length > 0 ? (
                        <div className="schedule-table__entries">
                          {entries.map((booking) => <ScheduleEntry booking={booking} past={booking.sessionDate < today} returnTo={returnTo} key={booking.id} />)}
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

function ScheduleEntry({ booking, past, returnTo }: { booking: MemberBookingRecord; past: boolean; returnTo: string }) {
  const selfTraining = booking.trainingType === 'SELF_TRAINING'
  const sport = booking.sportName || 'Sport'
  const title = booking.className || `${sport} ${selfTraining ? 'Self-training' : 'Session'}`
  const details = `/member/sessions/${sessionRouteReference({ id: booking.sessionId, sessionId: booking.sessionCode })}?${new URLSearchParams({ returnTo })}`

  return (
    <article className="schedule-entry">
      <span className="session-identity">Session ID <code>{sessionIdText(booking.sessionCode)}</code></span>
      <Link className="schedule-entry__title" to={details}>{title}</Link>
      <p className="schedule-entry__meta">{slot(booking.startTime)} - {slot(booking.endTime)}</p>
      <p className="schedule-entry__meta">{sport} - {selfTraining ? 'Self-training' : 'Coach-led'}</p>
      <p className="schedule-entry__meta">{selfTraining ? 'Self-training - No assigned coach' : `Coach: ${booking.coachName || 'Coach not assigned'}`}</p>
      <p className="schedule-entry__meta">{booking.facilityName || 'Facility not provided'}</p>
      {past ? <Badge>Past session</Badge> : <Badge tone="success">Booked</Badge>}
    </article>
  )
}
