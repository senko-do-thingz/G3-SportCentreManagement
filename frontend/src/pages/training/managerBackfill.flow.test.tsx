import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

const path = '/manager/training/attendance/backfill'
const booking = { bookingId: 11, sessionId: 5, sessionCode: '011126SW01', coachName: 'Test Coach', facilityName: 'Pool 1', memberName: 'Test member', className: 'Swimming', date: '2026-11-01', startTime: '08:00:00', endTime: '09:00:00' }
const workspace = { enabled: true, window: 'PT48H', message: 'Manager only', bookings: [booking] }
function signedIn(role: string, handler: (call: RecordedCall) => Response) {
  tokenStorage.startSession({ accessToken: 'fixture-token', refreshToken: 'refresh' })
  return installFakeFetch(call => call.path === '/users/me' ? json(200, { ...MEMBER, role }) : handler(call))
}
afterEach(() => vi.unstubAllGlobals())
describe('Manager controlled attendance backfill', () => {
  it('shows the disabled policy state without a functional write form', async () => {
    const { calls } = signedIn('MANAGER', call => call.path === path ? json(200, { enabled: false, window: null, message: 'Disabled pending cutoff approval', bookings: [] }) : json(200, []))
    renderApp(path)
    expect(await screen.findByText('Disabled pending cutoff approval')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save administrative attendance' })).not.toBeInTheDocument()
    expect(calls.some(c => c.method === 'POST')).toBe(false)
  })
  it('shows a missed session and submits only trusted booking/status/reason/notes then refreshes', async () => {
    let saved = false
    const { calls } = signedIn('MANAGER', call => {
      if (call.path === path && call.method === 'GET') return json(200, { ...workspace, bookings: saved ? [] : [booking] })
      if (call.path === path && call.method === 'POST') { saved = true; return json(201, { id: 55 }) }
      return json(200, [])
    })
    renderApp(path)
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText(/^Missed booking/), '11')
    expect(screen.getByText('011126SW01')).toBeInTheDocument()
    expect(screen.getByText('Current attendance: Unmarked.')).toBeInTheDocument()
    expect(screen.getByText('Facility: Pool 1')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText(/^Backfill status/), 'LATE')
    await user.type(screen.getByLabelText('Administrative reason'), ' Verified missed roll call ')
    await user.type(screen.getByLabelText('Attendance notes'), 'Operator note')
    await user.click(screen.getByRole('button', { name: 'Save administrative attendance' }))
    expect(await screen.findByText('Attendance backfilled and audited. The member can view the saved status.')).toBeInTheDocument()
    expect(calls.find(c => c.method === 'POST')?.body).toEqual({ bookingId: 11, status: 'LATE', reason: 'Verified missed roll call', notes: 'Operator note' })
    expect(await screen.findByText('No eligible missed bookings')).toBeInTheDocument()
  })
  it('keeps reason and selection when a stale booking conflicts without an automatic replay', async () => {
    const { calls } = signedIn('MANAGER', call => call.path === path && call.method === 'POST' ? backendError(409, 'Attendance already exists; use correction') : call.path === path ? json(200, workspace) : json(200, []))
    renderApp(path)
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText(/^Missed booking/), '11')
    await user.type(screen.getByLabelText('Administrative reason'), 'Confirmed attendance')
    await user.click(screen.getByRole('button', { name: 'Save administrative attendance' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Attendance already exists; use correction')
    expect(screen.getByLabelText('Administrative reason')).toHaveValue('Confirmed attendance')
    expect(calls.filter(c => c.method === 'POST')).toHaveLength(1)
  })
  it('rejects trimmed short reasons without a request', async () => {
    const { calls } = signedIn('MANAGER', call => call.path === path ? json(200, workspace) : json(200, []))
    renderApp(path)
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText(/^Missed booking/), '11')
    await user.type(screen.getByLabelText('Administrative reason'), '  a  ')
    await user.click(screen.getByRole('button', { name: 'Save administrative attendance' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('meaningful reason')
    expect(calls.some(c => c.method === 'POST')).toBe(false)
  })
  it.each(['COACH', 'MEMBER', 'RECEPTIONIST'])('does not expose the action to %s', async role => {
    const { calls } = signedIn(role, () => json(200, []))
    renderApp(path)
    await waitFor(() => expect(calls.some(c => c.path === '/users/me')).toBe(true))
    expect(calls.some(c => c.path === path)).toBe(false)
    expect(screen.queryByRole('button', { name: 'Save administrative attendance' })).not.toBeInTheDocument()
  })
  it('shows a read error and retries the real API contract loader', async () => {
    let attempts = 0
    signedIn('MANAGER', call => call.path === path ? ++attempts === 1 ? backendError(503, 'Temporary service error') : json(200, workspace) : json(200, []))
    renderApp(path)
    expect(await screen.findByRole('alert')).toHaveTextContent('Temporary service error')
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByLabelText(/^Missed booking/)).toBeInTheDocument()
  })
})
