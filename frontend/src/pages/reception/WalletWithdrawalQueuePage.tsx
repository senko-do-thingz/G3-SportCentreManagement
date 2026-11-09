import { useCallback, useState } from 'react'
import { getErrorMessage } from '../../api/errors'
import { WITHDRAWAL_STATUS_LABELS, receptionWithdrawalApi, type WalletWithdrawal } from '../../api/walletApi'
import { useResource } from '../../hooks/useResource'
import { Badge, Button, Card, EmptyState, ErrorState, Input, Notice, PageHeader, Select, Spinner } from '../../components/ui'
import { formatDateTime } from '../../utils/dateDisplay'
import { formatMoney } from '../../utils/format'

const FILTERS = [
  { value: 'OPEN', label: 'Open (pending and approved)' },
  { value: 'COMPLETED', label: 'Paid out' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ALL', label: 'All recent' },
]

/** MW-06: Receptionist withdrawal review. Approve, then make the transfer, then record it as a separate step. */
export function WalletWithdrawalQueuePage() {
  const [status, setStatus] = useState('OPEN')
  const load = useCallback((signal: AbortSignal) => receptionWithdrawalApi.list(status, signal), [status])
  const [resource, reload] = useResource(load)
  const [selected, setSelected] = useState<number | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const rows = resource.status === 'ready' ? resource.data : []
  return (
    <div className="page">
      <PageHeader eyebrow="Payments" title="Wallet withdrawals" description="Approve or reject member withdrawals. Record the transfer only after you have made it." />
      <Card title="Requests" actions={<Button variant="secondary" size="sm" onClick={reload}>Refresh</Button>}>
        <Select label="Show" value={status} options={FILTERS} onChange={(e) => { setStatus(e.target.value); setSelected(null) }} />
        {message && <Notice tone="success" live="polite">{message}</Notice>}
        {resource.status === 'loading' && <Spinner label="Loading withdrawals" />}
        {resource.status === 'error' && <ErrorState title="We could not load withdrawals" error={resource.error} onRetry={reload} />}
        {resource.status === 'ready' && (rows.length === 0 ? <EmptyState title="No withdrawals" description="Member withdrawal requests appear here." /> : (
          <div className="table-scroll">
            <table className="table">
              <caption>Wallet withdrawal requests</caption>
              <thead><tr><th>Request</th><th>Member</th><th>Amount</th><th>Account</th><th>Created</th><th>Status</th><th>Action</th></tr></thead>
              <tbody>{rows.map((w) => (
                <tr key={w.id}>
                  <td>{w.code} {w.pool === 'SIMULATED' && <Badge tone="warning">Demo</Badge>}</td>
                  <td>{w.memberName}<div className="health-meta">{w.memberCode}</div></td>
                  <td>{formatMoney(w.amount)}</td>
                  <td>{w.accountNumber ? `${w.bankName} ${w.accountNumber}` : 'No bank transfer'}</td>
                  <td>{formatDateTime(w.createdAt)}</td>
                  <td><Badge tone={w.status === 'APPROVED' || w.status === 'PENDING' ? 'warning' : w.status === 'COMPLETED' ? 'success' : 'neutral'}>{WITHDRAWAL_STATUS_LABELS[w.status]}</Badge>
                    {w.payoutReference && <div className="health-meta">ref {w.payoutReference}</div>}</td>
                  <td>{w.status === 'PENDING' || w.status === 'APPROVED'
                    ? <Button size="sm" variant="secondary" onClick={() => { setSelected(w.id); setMessage(null) }}>Process</Button> : '-'}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        ))}
      </Card>
      {selected && <ProcessPanel key={selected} id={selected} onDone={(text) => { setMessage(text); setSelected(null); reload() }} />}
    </div>
  )
}

function ProcessPanel({ id, onDone }: { id: number; onDone: (message: string) => void }) {
  const load = useCallback((signal: AbortSignal) => receptionWithdrawalApi.detail(id, signal), [id])
  const [resource] = useResource(load)
  const [reference, setReference] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  if (resource.status !== 'ready') return resource.status === 'loading' ? <Spinner label="Loading withdrawal" /> : null
  const w: WalletWithdrawal = resource.data
  const demo = w.pool === 'SIMULATED'

  const run = async (action: () => Promise<WalletWithdrawal>, text: (result: WalletWithdrawal) => string) => {
    setBusy(true)
    setError(null)
    try { onDone(text(await action())) } catch (failure) { setError(getErrorMessage(failure)); setBusy(false) }
  }

  return (
    <Card title={`Process ${w.code} - ${w.memberName}`} actions={<Badge tone="warning">{WITHDRAWAL_STATUS_LABELS[w.status]}</Badge>}>
      <dl className="browse-facts">
        <div><dt>Amount</dt><dd>{formatMoney(w.amount)}</dd></div>
        <div><dt>Bank</dt><dd>{w.bankName ?? 'Demo funds, no bank'}</dd></div>
        <div><dt>Account number</dt><dd>{w.accountNumber ?? '-'}</dd></div>
        <div><dt>Account holder</dt><dd>{w.accountHolder ?? '-'}</dd></div>
      </dl>
      <Input label="Note or reason" name="note" value={note} onChange={(e) => setNote(e.target.value)} />
      {w.status === 'APPROVED' && !demo && (
        <Input label="Bank payout reference" name="payoutReference" value={reference} onChange={(e) => setReference(e.target.value)}
          hint="Enter it only after you have made the transfer." />
      )}
      <Notice tone="info">{w.status === 'PENDING'
        ? 'Approving does not transfer money. After approval, make the transfer and record it here.'
        : demo ? 'Demo funds: completing records a demo payout. No real money moves.' : 'Record the transfer only after the bank shows it as sent.'}</Notice>
      {error && <Notice tone="danger" live="assertive">{error}</Notice>}
      <div className="profile-form__actions">
        {w.status === 'PENDING' && <Button loading={busy} onClick={() => run(() => receptionWithdrawalApi.approve(w.id, note), (r) => `${r.code} approved. It is not paid yet.`)}>Approve</Button>}
        {w.status === 'APPROVED' && <Button loading={busy} onClick={() => run(() => receptionWithdrawalApi.complete(w.id, demo ? null : reference.trim(), note),
          (r) => `${r.code} ${r.status === 'SIMULATED_COMPLETED' ? 'marked as demo completed (no real transfer)' : 'recorded as paid out'}.`)}>
          {demo ? 'Complete demo withdrawal' : 'Record transfer as paid'}</Button>}
        <Button variant="secondary" disabled={busy || !note.trim()} onClick={() => run(() => receptionWithdrawalApi.reject(w.id, note), (r) => `${r.code} rejected. Funds returned to the wallet.`)}>Reject</Button>
        {w.status === 'APPROVED' && <Button variant="secondary" disabled={busy || !note.trim()} onClick={() => run(() => receptionWithdrawalApi.fail(w.id, note), (r) => `${r.code} marked as failed. Funds returned to the wallet.`)}>Transfer failed</Button>}
      </div>
    </Card>
  )
}
