import { api } from './client'
import type { SessionId } from './sessionIdentity'

export interface RecommendationPreferences {
  sportId?: number; level?: string; goal?: string; from?: string; to?: string; earliest?: string; latest?: string; page?: number; size?: number
}
export interface ClassRecommendation {
  sessionCode?: SessionId
  classId: number; sessionId: number; sportId: number; className: string; sportName: string; level: string
  coachName: string | null; trainingFormat: string; date: string; startTime: string; endTime: string
  freeSeats: number; score: number; reasons: string[]
}
export interface Recommendations {
  mode: 'RULE_BASED'; ruleVersion: string; bookingEligibilityChecked: false; message: string
  page: number; size: number; totalMatches: number; items: ClassRecommendation[]
}
export interface Consultation {
  mode: 'KNOWLEDGE_BASED' | 'AI_ASSISTED'; status: 'ANSWERED' | 'NO_ANSWER' | 'CONSENT_REQUIRED' | 'UNAVAILABLE'
  answer: string; historyStored: false
  sources: { kind: string; id: number; title: string; detail: string; path: string }[]
  /** 'vi' when the question was Vietnamese and the answer text is Vietnamese. */
  language?: 'vi' | 'en'
}
export const trainingGuidanceApi = {
  recommendations: (preferences: RecommendationPreferences, signal: AbortSignal) => api.get<Recommendations>('/member/training/recommendations', { query: { ...preferences }, signal }),
  consult: (question: string) => api.post<Consultation>('/member/training/consultation', { question, optIn: true }),
}
