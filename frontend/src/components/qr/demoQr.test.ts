import { describe, expect, it } from 'vitest'
import { demoQrPath, demoQrPayload } from './demoQr'
import type { DemoBank } from './demoBanks'

describe('QR-03 versioned non-payable payload boundary', () => {
  it('encodes only a validated id and canonical default demo code', () => {
    expect(demoQrPayload(52)).toBe('SPORTIFY_DEMO_QR|VERSION=2|PAYMENT_ID=52|MODE=NON_PAYABLE|BANK_CODE=VIETCOMBANK_DEMO')
    expect(demoQrPath(52)).toBe('/member/payments/52/qr-demo')
  })

  it.each([0, -1, 1.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1, '52|ACCOUNT=1234567890' as unknown as number])('rejects unsafe id %s rather than encoding arbitrary data', (id) => {
    expect(() => demoQrPayload(id)).toThrow('Invalid demo payment identifier')
    expect(() => demoQrPath(id)).toThrow()
  })

  it('supports the full safe id range without a URL, beneficiary, amount or EMV prefix', () => {
    const payload = demoQrPayload(Number.MAX_SAFE_INTEGER)
    expect(payload).toMatch(/^SPORTIFY_DEMO_QR\|VERSION=2\|PAYMENT_ID=\d+\|MODE=NON_PAYABLE\|BANK_CODE=VIETCOMBANK_DEMO$/)
    expect(payload).not.toMatch(/https?:|000201|ACCOUNT=|AMOUNT=|NAPAS|970436/)
  })

  it.each([
    ['vietcombank', 'VIETCOMBANK_DEMO'], ['bidv', 'BIDV_DEMO'],
    ['techcombank', 'TECHCOMBANK_DEMO'], ['mb', 'MB_BANK_DEMO'],
  ] as const)('is deterministic and bank-aware for %s', (bank, code) => {
    const expected = `SPORTIFY_DEMO_QR|VERSION=2|PAYMENT_ID=52|MODE=NON_PAYABLE|BANK_CODE=${code}`
    expect(demoQrPayload(52, bank)).toBe(expected)
    expect(demoQrPayload(52, bank)).toBe(expected)
    expect(demoQrPayload(53, bank)).toBe(expected.replace('PAYMENT_ID=52', 'PAYMENT_ID=53'))
  })

  it.each([undefined, null, '', '__proto__', 'constructor', 'BIDV', '|MODE=PAYABLE', 'mb\nACCOUNT=123', 'https://example.invalid', 'bidv=PAYABLE', 'b\u0456dv', {}, 52])('fails closed for unexpected bank %s', (bank) => {
    expect(demoQrPayload(52, bank as DemoBank)).toBe(demoQrPayload(52, 'vietcombank'))
  })

  it('produces four distinct contents and reproduces a bank after switching away', () => {
    const banks: DemoBank[] = ['vietcombank', 'bidv', 'techcombank', 'mb']
    const first = demoQrPayload(52, 'vietcombank')
    expect(new Set(banks.map((bank) => demoQrPayload(52, bank))).size).toBe(4)
    expect(demoQrPayload(52, 'vietcombank')).toBe(first)
  })
})
