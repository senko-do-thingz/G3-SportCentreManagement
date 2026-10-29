import { useCallback, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { cardsApi, type CardTier } from '../../api/cardsApi'
import { ApiError, getErrorMessage, splitFieldErrors } from '../../api/errors'
import type { PackageRegistration } from '../../api/memberApi'
import { packagesApi, type SportPackage } from '../../api/packagesApi'
import { receptionApi, type DeskSale, type MemberOverview, type PaymentMethod } from '../../api/receptionApi'
import { receptionPaymentsApi } from '../../api/receptionPaymentsApi'
import { Button, Card, EmptyState, ErrorState, Input, PageHeader, Select, Table, type TableColumn } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { useResource } from '../../hooks/useResource'
import { formatDate, formatDateTime, formatMoney, humanizeCode } from '../../utils/format'
import { addDays } from '../member/cardsModel'
import { currentCard } from '../member/dashboardModel'
import { packageEndDate, packageSummary, packageTitle, priceBreakdown, validateStartDate } from '../member/packagesModel'
import { MemberHeader, MemberPicker } from './ReceptionMember'
import {
  CARD_PLAN_LABELS,
  changeDue,
  deskCardPlan,
  EMPTY_DESK_PAYMENT,
  isRegisterTab,
  memberSearchPath,
  parseAmount,
  PAYMENT_METHOD_OPTIONS,
  pendingRequests,
  positiveId,
  receptionPaymentPath,
  receptionDate,
  registerPath,
  saleOutcome,
  sellableTiers,
  validateDeskPayment,
  type DeskPaymentErrors,
  type DeskPaymentForm,
  type PendingRequest,
  type RegisterTab,
} from './receptionModel'

/**
 * Register and Renew (Figma F1-14 and its Membership Card state): sells a sport package or a
 * membership card at the desk with the payment method, reference and amount received (BE-19), and
 * shows the receipt link after verifying the payment. The third tab lists the pending online requests
 * (GET /membership-cards/pending, GET /packages/registrations/pending) and opens the payment
 * confirmation screen (F3-05) for each.
 */
const TAB_LABELS: Record<RegisterTab, string> = {
  package: 'Sport package',
  card: 'Membership card',
  pending: 'Pending online requests',
}

export function RegisterRenewPage() {
  const [params, setParams] = useSearchParams()
  const tabParam = params.get('tab')
  const tab: RegisterTab = isRegisterTab(tabParam) ? tabParam : 'package'
  const memberId = positiveId(params.get('memberId'))
  const packageId = positiveId(params.get('packageId'))

  const setTab = (next: RegisterTab) => {
    setParams((current) => {
      const updated = new URLSearchParams(current)
      updated.set('tab', next)
      return updated
    })
  }

  return (
    <div className="page reception-page">
      <PageHeader
        eyebrow="Front desk"
        title="Register and Renew"
        description="Register or renew a sport package or a membership card, and take the payment at the desk."
      />
      <div className="reception-tabs" role="tablist" aria-label="Request type">
        {(Object.keys(TAB_LABELS) as RegisterTab[]).map((value) => (
          <button
            key={value}
            type="button"
            role="tab"
            id={`register-tab-${value}`}
            aria-selected={tab === value}
            aria-controls="register-panel"
            className={`reception-tabs__tab${tab === value ? ' reception-tabs__tab--active' : ''}`}
            onClick={() => setTab(value)}
          >
            {TAB_LABELS[value]}
          </button>
        ))}
      </div>
      <div id="register-panel" role="tabpanel" aria-labelledby={`register-tab-${tab}`}>
        {tab === 'pending' ? (
          <PendingRequestsPanel />
        ) : memberId === null ? (
          <Card title="Choose a member">
            <MemberPicker
              autoSelectSingle
              buttonLabel="Find member"
              onSelect={(member, how) =>
                setParams(
                  (current) => {
                    const updated = new URLSearchParams(current)
                    updated.set('memberId', String(member.userId))
                    return updated
                  },
                  { replace: how === 'auto' },
                )
              }
              notFoundAction={
                <Link className={buttonClassName('secondary', 'md')} to="/reception">
                  Create the member on Member search
                </Link>
              }
            />
          </Card>
        ) : (
          <SaleWorkspace key={`${tab}-${memberId}`} tab={tab} memberId={memberId} packageId={packageId} />
        )}
      </div>
    </div>
  )
}

interface SaleData {
  overview: MemberOverview
  packages: SportPackage[]
  tiers: CardTier[]
}

function SaleWorkspace({ tab, memberId, packageId }: { tab: 'package' | 'card'; memberId: number; packageId: number | null }) {
  const load = useCallback(
    async (signal: AbortSignal): Promise<SaleData> => {
      const [overview, packages, tiers] = await Promise.all([
        receptionApi.memberOverview(memberId, { signal }),
        tab === 'package' ? packagesApi.packages({ signal }) : Promise.resolve([]),
        tab === 'card' ? cardsApi.tiers({ signal }) : Promise.resolve([]),
      ])
      return { overview, packages, tiers }
    },
    [memberId, tab],
  )
  const [data, reload] = useResource(load)
  // The receipt lives here, so reloading the overview after a sale does not lose it. Each new sale
  // mounts a fresh form (saleCount) on the reloaded overview: the card plan, the renewal start date
  // and the request type are then computed from the member's new cards and registrations.
  const [result, setResult] = useState<SaleResult | null>(null)
  const [saleCount, setSaleCount] = useState(0)

  const onSold = (sold: SaleResult) => {
    setResult(sold)
    reload()
  }

  if (result) {
    return (
      <SaleReceipt
        result={result}
        memberId={memberId}
        onNewSale={() => {
          setResult(null)
          setSaleCount((count) => count + 1)
        }}
      />
    )
  }
  if (data.status === 'loading') {
    return <Spinner label="Loading the member and the price list" />
  }
  if (data.status === 'error') {
    return <ErrorState title="We could not load the registration details" error={data.error} onRetry={reload} />
  }
  const { overview } = data.data
  const memberBlock = (
    <MemberHeader member={overview.member}>
      <Link className={buttonClassName('ghost', 'sm')} to={registerPath({ tab })}>
        Change member
      </Link>
    </MemberHeader>
  )
  return tab === 'package' ? (
    <PackageSale key={saleCount} data={data.data} memberBlock={memberBlock} initialPackageId={packageId} onSold={onSold} onRefresh={reload} />
  ) : (
    <CardSale key={saleCount} data={data.data} memberBlock={memberBlock} onSold={onSold} onRefresh={reload} />
  )
}

/* Desk payment fields shared by both sales */

function DeskPaymentFields({
  form,
  errors,
  amountDue,
  disabled,
  onChange,
}: {
  form: DeskPaymentForm
  errors: DeskPaymentErrors
  amountDue: number
  disabled: boolean
  onChange: (field: keyof DeskPaymentForm, value: string) => void
}) {
  const change = changeDue(form, amountDue)
  return (
    <fieldset className="reception-fieldset" disabled={disabled}>
      <legend>Payment at the desk</legend>
      <div className="reception-form__grid">
        <Select
          label="Payment method"
          required
          options={PAYMENT_METHOD_OPTIONS}
          value={form.method}
          onChange={(event) => onChange('method', event.target.value)}
          error={errors.method}
        />
        <Input
          label="Payment reference"
          required={form.method !== 'CASH'}
          hint={form.method === 'CASH' ? 'Optional for cash' : 'Transaction or slip number'}
          maxLength={100}
          value={form.reference}
          onChange={(event) => onChange('reference', event.target.value)}
          error={errors.reference}
        />
        <Input
          label="Amount received (VND)"
          required
          inputMode="numeric"
          maxLength={15}
          value={form.amountReceived}
          onChange={(event) => onChange('amountReceived', event.target.value)}
          error={errors.amountReceived}
          labelAction={
            <button type="button" className="link-button" onClick={() => onChange('amountReceived', String(amountDue))}>
              Use exact amount
            </button>
          }
        />
        <div className="field">
          <p className="field__label">Change to give</p>
          <p className="reception-change" data-testid="change-due">
            {formatMoney(change)}
          </p>
        </div>
      </div>
    </fieldset>
  )
}

function deskPayload(form: DeskPaymentForm, amountDue: number) {
  if (amountDue === 0) return { expectedAmountDue: 0 }
  return {
    expectedAmountDue: amountDue,
    paymentMethod: form.method as PaymentMethod,
    referenceCode: form.reference.trim() || null,
    amountReceived: parseAmount(form.amountReceived) ?? 0,
  }
}

const DESK_FIELDS = ['startDate', 'packageId', 'tierId', 'paymentMethod', 'referenceCode', 'paymentReference', 'amountReceived'] as const
const UNCERTAIN_SALE = 'The sale could not be verified. Check the member profile and payment requests before starting another sale.'

async function verifySalePayment(item: DeskSale, expected: number) {
  const rawAmount = 'cardCode' in item ? item.pricePaid : item.paidAmount
  const amount = rawAmount == null ? NaN : Number(rawAmount)
  if (!Number.isFinite(amount) || amount !== expected) throw new Error(UNCERTAIN_SALE)
  if (item.status === 'ACTIVE' && item.paymentId !== null) {
    const payment = await receptionPaymentsApi.payment(item.paymentId)
    if (payment.status !== 'SUCCESS' || payment.memberId !== item.memberId || Number(payment.amount) !== amount) throw new Error(UNCERTAIN_SALE)
    const received = item.amountReceived == null ? NaN : Number(item.amountReceived)
    const change = item.changeAmount == null ? NaN : Number(item.changeAmount)
    const expectedChange = payment.method === 'CASH' ? received - amount : 0
    if (!Number.isFinite(received) || !Number.isFinite(change) || received < amount || change < 0
      || Math.round(expectedChange * 100) !== Math.round(change * 100)
      || (payment.method !== 'CASH' && received !== amount)) throw new Error(UNCERTAIN_SALE)
    return { received, change }
  }
  return { received: 0, change: 0 }
}

/** Maps backend field errors onto the form; the rest goes above the form. */
function saleErrors(error: unknown): { payment: DeskPaymentErrors; startDate?: string; message: string | null } {
  if (error instanceof ApiError && error.hasFieldErrors) {
    const split = splitFieldErrors(error.fieldErrors, DESK_FIELDS)
    const leftovers = [split.fields.packageId, split.fields.tierId, split.formMessage].filter(Boolean).join(' ')
    return {
      payment: {
        method: split.fields.paymentMethod,
        reference: split.fields.referenceCode ?? split.fields.paymentReference,
        amountReceived: split.fields.amountReceived,
      },
      startDate: split.fields.startDate,
      message: leftovers || null,
    }
  }
  return { payment: {}, message: getErrorMessage(error) }
}

interface SaleResult {
  kind: 'package' | 'card'
  code: string
  title: string
  status: string
  startDate: string | null
  endDate: string | null
  paymentId: number | null
  paymentCode: string | null
  amount: number | string | null
  change: number
  itemId: number
}

function SaleReceipt({ result, memberId, onNewSale }: { result: SaleResult; memberId: number; onNewSale: () => void }) {
  const outcome = saleOutcome({ status: result.status, paymentId: result.paymentId, amount: result.amount })
  const notice: Record<typeof outcome, { tone: string; text: string }> = {
    paid: { tone: 'success', text: `${result.title} is active. Payment ${result.paymentCode ?? result.paymentId} is recorded.` },
    pending: {
      tone: 'warning',
      text: `${result.title} was saved as pending payment. Confirm the payment to activate it.`,
    },
    unrecorded: {
      tone: 'warning',
      text: `${result.title} is active, but no payment is linked. Ask a manager to reconcile the sale before collecting money.`,
    },
    noPayment: { tone: 'success', text: `${result.title} is active. No payment is required.` },
    other: { tone: 'warning', text: `${result.title} was saved as ${humanizeCode(result.status).toLowerCase()}.` },
  }
  return (
    <Card title="Sale recorded" aria-label="Sale recorded">
      <p className={`notice notice--${notice[outcome].tone}`} role="status">
        {notice[outcome].text}
      </p>
      <dl className="summary-list">
        <div>
          <dt>Reference</dt>
          <dd>{result.code}</dd>
        </div>
        <div>
          <dt>Validity</dt>
          <dd>
            {formatDate(result.startDate)} to {formatDate(result.endDate)}
          </dd>
        </div>
        <div>
          <dt>Payment</dt>
          <dd>{result.paymentCode ?? (outcome === 'unrecorded' ? 'No payment record' : '-')}</dd>
        </div>
        <div className="summary-list__total">
          <dt>{outcome === 'paid' ? 'Amount charged' : outcome === 'noPayment' ? 'Fee' : 'Amount to review'}</dt>
          <dd>{formatMoney(result.amount)}</dd>
        </div>
        {outcome === 'paid' && result.change > 0 && (
          <div>
            <dt>Change to give</dt>
            <dd>{formatMoney(result.change)}</dd>
          </div>
        )}
      </dl>
      <div className="reception-actions">
        {outcome === 'paid' && result.paymentId !== null && (
          <Link className={buttonClassName('primary', 'md')} to={receptionPaymentPath(result.paymentId)}>
            View receipt
          </Link>
        )}
        {outcome === 'pending' && result.paymentId !== null && (
          <Link
            className={buttonClassName('primary', 'md')}
            to={receptionPaymentPath(result.paymentId, memberId)}
          >
            Confirm payment
          </Link>
        )}
        <Link className={buttonClassName('secondary', 'md')} to={memberSearchPath(memberId)}>
          Back to member profile
        </Link>
        <Button variant="ghost" onClick={onNewSale}>
          New sale
        </Button>
      </div>
    </Card>
  )
}

/* Sport package sale */

function defaultStartDate(registrations: PackageRegistration[], packageId: number | null, today: string): string {
  if (packageId === null) return today
  // Renewal: start the day after the latest active registration of the same package.
  const latestEnd = registrations
    .filter((item) => item.packageId === packageId && item.status === 'ACTIVE' && item.endDate && item.endDate >= today)
    .map((item) => item.endDate as string)
    .sort()
    .pop()
  return latestEnd ? addDays(latestEnd, 1) : today
}

function PackageSale({
  data,
  memberBlock,
  initialPackageId,
  onSold,
  onRefresh,
}: {
  data: SaleData
  memberBlock: ReactNode
  initialPackageId: number | null
  onSold: (result: SaleResult) => void
  onRefresh: () => void
}) {
  const today = receptionDate(new Date())
  const { overview, packages } = data
  const known = initialPackageId !== null && packages.some((item) => item.id === initialPackageId)
  const [selectedId, setSelectedId] = useState<string>(known ? String(initialPackageId) : '')
  const [startDate, setStartDate] = useState(() =>
    defaultStartDate(overview.registrations, known ? initialPackageId : null, today),
  )
  const [payment, setPayment] = useState<DeskPaymentForm>(EMPTY_DESK_PAYMENT)
  const [errors, setErrors] = useState<{ packageId?: string; startDate?: string; payment: DeskPaymentErrors }>({
    payment: {},
  })
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const inFlight = useRef(false)
  const [uncertain, setUncertain] = useState(false)
  const [priceChanged, setPriceChanged] = useState(false)

  const selected = packages.find((item) => String(item.id) === selectedId) ?? null
  const card = currentCard(overview.cards, today)
  const price = selected ? priceBreakdown(selected, card) : null
  const amountDue = price?.final ?? 0
  const isRenewal =
    selected !== null && overview.registrations.some(
      (item) => item.packageId === selected.id && (item.status === 'ACTIVE' || item.status === 'EXPIRED'),
    )
  const options = useMemo(
    () =>
      [...packages]
        .sort((a, b) => a.sportName.localeCompare(b.sportName) || a.durationDays - b.durationDays)
        .map((item) => ({ value: String(item.id), label: `${packageTitle(item)} - ${packageSummary(item)}` })),
    [packages],
  )

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (inFlight.current || uncertain) return
    const checkToday = receptionDate(new Date())
    const packageError = selected ? undefined : 'Choose a package'
    const dateError = validateStartDate(startDate, checkToday) ?? undefined
    const paymentErrors = price ? validateDeskPayment(payment, price.final) : {}
    setErrors({ packageId: packageError, startDate: dateError, payment: paymentErrors })
    setFormError(null)
    if (!selected || !price || packageError || dateError || Object.keys(paymentErrors).length > 0) {
      return
    }
    setSaving(true)
    inFlight.current = true
    let accepted = false
    try {
      const sold = await receptionApi.sellPackage({
        memberId: overview.member.userId,
        packageId: selected.id,
        startDate,
        channel: 'RECEPTION',
        ...deskPayload(payment, amountDue),
      })
      accepted = true
      const collection = await verifySalePayment(sold, amountDue)
      onSold({
        kind: 'package',
        itemId: sold.id,
        code: sold.registrationCode,
        title: `${packageTitle(selected)} (${sold.registrationCode})`,
        status: sold.status,
        startDate: sold.startDate,
        endDate: sold.endDate,
        paymentId: sold.paymentId,
        paymentCode: sold.paymentCode,
        amount: sold.paidAmount ?? price.final,
        change: collection.change,
      })
    } catch (error) {
      const ambiguous = accepted || !(error instanceof ApiError) || error.status === 0 || error.status >= 500
      const stalePrice = error instanceof ApiError && error.code === 'DESK_SALE_PRICE_CHANGED'
      setPriceChanged(stalePrice)
      setUncertain(ambiguous || stalePrice)
      const mapped = ambiguous ? { payment: {}, message: UNCERTAIN_SALE } : saleErrors(error)
      setErrors({ payment: mapped.payment, startDate: mapped.startDate })
      setFormError(mapped.message)
    } finally {
      inFlight.current = false
      setSaving(false)
    }
  }

  return (
    <div className="reception-sale">
      <Card title="Registration details">
        {memberBlock}
        <form className="reception-form" onSubmit={submit} noValidate aria-label="Sell a sport package">
          {formError && (
            <p className="notice notice--danger" role="alert">
              {formError}
            </p>
          )}
          {packages.length === 0 ? (
            <EmptyState title="No package on sale" description="The manager has not published any sport package yet." />
          ) : (
            <div className="reception-form__grid">
              <Select
                label="Package"
                required
                placeholder="Choose a package"
                options={options}
                value={selectedId}
                disabled={saving || uncertain}
                onChange={(event) => {
                  setSelectedId(event.target.value)
                  const id = positiveId(event.target.value)
                  const nextPackage = packages.find((item) => item.id === id)
                  if (nextPackage && (amountDue === 0) !== (priceBreakdown(nextPackage, card).final === 0)) setPayment(EMPTY_DESK_PAYMENT)
                  setStartDate(defaultStartDate(overview.registrations, id, receptionDate(new Date())))
                  setErrors((current) => ({ ...current, packageId: undefined }))
                }}
                error={errors.packageId}
              />
              <Input
                label="Start date"
                type="date"
                required
                min={today}
                value={startDate}
                disabled={saving || uncertain}
                onChange={(event) => {
                  setStartDate(event.target.value)
                  setErrors((current) => ({ ...current, startDate: undefined }))
                }}
                error={errors.startDate}
              />
            </div>
          )}
          {price && amountDue > 0 && (
            <DeskPaymentFields
              form={payment}
              errors={errors.payment}
              amountDue={price.final}
              disabled={saving || uncertain}
              onChange={(field, value) => {
                setPayment((current) => ({ ...current, [field]: value }))
                setErrors((current) => ({ ...current, payment: { ...current.payment, [field]: undefined } }))
              }}
            />
          )}
          <div className="reception-form__actions">
            <Button type="submit" loading={saving} disabled={uncertain || packages.length === 0}>
              Record payment and register
            </Button>
            {priceChanged && <Button variant="secondary" onClick={onRefresh}>Refresh prices</Button>}
          </div>
        </form>
      </Card>
      <Card title="Fee summary" aria-label="Fee summary">
        <dl className="summary-list">
          <div>
            <dt>Member</dt>
            <dd>{overview.member.fullName}</dd>
          </div>
          <div>
            <dt>Request type</dt>
            <dd>{selected ? (isRenewal ? 'Renewal' : 'New registration') : '-'}</dd>
          </div>
          <div>
            <dt>Sport and training</dt>
            <dd>{selected ? packageTitle(selected) : '-'}</dd>
          </div>
          <div>
            <dt>Package</dt>
            <dd>{selected ? packageSummary(selected) : '-'}</dd>
          </div>
          <div>
            <dt>Validity</dt>
            <dd>
              {selected && startDate
                ? `${formatDate(startDate)} to ${formatDate(packageEndDate(startDate, selected.durationDays))}`
                : '-'}
            </dd>
          </div>
          <div>
            <dt>Original price</dt>
            <dd>{price ? formatMoney(price.original) : '-'}</dd>
          </div>
          <div>
            <dt>Membership discount</dt>
            <dd>
              {price ? (price.discountPercent > 0 ? `${price.discountPercent}% - ${formatMoney(price.discountAmount)}` : 'None') : '-'}
            </dd>
          </div>
          <div className="summary-list__total">
            <dt>Total payable</dt>
            <dd data-testid="total-payable">{price ? formatMoney(price.final) : '-'}</dd>
          </div>
        </dl>
        <p className="notice notice--info">
          Use the existing member profile for renewals. Card and sport package purchases are separate transactions.
        </p>
      </Card>
    </div>
  )
}

/* Membership card sale */

function CardSale({
  data,
  memberBlock,
  onSold,
  onRefresh,
}: {
  data: SaleData
  memberBlock: ReactNode
  onSold: (result: SaleResult) => void
  onRefresh: () => void
}) {
  const today = receptionDate(new Date())
  const { overview } = data
  const tiers = useMemo(() => sellableTiers(data.tiers), [data.tiers])
  const [tierId, setTierId] = useState('')
  const [payment, setPayment] = useState<DeskPaymentForm>(EMPTY_DESK_PAYMENT)
  const [errors, setErrors] = useState<{ tierId?: string; payment: DeskPaymentErrors }>({ payment: {} })
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const inFlight = useRef(false)
  const [uncertain, setUncertain] = useState(false)
  const [priceChanged, setPriceChanged] = useState(false)

  const tier = tiers.find((item) => String(item.id) === tierId) ?? null
  const desk = tier ? deskCardPlan(tier, overview.cards, data.tiers, today) : null
  const plan = desk?.plan ?? null
  const amountDue = plan?.price ?? 0
  const card = currentCard(overview.cards, today)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (inFlight.current || uncertain) return
    const tierError = tier ? undefined : 'Choose a membership card'
    const paymentErrors = desk?.sellable ? validateDeskPayment(payment, amountDue) : {}
    setErrors({ tierId: tierError, payment: paymentErrors })
    setFormError(null)
    if (!tier || !desk || !desk.sellable || tierError || Object.keys(paymentErrors).length > 0) {
      return
    }
    setSaving(true)
    inFlight.current = true
    let accepted = false
    try {
      const sold = await receptionApi.sellCard({
        memberId: overview.member.userId,
        tierId: tier.id,
        ...deskPayload(payment, amountDue),
      })
      accepted = true
      const collection = await verifySalePayment(sold, amountDue)
      onSold({
        kind: 'card',
        itemId: sold.id,
        code: sold.cardCode,
        title: `${sold.tierName} card (${sold.cardCode})`,
        status: sold.status,
        startDate: sold.startDate,
        endDate: sold.endDate,
        paymentId: sold.paymentId,
        paymentCode: sold.paymentCode,
        amount: sold.pricePaid ?? amountDue,
        change: collection.change,
      })
    } catch (error) {
      const ambiguous = accepted || !(error instanceof ApiError) || error.status === 0 || error.status >= 500
      const stalePrice = error instanceof ApiError && error.code === 'DESK_SALE_PRICE_CHANGED'
      setPriceChanged(stalePrice)
      setUncertain(ambiguous || stalePrice)
      const mapped = ambiguous ? { payment: {}, message: UNCERTAIN_SALE } : saleErrors(error)
      setErrors({ payment: mapped.payment })
      setFormError(mapped.message)
    } finally {
      inFlight.current = false
      setSaving(false)
    }
  }

  return (
    <div className="reception-sale">
      <Card title="Registration details">
        {memberBlock}
        <form className="reception-form" onSubmit={submit} noValidate aria-label="Sell a membership card">
          {formError && (
            <p className="notice notice--danger" role="alert">
              {formError}
            </p>
          )}
          {tiers.length === 0 ? (
            <EmptyState title="No card on sale" description="The manager has not published any paid membership card." />
          ) : (
            <div className="reception-form__grid">
              <Select
                label="Membership card"
                required
                placeholder="Choose a card"
                options={tiers.map((item) => ({
                  value: String(item.id),
                  label: `${item.name} - ${item.durationMonths} months - ${formatMoney(item.price)}`,
                }))}
                value={tierId}
                disabled={saving || uncertain}
                onChange={(event) => {
                  const nextTier = tiers.find((item) => String(item.id) === event.target.value)
                  if (nextTier && (amountDue === 0) !== (deskCardPlan(nextTier, overview.cards, data.tiers, today).plan.price === 0)) setPayment(EMPTY_DESK_PAYMENT)
                  setTierId(event.target.value)
                  setErrors((current) => ({ ...current, tierId: undefined }))
                  setFormError(null)
                }}
                error={errors.tierId}
              />
              <div className="field">
                <p className="field__label">Current card</p>
                <p className="reception-change">
                  {card ? `${card.tierName} until ${formatDate(card.endDate)}` : 'No card in force'}
                </p>
              </div>
            </div>
          )}
          {desk?.sellable && amountDue > 0 && (
            <DeskPaymentFields
              form={payment}
              errors={errors.payment}
              amountDue={amountDue}
              disabled={saving || uncertain}
              onChange={(field, value) => {
                setPayment((current) => ({ ...current, [field]: value }))
                setErrors((current) => ({ ...current, payment: { ...current.payment, [field]: undefined } }))
              }}
            />
          )}
          {desk && !desk.sellable && (
            <p className="notice notice--warning" role="alert">
              {desk.message}
            </p>
          )}
          <div className="reception-form__actions">
            <Button type="submit" loading={saving} disabled={uncertain || tiers.length === 0 || (desk !== null && !desk.sellable)}>
              Record payment and issue card
            </Button>
            {priceChanged && <Button variant="secondary" onClick={onRefresh}>Refresh prices</Button>}
          </div>
        </form>
      </Card>
      <Card title="Fee summary" aria-label="Fee summary">
        <dl className="summary-list">
          <div>
            <dt>Member</dt>
            <dd>{overview.member.fullName}</dd>
          </div>
          <div>
            <dt>Membership card</dt>
            <dd>{tier ? tier.name : '-'}</dd>
          </div>
          <div>
            <dt>Request type</dt>
            <dd>{plan ? CARD_PLAN_LABELS[plan.kind] : '-'}</dd>
          </div>
          <div>
            <dt>Package discount</dt>
            <dd>{tier ? `${tier.discountPercentage ?? 0}%` : '-'}</dd>
          </div>
          <div>
            <dt>New term</dt>
            <dd>{plan?.startDate ? `${formatDate(plan.startDate)} to ${formatDate(plan.endDate)}` : '-'}</dd>
          </div>
          <div className="summary-list__total">
            <dt>Card fee</dt>
            <dd data-testid="total-payable">{plan && plan.price !== null ? formatMoney(plan.price) : '-'}</dd>
          </div>
        </dl>
        <p className="notice notice--info">
          Renewals start the day after the current term ends. An upgrade keeps the current end date and costs the
          difference between the two cards.
        </p>
      </Card>
    </div>
  )
}

/* Pending online requests */

async function loadPending(signal: AbortSignal): Promise<PendingRequest[]> {
  const { cards, registrations } = await receptionApi.pendingQueue({ signal })
  return pendingRequests(cards, registrations)
}

function PendingRequestsPanel() {
  const [pending, reload] = useResource(loadPending)
  const columns: TableColumn<PendingRequest>[] = [
    { key: 'createdAt', header: 'Requested', render: (row) => formatDateTime(row.createdAt) },
    { key: 'member', header: 'Member', render: (row) => row.memberLabel },
    {
      key: 'request',
      header: 'Request',
      render: (row) => (
        <>
          <span className="reception-cell__title">{row.description}</span>
          <span className="reception-cell__meta">{row.code}</span>
        </>
      ),
    },
    { key: 'paymentCode', header: 'Payment', render: (row) => row.paymentCode ?? '-' },
    { key: 'amount', header: 'Amount', align: 'right', render: (row) => formatMoney(row.amount) },
    {
      key: 'action',
      header: <span className="visually-hidden">Action</span>,
      align: 'right',
      render: (row) =>
        row.paymentId !== null ? (
          <Link
            className={buttonClassName('primary', 'sm')}
            to={receptionPaymentPath(row.paymentId, row.memberId)}
            aria-label={`Confirm payment for ${row.code}`}
          >
            Confirm payment
          </Link>
        ) : (
          <span className="reception-cell__meta">No payment linked</span>
        ),
    },
  ]
  return (
    <Card
      title="Pending online requests"
      actions={
        <Button variant="secondary" size="sm" onClick={reload} disabled={pending.status === 'loading'}>
          Refresh
        </Button>
      }
    >
      <p className="card__intro">
        Members who requested a card or a package online and will pay at the desk or by transfer. Oldest first.
      </p>
      <Table
        caption="Pending online requests"
        columns={columns}
        rows={pending.status === 'ready' ? pending.data : []}
        rowKey={(row) => row.key}
        loading={pending.status === 'loading'}
        error={pending.status === 'error' ? pending.error : undefined}
        onRetry={reload}
        emptyTitle="No pending request"
        emptyDescription="Every online request is paid or cancelled."
      />
    </Card>
  )
}
