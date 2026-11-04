import { DEFAULT_DEMO_BANK, DEMO_BANKS, normalizeDemoBank, type DemoBank } from './demoBanks'

/** Deliberately plain text, never a bank/EMV payload, URL or gateway token. */
export function demoQrPayload(paymentId: number, bank: DemoBank = DEFAULT_DEMO_BANK): string {
  if (!Number.isSafeInteger(paymentId) || paymentId <= 0) throw new Error('Invalid demo payment identifier')
  return `SPORTIFY_DEMO_QR|VERSION=2|PAYMENT_ID=${paymentId}|MODE=NON_PAYABLE|BANK_CODE=${DEMO_BANKS[normalizeDemoBank(bank)].code}`
}

/** Synthetic non-payable text for the local QR renderer. Contains no private or sensitive data. */
export function demoQrSyntheticPayload(paymentId: number, code?: string): string {
  if (!Number.isSafeInteger(paymentId) || paymentId <= 0) throw new Error('Invalid demo payment identifier')
  const candidate = code?.trim() ?? ''
  const ref = /^(?:PAY|DEMO)-[0-9]{1,20}$/.test(candidate) ? candidate : `DEMO-${paymentId}`
  return `SPORTIFY_DEMO|REF=${ref}|NON_PAYABLE=1`
}

export function demoQrImageUrl(payload: string): string {
  return `https://api.qrserver.com/v1/create-qr-code/?size=240x240&format=png&qzone=4&ecc=M&data=${encodeURIComponent(payload)}`
}

export function demoQrPath(paymentId: number): string {
  demoQrPayload(paymentId)
  return `/member/payments/${paymentId}/qr-demo`
}
