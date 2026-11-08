import { useCallback, useState, type FormEvent } from 'react'
import { getErrorMessage } from '../../api/errors'
import { receptionTopUpApi, type WalletTopUp } from '../../api/walletApi'
import { useResource } from '../../hooks/useResource'
import { Badge, Button, Card, EmptyState, ErrorState, Input, Notice, PageHeader, Select, Spinner, TextArea } from '../../components/ui'
import { formatDateTime } from '../../utils/dateDisplay'
import { formatMoney } from '../../utils/format'

const FILTERS = [
  { value: 'PENDING', label: 'Pending review' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ALL', label: 'All recent' },
]

/**
 * MW-04: Receptionists confirm a top-up only after checking the bank statement. The received amount is typed, never
 * prefilled, so a confirmation always reflects what staff actually saw.
 */
export function WalletTopUpQueuePage() {
  const [status, setStatus] = useState('PENDING')
  const load = useCallback((signal: AbortSignal) => receptionTopUpApi.list(status, signal), [status])
  const [resource, reload] = useResource(load)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const rows = resource.status === 'ready' ? resource.data : []
  const selected = rows.find((t) => t.id === selectedId) ?? null

  return (
    <div className="page">
      <PageHeader eyebrow="Payments" title="Wallet top-ups" description="Check each transfer against the bank statement, then confirm or reject it." />
      <Card title="Requests" actions={<Button variant="secondary" size="sm" onClick={reload}>Refresh</Button>}>
        <Select label="Show" value={status} options={FILTERS} onChange={(e) => { setStatus(e.target.value); setSelectedId(null) }} />
        {message && <Notice tone="success" live="polite">{message}</Notice>}
        {resource.status === 'loading' && <Spinner label="Loading top-ups" />}
        {resource.status === 'error' && <ErrorState title="We could not load top-ups" error={resource.error} onRetry={reload} />}
        {resource.status === 'ready' && (rows.length === 0 ? <EmptyState title="No top-up requests" description="New member top-ups appear here." /> : (
          <div className="table-scroll">
            <table className="table">
              <caption>Wallet top-up requests</caption>
              <thead><tr><th>Request</th><th>Member</th><th>Amount</th><th>Transfer note</th><th>Created</th><th>Status</th><th>Action</th></tr></thead>
              <tbody>
                {rows.map((t) => (
                  <tr key={t.id} aria-selected={t.id === selectedId}>
                    <td>{t.code} {t.mode === 'SIMULATED' && <Badge tone="warning">Demo</Badge>}</td>
                    <td>{t.memberName}<div className="health-meta">{t.memberCode}</div></td>
                    <td>{formatMoney(t.amount)}</td>
                    <td><code>{t.transferReference}</code></td>
                    <td>{formatDateTime(t.createdAt)}</td>
                    <td><Badge tone={t.status === 'PENDING' ? 'warning' : t.status === 'CONFIRMED' ? 'success' : 'neutral'}>{t.status}</Badge>
                      {t.reviewedByName && <div className="health-meta">by {t.reviewedByName}{t.bankReference ? `, ref ${t.bankReference}` : ''}</div>}</td>
                    <td>{t.status === 'PENDING' ? <Button size="sm" variant="secondary" onClick={() => { setSelectedId(t.id); setMessage(null) }}>Review</Button> : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
      </Card>
      {selected && selected.status === 'PENDING' && (
        <ReviewPanel key={selected.id} topUp={selected} onDone={(text) => { setMessage(text); setSelectedId(null); reload() }} />
      )}
    </div>
  )
}

function ReviewPanel({ topUp, onDone }: { topUp: WalletTopUp; onDone: (message: string) => void }) {
  const demo = topUp.mode === 'SIMULATED'
  const [received, setReceived] = useState('')
  const [reference, setReference] = useState('')
  const [note, setNote] = useState('')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const confirm = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const done = await receptionTopUpApi.confirm(topUp.id, Number(received), demo ? null : reference.trim(), note)
      onDone(`${done.code} confirmed. ${formatMoney(done.amount)} ${demo ? 'demo funds were' : 'was'} added to the member's wallet.`)
    } catch (failure) {
      setError(getErrorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  const reject = async () => {
    setBusy(true)
    setError(null)
    try {
      const done = await receptionTopUpApi.reject(topUp.id, reason)
      onDone(`${done.code} rejected. The wallet was not changed.`)
    } catch (failure) {
      setError(getErrorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title={`Review ${topUp.code} - ${topUp.memberName}`}>
      <dl className="browse-facts">
        <div><dt>Requested amount</dt><dd>{formatMoney(topUp.amount)}</dd></div>
        <div><dt>Expected transfer note</dt><dd><code>{topUp.transferReference}</code></dd></div>
        <div><dt>Type</dt><dd>{demo ? 'Demo top-up (not real money)' : 'Bank transfer'}</dd></div>
      </dl>
      <Notice tone={demo ? 'warning' : 'info'}>
        {demo ? 'Demo top-up: no bank check is needed. It credits demo funds that cannot be spent on real services or withdrawn.'
          : 'Confirm only after you see this exact amount with this transfer note on the bank statement.'}
      </Notice>
      <form onSubmit={confirm} aria-label="Confirm top-up">
        <div className="profile-form__grid">
          <Input label="Amount received (VND)" name="receivedAmount" inputMode="numeric" value={received} onChange={(e) => setReceived(e.target.value.replace(/\D/g, ''))} required />
          {!demo && <Input label="Bank transaction reference" name="bankReference" value={reference} onChange={(e) => setReference(e.target.value)} required />}
        </div>
        <TextArea label="Note (optional)" name="note" value={note} onChange={(e) => setNote(e.target.value)} rows={2} />
        {error && <Notice tone="danger" live="assertive">{error}</Notice>}
        <div className="profile-form__actions">
          <Button type="submit" loading={busy}>Confirm and credit wallet</Button>
        </div>
      </form>
      <div className="topup-reject">
        <Input label="Reason to reject" name="rejectReason" value={reason} onChange={(e) => setReason(e.target.value)} />
        <Button variant="secondary" disabled={busy || !reason.trim()} onClick={reject}>Reject request</Button>
      </div>
    </Card>
  )
}
