import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/errors'
import { memberBookingsApi, type BookingWhen, type MemberBookingRecord } from '../../api/memberBookingsApi'
import { memberApi, type PackageRegistration } from '../../api/memberApi'
import { tokenStorage } from '../../api/tokenStorage'
import { Badge, Button, Card, EmptyState, PageHeader, PageLoader, buttonClassName } from '../../components/ui'
import { useAuth } from '../../auth/useAuth'
import { BrowseError } from './BrowseError'
import { MemberScheduleNav } from './MemberScheduleNav'
import { useMemberResource } from './useMemberResource'
import { bookingDate, bookingErrorMessage, bookingTime, cancellationDeadline } from './bookingPresentation'

function routeId(value: string | undefined): number | null {
  if (!value || !/^\d+$/.test(value)) return null
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 ? number : null
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
  const { user } = useAuth()
  const memberId = user?.id ?? null
  const [params, setParams] = useSearchParams()
  const when: BookingWhen = params.get('when') === 'PAST' ? 'PAST' : 'UPCOMING'
  const loader = useCallback(async (signal: AbortSignal) => {
    if (memberId === null) return []
    return (await memberBookingsApi.my(when, signal)).filter((booking) => booking.memberId === memberId)
  }, [memberId, when])
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
    <div className="browse-actions booking-actions">{when === 'UPCOMING' && booking.status === 'CONFIRMED' && <Link className={buttonClassName('secondary')} to={cancelHref}>Cancel booking</Link>}{when === 'PAST' && booking.status === 'CONFIRMED' && <span className="browse-muted">Session completed</span>}</div>
  </article>
}

export function BookingCancellationPage() {
  const { user } = useAuth()
  const memberId = user?.id ?? null
  const { bookingId: rawId } = useParams()
  const id = routeId(rawId)
  if (!id) return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Booking unavailable" /><Card><p role="alert">Choose a valid booking to cancel.</p><Link className={buttonClassName()} to="/member/bookings">Back to My Bookings</Link></Card></div>
  return <BookingCancellationContent key={`${id}-${memberId ?? 'unknown'}`} id={id} memberId={memberId} />
}

type CancellationState = 'ready' | 'submitting' | 'checking' | 'unknown' | 'confirmed' | 'rejected' | 'complete'

function BookingCancellationContent({ id, memberId }: { id: number; memberId: number | null }) {
  const navigate = useNavigate()
  const loader = useCallback(async (signal: AbortSignal) => {
    if (memberId === null) return null
    return memberBookingsApi.findMine(id, memberId, signal)
  }, [id, memberId])
  const [resource, reload] = useMemberResource(loader)
  const [state, setState] = useState<CancellationState>('ready')
  const [checkedBooking, setCheckedBooking] = useState<MemberBookingRecord | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [session] = useState(() => ({ epoch: tokenStorage.getEpoch(), id: tokenStorage.getSessionId() }))
  const mounted = useRef(false)
  const statusController = useRef<AbortController | null>(null)
  const submitted = useRef(false)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      statusController.current?.abort()
    }
  }, [])

  // Check storage as well as mounting: the new account profile may still be loading.
  function isCurrent() {
    return mounted.current && tokenStorage.getEpoch() === session.epoch && tokenStorage.getSessionId() === session.id
  }

  async function reconcileCancellation() {
    if (memberId === null || !isCurrent()) return
    const controller = new AbortController()
    statusController.current = controller
    setState('checking')
    try {
      const current = await memberBookingsApi.findMine(id, memberId, controller.signal)
      if (!isCurrent() || controller.signal.aborted) return
      if (!current || current.id !== id || current.memberId !== memberId) {
        throw new ApiError(0, 'No matching booking was found in your booking history.')
      }
      if (current.status === 'CANCELLED') {
        setState('complete')
        navigate(`/member/bookings/${id}/cancelled`, { replace: true })
      } else if (current.status === 'CONFIRMED') {
        setCheckedBooking(current)
        setState('confirmed')
      } else {
        throw new ApiError(0, 'The server did not confirm the current booking status.')
      }
    } catch (cause) {
      if (!isCurrent() || controller.signal.aborted) return
      setError(bookingErrorMessage(cause))
      setState('unknown')
    }
  }

  async function checkCancellationStatus() {
    if (submitted.current || state !== 'unknown' || !isCurrent()) return
    submitted.current = true
    setError(null)
    try {
      await reconcileCancellation()
    } finally {
      submitted.current = false
    }
  }

  async function confirmCancel() {
    const booking = checkedBooking ?? (resource.status === 'ready' ? resource.data : null)
    if (submitted.current || !isCurrent() || (state !== 'ready' && state !== 'confirmed')
      || memberId === null || !booking || booking.id !== id || booking.memberId !== memberId || booking.status !== 'CONFIRMED') return
    submitted.current = true
    setState('submitting')
    setError(null)
    try {
      const cancelled = await memberBookingsApi.cancel(id)
      if (!isCurrent()) return
      if (cancelled.id !== id || (cancelled.memberId != null && cancelled.memberId !== memberId) || cancelled.status !== 'CANCELLED') {
        throw new ApiError(0, 'The server did not confirm this booking cancellation.')
      }
      setState('complete')
      navigate(`/member/bookings/${id}/cancelled`, { replace: true })
    } catch (cause) {
      if (!isCurrent()) return
      // Match FE-10: a timeout can follow a committed mutation, even for HTTP 408.
      const definitive = cause instanceof ApiError && cause.status >= 400 && cause.status < 500 && cause.status !== 408
      if (definitive && cause.code !== 'ALREADY_CANCELLED') {
        setError(bookingErrorMessage(cause))
        setState('rejected')
      } else {
        await reconcileCancellation()
      }
    } finally {
      submitted.current = false
    }
  }

  if (resource.status === 'loading') return <div className="page browse-page"><MemberScheduleNav /><PageLoader label="Loading booking details" /></div>
  if (resource.status === 'error') return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Cancel booking" /><BrowseError error={resource.error} onRetry={reload} /></div>
  if (!resource.data) return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Booking not found" /><Card><p>This booking could not be found in your upcoming or past bookings.</p><Link className={buttonClassName()} to="/member/bookings">My Bookings</Link></Card></div>

  const booking = checkedBooking ?? resource.data
  const deadline = cancellationDeadline(booking)
  if (booking.status !== 'CONFIRMED') return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Booking cannot be cancelled" /><Card><Badge>Cancelled</Badge><p>This booking has already been cancelled.</p><Link className={buttonClassName()} to="/member/bookings?when=PAST">View past bookings</Link></Card></div>

  return <div className="page browse-page booking-flow">
    <MemberScheduleNav />
    <PageHeader eyebrow="Member / My Bookings" title="Cancel booking" description="Please confirm before cancelling this session." />
    <Card className="booking-cancel-card">
      <div className="booking-cancel-warning" role="note"><strong>Cancellation deadline</strong><p>{deadline ? `Cancel by ${deadline} Vietnam time. This is two hours before the session starts.` : 'The backend will verify whether this booking is within the cancellation period.'}</p><p>After the cutoff, the booking service will reject the request and the booking will remain confirmed.</p></div>
      <h2>{bookingTitle(booking)}</h2>
      <dl className="browse-facts booking-confirmation__facts"><div><dt>Booking code</dt><dd>{booking.bookingCode}</dd></div><div><dt>Date</dt><dd>{bookingDate(booking.sessionDate)}</dd></div><div><dt>Time</dt><dd>{bookingTime(booking.startTime)} - {bookingTime(booking.endTime)}</dd></div><div><dt>Package</dt><dd>{booking.packageName || 'Sport package'}</dd></div>{booking.trainingType !== 'SELF_TRAINING' && <div><dt>Coach</dt><dd>{booking.coachName || 'Not provided'}</dd></div>}<div><dt>Facility</dt><dd>{booking.facilityName || 'Not provided'}</dd></div></dl>
      <p>After an accepted cancellation, the booking service updates the package balance. The cancellation result will show the latest balance available from your package record.</p>
      {state === 'checking' && <p role="status">Checking cancellation status. Do not submit another cancellation.</p>}
      {state === 'unknown' && <div className="booking-error" role="alert"><strong>Cancellation outcome unknown</strong><p>The cancellation may have been accepted. Check its status or return to My Bookings before taking further action.</p>{error && <p>{error}</p>}</div>}
      {state === 'confirmed' && <div role="status"><strong>Booking currently confirmed</strong><p>A fresh booking history check shows this booking as confirmed. Review the details before choosing to try cancellation again.</p></div>}
      {state === 'rejected' && <div className="booking-error" role="alert"><strong>Cancellation request rejected</strong><p>{error}</p><p>Review My Bookings for the current status before taking further action.</p></div>}
      <div className="browse-actions booking-actions"><Link className={buttonClassName('secondary')} to="/member/bookings?when=UPCOMING">Keep booking</Link>{(state === 'unknown' || state === 'checking') && <Button variant="secondary" disabled={state === 'checking'} onClick={() => void checkCancellationStatus()}>Check cancellation status</Button>}<Button variant="danger" loading={state === 'submitting'} disabled={state !== 'ready' && state !== 'confirmed'} onClick={() => void confirmCancel()}>{state === 'confirmed' ? 'Try cancellation again' : 'Confirm cancellation'}</Button></div>
    </Card>
  </div>
}

export function BookingCancelledPage() {
  const { user } = useAuth()
  const memberId = user?.id ?? null
  const { bookingId: rawId } = useParams()
  const id = routeId(rawId)
  if (!id) return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Cancellation unavailable" /><Card><p role="alert">Choose a valid booking.</p><Link className={buttonClassName()} to="/member/bookings">My Bookings</Link></Card></div>
  return <BookingCancelledContent key={`${id}-${memberId ?? 'unknown'}`} id={id} memberId={memberId} />
}

interface VerifiedCancellation {
  booking: MemberBookingRecord
  registration: PackageRegistration | null
}

function BookingCancelledContent({ id, memberId }: { id: number; memberId: number | null }) {
  const loader = useCallback(async (signal: AbortSignal) => {
    if (memberId === null) return null
    const booking = await memberBookingsApi.findMine(id, memberId, signal)
    if (!booking || booking.status !== 'CANCELLED') return null
    if (booking.packageRegistrationId === null) return { booking, registration: null } satisfies VerifiedCancellation
    try {
      const registrations = await memberApi.myRegistrations({ signal })
      const registration = registrations.find((row) => row.id === booking.packageRegistrationId && row.memberId === memberId) ?? null
      return { booking, registration } satisfies VerifiedCancellation
    } catch (error) {
      if (signal.aborted) throw error
      return { booking, registration: null } satisfies VerifiedCancellation
    }
  }, [id, memberId])
  const [resource, reload] = useMemberResource(loader)
  if (resource.status === 'loading') return <div className="page browse-page"><MemberScheduleNav /><PageLoader label="Loading cancellation result" /></div>
  if (resource.status === 'error') return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Cancellation result" /><BrowseError error={resource.error} onRetry={reload} /></div>
  if (!resource.data) return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Cancellation not confirmed" /><Card><div className="booking-error" role="alert"><p>This booking is not listed as cancelled. Refresh My Bookings to confirm its current status.</p></div><Link className={buttonClassName()} to="/member/bookings">My Bookings</Link></Card></div>

  const { booking, registration } = resource.data
  return <div className="page browse-page booking-flow">
    <MemberScheduleNav />
    <PageHeader eyebrow="Member / My Bookings" title="Booking cancelled" description="The booking service confirmed your cancellation." />
    <Card className="booking-confirmation">
      <div className="booking-confirmation__mark booking-confirmation__mark--cancelled" aria-hidden="true" />
      <Badge>Cancelled</Badge>
      <h2>{bookingTitle(booking)}</h2>
      <p className="booking-code">Booking code: <strong>{booking.bookingCode}</strong></p>
      <dl className="browse-facts booking-confirmation__facts"><div><dt>Date</dt><dd>{bookingDate(booking.sessionDate)}</dd></div><div><dt>Time</dt><dd>{bookingTime(booking.startTime)} - {bookingTime(booking.endTime)} (Vietnam time)</dd></div><div><dt>Cancelled by</dt><dd>{actorName(booking)}</dd></div>{booking.cancelledAt && <div><dt>Cancelled at</dt><dd>{booking.cancelledAt.replace('T', ' ').slice(0, 16)}</dd></div>}</dl>
      {registration?.totalSessions === null ? <p>This package has no session limit. You can review its current status in My sport packages.</p> : registration && registration.remainingSessions !== null ? <p>Latest package balance: {registration.remainingSessions} sessions remaining.</p> : <p>The latest package balance is unavailable. Review My sport packages for the current count.</p>}
      <div className="browse-actions booking-actions"><Link className={buttonClassName()} to="/member/bookings?when=PAST">View past bookings</Link><Link className={buttonClassName('secondary')} to="/member/schedule">Browse schedule</Link><Link className={buttonClassName('secondary')} to="/member/membership">My sport packages</Link></div>
    </Card>
  </div>
}
