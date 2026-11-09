import { api } from './client'

export type WalletPool = 'ACTUAL' | 'SIMULATED'
export type WalletEntryType = 'TOPUP_CREDIT' | 'PURCHASE_DEBIT' | 'COMPENSATION_CREDIT' | 'WITHDRAWAL_RESERVE' | 'WITHDRAWAL_RELEASE' | 'WITHDRAWAL_PAYOUT'

export interface WalletEntry {
  id: number
  entryType: WalletEntryType
  pool: WalletPool
  amount: number
  direction: 'CREDIT' | 'DEBIT' | 'PAYOUT'
  availableAfter: number
  reservedAfter: number
  sourceType: string
  sourceId: number
  description: string
  createdAt: string
}

/** Balances are computed by the backend; the page only displays them. */
export interface WalletOverview {
  availableBalance: number
  reservedBalance: number
  demoAvailableBalance: number
  demoReservedBalance: number
  currency: string
  updatedAt: string
  recentActivity: WalletEntry[]
  activityCount: number
  notice: string
}

export interface WalletLedgerPage { content: WalletEntry[]; page: number; size: number; totalElements: number; totalPages: number }

export const WALLET_ENTRY_LABELS: Record<WalletEntryType, string> = {
  TOPUP_CREDIT: 'Top-up',
  PURCHASE_DEBIT: 'Wallet payment',
  COMPENSATION_CREDIT: 'Compensation',
  WITHDRAWAL_RESERVE: 'Withdrawal requested (held)',
  WITHDRAWAL_RELEASE: 'Withdrawal released',
  WITHDRAWAL_PAYOUT: 'Withdrawal paid out',
}

/** MW-03: the signed-in Member's own internal wallet. */
export const walletApi = {
  overview: (signal: AbortSignal) => api.get<WalletOverview>('/member/wallet', { signal }),
  ledger: (page: number, size: number, signal: AbortSignal) =>
    api.get<WalletLedgerPage>('/member/wallet/ledger', { signal, query: { page, size } }),
}

export type TopUpStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED'

export interface TopUpInstructions {
  payable: boolean
  notice: string
  bankBin: string | null
  bankName: string | null
  accountNumber: string | null
  accountName: string | null
  amount: number
  transferReference: string
  qrPayload: string | null
}

export interface WalletTopUp {
  id: number
  code: string
  memberId: number
  memberCode: string
  memberName: string
  mode: WalletPool
  amount: number
  status: TopUpStatus
  transferReference: string
  receivedAmount: number | null
  bankReference: string | null
  reviewNote: string | null
  reviewedByName: string | null
  reviewedAt: string | null
  ledgerEntryId: number | null
  createdAt: string
  instructions: TopUpInstructions | null
}

export interface MemberTopUps { simulatedTopUpsEnabled: boolean; realTopUpsAvailable: boolean; minAmount: number; maxAmount: number; topUps: WalletTopUp[] }

/** MW-04: the Member's own top-up requests. Creating one only records PENDING. */
export const topUpApi = {
  mine: (signal: AbortSignal) => api.get<MemberTopUps>('/member/wallet/topups', { signal }),
  create: (amount: number, mode: WalletPool, key: string) =>
    api.post<WalletTopUp>('/member/wallet/topups', { amount, mode }, { headers: { 'Idempotency-Key': key } }),
  cancel: (id: number) => api.post<WalletTopUp>(`/member/wallet/topups/${id}/cancel`),
}

/** MW-04: Receptionist confirmation queue. */
export const receptionTopUpApi = {
  list: (status: string, signal: AbortSignal) => api.get<WalletTopUp[]>('/reception/wallet/topups', { signal, query: { status } }),
  detail: (id: number) => api.get<WalletTopUp>(`/reception/wallet/topups/${id}`),
  confirm: (id: number, receivedAmount: number, bankReference: string | null, note: string) =>
    api.post<WalletTopUp>(`/reception/wallet/topups/${id}/confirm`, { receivedAmount, bankReference, note }),
  reject: (id: number, reason: string) => api.post<WalletTopUp>(`/reception/wallet/topups/${id}/reject`, { reason }),
}

export interface WalletPaymentResult {
  paymentId: number
  paymentCode: string
  status: string
  method: string
  amount: number
  ledgerEntryId: number
  availableAfter: number
  invoiceId: number | null
  invoiceNumber: string | null
  replay: boolean
}

/** MW-05: settle one own pending purchase from the ACTUAL wallet balance (debit, activation and invoice together). */
export const walletPaymentApi = {
  pay: (paymentId: number) => api.post<WalletPaymentResult>(`/member/wallet/payments/${paymentId}`),
}
