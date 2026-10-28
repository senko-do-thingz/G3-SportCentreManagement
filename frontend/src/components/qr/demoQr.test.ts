import { describe, expect, it } from 'vitest'
import { demoQrPath, demoQrPayload } from './demoQr'

describe('QR-01 non-payable payload boundary', () => {
  it('encodes only a validated id and fixed non-bank demo labels', () => {
    expect(demoQrPayload(52)).toBe('SPORTIFY_DEMO_QR|VERSION=1|PAYMENT_ID=52|MODE=NON_PAYABLE|BANK_LABEL=VIETCOMBANK_DEMO')
    expect(demoQrPath(52)).toBe('/member/payments/52/qr-demo')
  })

  it.each([0, -1, 1.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1, '52|ACCOUNT=1234567890' as unknown as number])('rejects unsafe id %s rather than encoding arbitrary data', (id) => {
    expect(() => demoQrPayload(id)).toThrow('Invalid demo payment identifier')
    expect(() => demoQrPath(id)).toThrow()
  })

  it('supports the full safe id range without a URL, beneficiary, amount or EMV prefix', () => {
    const payload = demoQrPayload(Number.MAX_SAFE_INTEGER)
    expect(payload).toMatch(/^SPORTIFY_DEMO_QR\|VERSION=1\|PAYMENT_ID=\d+\|MODE=NON_PAYABLE\|BANK_LABEL=VIETCOMBANK_DEMO$/)
    expect(payload).not.toMatch(/https?:|000201|ACCOUNT=|AMOUNT=|NAPAS|970436/)
  })
})
