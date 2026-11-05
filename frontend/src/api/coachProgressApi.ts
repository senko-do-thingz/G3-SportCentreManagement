import { api } from './client'
import type { CoachFeedback } from './memberTrainingApi'
export interface LearnerProfile { memberId: number; memberCode: string | null; memberName: string; mainGoal: string | null; currentLevel: string | null }
export interface LearnerProgress {
  learner: LearnerProfile
  sessions: { resultId: number; sessionId: number; sessionCode: string; date: string; notes: string | null; attendanceStatus: string | null; notesOnly: boolean; scores: { metricId: number; metricName: string; maxScore: number; score: number }[] }[]
  feedback: CoachFeedback[]
  interpretation: string
}
export async function coachProgress(classId: number, memberId: number, signal: AbortSignal) {
  const base = `/coach/training/classes/${classId}/members/${memberId}`
  const [profile, progress] = await Promise.all([
    api.get<LearnerProfile>(`${base}/profile`, { signal }), api.get<LearnerProgress>(`${base}/progress`, { signal }),
  ])
  if (profile.memberId !== memberId || progress.learner?.memberId !== memberId || !Array.isArray(progress.sessions) || !Array.isArray(progress.feedback)) throw new Error('The progress service returned an unexpected learner response.')
  return { profile, progress }
}
