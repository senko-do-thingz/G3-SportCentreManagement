import { useCallback, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getErrorMessage } from '../../api/errors'
import { walletApi, walletPaymentApi } from '../../api/walletApi'
import { Button, Notice } from '../../components/ui'
import { formatMoney } from '../../utils/format'
import { PAYMENT_PATHS } from './paymentsModel'
import { useMemberResource } from './useMemberResource'

/**
 * MW-05: pay a pending purchase from the Sportify Wallet. The shown balance is only a hint; the server checks it again
 * and either debits, activates and invoices together or changes nothing.
 */
export function WalletPayCard({ paymentId, amount, onStale }: { paymentId: number; amount: number; onStale: () => void }) {
  const load = useCallback((signal: AbortSignal) => walletApi.overview(signal), [])
  const [wallet] = useMemberResource(load)
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  if (wallet.status !== 'ready') return null
  const available = wallet.data.availableBalance
  const enough = available >= amount

  const pay = async () => {
    setBusy(true)
    setError(null)
    try {
      const result = await walletPaymentApi.pay(paymentId)
      navigate(result.invoiceId ? PAYMENT_PATHS.receipt(result.invoiceId) : PAYMENT_PATHS.status(paymentId))
    } catch (failure) {
      setBusy(false)
      setError(getErrorMessage(failure))
    }
  }

  return (
    <section className="wallet-pay" aria-label="Pay with Sportify Wallet">
      <h3 className="payment-methods__legend">Or pay with Sportify Wallet</h3>
      <p className="payment-methods__hint">Available wallet balance: <strong>{formatMoney(available)}</strong>. Demo funds cannot be used.</p>
      {!enough && (
        <Notice tone="warning">Your wallet balance is not enough for this payment. Nothing will be charged. <Link to="/member/wallet/topup">Top up wallet</Link></Notice>
      )}
      {error && (
        <div className="notice notice--danger" role="alert">
          <p>{error}</p>
          <button type="button" className="link-button" onClick={onStale}>Reload the payment</button>
        </div>
      )}
      <Button type="button" variant="secondary" fullWidth loading={busy} disabled={!enough} onClick={pay}>
        Pay {formatMoney(amount)} from wallet
      </Button>
    </section>
  )
}
