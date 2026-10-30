import { useState } from 'react'
import { Link } from 'react-router-dom'
import { memberApi, type MemberBooking, type MemberCard, type PackageRegistration } from '../../api/memberApi'
import { useAuth } from '../../auth/useAuth'
import { Badge, Card, EmptyState, ErrorState, PageHeader, StatCard, StatusBadge } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { formatDate, formatDateTime } from '../../utils/format'
import {
  activeRegistrations,
  canBook,
  cardDiscountText,
  currentCard,
  dashboardState,
  hasHadPackage,
  heroVariant,
  isExhausted,
  MEMBER_PATHS,
  paymentSummaryPath,
  pendingItems,
  registrationTitle,
  remainingSessionCount,
  sessionShare,
  shortTime,
  STANDARD_TIER_NAME,
  startsLater,
  toIsoDate,
  trainingFormatLabel,
  upcomingBookings,
  type HeroVariant,
  type PendingItem,
} from './dashboardModel'
import { AssistantEntry } from './assistant/AssistantEntry'
import { useMemberResource, type Resource } from './useMemberResource'

/** Number of packages and sessions listed on the dashboard; the rest is on the linked screens. */
const LIST_LIMIT = 3

/** Id of the Pending payments block, the target of the hero action when a payment has no summary yet. */
const PENDING_PAYMENTS_ID = 'pending-payments'

interface Purchases {
  cards: MemberCard[]
  registrations: PackageRegistration[]
}

/** Cards and packages decide the state of the page, so they load together. */
function loadPurchases(signal: AbortSignal): Promise<Purchases> {
  return Promise.all([memberApi.myCards({ signal }), memberApi.myRegistrations({ signal })]).then(
    ([cards, registrations]) => ({ cards, registrations }),
  )
}

/** Bookings load on their own: a failure there only affects the Upcoming sessions block. */
function loadBookings(signal: AbortSignal): Promise<MemberBooking[]> {
  return memberApi.myUpcomingBookings({ signal })
}

/**
 * Member Dashboard (Figma F1-07, states "New Standard Member" and "After Purchase").
 * Data: the signed in profile from the auth context (GET /users/me), GET /membership-cards/my,
 * GET /packages/registrations/my and GET /bookings/my?when=UPCOMING.
 */
export function MemberDashboardPage() {
  const { user } = useAuth()
  const [purchases, reloadPurchases] = useMemberResource(loadPurchases)
  const [bookings, reloadBookings] = useMemberResource(loadBookings)
  // Fixed for the life of the page, so the lists do not change while the member reads them.
  const [now] = useState(() => new Date())
  const today = toIsoDate(now)

  const header = (
    <PageHeader
      eyebrow="Your sport, your way"
      title="Member Dashboard"
      description="Your membership, sport packages and upcoming training in one place."
    />
  )

  if (purchases.status === 'loading') {
    return (
      <div className="page">
        {header}
        <div className="dashboard-loading">
          <Spinner label="Loading your dashboard" />
        </div>
      </div>
    )
  }

  if (purchases.status === 'error') {
    return (
      <div className="page">
        {header}
        <ErrorState
          title="We could not load your dashboard"
          error={purchases.error}
          onRetry={() => {
            reloadPurchases()
            // Bookings that already loaded stay as they are; only a failed load is tried again.
            if (bookings.status === 'error') {
              reloadBookings()
            }
          }}
        />
      </div>
    )
  }

  const { cards, registrations } = purchases.data
  const state = dashboardState(cards, registrations)
  const card = currentCard(cards, today)
  const active = activeRegistrations(registrations, today)
  const pending = pendingItems(cards, registrations)
  const bookableCount = active.filter((registration) => canBook(registration, today)).length
  const bookable = bookableCount > 0
  const upcoming = bookings.status === 'ready' ? upcomingBookings(bookings.data, now) : []

  return (
    <div className="page dashboard" data-state={state}>
      {header}

      <DashboardHero
        variant={heroVariant({
          state,
          hasBookablePackage: bookable,
          pendingCount: pending.length,
          hasHadPackage: hasHadPackage(registrations),
        })}
        fullName={user?.fullName ?? ''}
        firstPending={pending[0] ?? null}
        card={card}
      />

      <div className="dashboard__stats">
        <StatCard
          label="Membership tier"
          value={card ? card.tierName : STANDARD_TIER_NAME}
          hint={card ? cardHint(card) : 'Explore cards for package discounts'}
        />
        <StatCard label="Active packages" value={active.length} hint={activePackagesHint(active.length, bookableCount)} />
        <StatCard
          label="Upcoming sessions"
          value={bookings.status === 'ready' ? upcoming.length : '-'}
          hint={bookings.status === 'error' ? 'Could not load your bookings' : 'View and book your schedule'}
        />
      </div>

      <div className="dashboard__grid">
        <div className="dashboard__main">
          {pending.length > 0 && <PendingPaymentsCard items={pending} />}
          <PackagesCard registrations={active} today={today} />
          <UpcomingSessionsCard bookings={bookings} upcoming={upcoming} hasBookablePackage={bookable} onRetry={reloadBookings} />
        </div>
        <aside className="dashboard__side" aria-label="Profile and quick links">
          <ProfileCard />
          <QuickLinksCard />
          <AssistantEntry />
        </aside>
      </div>
    </div>
  )
}

function cardHint(card: MemberCard): string {
  const discount = card.discountPercentage ? cardDiscountText(card.discountPercentage) : null
  const until = card.endDate ? `Active - Until ${formatDate(card.endDate)}` : 'Active'
  return discount ? `${until} - ${discount}` : until
}

interface HeroContent {
  eyebrow: string
  title: string
  text: string
  action: { label: string; to?: string; href?: string }
}

/**
 * Hint of the Active packages stat. The value counts the same packages as the My sport packages
 * list (ACTIVE and not ended), so the hint says how many of them can still be booked when some
 * have no sessions left.
 */
function activePackagesHint(activeCount: number, bookableCount: number): string {
  const usedUp = activeCount - bookableCount
  if (usedUp <= 0) {
    return 'Your paid sport access'
  }
  return `${bookableCount} can be booked - ${usedUp} with no sessions left`
}

/** Text of the first-package hero: the card discount when a card is in force. */
function firstPackageText(card: MemberCard | null): string {
  if (card && card.discountPercentage) {
    return `Your ${card.tierName} card gives you ${cardDiscountText(card.discountPercentage)}. Pick a sport to start training.`
  }
  return 'Pick a sport and a training style to start training.'
}

function heroContent(
  variant: HeroVariant,
  name: string,
  firstPending: PendingItem | null,
  card: MemberCard | null,
): HeroContent {
  switch (variant) {
    case 'welcome':
      return {
        eyebrow: 'Welcome to Sportify',
        title: `Welcome, ${name}.`,
        text: 'Find a sport package that fits your goals.',
        action: { label: 'Explore packages', to: MEMBER_PATHS.packages },
      }
    case 'book':
      return {
        eyebrow: `Welcome back, ${name}`,
        title: 'Ready for your next session?',
        text: 'Book a session with your active sport package.',
        action: { label: 'Book a session', to: MEMBER_PATHS.schedule },
      }
    case 'pay':
      return {
        eyebrow: 'Almost there',
        title: 'Complete your payment to start training.',
        text: 'Your request starts after the payment is confirmed. Pay online or at the front desk.',
        action:
          firstPending && firstPending.paymentId !== null
            ? { label: 'Pay now', to: paymentSummaryPath(firstPending.paymentId) }
            : { label: 'View pending payments', href: `#${PENDING_PAYMENTS_ID}` },
      }
    case 'first-package':
      return {
        eyebrow: `Welcome back, ${name}`,
        title: 'Choose your first sport package.',
        text: firstPackageText(card),
        action: { label: 'Explore packages', to: MEMBER_PATHS.packages },
      }
    case 'renew':
      return {
        eyebrow: `Welcome back, ${name}`,
        title: 'Find your next sport package.',
        text: 'Your packages have no sessions left or have ended. Renew one or choose a new sport.',
        action: { label: 'Explore packages', to: MEMBER_PATHS.packages },
      }
  }
}

function DashboardHero({
  variant,
  fullName,
  firstPending,
  card,
}: {
  variant: HeroVariant
  fullName: string
  firstPending: PendingItem | null
  card: MemberCard | null
}) {
  const content = heroContent(variant, fullName || 'member', firstPending, card)
  const actionClass = buttonClassName('accent', 'md')
  return (
    <section className="dashboard-hero" aria-labelledby="dashboard-hero-title" data-variant={variant}>
      <div className="dashboard-hero__text">
        <p className="dashboard-hero__eyebrow">{content.eyebrow}</p>
        <h2 className="dashboard-hero__title" id="dashboard-hero-title">
          {content.title}
        </h2>
        <p className="dashboard-hero__description">{content.text}</p>
      </div>
      {content.action.to ? (
        <Link className={actionClass} to={content.action.to}>
          {content.action.label}
        </Link>
      ) : (
        <a className={actionClass} href={content.action.href}>
          {content.action.label}
        </a>
      )}
    </section>
  )
}

function PendingPaymentsCard({ items }: { items: PendingItem[] }) {
  return (
    <Card
      title="Pending payments"
      className="dashboard-pending"
      aria-label="Pending payments"
      id={PENDING_PAYMENTS_ID}
      tabIndex={-1}
    >
      <p className="dashboard-pending__intro">
        These requests start after the payment is confirmed. Pay online or at the front desk.
      </p>
      <ul className="dashboard-list">
        {items.map((item) => (
          <li key={item.key} className="dashboard-list__item">
            <div className="dashboard-list__text">
              <p className="dashboard-list__title">{item.title}</p>
              <p className="dashboard-list__meta">
                {item.reference} - {item.amount}
                {item.requestedAt ? ` - Requested ${formatDateTime(item.requestedAt)}` : ''}
              </p>
            </div>
            <div className="dashboard-list__actions">
              <StatusBadge status="PENDING_PAYMENT" />
              {item.paymentId !== null ? (
                <Link
                  className={buttonClassName('primary', 'sm')}
                  to={paymentSummaryPath(item.paymentId)}
                  aria-label={`Pay now for ${item.title}`}
                >
                  Pay now
                </Link>
              ) : (
                <span className="dashboard-list__note">Pay at the front desk</span>
              )}
            </div>
          </li>
        ))}
      </ul>
    </Card>
  )
}

function PackagesCard({ registrations, today }: { registrations: PackageRegistration[]; today: string }) {
  const shown = registrations.slice(0, LIST_LIMIT)
  return (
    <Card title="My sport packages" aria-label="My sport packages">
      {shown.length === 0 ? (
        <EmptyState
          title="No sport packages yet"
          description="Explore sports and find a package that suits you."
          action={
            <Link className={buttonClassName('primary', 'md')} to={MEMBER_PATHS.packages}>
              Explore packages
            </Link>
          }
        />
      ) : (
        <ul className="package-list">
          {shown.map((registration) => (
            <PackageItem key={registration.id} registration={registration} today={today} />
          ))}
        </ul>
      )}
      <div className="dashboard-card__footer">
        <Link className={buttonClassName('secondary', 'sm')} to={MEMBER_PATHS.membership}>
          View all packages
        </Link>
      </div>
    </Card>
  )
}

function PackageItem({ registration, today }: { registration: PackageRegistration; today: string }) {
  const share = sessionShare(registration)
  const remaining = remainingSessionCount(registration)
  const exhausted = isExhausted(registration)
  const bookable = canBook(registration, today)
  const title = registrationTitle(registration)
  const later = startsLater(registration, today)
  const total = Math.max(0, registration.totalSessions ?? 0)
  const sessionsText = remaining === null ? 'No session limit' : `${remaining} of ${total} sessions remaining`
  return (
    <li className="package-item" aria-label={title}>
      <div className="package-item__head">
        <div>
          <p className="package-item__code">{registration.registrationCode}</p>
          <h3 className="package-item__title">{title}</h3>
          <p className="package-item__meta">
            {registration.packageName}
            {registration.endDate ? ` - Ends ${formatDate(registration.endDate)}` : ''}
          </p>
        </div>
        {exhausted ? (
          <Badge tone="warning">No sessions left</Badge>
        ) : later ? (
          <Badge tone="info">Starts {formatDate(registration.startDate)}</Badge>
        ) : (
          <StatusBadge status={registration.status} />
        )}
      </div>
      <p className="package-item__sessions">{sessionsText}</p>
      {share !== null && total > 0 && (
        <div
          className="progress"
          role="progressbar"
          aria-label={`${title} sessions remaining`}
          aria-valuemin={0}
          aria-valuemax={total}
          aria-valuenow={remaining ?? 0}
        >
          <span className="progress__bar" style={{ width: `${Math.round(share * 100)}%` }} />
        </div>
      )}
      <div className="package-item__actions">
        {bookable && (
          <Link className={buttonClassName('primary', 'sm')} to={MEMBER_PATHS.schedule}>
            Book a session
          </Link>
        )}
        <Link className={buttonClassName(bookable ? 'secondary' : 'primary', 'sm')} to={MEMBER_PATHS.packages}>
          Renew package
        </Link>
      </div>
    </li>
  )
}

function UpcomingSessionsCard({
  bookings,
  upcoming,
  hasBookablePackage,
  onRetry,
}: {
  bookings: Resource<MemberBooking[]>
  upcoming: MemberBooking[]
  hasBookablePackage: boolean
  onRetry: () => void
}) {
  const scheduleLink = (
    <Link className={buttonClassName('secondary', 'md')} to={MEMBER_PATHS.schedule}>
      View schedule
    </Link>
  )
  let content
  if (bookings.status === 'loading') {
    content = <Spinner label="Loading your sessions" />
  } else if (bookings.status === 'error') {
    content = <ErrorState title="We could not load your sessions" error={bookings.error} onRetry={onRetry} />
  } else if (upcoming.length === 0) {
    content = (
      <EmptyState
        title="No upcoming sessions"
        description={
          hasBookablePackage
            ? 'Book a session with your active sport package.'
            : 'Choose a session after your sport package becomes active.'
        }
        action={scheduleLink}
      />
    )
  } else {
    content = (
      <ul className="dashboard-list">
        {upcoming.slice(0, LIST_LIMIT).map((booking) => (
          <li key={booking.id} className="dashboard-list__item">
            <div className="dashboard-list__text">
              <p className="dashboard-list__title">
                {booking.sportName ?? 'Session'}
                {booking.trainingType ? ` - ${trainingFormatLabel(booking.trainingType)}` : ''}
              </p>
              <p className="dashboard-list__meta">
                {formatDate(booking.sessionDate)}
                {booking.startTime ? `, ${shortTime(booking.startTime)}` : ''}
                {booking.endTime ? ` to ${shortTime(booking.endTime)}` : ''} - {booking.bookingCode}
              </p>
            </div>
            <StatusBadge status={booking.status} />
          </li>
        ))}
      </ul>
    )
  }
  return (
    <Card title="Upcoming sessions" aria-label="Upcoming sessions">
      {content}
      {bookings.status === 'ready' && upcoming.length > 0 && <div className="dashboard-card__footer">{scheduleLink}</div>}
    </Card>
  )
}

function ProfileCard() {
  return (
    <Card title="Your profile" aria-label="Your profile">
      <p className="dashboard-side__text">Keep your goals and contact details up to date.</p>
      <Link className={buttonClassName('secondary', 'sm')} to={MEMBER_PATHS.profile}>
        Edit profile
      </Link>
    </Card>
  )
}

const QUICK_LINKS = [
  { to: MEMBER_PATHS.membershipCard, label: 'My membership card', hint: 'Tier, benefits and renewal' },
  { to: MEMBER_PATHS.packages, label: 'Sport packages', hint: 'Prices by sport and training style' },
  { to: MEMBER_PATHS.schedule, label: 'My schedule', hint: 'Book and manage sessions' },
  { to: MEMBER_PATHS.payments, label: 'My payments', hint: 'Payment status and receipts' },
] as const

function QuickLinksCard() {
  return (
    <Card title="Quick links" aria-label="Quick links">
      <nav aria-label="Quick links">
        <ul className="quick-links">
          {QUICK_LINKS.map((link) => (
            <li key={link.to}>
              <Link className="quick-links__link" to={link.to}>
                <span className="quick-links__label">{link.label}</span>
                <span className="quick-links__hint">{link.hint}</span>
              </Link>
            </li>
          ))}
        </ul>
      </nav>
    </Card>
  )
}
