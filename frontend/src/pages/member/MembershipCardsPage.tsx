import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { cardsApi, type CardTier } from '../../api/cardsApi'
import { getErrorMessage } from '../../api/errors'
import { memberApi, type MemberCard } from '../../api/memberApi'
import { Badge, Button, Card, EmptyState, ErrorState, Modal, PageHeader, useToast } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { formatDate, formatMoney } from '../../utils/format'
import {
  cardInForce,
  isFreeTier,
  isQueued,
  lastPaidCard,
  MEMBERSHIP_CARD_TAB_PATH,
  pendingCard,
  PLAN_ACTION_LABELS,
  planCard,
  sortTiers,
  tierPerks,
  validityLabel,
  type CardPlan,
} from './cardsModel'
import { MEMBER_PATHS, paymentSummaryPath, toIsoDate } from './dashboardModel'
import { useMemberResource } from './useMemberResource'

interface CardsData {
  tiers: CardTier[]
  cards: MemberCard[]
}

/** Both lists are needed to compute any plan, so they load and fail together. */
function loadCardsData(signal: AbortSignal): Promise<CardsData> {
  return Promise.all([cardsApi.tiers({ signal }), memberApi.myCards({ signal })]).then(([tiers, cards]) => ({
    tiers,
    cards,
  }))
}

/**
 * Membership Cards (Figma F1-11) and the Renew Membership Card overlay.
 * Data: GET /membership-cards/tiers and GET /membership-cards/my. Each paid tier shows the action
 * the backend will apply (new, renew, upgrade or later start) and the overlay shows the computed
 * start date, end date and price before Confirm. Confirm sends POST /membership-cards/purchase,
 * which creates a PENDING_PAYMENT card and its payment; the member continues to the payment
 * summary /member/payments/{paymentId}.
 */
export function MembershipCardsPage() {
  const [data, reload] = useMemberResource(loadCardsData)
  const [today] = useState(() => toIsoDate(new Date()))
  const [selected, setSelected] = useState<CardTier | null>(null)

  const header = (
    <PageHeader
      eyebrow="Your sport, your way"
      title="Membership Cards"
      description="Enjoy discounts on sport packages with an eligible membership card."
      actions={
        <Link className={buttonClassName('secondary', 'md')} to={MEMBERSHIP_CARD_TAB_PATH}>
          My membership card
        </Link>
      }
    />
  )

  if (data.status === 'loading') {
    return (
      <div className="page">
        {header}
        <Spinner label="Loading membership cards" />
      </div>
    )
  }
  if (data.status === 'error') {
    return (
      <div className="page">
        {header}
        <ErrorState title="We could not load the membership cards" error={data.error} onRetry={reload} />
      </div>
    )
  }

  const { tiers, cards } = data.data
  const sorted = sortTiers(tiers)
  const current = cardInForce(cards, tiers, today)
  const last = lastPaidCard(cards, tiers, today)
  const pending = pendingCard(cards)

  return (
    <div className="page">
      {header}
      {pending && (
        <div className="notice notice--warning cards-pending" role="status">
          <p>
            Your {pending.tierName} card request ({pending.cardCode}) is waiting for payment. Pay or cancel it before
            you request another card.
          </p>
          <div className="cards-pending__actions">
            {pending.paymentId !== null && (
              <Link className={buttonClassName('primary', 'sm')} to={paymentSummaryPath(pending.paymentId)}>
                Pay now
              </Link>
            )}
            <Link className={buttonClassName('secondary', 'sm')} to={MEMBERSHIP_CARD_TAB_PATH}>
              Manage request
            </Link>
          </div>
        </div>
      )}
      {sorted.length === 0 ? (
        <EmptyState title="No membership cards on sale" description="Please check again later or ask the front desk." />
      ) : (
        <ul className="tier-grid" aria-label="Membership card tiers">
          {sorted.map((tier) => (
            <TierCard
              key={tier.id}
              tier={tier}
              plan={isFreeTier(tier) ? null : planCard(tier, cards, tiers, today)}
              isCurrent={current ? current.tierId === tier.id : isFreeTier(tier)}
              queuedFrom={last && last !== current && last.tierId === tier.id && isQueued(last, today) ? last.startDate : null}
              isPending={pending?.tierId === tier.id}
              onSelect={() => setSelected(tier)}
            />
          ))}
        </ul>
      )}
      <HowCardWorks />
      {selected && (
        <ConfirmCardModal
          tier={selected}
          cards={cards}
          tiers={tiers}
          onClose={() => setSelected(null)}
        />
      )}
    </div>
  )
}

function TierCard({
  tier,
  plan,
  isCurrent,
  queuedFrom,
  isPending,
  onSelect,
}: {
  tier: CardTier
  plan: CardPlan | null
  isCurrent: boolean
  queuedFrom: string | null
  isPending: boolean
  onSelect: () => void
}) {
  const free = isFreeTier(tier)
  return (
    <li className={`card tier-card${isCurrent ? ' tier-card--current' : ''}`} aria-label={tier.name}>
      <div className="tier-card__head">
        <p className="eyebrow">{free ? 'Your account' : 'Membership card'}</p>
        <div className="tier-card__badges">
          {isCurrent && <Badge tone="success">{free ? 'Current' : 'Active'}</Badge>}
          {queuedFrom && <Badge tone="info">Starts {formatDate(queuedFrom)}</Badge>}
          {isPending && <Badge tone="warning">Pending payment</Badge>}
        </div>
      </div>
      <h2 className="tier-card__name">{tier.name}</h2>
      <p className="tier-card__price">{free ? 'Free' : formatMoney(tier.price)}</p>
      <p className="tier-card__validity">{free ? 'No paid card required' : validityLabel(tier)}</p>
      <ul className="tier-card__perks">
        {tierPerks(tier).map((perk) => (
          <li key={perk}>{perk}</li>
        ))}
      </ul>
      {tier.description && <p className="tier-card__description">{tier.description}</p>}
      <div className="tier-card__action">
        {free || !plan ? (
          <Link className={buttonClassName('secondary', 'md', true)} to={MEMBER_PATHS.packages}>
            Explore sport packages
          </Link>
        ) : plan.kind === 'blocked' ? (
          <p className="notice notice--info">{plan.reason}</p>
        ) : (
          <>
            {plan.kind === 'later' && plan.startDate && (
              <p className="tier-card__hint">Starts {formatDate(plan.startDate)}, after your current card ends.</p>
            )}
            {plan.kind === 'upgrade' && plan.price !== null && (
              <p className="tier-card__hint">Pay {formatMoney(plan.price)} now and keep your current end date.</p>
            )}
            <Button variant={isCurrent || plan.kind === 'upgrade' ? 'primary' : 'secondary'} fullWidth onClick={onSelect}>
              {PLAN_ACTION_LABELS[plan.kind]}
            </Button>
          </>
        )}
      </div>
    </li>
  )
}

function HowCardWorks() {
  return (
    <Card title="How your card works" aria-label="How your card works">
      <dl className="summary-list">
        <div>
          <dt>Discount applies to</dt>
          <dd>Packages longer than a single visit</dd>
        </div>
        <div>
          <dt>Single-session visits</dt>
          <dd>No discount</dd>
        </div>
        <div>
          <dt>Your training access</dt>
          <dd>Purchase each sport package separately</dd>
        </div>
        <div>
          <dt>Payment</dt>
          <dd>The card starts after the payment is confirmed</dd>
        </div>
      </dl>
      <p className="notice notice--info">Card expiry does not cancel a paid sport package that is still valid.</p>
    </Card>
  )
}

const OVERLAY_EYEBROWS: Record<CardPlan['kind'], string> = {
  new: 'New card',
  renew: 'Renew card',
  upgrade: 'Upgrade card',
  later: 'New card after current card',
  blocked: 'Upgrade card',
  unknown: 'New card',
}

/**
 * Renew Membership Card overlay (F1-11). The plan is computed again when the overlay opens, so a
 * page left open past midnight still shows today's dates. The backend stays the authority: any
 * error it returns (for example a request already waiting for payment) is shown as is.
 */
function ConfirmCardModal({
  tier,
  cards,
  tiers,
  onClose,
}: {
  tier: CardTier
  cards: MemberCard[]
  tiers: CardTier[]
  onClose: () => void
}) {
  const navigate = useNavigate()
  const toast = useToast()
  const [plan] = useState(() => planCard(tier, cards, tiers, toIsoDate(new Date())))
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const sendingRef = useRef(false)
  const baseTierName = plan.baseCard?.tierName ?? 'current'

  const confirm = async () => {
    if (sendingRef.current || plan.kind === 'blocked') {
      return
    }
    sendingRef.current = true
    setSubmitting(true)
    setError(null)
    try {
      const created = await cardsApi.purchase({ tierId: tier.id })
      if (created.paymentId !== null) {
        navigate(paymentSummaryPath(created.paymentId))
      } else {
        toast.success('Card request created', `${created.tierName} - ${created.cardCode}`)
        navigate(MEMBERSHIP_CARD_TAB_PATH)
      }
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
      title={
        <>
          <span className="eyebrow modal-eyebrow">{OVERLAY_EYEBROWS[plan.kind]}</span>
          {tier.name} membership
        </>
      }
      description="Your sport packages are purchased separately."
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button onClick={confirm} loading={submitting} disabled={plan.kind === 'blocked'}>
            Confirm and continue
          </Button>
        </>
      }
    >
      {error && (
        <p className="notice notice--danger" role="alert">
          {error}
        </p>
      )}
      {plan.kind === 'blocked' && (
        <p className="notice notice--warning" role="status">
          {plan.reason}
        </p>
      )}
      <dl className="summary-list" aria-label="Card request summary">
        <div>
          <dt>Card fee</dt>
          <dd>{formatMoney(plan.tierPrice)}</dd>
        </div>
        {plan.kind === 'upgrade' && plan.price !== null && (
          <div>
            <dt>Paid with your {baseTierName} card</dt>
            <dd>-{formatMoney(plan.tierPrice - plan.price)}</dd>
          </div>
        )}
        <div className="summary-list__total">
          <dt>Amount to pay</dt>
          <dd data-testid="card-amount">{plan.price === null ? 'Calculated when you confirm' : formatMoney(plan.price)}</dd>
        </div>
        <div>
          <dt>Validity</dt>
          <dd>{plan.kind === 'upgrade' ? `Until your ${baseTierName} card ends` : validityLabel(tier)}</dd>
        </div>
        <div>
          <dt>Package discount</dt>
          <dd>{tier.discountPercentage ?? 0}%</dd>
        </div>
        <div>
          <dt>New term starts</dt>
          <dd data-testid="card-start">{plan.startDate ? formatDate(plan.startDate) : '-'}</dd>
        </div>
        <div>
          <dt>New term ends</dt>
          <dd data-testid="card-end">{plan.endDate ? formatDate(plan.endDate) : '-'}</dd>
        </div>
      </dl>
      <p className="notice notice--info">{planNote(plan, baseTierName)}</p>
    </Modal>
  )
}

function planNote(plan: CardPlan, baseTierName: string): string {
  switch (plan.kind) {
    case 'renew':
      return 'The new term starts the day after your current term ends. The card starts after the payment is confirmed.'
    case 'upgrade':
      return `You pay only the price difference. Your ${baseTierName} card is replaced and the end date stays the same once the payment is confirmed.`
    case 'later':
      return 'This card starts the day after your current card ends. Your current card keeps its discount until then.'
    case 'unknown':
      return 'The dates and the price are calculated when you confirm.'
    default:
      return 'Eligible duration packages only. Single visits are excluded. The card starts after the payment is confirmed.'
  }
}
