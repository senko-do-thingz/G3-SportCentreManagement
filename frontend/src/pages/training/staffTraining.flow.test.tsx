import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

const trainingClass = { id: 1, name: 'Beginner swimming', sportId: 1 }
const session = { id: 5, classId: 1, className: 'Beginner swimming', sportId: 1, date: '2026-11-01', startTime: '08:00:00', endTime: '09:00:00', trainingType: 'COACH_LED' }
const attendanceSession = { ...session, sessionId: '011126SW01', sportName: 'Swimming', coachName: 'An', facilityName: 'Pool 1', status: 'PUBLISHED', attendanceDateOpen: true, attendanceState: 'OPEN', bookedCount: 1, markedCount: 0 }
const bookingCandidate = { bookingId: 11, memberId: 42, memberName: 'Lan', sessionDate: '2026-11-01' }
const plan = { id: 7, title: 'Personal plan', description: null, classId: 1, className: 'Beginner swimming', memberId: 42, memberName: 'Lan', coachName: 'An' }
const rosterMember = { bookingId: 11, memberId: 42, memberName: 'Lan', attendanceId: null as number | null, status: null as string | null, notes: null as string | null }

function signedIn(role: 'COACH' | 'RECEPTIONIST' | 'MEMBER', handler: (call: RecordedCall) => Response | Promise<Response>) {
  tokenStorage.startSession({ accessToken: 'staff-token', refreshToken: 'refresh' })
  return installFakeFetch(call => call.path === '/users/me' ? json(200, { ...MEMBER, role }) : handler(call))
}
const unexpected = (call: RecordedCall) => backendError(404, `Unexpected endpoint: ${call.method} ${call.path}`)
// Member training context shown next to the drill inputs (F5-06): profile and recorded progress of member 42 in class 1.
const learnerProfile = { memberId: 42, memberCode: 'MEM-0042', memberName: 'Lan', mainGoal: 'BUILD_SKILLS', currentLevel: 'INTERMEDIATE' }
const learnerProgress = {
  learner: learnerProfile, interpretation: 'Chronological recorded outcomes.',
  sessions: [{ resultId: 3, sessionId: 5, sessionCode: '011126SW01', date: '2026-11-01', notes: null, attendanceStatus: 'PRESENT', notesOnly: false, scores: [{ metricId: 9, metricName: 'Kick technique', maxScore: 10, score: 4.5 }] }],
  feedback: [{ id: 1, planId: 7, planTitle: 'Personal plan', coachName: 'An', rating: 4, comments: 'Good effort', strengths: 'Breathing', areasForImprovement: 'Kick timing', createdAt: '2026-11-02T08:00:00' }],
}
const learnerContext = (call: RecordedCall) => call.path === '/coach/training/classes/1/members/42/profile' ? json(200, learnerProfile)
  : call.path === '/coach/training/classes/1/members/42/progress' ? json(200, learnerProgress) : null
afterEach(() => vi.unstubAllGlobals())

describe('Coach training writes', () => {
  it('previews rule-based drills, then requires review and an explicit plan assignment', async () => {
    const draft = { title: 'Controlled short passing', goal: 'Passing accuracy', durationMinutes: 5, instructions: 'Use the target selected by your coach.', reason: 'Matches coach feedback focus: passing.', level: 'BEGINNER' }
    const { calls } = signedIn('COACH', call => {
      if (call.path === '/coach/training/classes') return json(200, [{ ...trainingClass, name: 'Football class' }])
      if (call.path === '/coach/training/classes/1/members') return json(200, [bookingCandidate])
      if (call.path === '/coach/training/plans' && call.method === 'GET') return json(200, [])
      if (call.path === '/coach/training/bookings/11/drill-suggestions') return json(200, { mode: 'RULE_BASED', ruleVersion: 'curated-drills-v2', requiresCoachReview: true, persisted: false, bookingId: 11, memberId: 42, classId: 1, sportName: 'Football', level: 'BEGINNER', drafts: [draft] })
      if (call.path === '/coach/training/plans' && call.method === 'POST') return json(201, { id: 8 })
      return unexpected(call)
    })
    renderApp('/coach/training/plans')
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Assigned class'), '1')
    await user.selectOptions(await screen.findByLabelText(/Member booking/), '11')
    await user.click(screen.getByRole('button', { name: 'Suggest practice drills' }))
    expect(await screen.findByText('Matches coach feedback focus: passing.')).toBeInTheDocument()
    expect(calls.filter(call => call.method === 'POST')).toHaveLength(0)
    await user.click(screen.getByLabelText('Select draft: Controlled short passing'))
    await user.click(screen.getByRole('button', { name: 'Review selected drafts in plan form' }))
    expect(screen.getByLabelText(/Exercise title 1/)).toHaveValue(draft.title)
    await user.type(screen.getByLabelText(/Plan title/), 'Reviewed passing')
    await user.type(screen.getByLabelText('Exercise instructions 1'), ' Coach reviewed.')
    expect(calls.filter(call => call.method === 'POST')).toHaveLength(0)
    await user.click(screen.getByRole('button', { name: 'Assign plan' }))
    expect(await screen.findByText('Plan assigned. The member can view it immediately.')).toBeInTheDocument()
    expect(calls.find(call => call.method === 'POST')?.body).toMatchObject({ title: 'Reviewed passing', steps: [{ title: draft.title, durationMinutes: 5, instructions: draft.instructions + ' Coach reviewed.' }] })
  })
  it('shows the member training context and sends the Coach focus and session duration with the preview', async () => {
    const draft = { title: 'Flutter kick timing', goal: 'Kick technique', durationMinutes: 10, instructions: 'Kick along the wall, then reset.', reason: 'Kick technique scored 4.5/10.', level: 'INTERMEDIATE' }
    const { calls } = signedIn('COACH', call => {
      if (call.path === '/coach/training/classes') return json(200, [trainingClass])
      if (call.path === '/coach/training/classes/1/members') return json(200, [bookingCandidate])
      if (call.path === '/coach/training/plans' && call.method === 'GET') return json(200, [])
      if (call.path.startsWith('/coach/training/bookings/11/drill-suggestions')) return json(200, { mode: 'AI_GENERATED', ruleVersion: 'gemini-3.5-flash-lite', requiresCoachReview: true, persisted: false, bookingId: 11, memberId: 42, classId: 1, sportName: 'Swimming', level: 'INTERMEDIATE', drafts: [draft] })
      return learnerContext(call) ?? unexpected(call)
    })
    renderApp('/coach/training/plans')
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Assigned class'), '1')
    await user.selectOptions(await screen.findByLabelText(/Member booking/), '11')
    const context = await screen.findByRole('region', { name: 'Member training context' })
    expect(await within(context).findByText('Kick technique 4.5/10')).toBeInTheDocument()
    expect(within(context).getByText('Kick timing')).toBeInTheDocument()
    expect(within(context).getByText('Intermediate')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Current focus (optional)'), 'Kick timing')
    await user.type(screen.getByLabelText('Session duration in minutes (optional)'), '45')
    await user.click(screen.getByRole('button', { name: 'Suggest practice drills' }))
    expect(await screen.findByText('Select draft: Flutter kick timing')).toBeInTheDocument()
    expect(calls.find(call => call.path.startsWith('/coach/training/bookings/11/drill-suggestions'))?.path)
      .toBe('/coach/training/bookings/11/drill-suggestions?focus=Kick+timing&sessionMinutes=45')
  })
  it('rejects a session duration outside 15 to 180 minutes before calling the server', async () => {
    const { calls } = signedIn('COACH', call => {
      if (call.path === '/coach/training/classes') return json(200, [trainingClass])
      if (call.path === '/coach/training/classes/1/members') return json(200, [bookingCandidate])
      if (call.path === '/coach/training/plans' && call.method === 'GET') return json(200, [])
      return learnerContext(call) ?? unexpected(call)
    })
    renderApp('/coach/training/plans')
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Assigned class'), '1')
    await user.selectOptions(await screen.findByLabelText(/Member booking/), '11')
    await user.type(screen.getByLabelText('Session duration in minutes (optional)'), '200')
    await user.click(screen.getByRole('button', { name: 'Suggest practice drills' }))
    expect(await screen.findByText('Session duration must be a whole number between 15 and 180 minutes.')).toBeInTheDocument()
    expect(calls.some(call => call.path.includes('drill-suggestions'))).toBe(false)
  })
  it('drafts and assigns a whole-class plan from class-level suggestions with an idempotency key', async () => {
    const draft = { title: 'Group relay kicks', goal: 'Kick technique', durationMinutes: 15, instructions: 'Relay along the lane.', reason: 'Lowest class average: kick technique.', level: 'BEGINNER' }
    const { calls, fetchMock } = signedIn('COACH', call => {
      if (call.path === '/coach/training/classes') return json(200, [trainingClass])
      if (call.path === '/coach/training/plans' && call.method === 'GET') return json(200, [])
      if (call.path.startsWith('/coach/training/classes/1/drill-suggestions')) return json(200, { mode: 'AI_GENERATED', ruleVersion: 'gemini-3.5-flash-lite', requiresCoachReview: true, persisted: false, classId: 1, sportName: 'Swimming', level: 'BEGINNER', memberCount: 6, drafts: [draft] })
      if (call.path === '/coach/training/class-plans' && call.method === 'POST') return json(201, { plan: { id: 30 }, steps: [] })
      return unexpected(call)
    })
    renderApp('/coach/training/plans')
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Plan scope'), 'CLASS')
    await user.selectOptions(screen.getByLabelText('Assigned class'), '1')
    expect(screen.getByText(/No individual member data is used/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Suggest practice drills' }))
    expect(await screen.findByText('Based on 6 members with a confirmed booking in this class.')).toBeInTheDocument()
    await user.click(screen.getByLabelText('Select draft: Group relay kicks'))
    await user.click(screen.getByRole('button', { name: 'Review selected drafts in plan form' }))
    await user.type(screen.getByLabelText(/Plan title/), 'Week 3 class plan')
    expect(calls.filter(call => call.method === 'POST')).toHaveLength(0)
    await user.click(screen.getByRole('button', { name: 'Assign class plan' }))
    expect(await screen.findByText('Class plan assigned. Members with a confirmed booking in this class can view it.')).toBeInTheDocument()
    const post = calls.find(call => call.method === 'POST' && call.path === '/coach/training/class-plans')
    expect(post?.body).toMatchObject({ classId: 1, title: 'Week 3 class plan', steps: [{ title: draft.title, durationMinutes: 15, instructions: draft.instructions }] })
    const keys = fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST').map(([, init]) => new Headers(init?.headers).get('Idempotency-Key'))
    expect(keys).toHaveLength(1)
    expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/)
  })
  it('labels AI drafts, shows why each fits the member, and still assigns nothing until the Coach reviews', async () => {
    const draft = { title: 'Six-corner shadow footwork', goal: 'Footwork', durationMinutes: 10, instructions: 'Shadow the six corners, then reset.', reason: 'Footwork scored 4.5/10 and is the latest area for improvement.', level: 'ADVANCED' }
    const { calls } = signedIn('COACH', call => {
      if (call.path === '/coach/training/classes') return json(200, [{ ...trainingClass, name: 'Badminton class' }])
      if (call.path === '/coach/training/classes/1/members') return json(200, [bookingCandidate])
      if (call.path === '/coach/training/plans' && call.method === 'GET') return json(200, [])
      if (call.path === '/coach/training/bookings/11/drill-suggestions') return json(200, { mode: 'AI_GENERATED', ruleVersion: 'gemini-3.5-flash-lite', requiresCoachReview: true, persisted: false, bookingId: 11, memberId: 42, classId: 1, sportName: 'Badminton', level: 'ADVANCED', drafts: [draft] })
      return unexpected(call)
    })
    renderApp('/coach/training/plans')
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Assigned class'), '1')
    await user.selectOptions(await screen.findByLabelText(/Member booking/), '11')
    await user.click(screen.getByRole('button', { name: 'Suggest practice drills' }))
    expect(await screen.findByRole('note')).toHaveTextContent('AI draft generated by gemini-3.5-flash-lite for review. Check every exercise for safety and suitability before assigning. Drafts are not saved.')
    expect(screen.getByText(draft.reason)).toBeInTheDocument()
    expect(screen.getByText('Footwork - ADVANCED - 10 minutes')).toBeInTheDocument()
    await user.click(screen.getByLabelText('Select draft: Six-corner shadow footwork'))
    await user.click(screen.getByRole('button', { name: 'Review selected drafts in plan form' }))
    expect(screen.getByLabelText(/Exercise title 1/)).toHaveValue(draft.title)
    expect(calls.filter(call => call.method === 'POST')).toHaveLength(0)
  })
  it('clears drafts and every plan field when the Coach switches to another member booking, and surfaces duplicate rejection', async () => {
    const draft = { title: 'Walking shadow footwork', goal: 'Court movement', durationMinutes: 5, instructions: 'Walk between court positions.', reason: 'Matches the Badminton class at beginner level. Coach review is required.', level: 'BEGINNER' }
    const otherBooking = { bookingId: 12, memberId: 43, memberName: 'Minh', sessionDate: '2026-11-01' }
    const { calls } = signedIn('COACH', call => {
      if (call.path === '/coach/training/classes') return json(200, [{ ...trainingClass, name: 'Badminton class' }])
      if (call.path === '/coach/training/classes/1/members') return json(200, [bookingCandidate, otherBooking])
      if (call.path === '/coach/training/plans' && call.method === 'GET') return json(200, [])
      if (call.path === '/coach/training/bookings/11/drill-suggestions') return json(200, { mode: 'RULE_BASED', ruleVersion: 'curated-drills-v2', requiresCoachReview: true, persisted: false, bookingId: 11, memberId: 42, classId: 1, sportName: 'Badminton', level: 'BEGINNER', drafts: [draft] })
      if (call.path === '/coach/training/plans' && call.method === 'POST') return backendError(409, 'A plan with this title already exists for this booking; choose a distinct title for a new plan')
      return unexpected(call)
    })
    renderApp('/coach/training/plans')
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Assigned class'), '1')
    await user.selectOptions(await screen.findByLabelText(/Member booking/), '11')
    await user.click(screen.getByRole('button', { name: 'Suggest practice drills' }))
    expect(await screen.findByText('Mode: RULE_BASED, rule version curated-drills-v2. Drafts are not saved.')).toBeInTheDocument()
    await user.click(screen.getByLabelText('Select draft: Walking shadow footwork'))
    await user.click(screen.getByRole('button', { name: 'Review selected drafts in plan form' }))
    expect(screen.getByText('1 draft copied into the exercise fields below. Edit them, then select Assign plan. Nothing is assigned yet.')).toBeInTheDocument()
    await user.type(screen.getByLabelText(/Plan title/), 'Lan footwork plan')
    await user.type(screen.getByLabelText('Plan description'), 'For Lan only')
    expect(screen.getByLabelText(/Exercise title 1/)).toHaveValue(draft.title)
    expect(calls.filter(call => call.method === 'POST')).toHaveLength(0)

    await user.selectOptions(screen.getByLabelText(/Member booking/), '12')
    expect(screen.getByLabelText(/Plan title/)).toHaveValue('')
    expect(screen.getByLabelText('Plan description')).toHaveValue('')
    expect(screen.getByLabelText(/Exercise title 1/)).toHaveValue('')
    expect(screen.queryByText('Select draft: Walking shadow footwork')).not.toBeInTheDocument()
    expect(screen.queryByText(/copied into the exercise fields/)).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText(/Member booking/), '11')
    await user.type(screen.getByLabelText(/Plan title/), 'Lan footwork plan')
    await user.type(screen.getByLabelText(/Exercise title 1/), 'Warm-up')
    await user.click(screen.getByRole('button', { name: 'Assign plan' }))
    expect(await screen.findByText(/already exists for this booking/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Plan title/)).toHaveValue('Lan footwork plan')
    expect(calls.filter(call => call.method === 'POST')).toHaveLength(1)
  })
  it('assigns an individual plan then publishes plan feedback without waiting for a session result', async () => {
    let created = false
    const { calls } = signedIn('COACH', call => {
      if (call.path === '/coach/training/classes') return json(200, [trainingClass])
      if (call.path === '/coach/training/classes/1/members') return json(200, [bookingCandidate])
      if (call.path === '/coach/training/plans' && call.method === 'GET') return json(200, created ? [plan] : [])
      if (call.path === '/coach/training/plans' && call.method === 'POST') { created = true; return json(201, { id: 7 }) }
      if (call.path === '/coach/training/plans/7') return json(200, { plan, steps: [{ id: 1, stepOrder: 1, title: 'Warm-up', durationMinutes: 10, instructions: null }] })
      if (call.path === '/coach/training/plans/7/feedback') return json(201, { id: 12 })
      return unexpected(call)
    })
    renderApp('/coach/training/plans')
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Assigned class'), '1')
    await user.selectOptions(await screen.findByLabelText(/Member booking/), '11')
    await user.type(screen.getByLabelText(/Plan title/), 'Personal plan')
    await user.type(screen.getByLabelText(/Exercise title 1/), 'Warm-up')
    await user.click(screen.getByRole('button', { name: 'Assign plan' }))
    expect(await screen.findByText('Plan assigned. The member can view it immediately.')).toBeInTheDocument()
    expect(calls.find(call => call.method === 'POST' && call.path === '/coach/training/plans')?.body).toMatchObject({
      bookingId: 11, classId: 1, memberId: 42, title: 'Personal plan', steps: [{ title: 'Warm-up', durationMinutes: 10 }],
    })
    await user.selectOptions(screen.getByLabelText('Assigned plans'), '7')
    await user.type(await screen.findByLabelText('Feedback'), 'Good progress')
    await user.click(screen.getByRole('button', { name: 'Save feedback' }))
    expect(await screen.findByText('Plan feedback saved. The member can view it immediately.')).toBeInTheDocument()
    expect(calls.find(call => call.path.endsWith('/7/feedback'))?.body).toMatchObject({ rating: 4, comments: 'Good progress' })
    expect(calls.some(call => call.path.includes('/results'))).toBe(false)
  })

  it('keeps plan inputs when the backend rejects a cancelled booking', async () => {
    signedIn('COACH', call => {
      if (call.path === '/coach/training/classes') return json(200, [trainingClass])
      if (call.path === '/coach/training/classes/1/members') return json(200, [bookingCandidate])
      if (call.path === '/coach/training/plans' && call.method === 'GET') return json(200, [])
      if (call.path === '/coach/training/plans' && call.method === 'POST') return backendError(409, 'Booking has been cancelled')
      return learnerContext(call) ?? unexpected(call)
    })
    renderApp('/coach/training/plans')
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Assigned class'), '1')
    await user.selectOptions(await screen.findByLabelText(/Member booking/), '11')
    await user.type(screen.getByLabelText(/Plan title/), 'Unsaved plan')
    await user.type(screen.getByLabelText(/Exercise title 1/), 'Warm-up')
    await user.click(screen.getByRole('button', { name: 'Assign plan' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Booking has been cancelled')
    expect(screen.getByLabelText(/Plan title/)).toHaveValue('Unsaved plan')
    expect(screen.getByRole('button', { name: 'Assign plan' })).toBeEnabled()
  })

  it('saves notes-only results and excludes absent members from the picker', async () => {
    const { calls } = signedIn('COACH', call => {
      if (call.path === '/coach/training/sessions') return json(200, [session])
      if (call.path === '/coach/training/sessions/5/members') return json(200, [
        { ...rosterMember, status: 'PRESENT', attendanceId: 20 },
        { ...rosterMember, bookingId: 12, memberId: 99, memberName: 'Minh', status: 'ABSENT', attendanceId: 21 },
      ])
      if (call.path === '/coach/training/metrics?sportId=1') return json(200, [])
      if (call.path === '/coach/training/results') return json(201, { id: 9 })
      return unexpected(call)
    })
    renderApp('/coach/training/results')
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Assigned session'), '5')
    await user.selectOptions(await screen.findByLabelText(/Member receiving results/), '42')
    expect(screen.queryByRole('option', { name: /Minh/ })).not.toBeInTheDocument()
    expect(screen.getByText(/No skill metrics are configured/)).toBeInTheDocument()
    await user.type(screen.getByLabelText('Result notes'), 'Exercises completed')
    await user.click(screen.getByRole('button', { name: 'Save results' }))
    expect(await screen.findByText('Training results saved. The member can view them immediately.')).toBeInTheDocument()
    expect(screen.getByText('Results already saved')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save results' })).not.toBeInTheDocument()
    expect(calls.find(call => call.path === '/coach/training/results')?.body).toEqual({ sessionId: 5, memberId: 42, notes: 'Exercises completed', scores: [] })
  })

  it('uses the configured metric scale and blocks out-of-range scores before submission', async () => {
    const { calls } = signedIn('COACH', call => {
      if (call.path === '/coach/training/sessions') return json(200, [session])
      if (call.path === '/coach/training/sessions/5/members') return json(200, [{ ...rosterMember, status: 'LATE', attendanceId: 20 }])
      if (call.path === '/coach/training/metrics?sportId=1') return json(200, [{ id: 3, name: 'Endurance', maxScore: 100 }])
      if (call.path === '/coach/training/results') return json(201, { id: 9 })
      return unexpected(call)
    })
    renderApp('/coach/training/results')
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Assigned session'), '5')
    await user.selectOptions(await screen.findByLabelText(/Member receiving results/), '42')
    const score = screen.getByLabelText('Endurance (0-100)')
    await user.type(score, '101')
    await user.click(screen.getByRole('button', { name: 'Save results' }))
    expect(calls.filter(call => call.method === 'POST')).toHaveLength(0)
    await user.clear(score)
    await user.type(score, '75.5')
    await user.click(screen.getByRole('button', { name: 'Save results' }))
    await waitFor(() => expect(calls.find(call => call.path === '/coach/training/results')?.body).toMatchObject({ scores: [{ metricId: 3, score: 75.5 }] }))
  })
})

describe('Coach session attendance writes and role boundaries', () => {
  it('R07 shows server-closed dates without treating unmarked as absent for COACH', async () => {
    const { calls } = signedIn('COACH', call => call.path === '/coach/training/sessions'
      ? json(200, [{ ...attendanceSession, attendanceDateOpen: false }])
      : call.path === '/coach/training/sessions/by-code/011126SW01/members' ? json(200, [rosterMember]) : unexpected(call))
    renderApp('/coach/training/attendance')
    await userEvent.click(await screen.findByRole('button', { name: /Select session 011126SW01/ }))
    expect(await screen.findByText(/Initial attendance is closed/)).toBeInTheDocument()
    await screen.findByRole('form', { name: /attendance for Lan/ })
    expect(await screen.findByText(/Attendance not recorded\./)).toBeInTheDocument()
    expect(screen.getByText(/An unmarked record does not mean absent/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save attendance' })).not.toBeInTheDocument()
    expect(calls.some(call => call.method === 'POST')).toBe(false)
  })
  it('R07 retains saved history and reasoned correction when initial date is closed for COACH', async () => {
    signedIn('COACH', call => call.path === '/coach/training/sessions'
      ? json(200, [{ ...attendanceSession, attendanceDateOpen: false }])
      : call.path === '/coach/training/sessions/by-code/011126SW01/members' ? json(200, [{ ...rosterMember, attendanceId: 50, status: 'PRESENT', notes: 'Saved historical attendance' }]) : unexpected(call))
    renderApp('/coach/training/attendance')
    await userEvent.click(await screen.findByRole('button', { name: /Select session 011126SW01/ }))
    await screen.findByRole('form', { name: /attendance for Lan/ })
    expect(screen.getByLabelText(/Attendance note for Lan/)).toHaveValue('Saved historical attendance')
    expect(screen.getByLabelText(/Correction reason for Lan/)).toBeRequired()
    expect(screen.getByRole('button', { name: 'Save correction' })).toBeInTheDocument()
  })
  it('records attendance and requires an audit reason for a correction', async () => {
    let row = { ...rosterMember }
    const { calls } = signedIn('COACH', call => {
      if (call.path === '/coach/training/sessions') return json(200, [attendanceSession])
      if (call.path === '/coach/training/sessions/by-code/011126SW01/members') return json(200, [row])
      if (call.path === '/coach/training/attendance') { row = { ...row, attendanceId: 50, status: 'LATE', notes: 'Arrived late' }; return json(201, { id: 50 }) }
      if (call.path === '/coach/training/attendance/50') { row = { ...row, status: 'PRESENT' }; return json(200, { id: 50 }) }
      return unexpected(call)
    })
    renderApp('/coach/training/attendance')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Select session 011126SW01/ }))
    await screen.findByRole('form', { name: /attendance for Lan/ })
    await user.click(screen.getByRole('radio', { name: 'Late' }))
    await user.type(screen.getByLabelText(/Attendance note for Lan/), 'Arrived late')
    await user.click(screen.getByRole('button', { name: 'Save attendance' }))
    expect(await screen.findByText('Attendance saved and the list updated.')).toBeInTheDocument()
    expect(calls.find(call => call.path === '/coach/training/attendance')?.body).toEqual({ bookingId: 11, status: 'LATE', notes: 'Arrived late' })
    await screen.findByRole('form', { name: /attendance for Lan/ })
    expect(screen.getByLabelText(/Attendance note for Lan/)).toHaveValue('Arrived late')
    await user.click(screen.getByRole('radio', { name: 'Present' }))
    await user.click(screen.getByRole('button', { name: 'Save correction' }))
    expect(calls.some(call => call.method === 'PUT')).toBe(false)
    await user.type(screen.getByLabelText(/Correction reason for Lan/), 'Correct an incorrect record')
    await user.click(screen.getByRole('button', { name: 'Save correction' }))
    await waitFor(() => expect(calls.find(call => call.method === 'PUT')?.body).toEqual({ status: 'PRESENT', notes: 'Arrived late', reason: 'Correct an incorrect record' }))
  })

  it.each([
    ['MEMBER', '/coach/training/attendance'],
    ['RECEPTIONIST', '/coach/training/attendance'],
    ['RECEPTIONIST', '/reception/training/attendance'],
  ] as const)('blocks %s access to %s before fetching session attendance data', async (role, path) => {
    const { calls } = signedIn(role, unexpected)
    renderApp(path)
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    // MW-07: the receptionist inbox badge polls its own inbox; no attendance data may be fetched.
    expect(calls.map(call => call.path).filter(path => !path.startsWith('/reception/inbox'))).toEqual(['/users/me'])
  })

  it('shows canonical identity and context, supports keyboard selection and searches by facility', async () => {
    signedIn('COACH', call => call.path === '/coach/training/sessions' ? json(200, [attendanceSession])
      : call.path === '/coach/training/sessions/by-code/011126SW01/members' ? json(200, [rosterMember]) : unexpected(call))
    renderApp('/coach/training/attendance')
    const user = userEvent.setup()
    const card = await screen.findByRole('button', { name: /Select session 011126SW01/ })
    card.focus()
    await user.keyboard('{Enter}')
    expect(card).toHaveAttribute('aria-pressed', 'true')
    await user.type(screen.getByRole('searchbox', { name: 'Search sessions' }), 'Pool 1')
    expect(screen.getByRole('button', { name: /Select session 011126SW01/ })).toBeInTheDocument()
    expect(screen.getAllByText('01/11/2026 08:00 - 09:00').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Facility: Pool 1').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Coach: An').length).toBeGreaterThan(0)
    expect(screen.getByText('Attendance to record today')).toBeInTheDocument()
    expect(screen.queryByLabelText('Booking to mark attendance')).not.toBeInTheDocument()
    expect(await screen.findByLabelText(/Attendance note for Lan/)).toHaveProperty('type', 'text')
    expect(screen.getByLabelText(/Attendance note for Lan/)).toHaveAttribute('maxlength', '500')
  })

  it('labels a server-upcoming session and never offers a first-marking write', async () => {
    const { calls } = signedIn('COACH', call => call.path === '/coach/training/sessions'
      ? json(200, [{ ...attendanceSession, attendanceDateOpen: false, attendanceState: 'UPCOMING' }])
      : call.path === '/coach/training/sessions/by-code/011126SW01/members' ? json(200, [rosterMember]) : unexpected(call))
    renderApp('/coach/training/attendance')
    await userEvent.click(await screen.findByRole('button', { name: /Select session 011126SW01/ }))
    expect(screen.getByText('Upcoming - first marking is not open')).toBeInTheDocument()
    expect(await screen.findByText(/Attendance not recorded\./)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save attendance' })).not.toBeInTheDocument()
    expect(calls.some(call => call.method === 'POST')).toBe(false)
  })

  it('retains result restrictions and rejects a whitespace correction reason without writing', async () => {
    const { calls } = signedIn('COACH', call => call.path === '/coach/training/sessions' ? json(200, [{ ...attendanceSession, markedCount: 1 }])
      : call.path === '/coach/training/sessions/by-code/011126SW01/members' ? json(200, [{ ...rosterMember, attendanceId: 50, resultId: 9, status: 'PRESENT' }]) : unexpected(call))
    renderApp('/coach/training/attendance')
    await userEvent.click(await screen.findByRole('button', { name: /Select session 011126SW01/ }))
    const status = await screen.findByRole('group', { name: /Attendance status for Lan/ })
    expect(within(status).getAllByRole('radio').map(option => option.getAttribute('value'))).toEqual(['PRESENT', 'LATE'])
    await userEvent.type(screen.getByLabelText(/Correction reason for Lan/), '   ')
    await userEvent.click(screen.getByRole('button', { name: 'Save correction' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('meaningful correction reason')
    expect(calls.some(call => call.method === 'PUT')).toBe(false)
  })

  it('guards duplicate per-row submissions, refreshes counts and restores focus after saving', async () => {
    let saved = false
    let finish: ((response: Response) => void) | undefined
    const { calls } = signedIn('COACH', call => {
      if (call.path === '/coach/training/sessions') return json(200, [{ ...attendanceSession, markedCount: saved ? 1 : 0 }])
      if (call.path === '/coach/training/sessions/by-code/011126SW01/members') return json(200, [{ ...rosterMember, attendanceId: saved ? 50 : null, status: saved ? 'PRESENT' : null }])
      if (call.path === '/coach/training/attendance') return new Promise<Response>(resolve => { finish = response => { saved = true; resolve(response) } })
      return unexpected(call)
    })
    renderApp('/coach/training/attendance')
    const button = await screen.findByRole('button', { name: 'Save attendance' })
    await userEvent.dblClick(button)
    expect(calls.filter(call => call.method === 'POST')).toHaveLength(1)
    expect(button).toBeDisabled()
    finish!(json(201, { id: 50 }))
    expect(await screen.findByRole('button', { name: 'Save correction' })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save correction' })).toHaveFocus())
    expect(screen.getByText('1 of 1 marked')).toBeInTheDocument()
  })

  it('keeps per-row input and feedback after a stale write conflict', async () => {
    signedIn('COACH', call => call.path === '/coach/training/sessions' ? json(200, [attendanceSession])
      : call.path === '/coach/training/sessions/by-code/011126SW01/members' ? json(200, [rosterMember])
        : call.path === '/coach/training/attendance' ? backendError(409, 'Attendance changed. Refresh the roster.') : unexpected(call))
    renderApp('/coach/training/attendance')
    await userEvent.type(await screen.findByLabelText(/Attendance note for Lan/), 'Keep this note')
    await userEvent.click(screen.getByRole('button', { name: 'Save attendance' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Attendance changed')
    expect(screen.getByRole('alert')).toHaveClass('notice--warning')
    expect(screen.getByText('Record changed')).toBeInTheDocument()
    expect(screen.getByLabelText(/Attendance note for Lan/)).toHaveValue('Keep this note')
    expect(screen.getByRole('button', { name: 'Refresh roster' })).toBeEnabled()
  })
})

 it('ID-04 preserves the public identity through a notes-only result write', async () => {
   const calls = signedIn('COACH', call => {
     if (call.path === '/coach/training/sessions') return json(200, [attendanceSession])
     if (call.path === '/coach/training/sessions/by-code/011126SW01/members') return json(200, [{ ...rosterMember, status: 'LATE', attendanceId: 20 }])
     if (call.path === '/coach/training/metrics?sportId=1') return json(200, [])
     if (call.path === '/coach/training/sessions/by-code/011126SW01/results') return json(201, { id: 9 })
     return unexpected(call)
   })
   renderApp('/coach/training/results')
   const user = userEvent.setup()
   await user.selectOptions(await screen.findByLabelText('Assigned session'), '011126SW01')
   await user.selectOptions(await screen.findByLabelText(/Member receiving results/), '42')
   expect(screen.getByText('011126SW01')).toBeInTheDocument()
   await user.type(screen.getByLabelText('Result notes'), 'Code-linked result')
   await user.click(screen.getByRole('button', { name: 'Save results' }))
   expect(await screen.findByText('Results already saved')).toBeInTheDocument()
   expect(calls.calls.find(call => call.method === 'POST')?.body).toEqual({ memberId: 42, notes: 'Code-linked result', scores: [] })
 })
