import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MANAGER, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
import { safeMemberReturnTo } from './assistantNavigation'

const session = { id: 5, className: 'Beginner swimming', date: '2026-10-30', startTime: '08:00:00', endTime: '09:00:00' }
function signedIn(handler: (call: RecordedCall) => Response, profile = MEMBER) {
  tokenStorage.startSession({ accessToken: 'member-token', refreshToken: 'refresh' })
  return installFakeFetch(call => call.path === '/users/me' ? json(200, profile) : handler(call))
}
afterEach(() => vi.unstubAllGlobals())
describe('Member training', () => {
  it('opens a read-only assigned plan and its steps', async () => {
    const plan = { id: 7, title: 'Breathing technique', className: 'Beginner swimming', coachName: 'Coach An', description: 'Breathing practice' }
    const { calls } = signedIn(call => call.path.endsWith('/plans/7')
      ? json(200, { plan, steps: [{ id: 1, stepOrder: 1, title: 'Warm-up', durationMinutes: 10, instructions: 'Shoulder rotations' }], feedback: [] })
      : json(200, [plan]))
    renderApp('/member/training/plans')
    await userEvent.click(await screen.findByRole('link', { name: 'View plan details: Breathing technique' }))
    expect(await screen.findByText('Shoulder rotations')).toBeInTheDocument()
    expect(screen.getByText(/Read only/)).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(calls.every(call => call.method === 'GET')).toBe(true)
  })
  it('shows attendance date, time and status', async () => {
    signedIn(() => json(200, [{ id: 1, session, status: 'LATE', notes: 'Arrived 5 minutes late' }]))
    renderApp('/member/training/attendance')
    expect(await screen.findByText('Late')).toBeInTheDocument()
    expect(screen.getByText(/30\/10\/2026 - 08:00-09:00/)).toBeInTheDocument()
  })
  it('distinguishes unmarked and cancelled sessions from absence', async () => {
    signedIn(() => json(200, [
      { id: null, bookingId: 3, session, status: 'UNMARKED', notes: null, recordedStatus: null },
      { id: 4, bookingId: 4, session, status: 'CANCELLED', notes: null, recordedStatus: 'PRESENT' },
    ]))
    renderApp('/member/training/attendance')
    expect(await screen.findByText('Not marked')).toBeInTheDocument()
    expect(screen.getByText('Cancelled')).toBeInTheDocument()
    expect(screen.getByText('Recorded participation: Present')).toBeInTheDocument()
    expect(screen.queryByText('Absent')).not.toBeInTheDocument()
  })
  it('shows feedback on its personal plan immediately without a session result', async () => {
    const { calls } = signedIn(() => json(200, {
      plan: { id: 7, title: 'Breathing technique', className: 'Beginner swimming', coachName: 'An', description: null },
      steps: [],
      feedback: [{ id: 3, planId: 7, planTitle: 'Breathing technique', createdAt: '2026-11-01T09:00:00', coachName: 'An', rating: 4, comments: 'Good progress', strengths: 'Persistence', areasForImprovement: 'Breathing rhythm' }],
    }))
    renderApp('/member/training/plans/7')
    expect(await screen.findByText('Good progress')).toBeInTheDocument()
    expect(screen.getByText('Breathing rhythm')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Breathing technique' })).toHaveAttribute('href', '/member/training/plans/7')
    expect(document.querySelector('time')).toHaveAttribute('datetime', '2026-11-01T09:00:00')
    expect(calls.some(call => call.path.includes('/results'))).toBe(false)
  })
  it('shows metrics with their actual scale and links to plan feedback', async () => {
    signedIn(() => json(200, { result: { id: 1, session, notes: 'Completed' },
      scores: [{ id: 2, metricName: 'Endurance', score: 75, maxScore: 100, description: null }] }))
    renderApp('/member/training/results/1')
    expect(await screen.findByText('75 / 100')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View plan feedback' })).toHaveAttribute('href', '/member/training/feedback')
  })
  it('shows an empty state and retries an API failure', async () => {
    let fail = true
    signedIn(() => fail ? backendError(503, 'Service unavailable') : json(200, []))
    renderApp('/member/training/results')
    expect(await screen.findByRole('alert')).toHaveTextContent('Service unavailable')
    fail = false
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('No training results yet')).toBeInTheDocument()
  })
  it('rejects non-Member before fetching training data', async () => {
    const { calls } = signedIn(() => json(200, []), MANAGER)
    renderApp('/member/training/plans')
    await waitFor(() => expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/access|permission|403/i))
    expect(calls.filter(call => call.path.startsWith('/member/training'))).toHaveLength(0)
  })
  it('preserves return navigation when opening the Assistant', async () => {
    signedIn(() => json(200, []))
    renderApp('/member/training/plans?view=week#plan-list')
    await userEvent.click(await screen.findByRole('button', { name: 'Open member navigation' }))
    await userEvent.click(screen.getByRole('link', { name: 'Open AI Assistant' }))
    expect(await screen.findByText('AI is not configured')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to previous page' })).toHaveAttribute('href', '/member/training/plans?view=week#plan-list')
    expect(screen.getByRole('button', { name: 'Open member navigation' })).toHaveAttribute('aria-expanded', 'false')
  })
  it('keeps one training entry in the workspace header and navigates locally between training screens', async () => {
    signedIn(() => json(200, []))
    renderApp('/member/training/plans')
    const header = await screen.findByRole('navigation', { name: 'Member navigation' })
    expect(within(header).getByRole('link', { name: 'Training' })).toHaveAttribute('href', '/member/training/plans')
    expect(within(header).getByRole('link', { name: 'My Payments' })).toHaveAttribute('href', '/member/payments')
    expect(within(header).queryByRole('link', { name: 'Attendance history' })).not.toBeInTheDocument()
    const training = screen.getByRole('navigation', { name: 'Training navigation' })
    await userEvent.click(within(training).getByRole('link', { name: 'Attendance history' }))
    expect(await screen.findByRole('heading', { name: 'Attendance history' })).toBeInTheDocument()
    expect(within(training).getByRole('link', { name: 'Attendance history' })).toHaveAttribute('aria-current', 'page')
    expect(within(header).getByRole('link', { name: 'Training' })).toHaveClass('workspace-header__nav-link--active')
  })
  it('labels deterministic recommendations and uses the canonical service', async () => {
    const { calls } = signedIn(call => call.path.startsWith('/member/training/recommendations')
      ? json(200, { mode: 'RULE_BASED', ruleVersion: 'class-rules-v1', bookingEligibilityChecked: false, message: 'Preference matches only.', page: 0, size: 10, totalMatches: 0, items: [] })
      : json(200, []))
    renderApp('/member/training/recommendations')
    expect(await screen.findByText('No matching available classes')).toBeInTheDocument()
    expect(screen.getByText(/RULE_BASED - Preference matches only/)).toBeInTheDocument()
    expect(calls.some(call => call.path.startsWith('/member/training/recommendations'))).toBe(true)
    expect(calls.every(call => call.method === 'GET')).toBe(true)
  })
  it('rejects external, traversal, assistant-loop and staff return paths', () => {
    for (const value of ['https://evil.test', '//evil.test', '/member/../manager', '/memberish', '/member/assistant', '/member\\evil']) {
      expect(safeMemberReturnTo(value)).toBe('/member')
    }
    expect(safeMemberReturnTo('/member/training/plans?x=1')).toBe('/member/training/plans?x=1')
  })
  it('requires login for a direct Assistant link', async () => {
    const { calls } = installFakeFetch(() => json(200, []))
    renderApp('/member/assistant?returnTo=%2Fmember%2Ftraining%2Fplans')
    expect(await screen.findByLabelText(/email address/i)).toBeInTheDocument()
    expect(calls).toHaveLength(0)
  })
})
