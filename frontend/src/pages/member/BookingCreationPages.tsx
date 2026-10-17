import { useCallback, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/errors'
import { memberBookingsApi, type MemberBookingRecord } from '../../api/memberBookingsApi'
import { memberApi, type PackageRegistration } from '../../api/memberApi'
import { memberSessionsApi, type SessionDetail } from '../../api/memberSessionsApi'
import { useAuth } from '../../auth/useAuth'
import { Badge, Button, Card, PageHeader, PageLoader, buttonClassName } from '../../components/ui'
import { BrowseError } from './BrowseError'
import { MemberScheduleNav } from './MemberScheduleNav'
import { browseId, safeBrowseReturn } from './scheduleFilters'
import { ELIGIBILITY_COPY, resolveSessionDetailState } from './sessionEligibility'
import { useMemberResource } from './useMemberResource'
import { bookingCalendarHref, bookingDate, bookingErrorMessage, bookingTime, eligibleRegistrations } from './bookingPresentation'

function routeId(value: string | null): number | null {
  if (!value || !/^\d+$/.test(value)) return null
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 ? number : null
}

function sessionLabel(detail: SessionDetail): string {
  return detail.session.className || `${detail.session.sportName} session`
}

interface VerifiedBooking {
  booking: MemberBookingRecord
  registration: PackageRegistration | null
}

function SessionFacts({ detail }: { detail: SessionDetail }) {
  const { session } = detail
  const self = session.trainingFormat === 'SELF_TRAINING'
  return <dl className="browse-facts booking-facts">
    <div><dt>Date</dt><dd>{bookingDate(session.sessionDate)}</dd></div>
    <div><dt>Time</dt><dd>{bookingTime(session.startTime)} - {bookingTime(session.endTime)}</dd></div>
    <div><dt>Sport</dt><dd>{session.sportName}</dd></div>
    <div><dt>Training</dt><dd>{self ? 'Self-training' : 'Coach-led'}</dd></div>
    {!self && <div><dt>Coach</dt><dd>{session.coachName || 'Not provided'}</dd></div>}
    <div><dt>Facility</dt><dd>{session.facilityName || 'Not provided'}</dd></div>
  </dl>
}

export function BookingOptionsPage() {
  const [params] = useSearchParams()
  const sessionId = browseId(params.get('sessionId'))
  const returnTo = safeBrowseReturn(params.get('returnTo'))
  if (!sessionId) return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Booking unavailable" /><Card><p role="alert">Choose a valid session from the schedule before booking.</p><Link className={buttonClassName()} to={returnTo}>Back to schedule</Link></Card></div>
  return <BookingOptionsContent key={sessionId} sessionId={sessionId} returnTo={returnTo} />
}

function BookingOptionsContent({ sessionId, returnTo }: { sessionId: number; returnTo: string }) {
  const loader = useCallback((signal: AbortSignal) => memberSessionsApi.detail(sessionId, signal), [sessionId])
  const [resource, reload] = useMemberResource(loader)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  if (resource.status === 'loading') return <div className="page browse-page"><MemberScheduleNav /><PageLoader label="Checking session eligibility" /></div>
  if (resource.status === 'error') return <div className="page browse-page"><MemberScheduleNav /><PageHeader eyebrow="Member / Booking" title="Booking options" /><BrowseError error={resource.error} onRetry={reload} /></div>

  const detail = resource.data
  const status = resolveSessionDetailState(detail)
  const options = eligibleRegistrations(detail)
  const selected = options.find((registration) => registration.registrationId === selectedId) ?? options[0]
  const canContinue = status === 'ELIGIBLE' && Boolean(selected)
  const reviewHref = canContinue
    ? `/member/bookings/review?${new URLSearchParams({ sessionId: String(sessionId), packageRegistrationId: String(selected.registrationId), returnTo })}`
    : null

  return <div className="page browse-page booking-flow">
    <MemberScheduleNav />
    <PageHeader eyebrow="Member / Booking" title={detail.session.trainingFormat === 'SELF_TRAINING' ? 'Booking options - Self-training' : 'Booking options'} description="Choose an eligible sport package for this session. Your package session is reserved when you confirm." />
    <div className="browse-detail booking-layout">
      <Card title={sessionLabel(detail)}>
        <SessionFacts detail={detail} />
        <div className="browse-eligibility" role="status">
          <Badge tone={status === 'ELIGIBLE' ? 'success' : 'warning'}>{status === 'ELIGIBLE' ? 'Eligible' : ELIGIBILITY_COPY[status].title}</Badge>
          {status !== 'ELIGIBLE' && <p>{ELIGIBILITY_COPY[status].message}</p>}
          {detail.session.trainingFormat === 'SELF_TRAINING' && <p className="notice notice--info">Self-training sessions do not have a coach assignment.</p>}
        </div>
      </Card>
      <Card title="Choose a package">
        {!canContinue ? <div className="booking-empty"><p>No eligible package registration is available for this session.</p><Link className={buttonClassName()} to={`/member/packages?${new URLSearchParams({ sport: String(detail.session.sportId), format: detail.session.trainingFormat })}`}>View other packages</Link></div> : <fieldset className="booking-package-options">
          <legend className="visually-hidden">Eligible packages</legend>
          {options.map((registration) => <label className={`booking-package${selected.registrationId === registration.registrationId ? ' booking-package--selected' : ''}`} key={registration.registrationId}>
            <input type="radio" name="packageRegistrationId" value={registration.registrationId} checked={selected.registrationId === registration.registrationId} onChange={() => setSelectedId(registration.registrationId)} />
            <span className="booking-package__body"><strong>{registration.packageName}</strong><span>Code {registration.registrationCode}</span><span>{registration.remainingSessions} sessions before booking</span><span>{registration.remainingSessions - 1} sessions after booking</span></span>
          </label>)}
        </fieldset>}
        {canContinue && <div className="booking-summary"><span>Additional payment</span><strong>0 VND</strong><p>One session will be deducted from the selected package after confirmation.</p></div>}
        <div className="browse-actions booking-actions">
          <Link className={buttonClassName('secondary')} to={returnTo}>Back to session</Link>
          {reviewHref && <Link className={buttonClassName()} to={reviewHref}>Review booking</Link>}
        </div>
      </Card>
    </div>
  </div>
}

export function BookingReviewPage() {
  const [params] = useSearchParams()
  const sessionId = routeId(params.get('sessionId'))
  const registrationId = routeId(params.get('packageRegistrationId'))
  const returnTo = safeBrowseReturn(params.get('returnTo'))
  if (!sessionId || !registrationId) return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Booking review unavailable" /><Card><p role="alert">Return to the session and choose an eligible package before reviewing.</p><Link className={buttonClassName()} to={returnTo}>Back to schedule</Link></Card></div>
  return <BookingReviewContent key={`${sessionId}-${registrationId}`} sessionId={sessionId} registrationId={registrationId} returnTo={returnTo} />
}

function BookingReviewContent({ sessionId, registrationId, returnTo }: { sessionId: number; registrationId: number; returnTo: string }) {
  const navigate = useNavigate()
  const loader = useCallback((signal: AbortSignal) => memberSessionsApi.detail(sessionId, signal), [sessionId])
  const [resource, reload] = useMemberResource(loader)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submitted = useRef(false)

  async function confirmBooking() {
    if (submitted.current) return
    submitted.current = true
    setSubmitting(true)
    setError(null)
    try {
      const booking = await memberBookingsApi.create({ sessionId, packageRegistrationId: registrationId })
      navigate(`/member/bookings/confirmed/${booking.id}`, { replace: true, state: { returnTo } })
    } catch (cause) {
      submitted.current = false
      setError(bookingErrorMessage(cause))
    } finally {
      setSubmitting(false)
    }
  }

  if (resource.status === 'loading') return <div className="page browse-page"><MemberScheduleNav /><PageLoader label="Reviewing session availability" /></div>
  if (resource.status === 'error') return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Booking review" /><BrowseError error={resource.error} onRetry={reload} /></div>

  const detail = resource.data
  const selected = eligibleRegistrations(detail).find((registration) => registration.registrationId === registrationId)
  return <div className="page browse-page booking-flow">
    <MemberScheduleNav />
    <PageHeader eyebrow="Member / Booking" title="Review booking" description="Confirm the session and eligible package before reserving your place." />
    {!selected ? <Card><div className="booking-error" role="alert"><strong>Package eligibility changed</strong><p>Refresh the session and select an eligible package again. No booking was created.</p></div><div className="browse-actions"><Link className={buttonClassName()} to={`/member/bookings/new?${new URLSearchParams({ sessionId: String(sessionId), returnTo })}`}>Choose another package</Link><Button variant="secondary" onClick={reload}>Refresh eligibility</Button></div></Card> : <div className="browse-detail booking-layout">
      <Card title={sessionLabel(detail)}><SessionFacts detail={detail} /><div className="booking-summary"><span>Package</span><strong>{selected.packageName}</strong><span>Remaining sessions</span><strong>{selected.remainingSessions} before / {selected.remainingSessions - 1} after</strong><span>Additional payment</span><strong>0 VND</strong><p>Confirming reserves one package session immediately.</p></div></Card>
      <Card title="Confirm your place">
        <p>Review the session information. The booking is subject to the latest seat and package availability when submitted.</p>
        {error && <div className="booking-error" role="alert"><strong>Booking was not completed</strong><p>{error}</p><p>No confirmation was recorded. You can review availability and try again.</p></div>}
        <div className="browse-actions booking-actions"><Link className={buttonClassName('secondary')} to={`/member/bookings/new?${new URLSearchParams({ sessionId: String(sessionId), returnTo })}`}>Back to options</Link><Button loading={submitting} disabled={submitting} onClick={() => void confirmBooking()}>Confirm booking</Button></div>
      </Card>
    </div>}
  </div>
}

export function BookingConfirmedPage() {
  const { user } = useAuth()
  const memberId = user?.id ?? null
  const { state } = useLocation()
  const { bookingId: rawBookingId } = useParams()
  const bookingId = routeId(rawBookingId ?? null)
  const stateData = state && typeof state === 'object' ? state as { returnTo?: unknown } : {}
  const returnTo = safeBrowseReturn(typeof stateData.returnTo === 'string' ? stateData.returnTo : '/member/schedule')
  const loader = useCallback(async (signal: AbortSignal) => {
    if (!bookingId) throw new ApiError(0, 'Choose a valid booking confirmation.')
    if (memberId === null) return null
    const booking = await memberBookingsApi.findMine(bookingId, memberId, signal)
    if (!booking || booking.status !== 'CONFIRMED') return null
    let registration: PackageRegistration | null = null
    if (booking.packageRegistrationId !== null) {
      try {
        registration = (await memberApi.myRegistrations({ signal })).find((row) => row.id === booking.packageRegistrationId && row.memberId === memberId) ?? null
      } catch (error) {
        if (signal.aborted) throw error
      }
    }
    return { booking, registration } satisfies VerifiedBooking
  }, [bookingId, memberId])
  const [resource] = useMemberResource(loader)
  if (resource.status === 'loading') return <div className="page browse-page"><MemberScheduleNav /><PageLoader label="Loading booking confirmation" /></div>
  if (resource.status === 'error') return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Booking confirmation" /><BrowseError error={resource.error} /><Link className={buttonClassName()} to="/member/bookings">My Bookings</Link></div>
  if (!resource.data) return <div className="page browse-page"><MemberScheduleNav /><PageHeader title="Booking confirmation" /><Card><p role="status">This booking is not in your upcoming list. It may have been cancelled or changed.</p><Link className={buttonClassName()} to="/member/bookings?when=UPCOMING">View My Bookings</Link></Card></div>

  const { booking, registration } = resource.data
  const self = booking.trainingType === 'SELF_TRAINING'
  const calendarHref = bookingCalendarHref(booking)
  return <div className="page browse-page booking-flow">
    <MemberScheduleNav />
    <PageHeader eyebrow="Member / Booking" title="Booking confirmed" description="Your place is reserved. The booking appears in your upcoming sessions." />
    <Card className="booking-confirmation">
      <div className="booking-confirmation__mark" aria-hidden="true" />
      <Badge tone="success">Confirmed</Badge>
      <h2>{booking.className || `${booking.sportName || 'Sport'} session`}</h2>
      <p className="booking-code">Booking code: <strong>{booking.bookingCode}</strong></p>
      <dl className="browse-facts booking-confirmation__facts">
        <div><dt>Date</dt><dd>{bookingDate(booking.sessionDate)}</dd></div>
        <div><dt>Time</dt><dd>{bookingTime(booking.startTime)} - {bookingTime(booking.endTime)} (Vietnam time)</dd></div>
        <div><dt>Sport</dt><dd>{booking.sportName || 'Not provided'}</dd></div>
        <div><dt>Training</dt><dd>{self ? 'Self-training' : 'Coach-led'}</dd></div>
        {!self && <div><dt>Coach</dt><dd>{booking.coachName || 'Not provided'}</dd></div>}
        <div><dt>Facility</dt><dd>{booking.facilityName || 'Not provided'}</dd></div>
        <div><dt>Package</dt><dd>{booking.packageName || 'Sport package'}</dd></div>
        {registration?.totalSessions === null ? <div><dt>Package balance</dt><dd>No session limit</dd></div> : registration && registration.remainingSessions !== null ? <div><dt>Package sessions remaining</dt><dd>{registration.remainingSessions}</dd></div> : <div><dt>Package balance</dt><dd>Latest balance unavailable</dd></div>}
      </dl>
      <p className="browse-muted">The booking service confirms the reservation and manages package balance updates. View My sport packages for the current package record.</p>
      <div className="browse-actions booking-actions">
        <Link className={buttonClassName()} to="/member/bookings?when=UPCOMING">View My Bookings</Link>
        <Link className={buttonClassName('secondary')} to={returnTo}>Browse schedule</Link>
        <Link className={buttonClassName('secondary')} to="/member/membership">My sport packages</Link>
        {calendarHref && <a className={buttonClassName('secondary')} href={calendarHref} download={`sportify-${booking.bookingCode}.ics`}>Add to calendar</a>}
      </div>
    </Card>
  </div>
}
