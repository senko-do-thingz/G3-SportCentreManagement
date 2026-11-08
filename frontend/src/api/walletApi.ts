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
