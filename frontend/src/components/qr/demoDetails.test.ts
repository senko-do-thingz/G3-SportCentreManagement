import { describe, expect, it } from 'vitest'
import { payment } from '../../test/memberFixtures'
import { DEMO_BANK_OPTIONS, DEMO_BANKS, demoDetails, demoReference, normalizeDemoBank } from './demoDetails'
import { demoQrPayload } from './demoQr'

describe('QR-02 display-only details', () => {
  it('has exactly the four approved labels in order', () => {
    expect(DEMO_BANK_OPTIONS.map((option) => option.label)).toEqual([
      'Vietcombank (demo)', 'BIDV (demo)', 'Techcombank (demo)', 'MB Bank (demo)',
    ])
  })

  it.each([undefined, null, '', '__proto__', 'constructor', 'BIDV', '970436', 52])('defaults unexpected selection %s safely', (value) => {
    expect(normalizeDemoBank(value)).toBe('vietcombank')
  })

  it.each(DEMO_BANK_OPTIONS)('copies deterministic safe details for $label without changing the QR', ({ value, label }) => {
    const bank = normalizeDemoBank(value)
    expect(demoDetails(payment({ amount: '731234' }), bank)).toBe([
      `Demo bank: ${label}`,
      'Account: DEMO - NOT AVAILABLE',
      'Amount from payment: 731.234 VND',
      'Demo reference: DEMO-52',
      'DEMO ONLY - NOT A REAL PAYMENT',
    ].join('\n'))
    expect(DEMO_BANKS[bank]).toBe(label)
    expect(demoQrPayload(52)).toBe('SPORTIFY_DEMO_QR|VERSION=1|PAYMENT_ID=52|MODE=NON_PAYABLE|BANK_LABEL=VIETCOMBANK_DEMO')
  })

  it('rejects missing/unsafe IDs and amounts rather than inventing copy data', () => {
    expect(demoReference(52)).toBe('DEMO-52')
    expect(() => demoReference(0)).toThrow()
    expect(() => demoReference('52|ACCOUNT=1234567890' as unknown as number)).toThrow()
    expect(() => demoDetails(payment({ amount: 'invalid' }), 'mb')).toThrow('Demo amount unavailable')
  })
})
