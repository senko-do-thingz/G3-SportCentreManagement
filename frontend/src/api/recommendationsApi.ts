import { api } from './client'

export type RecommendationStatus = 'PENDING' | 'APPROVED' | 'MODIFIED' | 'REJECTED'

export interface RecommendationInput {
  memberId: number
  goal: string
  fitnessLevel: string
  currentFocus: string
  sessionDurationMinutes: number
  consentConfirmed: boolean
}

export interface SuggestedExercise {
  id: string
  name: string
  durationMinutes: number
  target: string
  rationale: string
  sets: number | null
  reps: number | null
  restSeconds: number | null
}

export interface WorkoutRecommendation {
  id: number
  memberId: number
  memberName: string
  memberCode: string
  className: string | null
  sportName: string
  goal: string
  fitnessLevel: string
  currentFocus: string
  recentResultsSummary: string | null
  sessionDurationMinutes: number
  status: RecommendationStatus
  revision: number
  exercises: SuggestedExercise[]
  coachAdjustments: string | null
  generatedAt: string
  reviewedAt: string | null
  draftPlanId: number | null
}

export interface RecommendationReviewInput {
  exercises: SuggestedExercise[]
  coachAdjustments: string
  decision: 'APPROVE' | 'REJECT'
  expectedRevision: number
}

function asList(value: unknown): WorkoutRecommendation[] {
  if (!Array.isArray(value)) throw new Error('The server returned an unexpected recommendation response.')
  return value as WorkoutRecommendation[]
}

export const recommendationsApi = {
  list: async (signal?: AbortSignal) => asList(await api.get<unknown>('/coach/ai-recommendations', { signal })),
  generate: (input: RecommendationInput) => api.post<WorkoutRecommendation>('/coach/ai-recommendations', input),
  review: (recommendationId: number, input: RecommendationReviewInput) =>
    api.put<WorkoutRecommendation>(`/coach/ai-recommendations/${recommendationId}/review`, input),
  createDraft: (recommendationId: number, expectedRevision: number) =>
    api.post<WorkoutRecommendation>(`/coach/ai-recommendations/${recommendationId}/training-plan-draft`, { expectedRevision }),
}
