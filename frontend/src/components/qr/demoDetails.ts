import type { Payment } from '../../api/paymentsApi'
import { formatPaymentMoney } from '../../pages/member/paymentsModel'
import { demoQrPayload } from './demoQr'
import { DEMO_BANKS, normalizeDemoBank, type DemoBank } from './demoBanks'

export { DEFAULT_DEMO_BANK, DEMO_BANK_OPTIONS, DEMO_BANKS, normalizeDemoBank, type DemoBank } from './demoBanks'
export const DEMO_ACCOUNT = 'DEMO - NOT AVAILABLE'
export const DEMO_WARNING = 'Simulated QR - No real money is transferred.'

/** QR demo eligibility only; does not reprice or mutate the underlying payment. */
export function demoPaymentAmountAvailable(amount: Payment['amount']): boolean {
  return (typeof amount === 'number' || typeof amount === 'string') && Number(amount) > 0 && formatPaymentMoney(amount) !== '-'
}

export function demoReference(paymentId: number): string {
  demoQrPayload(paymentId)
  return `DEMO-${paymentId}`
}

/** Display-only data from the authorized DTO, never real beneficiary instructions. */
export function demoDetails(payment: Pick<Payment, 'id' | 'amount'>, bank: DemoBank): string {
  const reference = demoReference(payment.id)
  const amount = formatPaymentMoney(payment.amount)
  if (!demoPaymentAmountAvailable(payment.amount)) throw new Error('Demo amount unavailable')
  return [
    `Demo bank: ${DEMO_BANKS[normalizeDemoBank(bank)].label}`,
    `Account: ${DEMO_ACCOUNT}`,
    `Amount from payment: ${amount}`,
    `Demo reference: ${reference}`,
    DEMO_WARNING,
  ].join('\n')
}
