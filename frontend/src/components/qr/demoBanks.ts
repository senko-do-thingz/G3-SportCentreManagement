/** Canonical display labels and deliberately non-banking payload codes share one allowlist. */
export const DEMO_BANKS = {
  vietcombank: { label: 'Vietcombank (demo)', code: 'VIETCOMBANK_DEMO' },
  bidv: { label: 'BIDV (demo)', code: 'BIDV_DEMO' },
  techcombank: { label: 'Techcombank (demo)', code: 'TECHCOMBANK_DEMO' },
  mb: { label: 'MB Bank (demo)', code: 'MB_BANK_DEMO' },
} as const

export type DemoBank = keyof typeof DEMO_BANKS
export const DEFAULT_DEMO_BANK: DemoBank = 'vietcombank'
export const DEMO_BANK_OPTIONS = Object.entries(DEMO_BANKS).map(([value, bank]) => ({ value, label: bank.label }))

export function normalizeDemoBank(value: unknown): DemoBank {
  return typeof value === 'string' && Object.hasOwn(DEMO_BANKS, value) ? value as DemoBank : DEFAULT_DEMO_BANK
}
