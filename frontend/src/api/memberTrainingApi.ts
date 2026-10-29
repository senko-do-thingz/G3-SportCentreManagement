import { api, type RequestOptions } from './client'
import { ApiError } from './errors'
import { isNonnegativeInteger, isPositiveId, isRecord } from './responseValidation'
import type { PageResponse } from './types'

/**
 * Member training reads for Flow 4 (NX-14 plans and coach notes, NX-15 attendance and results).
 *
 * PROVISIONAL CONTRACT. docs/nx-03 (F4-PLAN-04, F4-PROGRESS-04, F4-ATTEND-03, F4-RESULT-03) is an
 * unsigned draft: every route is TBD_AFTER_APPROVAL and no field list is approved. The paths and
 * shapes below are this screen's proposal, kept in this one file so that signing the contract
 * changes only this file. Nothing here is served by the backend yet.
 *
 * Privacy rules taken from the draft and enforced by the parsers: each parser copies only an
 * allowlist of fields, so a peer identity or a staff only note that a server sends by mistake is
 * dropped and never reaches the screen. Missing is kept apart from zero and from empty.
 */
export const MEMBER_TRAINING_ROUTES = {
  plans: '/members/me/training-plans',
  notes: '/members/me/coach-notes',
  attendance: '/members/me/attendance',
  results: '/members/me/training-results',
} as const

export type PlanAudience = 'PERSONAL' | 'CLASS'
/** UNRECORDED means the coach has not marked the session yet; it is not the same as ABSENT. */
export type AttendanceState = 'ATTENDED' | 'ABSENT' | 'UNRECORDED'

export interface TrainingPlanStep {
  order: number
  title: string
  details: string | null
}

export interface TrainingPlan {
  id: number
  title: string
  summary: string | null
  audience: PlanAudience
  coachName: string | null
  /** Revision shown to the member; later revisions replace it, earlier ones are not listed. */
  revision: number
  publishedAt: string | null
  steps: TrainingPlanStep[]
}

export interface CoachNote {
  id: number
  planId: number | null
  sessionId: number | null
  coachName: string | null
  comment: string
  createdAt: string | null
}

/** Session label fields shared by attendance and result rows. */
export interface TrainingSessionLabel {
  sessionId: number
  /** yyyy-MM-dd, the center's local calendar date. */
  sessionDate: string
  /** HH:mm[:ss], the center's local wall clock. */
  startTime: string | null
  endTime: string | null
  sportName: string | null
  className: string | null
  coachName: string | null
  facilityName: string | null
}

export interface AttendanceRecord extends TrainingSessionLabel {
  id: number
  state: AttendanceState
  /** Arrival time, only when the state is ATTENDED and the arrival was recorded. */
  arrivedAt: string | null
}

export interface ResultMetric {
  label: string
  /** null means not recorded. 0 is a real recorded value. */
  value: number | null
  unit: string | null
}

export interface TrainingResult extends TrainingSessionLabel {
  id: number
  headline: string | null
  metrics: ResultMetric[]
  /** Comment the coach chose to share with the member. */
  comment: string | null
  recordedAt: string | null
}

export interface HistoryQuery {
  page?: number
  size?: number
  signal?: AbortSignal
}

const UNEXPECTED = 'The server returned an unexpected training response. Please try again later.'
const AUDIENCES: PlanAudience[] = ['PERSONAL', 'CLASS']
const ATTENDANCE_STATES: AttendanceState[] = ['ATTENDED', 'ABSENT', 'UNRECORDED']

function bad(): never {
  throw new ApiError(0, UNEXPECTED)
}

function text(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value : null
}

function requireId(value: unknown): number {
  return isPositiveId(value) ? value : bad()
}

function optionalId(value: unknown): number | null {
  return value === null || value === undefined ? null : requireId(value)
}

function requireRecord(value: unknown): Record<string, unknown> {
  return isRecord(value) ? value : bad()
}

function parseStep(value: unknown): TrainingPlanStep {
  const step = requireRecord(value)
  const title = text(step.title) ?? bad()
  if (!isNonnegativeInteger(step.order)) bad()
  return { order: step.order as number, title, details: text(step.details) }
}

export function parseTrainingPlan(value: unknown): TrainingPlan {
  const plan = requireRecord(value)
  const title = text(plan.title) ?? bad()
  if (!AUDIENCES.includes(plan.audience as PlanAudience)) bad()
  if (!isPositiveId(plan.revision) || !Array.isArray(plan.steps)) bad()
  return {
    id: requireId(plan.id),
    title,
    summary: text(plan.summary),
    audience: plan.audience as PlanAudience,
    coachName: text(plan.coachName),
    revision: plan.revision as number,
    publishedAt: text(plan.publishedAt),
    steps: (plan.steps as unknown[]).map(parseStep).sort((a, b) => a.order - b.order),
  }
}

export function parseCoachNote(value: unknown): CoachNote {
  const note = requireRecord(value)
  return {
    id: requireId(note.id),
    planId: optionalId(note.planId),
    sessionId: optionalId(note.sessionId),
    coachName: text(note.coachName),
    comment: text(note.comment) ?? bad(),
    createdAt: text(note.createdAt),
  }
}

function parseSessionLabel(record: Record<string, unknown>): TrainingSessionLabel {
  const date = text(record.sessionDate)
  if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) bad()
  return {
    sessionId: requireId(record.sessionId),
    sessionDate: date as string,
    startTime: text(record.startTime),
    endTime: text(record.endTime),
    sportName: text(record.sportName),
    className: text(record.className),
    coachName: text(record.coachName),
    facilityName: text(record.facilityName),
  }
}

export function parseAttendanceRecord(value: unknown): AttendanceRecord {
  const record = requireRecord(value)
  if (!ATTENDANCE_STATES.includes(record.state as AttendanceState)) bad()
  const state = record.state as AttendanceState
  return {
    ...parseSessionLabel(record),
    id: requireId(record.id),
    state,
    arrivedAt: state === 'ATTENDED' ? text(record.arrivedAt) : null,
  }
}

function parseMetric(value: unknown): ResultMetric {
  const metric = requireRecord(value)
  const raw = metric.value
  if (raw !== null && raw !== undefined && (typeof raw !== 'number' || !Number.isFinite(raw))) bad()
  return { label: text(metric.label) ?? bad(), value: typeof raw === 'number' ? raw : null, unit: text(metric.unit) }
}

export function parseTrainingResult(value: unknown): TrainingResult {
  const record = requireRecord(value)
  if (record.metrics !== undefined && !Array.isArray(record.metrics)) bad()
  return {
    ...parseSessionLabel(record),
    id: requireId(record.id),
    headline: text(record.headline),
    metrics: ((record.metrics as unknown[] | undefined) ?? []).map(parseMetric),
    comment: text(record.comment),
    recordedAt: text(record.recordedAt),
  }
}

/** Accepts the PageResponse shape or a Spring page, and a bare array for short lists. */
function parseList<T>(source: unknown, parseRow: (row: unknown) => T): PageResponse<T> {
  if (Array.isArray(source)) {
    const content = source.map(parseRow)
    return { content, page: 0, size: content.length, totalElements: content.length, totalPages: content.length ? 1 : 0 }
  }
  const page = requireRecord(source)
  if (!Array.isArray(page.content)) bad()
  const content = (page.content as unknown[]).map(parseRow)
  const number = page.page ?? page.number ?? 0
  if (!isNonnegativeInteger(number) || !isNonnegativeInteger(page.totalElements) || !isNonnegativeInteger(page.totalPages)) bad()
  return {
    content,
    page: number as number,
    size: isPositiveId(page.size) ? (page.size as number) : Math.max(content.length, 1),
    totalElements: page.totalElements as number,
    totalPages: page.totalPages as number,
  }
}

function pageOptions({ page = 0, size = 10, signal }: HistoryQuery): Omit<RequestOptions, 'method' | 'body'> {
  return { signal, query: { page, size } }
}

/**
 * Every call is scoped to the signed in member by the server (the path has no member id), so the
 * screen can never ask for another member's rows.
 */
export const memberTrainingApi = {
  async plans(signal?: AbortSignal): Promise<TrainingPlan[]> {
    return parseList(await api.get<unknown>(MEMBER_TRAINING_ROUTES.plans, { signal }), parseTrainingPlan).content
  },
  async notes(signal?: AbortSignal): Promise<CoachNote[]> {
    return parseList(await api.get<unknown>(MEMBER_TRAINING_ROUTES.notes, { signal }), parseCoachNote).content
  },
  async attendance(query: HistoryQuery = {}): Promise<PageResponse<AttendanceRecord>> {
    return parseList(await api.get<unknown>(MEMBER_TRAINING_ROUTES.attendance, pageOptions(query)), parseAttendanceRecord)
  },
  async results(query: HistoryQuery = {}): Promise<PageResponse<TrainingResult>> {
    return parseList(await api.get<unknown>(MEMBER_TRAINING_ROUTES.results, pageOptions(query)), parseTrainingResult)
  },
}
