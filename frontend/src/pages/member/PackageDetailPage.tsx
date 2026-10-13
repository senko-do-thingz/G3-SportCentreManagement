import { useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../../api/errors'
import { memberApi, type MemberCard, type PackageRegistration } from '../../api/memberApi'
import { packagesApi, type SportPackage } from '../../api/packagesApi'
import { Badge, Button, Card, EmptyState, ErrorState, Input, Modal, PageHeader } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { formatDate, formatMoney } from '../../utils/format'
import { useFocusFirstInvalid } from '../../utils/useFocusFirstInvalid'
import { currentCard, MEMBER_PATHS, paymentSummaryPath, toIsoDate, trainingFormatLabel } from './dashboardModel'
import {
  isSingleVisit,
  packageEndDate,
  packageSummary,
  packageTitle,
  priceBreakdown,
  sessionsLabel,
  START_DATE_IN_PAST,
  validateStartDate,
} from './packagesModel'
import { useMemberResource } from './useMemberResource'

function loadPackages(signal: AbortSignal): Promise<SportPackage[]> {
  return packagesApi.packages({ signal })
}

function loadMyCards(signal: AbortSignal): Promise<MemberCard[]> {
  return memberApi.myCards({ signal })
}

/** Used to find a registration of this package that still waits for payment (no duplicates). */
function loadMyRegistrations(signal: AbortSignal): Promise<PackageRegistration[]> {
  return memberApi.myRegistrations({ signal })
}

/**
 * Package Detail and Registration (Figma F1-10) and the Payment Handoff overlay.
 * Data: GET /packages (the backend has no GET /packages/{id}), GET /membership-cards/my for the
 * discount preview. Confirm sends POST /packages/registrations with channel ONLINE; the answer
 * carries the PENDING payment (BE-17), shown in the overlay, which continues to the payment
 * summary /member/payments/{paymentId} (F3-01).
 */
export function PackageDetailPage() {
  const { packageId } = useParams()
  const location = useLocation()
  const [packages, reloadPackages] = useMemberResource(loadPackages)
  const [cards] = useMemberResource(loadMyCards)
  const [registrations] = useMemberResource(loadMyRegistrations)
  const [today] = useState(() => toIsoDate(new Date()))
  const backTo = `${MEMBER_PATHS.packages}${location.search}`

  const header = (
    <PageHeader
      eyebrow="Your sport, your way"
      title="Package Detail and Registration"
      description="Review package access, your discount and start date."
      actions={
        <Link className={buttonClassName('secondary', 'md')} to={backTo}>
          Back to packages
        </Link>
      }
    />
  )

  if (packages.status === 'loading') {
    return (
      <div className="page">
        {header}
        <Spinner label="Loading the package" />
      </div>
    )
  }
  if (packages.status === 'error') {
    return (
      <div className="page">
        {header}
        <ErrorState title="We could not load the package" error={packages.error} onRetry={reloadPackages} />
      </div>
    )
  }

  const pkg = packages.data.find((candidate) => String(candidate.id) === packageId && candidate.isActive !== false)
  if (!pkg) {
    return (
      <div className="page">
        {header}
        <EmptyState
          title="This package is not available"
          description="It may have been removed or is no longer offered."
          action={
            <Link className={buttonClassName('primary', 'md')} to={backTo}>
              Back to packages
            </Link>
          }
        />
      </div>
    )
  }

  return (
    <div className="page">
      {header}
      <RegistrationForm
        pkg={pkg}
        card={cards.status === 'ready' ? currentCard(cards.data, today) : null}
        cardsFailed={cards.status === 'error'}
        cardsLoading={cards.status === 'loading'}
        // The backend does not block a second pending registration, so the page does. While the
        // list loads, Confirm is disabled; if it cannot be loaded, nothing is blocked (the backend
        // stays the authority).
        registrationsLoading={registrations.status === 'loading'}
        pendingRegistration={
          registrations.status === 'ready'
            ? (registrations.data.find(
                (candidate) => candidate.packageId === pkg.id && candidate.status === 'PENDING_PAYMENT',
              ) ?? null)
            : null
        }
      />
    </div>
  )
}

function RegistrationForm({
  pkg,
  card,
  cardsFailed,
  cardsLoading,
  registrationsLoading,
  pendingRegistration,
}: {
  pkg: SportPackage
  card: MemberCard | null
  cardsFailed: boolean
  cardsLoading: boolean
  registrationsLoading: boolean
  pendingRegistration: PackageRegistration | null
}) {
  // "Today" is read again on focus, on change and at submit, so a page left open past midnight
  // never accepts yesterday and the picker minimum follows the date.
  const [minDate, setMinDate] = useState(() => toIsoDate(new Date()))
  const refreshMinDate = () => setMinDate(toIsoDate(new Date()))
  const [startDate, setStartDate] = useState(minDate)
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [registration, setRegistration] = useState<PackageRegistration | null>(null)
  const sendingRef = useRef(false)
  const formRef = useRef<HTMLFormElement>(null)
  const focusFirstInvalid = useFocusFirstInvalid(formRef)

  const price = priceBreakdown(pkg, card)
  const endDate = startDate && !validateStartDate(startDate, minDate) ? packageEndDate(startDate, pkg.durationDays) : null
  const blocked = pendingRegistration !== null && registration === null
  // Confirm waits for the registrations list, so a fast click cannot create a duplicate.
  const checking = registrationsLoading && registration === null
  const title = packageTitle(pkg)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (sendingRef.current || registration || blocked || checking) {
      return
    }
    setFormError(null)
    const now = toIsoDate(new Date())
    setMinDate(now)
    const error = validateStartDate(startDate, now)
    setFieldError(error)
    if (error) {
      focusFirstInvalid()
      return
    }
    sendingRef.current = true
    setSubmitting(true)
    try {
      setRegistration(await packagesApi.register({ packageId: pkg.id, startDate, channel: 'ONLINE' }))
    } catch (caught) {
      if (caught instanceof ApiError && caught.fieldErrors.startDate) {
        setFieldError(caught.fieldErrors.startDate)
        focusFirstInvalid()
      } else if (
        caught instanceof ApiError &&
        caught.status === 400 &&
        !caught.hasFieldErrors &&
        caught.message === START_DATE_IN_PAST
      ) {
        // BusinessRuleException from registerPackage: 400 {timestamp, status, message}, no details.
        setFieldError(caught.message)
        focusFirstInvalid()
      } else {
        setFormError(getErrorMessage(caught))
      }
    } finally {
      sendingRef.current = false
      setSubmitting(false)
    }
  }

  let discountText: string
  if (isSingleVisit(pkg)) {
    discountText = 'Not applied to single visits'
  } else if (cardsLoading) {
    discountText = 'Checking your card'
  } else if (cardsFailed) {
    discountText = 'Calculated when you confirm'
  } else if (price.discountPercent > 0 && card) {
    discountText = `${card.tierName} - ${price.discountPercent}% (-${formatMoney(price.discountAmount)})`
  } else {
    discountText = 'None (Standard)'
  }

  return (
    <>
      <form ref={formRef} className="package-detail" onSubmit={handleSubmit} noValidate aria-label="Package registration">
        <Card title="Package details" className="package-detail__main">
          <Badge tone="info">{trainingFormatLabel(pkg.trainingFormat)}</Badge>
          <h2 className="package-detail__title">
            {pkg.sportName} - {packageSummary(pkg)}
          </h2>
          {pkg.description && <p className="package-detail__description">{pkg.description}</p>}
          <dl className="summary-list">
            <div>
              <dt>Included sessions</dt>
              <dd>{sessionsLabel(pkg.sessionCount)}</dd>
            </div>
            <div>
              <dt>Session duration</dt>
              <dd>{pkg.sessionMinutes ? `${pkg.sessionMinutes} minutes` : '-'}</dd>
            </div>
            <div>
              <dt>Package validity</dt>
              <dd>{isSingleVisit(pkg) ? 'The start date only' : `${pkg.durationDays} consecutive days`}</dd>
            </div>
          </dl>
          <fieldset className="profile-form" disabled={submitting || registration !== null || blocked}>
            <Input
              label="Start date"
              type="date"
              name="startDate"
              min={minDate}
              value={startDate}
              onFocus={refreshMinDate}
              onChange={(event) => {
                setStartDate(event.target.value)
                setFieldError(null)
                refreshMinDate()
              }}
              error={fieldError ?? undefined}
              hint="Today or later."
              required
            />
          </fieldset>
          <p className="notice notice--info package-detail__note">
            Use the included sessions within the package validity. Class reservations are made separately in your
            schedule.
          </p>
        </Card>

        <Card title="Registration summary" className="package-detail__summary" aria-label="Registration summary">
          {formError && (
            <p className="notice notice--danger" role="alert">
              {formError}
            </p>
          )}
          {blocked && pendingRegistration && (
            <div className="notice notice--warning" role="status">
              <p>You already have a registration waiting for payment for this package ({pendingRegistration.registrationCode}).</p>
              {pendingRegistration.paymentId !== null ? (
                <Link to={paymentSummaryPath(pendingRegistration.paymentId)}>Continue to payment</Link>
              ) : (
                <p>Please pay at the front desk.</p>
              )}
            </div>
          )}
          <dl className="summary-list">
            <div>
              <dt>Sport</dt>
              <dd>{pkg.sportName}</dd>
            </div>
            <div>
              <dt>Training type</dt>
              <dd>{trainingFormatLabel(pkg.trainingFormat)}</dd>
            </div>
            <div>
              <dt>Package</dt>
              <dd>{packageSummary(pkg)}</dd>
            </div>
            <div>
              <dt>Original price</dt>
              <dd>{formatMoney(price.original)}</dd>
            </div>
            <div>
              <dt>Membership discount</dt>
              <dd>{discountText}</dd>
            </div>
            <div className="summary-list__total">
              <dt>Total payable</dt>
              <dd data-testid="total-payable">{formatMoney(cardsFailed ? price.original : price.final)}</dd>
            </div>
            <div>
              <dt>Start date</dt>
              <dd>{startDate ? formatDate(startDate) : '-'}</dd>
            </div>
            <div>
              <dt>Expiry date</dt>
              <dd>{endDate ? formatDate(endDate) : '-'}</dd>
            </div>
          </dl>
          <Button type="submit" loading={submitting} disabled={registration !== null || blocked || checking} fullWidth>
            Confirm registration
          </Button>
          {checking && (
            <p className="package-detail__checking" role="status">
              Checking your registrations
            </p>
          )}
          <p className="notice notice--info package-detail__note">
            Payment confirmation is required before activation. A package that starts later stays scheduled until its
            start date.
          </p>
        </Card>
      </form>

      <Modal
        open={registration !== null}
        onClose={() => undefined}
        dismissible={false}
        title="Payment summary"
        description={title}
        footer={
          registration && (
            <>
              <Link className={buttonClassName('secondary', 'md')} to={MEMBER_PATHS.membership}>
                View my registration
              </Link>
              {registration.paymentId !== null && (
                <Link className={buttonClassName('primary', 'md')} to={paymentSummaryPath(registration.paymentId)}>
                  Continue to payment
                </Link>
              )}
            </>
          )
        }
      >
        {registration && (
          <>
            <dl className="summary-list">
              <div>
                <dt>Registration reference</dt>
                <dd>{registration.registrationCode}</dd>
              </div>
              {registration.paymentCode && (
                <div>
                  <dt>Payment reference</dt>
                  <dd>{registration.paymentCode}</dd>
                </div>
              )}
              <div>
                <dt>Package</dt>
                <dd>{packageSummary(pkg)}</dd>
              </div>
              <div>
                <dt>Start date</dt>
                <dd>{formatDate(registration.startDate)}</dd>
              </div>
              <div className="summary-list__total">
                <dt>Amount payable</dt>
                <dd data-testid="amount-payable">{formatMoney(registration.paidAmount)}</dd>
              </div>
            </dl>
            <p className="notice notice--info">
              {registration.paymentId !== null
                ? 'Payment must be verified before your package is activated. Your registration stays pending until confirmation.'
                : 'Your registration is waiting for payment. Please pay at the front desk.'}
            </p>
          </>
        )}
      </Modal>
    </>
  )
}
