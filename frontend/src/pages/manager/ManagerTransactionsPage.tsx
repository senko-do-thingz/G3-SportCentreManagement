import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../../api/errors'
import { managerFinanceApi, type FinanceTransaction, type ReviewRefundRequest } from '../../api/managerFinanceApi'
import type { Refund } from '../../api/receptionPaymentsApi'
import { tokenStorage } from '../../api/tokenStorage'
import { Button, Card, Input, Modal, PageHeader, Select, StatusBadge, Table, TextArea, type TableColumn } from '../../components/ui'
import { useResource, useDebounced } from './useManagerTools'
import { ManagerError, PreviewNotice } from './ManagerShared'
import { formatDateTime, formatMoney, humanizeCode } from '../../utils/format'
import { useFocusFirstInvalid } from '../../utils/useFocusFirstInvalid'
import '../../styles/managerFinance.css'

const PAGE_SIZE = 20
const TRANSACTION_STATUSES = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'SUCCESS', label: 'Successful' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'REFUNDED', label: 'Refunded' },
]
const PAYMENT_METHODS = ['CASH', 'CREDIT_CARD', 'BANK_TRANSFER', 'MOMO', 'VNPAY', 'ZALOPAY'].map((value) => ({ value, label: humanizeCode(value) }))

function currentMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function validMonth(value: string | null): string {
  return value && /^\d{4}-(0[1-9]|1[0-2])$/.test(value) ? value : currentMonth()
}

function validPage(value: string | null): number {
  const page = Number(value)
  return Number.isSafeInteger(page) && page >= 0 ? page : 0
}

function refundAmount(refund: Refund): number | null {
  const value = refund.amountApproved ?? refund.amountRequested
  if (value === null || value === undefined || value === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null
}

const TRANSACTION_COLUMNS: TableColumn<FinanceTransaction>[] = [
  { key: 'reference', header: 'Transaction', render: (row) => <div className="manager-cell"><strong>{row.invoiceNumber ?? row.code}</strong><span>{row.referenceCode ?? row.code}</span></div> },
  { key: 'member', header: 'Member', render: (row) => <div className="manager-cell"><span className="manager-cell-primary">{row.memberName ?? 'Member not provided'}</span><span>{row.memberCode ?? '-'}</span></div> },
  { key: 'service', header: 'Service', render: (row) => <div className="manager-cell"><span className="manager-cell-primary">{row.itemName ?? row.itemCode ?? row.sportName ?? 'Service not provided'}</span><span>{humanizeCode(row.itemType)}</span></div> },
  { key: 'amount', header: 'Amount', align: 'right', render: (row) => <strong className="manager-nowrap">{formatMoney(row.amount)}</strong> },
  { key: 'method', header: 'Method', render: (row) => humanizeCode(row.method) },
  { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} label={humanizeCode(row.status)} /> },
  { key: 'time', header: 'Date', render: (row) => formatDateTime(row.time) },
]

const REFUND_COLUMNS: TableColumn<Refund>[] = [
  { key: 'refundCode', header: 'Refund request', render: (row) => <div className="manager-cell"><strong>{row.refundCode}</strong><span>{row.registrationCode ?? '-'}</span></div> },
  { key: 'member', header: 'Member', render: (row) => <div className="manager-cell"><span className="manager-cell-primary">{row.memberFullName ?? 'Member not provided'}</span><span>{row.memberCode ?? '-'}</span></div> },
  { key: 'reason', header: 'Reason', render: (row) => row.reason || 'No reason provided' },
  { key: 'amount', header: 'Requested', align: 'right', render: (row) => <strong className="manager-nowrap">{formatMoney(refundAmount(row))}</strong> },
  { key: 'createdAt', header: 'Requested', render: (row) => formatDateTime(row.createdAt) },
  { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} label={humanizeCode(row.status)} /> },
]

export function ManagerTransactionsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const month = validMonth(searchParams.get('month'))
  const search = searchParams.get('query') ?? ''
  const debouncedSearch = useDebounced(search, 300)
  const status = searchParams.get('status') ?? ''
  const method = searchParams.get('method') ?? ''
  const page = validPage(searchParams.get('page'))
  const [selectedRefund, setSelectedRefund] = useState<Refund | null>(null)
  const [decisionNotice, setDecisionNotice] = useState<{ tone: 'success' | 'rejected' | 'warning'; message: string } | null>(null)
  const mounted = useRef(false)
  const decisionVersion = useRef(0)
  const reconciliation = useRef<AbortController | null>(null)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      reconciliation.current?.abort()
    }
  }, [])

  const openRefund = (refund: Refund) => {
    decisionVersion.current += 1
    reconciliation.current?.abort()
    setSelectedRefund(refund)
  }

  const transactionsLoader = useCallback((signal: AbortSignal) => managerFinanceApi.transactions({
    month,
    query: debouncedSearch.trim(),
    status,
    method,
    page,
    size: PAGE_SIZE,
    signal,
  }), [month, debouncedSearch, status, method, page])
  const [transactions, reloadTransactions] = useResource(transactionsLoader)
  const refundsLoader = useCallback((signal: AbortSignal) => managerFinanceApi.pendingRefunds({ signal }), [])
  const [refunds, reloadRefunds] = useResource(refundsLoader)

  const updateFilter = (key: string, value: string) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (value) next.set(key, value)
      else next.delete(key)
      next.delete('page')
      return next
    }, { replace: true })
  }

  const updatePage = (nextPage: number) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (nextPage > 0) next.set('page', String(nextPage))
      else next.delete('page')
      return next
    }, { replace: true })
  }

  const clearFilters = () => setSearchParams((current) => {
    const next = new URLSearchParams(current)
    next.delete('query')
    next.delete('status')
    next.delete('method')
    next.delete('page')
    return next
  }, { replace: true })

  const handleReviewed = (refund: Refund) => {
    const approved = refund.status === 'APPROVED'
    const message = approved
      ? `${refund.refundCode} was approved for ${formatMoney(refund.amountApproved ?? refund.amountRequested)}. It is awaiting payout.`
      : `${refund.refundCode} was rejected. ${refund.managerNote ? `Manager note: ${refund.managerNote}` : ''}`.trim()
    setDecisionNotice({ tone: approved ? 'success' : 'rejected', message })
    setSelectedRefund(null)
    reloadRefunds()
    reloadTransactions()
  }

  const handleConflict = (refund: Refund) => {
    setSelectedRefund(null)
    setDecisionNotice({ tone: 'warning', message: `${refund.refundCode} may have been reviewed by another manager. The pending queue has been refreshed; verify the latest status before taking further action.` })
    reloadRefunds()
  }

  const handleUncertain = async (refund: Refund) => {
    const epoch = tokenStorage.getEpoch()
    const version = ++decisionVersion.current
    reconciliation.current?.abort()
    const controller = new AbortController()
    reconciliation.current = controller
    const isCurrent = () => mounted.current && !controller.signal.aborted && tokenStorage.getEpoch() === epoch && version === decisionVersion.current
    setSelectedRefund(null)
    setDecisionNotice({ tone: 'warning', message: `The decision for ${refund.refundCode} was not confirmed. Do not submit it again until its current status is verified.` })
    reloadRefunds()
    try {
      const latest = await managerFinanceApi.pendingRefunds({ signal: controller.signal })
      if (!isCurrent()) return
      const stillPending = latest.some((row) => row.id === refund.id && row.status === 'PENDING')
      setDecisionNotice({
        tone: 'warning',
        message: stillPending
          ? `The decision for ${refund.refundCode} was not confirmed. It still appears in the pending queue; verify the request before trying again.`
          : `${refund.refundCode} is no longer in the pending queue, but its decision could not be confirmed. Check the latest refund status before taking further action.`,
      })
    } catch {
      if (!isCurrent()) return
      setDecisionNotice({ tone: 'warning', message: `The decision for ${refund.refundCode} could not be confirmed and the pending queue could not be refreshed. Do not submit it again until its current status is verified.` })
    }
  }

  return <div className="page manager-page manager-finance-page">
    <PageHeader eyebrow="Manager / Payments & Reports" title="Transactions and Refunds" description="Review collected payments and decide pending refund requests." />
    <PreviewNotice />

    <Card title="Transaction ledger">
      <div className="manager-filters">
        <Input label="Search transactions" placeholder="Invoice, member or payment code" value={search} onChange={(event) => updateFilter('query', event.target.value)} />
        <Input label="Reporting month" type="month" value={month} onChange={(event) => updateFilter('month', event.target.value)} />
        <Select label="Payment status" placeholder="All statuses" value={status} options={TRANSACTION_STATUSES} onChange={(event) => updateFilter('status', event.target.value)} />
        <Select label="Payment method" placeholder="All methods" value={method} options={PAYMENT_METHODS} onChange={(event) => updateFilter('method', event.target.value)} />
        <Button variant="ghost" onClick={clearFilters}>Clear search, status and method</Button>
      </div>
      {transactions.status === 'error' ? <ManagerError error={transactions.error} onRetry={reloadTransactions} /> : <Table
        caption="Payment transactions"
        columns={TRANSACTION_COLUMNS}
        rows={transactions.status === 'ready' ? transactions.data.content : []}
        rowKey={(row) => row.id}
        loading={transactions.status === 'loading'}
        emptyTitle="No transactions found"
        emptyDescription="Change the reporting month or clear the search, status and method filters."
        pagination={transactions.status === 'ready' ? { page: transactions.data.page, pageSize: transactions.data.size, totalItems: transactions.data.totalElements, onPageChange: updatePage } : undefined}
      />}
    </Card>

    <Card title="Refund approval queue" id="refund-queue">
      {decisionNotice && <div className={`finance-review-result finance-review-result--${decisionNotice.tone}`} role="status">
        <p>{decisionNotice.message}</p>
        <Button size="sm" variant="ghost" onClick={() => setDecisionNotice(null)}>Dismiss</Button>
      </div>}
      {refunds.status === 'error' ? <ManagerError error={refunds.error} onRetry={reloadRefunds} /> : <Table
        caption="Pending refund requests"
        columns={[...REFUND_COLUMNS, { key: 'actions', header: 'Action', render: (row) => row.status === 'PENDING' ? <Button size="sm" variant="secondary" onClick={() => openRefund(row)} aria-label={`Review refund ${row.refundCode}`}>Review</Button> : <span>Already reviewed</span> }]}
        rows={refunds.status === 'ready' ? refunds.data : []}
        rowKey={(row) => row.id}
        loading={refunds.status === 'loading'}
        emptyTitle="No refunds waiting for approval"
        emptyDescription="New refund requests will appear here."
      />}
    </Card>

    {selectedRefund && <RefundReviewModal
      key={selectedRefund.id}
      refund={selectedRefund}
      onClose={() => setSelectedRefund(null)}
      onReviewed={handleReviewed}
      onConflict={handleConflict}
      onUncertain={handleUncertain}
    />}
  </div>
}

function RefundReviewModal({
  refund,
  onClose,
  onReviewed,
  onConflict,
  onUncertain,
}: {
  refund: Refund
  onClose: () => void
  onReviewed: (refund: Refund) => void
  onConflict: (refund: Refund) => void
  onUncertain: (refund: Refund) => void
}) {
  const formRef = useRef<HTMLFormElement>(null)
  const focusInvalid = useFocusFirstInvalid(formRef)
  const sending = useRef(false)
  const mounted = useRef(false)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])
  const [decision, setDecision] = useState<'APPROVE' | 'REJECT'>('APPROVE')
  const [approvedAmount, setApprovedAmount] = useState(refund.amountRequested === null ? '' : String(refund.amountRequested))
  const [managerNote, setManagerNote] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (sending.current) return
    const nextErrors: Record<string, string> = {}
    if (!managerNote.trim()) nextErrors.managerNote = 'Enter a manager note for this decision.'
    let amountValue: number | null = null
    if (decision === 'APPROVE') {
      const parsed = Number(approvedAmount)
      if (!/^(?:\d+|\d*\.\d{1,2})$/.test(approvedAmount.trim()) || !Number.isFinite(parsed) || parsed <= 0) {
        nextErrors.amountApproved = 'Enter a positive amount with at most two decimal places.'
      } else amountValue = parsed
    }
    setFieldErrors(nextErrors)
    setError('')
    if (Object.keys(nextErrors).length) {
      focusInvalid()
      return
    }

    const request: ReviewRefundRequest = {
      approved: decision === 'APPROVE',
      amountApproved: amountValue,
      managerNote: managerNote.trim(),
    }
    sending.current = true
    setSaving(true)
    const epoch = tokenStorage.getEpoch()
    const isCurrent = () => mounted.current && tokenStorage.getEpoch() === epoch
    try {
      const updated = await managerFinanceApi.reviewRefund(refund.id, request)
      if (!isCurrent()) {
        if (mounted.current) onClose()
        return
      }
      onReviewed(updated)
    } catch (cause) {
      if (!isCurrent()) {
        if (mounted.current) onClose()
        return
      }
      if (cause instanceof ApiError && cause.status === 409) {
        onConflict(refund)
      } else if (!(cause instanceof ApiError) || cause.status === 0 || cause.status === 408 || cause.status >= 500) {
        onUncertain(refund)
      } else {
        setError(getErrorMessage(cause))
        if (cause instanceof ApiError) {
          setFieldErrors((current) => ({ ...current, ...cause.fieldErrors }))
        }
        focusInvalid()
      }
    } finally {
      sending.current = false
      if (mounted.current) setSaving(false)
    }
  }

  return <Modal open title={`Review refund ${refund.refundCode}`} description="Review the request and record a decision. Approved refunds are awaiting payout; approval does not mark the money as returned." onClose={() => !saving && onClose()} dismissible={!saving}>
    <form ref={formRef} className="finance-review-form" onSubmit={(event) => void submit(event)} noValidate>
      <dl className="finance-review-summary">
        <div><dt>Member</dt><dd>{refund.memberFullName ?? 'Member not provided'}{refund.memberCode ? ` (${refund.memberCode})` : ''}</dd></div>
        <div><dt>Registration</dt><dd>{refund.registrationCode ?? '-'}</dd></div>
        <div><dt>Requested amount</dt><dd>{formatMoney(refund.amountRequested)}</dd></div>
        <div><dt>Reason</dt><dd>{refund.reason || 'No reason provided'}</dd></div>
      </dl>
      {error && <p className="notice notice--danger" role="alert">{error}</p>}
      <Select label="Decision" value={decision} options={[{ value: 'APPROVE', label: 'Approve refund' }, { value: 'REJECT', label: 'Reject refund' }]} onChange={(event) => { setDecision(event.target.value as 'APPROVE' | 'REJECT'); setFieldErrors({}); setError('') }} disabled={saving} />
      {decision === 'APPROVE' && <Input
        label="Approved amount"
        type="number"
        min="0.01"
        step="0.01"
        value={approvedAmount}
        onChange={(event) => setApprovedAmount(event.target.value)}
        error={fieldErrors.amountApproved}
        hint="The approved amount must not exceed the original paid amount."
        disabled={saving}
        required
      />}
      <TextArea label="Manager note" value={managerNote} onChange={(event) => setManagerNote(event.target.value)} error={fieldErrors.managerNote} hint="The note will be included in the refund decision record." maxLength={500} disabled={saving} required />
      <div className="manager-form-actions">
        <Button type="submit" variant={decision === 'APPROVE' ? 'primary' : 'danger'} loading={saving}>{decision === 'APPROVE' ? 'Approve refund' : 'Reject refund'}</Button>
        <Button variant="secondary" disabled={saving} onClick={onClose}>Cancel</Button>
      </div>
    </form>
  </Modal>
}
