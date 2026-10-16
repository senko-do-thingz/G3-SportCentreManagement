import { api } from './client'
import { normalizePage } from './page'
import type { PageResponse, SpringPage } from './types'

export type ClassLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'
export interface ManagerClass {
  id: number
  code: string
  name: string
  sportId: number
  sportName: string
  ageGroupId: number
  ageGroupLabel: string
  level: ClassLevel
  coachId: number
  coachName: string
  maxMembers: number
  upcomingSessions: number
  active: boolean
  createdAt: string
  updatedAt: string
}
export interface ClassRequest {
  name: string
  sportId: number
  ageGroupId: number
  level: ClassLevel
  coachId: number
  maxMembers: number
}
export interface ClassFilters {
  search?: string
  sportId?: number
  level?: ClassLevel
  coachId?: number
  active?: boolean
  page?: number
  size?: number
}
export interface AgeGroup {
  id: number
  code: string
  label: string
  minAge: number | null
  maxAge: number | null
}
export interface CoachSummary {
  id: number
  fullName: string
  headline: string | null
  specialties: string | null
  yearsExperience: number | null
  sportIds: number[]
}

export const managerClassesApi = {
  async list(filters: ClassFilters = {}, signal?: AbortSignal): Promise<PageResponse<ManagerClass>> {
    return normalizePage(await api.get<PageResponse<ManagerClass> | SpringPage<ManagerClass>>(
      '/manager/classes', { query: { ...filters }, signal },
    ))
  },
  get: (id: number, signal?: AbortSignal) => api.get<ManagerClass>(`/manager/classes/${id}`, { signal }),
  create: (body: ClassRequest) => api.post<ManagerClass>('/manager/classes', body),
  update: (id: number, body: ClassRequest) => api.put<ManagerClass>(`/manager/classes/${id}`, body),
  setActive: (id: number, active: boolean) => api.patch<ManagerClass>(`/manager/classes/${id}/status`, { active }),
  ageGroups: (signal?: AbortSignal) => api.get<AgeGroup[]>('/age-groups', { signal }),
  coaches: (sportId?: number, signal?: AbortSignal) => api.get<CoachSummary[]>('/coaches', { query: { sportId }, signal }),
}
