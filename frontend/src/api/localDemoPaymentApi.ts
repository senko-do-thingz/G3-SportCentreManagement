import { api } from './client'

export interface LocalDemoCapability {
  contractVersion: number
  mode: string
  enabled: boolean
}

export interface LocalDemoConfirmResult {
  paymentId: number
  status: 'SUCCESS'
  settlementType: 'SIMULATED'
  settlementProvider: string
  simulatedTransactionId?: number | null
  referenceCode?: string | null
  invoiceId?: number | null
  invoiceNumber?: string | null
}

export const localDemoPaymentApi = {
  capability: async (signal?: AbortSignal): Promise<boolean> => {
    try {
      const res = await api.get<LocalDemoCapability>('/payments/local-demo/capability', { signal })
      return res?.contractVersion === 1 && res?.mode === 'LOCAL_DEMO' && res?.enabled === true
    } catch {
      return false
    }
  },

  confirm: async (paymentId: number): Promise<LocalDemoConfirmResult> => {
    if (!Number.isSafeInteger(paymentId) || paymentId <= 0) {
      throw new Error('Invalid payment identifier')
    }
    return api.post<LocalDemoConfirmResult>(`/payments/${encodeURIComponent(paymentId)}/demo-confirm`, {})
  },
}
