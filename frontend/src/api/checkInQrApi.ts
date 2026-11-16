import { api } from './client'

/** Mirrors MemberCheckInQrService.MemberCheckInQr (GET /member/check-in-qr). */
export interface MemberCheckInQr {
  memberCode: string
  /** Fixed QR content for check-in only. It is the member code and never changes or expires. */
  qrContent: string
}

/** Same options every time, so the same member code always renders the identical QR image. */
export const CHECK_IN_QR_OPTIONS = { width: 320, margin: 4, errorCorrectionLevel: 'M' } as const

export const checkInQrApi = {
  mine(signal?: AbortSignal): Promise<MemberCheckInQr> {
    return api.get<MemberCheckInQr>('/member/check-in-qr', { signal })
  },
}
