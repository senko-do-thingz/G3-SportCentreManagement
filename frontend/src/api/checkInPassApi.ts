import { api } from './client'
import type { CheckInRecord } from './receptionApi'

export interface MemberCheckInPass {
  memberCode: string
  token: string
  expiresAt: string
  serverTime: string
}

export const checkInPassApi = {
  issue(signal?: AbortSignal): Promise<MemberCheckInPass> {
    return api.post<MemberCheckInPass>('/member/check-in-pass', undefined, { signal })
  },
  redeem(token: string): Promise<CheckInRecord> {
    return api.post<CheckInRecord>('/reception/check-in-pass/redeem', { token: token.trim() })
  },
}
