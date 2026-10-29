import { readFileSync } from 'node:fs'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MANAGER } from '../../test/fakeBackend'
import { addDays, centerNow, displayDate, monday } from './scheduleModel'

const date = addDays(centerNow().date, 7)
const item = { id: 5, code: 'CL-005', name: 'Badminton foundations', sportId: 1, sportName: 'Badminton', ageGroupId: 1, ageGroupLabel: 'Adults', level: 'BEGINNER', coachId: 3, coachName: 'Quan Coach', maxMembers: 20, upcomingSessions: 1, active: true }
const session = { id: 7, date, startTime: '09:00:00', endTime: '10:00:00', class: { id: 5, code: item.code, name: item.name }, coach: { id: 3, name: 'Quan Coach' }, facility: { id: 1, name: 'Court A' }, capacity: 20, bookedCount: 2, trainingType: 'COACH_LED', status: 'PUBLISHED', cancelReason: null }
const cancelledSession = { ...session, id: 8, status: 'CANCELLED', cancelReason: 'Maintenance' }
const tokensCss = readFileSync('src/styles/tokens.css', 'utf8')
const scheduleCss = readFileSync('src/styles/managerSchedule.css', 'utf8')
let injectedStyles: HTMLStyleElement

function backend(conflict = false, facilitiesMissing: boolean | number = false, schedule: unknown[] = [session, cancelledSession]) {
  return installFakeFetch((call) => {
    const path = call.path.split('?')[0]
    if (path === '/users/me') return json(200, MANAGER)
    if (path === '/manager/classes') return json(200, { content: [item], page: 0, size: 100, totalElements: 1, totalPages: 1 })
    if (path === '/coaches') return json(200, [{ id: 3, fullName: 'Quan Coach', sportIds: [1] }])
    if (path === '/facilities') return facilitiesMissing ? backendError(typeof facilitiesMissing === 'number' ? facilitiesMissing : 404, 'Facility endpoint unavailable') : json(200, [{ id: 1, name: 'Court A', active: true }])
    if (path === '/manager/sessions') return json(200, schedule)
    if (path === '/manager/classes/5/sessions' && call.method === 'POST') return conflict ? json(409, { status: 409, message: 'Schedule conflict', details: { conflicts: [{ sessionId: 7, requestedDate: date, date, startTime: '09:00:00', endTime: '10:00:00', class: session.class, coach: session.coach, facility: session.facility, conflictType: 'BOTH' }, { sessionId: 9, requestedDate: date, date, startTime: '09:15:00', endTime: '10:15:00', class: null, coach: null, facility: null, conflictType: 'FACILITY' }] } }) : json(201, [session])
    if (path === '/manager/sessions/7/cancel' || path === '/manager/sessions/7') return json(200, session)
    return backendError(404, 'Unexpected test request')
  })
}
beforeEach(() => {
  vi.stubEnv('VITE_MANAGER_PREVIEW', 'false')
  tokenStorage.startSession({ accessToken: 'manager-test', refreshToken: 'refresh-test' })
  injectedStyles = document.createElement('style')
  const computedStyleCss = scheduleCss
    .replaceAll('var(--color-primary-soft)', 'rgb(1, 2, 3)')
    .replaceAll('var(--color-surface-muted)', 'rgb(4, 5, 6)')
  injectedStyles.textContent = `${tokensCss}\n${computedStyleCss}`
  document.head.append(injectedStyles)
})
afterEach(() => { injectedStyles.remove(); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs() })
describe('FE-07 schedule', () => {
  it.each([403, 500])('does not turn a facility %s into a missing-lookup fallback', async (status) => {
    backend(false, status)
    renderApp(`/manager/schedule?week=${monday(date)}&classId=5`)
    await screen.findByText('Reason: Maintenance')
    await waitFor(() => expect(screen.getByRole('button', { name: '+ Create session' })).toBeDisabled())
    expect(screen.queryByText(/The facility dropdown API is not available/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit' })).toBeDisabled()
  })
  it('renders the week as time-slot rows with the cancelled reason and valid actions', async () => {
    backend()
    renderApp(`/manager/schedule?week=${monday(date)}`)
    expect(await screen.findByText('Reason: Maintenance')).toBeInTheDocument()
    const table = screen.getByRole('table', { name: 'Manager weekly schedule' })
    expect(within(table).getAllByRole('columnheader')).toHaveLength(8)
    expect(within(table).getAllByRole('row')).toHaveLength(2)
    expect(table.querySelectorAll('.f2-session')).toHaveLength(2)
    expect(screen.getAllByRole('button', { name: 'Edit' })).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: 'Cancel session' })).toHaveLength(1)
  })
  it('shows multiple time slots and sorts same-time sessions regardless of API order', async () => {
    const namedSession = (id: number, name: string, sessionDate = date, startTime = '09:00:00', endTime = '10:00:00') => ({
      ...session,
      id,
      date: sessionDate,
      startTime,
      endTime,
      class: { id, code: `CL-${id}`, name },
    })
    backend(false, false, [
      namedSession(30, 'Zulu class'),
      namedSession(31, 'Next day class', addDays(date, 1)),
      namedSession(32, 'Later class', date, '10:00:00', '11:00:00'),
      namedSession(33, 'Alpha class'),
    ])
    renderApp(`/manager/schedule?week=${monday(date)}`)

    const table = await screen.findByRole('table', { name: 'Manager weekly schedule' })
    const rows = within(table).getAllByRole('row')
    expect(rows.map((row) => within(row).queryByRole('rowheader')?.textContent).filter(Boolean)).toEqual(['09:00', '10:00'])
    expect([...table.querySelectorAll('.f2-session__title')].map((title) => title.textContent)).toEqual([
      'Alpha class', 'Zulu class', 'Next day class', 'Later class',
    ])
  })
  it('highlights today even though the general table header has its own background', async () => {
    const today = centerNow().date
    backend(false, false, [{ ...session, date: today }])
    renderApp(`/manager/schedule?week=${monday(today)}`)

    const table = await screen.findByRole('table', { name: 'Manager weekly schedule' })
    const todayHeader = within(table).getByRole('columnheader', { name: new RegExp(displayDate(today)) })
    const otherHeader = within(table).getAllByRole('columnheader').find((header) => header !== todayHeader && header.className !== 'f2-schedule-table__time-heading')!
    expect(todayHeader).toHaveAttribute('aria-current', 'date')
    expect(getComputedStyle(todayHeader).backgroundColor).toBe('rgb(1, 2, 3)')
    expect(getComputedStyle(otherHeader).backgroundColor).toBe('rgb(4, 5, 6)')
  })
  it('navigates between previous, current, and next-week ranges', async () => {
    const api = backend()
    const user = userEvent.setup()
    const currentWeek = monday(centerNow().date)
    const selectedWeek = addDays(currentWeek, 14)
    renderApp(`/manager/schedule?week=${selectedWeek}`)
    await screen.findByText(`${displayDate(selectedWeek)} - ${displayDate(addDays(selectedWeek, 6))}`)

    const navigation = [
      { button: 'Previous week', week: addDays(selectedWeek, -7) },
      { button: 'This week', week: currentWeek },
      { button: 'Next week', week: addDays(currentWeek, 7) },
    ]
    for (const { button, week } of navigation) {
      await user.click(screen.getByRole('button', { name: button }))
      expect(await screen.findByText(`${displayDate(week)} - ${displayDate(addDays(week, 6))}`)).toBeInTheDocument()
      await waitFor(() => expect(api.calls.some((call) => call.path.includes(`from=${week}&to=${addDays(week, 6)}`))).toBe(true))
    }
  })
  it('contains horizontal overflow and exposes the timetable to keyboard users', async () => {
    const user = userEvent.setup()
    backend()
    renderApp(`/manager/schedule?week=${monday(date)}`)
    await screen.findByText('Reason: Maintenance')

    const region = screen.getByRole('region', { name: 'Weekly session timetable' })
    const table = within(region).getByRole('table', { name: 'Manager weekly schedule' })
    expect(getComputedStyle(region).overflowX).toBe('auto')
    expect(getComputedStyle(table).minWidth).toBe('900px')
    region.focus()
    expect(region).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('button', { name: 'Edit' })).toHaveFocus()
    await user.tab({ shift: true })
    expect(region).toHaveFocus()
  })
  it('allows explicit facility IDs when only the dropdown API is missing', async () => {
    const api = backend(false, true)
    const user = userEvent.setup()
    renderApp(`/manager/schedule?week=${monday(date)}&classId=5`)
    await screen.findByText('Reason: Maintenance')
    await waitFor(() => expect(screen.getByRole('button', { name: '+ Create session' })).toBeEnabled())
    expect(await screen.findByText(/The facility dropdown API is not available/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '+ Create session' }))
    const dialog = screen.getByRole('dialog', { name: 'Create session' })
    fireEvent.change(within(dialog).getByLabelText(/^Date/), { target: { value: date } })
    await waitFor(() => expect(within(dialog).getByLabelText(/^Coach/)).toBeEnabled())
    await user.click(within(dialog).getByRole('button', { name: 'Create schedule' }))
    expect(await within(dialog).findByText('Enter a valid existing facility ID.')).toBeInTheDocument()
    fireEvent.change(within(dialog).getByLabelText(/^Facility ID/), { target: { value: '1' } })
    await user.click(within(dialog).getByRole('button', { name: 'Create schedule' }))
    await waitFor(() => expect(api.calls.find((call) => call.method === 'POST')?.body).toMatchObject({ facilityId: 1 }))
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
  it('creates a weekly schedule using weekdays and range, without a date or draft status', async () => {
    const api = backend()
    const user = userEvent.setup()
    renderApp(`/manager/schedule?classId=5`)
    await waitFor(() => expect(screen.getByRole('button', { name: '+ Create session' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: '+ Create session' }))
    const dialog = screen.getByRole('dialog', { name: 'Create session' })
    await user.click(within(dialog).getByLabelText('Repeat weekly'))
    fireEvent.change(within(dialog).getByLabelText(/^From date/), { target: { value: monday(addDays(date, 7)) } })
    fireEvent.change(within(dialog).getByLabelText(/^To date/), { target: { value: addDays(monday(addDays(date, 7)), 21) } })
    await user.click(within(dialog).getByLabelText('MON'))
    expect(within(dialog).getByText('4 sessions will be created.')).toBeInTheDocument()
    await user.selectOptions(within(dialog).getByLabelText(/^Facility/), '1')
    await waitFor(() => expect(within(dialog).getByLabelText(/^Coach/)).toBeEnabled())
    await user.click(within(dialog).getByRole('button', { name: 'Create schedule' }))
    await waitFor(() => expect(api.calls.some((call) => call.method === 'POST')).toBe(true))
    const body = api.calls.find((call) => call.method === 'POST')?.body
    expect(body).toMatchObject({ daysOfWeek: ['MONDAY'], startTime: '09:00:00', endTime: '10:00:00', trainingType: 'COACH_LED', facilityId: 1 })
    expect(body).not.toHaveProperty('date')
    expect(body).not.toHaveProperty('status')
  })
  it('prevents capacity below booked count and keeps class and training type immutable on edit', async () => {
    const api = backend()
    const user = userEvent.setup()
    renderApp(`/manager/schedule?week=${monday(date)}`)
    await screen.findByText('Reason: Maintenance')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Edit' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog', { name: 'Edit session' })
    expect(within(dialog).getByLabelText(/^Class/)).toBeDisabled()
    expect(within(dialog).getByLabelText(/^Training type/)).toBeDisabled()
    fireEvent.change(within(dialog).getByLabelText(/^Capacity/), { target: { value: '1' } })
    await user.click(within(dialog).getByRole('button', { name: 'Save session' }))
    expect(await within(dialog).findByText('Capacity must be a whole number from 2 to 20.')).toBeInTheDocument()
    expect(api.calls.some((call) => call.method === 'PUT')).toBe(false)
    fireEvent.change(within(dialog).getByLabelText(/^Capacity/), { target: { value: '12' } })
    await user.click(within(dialog).getByRole('button', { name: 'Save session' }))
    await waitFor(() => expect(api.calls.some((call) => call.path === '/manager/sessions/7' && call.method === 'PUT')).toBe(true))
    expect(api.calls.find((call) => call.method === 'PUT')?.body).not.toHaveProperty('trainingType')
  })
})
