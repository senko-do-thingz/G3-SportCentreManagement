import { useRef, useState, type KeyboardEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { cardsApi } from '../../api/cardsApi'
import { getErrorMessage } from '../../api/errors'
import { memberApi, type MemberCard, type PackageRegistration } from '../../api/memberApi'
import { Badge, Button, Card, EmptyState, ErrorState, Modal, PageHeader, StatusBadge, useToast } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { formatDate, formatMoney } from '../../utils/format'
import {
  canCancelCard,
  displayStatus,
  isQueued,
  MEMBERSHIP_CARDS_PATH,
  sortCards,
  sortRegistrations,
  tabFromParam,
  termDays,
  type MembershipTab,
} from './cardsModel'
import {
  isExhausted,
  MEMBER_PATHS,
  paymentSummaryPath,
  registrationTitle,
  remainingSessionCount,
  sessionShare,
  startsLater,
  toIsoDate,
} from './dashboardModel'
import { useMemberResource } from './useMemberResource'

function loadRegistrations(signal: AbortSignal): Promise<PackageRegistration[]> {
  return memberApi.myRegistrations({ signal })
}

function loadCards(signal: AbortSignal): Promise<MemberCard[]> {
  return memberApi.myCards({ signal })
}

const TABS: { id: MembershipTab; label: string }[] = [
  { id: 'packages', label: 'Sport packages' },
  { id: 'card', label: 'Membership card' },
]

/**
 * My Membership and Sport Packages (Figma F1-12) with the states Active, Expired and Pending
 * Payment. Tab "Sport packages" lists GET /packages/registrations/my; tab "Membership card" lists
 * GET /membership-cards/my, where a request that waits for payment can be paid or cancelled
 * (PUT /membership-cards/{id}/cancel). The tab is kept in the query string (?tab=card).
 */
export function MyMembershipPage() {
  const [params, setParams] = useSearchParams()
  const tab = tabFromParam(params.get('tab'))
  const [today] = useState(() => toIsoDate(new Date()))
  const tabRefs = useRef<Record<MembershipTab, HTMLButtonElement | null>>({ packages: null, card: null })

  const selectTab = (next: MembershipTab, focus = false) => {
    setParams(next === 'packages' ? {} : { tab: next }, { replace: true })
    if (focus) {
      tabRefs.current[next]?.focus()
    }
  }

  const handleTabKey = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') {
      return
    }
    event.preventDefault()
    const index = TABS.findIndex((item) => item.id === tab)
    const step = event.key === 'ArrowRight' ? 1 : -1
    selectTab(TABS[(index + step + TABS.length) % TABS.length].id, true)
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Your sport, your way"
        title="My Membership and Sport Packages"
        description="Track card validity and each sport package separately."
        actions={
          <Link className={buttonClassName('primary', 'md')} to={MEMBER_PATHS.packages}>
            Explore packages
          </Link>
        }
      />
      <div className="tabs" role="tablist" aria-label="My membership">
        {TABS.map((item) => (
          <button
            key={item.id}
            ref={(element) => {
              tabRefs.current[item.id] = element
            }}
            type="button"
            role="tab"
            id={`membership-tab-${item.id}`}
            aria-selected={tab === item.id}
            aria-controls={`membership-panel-${item.id}`}
            tabIndex={tab === item.id ? 0 : -1}
            className={`tabs__tab${tab === item.id ? ' tabs__tab--active' : ''}`}
            onClick={() => selectTab(item.id)}
            onKeyDown={handleTabKey}
          >
            {item.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`membership-panel-${tab}`} aria-labelledby={`membership-tab-${tab}`}>
        {tab === 'packages' ? <RegistrationsPanel today={today} /> : <CardsPanel today={today} />}
      </div>
    </div>
  )
}

function RegistrationsPanel({ today }: { today: string }) {
  const [registrations, reload] = useMemberResource(loadRegistrations)
  return (
    <Card title="My registrations" aria-label="My registrations">
      {registrations.status === 'loading' && <Spinner label="Loading your registrations" />}
      {registrations.status === 'error' && (
        <ErrorState title="We could not load your registrations" error={registrations.error} onRetry={reload} />
      )}
      {registrations.status === 'ready' &&
        (registrations.data.length === 0 ? (
          <EmptyState
            title="No sport packages yet"
            description="Choose a sport package to start your training."
            action={
              <Link className={buttonClassName('primary', 'md')} to={MEMBER_PATHS.packages}>
                Explore packages
              </Link>
            }
          />
        ) : (
          <ul className="package-list" aria-label="Sport package registrations">
            {sortRegistrations(registrations.data, today).map((registration) => (
              <RegistrationItem key={registration.id} registration={registration} today={today} />
            ))}
          </ul>
        ))}
    </Card>
  )
}

function RegistrationItem({ registration, today }: { registration: PackageRegistration; today: string }) {
  const status = displayStatus(registration, today)
  const title = registrationTitle(registration)
  const days = termDays(registration.startDate, registration.endDate)
  const remaining = remainingSessionCount(registration)
  const share = sessionShare(registration)
  const total = Math.max(0, registration.totalSessions ?? 0)
  const meta = [days ? `${days} ${days === 1 ? 'day' : 'days'}` : null, remaining === null ? 'No session limit' : `${total} sessions`]
    .filter(Boolean)
    .join(' - ')
  const later = status === 'ACTIVE' && startsLater(registration, today)
  const exhausted = status === 'ACTIVE' && isExhausted(registration)

  return (
    <li className="package-item" aria-label={`${registration.registrationCode} ${title}`}>
      <div className="package-item__head">
        <div>
          <p className="package-item__code">{registration.registrationCode}</p>
          <h3 className="package-item__title">{title}</h3>
          <p className="package-item__meta">{meta}</p>
        </div>
        <div className="membership-item__badges">
          <StatusBadge status={status} />
          {later && <Badge tone="info">Starts {formatDate(registration.startDate)}</Badge>}
          {exhausted && <Badge tone="warning">No sessions left</Badge>}
        </div>
      </div>
      <dl className="summary-list">
        <div>
          <dt>Validity</dt>
          <dd>
            {formatDate(registration.startDate)} to {formatDate(registration.endDate)}
          </dd>
        </div>
        <div>
          <dt>Amount</dt>
          <dd>{formatMoney(registration.paidAmount)}</dd>
        </div>
      </dl>
      <p className="package-item__sessions">
        {remaining === null ? 'No session limit' : `${remaining} of ${total} sessions remaining`}
      </p>
      {share !== null && total > 0 && (
        <div
          className="progress"
          role="progressbar"
          aria-label={`${registration.registrationCode} sessions remaining`}
          aria-valuemin={0}
          aria-valuemax={total}
          aria-valuenow={remaining ?? 0}
        >
          <span className="progress__bar" style={{ width: `${Math.round(share * 100)}%` }} />
        </div>
      )}
      <RegistrationActions registration={registration} status={status} exhausted={exhausted} />
    </li>
  )
}

function RegistrationActions({
  registration,
  status,
  exhausted,
}: {
  registration: PackageRegistration
  status: string
  exhausted: boolean
}) {
  if (status === 'PENDING_PAYMENT') {
    return (
      <div className="package-item__actions">
        {registration.paymentId !== null ? (
          <Link className={buttonClassName('primary', 'sm')} to={paymentSummaryPath(registration.paymentId)}>
            Pay now
          </Link>
        ) : (
          <p className="package-item__sessions">Please pay at the front desk.</p>
        )}
      </div>
    )
  }
  if (status === 'ACTIVE') {
    return (
      <div className="package-item__actions">
        {!exhausted && (
          <Link className={buttonClassName('primary', 'sm')} to={MEMBER_PATHS.schedule}>
            Book a session
          </Link>
        )}
        <Link className={buttonClassName(exhausted ? 'primary' : 'secondary', 'sm')} to={MEMBER_PATHS.packages}>
          Renew package
        </Link>
      </div>
    )
  }
  if (status === 'EXPIRED') {
    return (
      <div className="package-item__actions">
        <Link className={buttonClassName('secondary', 'sm')} to={MEMBER_PATHS.packages}>
          Renew package
        </Link>
        {registration.paymentId !== null && (
          <Link className={buttonClassName('secondary', 'sm')} to={paymentSummaryPath(registration.paymentId)}>
            View payment
          </Link>
        )}
      </div>
    )
  }
  return null
}

function CardsPanel({ today }: { today: string }) {
  const [cards, reload] = useMemberResource(loadCards)
  const [cancelling, setCancelling] = useState<MemberCard | null>(null)

  return (
    <Card
      title="My membership card"
      aria-label="My membership card"
      actions={
        <Link className={buttonClassName('secondary', 'sm')} to={MEMBERSHIP_CARDS_PATH}>
          View card tiers
        </Link>
      }
    >
      {cards.status === 'loading' && <Spinner label="Loading your cards" />}
      {cards.status === 'error' && <ErrorState title="We could not load your cards" error={cards.error} onRetry={reload} />}
      {cards.status === 'ready' &&
        (cards.data.length === 0 ? (
          <EmptyState
            title="You are on the Standard tier"
            description="Standard is free and has no package discount. Get a membership card to save on sport packages."
            action={
              <Link className={buttonClassName('primary', 'md')} to={MEMBERSHIP_CARDS_PATH}>
                View card tiers
              </Link>
            }
          />
        ) : (
          <ul className="package-list" aria-label="Membership cards">
            {sortCards(cards.data, today).map((card) => (
              <CardItem key={card.id} card={card} today={today} onCancel={() => setCancelling(card)} />
            ))}
          </ul>
        ))}
      {cancelling && (
        <CancelCardModal
          card={cancelling}
          onClose={() => setCancelling(null)}
          onCancelled={() => {
            setCancelling(null)
            reload()
          }}
        />
      )}
    </Card>
  )
}

function CardItem({ card, today, onCancel }: { card: MemberCard; today: string; onCancel: () => void }) {
  const status = displayStatus(card, today)
  const queued = isQueued(card, today)
  return (
    <li className="package-item" aria-label={`${card.cardCode} ${card.tierName}`}>
      <div className="package-item__head">
        <div>
          <p className="package-item__code">{card.cardCode}</p>
          <h3 className="package-item__title">{card.tierName}</h3>
          <p className="package-item__meta">{card.discountPercentage ?? 0}% off packages longer than a single visit</p>
        </div>
        <div className="membership-item__badges">
          <StatusBadge status={status} />
          {queued && <Badge tone="info">Starts {formatDate(card.startDate)}</Badge>}
        </div>
      </div>
      <dl className="summary-list">
        <div>
          <dt>Validity</dt>
          <dd>
            {card.endDate ? `${formatDate(card.startDate)} to ${formatDate(card.endDate)}` : 'No expiry'}
          </dd>
        </div>
        <div>
          <dt>{status === 'PENDING_PAYMENT' ? 'Amount to pay' : 'Amount'}</dt>
          <dd>{formatMoney(card.pricePaid)}</dd>
        </div>
        {card.paymentCode && (
          <div>
            <dt>Payment reference</dt>
            <dd>{card.paymentCode}</dd>
          </div>
        )}
      </dl>
      {status === 'PENDING_PAYMENT' && (
        <p className="package-item__sessions">
          The dates are set again on the day the payment is confirmed.
        </p>
      )}
      {canCancelCard(card) && (
        <div className="package-item__actions">
          {card.paymentId !== null && (
            <Link className={buttonClassName('primary', 'sm')} to={paymentSummaryPath(card.paymentId)}>
              Pay now
            </Link>
          )}
          <Button variant="secondary" size="sm" onClick={onCancel}>
            Cancel request
          </Button>
        </div>
      )}
      {status === 'EXPIRED' && (
        <div className="package-item__actions">
          <Link className={buttonClassName('primary', 'sm')} to={MEMBERSHIP_CARDS_PATH}>
            Renew card
          </Link>
        </div>
      )}
    </li>
  )
}

function CancelCardModal({
  card,
  onClose,
  onCancelled,
}: {
  card: MemberCard
  onClose: () => void
  onCancelled: () => void
}) {
  const toast = useToast()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const sendingRef = useRef(false)

  const confirm = async () => {
    if (sendingRef.current) {
      return
    }
    sendingRef.current = true
    setSubmitting(true)
    setError(null)
    try {
      await cardsApi.cancel(card.id)
      toast.success('Card request cancelled', `${card.tierName} - ${card.cardCode}`)
      onCancelled()
    } catch (caught) {
      setError(getErrorMessage(caught))
      sendingRef.current = false
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      dismissible={!submitting}
      size="sm"
      title="Cancel this card request?"
      description={`${card.tierName} - ${card.cardCode}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            Keep request
          </Button>
          <Button variant="danger" onClick={confirm} loading={submitting}>
            Cancel request
          </Button>
        </>
      }
    >
      {error && (
        <p className="notice notice--danger" role="alert">
          {error}
        </p>
      )}
      <p>The request and its payment of {formatMoney(card.pricePaid)} are cancelled. Do not transfer money for it.</p>
    </Modal>
  )
}
