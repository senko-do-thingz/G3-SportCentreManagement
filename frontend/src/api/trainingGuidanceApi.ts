import { api } from './client'
import type { SessionId } from './sessionIdentity'

export interface RecommendationPreferences {
  sportId?: number; level?: string; goal?: string; from?: string; to?: string; earliest?: string; latest?: string; page?: number; size?: number
  /** COACH_LED or SELF_TRAINING; omitted means both. */
  format?: string
  /** Comma-separated weekday codes such as MON,THU; omitted means any day. */
  days?: string
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
/** F5-01: the Member's level for one sport; null means the general profile level is used. */
export interface SportLevel { sportId: number; sportName: string; level: string | null }
/** F5-05: a bookmarked class. It is not a booking; the next session is recomputed on every read. */
export interface SavedRecommendation {
  id: number; classId: number; className: string; sportName: string; level: string; coachName: string | null
  reason: string | null; savedAt: string
  /** False once the class or its sport is no longer offered. Older servers omit it. */
  available?: boolean
  nextSession: { sessionId: number; sessionCode: SessionId | null; date: string; startTime: string; endTime: string; freeSeats: number } | null
}
export interface ContextPackage { sportName: string; packageName: string; trainingFormat: string; remainingSessions: number; totalSessions: number; endDate: string }
export interface TrainingContext {
  memberName: string; mainGoal: string | null; currentLevel: string | null; dateOfBirthKnown: boolean
  preferredSportIds: number[]; activePackages: ContextPackage[]
}
export type SessionAvailability = 'AVAILABLE' | 'FULL' | 'BOOKED' | 'OVERLAP' | 'AGE'
export type PackageCheck = 'ELIGIBLE' | 'NO_PACKAGE' | 'NO_SESSIONS_LEFT' | 'OUTSIDE_VALIDITY'
export interface SuitableSession {
  sessionId: number; sessionCode: SessionId | null; date: string; startTime: string; endTime: string; trainingType: string
  facilityName: string | null; freeSeats: number; availability: SessionAvailability; packageCheck: PackageCheck
}
export interface SuitableSessions {
  classId: number; className: string; sportName: string; level: string | null; coachName: string | null; ageGroup: string | null
  sessions: SuitableSession[]; packages: ContextPackage[]
}
const training = '/member/training'
export const trainingGuidanceApi = {
  context: (signal: AbortSignal) => api.get<TrainingContext>(`${training}/context`, { signal }),
  savePreferredSports: (sportIds: number[]) => api.put<TrainingContext>(`${training}/preferred-sports`, { sportIds }),
  suitableSessions: (classId: number, signal: AbortSignal) => api.get<SuitableSessions>(`${training}/recommendations/classes/${classId}/sessions`, { signal }),
  recommendations: (preferences: RecommendationPreferences, signal: AbortSignal) => api.get<Recommendations>('/member/training/recommendations', { query: { ...preferences }, signal }),
  sportLevels: (signal: AbortSignal) => api.get<SportLevel[]>(`${training}/sport-levels`, { signal }),
  saveSportLevel: (sportId: number, level: string) => api.put<SportLevel>(`${training}/sport-levels/${sportId}`, { level }),
  clearSportLevel: (sportId: number) => api.delete<void>(`${training}/sport-levels/${sportId}`),
  savedRecommendations: (signal: AbortSignal) => api.get<SavedRecommendation[]>(`${training}/saved-recommendations`, { signal }),
  saveRecommendation: (classId: number, reason: string | null) => api.post<SavedRecommendation>(`${training}/saved-recommendations`, { classId, reason }),
  removeSavedRecommendation: (id: number) => api.delete<void>(`${training}/saved-recommendations/${id}`),
  consult: (question: string) => api.post<Consultation>('/member/training/consultation', { question, optIn: true }),
}
