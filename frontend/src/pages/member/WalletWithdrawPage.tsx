import { useCallback, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { getErrorMessage } from '../../api/errors'
import {
  WITHDRAWAL_STATUS_LABELS,
  walletApi,
  withdrawalApi,
  type BankOption,
  type PayoutAccount,
  type WalletOverview,
  type WalletPool,
  type WalletWithdrawal,
} from '../../api/walletApi'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  Input,
  Notice,
  Select,
  Spinner,
  buttonClassName,
} from '../../components/ui'
import { formatDateTime } from '../../utils/dateDisplay'
import { formatMoney } from '../../utils/format'
import {
  MemberCommerceCard,
  MemberCommerceHeader,
  MemberCommerceHero,
} from './MemberCommerceComponents'
import { useMemberResource } from './useMemberResource'

interface WithdrawData {
  wallet: WalletOverview
  account: PayoutAccount | null
  banks: BankOption[]
  withdrawals: WalletWithdrawal[]
}

/** MW-06: payout account and withdrawal requests. Approval by reception is not the transfer itself. */
export function WalletWithdrawPage() {
  const load = useCallback(async (signal: AbortSignal): Promise<WithdrawData> => {
    const [wallet, account, banks, withdrawals] = await Promise.all([
      walletApi.overview(signal),
      withdrawalApi.account(signal),
      withdrawalApi.banks(signal),
      withdrawalApi.mine(signal),
    ])
    return { wallet, account, banks: banks.filter((b) => b.transferSupported), withdrawals }
  }, [])
  const [resource, reload] = useMemberResource(load)
  // Kept here so the confirmation survives the reload that follows a new request.
  const [notice, setNotice] = useState<string | null>(null)
  return (
    <div className="page member-commerce">
      <MemberCommerceHeader
        eyebrow="Member Workspace"
        title="Withdraw from"
        highlight="Wallet"
        description="Save your bank account, then request a withdrawal. Reception reviews it and makes the transfer."
        actions={
          <Link className={buttonClassName('secondary')} to="/member/wallet">
            Back to My Wallet
          </Link>
        }
      />
      <MemberCommerceHero
        kicker="SPORTIFY MEMBER WALLET"
        title="SAFE AND RELIABLE WITHDRAWALS."
        highlight="DIRECT TO YOUR BANK."
        description="Available balance is transferred directly to your saved bank account upon front desk review."
      />
      {notice && (
        <Notice tone="success" live="polite">
          {notice}
        </Notice>
      )}
      {resource.status === 'loading' && <Spinner label="Loading withdrawals" />}
      {resource.status === 'error' && (
        <ErrorState title="We could not load your withdrawals" error={resource.error} onRetry={reload} />
      )}
      {resource.status === 'ready' && (
        <WithdrawWorkspace data={resource.data} onChanged={reload} onNotice={setNotice} />
      )}
    </div>
  )
}

function WithdrawWorkspace({
  data,
  onChanged,
  onNotice,
}: {
  data: WithdrawData
  onChanged: () => void
  onNotice: (text: string | null) => void
}) {
  return (
    <div className="member-commerce-grid">
      <div className="member-commerce-column">
        <RequestForm data={data} onChanged={onChanged} onNotice={onNotice} />
        <MemberCommerceCard title="My Withdrawals">
          {data.withdrawals.length === 0 ? (
            <EmptyState
              title="No withdrawals yet"
              description="Your requests and their status appear here."
            />
          ) : (
            <div className="table-scroll">
              <table className="table">
                <caption>Withdrawal requests, newest first</caption>
                <thead>
                  <tr>
                    <th>Request</th>
                    <th>Created</th>
                    <th>Amount</th>
                    <th>To account</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {data.withdrawals.map((w) => (
                    <tr key={w.id}>
                      <td>
                        {w.code} {w.pool === 'SIMULATED' && <Badge tone="warning">Demo</Badge>}
                      </td>
                      <td>{formatDateTime(w.createdAt)}</td>
                      <td>{formatMoney(w.amount)}</td>
                      <td>{w.accountNumber ? `${w.bankName} ${w.accountNumber}` : 'No bank transfer'}</td>
                      <td>
                        <Badge
                          tone={
                            w.status === 'COMPLETED'
                              ? 'success'
                              : w.status === 'PENDING' || w.status === 'APPROVED'
                              ? 'warning'
                              : 'neutral'
                          }
                        >
                          {WITHDRAWAL_STATUS_LABELS[w.status]}
                        </Badge>
                        {w.reviewNote && (w.status === 'REJECTED' || w.status === 'FAILED') && (
                          <div className="health-meta">{w.reviewNote}</div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </MemberCommerceCard>
      </div>
      <div className="member-commerce-column">
        <PayoutAccountCard account={data.account} banks={data.banks} onSaved={onChanged} />
      </div>
    </div>
  )
}

function RequestForm({
  data,
  onChanged,
  onNotice,
}: {
  data: WithdrawData
  onChanged: () => void
  onNotice: (text: string | null) => void
}) {
  const [amount, setAmount] = useState('')
  const [pool, setPool] = useState<WalletPool>('ACTUAL')
  const [last4, setLast4] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null)
  const intent = useRef<{ serialized: string; key: string } | null>(null)
  const available = pool === 'ACTUAL' ? data.wallet.availableBalance : data.wallet.demoAvailableBalance

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    // Synchronous guard against double clicks in the same event tick
    if (busy) return
    const value = Number(amount)
    if (!Number.isInteger(value) || value < 10000) {
      setMessage({ tone: 'danger', text: 'Enter a whole amount of at least 10.000 VND.' })
      return
    }
    if (value > available) {
      setMessage({
        tone: 'danger',
        text: `The amount is more than your available ${pool === 'ACTUAL' ? '' : 'demo '}balance.`,
      })
      return
    }
    const serialized = `${value}|${pool}|${last4}`
    if (intent.current?.serialized !== serialized) intent.current = { serialized, key: crypto.randomUUID() }
    setBusy(true)
    setMessage(null)
    onNotice(null)
    try {
      const created = await withdrawalApi.create(
        value,
        pool,
        pool === 'ACTUAL' ? last4 : null,
        intent.current.key
      )
      intent.current = null
      setAmount('')
      setLast4('')
      onNotice(`${created.code} requested. ${formatMoney(created.amount)} is held until reception reviews it.`)
      onChanged()
    } catch (failure) {
      setMessage({ tone: 'danger', text: getErrorMessage(failure) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <MemberCommerceCard title="New Withdrawal">
      <p className="health-meta" style={{ marginTop: 0, marginBottom: 'var(--space-3)' }}>
        Available: {formatMoney(data.wallet.availableBalance)}. Held for withdrawal:{' '}
        {formatMoney(data.wallet.reservedBalance)}.
        {data.wallet.demoAvailableBalance > 0 &&
          ` Demo funds: ${formatMoney(data.wallet.demoAvailableBalance)} (never paid to a bank).`}
      </p>
      <form onSubmit={submit} aria-label="New withdrawal" noValidate>
        <div className="profile-form__grid">
          <Input
            label="Amount (VND)"
            name="amount"
            inputMode="numeric"
            value={amount}
            onChange={(e) => setAmount(e.target.value.replace(/\D/g, ''))}
          />
          {data.wallet.demoAvailableBalance > 0 && (
            <Select
              label="From"
              name="pool"
              value={pool}
              onChange={(e) => setPool(e.target.value as WalletPool)}
              options={[
                { value: 'ACTUAL', label: 'Real balance (bank transfer)' },
                { value: 'SIMULATED', label: 'Demo funds (no real transfer)' },
              ]}
            />
          )}
          {pool === 'ACTUAL' && (
            <Input
              label="Confirm last 4 digits of your account"
              name="confirmAccountLast4"
              inputMode="numeric"
              maxLength={4}
              value={last4}
              onChange={(e) => setLast4(e.target.value.replace(/\D/g, ''))}
              hint={
                data.account
                  ? `Paying to ${data.account.bankName} ${data.account.maskedAccountNumber}`
                  : 'Save a payout account first'
              }
            />
          )}
        </div>
        {message && (
          <Notice tone={message.tone} live="polite">
            {message.text}
          </Notice>
        )}
        <div className="profile-form__actions" style={{ marginTop: 'var(--space-4)' }}>
          <Button type="submit" loading={busy} disabled={pool === 'ACTUAL' && !data.account}>
            Request withdrawal
          </Button>
        </div>
      </form>
    </MemberCommerceCard>
  )
}

function PayoutAccountCard({
  account,
  banks,
  onSaved,
}: {
  account: PayoutAccount | null
  banks: BankOption[]
  onSaved: () => void
}) {
  const [editing, setEditing] = useState(account === null)
  const [bin, setBin] = useState(account?.bankBin ?? '')
  const [number, setNumber] = useState('')
  const [holder, setHolder] = useState(account?.accountHolder ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const save = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await withdrawalApi.saveAccount(bin, number, holder)
      setNumber('')
      setEditing(false)
      onSaved()
    } catch (failure) {
      setError(getErrorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title="Payout bank account">
      <Notice tone="info">
        We only need the bank, account number and account holder. Sportify never asks for your bank login, PIN or OTP.
      </Notice>
      {account && !editing ? (
        <>
          <dl className="browse-facts" style={{ margin: 'var(--space-4) 0' }}>
            <div>
              <dt>Bank</dt>
              <dd>{account.bankName}</dd>
            </div>
            <div>
              <dt>Account</dt>
              <dd>{account.maskedAccountNumber}</dd>
            </div>
            <div>
              <dt>Account holder</dt>
              <dd>{account.accountHolder}</dd>
            </div>
          </dl>
          <Button variant="secondary" onClick={() => setEditing(true)}>
            Change account
          </Button>
        </>
      ) : (
        <form onSubmit={save} aria-label="Payout bank account">
          <Select
            label="Bank"
            name="bankBin"
            value={bin}
            placeholder="Choose a bank"
            onChange={(e) => setBin(e.target.value)}
            options={banks.map((b) => ({ value: b.bin, label: `${b.name} (${b.code})` }))}
          />
          <Input
            label="Account number"
            name="accountNumber"
            inputMode="numeric"
            value={number}
            onChange={(e) => setNumber(e.target.value.replace(/\D/g, ''))}
          />
          <Input
            label="Account holder (as printed by the bank)"
            name="accountHolder"
            value={holder}
            onChange={(e) => setHolder(e.target.value)}
          />
          {error && (
            <Notice tone="danger" live="assertive">
              {error}
            </Notice>
          )}
          <div className="profile-form__actions" style={{ marginTop: 'var(--space-4)' }}>
            <Button type="submit" loading={busy}>
              Save account
            </Button>
            {account && (
              <Button type="button" variant="secondary" onClick={() => setEditing(false)}>
                Cancel
              </Button>
            )}
          </div>
        </form>
      )}
    </Card>
  )
}
