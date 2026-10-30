import { api } from './client'
import { DEMO_BANKS } from '../components/qr/demoBanks'

export type DemoSessionState = 'PENDING' | 'AUTHORIZED_PENDING_SETTLEMENT' | 'SETTLED' | 'CANCELED' | 'EXPIRED'
export interface DemoSession {
  id: string
  paymentId: number
  reference: string
  amount: number | string
  currency: 'VND'
  bankCode: string
  bankLabel: string
  createdAt: string
  expiresAt: string
  serverTime: string
  state: DemoSessionState
  mode: 'NON_PAYABLE'
  settlementType?: string | null
  settlementProvider?: string | null
  simulatedTransactionId?: number | null
  transactionCode?: string | null
  settledAt?: string | null
  invoiceId?: number | null
  invoiceNumber?: string | null
}
export interface CreatedDemoSession { session: DemoSession; confirmationUrl: string }
const path = '/payments/simulated-qr/sessions'
export const DEMO_TOKEN = /^[A-Za-z0-9_-]{43}$/

function session(value: unknown): DemoSession {
  const item = value as DemoSession | null
  if (!item || typeof item.id !== 'string' || !/^[a-f0-9-]{36}$/.test(item.id)
    || !Number.isSafeInteger(item.paymentId) || item.paymentId <= 0 || item.reference !== `DEMO-${item.paymentId}`
    || !Number.isFinite(Number(item.amount)) || Number(item.amount) <= 0 || item.currency !== 'VND'
    || !Object.values(DEMO_BANKS).some((bank) => bank.code === item.bankCode && bank.label === item.bankLabel)
    || !['PENDING', 'AUTHORIZED_PENDING_SETTLEMENT', 'SETTLED', 'CANCELED', 'EXPIRED'].includes(item.state) || item.mode !== 'NON_PAYABLE'
    || ![item.createdAt, item.expiresAt, item.serverTime].every((time) => typeof time === 'string' && Number.isFinite(Date.parse(time)))) {
    throw new Error('Invalid demo session response')
  }
  return item
}

function created(value: unknown): CreatedDemoSession {
  const item = value as CreatedDemoSession
  const snapshot = session(item?.session)
  const url = new URL(item.confirmationUrl)
  if (url.protocol !== 'https:' || url.username || url.password || url.search
    || !url.pathname.endsWith('/demo/qr/confirm') || !DEMO_TOKEN.test(url.hash.slice(7)) || !url.hash.startsWith('#token=')) {
    throw new Error('Invalid demo confirmation link')
  }
  return { session: snapshot, confirmationUrl: url.href }
}

/** The shared bearer client handles authentication; simulated settlement is gated by backend capability. */
export const demoQrApi = {
  capability: async (): Promise<boolean> => {
    const value = await api.get<{ enabled: boolean; settlementEnabled: boolean; contractVersion: number }>('/payments/simulated-qr/capability')
    return value.enabled === true && value.settlementEnabled === true && value.contractVersion === 1
  },
  create: async (paymentId: number, bankCode: string) => created(await api.post(path, { paymentId, bankCode, requestId: crypto.randomUUID() })),
  get: async (id: string) => session(await api.get(`${path}/${encodeURIComponent(id)}`)),
  preview: async (token: string) => session(await api.post(`${path}/preview`, { token })),
  confirm: async (token: string) => session(await api.post(`${path}/confirm-intent`, { token })),
  cancel: async (id: string) => session(await api.post(`${path}/${encodeURIComponent(id)}/cancel`)),
}
