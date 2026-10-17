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
export const coachProfilesApi = { get: (id: number, signal?: AbortSignal) => api.get<CoachProfile>(`/coaches/${id}`, { signal }) }
