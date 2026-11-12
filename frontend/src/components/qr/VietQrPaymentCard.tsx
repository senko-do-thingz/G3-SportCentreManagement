import { useEffect, useState, type ReactNode } from 'react'
import QRCode from 'qrcode'
import { getErrorMessage } from '../../api/errors'
import { paymentsApi, type Payment, type VietQrInstructions } from '../../api/paymentsApi'
import { localDemoPaymentApi } from '../../api/localDemoPaymentApi'
import { copyText } from '../../utils/clipboard'
import { formatPaymentMoney } from '../../pages/member/paymentsModel'
import { Button, Card, useToast } from '../ui'
import { Spinner } from '../ui/Spinner'

type InstructionsState =
  | { status: 'loading' }
  | { status: 'ready'; instructions: VietQrInstructions }
  | { status: 'error'; error: string }

export function VietQrPaymentCard({
  payment,
  onRefresh,
  fallback,
}: {
  payment: Payment
  onRefresh: () => void
  fallback: ReactNode
}) {
  const [state, setState] = useState<InstructionsState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [qrImage, setQrImage] = useState<string | null>(null)
  const [brandedImageFailed, setBrandedImageFailed] = useState(false)
  const [demoAvailable, setDemoAvailable] = useState(false)
  const [submittingDemo, setSubmittingDemo] = useState(false)
  const [demoMessage, setDemoMessage] = useState<string | null>(null)
  const [demoError, setDemoError] = useState<string | null>(null)
  const toast = useToast()

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    setState({ status: 'loading' })
    paymentsApi.vietQrInstructions(payment.id, { signal: controller.signal }).then(
      (instructions) => {
        if (active) setState({ status: 'ready', instructions })
      },
    ).catch((error: unknown) => {
      if (active && !controller.signal.aborted) setState({ status: 'error', error: getErrorMessage(error) })
    })
    void localDemoPaymentApi.capability(controller.signal).then((enabled) => {
      if (active) setDemoAvailable(enabled)
    })
    return () => {
      active = false
      controller.abort()
    }
  }, [payment.id, attempt])

  const instructions = state.status === 'ready' ? state.instructions : null
  const brandedImage = instructions?.payable ? quickLinkImage(instructions) : null
  useEffect(() => {
    let active = true
    setQrImage(null)
    setBrandedImageFailed(false)
    if (!instructions?.payable || !instructions.qrPayload) return () => { active = false }
    QRCode.toDataURL(instructions.qrPayload, { width: 300, margin: 2, errorCorrectionLevel: 'M' }).then((url) => {
      if (active) setQrImage(url)
    }).catch(() => {
      if (active) setQrImage(null)
    })
    return () => { active = false }
  }, [instructions?.payable, instructions?.qrPayload])

  async function copyReference() {
    if (!instructions?.transferReference) return
    try {
      await copyText(instructions.transferReference)
      toast.success('Copied transfer content', instructions.transferReference)
    } catch {
      toast.error('Could not copy transfer content', 'Select the text and copy it by hand.')
    }
  }

  async function submitLocalDemo() {
    if (submittingDemo) return
    setSubmittingDemo(true)
    setDemoMessage(null)
    setDemoError(null)
    try {
      await localDemoPaymentApi.confirm(payment.id)
      setDemoMessage('Local demo marked this payment as paid. No bank transfer was checked and no money moved.')
      onRefresh()
    } catch (error) {
      setDemoError(getErrorMessage(error))
    } finally {
      setSubmittingDemo(false)
    }
  }

  if (state.status === 'loading') {
    return <Card title="VietQR transfer" aria-label="VietQR transfer"><Spinner label="Preparing your VietQR transfer instructions" /></Card>
  }

  if (state.status === 'error') {
    return (
      <Card title="VietQR transfer unavailable" aria-label="VietQR transfer unavailable">
        <p className="notice notice--warning" role="alert">{state.error}</p>
        <p>We could not load the transfer QR. Try again or ask reception for payment options.</p>
        <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>Try again</Button>
        {payment.bankTransferInstructions?.payable && <div style={{ marginTop: 'var(--space-4)' }}>{fallback}</div>}
      </Card>
    )
  }

  if (!instructions) {
    return <Card title="VietQR transfer" aria-label="VietQR transfer"><Spinner label="Preparing your VietQR transfer instructions" /></Card>
  }

  if (!instructions.payable || !instructions.qrPayload) {
    return (
      <Card title="VietQR transfer QR unavailable" aria-label="VietQR transfer QR unavailable">
        <p className="notice notice--warning">{instructions.notice}</p>
        <p>Configure the authorized receiving account and its six-digit bank BIN in <code>backend/.env</code>. Refresh the bank list with <code>scripts/payment/Refresh-VietQrBanks.ps1</code>, then restart the backend.</p>
        <p>VietQR Paygate credentials are not needed for this transfer QR.</p>
        {payment.bankTransferInstructions?.payable && <div style={{ marginTop: 'var(--space-4)' }}>{fallback}</div>}
      </Card>
    )
  }

  return (
    <Card title="VietQR bank transfer" aria-label="VietQR bank transfer">
      <p>{instructions.notice}</p>
      <div className="vietqr-payment-card__qr-wrap">
        {(brandedImage && !brandedImageFailed) || qrImage ? (
          <div className="vietqr-payment-card__qr-frame">
            <img
              src={brandedImage && !brandedImageFailed ? brandedImage : qrImage ?? undefined}
              alt={`VietQR transfer code for ${payment.code}`}
              onError={() => { if (brandedImage && !brandedImageFailed) setBrandedImageFailed(true) }}
            />
          </div>
        ) : <Spinner label="Rendering your VietQR code" />}
      </div>
      {demoAvailable && (
        <div className="vietqr-payment-card__demo no-print">
          <Button variant="primary" fullWidth loading={submittingDemo} onClick={() => void submitLocalDemo()}>
            Complete payment (local demo)
          </Button>
          <p className="payment-methods__hint">
            Completes this payment in the localhost demo without receptionist confirmation. No transfer is verified and no money moves.
          </p>
          {demoMessage && <p role="status" className="notice notice--info">{demoMessage}</p>}
          {demoError && <p role="alert" className="notice notice--danger">{demoError}</p>}
        </div>
      )}
      <dl className="summary-list">
        <div><dt>Amount</dt><dd>{formatPaymentMoney(instructions.amount)}</dd></div>
        <div><dt>Bank</dt><dd>{instructions.bankName ?? instructions.bankBin}</dd></div>
        <div><dt>Account number</dt><dd>{instructions.accountNumber}</dd></div>
        <div><dt>Account name</dt><dd>{instructions.accountName}</dd></div>
        <div>
          <dt>Transfer content</dt>
          <dd className="copy-value">
            <code className="copy-value__text">{instructions.transferReference}</code>
            <button type="button" className="copy-value__button no-print" onClick={() => void copyReference()}>Copy</button>
          </dd>
        </div>
      </dl>
      <p className="notice notice--info">
        The QR contains a bank transfer instruction. Reception must verify the received funds before this payment is confirmed.
      </p>
    </Card>
  )
}

function quickLinkImage(instructions: VietQrInstructions): string | null {
  if (!instructions.bankBin || !instructions.accountNumber || !instructions.accountName
      || !instructions.transferReference) return null
  const amount = Number(instructions.amount)
  if (!Number.isSafeInteger(amount) || amount <= 0) return null
  const bank = encodeURIComponent(instructions.bankBin)
  const account = encodeURIComponent(instructions.accountNumber)
  const imageUrl = new URL(`https://img.vietqr.io/image/${bank}-${account}-compact2.svg`)
  imageUrl.searchParams.set('amount', String(amount))
  imageUrl.searchParams.set('addInfo', instructions.transferReference)
  imageUrl.searchParams.set('accountName', instructions.accountName)
  return imageUrl.toString()
}
