import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import QRCode from 'qrcode'
import { Link } from 'react-router-dom'
import { getErrorMessage } from '../../api/errors'
import { topUpApi, type MemberTopUps, type WalletPool, type WalletTopUp } from '../../api/walletApi'
import { Badge, Button, Card, EmptyState, ErrorState, Input, Notice, PageHeader, Select, Spinner, buttonClassName } from '../../components/ui'
import { formatDateTime } from '../../utils/dateDisplay'
import { formatMoney } from '../../utils/format'
import { useMemberResource } from './useMemberResource'

/** MW-04: request a wallet top-up. The request stays PENDING until reception confirms the money arrived. */
export function WalletTopUpPage() {
  const load = useCallback((signal: AbortSignal) => topUpApi.mine(signal), [])
  const [resource, reload] = useMemberResource(load)
  return (
    <div className="page">
      <PageHeader
        eyebrow="My Wallet"
        title="Top up wallet"
        description="Transfer money with the QR code. Your wallet is credited only after reception checks the transfer."
        actions={<Link className={buttonClassName('secondary')} to="/member/wallet">Back to My Wallet</Link>}
      />
      {resource.status === 'loading' && <Spinner label="Loading your top-ups" />}
      {resource.status === 'error' && <ErrorState title="We could not load your top-ups" error={resource.error} onRetry={reload} />}
      {resource.status === 'ready' && <TopUpWorkspace data={resource.data} onChanged={reload} />}
    </div>
  )
}

function TopUpWorkspace({ data, onChanged }: { data: MemberTopUps; onChanged: () => void }) {
  const [amount, setAmount] = useState('')
  const [mode, setMode] = useState<WalletPool>('ACTUAL')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [created, setCreated] = useState<WalletTopUp | null>(null)
  const intent = useRef<{ serialized: string; key: string } | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const value = Number(amount)
    if (!Number.isInteger(value) || value < data.minAmount || value > data.maxAmount) {
      setError(`Enter a whole amount from ${formatMoney(data.minAmount)} to ${formatMoney(data.maxAmount)}.`)
      return
    }
    // One key per intended request, so a retry after a network error never creates a second top-up.
    const serialized = `${value}|${mode}`
    if (intent.current?.serialized !== serialized) intent.current = { serialized, key: crypto.randomUUID() }
    setBusy(true)
    setError(null)
    try {
      const topUp = await topUpApi.create(value, mode, intent.current.key)
      intent.current = null
      setCreated(topUp)
      setAmount('')
      onChanged()
    } catch (failure) {
      setError(getErrorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  const pending = created && created.status === 'PENDING' ? created : data.topUps.find((t) => t.status === 'PENDING') ?? null

  return (
    <div className="health-layout">
      <div className="health-side">
        <Card title="New top-up">
          <form onSubmit={submit} aria-label="New top-up" noValidate>
            <Input label="Amount (VND)" name="amount" inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value.replace(/\D/g, ''))}
              hint={`From ${formatMoney(data.minAmount)} to ${formatMoney(data.maxAmount)}`} />
            {data.simulatedTopUpsEnabled && (
              <Select label="Top-up type" name="mode" value={mode} onChange={(e) => setMode(e.target.value as WalletPool)}
                options={[{ value: 'ACTUAL', label: 'Bank transfer (real money)' }, { value: 'SIMULATED', label: 'Demo top-up (not real money)' }]} />
            )}
            {error && <Notice tone="danger" live="assertive">{error}</Notice>}
            <div className="profile-form__actions"><Button type="submit" loading={busy}>Create top-up request</Button></div>
          </form>
        </Card>
        <Card title="My top-up requests">
          {data.topUps.length === 0 ? <EmptyState title="No top-up requests yet" description="Your requests and their review status appear here." />
            : <TopUpTable topUps={data.topUps} onChanged={onChanged} />}
        </Card>
      </div>
      <div className="health-side">
        {pending ? <PendingInstructions topUp={pending} /> : (
          <Card title="Transfer instructions"><p className="health-note">Create a top-up request to see its transfer details.</p></Card>
        )}
      </div>
    </div>
  )
}

function PendingInstructions({ topUp }: { topUp: WalletTopUp }) {
  const [image, setImage] = useState<string | null>(null)
  const instructions = topUp.instructions
  useEffect(() => {
    let active = true
    if (instructions?.qrPayload) {
      QRCode.toDataURL(instructions.qrPayload, { width: 280, margin: 4, errorCorrectionLevel: 'M' }).then((url) => { if (active) setImage(url) })
    }
    return () => { active = false }
  }, [instructions?.qrPayload])
  if (!instructions) return null
  return (
    <Card title={`Top-up ${topUp.code}`} actions={<Badge tone="warning">Pending review</Badge>}>
      <Notice tone={instructions.payable ? 'info' : 'warning'}>{instructions.notice}</Notice>
      {instructions.payable && image && <img className="topup-qr" src={image} alt={`VietQR code for top-up ${topUp.code}`} />}
      <dl className="browse-facts">
        <div><dt>Amount</dt><dd>{formatMoney(instructions.amount)}</dd></div>
        <div><dt>Transfer note</dt><dd><code>{instructions.transferReference}</code></dd></div>
        {instructions.payable && <>
          <div><dt>Bank</dt><dd>{instructions.bankName}</dd></div>
          <div><dt>Account</dt><dd>{instructions.accountNumber}</dd></div>
          <div><dt>Account name</dt><dd>{instructions.accountName}</dd></div>
        </>}
        <div><dt>Type</dt><dd>{topUp.mode === 'SIMULATED' ? 'Demo (not real money)' : 'Bank transfer'}</dd></div>
      </dl>
      <p className="health-meta">Showing this code does not credit your wallet. Reception confirms the transfer first.</p>
    </Card>
  )
}

const STATUS_TONE = { PENDING: 'warning', CONFIRMED: 'success', REJECTED: 'danger', CANCELLED: 'neutral' } as const

function TopUpTable({ topUps, onChanged }: { topUps: WalletTopUp[]; onChanged: () => void }) {
  const [error, setError] = useState<string | null>(null)
  const cancel = async (id: number) => {
    setError(null)
    try { await topUpApi.cancel(id); onChanged() } catch (failure) { setError(getErrorMessage(failure)) }
  }
  return (
    <>
      {error && <Notice tone="danger">{error}</Notice>}
      <div className="table-scroll">
        <table className="table">
          <caption>Top-up requests, newest first</caption>
          <thead><tr><th>Request</th><th>Created</th><th>Amount</th><th>Status</th><th>Action</th></tr></thead>
          <tbody>
            {topUps.map((t) => (
              <tr key={t.id}>
                <td>{t.code} {t.mode === 'SIMULATED' && <Badge tone="warning">Demo</Badge>}</td>
                <td>{formatDateTime(t.createdAt)}</td>
                <td>{formatMoney(t.amount)}</td>
                <td><Badge tone={STATUS_TONE[t.status]}>{t.status === 'PENDING' ? 'Pending review' : t.status.charAt(0) + t.status.slice(1).toLowerCase()}</Badge>
                  {t.reviewNote && t.status === 'REJECTED' && <div className="health-meta">{t.reviewNote}</div>}</td>
                <td>{t.status === 'PENDING' ? <Button variant="secondary" size="sm" onClick={() => cancel(t.id)}>Cancel</Button> : '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
