import type { Payment } from '../../api/paymentsApi'
import { formatPaymentMoney } from '../../pages/member/paymentsModel'
import { demoQrPayload } from './demoQr'

export const DEMO_BANKS = {
  vietcombank: 'Vietcombank (demo)',
  bidv: 'BIDV (demo)',
  techcombank: 'Techcombank (demo)',
  mb: 'MB Bank (demo)',
} as const

export type DemoBank = keyof typeof DEMO_BANKS
export const DEFAULT_DEMO_BANK: DemoBank = 'vietcombank'
export const DEMO_BANK_OPTIONS = Object.entries(DEMO_BANKS).map(([value, label]) => ({ value, label }))
export const DEMO_ACCOUNT = 'DEMO - NOT AVAILABLE'
export const DEMO_WARNING = 'DEMO ONLY - NOT A REAL PAYMENT'

export function normalizeDemoBank(value: unknown): DemoBank {
  return typeof value === 'string' && Object.hasOwn(DEMO_BANKS, value) ? value as DemoBank : DEFAULT_DEMO_BANK
}

export function demoReference(paymentId: number): string {
  demoQrPayload(paymentId)
  return `DEMO-${paymentId}`
}

/** Display-only data from the authorized DTO, never real beneficiary instructions. */
export function demoDetails(payment: Pick<Payment, 'id' | 'amount'>, bank: DemoBank): string {
  const reference = demoReference(payment.id)
  const amount = formatPaymentMoney(payment.amount)
  if (amount === '-') throw new Error('Demo amount unavailable')
  return [
    `Demo bank: ${DEMO_BANKS[normalizeDemoBank(bank)]}`,
    `Account: ${DEMO_ACCOUNT}`,
    `Amount from payment: ${amount}`,
    `Demo reference: ${reference}`,
    DEMO_WARNING,
  ].join('\n')
}
