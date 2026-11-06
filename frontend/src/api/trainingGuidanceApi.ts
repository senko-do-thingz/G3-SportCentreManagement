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
  mode: 'KNOWLEDGE_BASED'; status: 'ANSWERED' | 'NO_ANSWER' | 'CONSENT_REQUIRED' | 'UNAVAILABLE'
  answer: string; historyStored: false
  sources: { kind: string; id: number; title: string; detail: string; path: string }[]
}
/** F5-01: the Member's level for one sport; null means the general profile level is used. */
export interface SportLevel { sportId: number; sportName: string; level: string | null }
/** F5-05: a bookmarked class. It is not a booking; the next session is recomputed on every read. */
export interface SavedRecommendation {
  id: number; classId: number; className: string; sportName: string; level: string; coachName: string | null
  reason: string | null; savedAt: string
  nextSession: { sessionId: number; sessionCode: SessionId | null; date: string; startTime: string; endTime: string; freeSeats: number } | null
}
const training = '/member/training'
export const trainingGuidanceApi = {
  recommendations: (preferences: RecommendationPreferences, signal: AbortSignal) => api.get<Recommendations>('/member/training/recommendations', { query: { ...preferences }, signal }),
  sportLevels: (signal: AbortSignal) => api.get<SportLevel[]>(`${training}/sport-levels`, { signal }),
  saveSportLevel: (sportId: number, level: string) => api.put<SportLevel>(`${training}/sport-levels/${sportId}`, { level }),
  clearSportLevel: (sportId: number) => api.delete<void>(`${training}/sport-levels/${sportId}`),
  savedRecommendations: (signal: AbortSignal) => api.get<SavedRecommendation[]>(`${training}/saved-recommendations`, { signal }),
  saveRecommendation: (classId: number, reason: string | null) => api.post<SavedRecommendation>(`${training}/saved-recommendations`, { classId, reason }),
  removeSavedRecommendation: (id: number) => api.delete<void>(`${training}/saved-recommendations/${id}`),
  consult: (question: string) => api.post<Consultation>('/member/training/consultation', { question, optIn: true }),
}
