import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

afterEach(() => vi.unstubAllGlobals())

const drafts = [
  { title: 'Warm-up and mobility', goal: 'Preparation', durationMinutes: 15, instructions: 'Easy movement.', reason: 'Prepare movement.', level: 'BEGINNER' },
  { title: 'First-touch passing', goal: 'Ball control', durationMinutes: 25, instructions: 'Short passes.', reason: 'Current skill focus.', level: 'BEGINNER' },
]

function coachApp() {
  tokenStorage.startSession({ accessToken: 'staff-token', refreshToken: 'refresh' })
  return installFakeFetch((call: RecordedCall) => {
    if (call.path === '/users/me') return json(200, { ...MEMBER, role: 'COACH' })
    if (call.path === '/coach/training/classes') return json(200, [{ id: 1, name: 'Football Fundamentals', sportId: 1 }])
    if (call.path === '/coach/training/classes/1/members') return json(200, [{ bookingId: 11, memberId: 42, memberName: 'Taylor', sessionDate: '2026-11-05' }])
    if (call.path === '/coach/training/plans' && call.method === 'GET') return json(200, [])
    if (call.path.startsWith('/coach/training/bookings/11/drill-suggestions')) return json(200, { mode: 'AI_GENERATED', ruleVersion: 'gemini-flash-lite-latest', requiresCoachReview: true, persisted: false, bookingId: 11, memberId: 42, classId: 1, sportName: 'Football', level: 'BEGINNER', drafts })
    if (call.path === '/coach/training/plans' && call.method === 'POST') return json(201, { id: 9 })
    return backendError(404, 'Not used in this test')
  })
}

it('F5-07 reviews drafts in a table, carries Coach adjustments into the plan and can revise the inputs', async () => {
  const { calls } = coachApp()
  const user = userEvent.setup()
  renderApp('/coach/training/plans')
  await user.selectOptions(await screen.findByLabelText('Assigned class'), '1')
  await user.selectOptions(await screen.findByLabelText(/Member booking/), '11')
  await user.type(screen.getByLabelText('Session duration in minutes (optional)'), '90')
  await user.click(screen.getByRole('button', { name: 'Suggest practice drills' }))
  const table = await screen.findByRole('table', { name: 'Suggested exercises for review' })
  expect(within(table).getByText('Current skill focus.')).toBeInTheDocument()
  expect(within(table).getByText('25 min')).toBeInTheDocument()

  await user.click(screen.getByLabelText('Select draft: Warm-up and mobility'))
  await user.click(screen.getByLabelText('Select draft: First-touch passing'))
  expect(screen.getByText('Selected: 2 exercises, 40 minutes of a 90-minute session.')).toBeInTheDocument()
  await user.type(screen.getByLabelText('Coach adjustments (optional)'), 'Keep beginner intensity')
  await user.click(screen.getByRole('button', { name: 'Review selected drafts in plan form' }))
  expect(screen.getByLabelText('Plan description')).toHaveValue('Coach adjustments: Keep beginner intensity')
  expect(screen.getByLabelText(/Exercise title 2/)).toHaveValue('First-touch passing')

  await user.click(screen.getByRole('button', { name: 'Revise inputs' }))
  expect(screen.queryByRole('table', { name: 'Suggested exercises for review' })).not.toBeInTheDocument()
  expect(screen.getByLabelText('Current focus (optional)')).toHaveFocus()
  expect(screen.getByLabelText(/Exercise title 1/)).toHaveValue('Warm-up and mobility')
  expect(calls.some(call => call.method === 'POST')).toBe(false)
})

it('F5-08 shows a "Draft / Not shared" summary that follows the plan form until the Coach assigns it', async () => {
  const { calls } = coachApp()
  const user = userEvent.setup()
  renderApp('/coach/training/plans')
  await user.selectOptions(await screen.findByLabelText('Assigned class'), '1')
  await user.selectOptions(await screen.findByLabelText(/Member booking/), '11')
  expect(screen.queryByRole('region', { name: 'Training plan draft' })).not.toBeInTheDocument()
  await user.type(screen.getByLabelText(/^Plan title/), 'Week 1 ball control')
  const exercise = screen.getByLabelText(/Exercise title 1/)
  await user.type(exercise, 'Passing pairs')
  const duration = screen.getByLabelText(/Exercise duration 1/)
  await user.clear(duration)
  await user.type(duration, '30')
  const summary = screen.getByRole('region', { name: 'Training plan draft' })
  expect(within(summary).getByText('Draft / Not shared')).toBeInTheDocument()
  expect(within(summary).getByText(/^Taylor \/ Individual draft/)).toBeInTheDocument()
  expect(within(summary).getByText('30 minutes in 1 exercise')).toBeInTheDocument()
  expect(within(summary).getByText('Member visibility: Not shared yet.')).toBeInTheDocument()
  expect(calls.some(call => call.method === 'POST')).toBe(false)
  await user.click(screen.getByRole('button', { name: 'Assign plan' }))
  expect(await screen.findByText('Plan assigned. The member can view it immediately.')).toBeInTheDocument()
  expect(calls.filter(call => call.method === 'POST' && call.path === '/coach/training/plans')).toHaveLength(1)
})
