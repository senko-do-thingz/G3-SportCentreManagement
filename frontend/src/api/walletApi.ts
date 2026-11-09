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

export type WithdrawalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'FAILED' | 'SIMULATED_COMPLETED'

export interface PayoutAccount { bankBin: string; bankName: string; maskedAccountNumber: string; accountHolder: string; updatedAt: string }

export interface WalletWithdrawal {
  id: number
  code: string
  memberId: number
  memberCode: string
  memberName: string
  pool: WalletPool
  amount: number
  status: WithdrawalStatus
  bankName: string | null
  /** Masked for Members and lists; full only in the Receptionist processing detail. */
  accountNumber: string | null
  accountHolder: string | null
  reviewNote: string | null
  reviewedByName: string | null
  reviewedAt: string | null
  payoutReference: string | null
  completedByName: string | null
  completedAt: string | null
  createdAt: string
}

export interface BankOption { bin: string; code: string; name: string; transferSupported: boolean }

export const WITHDRAWAL_STATUS_LABELS: Record<WithdrawalStatus, string> = {
  PENDING: 'Waiting for review',
  APPROVED: 'Approved, not yet paid',
  REJECTED: 'Rejected, funds released',
  COMPLETED: 'Paid out',
  FAILED: 'Payout failed, funds released',
  SIMULATED_COMPLETED: 'Demo completed (no real transfer)',
}

/** MW-06: the Member's payout account and withdrawal requests. */
export const withdrawalApi = {
  account: async (signal: AbortSignal) => (await api.get<PayoutAccount | undefined>('/member/wallet/payout-account', { signal })) ?? null,
  saveAccount: (bankBin: string, accountNumber: string, accountHolder: string) =>
    api.put<PayoutAccount>('/member/wallet/payout-account', { bankBin, accountNumber, accountHolder }),
  banks: async (signal: AbortSignal) => (await api.get<{ banks: BankOption[] }>('/payments/vietqr-banks', { signal })).banks,
  mine: (signal: AbortSignal) => api.get<WalletWithdrawal[]>('/member/wallet/withdrawals', { signal }),
  create: (amount: number, pool: WalletPool, confirmAccountLast4: string | null, key: string) =>
    api.post<WalletWithdrawal>('/member/wallet/withdrawals', { amount, pool, confirmAccountLast4 }, { headers: { 'Idempotency-Key': key } }),
}

/** MW-06: Receptionist review. Approve is not a payout; complete records the transfer separately. */
export const receptionWithdrawalApi = {
  list: (status: string, signal: AbortSignal) => api.get<WalletWithdrawal[]>('/reception/wallet/withdrawals', { signal, query: { status } }),
  detail: (id: number, signal: AbortSignal) => api.get<WalletWithdrawal>(`/reception/wallet/withdrawals/${id}`, { signal }),
  approve: (id: number, note: string) => api.post<WalletWithdrawal>(`/reception/wallet/withdrawals/${id}/approve`, { note }),
  reject: (id: number, note: string) => api.post<WalletWithdrawal>(`/reception/wallet/withdrawals/${id}/reject`, { note }),
  complete: (id: number, payoutReference: string | null, note: string) =>
    api.post<WalletWithdrawal>(`/reception/wallet/withdrawals/${id}/complete`, { payoutReference, note }),
  fail: (id: number, note: string) => api.post<WalletWithdrawal>(`/reception/wallet/withdrawals/${id}/fail`, { note }),
}

export interface InboxItem {
  id: number
  title: string
  content: string
  createdAt: string
  /** Presentation state only. */
  read: boolean
  sourceType: 'WALLET_TOPUP' | 'WALLET_WITHDRAWAL' | 'PAYMENT_EXCEPTION' | 'SUPPORT_REQUEST' | 'UNKNOWN'
  sourceId: number
  /** Live status of the task itself, read from its source record. */
  sourceStatus: string
  open: boolean
  link: string
}

export interface ReceptionInbox { items: InboxItem[]; unreadOpen: number; open: number }

/** MW-07: the Receptionist's own action inbox. */
export const receptionInboxApi = {
  get: (openOnly: boolean, signal?: AbortSignal) => api.get<ReceptionInbox>('/reception/inbox', { signal, query: { openOnly } }),
  markRead: (id: number) => api.put<InboxItem>(`/reception/inbox/${id}/read`),
}

export interface WalletActivityRow {
  type: 'TOPUP' | 'WALLET_PAYMENT' | 'WITHDRAWAL' | 'COMPENSATION'
  id: number
  reference: string
  createdAt: string
  amount: number
  /** NONE for a pending top-up or a released withdrawal: no money moved for the member. */
  direction: 'CREDIT' | 'DEBIT' | 'NONE'
  status: string
  pool: WalletPool
  availableAfter: number | null
  detail: string
  paymentId: number | null
  bankReference: string | null
  settledAt: string | null
}

export interface WalletHistory {
  page: { content: WalletActivityRow[]; page: number; size: number; totalElements: number; totalPages: number }
  availableBalance: number
  reservedBalance: number
  ledgerAvailable: number
  ledgerReserved: number
  reconciled: boolean
}

/** MW-08: the Member's own wallet activity. */
export const walletHistoryApi = {
  history: (filters: { type: string; status: string; from: string; to: string; page: number; size: number }, signal: AbortSignal) =>
    api.get<WalletHistory>('/member/wallet/history', { signal, query: filters }),
}
