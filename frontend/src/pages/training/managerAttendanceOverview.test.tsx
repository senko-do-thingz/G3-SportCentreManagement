import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MANAGER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

afterEach(() => vi.unstubAllGlobals())
it('shows unmarked separately and loads real session details without issuing writes', async () => {
  tokenStorage.startSession({ accessToken: 'manager', refreshToken: 'refresh' })
  const session = { id: 1, sessionCode: '051126FB01', date: '2026-11-05', startTime: '09:00:00', className: 'Football', coachName: 'An', confirmed: 2, present: 1, late: 0, absent: 0, excused: 0, unmarked: 1, attendanceRate: 50 }
  const { calls } = installFakeFetch(call => {
    if (call.path === '/users/me') return json(200, MANAGER)
    if (call.path.startsWith('/manager/classes?')) return json(200, { content: [], totalElements: 0, totalPages: 0 })
    if (call.path === '/coaches') return json(200, [])
    if (call.path.includes('/attendance/overview?')) return json(200, { sessions: [session], truncated: true })
    if (call.path.endsWith('/sessions/051126FB01')) return json(200, { session, members: [{ bookingId: 1, memberName: 'Lan', memberCode: 'M1', bookingStatus: 'CONFIRMED', attendanceStatus: null }] })
    return backendError(404, 'Not found')
  })
  renderApp('/manager/training/attendance')
  expect(await screen.findByText('50%')).toBeInTheDocument()
  expect(screen.getByText(/newest 200 sessions/)).toBeInTheDocument()
  await userEvent.setup().click(screen.getByRole('button', { name: 'View 051126FB01' }))
  expect(await screen.findByText('M1 - Lan')).toBeInTheDocument()
  expect(screen.getByRole('cell', { name: 'Unmarked' })).toBeInTheDocument()
  expect(calls.every(call => call.method === 'GET')).toBe(true)
})
