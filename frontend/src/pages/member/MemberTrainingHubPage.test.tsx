import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

const session = {
  id: 31,
  sessionId: '301126SW01',
  className: 'Swimming basics',
  sportName: 'Swimming',
  coachName: 'Coach An',
  facilityName: 'Pool 1',
  trainingType: 'COACH_LED',
  date: '2026-11-30',
  startTime: '08:00:00',
  endTime: '09:00:00',
}
const plan = { id: 7, title: 'Breathing practice', description: null, className: 'Swimming basics', coachName: 'Coach An' }
const trainingRecords = {
  plans: [plan],
  attendance: [{ id: 2, session, status: 'PRESENT', notes: null, bookingId: 3, bookingStatus: 'CONFIRMED', sessionStatus: 'COMPLETED', recordedStatus: 'PRESENT' }],
  results: [{ id: 4, session, notes: 'Steady pacing' }],
  feedback: [{ id: 5, planId: 7, planTitle: plan.title, coachName: 'Coach An', rating: 4, comments: 'Good breathing rhythm.', strengths: null, areasForImprovement: null, createdAt: '2026-11-09T10:30:00' }],
}

function signedIn(handler: (call: RecordedCall) => Response) {
  tokenStorage.startSession({ accessToken: 'training-hub-token', refreshToken: 'refresh' })
  return installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) : handler(call))
}

afterEach(() => vi.unstubAllGlobals())

describe('Member Training Hub', () => {
  it('summarizes real training records and keeps every training destination reachable', async () => {
    const { calls } = signedIn(call => {
      if (call.path === '/member/training/plans') return json(200, trainingRecords.plans)
      if (call.path === '/member/training/attendance') return json(200, trainingRecords.attendance)
      if (call.path === '/member/training/results') return json(200, trainingRecords.results)
      if (call.path === '/member/training/feedback') return json(200, trainingRecords.feedback)
      return backendError(404, 'Not found')
    })
    renderApp('/member/training')

    expect(await screen.findByRole('heading', { name: 'Training Hub' })).toBeInTheDocument()
    expect(await screen.findByText('Good breathing rhythm.')).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { name: 'Breathing practice' })).toHaveLength(2)
    expect(screen.getByText('Steady pacing')).toBeInTheDocument()
    expect(screen.getByText('Good breathing rhythm.')).toBeInTheDocument()
    const metrics = document.querySelector('.training-hub__metrics')
    expect(metrics).toHaveTextContent('Assigned plans1')
    expect(metrics).toHaveTextContent('Attendance records1')
    expect(metrics).toHaveTextContent('Recorded results1')
    expect(metrics).toHaveTextContent('Coach feedback1')

    const tools = screen.getByRole('navigation', { name: 'Training tools' })
    for (const [label, href] of [
      ['My classes', '/member/training/classes'],
      ['Training plans', '/member/training/plans'],
      ['Coach feedback', '/member/training/feedback'],
      ['Attendance history', '/member/training/attendance'],
      ['Training results', '/member/training/results'],
      ['Class recommendations', '/member/training/recommendations'],
      ['Training preferences', '/member/training/goals'],
      ['Ask your coach', '/member/training/questions'],
    ]) {
      expect(within(tools).getByRole('link', { name: new RegExp(label) })).toHaveAttribute('href', href)
    }

    expect(calls.filter(call => call.path.startsWith('/member/training/')).map(call => call.path).sort()).toEqual([
      '/member/training/attendance',
      '/member/training/feedback',
      '/member/training/plans',
      '/member/training/results',
    ])
    expect(calls.filter(call => call.path.startsWith('/member/training/')).every(call => call.method === 'GET')).toBe(true)
    expect(calls.filter(call => call.path.startsWith('/member/training/')).every(call => call.authorization === 'Bearer training-hub-token')).toBe(true)
  })

  it('shows honest empty states and retries a failed source without fabricating values', async () => {
    let failResults = true
    const { calls } = signedIn(call => {
      if (call.path === '/member/training/results' && failResults) return backendError(503, 'Training results are unavailable')
      if (call.path.startsWith('/member/training/')) return json(200, [])
      return backendError(404, 'Not found')
    })
    const user = userEvent.setup()
    renderApp('/member/training')

    expect(await screen.findByText('No assigned plans yet')).toBeInTheDocument()
    expect(screen.getByText('No coach feedback yet')).toBeInTheDocument()
    expect(screen.getByText('Unavailable')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Unable to load your results')

    failResults = false
    await user.click(screen.getByRole('button', { name: 'Refresh training overview' }))
    expect(await screen.findByText('No training results yet')).toBeInTheDocument()
    expect(calls.filter(call => call.path === '/member/training/results')).toHaveLength(2)
  })
})
