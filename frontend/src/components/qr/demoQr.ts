import { DEFAULT_DEMO_BANK, DEMO_BANKS, normalizeDemoBank, type DemoBank } from './demoBanks'

/** Deliberately plain text, never a bank/EMV payload, URL or gateway token. */
export function demoQrPayload(paymentId: number, bank: DemoBank = DEFAULT_DEMO_BANK): string {
  if (!Number.isSafeInteger(paymentId) || paymentId <= 0) throw new Error('Invalid demo payment identifier')
  return `SPORTIFY_DEMO_QR|VERSION=2|PAYMENT_ID=${paymentId}|MODE=NON_PAYABLE|BANK_CODE=${DEMO_BANKS[normalizeDemoBank(bank)].code}`
}

export function demoQrPath(paymentId: number): string {
  demoQrPayload(paymentId)
  return `/member/payments/${paymentId}/qr-demo`
}
