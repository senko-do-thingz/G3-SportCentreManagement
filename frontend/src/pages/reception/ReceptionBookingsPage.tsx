import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../../api/errors'
import type { BookingWhen, MemberBookingRecord } from '../../api/memberBookingsApi'
import { memberSessionsApi, type BrowseSession } from '../../api/memberSessionsApi'
import { receptionApi, type MemberSummary } from '../../api/receptionApi'
import { receptionBookingsApi, type StaffBookingStatus } from '../../api/receptionBookingsApi'
import { Badge, Button, Card, EmptyState, ErrorState, Modal, PageHeader, PageLoader, Pagination, Select, StatusBadge, useToast } from '../../components/ui'
import { browseAddDays, browseDate, browseDay, browseMonday, browseToday, validBrowseDate } from '../member/memberScheduleDates'
import { ELIGIBILITY_COPY, resolveSessionDetailState } from '../member/sessionEligibility'
import { bookingDate, bookingTime, eligibleRegistrations } from '../member/bookingPresentation'
import { MemberHeader, MemberPicker } from './ReceptionMember'
import { useResource } from '../../hooks/useResource'

const PAGE_SIZE = 20

function positiveId(value: string | null): number | null {
  if (!value || !/^\d+$/.test(value)) return null
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

function withQuery(params: URLSearchParams, values: Record<string, string | null>): URLSearchParams {
  const next = new URLSearchParams(params)
  for (const [key, value] of Object.entries(values)) {
    if (value === null || value === '') next.delete(key)
    else next.set(key, value)
  }
  return next
}

function bookingTitle(booking: MemberBookingRecord): string {
  return booking.className || `${booking.sportName || 'Sport'} session`
}

interface CancellationTarget {
  memberId: number
  booking: MemberBookingRecord
}

/**
 * True only for 4xx responses that mean the backend definitively rejected the mutation.
 * HTTP 408 Request Timeout is excluded: the server may have committed the booking or the
 * cancellation before the timeout was reported, so the caller must reconcile member booking
 * history instead of announcing a failure or inviting a blind retry of POST or DELETE.
 */
function isDefinitiveMutationRejection(error: unknown): boolean {
  return error instanceof ApiError && error.status >= 400 && error.status < 500 && error.status !== 408
}

export function ReceptionBookingsPage() {
  const [params] = useSearchParams()
  const memberId = positiveId(params.get('memberId'))
  return <ReceptionBookingsContent key={memberId ?? 'no-member'} />
}

function ReceptionBookingsContent() {
  const [params, setParams] = useSearchParams()
  const memberId = positiveId(params.get('memberId'))
  const query = params.get('q') ?? ''
  const when: BookingWhen = params.get('when') === 'PAST' ? 'PAST' : 'UPCOMING'
  const statusValue = params.get('status')
  const status: StaffBookingStatus | undefined = statusValue === 'CONFIRMED' || statusValue === 'CANCELLED' ? statusValue : undefined
  const page = positiveId(params.get('page')) ?? 0
  const loader = useCallback(async (signal: AbortSignal) => {
    if (!memberId) throw new Error('Choose a member to view booking history.')
    const [member, bookings] = await Promise.all([
      receptionApi.memberSummaryById(memberId, { signal }),
      receptionBookingsApi.forMember({ memberId, when, status, page, size: PAGE_SIZE, signal }),
    ])
    return { member, bookings }
  }, [memberId, when, status, page])
  const [resource, reloadResource] = useResource(loader)
  const [cancelTarget, setCancelTarget] = useState<CancellationTarget | null>(null)
  const [cancelling, setCancelling] = useState(false)
  const [unverifiedCancellation, setUnverifiedCancellation] = useState<{ memberId: number; bookingId: number } | null>(null)
  const [checkingCancellation, setCheckingCancellation] = useState(false)
  const [cancellationCheckError, setCancellationCheckError] = useState<string | null>(null)
  const cancelSubmitted = useRef(false)
  const mounted = useRef(true)
  const toast = useToast()

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  const selectMember = useCallback((member: MemberSummary) => {
    setParams((current) => withQuery(current, { memberId: String(member.userId), page: null, status: null }))
  }, [setParams])

  async function verifyCancellation() {
    if (!memberId || !unverifiedCancellation || unverifiedCancellation.memberId !== memberId || checkingCancellation) return
    setCheckingCancellation(true)
    setCancellationCheckError(null)
    try {
      const booking = await receptionBookingsApi.findForMember({
        memberId,
        matches: (item) => item.id === unverifiedCancellation.bookingId,
      })
      if (!mounted.current) return
      if (!booking || booking.memberId !== memberId) {
        throw new ApiError(0, 'The booking was not found in the selected member history.')
      }
      setUnverifiedCancellation(null)
      if (booking.status === 'CANCELLED') {
        toast.success('Booking cancelled', `${booking.bookingCode} is now cancelled.`)
      } else {
        toast.error('Booking remains confirmed', 'The latest member history still shows this booking as confirmed. Review it before attempting cancellation again.')
      }
      reloadResource()
    } catch (error) {
      if (!mounted.current) return
      setCancellationCheckError(`The cancellation status is still unverified. Refresh member history before trying another action. ${getErrorMessage(error)}`)
    } finally {
      if (mounted.current) setCheckingCancellation(false)
    }
  }

  async function confirmCancel() {
    if (!cancelTarget || cancelSubmitted.current) return
    const target = cancelTarget.booking
    const targetMemberId = cancelTarget.memberId
    if (!memberId || targetMemberId !== memberId || target.memberId !== memberId) {
      setCancelTarget(null)
      toast.error('Selected member changed', 'Refresh member history and select the booking again before cancelling.')
      return
    }
    cancelSubmitted.current = true
    setCancelling(true)
    try {
      const result = await receptionBookingsApi.cancel(target.id)
      if (!mounted.current) return
      if (result.id !== target.id || result.memberId !== targetMemberId || result.status !== 'CANCELLED') {
        throw new ApiError(0, 'The server did not confirm this member booking cancellation.')
      }
      setCancelTarget(null)
      setUnverifiedCancellation(null)
      toast.success('Booking cancelled', `${result.bookingCode} is now cancelled.`)
      reloadResource()
    } catch (error) {
      if (!mounted.current) return
      if (isDefinitiveMutationRejection(error)) {
        toast.error('Cancellation was rejected', getErrorMessage(error))
      } else {
        let current: MemberBookingRecord | null = null
        if (targetMemberId) {
          try {
            current = await receptionBookingsApi.findForMember({ memberId: targetMemberId, matches: (item) => item.id === target.id })
          } catch {
            current = null
          }
        }
        if (!mounted.current) return
        if (current?.memberId === targetMemberId && current.status === 'CANCELLED') {
          setCancelTarget(null)
          toast.success('Booking cancelled', `${current.bookingCode} is now cancelled.`)
          reloadResource()
        } else if (current?.memberId === targetMemberId && current.status === 'CONFIRMED') {
          setCancelTarget(null)
          toast.error('Booking remains confirmed', 'The latest member history still shows this booking as confirmed. Review it before attempting cancellation again.')
          reloadResource()
        } else {
          setCancelTarget(null)
          setUnverifiedCancellation({ memberId: targetMemberId, bookingId: target.id })
          setCancellationCheckError(null)
        }
      }
    } finally {
      if (mounted.current) {
        cancelSubmitted.current = false
        setCancelling(false)
      }
    }
  }

  return <div className="page reception-bookings-page">
    <PageHeader eyebrow="Front desk / Flow 2" title="Member Booking Search" description="Find a member to review or manage their session bookings." />
    <Card title="Find member">
      <MemberPicker
        key={query}
        initialQuery={query}
        onSearch={(nextQuery) => setParams(new URLSearchParams({ q: nextQuery }))}
        onSelect={selectMember}
      />
    </Card>

    {memberId && <Card title="Member bookings" actions={resource.status === 'ready' ? <Link className="btn btn--primary" to={`/reception/bookings/new?${new URLSearchParams({ memberId: String(memberId), q: query })}`}>Book for member</Link> : undefined}>
      {resource.status === 'loading' ? <PageLoader label="Loading member bookings" /> : resource.status === 'error' ? <ErrorState title="We could not load this member's bookings" error={resource.error} onRetry={reloadResource} /> : <>
        <section className="reception-booking-member" aria-label="Selected member">
          <MemberHeader member={resource.data.member} />
        </section>
        {unverifiedCancellation?.memberId === memberId && <div className="booking-error" role="alert">
          <strong>Cancellation outcome not verified</strong>
          <p>{cancellationCheckError ?? 'The cancellation response was inconclusive. Check the latest member booking history before taking another action.'}</p>
          <Button variant="secondary" loading={checkingCancellation} disabled={checkingCancellation} onClick={() => void verifyCancellation()}>Check booking status</Button>
        </div>}
        <div className="booking-tabs" role="tablist" aria-label="Member booking period">
          <button type="button" role="tab" aria-selected={when === 'UPCOMING'} className={`booking-tab${when === 'UPCOMING' ? ' booking-tab--active' : ''}`} onClick={() => setParams((current) => withQuery(current, { when: 'UPCOMING', page: null }))}>Upcoming</button>
          <button type="button" role="tab" aria-selected={when === 'PAST'} className={`booking-tab${when === 'PAST' ? ' booking-tab--active' : ''}`} onClick={() => setParams((current) => withQuery(current, { when: 'PAST', page: null }))}>Past</button>
        </div>
        <div className="reception-booking-filters">
          <Select label="Booking status" placeholder="All statuses" value={status ?? ''} options={[{ value: 'CONFIRMED', label: 'Confirmed' }, { value: 'CANCELLED', label: 'Cancelled' }]} onChange={(event) => setParams((current) => withQuery(current, { status: event.target.value || null, page: null }))} />
          <Button variant="ghost" onClick={reloadResource}>Refresh bookings</Button>
        </div>
        {resource.data.bookings.content.length === 0 ? <EmptyState title={`No ${when.toLowerCase()} bookings`} description="Bookings for this member will appear here." action={<Link className="btn btn--secondary" to={`/reception/bookings/new?${new URLSearchParams({ memberId: String(memberId), q: query })}`}>Book a session</Link>} /> : <div className="booking-list">
          {resource.data.bookings.content.map((booking) => <article className="booking-history-card" key={booking.id}>
            <div className="booking-history-card__heading">
              <div><p className="booking-history-card__date">{bookingDate(booking.sessionDate)} / {bookingTime(booking.startTime)} - {bookingTime(booking.endTime)}</p><h2>{bookingTitle(booking)}</h2></div>
              <StatusBadge status={booking.status} />
            </div>
            <p className="browse-muted">{booking.sportName || 'Sport not provided'}{booking.trainingType !== 'SELF_TRAINING' && ` - ${booking.coachName || 'Coach not provided'}`} - {booking.facilityName || 'Facility not provided'}</p>
            <dl className="browse-facts booking-facts"><div><dt>Booking code</dt><dd>{booking.bookingCode}</dd></div><div><dt>Package</dt><dd>{booking.packageName || 'Not provided'}</dd></div><div><dt>Booked</dt><dd>{booking.bookedAt?.replace('T', ' ').slice(0, 16) || 'Not provided'}</dd></div></dl>
            {booking.status === 'CANCELLED' && <p className="browse-muted">Cancelled {booking.cancelledAt ? `on ${booking.cancelledAt.replace('T', ' ').slice(0, 16)}` : ''}{booking.cancelledByName ? ` by ${booking.cancelledByName}` : ''}</p>}
            {booking.status === 'CONFIRMED' && when === 'UPCOMING' && booking.id !== unverifiedCancellation?.bookingId && <div className="browse-actions booking-actions"><Button variant="danger" onClick={() => setCancelTarget({ memberId, booking })}>Cancel booking</Button></div>}
          </article>)}
        </div>}
        {resource.data.bookings.totalElements > PAGE_SIZE && <Pagination page={resource.data.bookings.page} pageSize={resource.data.bookings.size} totalItems={resource.data.bookings.totalElements} onPageChange={(nextPage) => setParams((current) => withQuery(current, { page: String(nextPage) }))} />}
      </>}
    </Card>}

    <Modal open={Boolean(cancelTarget)} onClose={() => !cancelling && setCancelTarget(null)} title="Cancel member booking" description="The booking can be cancelled only if backend cutoff rules allow it. Package credit restoration depends on the registration's current state and backend rules." footer={<><Button variant="secondary" disabled={cancelling} onClick={() => setCancelTarget(null)}>Keep booking</Button><Button variant="danger" loading={cancelling} disabled={cancelling} onClick={() => void confirmCancel()}>Confirm cancellation</Button></>}>
      {cancelTarget && <div className="booking-cancel-warning"><strong>{bookingTitle(cancelTarget.booking)}</strong><p>{bookingDate(cancelTarget.booking.sessionDate)} at {bookingTime(cancelTarget.booking.startTime)} Vietnam time</p><p>This action cannot be undone. The backend will confirm whether this booking can still be cancelled.</p></div>}
    </Modal>
  </div>
}

function ReceptionEligibilityCard({ memberId, query, session }: { memberId: number; query: string; session: BrowseSession }) {
  const navigate = useNavigate()
  const sessionId = session.id
  const [selectedRegistrationId, setSelectedRegistrationId] = useState<number | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [outcomeUnknown, setOutcomeUnknown] = useState(false)
  const submitted = useRef(false)
  const mounted = useRef(true)
  const loader = useCallback((signal: AbortSignal) => memberSessionsApi.eligibilityForMember(sessionId, memberId, signal), [sessionId, memberId])
  const [eligibility, reloadEligibility] = useResource(loader)

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  function openConfirmation(booking: MemberBookingRecord) {
    const search = new URLSearchParams({ memberId: String(memberId), q: query }).toString()
    navigate(`/reception/bookings/confirmed/${booking.id}?${search}`, { replace: true, state: { booking, returnTo: `/reception/bookings?${search}` } })
  }

  async function createBooking(registrationId: number) {
    if (submitted.current) return
    submitted.current = true
    setSubmitting(true)
    setSubmitError(null)
    setOutcomeUnknown(false)
    try {
      const booking = await receptionBookingsApi.createForMember({ memberId, sessionId, packageRegistrationId: registrationId })
      if (!mounted.current) return
      if (booking.status !== 'CONFIRMED' || booking.memberId !== memberId || booking.sessionId !== sessionId || booking.packageRegistrationId !== registrationId) {
        throw new ApiError(0, 'The server did not confirm this member booking.')
      }
      openConfirmation(booking)
    } catch (error) {
      if (!mounted.current) return
      if (isDefinitiveMutationRejection(error)) {
        setSubmitError(getErrorMessage(error))
        submitted.current = false
      } else {
        try {
          const committedBooking = await receptionBookingsApi.findForMember({
            memberId,
            matches: (item) => item.memberId === memberId
              && item.sessionId === sessionId
              && item.packageRegistrationId === registrationId
              && item.status === 'CONFIRMED',
          })
          if (!mounted.current) return
          if (committedBooking) {
            openConfirmation(committedBooking)
            return
          }
        } catch {
          // The mutation result and its follow-up read are both inconclusive.
        }
        setOutcomeUnknown(true)
        setSubmitError('The booking outcome could not be verified. Check the member booking history before attempting another booking.')
      }
    } finally {
      if (mounted.current) setSubmitting(false)
    }
  }

  return <Card title="Check eligibility and choose package">
    {eligibility.status === 'loading' ? <PageLoader label="Checking member eligibility" /> : eligibility.status === 'error' ? <ErrorState title="Eligibility could not be checked" error={eligibility.error} onRetry={reloadEligibility} /> : (() => {
      const detail = eligibility.data
      const state = resolveSessionDetailState(detail)
      const registrations = eligibleRegistrations(detail)
      const selected = registrations.find((item) => item.registrationId === selectedRegistrationId) ?? registrations[0]
      const canBook = state === 'ELIGIBLE' && Boolean(selected)
      return <>
        <div className="browse-eligibility" role="status"><Badge tone={state === 'ELIGIBLE' ? 'success' : 'warning'}>{state === 'ELIGIBLE' ? 'Eligible' : ELIGIBILITY_COPY[state].title}</Badge><p>{state === 'ELIGIBLE' ? 'A suitable paid package is available. The booking request will recheck seat and credit availability.' : ELIGIBILITY_COPY[state].message}</p></div>
        {registrations.length ? <fieldset className="booking-package-options"><legend className="visually-hidden">Eligible package registrations</legend>{registrations.map((registration) => <label className={`booking-package${selected?.registrationId === registration.registrationId ? ' booking-package--selected' : ''}`} key={registration.registrationId}><input type="radio" name="packageRegistrationId" value={registration.registrationId} checked={selected?.registrationId === registration.registrationId} onChange={() => setSelectedRegistrationId(registration.registrationId)} /><span className="booking-package__body"><strong>{registration.packageName}</strong><span>Registration {registration.registrationCode}</span><span>{registration.remainingSessions} sessions before booking / {registration.remainingSessions - 1} after</span><span>Valid {bookingDate(registration.startDate)} - {bookingDate(registration.endDate)}</span></span></label>)}</fieldset> : <p className="notice notice--info">A paid eligible package is required. Single-visit checkout is not part of the available booking API.</p>}
        {submitError && <div className="booking-error" role="alert"><strong>{outcomeUnknown ? 'Booking outcome not verified' : 'Booking not completed'}</strong><p>{submitError}</p>{outcomeUnknown && <Link className="btn btn--secondary" to={`/reception/bookings?${new URLSearchParams({ memberId: String(memberId), q: query })}`}>Check member bookings</Link>}</div>}
        <div className="browse-actions booking-actions"><Button loading={submitting} disabled={!canBook || submitting || outcomeUnknown} onClick={() => selected && void createBooking(selected.registrationId)}>Confirm booking</Button></div>
      </>
    })()}
  </Card>
}

export function ReceptionAssistedBookingPage() {
  const [params, setParams] = useSearchParams()
  const memberId = positiveId(params.get('memberId'))
  const query = params.get('q') ?? ''
  const dateValue = params.get('week') ?? browseToday()
  const week = validBrowseDate(dateValue) ? browseMonday(dateValue) : browseMonday(browseToday())
  const weekEnd = browseAddDays(week, 6)
  const requestedSessionId = positiveId(params.get('sessionId'))
  const baseLoader = useCallback(async (signal: AbortSignal) => {
    if (!memberId) throw new Error('Choose a member before booking.')
    const [member, sessions] = await Promise.all([
      receptionApi.memberSummaryById(memberId, { signal }),
      memberSessionsApi.all({ from: week, to: weekEnd }, signal),
    ])
    return { member, sessions }
  }, [memberId, week, weekEnd])
  const [base, reloadBase] = useResource(baseLoader)
  const chosenSession = base.status === 'ready' ? base.data.sessions.find((session) => session.id === requestedSessionId) ?? null : null
  function changeWeek(date: string) {
    setParams((current) => withQuery(current, { week: date, sessionId: null }))
  }

  if (!memberId) return <div className="page reception-bookings-page"><PageHeader eyebrow="Front desk / Flow 2" title="Book for member" /><Card><ErrorState title="Choose a member first" message="Search for a member before creating a booking." /><Link className="btn btn--primary" to="/reception/bookings">Member booking search</Link></Card></div>
  return <div className="page reception-bookings-page">
    <PageHeader eyebrow="Front desk / Flow 2" title="Book for Member" description="Choose an available session and a package that covers it. Booking uses Vietnam time (UTC+7)." actions={<Link className="btn btn--secondary" to={`/reception/bookings?${new URLSearchParams({ memberId: String(memberId), q: query })}`}>Back to bookings</Link>} />
      {base.status === 'loading' ? <PageLoader label="Loading member and session options" /> : base.status === 'error' ? <ErrorState title="We could not load booking options" error={base.error} onRetry={reloadBase} /> : <>
      <Card title="Member"><MemberHeader member={base.data.member} /><p className="browse-muted">The selected member is attached to the booking request. Package credits are checked again when the booking is submitted.</p></Card>
      <Card title="Choose a session">
        <div className="browse-actions reception-booking-week"><Button variant="secondary" onClick={() => changeWeek(browseAddDays(week, -7))}>Previous week</Button><Button variant="secondary" onClick={() => changeWeek(browseMonday(browseToday()))}>This week</Button><Button variant="secondary" onClick={() => changeWeek(browseAddDays(week, 7))}>Next week</Button><strong>{browseDate(week)} - {browseDate(weekEnd)}</strong></div>
        {base.data.sessions.length === 0 ? <EmptyState title="No sessions this week" description="Try another week to find a published session." /> : <div className="reception-session-list" role="radiogroup" aria-label="Available sessions">
          {base.data.sessions.map((session) => {
            const available = session.status === 'PUBLISHED' && !session.full && session.freeSeats > 0
            const selected = session.id === requestedSessionId
            return <button type="button" role="radio" aria-checked={selected} disabled={!available} className={`reception-session-choice${selected ? ' reception-session-choice--selected' : ''}`} key={session.id} onClick={() => setParams((current) => withQuery(current, { sessionId: String(session.id) }))}>
              <span><strong>{session.className || `${session.sportName} session`}</strong><small>{browseDay(session.sessionDate)} {bookingDate(session.sessionDate)} / {bookingTime(session.startTime)} - {bookingTime(session.endTime)}</small><small>{session.sportName} - {session.trainingFormat === 'SELF_TRAINING' ? 'Self-training' : `Coach-led${session.coachName ? ` - ${session.coachName}` : ''}`} - {session.facilityName || 'Facility not provided'}</small></span>
              <Badge tone={available ? 'success' : 'neutral'}>{available ? `${session.freeSeats} seats` : session.status === 'PUBLISHED' ? 'Full' : session.status}</Badge>
            </button>
          })}
        </div>}
      </Card>
      {chosenSession && memberId && <ReceptionEligibilityCard key={`${memberId}-${chosenSession.id}`} memberId={memberId} query={query} session={chosenSession} />}
    </>}
  </div>
}

export function ReceptionBookingConfirmedPage() {
  const location = useLocation()
  const [params] = useSearchParams()
  const bookingId = positiveId(location.pathname.split('/').at(-1) ?? null)
  const memberId = positiveId(params.get('memberId'))
  const stateValue = location.state && typeof location.state === 'object' ? location.state as { booking?: unknown; returnTo?: unknown } : {}
  const confirmationLoader = useCallback(async (signal: AbortSignal) => {
    if (!bookingId || !memberId) throw new Error('Booking confirmation details are unavailable. Return to member search and check the booking history.')
    const booking = await receptionBookingsApi.findForMember({ memberId, matches: (item) => item.id === bookingId, signal })
    return booking?.memberId === memberId && booking.status === 'CONFIRMED' ? booking : null
  }, [bookingId, memberId])
  const [resource, reloadConfirmation] = useResource(confirmationLoader)

  const returnTo = typeof stateValue.returnTo === 'string' && stateValue.returnTo.startsWith('/reception/bookings')
    ? stateValue.returnTo : memberId ? `/reception/bookings?${new URLSearchParams({ memberId: String(memberId), q: params.get('q') ?? '' })}` : '/reception/bookings'
  if (resource.status === 'loading') return <div className="page reception-bookings-page"><PageLoader label="Loading booking confirmation" /></div>
  if (resource.status === 'error') return <div className="page reception-bookings-page"><PageHeader title="Booking confirmation" /><ErrorState title="We could not load the booking confirmation" error={resource.error} onRetry={reloadConfirmation} /><Link className="btn btn--primary" to={returnTo}>Back to member bookings</Link></div>
  if (!resource.data) return <div className="page reception-bookings-page"><PageHeader title="Booking confirmation" /><Card><EmptyState title="Booking not verified" description="We could not verify this booking as confirmed for the selected member. It may have been cancelled or may no longer be listed. Check member history before confirming with them." /><Link className="btn btn--primary" to={returnTo}>Back to member bookings</Link></Card></div>

  const booking = resource.data
  return <div className="page reception-bookings-page">
    <PageHeader eyebrow="Front desk / Flow 2" title="Booking Confirmed" description="The member's session reservation is confirmed." />
    <Card className="booking-confirmation">
      <span className="booking-confirmation__mark" aria-hidden="true" />
      <Badge tone="success">Confirmed</Badge>
      <h2>{bookingTitle(booking)}</h2>
      <p className="booking-code">Booking {booking.bookingCode}</p>
      <dl className="browse-facts booking-confirmation__facts"><div><dt>Member</dt><dd>{booking.memberFullName || 'Member'}</dd></div><div><dt>Date</dt><dd>{bookingDate(booking.sessionDate)}</dd></div><div><dt>Time</dt><dd>{bookingTime(booking.startTime)} - {bookingTime(booking.endTime)} Vietnam time</dd></div><div><dt>Sport</dt><dd>{booking.sportName || 'Not provided'}</dd></div><div><dt>Coach</dt><dd>{booking.trainingType === 'SELF_TRAINING' ? 'Self-training' : booking.coachName || 'Not provided'}</dd></div><div><dt>Facility</dt><dd>{booking.facilityName || 'Not provided'}</dd></div><div><dt>Package</dt><dd>{booking.packageName || 'Not provided'}</dd></div><div><dt>Status</dt><dd><StatusBadge status={booking.status} /></dd></div></dl>
      <p className="browse-muted">No notification delivery is reported by the booking response. Share these booking details with the member.</p>
      <div className="browse-actions booking-actions"><Link className="btn btn--primary" to={returnTo}>Back to member bookings</Link><Link className="btn btn--secondary" to="/reception/bookings">Search another member</Link></div>
    </Card>
  </div>
}
