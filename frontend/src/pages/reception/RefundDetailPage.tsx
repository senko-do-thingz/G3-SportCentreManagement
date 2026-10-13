import { useCallback, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { getErrorMessage } from '../../api/errors'
import { receptionPaymentsApi, type Refund } from '../../api/receptionPaymentsApi'
import { Button, Card, EmptyState, ErrorState, Input, PageHeader, useToast } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { useResource } from '../../hooks/useResource'
import { formatDateTime, formatMoney } from '../../utils/format'
import { positiveId } from './receptionModel'
import {
  REFUND_LIST_PATH,
  REFUND_REFERENCE_MAX,
  refundAmount,
  refundCreatePath,
  refundOriginal,
  refundStage,
  refundStatusLabel,
  validateRefundReference,
} from './receptionPaymentsModel'

const EYEBROW = 'Receptionist / Payments and reports'

/**
 * One refund at /reception/refunds/:refundId?memberId= (FE-12). There is no GET /refunds/{id}, so
 * the refund is read from GET /refunds/member/{memberId} (all statuses) or, without a member,
 * from GET /refunds?status=APPROVED (BE-21) and then GET /refunds/pending. The status picks the
 * Figma state:
 * - PENDING: Refund Request Submitted (S3-RefundSubmitted), the payment stays paid.
 * - APPROVED: Refund Approved (S3-RefundApproved) with the refund transaction reference and
 *   Record completed refund (PUT /refunds/{id}/complete).
 * - COMPLETED: Refund Completed (S3-RefundDone).
 * - REJECTED: the manager decision and note.
 */
export function RefundDetailPage() {
  const { refundId } = useParams()
  const [params] = useSearchParams()
  const id = positiveId(refundId)
  const memberId = positiveId(params.get('memberId'))

  const load = useCallback(
    async (signal: AbortSignal) => {
      if (id === null) throw new Error('The refund link is not valid.')
      if (memberId !== null) {
        const refunds = await receptionPaymentsApi.memberRefunds(memberId, { signal })
        return refunds.find((refund) => refund.id === id) ?? null
      }
      return findInQueues(id, signal)
    },
    [id, memberId],
  )
  const [refund, reload] = useResource(load)

  const back = (
    <Link className={buttonClassName('secondary', 'md')} to={memberId !== null ? refundCreatePath(memberId) : REFUND_LIST_PATH}>
      Back to refund requests
    </Link>
  )

  if (refund.status === 'loading') {
    return (
      <div className="page">
        <PageHeader eyebrow={EYEBROW} title="Refund" />
        <Spinner label="Loading the refund" />
      </div>
    )
  }
  if (refund.status === 'error') {
    return (
      <div className="page">
        <PageHeader eyebrow={EYEBROW} title="Refund" />
        <ErrorState title="We could not load the refund" error={refund.error} onRetry={reload} />
        <div className="reception-actions">{back}</div>
      </div>
    )
  }
  if (refund.data === null) {
    return (
      <div className="page">
        <PageHeader eyebrow={EYEBROW} title="Refund" />
        <EmptyState
          title="Refund not found"
          description="Open the refund again from the approved queue or the member's refunds."
          action={back}
        />
      </div>
    )
  }

  const data = refund.data
  switch (refundStage(data.status)) {
    case 'submitted':
      return (
        <RefundState
          title="Refund Request Submitted"
          description="The payment remains paid until the refund is completed."
          heading={`${data.refundCode} / Awaiting approval`}
          back={back}
        >
          <Row label="Original payment" value={refundOriginal(data)} />
          <Row label="Requested refund" value={formatMoney(data.amountRequested)} />
          <Row label="Reason" value={data.reason ?? '-'} />
          <Row label="Status" value={refundStatusLabel(data.status)} />
        </RefundState>
      )
    case 'approved':
      return <RefundApproved key={data.id} refund={data} back={back} onCompleted={reload} />
    case 'done':
      return (
        <RefundState
          title="Refund Completed"
          description="The money has been returned and the refund was recorded."
          heading={`Refund receipt / ${data.refundCode}`}
          back={back}
        >
          <Row label="Amount returned" value={formatMoney(refundAmount(data))} />
          <Row label="Refund reference" value={data.refundReference ?? '-'} />
          {data.completedAt && <Row label="Completed at" value={formatDateTime(data.completedAt)} />}
          <Row label="Status" value="Refunded" />
        </RefundState>
      )
    case 'rejected':
      return (
        <RefundState
          title="Refund Rejected"
          description="The manager rejected this refund. The payment stays paid."
          heading={`${data.refundCode} / Rejected`}
          back={back}
        >
          <Row label="Original payment" value={refundOriginal(data)} />
          <Row label="Requested refund" value={formatMoney(data.amountRequested)} />
          <Row label="Manager note" value={data.managerNote ?? '-'} />
        </RefundState>
      )
    default:
      return (
        <RefundState title="Refund" heading={data.refundCode} back={back}>
          <Row label="Status" value={refundStatusLabel(data.status)} />
        </RefundState>
      )
  }
}

/**
 * Without a member, the refund is looked up in the APPROVED queue first (the one the desk acts
 * on), then in the PENDING queue. A failing queue does not hide a refund found in the other one;
 * when neither answers, the first error is shown.
 */
async function findInQueues(id: number, signal: AbortSignal): Promise<Refund | null> {
  const queues = [
    () => receptionPaymentsApi.approvedRefunds({ signal }),
    () => receptionPaymentsApi.pendingRefunds({ signal }),
  ]
  let firstError: unknown = null
  let anyAnswered = false
  for (const queue of queues) {
    try {
      const found = (await queue()).find((refund) => refund.id === id)
      anyAnswered = true
      if (found) return found
    } catch (error) {
      firstError ??= error
    }
  }
  if (!anyAnswered && firstError !== null) throw firstError
  return null
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}

function RefundState({
  title,
  description,
  heading,
  back,
  children,
}: {
  title: string
  description?: string
  heading: string
  back: ReactNode
  children: ReactNode
}) {
  return (
    <div className="page">
      <PageHeader eyebrow={EYEBROW} title={title} description={description} />
      <Card title={heading} aria-label="Refund">
        <dl className="summary-list payment-rows payment-rows--narrow">{children}</dl>
        <div className="reception-actions">{back}</div>
      </Card>
    </div>
  )
}

function RefundApproved({ refund, back, onCompleted }: { refund: Refund; back: ReactNode; onCompleted: () => void }) {
  const toast = useToast()
  const [reference, setReference] = useState('')
  const [touched, setTouched] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const referenceError = validateRefundReference(reference)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setTouched(true)
    if (referenceError || saving) return
    setSaving(true)
    setError(null)
    try {
      await receptionPaymentsApi.completeRefund(refund.id, reference.trim())
      toast.success('Refund recorded', `${refund.refundCode} is completed.`)
      onCompleted()
    } catch (failure) {
      setSaving(false)
      setError(getErrorMessage(failure))
    }
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow={EYEBROW}
        title="Refund Approved"
        description="Return the approved amount and record the transfer evidence."
      />
      <Card title={`${refund.refundCode} / Approved - not yet refunded`} aria-label="Refund">
        <dl className="summary-list payment-rows payment-rows--narrow">
          <Row label="Approved amount" value={formatMoney(refundAmount(refund))} />
          <Row label="Original receipt" value={refundOriginal(refund)} />
          <Row label="Return method" value="Original payment method" />
          {refund.managerNote && <Row label="Manager note" value={refund.managerNote} />}
        </dl>
        <form className="reception-form" aria-label="Record completed refund" noValidate onSubmit={submit}>
          <Input
            label="Refund transaction reference"
            required
            maxLength={REFUND_REFERENCE_MAX}
            value={reference}
            disabled={saving}
            error={touched && referenceError ? referenceError : undefined}
            onChange={(event) => {
              setReference(event.target.value)
              setError(null)
            }}
            onBlur={() => setTouched(true)}
          />
          <p className="notice notice--info">Record the refund only after the money has actually been returned.</p>
          {error && (
            <p className="notice notice--danger" role="alert">
              {error}
            </p>
          )}
          <div className="reception-form__actions">
            <Button type="submit" loading={saving} disabled={referenceError !== null}>
              Record completed refund
            </Button>
            {back}
          </div>
        </form>
      </Card>
    </div>
  )
}
