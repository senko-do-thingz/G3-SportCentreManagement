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

  it.each(DEMO_BANK_OPTIONS)('copies deterministic safe details for $label independently of QR data', ({ value, label }) => {
    const bank = normalizeDemoBank(value)
    expect(demoDetails(payment({ amount: '731234' }), bank)).toBe([
      `Demo bank: ${label}`,
      'Account: DEMO - NOT AVAILABLE',
      'Amount from payment: 731.234 VND',
      'Demo reference: DEMO-52',
      'Simulated QR - No real money is transferred.',
    ].join('\n'))
    expect(DEMO_BANKS[bank].label).toBe(label)
    expect(demoQrPayload(52, bank)).toContain(`BANK_CODE=${DEMO_BANKS[bank].code}`)
  })

  it('rejects missing/unsafe IDs and amounts rather than inventing copy data', () => {
    expect(demoReference(52)).toBe('DEMO-52')
    expect(() => demoReference(0)).toThrow()
    expect(() => demoReference('52|ACCOUNT=1234567890' as unknown as number)).toThrow()
    expect(() => demoDetails(payment({ amount: 'invalid' }), 'mb')).toThrow('Demo amount unavailable')
  })

  it.each([0, -1, '0', '-0.01', '', ' ', NaN, Infinity])('does not copy an ineligible demo amount %s', (amount) => {
    expect(() => demoDetails(payment({ amount }), 'mb')).toThrow('Demo amount unavailable')
  })
})
