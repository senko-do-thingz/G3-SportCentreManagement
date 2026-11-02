import { api } from './client'

export interface RecommendationPreferences {
  sportId?: number; level?: string; goal?: string; from?: string; to?: string; earliest?: string; latest?: string; page?: number; size?: number
}
export interface ClassRecommendation {
  classId: number; sessionId: number; sportId: number; className: string; sportName: string; level: string
  coachName: string | null; trainingFormat: string; date: string; startTime: string; endTime: string
  freeSeats: number; score: number; reasons: string[]
}
export interface Recommendations {
  mode: 'RULE_BASED'; ruleVersion: string; bookingEligibilityChecked: false; message: string
  page: number; size: number; totalMatches: number; items: ClassRecommendation[]
}
export const trainingGuidanceApi = {
  recommendations: (preferences: RecommendationPreferences, signal: AbortSignal) => api.get<Recommendations>('/member/training/recommendations', { query: { ...preferences }, signal }),
}
