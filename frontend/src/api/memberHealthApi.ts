import { api } from './client'

export type HealthAnswer = 'YES' | 'NO' | 'PREFER_NOT_TO_ANSWER'
export type HealthOutcome = 'NO_REPORTED_CONCERNS' | 'COACH_DISCUSSION_RECOMMENDED' | 'INCOMPLETE'

export interface HealthAssessment {
  version: string
  questions: { key: string; text: string }[]
  answers: Partial<Record<string, HealthAnswer>>
  outcome: HealthOutcome
  shareWithCoach: boolean
  saved: boolean
  updatedAt: string | null
  consentUpdatedAt: string | null
  disclaimer: string
}

export interface HealthSaveRequest {
  answers: Partial<Record<string, HealthAnswer | null>>
  shareWithCoach: boolean
}

/** What an assigned Coach may see: the advisory outcome only, and only with the Member's consent. */
export interface CoachHealthSummary {
  memberId: number
  shared: boolean
  outcome: HealthOutcome | null
  consultationRequested: boolean | null
  updatedAt: string | null
  disclaimer: string
}

export const HEALTH_OUTCOME_LABELS: Record<HealthOutcome, string> = {
  NO_REPORTED_CONCERNS: 'No reported concerns',
  COACH_DISCUSSION_RECOMMENDED: 'Coach conversation recommended',
  INCOMPLETE: 'Incomplete',
}

/** MW-02: the Member's own optional questionnaire. */
export const memberHealthApi = {
  get: (signal: AbortSignal) => api.get<HealthAssessment>('/member/health-assessment', { signal }),
  save: (body: HealthSaveRequest) => api.put<HealthAssessment>('/member/health-assessment', body),
  clear: () => api.delete<HealthAssessment>('/member/health-assessment'),
  coachSummary: (classId: number, memberId: number, signal: AbortSignal) =>
    api.get<CoachHealthSummary>(`/coach/training/classes/${classId}/members/${memberId}/health-summary`, { signal }),
}
