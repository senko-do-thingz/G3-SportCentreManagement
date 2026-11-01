import { api } from './client'

export interface TrainingSession {
  id: number
  className: string
  date: string
  startTime: string
  endTime: string
}

export interface TrainingPlan {
  id: number
  title: string
  description: string | null
  className: string
  coachName: string
}

export interface TrainingStep {
  id: number
  stepOrder: number
  title: string
  durationMinutes: number
  instructions: string | null
}

export interface PlanDetail {
  plan: TrainingPlan
  steps: TrainingStep[]
  feedback: CoachFeedback[]
}

export interface Attendance {
  id: number
  session: TrainingSession
  status: string
  notes: string | null
}

export interface TrainingResult {
  id: number
  session: TrainingSession
  notes: string | null
}

export interface CoachFeedback {
  id: number
  planId: number
  planTitle: string
  coachName: string
  rating: number
  comments: string
  strengths: string | null
  areasForImprovement: string | null
  createdAt: string
}

export interface SkillScore {
  id: number
  metricName: string
  description: string | null
  score: number
  maxScore: number
}

export interface ResultDetail {
  result: TrainingResult
  scores: SkillScore[]
}

const root = '/member/training'
export const memberTrainingApi = {
  plans: (signal: AbortSignal) => api.get<TrainingPlan[]>(`${root}/plans`, { signal }),
  plan: (id: string, signal: AbortSignal) => api.get<PlanDetail>(`${root}/plans/${encodeURIComponent(id)}`, { signal }),
  attendance: (signal: AbortSignal) => api.get<Attendance[]>(`${root}/attendance`, { signal }),
  results: (signal: AbortSignal) => api.get<TrainingResult[]>(`${root}/results`, { signal }),
  result: (id: string, signal: AbortSignal) => api.get<ResultDetail>(`${root}/results/${encodeURIComponent(id)}`, { signal }),
  feedback: (signal: AbortSignal) => api.get<CoachFeedback[]>(`${root}/feedback`, { signal }),
}
