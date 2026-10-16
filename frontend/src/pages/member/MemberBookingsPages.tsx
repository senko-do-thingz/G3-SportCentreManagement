import { useCallback, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/errors'
import { memberBookingsApi, type BookingWhen, type MemberBookingRecord } from '../../api/memberBookingsApi'
import { Badge, Button, Card, EmptyState, PageHeader, PageLoader, buttonClassName } from '../../components/ui'
import { BrowseError } from './BrowseError'
import { MemberScheduleNav } from './MemberScheduleNav'
import { useMemberResource } from './useMemberResource'
import { bookingDate, bookingErrorMessage, bookingTime, cancellationDeadline } from './bookingPresentation'

function routeId(value: string | undefined): number | null {
  if (!value || !/^\d+$/.test(value)) return null
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 ? number : null
}

function stateBooking(state: unknown, id: number): MemberBookingRecord | null {
  if (!state || typeof state !== 'object') return null
  const value = (state as { booking?: unknown }).booking
  if (!value || typeof value !== 'object') return null
  const booking = value as Partial<MemberBookingRecord>
  return booking.id === id && typeof booking.bookingCode === 'string' && typeof booking.sessionDate === 'string'
    ? booking as MemberBookingRecord : null
}

function bookingTitle(booking: MemberBookingRecord): string {
  return booking.className || `${booking.sportName || 'Sport'} session`
}

function actorName(booking: MemberBookingRecord): string {
  if (booking.cancelledByName) return booking.cancelledByName
  if (booking.cancelledByRole === 'MANAGER') return 'Manager'
  if (booking.cancelledByRole === 'RECEPTIONIST') return 'Receptionist'
  if (booking.cancelledByRole === 'MEMBER') return 'Member'
  return 'Not provided'
}

export function MemberBookingsPage() {
  const [params, setParams] = useSearchParams()
  const when: BookingWhen = params.get('when') === 'PAST' ? 'PAST' : 'UPCOMING'
  const loader = useCallback((signal: AbortSignal) => memberBookingsApi.my(when, signal), [when])
  const [resource, reload] = useMemberResource(loader)

  return <div className="page browse-page booking-flow">
    <MemberScheduleNav />
    <PageHeader eyebrow="Member / Classes & schedule" title="My Bookings" description="Review your upcoming sessions and booking history." />
    <Card>
      <div className="booking-tabs" role="tablist" aria-label="Booking history">
        <button type="button" role="tab" aria-selected={when === 'UPCOMING'} className={`booking-tab${when === 'UPCOMING' ? ' booking-tab--active' : ''}`} onClick={() => setParams({ when: 'UPCOMING' })}>Upcoming</button>
        <button type="button" role="tab" aria-selected={when === 'PAST'} className={`booking-tab${when === 'PAST' ? ' booking-tab--active' : ''}`} onClick={() => setParams({ when: 'PAST' })}>Past</button>
      </div>
      {resource.status === 'loading' ? <PageLoader label={`Loading ${when.toLowerCase()} bookings`} /> : resource.status === 'error' ? <BrowseError error={resource.error} onRetry={reload} /> : resource.data.length === 0 ? <EmptyState title={when === 'UPCOMING' ? 'No upcoming bookings' : 'No past bookings'} description={when === 'UPCOMING' ? 'Browse the schedule and reserve a place in a session.' : 'Completed and cancelled bookings will appear here.'} action={when === 'UPCOMING' ? <Link className={buttonClassName()} to="/member/schedule">Browse schedule</Link> : undefined} /> : <div className="booking-list">
        {resource.data.map((booking) => <BookingHistoryCard booking={booking} when={when} key={booking.id} />)}
      </div>}
      <div className="browse-actions booking-actions"><Button variant="ghost" onClick={reload}>Refresh bookings</Button>{when === 'UPCOMING' && <Link className={buttonClassName('secondary')} to="/member/schedule">Browse schedule</Link>}</div>
    </Card>
  </div>
}

function BookingHistoryCard({ booking, when }: { booking: MemberBookingRecord; when: BookingWhen }) {
  const self = booking.trainingType === 'SELF_TRAINING'
  const cancelHref = `/member/bookings/${booking.id}/cancel`
  return <article className="booking-history-card">
    <div className="booking-history-card__heading"><div><p className="booking-history-card__date">{bookingDate(booking.sessionDate)} / {bookingTime(booking.startTime)} - {bookingTime(booking.endTime)}</p><h2>{bookingTitle(booking)}</h2></div><Badge tone={booking.status === 'CONFIRMED' ? 'success' : 'neutral'}>{booking.status === 'CONFIRMED' ? 'Confirmed' : 'Cancelled'}</Badge></div>
    <p className="browse-muted">{booking.sportName || 'Sport not provided'}{!self && ` - ${booking.coachName || 'Coach not provided'}`} - {booking.facilityName || 'Facility not provided'}</p>
    {booking.status === 'CANCELLED' && <div className="booking-cancelled-meta"><span>Cancelled by {actorName(booking)}</span>{booking.cancelledAt && <span>On {booking.cancelledAt.replace('T', ' ').slice(0, 16)}</span>}{booking.cancelReason && <span>Reason: {booking.cancelReason}</span>}</div>}
    <details className="booking-details"><summary>Booking details</summary><dl className="browse-facts"><div><dt>Booking code</dt><dd>{booking.bookingCode}</dd></div><div><dt>Training</dt><dd>{self ? 'Self-training' : 'Coach-led'}</dd></div><div><dt>Package</dt><dd>{booking.packageName || 'Not provided'}</dd></div>{!self && <div><dt>Coach</dt><dd>{booking.coachName || 'Not provided'}</dd></div>}<div><dt>Facility</dt><dd>{booking.facilityName || 'Not provided'}</dd></div></dl></details>
    <div className="browse-actions booking-actions">{when === 'UPCOMING' && booking.status === 'CONFIRMED' && <Link className={buttonClassName('secondary')} to={cancelHref} state={{ booking }}>Cancel booking</Link>}{when === 'PAST' && booking.status === 'CONFIRMED' && <span className="browse-muted">Session completed</span>}</div>
  </article>
}

export function BookingCancellationPage() {
  const { bookingId: rawId } = useParams()
  const id = routeId(rawId)
  const { state } = useLocation()
  if (!id) return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Booking unavailable" /><Card><p role="alert">Choose a valid booking to cancel.</p><Link className={buttonClassName()} to="/member/bookings">Back to My Bookings</Link></Card></div>
  return <BookingCancellationContent key={id} id={id} initial={stateBooking(state, id)} />
}

function BookingCancellationContent({ id, initial }: { id: number; initial: MemberBookingRecord | null }) {
  const navigate = useNavigate()
  const loader = useCallback(async (signal: AbortSignal) => {
    if (initial) return initial
    const [upcoming, past] = await Promise.all([memberBookingsApi.my('UPCOMING', signal), memberBookingsApi.my('PAST', signal)])
    return [...upcoming, ...past].find((booking) => booking.id === id) ?? null
  }, [id, initial])
  const [resource, reload] = useMemberResource(loader)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submitted = useRef(false)

  async function confirmCancel() {
    if (submitted.current) return
    submitted.current = true
    setSubmitting(true)
    setError(null)
    try {
      const cancelled = await memberBookingsApi.cancel(id)
      if (cancelled.status !== 'CANCELLED') throw new ApiError(0, 'The server did not confirm that this booking was cancelled.')
      navigate(`/member/bookings/${id}/cancelled`, { replace: true, state: { booking: cancelled } })
    } catch (cause) {
      submitted.current = false
      setError(bookingErrorMessage(cause))
    } finally {
      setSubmitting(false)
    }
  }

  if (resource.status === 'loading') return <div className="page browse-page"><MemberScheduleNav /><PageLoader label="Loading booking details" /></div>
  if (resource.status === 'error') return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Cancel booking" /><BrowseError error={resource.error} onRetry={reload} /></div>
  if (!resource.data) return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Booking not found" /><Card><p>This booking could not be found in your upcoming or past bookings.</p><Link className={buttonClassName()} to="/member/bookings">My Bookings</Link></Card></div>

  const booking = resource.data
  const deadline = cancellationDeadline(booking)
  if (booking.status !== 'CONFIRMED') return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Booking cannot be cancelled" /><Card><Badge>Cancelled</Badge><p>This booking has already been cancelled.</p><Link className={buttonClassName()} to="/member/bookings?when=PAST">View past bookings</Link></Card></div>

  return <div className="page browse-page booking-flow">
    <MemberScheduleNav />
    <PageHeader eyebrow="Member / My Bookings" title="Cancel booking" description="Please confirm before cancelling this session." />
    <Card className="booking-cancel-card">
      <div className="booking-cancel-warning" role="note"><strong>Cancellation deadline</strong><p>{deadline ? `Cancel by ${deadline} Vietnam time. This is two hours before the session starts.` : 'The backend will verify whether this booking is within the cancellation period.'}</p><p>After the cutoff, the booking service will reject the request and the booking will remain confirmed.</p></div>
      <h2>{bookingTitle(booking)}</h2>
      <dl className="browse-facts booking-confirmation__facts"><div><dt>Booking code</dt><dd>{booking.bookingCode}</dd></div><div><dt>Date</dt><dd>{bookingDate(booking.sessionDate)}</dd></div><div><dt>Time</dt><dd>{bookingTime(booking.startTime)} - {bookingTime(booking.endTime)}</dd></div><div><dt>Package</dt><dd>{booking.packageName || 'Sport package'}</dd></div>{booking.trainingType !== 'SELF_TRAINING' && <div><dt>Coach</dt><dd>{booking.coachName || 'Not provided'}</dd></div>}<div><dt>Facility</dt><dd>{booking.facilityName || 'Not provided'}</dd></div></dl>
      <p>When the cancellation is accepted, the booking is marked cancelled and the booking service restores the reserved package session.</p>
      {error && <div className="booking-error" role="alert"><strong>Booking not cancelled</strong><p>{error}</p><p>Your booking remains unchanged unless My Bookings shows it as cancelled.</p></div>}
      <div className="browse-actions booking-actions"><Link className={buttonClassName('secondary')} to="/member/bookings?when=UPCOMING">Keep booking</Link><Button variant="danger" loading={submitting} disabled={submitting} onClick={() => void confirmCancel()}>Confirm cancellation</Button></div>
    </Card>
  </div>
}

export function BookingCancelledPage() {
  const { bookingId: rawId } = useParams()
  const id = routeId(rawId)
  const { state } = useLocation()
  if (!id) return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Cancellation unavailable" /><Card><p role="alert">Choose a valid booking.</p><Link className={buttonClassName()} to="/member/bookings">My Bookings</Link></Card></div>
  return <BookingCancelledContent key={id} id={id} initial={stateBooking(state, id)} />
}

function BookingCancelledContent({ id, initial }: { id: number; initial: MemberBookingRecord | null }) {
  const loader = useCallback(async (signal: AbortSignal) => {
    if (initial?.status === 'CANCELLED') return initial
    return (await memberBookingsApi.my('PAST', signal)).find((booking) => booking.id === id && booking.status === 'CANCELLED') ?? null
  }, [id, initial])
  const [resource, reload] = useMemberResource(loader)
  if (resource.status === 'loading') return <div className="page browse-page"><MemberScheduleNav /><PageLoader label="Loading cancellation result" /></div>
  if (resource.status === 'error') return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Cancellation result" /><BrowseError error={resource.error} onRetry={reload} /></div>
  if (!resource.data) return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Cancellation not confirmed" /><Card><div className="booking-error" role="alert"><p>This booking is not listed as cancelled. Refresh My Bookings to confirm its current status.</p></div><Link className={buttonClassName()} to="/member/bookings">My Bookings</Link></Card></div>

  const booking = resource.data
  return <div className="page browse-page booking-flow">
    <MemberScheduleNav />
    <PageHeader eyebrow="Member / My Bookings" title="Booking cancelled" description="The booking service confirmed your cancellation." />
    <Card className="booking-confirmation">
      <div className="booking-confirmation__mark booking-confirmation__mark--cancelled" aria-hidden="true" />
      <Badge>Cancelled</Badge>
      <h2>{bookingTitle(booking)}</h2>
      <p className="booking-code">Booking code: <strong>{booking.bookingCode}</strong></p>
      <dl className="browse-facts booking-confirmation__facts"><div><dt>Date</dt><dd>{bookingDate(booking.sessionDate)}</dd></div><div><dt>Time</dt><dd>{bookingTime(booking.startTime)} - {bookingTime(booking.endTime)} (Vietnam time)</dd></div><div><dt>Cancelled by</dt><dd>{actorName(booking)}</dd></div>{booking.cancelledAt && <div><dt>Cancelled at</dt><dd>{booking.cancelledAt.replace('T', ' ').slice(0, 16)}</dd></div>}</dl>
      <p>The booking service has released your reserved package session. You can review the updated status in Past bookings.</p>
      <div className="browse-actions booking-actions"><Link className={buttonClassName()} to="/member/bookings?when=PAST">View past bookings</Link><Link className={buttonClassName('secondary')} to="/member/schedule">Browse schedule</Link></div>
    </Card>
  </div>
}
