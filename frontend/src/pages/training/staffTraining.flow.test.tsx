import { screen, waitFor } from '@testing-library/react'
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
afterEach(() => vi.unstubAllGlobals())

describe('Coach training writes', () => {
  it('previews rule-based drills, then requires review and an explicit plan assignment', async () => {
    const draft = { title: 'Controlled short passing', goal: 'Passing accuracy', durationMinutes: 5, instructions: 'Use the target selected by your coach.', reason: 'Matches coach feedback focus: passing.', level: 'BEGINNER' }
    const { calls } = signedIn('COACH', call => {
      if (call.path === '/coach/training/classes') return json(200, [{ ...trainingClass, name: 'Football class' }])
      if (call.path === '/coach/training/classes/1/members') return json(200, [bookingCandidate])
      if (call.path === '/coach/training/plans' && call.method === 'GET') return json(200, [])
      if (call.path === '/coach/training/bookings/11/drill-suggestions') return json(200, { mode: 'RULE_BASED', ruleVersion: 'curated-drills-v1', requiresCoachReview: true, persisted: false, bookingId: 11, memberId: 42, classId: 1, sportName: 'Football', level: 'BEGINNER', drafts: [draft] })
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
      return unexpected(call)
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

describe('Reception training writes', () => {
  it.each(['COACH', 'RECEPTIONIST'] as const)('R07 shows server-closed dates without treating unmarked as absent for %s', async role => {
    const root = role === 'COACH' ? '/coach' : '/reception'
    const { calls } = signedIn(role, call => call.path === `${root}/training/sessions`
      ? json(200, [{ ...attendanceSession, attendanceDateOpen: false }])
      : call.path === `${root}/training/sessions/by-code/011126SW01/members` ? json(200, [rosterMember]) : unexpected(call))
    renderApp(`${root}/training/attendance`)
    await userEvent.click(await screen.findByRole('button', { name: /Select session 011126SW01/ }))
    expect(await screen.findByText(/Initial attendance is closed/)).toBeInTheDocument()
    await screen.findByRole('form', { name: /attendance for Lan/ })
    expect(await screen.findByText(/Attendance not recorded\./)).toBeInTheDocument()
    expect(screen.getByText(/An unmarked record does not mean absent/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save attendance' })).not.toBeInTheDocument()
    expect(calls.some(call => call.method === 'POST')).toBe(false)
  })
  it.each(['COACH', 'RECEPTIONIST'] as const)('R07 retains saved history and reasoned correction when initial date is closed for %s', async role => {
    const root = role === 'COACH' ? '/coach' : '/reception'
    signedIn(role, call => call.path === `${root}/training/sessions`
      ? json(200, [{ ...attendanceSession, attendanceDateOpen: false }])
      : call.path === `${root}/training/sessions/by-code/011126SW01/members` ? json(200, [{ ...rosterMember, attendanceId: 50, status: 'PRESENT', notes: 'Saved historical attendance' }]) : unexpected(call))
    renderApp(`${root}/training/attendance`)
    await userEvent.click(await screen.findByRole('button', { name: /Select session 011126SW01/ }))
    await screen.findByRole('form', { name: /attendance for Lan/ })
    expect(screen.getByLabelText(/Attendance note for Lan/)).toHaveValue('Saved historical attendance')
    expect(screen.getByLabelText(/Correction reason for Lan/)).toBeRequired()
    expect(screen.getByRole('button', { name: 'Save correction' })).toBeInTheDocument()
  })
  it('records attendance and requires an audit reason for a correction', async () => {
    let row = { ...rosterMember }
    const { calls } = signedIn('RECEPTIONIST', call => {
      if (call.path === '/reception/training/sessions') return json(200, [attendanceSession])
      if (call.path === '/reception/training/sessions/by-code/011126SW01/members') return json(200, [row])
      if (call.path === '/reception/training/attendance') { row = { ...row, attendanceId: 50, status: 'LATE', notes: 'Arrived late' }; return json(201, { id: 50 }) }
      if (call.path === '/reception/training/attendance/50') { row = { ...row, status: 'PRESENT' }; return json(200, { id: 50 }) }
      return unexpected(call)
    })
    renderApp('/reception/training/attendance')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Select session 011126SW01/ }))
    await screen.findByRole('form', { name: /attendance for Lan/ })
    await user.selectOptions(screen.getByLabelText(/Attendance status/), 'LATE')
    await user.type(screen.getByLabelText(/Attendance note for Lan/), 'Arrived late')
    await user.click(screen.getByRole('button', { name: 'Save attendance' }))
    expect(await screen.findByText('Attendance saved and the list updated.')).toBeInTheDocument()
    expect(calls.find(call => call.path === '/reception/training/attendance')?.body).toEqual({ bookingId: 11, status: 'LATE', notes: 'Arrived late' })
    await screen.findByRole('form', { name: /attendance for Lan/ })
    expect(screen.getByLabelText(/Attendance note for Lan/)).toHaveValue('Arrived late')
    await user.selectOptions(screen.getByLabelText(/Attendance status/), 'PRESENT')
    await user.click(screen.getByRole('button', { name: 'Save correction' }))
    expect(calls.some(call => call.method === 'PUT')).toBe(false)
    await user.type(screen.getByLabelText(/Correction reason for Lan/), 'Correct an incorrect record')
    await user.click(screen.getByRole('button', { name: 'Save correction' }))
    await waitFor(() => expect(calls.find(call => call.method === 'PUT')?.body).toEqual({ status: 'PRESENT', notes: 'Arrived late', reason: 'Correct an incorrect record' }))
  })

  it('blocks Member access to staff write routes before fetching staff data', async () => {
    const { calls } = signedIn('MEMBER', unexpected)
    renderApp('/reception/training/attendance')
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(calls.map(call => call.path)).toEqual(['/users/me'])
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
    const { calls } = signedIn('RECEPTIONIST', call => call.path === '/reception/training/sessions'
      ? json(200, [{ ...attendanceSession, attendanceDateOpen: false, attendanceState: 'UPCOMING' }])
      : call.path === '/reception/training/sessions/by-code/011126SW01/members' ? json(200, [rosterMember]) : unexpected(call))
    renderApp('/reception/training/attendance')
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
    const status = await screen.findByLabelText(/Attendance status for Lan/)
    expect([...status.querySelectorAll('option')].map(option => option.value)).toEqual(['PRESENT', 'LATE'])
    await userEvent.type(screen.getByLabelText(/Correction reason for Lan/), '   ')
    await userEvent.click(screen.getByRole('button', { name: 'Save correction' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('meaningful correction reason')
    expect(calls.some(call => call.method === 'PUT')).toBe(false)
  })

  it('guards duplicate per-row submissions, refreshes counts and restores focus after saving', async () => {
    let saved = false
    let finish: ((response: Response) => void) | undefined
    const { calls } = signedIn('RECEPTIONIST', call => {
      if (call.path === '/reception/training/sessions') return json(200, [{ ...attendanceSession, markedCount: saved ? 1 : 0 }])
      if (call.path === '/reception/training/sessions/by-code/011126SW01/members') return json(200, [{ ...rosterMember, attendanceId: saved ? 50 : null, status: saved ? 'PRESENT' : null }])
      if (call.path === '/reception/training/attendance') return new Promise<Response>(resolve => { finish = response => { saved = true; resolve(response) } })
      return unexpected(call)
    })
    renderApp('/reception/training/attendance')
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
    signedIn('RECEPTIONIST', call => call.path === '/reception/training/sessions' ? json(200, [attendanceSession])
      : call.path === '/reception/training/sessions/by-code/011126SW01/members' ? json(200, [rosterMember])
        : call.path === '/reception/training/attendance' ? backendError(409, 'Attendance changed. Refresh the roster.') : unexpected(call))
    renderApp('/reception/training/attendance')
    await userEvent.type(await screen.findByLabelText(/Attendance note for Lan/), 'Keep this note')
    await userEvent.click(screen.getByRole('button', { name: 'Save attendance' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Attendance changed')
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
