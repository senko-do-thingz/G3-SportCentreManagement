/** Deliberately plain text, never a bank/EMV payload, URL or gateway token. */
export function demoQrPayload(paymentId: number): string {
  if (!Number.isSafeInteger(paymentId) || paymentId <= 0) throw new Error('Invalid demo payment identifier')
  return `SPORTIFY_DEMO_QR|VERSION=1|PAYMENT_ID=${paymentId}|MODE=NON_PAYABLE|BANK_LABEL=VIETCOMBANK_DEMO`
}

export function demoQrPath(paymentId: number): string {
  demoQrPayload(paymentId)
  return `/member/payments/${paymentId}/qr-demo`
}
