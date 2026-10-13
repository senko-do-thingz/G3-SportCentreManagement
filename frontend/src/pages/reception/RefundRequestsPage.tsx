import { useCallback, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../../api/errors'
import { receptionApi } from '../../api/receptionApi'
import { receptionPaymentsApi, type Refund, type RefundQuote } from '../../api/receptionPaymentsApi'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  Input,
  PageHeader,
  StatusBadge,
  Table,
  TextArea,
  useToast,
  type TableColumn,
} from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { useResource } from '../../hooks/useResource'
import { formatDate, formatDateTime, formatMoney } from '../../utils/format'
import { parseAmount, positiveId } from './receptionModel'
import {
  memberLine,
  PAYMENT_LIST_PATH,
  paymentStatusLabel,
  QUERY_MAX,
  REASON_MAX,
  refundableRegistrations,
  refundAmount,
  refundCreatePath,
  refundDetailPath,
  refundStatusLabel,
  memberRefundHistory,
  sortRefunds,
  validateRefundForm,
  type RefundErrors,
  type RefundForm,
} from './receptionPaymentsModel'

const EYEBROW = 'Receptionist / Payments and reports'

/**
 * Refund Requests (Figma F3-07) at /reception/refunds.
 * - Find the member (GET /members) and one of their paid registrations, then
 *   GET /refunds/quote?registrationId= (BE-21) fills the refund amount with the suggested value and
 *   the eligibility check. Submit for approval sends POST /refunds and opens S3-RefundSubmitted.
 * - Queues: first the APPROVED refunds waiting to be paid back (GET /refunds?status=APPROVED,
 *   BE-21), which open S3-RefundApproved; then, for the chosen member, GET /refunds/member/{id}
 *   without PENDING rows; last the PENDING refunds of GET /refunds/pending. A PENDING refund is
 *   therefore shown in one table only.
 * The member and registration are kept in the URL (?memberId=&registrationId=).
 */
export function RefundRequestsPage() {
  const [params, setParams] = useSearchParams()
  const memberId = positiveId(params.get('memberId'))
  const registrationId = positiveId(params.get('registrationId'))

  const chooseMember = (id: number | null) => {
    setParams(id === null ? new URLSearchParams() : new URLSearchParams({ memberId: String(id) }))
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow={EYEBROW}
        title="Refund Requests"
        description="Check eligibility and submit a refund for manager approval."
        actions={
          <Link className={buttonClassName('secondary', 'md')} to={PAYMENT_LIST_PATH}>
            Payment requests
          </Link>
        }
      />
      <ApprovedRefunds />
      {memberId !== null && registrationId !== null ? (
        <RefundFormSection key={registrationId} memberId={memberId} registrationId={registrationId} />
      ) : (
        <RegistrationPicker memberId={memberId} onMember={chooseMember} />
      )}
      {memberId !== null && <MemberRefunds memberId={memberId} />}
      <PendingRefunds />
    </div>
  )
}

/* Step 1: member and registration */

function RegistrationPicker({ memberId, onMember }: { memberId: number | null; onMember: (id: number | null) => void }) {
  const [query, setQuery] = useState('')
  const [submitted, setSubmitted] = useState<string | null>(null)
  const [queryError, setQueryError] = useState<string | null>(null)

  const loadMembers = useCallback(
    (signal: AbortSignal) =>
      submitted ? receptionApi.searchMembers({ query: submitted, signal }).then((page) => page.content) : Promise.resolve([]),
    [submitted],
  )
  const [members, reloadMembers] = useResource(loadMembers)

  const onSearch = (event: FormEvent) => {
    event.preventDefault()
    const value = query.trim()
    if (!value) {
      setQueryError('Enter a name, phone, email or member code')
      return
    }
    setQueryError(null)
    setSubmitted(value.slice(0, QUERY_MAX))
  }

  return (
    <Card title="Find the registration" aria-label="Find the registration">
      {memberId === null ? (
        <>
          <form className="member-picker__form payment-picker-form" role="search" aria-label="Find member for refund" onSubmit={onSearch} noValidate>
            <Input
              label="Member"
              value={query}
              maxLength={QUERY_MAX}
              error={queryError ?? undefined}
              placeholder="Name, phone, email or member code"
              onChange={(event) => setQuery(event.target.value)}
            />
            <Button type="submit" variant="secondary">
              Find member
            </Button>
          </form>
          {submitted && members.status === 'loading' && <Spinner label="Searching members" />}
          {submitted && members.status === 'error' && (
            <ErrorState title="We could not search members" error={members.error} onRetry={reloadMembers} />
          )}
          {submitted && members.status === 'ready' && members.data.length === 0 && (
            <EmptyState title="No member found" description="Check the spelling or search by phone." variant="plain" />
          )}
          {members.status === 'ready' && members.data.length > 0 && (
            <ul className="member-results__list payment-member-results" aria-label="Members found">
              {members.data.map((member) => (
                <li key={member.userId} className="member-results__item payment-member-results__item">
                  <span>
                    <span className="reception-cell__title">{member.fullName}</span>
                    <span className="reception-cell__meta">{[member.memberCode, member.phone].filter(Boolean).join(' / ')}</span>
                  </span>
                  <Button
                    size="sm"
                    variant="secondary"
                    aria-label={`Select ${member.fullName} ${member.memberCode ?? ''}`.trim()}
                    onClick={() => onMember(member.userId)}
                  >
                    Select
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </>
      ) : (
        <MemberRegistrations memberId={memberId} onChangeMember={() => onMember(null)} />
      )}
    </Card>
  )
}

function MemberRegistrations({ memberId, onChangeMember }: { memberId: number; onChangeMember: () => void }) {
  const load = useCallback(
    (signal: AbortSignal) => receptionApi.memberRegistrations(memberId, { signal }).then(refundableRegistrations),
    [memberId],
  )
  const [registrations, reload] = useResource(load)

  return (
    <>
      <div className="reception-notice-row">
        <p className="card__intro">Choose the paid registration to refund.</p>
        <Button size="sm" variant="ghost" onClick={onChangeMember}>
          Change member
        </Button>
      </div>
      {registrations.status === 'loading' && <Spinner label="Loading registrations" />}
      {registrations.status === 'error' && (
        <ErrorState title="We could not load the registrations" error={registrations.error} onRetry={reload} />
      )}
      {registrations.status === 'ready' && registrations.data.length === 0 && (
        <EmptyState title="Nothing to refund" description="This member has no paid registration." variant="plain" />
      )}
      {registrations.status === 'ready' && registrations.data.length > 0 && (
        <ul className="member-results__list" aria-label="Paid registrations">
          {registrations.data.map((registration) => (
            <li key={registration.id} className="member-results__item payment-member-results__item">
              <span>
                <span className="reception-cell__title">
                  {registration.registrationCode} - {registration.packageName}
                </span>
                <span className="reception-cell__meta">
                  {formatMoney(registration.paidAmount)} / {formatDate(registration.startDate)} to {formatDate(registration.endDate)}
                </span>
              </span>
              <Link
                className={buttonClassName('secondary', 'sm')}
                to={refundCreatePath(memberId, registration.id)}
                aria-label={`Start refund for ${registration.registrationCode}`}
              >
                Start refund
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

/* Step 2: the refund form (F3-07) */

function RefundFormSection({ memberId, registrationId }: { memberId: number; registrationId: number }) {
  const load = useCallback(
    (signal: AbortSignal) => receptionPaymentsApi.refundQuote(registrationId, { signal }),
    [registrationId],
  )
  const [quote, reload] = useResource(load)

  if (quote.status === 'loading') {
    return (
      <Card title="New refund">
        <Spinner label="Checking the refund" />
      </Card>
    )
  }
  if (quote.status === 'error') {
    return (
      <Card title="New refund">
        <ErrorState title="We could not check the refund" error={quote.error} onRetry={reload} />
        <Link className={buttonClassName('ghost', 'md')} to={refundCreatePath(memberId)}>
          Choose another registration
        </Link>
      </Card>
    )
  }
  return <RefundFormCards key={quote.data.registrationId} memberId={memberId} quote={quote.data} />
}

function RefundFormCards({ memberId, quote }: { memberId: number; quote: RefundQuote }) {
  const toast = useToast()
  const navigate = useNavigate()
  const [form, setForm] = useState<RefundForm>({ amount: String(quote.suggestedAmount), reason: '' })
  const [touched, setTouched] = useState<Partial<Record<keyof RefundForm, boolean>>>({})
  const [serverErrors, setServerErrors] = useState<RefundErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const errors = validateRefundForm(form, quote.maxRefundable)
  const valid = Object.keys(errors).length === 0
  const fieldError = (key: keyof RefundForm) => serverErrors[key] ?? (touched[key] ? errors[key] : undefined)
  const change = (key: keyof RefundForm, value: string) => {
    setForm((current) => ({ ...current, [key]: value }))
    setServerErrors((current) => ({ ...current, [key]: undefined }))
    setFormError(null)
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setTouched({ amount: true, reason: true })
    if (!valid || !quote.eligible || saving) return
    setSaving(true)
    setFormError(null)
    try {
      const refund = await receptionPaymentsApi.createRefund({
        packageRegistrationId: quote.registrationId,
        amountRequested: parseAmount(form.amount) ?? 0,
        reason: form.reason.trim(),
      })
      toast.success('Refund submitted', `${refund.refundCode} waits for manager approval.`)
      navigate(refundDetailPath(refund.id, refund.memberId ?? memberId))
    } catch (failure) {
      setSaving(false)
      if (failure instanceof ApiError && failure.hasFieldErrors) {
        setServerErrors({
          amount: failure.fieldErrors.amountRequested,
          reason: failure.fieldErrors.reason,
        })
        const others = Object.entries(failure.fieldErrors)
          .filter(([key]) => key !== 'amountRequested' && key !== 'reason')
          .map(([, message]) => message)
        setFormError(others.length > 0 ? others.join(' ') : null)
        return
      }
      setFormError(getErrorMessage(failure))
    }
  }

  return (
    <div className="reception-grid">
      <Card title="Refund request" aria-label="Refund request">
        <dl className="summary-list payment-rows">
          <div>
            <dt>Member</dt>
            <dd>{memberLine(quote.memberFullName, quote.memberCode, quote.memberId ?? memberId)}</dd>
          </div>
          <div>
            <dt>Original payment</dt>
            <dd>
              {[quote.invoiceNumber ?? quote.paymentCode, quote.paidAmount !== null ? formatMoney(quote.paidAmount) : null]
                .filter(Boolean)
                .join(' / ') || '-'}
            </dd>
          </div>
          <div>
            <dt>Service</dt>
            <dd>{[quote.registrationCode, quote.serviceName].filter(Boolean).join(' / ') || '-'}</dd>
          </div>
        </dl>
        <form className="reception-form" aria-label="Submit refund" noValidate onSubmit={submit}>
          <div className="reception-form__grid">
            <Input
              label="Refund amount"
              required
              inputMode="numeric"
              value={form.amount}
              disabled={saving || !quote.eligible}
              error={fieldError('amount')}
              hint={`Suggested ${formatMoney(quote.suggestedAmount)}, at most ${formatMoney(quote.maxRefundable)}`}
              onChange={(event) => change('amount', event.target.value)}
              onBlur={() => setTouched((current) => ({ ...current, amount: true }))}
            />
            <TextArea
              label="Reason"
              required
              rows={2}
              maxLength={REASON_MAX}
              value={form.reason}
              disabled={saving || !quote.eligible}
              error={fieldError('reason')}
              onChange={(event) => change('reason', event.target.value)}
              onBlur={() => setTouched((current) => ({ ...current, reason: true }))}
            />
          </div>
          <p className="payment-hint">The refund is returned with the original payment method after approval.</p>
          {formError && (
            <p className="notice notice--danger" role="alert">
              {formError}
            </p>
          )}
          <div className="reception-form__actions">
            <Button type="submit" loading={saving} disabled={!quote.eligible}>
              Submit for approval
            </Button>
            <Link className={buttonClassName('ghost', 'md')} to={refundCreatePath(memberId)}>
              Choose another registration
            </Link>
          </div>
        </form>
      </Card>
      <Card title="Eligibility check" aria-label="Eligibility check">
        <dl className="summary-list payment-rows">
          <div>
            <dt>Original payment</dt>
            <dd>{quote.paymentStatus ? paymentStatusLabel(quote.paymentStatus) : '-'}</dd>
          </div>
          <div>
            <dt>Prior refund</dt>
            <dd>{quote.priorRefundAmount ? formatMoney(quote.priorRefundAmount) : 'None'}</dd>
          </div>
          <div>
            <dt>Refundable balance</dt>
            <dd>{formatMoney(quote.maxRefundable)}</dd>
          </div>
        </dl>
        <div className="payment-check" role="status">
          {quote.eligible ? <Badge tone="success">Eligible for review</Badge> : <Badge tone="danger">Not eligible</Badge>}
        </div>
        {!quote.eligible && (
          <p className="notice notice--warning" role="alert">
            {quote.ineligibleReason ?? 'This registration cannot be refunded.'}
          </p>
        )}
        <p className="notice notice--info">
          A cancelled package releases its reserved sessions. It does not refund the full package automatically.
        </p>
      </Card>
    </div>
  )
}

/* Queues */

/** Built once: the columns do not depend on any state. */
const REFUND_COLUMNS: TableColumn<Refund>[] = [
  {
    key: 'refundCode',
    header: 'Refund',
    render: (row) => (
      <>
        <span className="reception-cell__title">{row.refundCode}</span>
        <span className="reception-cell__meta">{row.registrationCode ?? '-'}</span>
      </>
    ),
  },
  { key: 'member', header: 'Member', render: (row) => memberLine(row.memberFullName, row.memberCode, row.memberId) },
  { key: 'amount', header: 'Amount', render: (row) => formatMoney(refundAmount(row)) },
  { key: 'createdAt', header: 'Requested', render: (row) => formatDateTime(row.createdAt) },
  { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} label={refundStatusLabel(row.status)} /> },
  {
    key: 'action',
    header: 'Action',
    render: (row) => (
      <Link
        className={buttonClassName('secondary', 'sm')}
        to={refundDetailPath(row.id, row.memberId)}
        aria-label={`Open refund ${row.refundCode}`}
      >
        {row.status === 'APPROVED' ? 'Record refund' : 'Open'}
      </Link>
    ),
  },
]

function RefundQueue({
  title,
  caption,
  load,
  emptyTitle,
  hint,
}: {
  title: string
  caption: string
  load: (signal: AbortSignal) => Promise<Refund[]>
  emptyTitle: string
  hint?: string
}) {
  const [refunds, reload] = useResource(load)
  return (
    <Card title={title}>
      <Table
        caption={caption}
        columns={REFUND_COLUMNS}
        rows={refunds.status === 'ready' ? refunds.data : []}
        rowKey={(row) => row.id}
        loading={refunds.status === 'loading'}
        error={refunds.status === 'error' ? refunds.error : undefined}
        onRetry={reload}
        emptyTitle={emptyTitle}
      />
      {hint && <p className="payment-hint">{hint}</p>}
    </Card>
  )
}

const loadApproved = (signal: AbortSignal) => receptionPaymentsApi.approvedRefunds({ signal }).then(sortRefunds)
const loadPending = (signal: AbortSignal) => receptionPaymentsApi.pendingRefunds({ signal }).then(sortRefunds)

/** APPROVED refunds, the only ones the desk acts on (S3-RefundApproved). GET /refunds?status=APPROVED (BE-21). */
function ApprovedRefunds() {
  return (
    <RefundQueue
      title="Approved - waiting to be refunded"
      caption="Approved refunds waiting to be refunded"
      load={loadApproved}
      emptyTitle="No approved refund is waiting"
      hint="Return the money first, then open the refund and record the transaction reference."
    />
  )
}

function PendingRefunds() {
  return (
    <RefundQueue
      title="Waiting for manager approval"
      caption="Refunds waiting for approval"
      load={loadPending}
      emptyTitle="No refund is waiting"
    />
  )
}

/** Refund history of the chosen member, without PENDING ones (they are in the approval queue). */
function MemberRefunds({ memberId }: { memberId: number }) {
  const load = useCallback(
    (signal: AbortSignal) => receptionPaymentsApi.memberRefunds(memberId, { signal }).then(memberRefundHistory),
    [memberId],
  )
  return (
    <RefundQueue
      title="Refunds of this member"
      caption="Refunds of this member"
      load={load}
      emptyTitle="No refund to show"
      hint="Pending refunds of this member are listed under Waiting for manager approval."
    />
  )
}
