import { api } from './client'
import type { BrowseSession } from './memberSessionsApi'
import type { PageResponse } from './types'

export interface CoachProfile {
  id: number
  name: string
  headline: string | null
  bio: string | null
  yearsExperience: number | null
  specialties: string | null
  sports: { id: number; name: string }[]
  certifications: { id: number; sportId: number | null; name: string; issuer: string | null; issuedOn: string | null; expiresOn: string | null; verified: boolean }[]
  upcomingSessions: PageResponse<BrowseSession>
}

export interface CoachSelfCertification {
  id: number
  sportId: number | null
  sportName: string | null
  name: string
  issuer: string | null
  issuedOn: string | null
  expiresOn: string | null
  verified: boolean
}

export interface CoachCertificationOption {
  key: string
  name: string
  issuer: string | null
  sportId: number | null
  sportName: string | null
}

export interface CoachSelfProfile {
  userId: number
  fullName: string
  email: string
  avatarUrl: string | null
  headline: string | null
  bio: string | null
  yearsExperience: number | null
  specialties: string | null
  isPublic: boolean
  displayOrder: number
  sports: { id: number; name: string }[]
  certifications: CoachSelfCertification[]
  certificationOptions: CoachCertificationOption[]
}

export interface CoachSelfProfileUpdateRequest {
  avatarUrl?: string | null
  headline: string
  bio: string
  yearsExperience: number | null
  specialties: string
}

export interface CoachSelfCertificationCreateRequest {
  name: string
  issuer?: string | null
  sportId?: number | null
  issuedOn?: string | null
  expiresOn?: string | null
}

// API xem ho so cong khai cua Coach (cho Member) va tu quan ly ho so + chung chi (cho Coach)
export const coachProfilesApi = {
  get: (id: number, signal?: AbortSignal) => api.get<CoachProfile>(`/coaches/${id}`, { signal }),
  getMyProfile: (signal?: AbortSignal) => api.get<CoachSelfProfile>('/coach/profile', { signal }),
  getCertificationOptions: (signal?: AbortSignal) => api.get<CoachCertificationOption[]>('/coach/profile/certification-options', { signal }),
  updateMyProfile: (request: CoachSelfProfileUpdateRequest) => api.put<CoachSelfProfile>('/coach/profile', request),
  uploadMyPhoto: (photo: Blob) => {
    const body = new FormData()
    body.append('file', photo, 'photo.jpg')
    return api.post<CoachSelfProfile>('/coach/profile/photo', body)
  },
  addCertification: (request: CoachSelfCertificationCreateRequest) =>
    api.post<CoachSelfProfile>('/coach/profile/certifications', request),
  removeCertification: (id: number) =>
    api.delete<CoachSelfProfile>(`/coach/profile/certifications/${id}`),
}
