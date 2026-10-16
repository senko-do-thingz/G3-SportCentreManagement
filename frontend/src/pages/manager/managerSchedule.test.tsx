import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MANAGER } from '../../test/fakeBackend'
import { addDays, centerNow, monday } from './scheduleModel'

const date = addDays(centerNow().date, 7)
const item = { id: 5, code: 'CL-005', name: 'Badminton foundations', sportId: 1, sportName: 'Badminton', ageGroupId: 1, ageGroupLabel: 'Adults', level: 'BEGINNER', coachId: 3, coachName: 'Quan Coach', maxMembers: 20, upcomingSessions: 1, active: true }
const session = { id: 7, date, startTime: '09:00:00', endTime: '10:00:00', class: { id: 5, code: item.code, name: item.name }, coach: { id: 3, name: 'Quan Coach' }, facility: { id: 1, name: 'Court A' }, capacity: 20, bookedCount: 2, trainingType: 'COACH_LED', status: 'PUBLISHED', cancelReason: null }
function backend(conflict = false, facilitiesMissing = false) {
  return installFakeFetch((call) => {
    const path = call.path.split('?')[0]
    if (path === '/users/me') return json(200, MANAGER)
    if (path === '/manager/classes') return json(200, { content: [item], page: 0, size: 100, totalElements: 1, totalPages: 1 })
    if (path === '/coaches') return json(200, [{ id: 3, fullName: 'Quan Coach', sportIds: [1] }])
    if (path === '/facilities') return facilitiesMissing ? backendError(404, 'Facility endpoint unavailable') : json(200, [{ id: 1, name: 'Court A', active: true }])
    if (path === '/manager/sessions') return json(200, [session, { ...session, id: 8, status: 'CANCELLED', cancelReason: 'Maintenance' }])
    if (path === '/manager/classes/5/sessions' && call.method === 'POST') return conflict ? json(409, { status: 409, message: 'Schedule conflict', details: { conflicts: [{ sessionId: 7, requestedDate: date, date, startTime: '09:00:00', endTime: '10:00:00', class: session.class, coach: session.coach, facility: session.facility, conflictType: 'BOTH' }, { sessionId: 9, requestedDate: date, date, startTime: '09:15:00', endTime: '10:15:00', class: null, coach: null, facility: null, conflictType: 'FACILITY' }] } }) : json(201, [session])
    if (path === '/manager/sessions/7/cancel' || path === '/manager/sessions/7') return json(200, session)
    return backendError(404, 'Unexpected test request')
  })
}
beforeEach(() => { vi.stubEnv('VITE_MANAGER_PREVIEW', 'false'); tokenStorage.startSession({ accessToken: 'manager-test', refreshToken: 'refresh-test' }) })
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs() })
describe('FE-07 schedule', () => {
  it('renders exact week, cancelled reason and only valid actions', async () => {
    backend()
    renderApp(`/manager/schedule?week=${monday(date)}`)
    expect(await screen.findByText('Reason: Maintenance')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Edit' })).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: 'Cancel session' })).toHaveLength(1)
  })
  it('disables creation on missing facility API without hiding sessions', async () => {
    backend(false, true)
    renderApp(`/manager/schedule?week=${monday(date)}`)
    await screen.findByText('Reason: Maintenance')
    expect(screen.getByRole('button', { name: '+ Create session' })).toBeDisabled()
    expect(await screen.findByText(/Facility lookup is unavailable/)).toBeInTheDocument()
  })
  it('preserves the draft and lists every exact 409 conflict in a stacked dialog', async () => {
    const api = backend(true)
    const user = userEvent.setup()
    renderApp(`/manager/schedule?week=${monday(date)}&classId=5`)
    await screen.findByText('Reason: Maintenance')
    await waitFor(() => expect(screen.getByRole('button', { name: '+ Create session' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: '+ Create session' }))
    const dialog = screen.getByRole('dialog', { name: 'Create session' })
    fireEvent.change(within(dialog).getByLabelText(/^Date/), { target: { value: date } })
    await user.selectOptions(within(dialog).getByLabelText(/^Facility/), '1')
    await waitFor(() => expect(within(dialog).getByLabelText(/^Coach/)).toBeEnabled())
    await user.click(within(dialog).getByRole('button', { name: 'Create schedule' }))
    const conflict = await screen.findByRole('dialog', { name: 'Schedule Conflict' })
    expect(within(conflict).getByText('2 conflicting sessions')).toBeInTheDocument()
    expect(within(conflict).getByText('Session #9: Unassigned class')).toBeInTheDocument()
    await user.click(within(conflict).getByRole('button', { name: 'Edit session' }))
    expect(within(dialog).getByLabelText(/^Date/)).toHaveValue(date)
    expect(api.calls.filter((call) => call.method === 'POST')).toHaveLength(1)
  })
  it('requires a reason before sending cancellation', async () => {
    const api = backend()
    const user = userEvent.setup()
    renderApp(`/manager/schedule?week=${monday(date)}`)
    await screen.findByText('Reason: Maintenance')
    await user.click(screen.getByRole('button', { name: 'Cancel session' }))
    await user.click(screen.getByRole('button', { name: 'Confirm cancellation' }))
    expect(await screen.findByText('Enter a cancellation reason of 1 to 255 characters.')).toBeInTheDocument()
    await user.type(screen.getByLabelText(/^Cancellation reason/), '  Weather  ')
    await user.click(screen.getByRole('button', { name: 'Confirm cancellation' }))
    await waitFor(() => expect(api.calls.some((call) => call.path === '/manager/sessions/7/cancel' && call.method === 'PUT')).toBe(true))
  })
})
