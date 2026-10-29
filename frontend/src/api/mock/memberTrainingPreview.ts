import type { RequestOptions } from '../client'
import { MEMBER_TRAINING_ROUTES, type AttendanceRecord, type CoachNote, type TrainingPlan, type TrainingResult } from '../memberTrainingApi'
import { browseAddDays, browseToday } from '../../pages/member/memberScheduleDates'

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const plans: TrainingPlan[] = [
  {
    id: 1, title: 'Basketball fundamentals, weeks 1 to 4', summary: 'Build ball control and shooting form before moving to game drills.',
    audience: 'CLASS', coachName: 'Daniel Smith', revision: 2, publishedAt: '2026-10-20T09:00:00',
    steps: [
      { order: 1, title: 'Dribbling warm-up', details: 'Ten minutes of stationary and moving dribbles with both hands.' },
      { order: 2, title: 'Form shooting', details: 'Five spots, ten shots each, focus on elbow alignment.' },
      { order: 3, title: 'Cool-down', details: null },
    ],
  },
  {
    id: 2, title: 'Personal conditioning block', summary: null, audience: 'PERSONAL', coachName: 'Chris Nguyen', revision: 1,
    publishedAt: '2026-10-25T16:30:00',
    steps: [{ order: 1, title: 'Interval runs', details: 'Six rounds of 200 m with 60 seconds rest.' }],
  },
]

const notes: CoachNote[] = [
  { id: 1, planId: 1, sessionId: null, coachName: 'Daniel Smith', comment: 'Good progress on your left-hand dribble. Keep your head up during drills.', createdAt: '2026-10-27T18:10:00' },
  { id: 2, planId: null, sessionId: 902, coachName: 'Chris Nguyen', comment: 'Please arrive ten minutes early next week so we can start the warm-up on time.', createdAt: '2026-10-26T11:00:00' },
]

function attendance(): AttendanceRecord[] {
  const today = browseToday()
  const states: AttendanceRecord['state'][] = ['ATTENDED', 'ATTENDED', 'ABSENT', 'ATTENDED', 'UNRECORDED', 'ATTENDED']
  return Array.from({ length: 12 }, (_, index) => {
    const state = index === 0 ? 'UNRECORDED' : states[index % states.length]
    const sessionDate = browseAddDays(today, -(index + 1) * 2)
    return {
      id: 500 + index, sessionId: 900 + index, sessionDate,
      startTime: `${String(8 + (index % 6)).padStart(2, '0')}:00:00`, endTime: `${String(9 + (index % 6)).padStart(2, '0')}:00:00`,
      sportName: 'Basketball', className: index % 3 === 0 ? 'Basketball Foundations' : index % 3 === 1 ? 'Basketball Skills Lab' : null,
      coachName: index % 3 === 2 ? null : 'Daniel Smith', facilityName: 'Training Court A',
      state, arrivedAt: state === 'ATTENDED' ? `${sessionDate}T${String(7 + (index % 6)).padStart(2, '0')}:55:00` : null,
    }
  })
}

function results(): TrainingResult[] {
  const today = browseToday()
  return [
    {
      id: 700, sessionId: 901, sessionDate: browseAddDays(today, -4), startTime: '09:00:00', endTime: '10:00:00', sportName: 'Basketball',
      className: 'Basketball Skills Lab', coachName: 'Daniel Smith', facilityName: 'Training Court A', headline: 'Free-throw accuracy improved',
      metrics: [{ label: 'Free throws made', value: 14, unit: 'of 20' }, { label: 'Turnovers', value: 0, unit: null }, { label: 'Sprint time', value: null, unit: 's' }],
      comment: 'Steady session. Work on the follow-through at home.', recordedAt: `${browseAddDays(today, -4)}T10:20:00`,
    },
    {
      id: 701, sessionId: 903, sessionDate: browseAddDays(today, -8), startTime: '08:00:00', endTime: '09:00:00', sportName: 'Basketball',
      className: 'Basketball Foundations', coachName: 'Daniel Smith', facilityName: 'Training Court A', headline: null,
      metrics: [], comment: null, recordedAt: `${browseAddDays(today, -8)}T09:15:00`,
    },
  ]
}

function page<T>(rows: T[], options: RequestOptions) {
  const query = options.query ?? {}
  const number = Number(query.page ?? 0), size = Number(query.size ?? 10)
  return { content: rows.slice(number * size, (number + 1) * size), page: number, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) }
}

/** Read-only fixtures for the member training screens behind the existing DEV flag and the synthetic MEMBER token. */
export function memberTrainingPreview(path: string, options: RequestOptions, scenario: string | null): Response | undefined {
  if ((options.method ?? 'GET') !== 'GET') return undefined
  const known = Object.values(MEMBER_TRAINING_ROUTES) as string[]
  if (!known.includes(path)) return undefined
  if (scenario === 'error') return json(503, { status: 503, message: 'The member preview service is temporarily unavailable.' })
  const empty = scenario === 'empty'
  if (path === MEMBER_TRAINING_ROUTES.plans) return json(200, empty ? [] : plans)
  if (path === MEMBER_TRAINING_ROUTES.notes) return json(200, empty ? [] : notes)
  if (path === MEMBER_TRAINING_ROUTES.attendance) return json(200, page(empty ? [] : attendance(), options))
  return json(200, page(empty ? [] : results(), options))
}
