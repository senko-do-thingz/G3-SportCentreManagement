import { readFileSync } from 'node:fs'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MANAGER } from '../../test/fakeBackend'
import { addDays, centerNow, displayDate, monday } from './scheduleModel'

const date = addDays(monday(centerNow().date), 7)
const item = { id: 5, code: 'CL-005', name: 'Badminton foundations', sportId: 1, sportName: 'Badminton', ageGroupId: 1, ageGroupLabel: 'Adults', level: 'BEGINNER', coachId: 3, coachName: 'Quan Coach', maxMembers: 20, upcomingSessions: 1, active: true }
const session = { id: 7, scheduleRevision: 2, date, startTime: '09:00:00', endTime: '10:00:00', class: { id: 5, code: item.code, name: item.name }, coach: { id: 3, name: 'Quan Coach' }, facility: { id: 1, name: 'Court A' }, capacity: 20, bookedCount: 2, trainingType: 'COACH_LED', status: 'PUBLISHED', cancelReason: null }
const cancelledSession = { ...session, id: 8, status: 'CANCELLED', cancelReason: 'Maintenance' }
const tokensCss = readFileSync('src/styles/tokens.css', 'utf8')
const scheduleCss = readFileSync('src/styles/managerSchedule.css', 'utf8')
let injectedStyles: HTMLStyleElement

function setCalendarDate(input: HTMLElement, iso: string) {
  fireEvent.change(input, { target: { value: displayDate(iso) } })
  fireEvent.blur(input)
}

function backend(conflict = false, facilitiesMissing: boolean | number = false, schedule: unknown[] = [session, cancelledSession], editResult: unknown = session, courtMax?: number) {
  return installFakeFetch((call) => {
    const path = call.path.split('?')[0]
    if (path === '/users/me') return json(200, MANAGER)
    if (path === '/manager/classes') return json(200, { content: [item], page: 0, size: 100, totalElements: 1, totalPages: 1 })
    if (path === '/coaches') return json(200, [{ id: 3, fullName: 'Quan Coach', sportIds: [1] }])
    if (path === '/facilities') return facilitiesMissing ? backendError(typeof facilitiesMissing === 'number' ? facilitiesMissing : 404, 'Facility endpoint unavailable') : json(200, [{ id: 1, name: 'Court A', active: true, capacity: courtMax }])
    if (path === '/manager/sessions') return json(200, schedule)
    if (path === '/manager/classes/5/sessions' && call.method === 'POST') return conflict ? json(409, { status: 409, message: 'Schedule conflict', details: { conflicts: [{ sessionId: 7, requestedDate: date, date, startTime: '09:00:00', endTime: '10:00:00', class: session.class, coach: session.coach, facility: session.facility, conflictType: 'BOTH' }, { sessionId: 9, requestedDate: date, date, startTime: '09:15:00', endTime: '10:15:00', class: null, coach: null, facility: null, conflictType: 'FACILITY' }] } }) : json(201, [session])
    if (path === '/manager/sessions/7/cancel') return json(200, session)
    if (path === '/manager/sessions/9/publish' && call.method === 'PUT') return json(200, { ...session, id: 9, status: 'PUBLISHED' })
    if (path === '/manager/sessions/7') return editResult instanceof Response ? editResult : json(200, editResult)
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
  it('explains start-time grouping and shows complete intervals for overlapping sessions', async () => {
    const overlaps = [
      { ...session, id: 40, startTime: '09:00:00', endTime: '11:00:00', class: { ...session.class, id: 40, code: 'CL-040', name: 'Morning skills session' } },
      { ...session, id: 41, startTime: '09:30:00', endTime: '10:30:00', class: { ...session.class, id: 41, code: 'CL-041', name: 'Technique practice session' } },
      { ...session, id: 42, startTime: '10:00:00', endTime: '10:30:00', class: { ...session.class, id: 42, code: 'CL-042', name: 'Afternoon court session' } },
    ]
    backend(false, false, overlaps)
    renderApp(`/manager/schedule?week=${monday(date)}`)

    expect(await screen.findByText('Sessions are grouped by start time. Each card shows its full time range; separate rows can overlap.')).toBeInTheDocument()
    const table = screen.getByRole('table', { name: 'Manager weekly schedule' })
    expect(within(table).getAllByRole('row')).toHaveLength(4)
    expect(within(table).getAllByRole('rowheader').map((header) => header.textContent)).toEqual(['09:00', '09:30', '10:00'])
    expect(screen.getByText('09:00 - 11:00')).toBeInTheDocument()
    expect(screen.getByText('09:30 - 10:30')).toBeInTheDocument()
    expect(screen.getByText('10:00 - 10:30')).toBeInTheDocument()
  })
  it('keeps dense weeks and lengthy session details in the scrollable timetable', async () => {
    const asTime = (minutes: number) => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}:00`
    const denseSessions = Array.from({ length: 12 }, (_, index) => ({
      ...session,
      id: 60 + index,
      startTime: asTime(6 * 60 + index * 30),
      endTime: asTime(6 * 60 + index * 30 + 45),
      class: { ...session.class, id: 60 + index, code: `CL-${60 + index}`, name: `Foundations and advanced skills practice for mixed ability groups ${index + 1}` },
      facility: { ...session.facility, name: 'North court training area with equipment storage and spectator seating' },
    }))
    backend(false, false, denseSessions)
    renderApp(`/manager/schedule?week=${monday(date)}`)

    const table = await screen.findByRole('table', { name: 'Manager weekly schedule' })
    const region = screen.getByRole('region', { name: 'Weekly session timetable' })
    expect(within(table).getAllByRole('row')).toHaveLength(13)
    expect(region).toHaveTextContent('North court training area with equipment storage and spectator seating')
    expect(screen.getAllByRole('button', { name: 'Edit' })).toHaveLength(12)
    expect(screen.getAllByRole('button', { name: 'Cancel session' })).toHaveLength(12)
  })
  it('shows multiple time slots and sorts same-time sessions regardless of API order', async () => {
    // Keep both fixture days inside the selected week, including Sunday runs.
    const sortDate = monday(date)
    const namedSession = (id: number, name: string, sessionDate = sortDate, startTime = '09:00:00', endTime = '10:00:00') => ({
      ...session,
      id,
      date: sessionDate,
      startTime,
      endTime,
      class: { id, code: `CL-${id}`, name },
    })
    backend(false, false, [
      namedSession(30, 'Zulu class'),
      namedSession(31, 'Next day class', addDays(sortDate, 1)),
      namedSession(32, 'Later class', sortDate, '10:00:00', '11:00:00'),
      namedSession(33, 'Alpha class'),
    ])
    renderApp(`/manager/schedule?week=${sortDate}`)

    const table = await screen.findByRole('table', { name: 'Manager weekly schedule' })
    const rows = within(table).getAllByRole('row')
    expect(rows.map((row) => within(row).queryByRole('rowheader')?.textContent).filter(Boolean)).toEqual(['09:00', '10:00'])
    expect([...table.querySelectorAll('.f2-session__title')].map((title) => title.textContent)).toEqual([
      'Alpha class', 'Zulu class', 'Next day class', 'Later class',
    ])
  })
  it('keeps sessions adjacent to midnight on their own calendar dates', async () => {
    const lateDate = addDays(monday(date), 3)
    const earlyDate = addDays(lateDate, 1)
    backend(false, false, [
      { ...session, id: 45, date: lateDate, startTime: '23:00:00', endTime: '23:59:00', class: { ...session.class, name: 'Late evening session' } },
      { ...session, id: 46, date: earlyDate, startTime: '00:00:00', endTime: '01:00:00', class: { ...session.class, name: 'Early morning session' } },
    ])
    renderApp('/manager/schedule?week=' + monday(date))

    const table = await screen.findByRole('table', { name: 'Manager weekly schedule' })
    const lateTitle = within(table).getByText('Late evening session')
    const earlyTitle = within(table).getByText('Early morning session')
    const lateCellIndex = lateTitle.closest('td')?.cellIndex ?? -1
    const earlyCellIndex = earlyTitle.closest('td')?.cellIndex ?? -1
    expect(lateCellIndex).toBe(earlyCellIndex - 1)
    expect(within(table).getByRole('rowheader', { name: '23:00' })).toBeInTheDocument()
    expect(within(table).getByRole('rowheader', { name: '00:00' })).toBeInTheDocument()
    expect(screen.getByText('23:00 - 23:59')).toBeInTheDocument()
    expect(screen.getByText('00:00 - 01:00')).toBeInTheDocument()
  })
  it('keeps the last selected week when a superseded schedule request resolves late', async () => {
    const selectedWeek = addDays(monday(centerNow().date), 14)
    const staleWeek = addDays(selectedWeek, 7)
    const latestWeek = addDays(selectedWeek, 14)
    let releaseStale!: (response: Response) => void
    const staleResponse = new Promise<Response>((resolve) => { releaseStale = resolve })
    const api = backend(false, false, [])
    const originalFetch = api.fetchMock
    const delayedFetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(typeof input === 'string' ? input : input.toString())
      if (url.pathname.endsWith('/manager/sessions')) {
        const from = url.searchParams.get('from')
        if (from === staleWeek) return staleResponse
        const displayedWeek = from === selectedWeek ? selectedWeek : latestWeek
        const name = from === selectedWeek ? 'Initial week session' : 'Latest week session'
        return json(200, [{ ...session, id: displayedWeek === selectedWeek ? 70 : 72, date: displayedWeek, class: { ...session.class, name } }])
      }
      return originalFetch(input, init)
    })
    vi.stubGlobal('fetch', delayedFetch)
    const user = userEvent.setup()
    renderApp(`/manager/schedule?week=${selectedWeek}`)

    expect(await screen.findByText('Initial week session')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Next week' }))
    await waitFor(() => expect(delayedFetch.mock.calls.some(([input]) => String(input).includes(`from=${staleWeek}`))).toBe(true))
    await user.click(screen.getByRole('button', { name: 'Next week' }))
    expect(await screen.findByText('Latest week session')).toBeInTheDocument()

    releaseStale(json(200, [{ ...session, id: 71, date: staleWeek, class: { ...session.class, name: 'Stale week session' } }]))
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(screen.getByText('Latest week session')).toBeInTheDocument()
    expect(screen.queryByText('Stale week session')).not.toBeInTheDocument()
    expect(screen.getByText(`${displayDate(latestWeek)} - ${displayDate(addDays(latestWeek, 6))}`)).toBeInTheDocument()
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
    expect(getComputedStyle(table).minWidth).toBe('1280px')
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
    fireEvent.change(within(dialog).getByLabelText(/^Date/), { target: { value: displayDate(date) } })
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
    fireEvent.change(within(dialog).getByLabelText(/^Date/), { target: { value: displayDate(date) } })
    await user.selectOptions(within(dialog).getByLabelText(/^Facility/), '1')
    await waitFor(() => expect(within(dialog).getByLabelText(/^Coach/)).toBeEnabled())
    await user.click(within(dialog).getByRole('button', { name: 'Create schedule' }))
    const conflict = await screen.findByRole('dialog', { name: 'Schedule Conflict' })
    expect(within(conflict).getByText('2 conflicting sessions')).toBeInTheDocument()
    expect(within(conflict).getByText('Session ID Session ID unavailable: Unassigned class')).toBeInTheDocument()
    await user.click(within(conflict).getByRole('button', { name: 'Edit session' }))
    expect(within(dialog).getByLabelText(/^Date/)).toHaveValue(displayDate(date))
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
  it('offers Publish only for a draft session and sends the publish request after confirmation', async () => {
    const draft = { ...session, id: 9, startTime: '11:00:00', endTime: '12:00:00', status: 'DRAFT' }
    const api = backend(false, false, [session, draft])
    const user = userEvent.setup()
    renderApp(`/manager/schedule?week=${monday(date)}`)
    expect(await screen.findAllByRole('button', { name: 'Edit' })).toHaveLength(2)
    expect(screen.getAllByRole('button', { name: 'Publish' })).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Publish' }))
    await user.click(screen.getByRole('button', { name: 'Publish session' }))
    await waitFor(() => expect(api.calls.some((call) => call.path === '/manager/sessions/9/publish' && call.method === 'PUT')).toBe(true))
  })
  it('shows physical court capacity and keeps the class maximum as an additional guard', async () => {
    backend(false, false, [], session, 10)
    const user = userEvent.setup()
    renderApp(`/manager/schedule?classId=5`)
    await waitFor(() => expect(screen.getByRole('button', { name: '+ Create session' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: '+ Create session' }))
    const dialog = screen.getByRole('dialog', { name: 'Create session' })
    expect(within(dialog).getByLabelText(/^Capacity/)).toHaveValue(20)
    expect(within(dialog).getByText(/Choose a facility to see its physical limit/)).toBeInTheDocument()
    await user.selectOptions(within(dialog).getByLabelText(/^Facility/), '1')
    expect(within(dialog).getByLabelText(/^Capacity/)).toHaveValue(10)
    expect(within(dialog).getByText(/Physical facility capacity: 10 members/)).toBeInTheDocument()
    expect(within(dialog).getByText(/Allowed session maximum: 10 members/)).toBeInTheDocument()
    fireEvent.change(within(dialog).getByLabelText(/^Capacity/), { target: { value: '15' } })
    await user.click(within(dialog).getByRole('button', { name: 'Create schedule' }))
    expect(await within(dialog).findByText('Capacity must be a whole number from 1 to 10.')).toBeInTheDocument()
  })
  it('never exceeds the class limit when a court is larger than its class', async () => {
    backend(false, false, [], session, 30)
    const user = userEvent.setup()
    renderApp(`/manager/schedule?classId=5`)
    await waitFor(() => expect(screen.getByRole('button', { name: '+ Create session' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Create session' }))
    const dialog = screen.getByRole('dialog', { name: 'Create session' })
    await user.selectOptions(within(dialog).getByLabelText(/^Facility/), '1')
    expect(within(dialog).getByLabelText(/^Capacity/)).toHaveValue(20)
    expect(within(dialog).getByText(/Physical facility capacity: 30 members/)).toBeInTheDocument()
    expect(within(dialog).getByText(/Allowed session maximum: 20 members/)).toBeInTheDocument()
  })
  it('creates a weekly schedule using weekdays and range, without a date or draft status', async () => {
    const api = backend()
    const user = userEvent.setup()
    renderApp(`/manager/schedule?classId=5`)
    await waitFor(() => expect(screen.getByRole('button', { name: '+ Create session' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: '+ Create session' }))
    const dialog = screen.getByRole('dialog', { name: 'Create session' })
    await user.click(within(dialog).getByLabelText('Repeat weekly'))
    setCalendarDate(within(dialog).getByLabelText(/^From date/), monday(addDays(date, 7)))
    setCalendarDate(within(dialog).getByLabelText(/^To date/), addDays(monday(addDays(date, 7)), 21))
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
    expect(within(dialog).getByLabelText(/^Date/)).toBeEnabled()
    expect(within(dialog).getByText(/Changing the date generates a new Session ID/)).toBeInTheDocument()
    fireEvent.change(within(dialog).getByLabelText(/^Capacity/), { target: { value: '1' } })
    await user.click(within(dialog).getByRole('button', { name: 'Save session' }))
    expect(await within(dialog).findByText('Capacity must be a whole number from 2 to 20.')).toBeInTheDocument()
    expect(api.calls.some((call) => call.method === 'PUT')).toBe(false)
    fireEvent.change(within(dialog).getByLabelText(/^Capacity/), { target: { value: '12' } })
    await user.click(within(dialog).getByRole('button', { name: 'Save session' }))
    await waitFor(() => expect(api.calls.some((call) => call.path === '/manager/sessions/7' && call.method === 'PUT')).toBe(true))
    expect(api.calls.find((call) => call.method === 'PUT')?.body).not.toHaveProperty('trainingType')
  })
  it('submits the edited date, revision and optional reason while preserving the numeric session', async () => {
    const api = backend()
    const user = userEvent.setup()
    renderApp(`/manager/schedule?week=${monday(date)}`)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Edit' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog', { name: 'Edit session' })
    setCalendarDate(within(dialog).getByLabelText(/^Date/), addDays(date, 2))
    await user.type(within(dialog).getByLabelText('Rescheduling reason (optional)'), 'Facility maintenance')
    await waitFor(() => expect(within(dialog).getByLabelText(/^Coach/)).toBeEnabled())
    await user.click(within(dialog).getByRole('button', { name: 'Save session' }))
    await waitFor(() => expect(api.calls.find((call) => call.method === 'PUT')?.body).toMatchObject({
      date: addDays(date, 2), expectedRevision: 2, rescheduleReason: 'Facility maintenance', capacity: 20,
    }))
    expect(api.calls.find((call) => call.method === 'PUT')?.path).toBe('/manager/sessions/7')
  })

  it('DEF-311-01 blocks an invalid visible date from updating the stale session date', async () => {
    const reviewedSession = { ...session, date: '2026-11-15' }
    const api = backend(false, false, [reviewedSession], reviewedSession)
    const user = userEvent.setup()
    renderApp(`/manager/schedule?week=${monday(reviewedSession.date)}`)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Edit' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog', { name: 'Edit session' })
    const input = within(dialog).getByLabelText(/^Date/)
    expect(input).toHaveValue('15/11/2026')
    await user.clear(input)
    await user.type(input, '31/11/2026')
    await user.click(within(dialog).getByRole('button', { name: 'Save session' }))
    expect(await within(dialog).findByText('Enter a valid date in dd/MM/yyyy format.')).toBeInTheDocument()
    expect(api.calls.some((call) => call.path === '/manager/sessions/7' && call.method === 'PUT')).toBe(false)
    expect(input).toHaveFocus()
  })

  it('DEF-311-01 blocks keyboard submission when the visible session date is invalid', async () => {
    const reviewedSession = { ...session, date: '2026-11-15' }
    const api = backend(false, false, [reviewedSession], reviewedSession)
    const user = userEvent.setup()
    renderApp(`/manager/schedule?week=${monday(reviewedSession.date)}`)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Edit' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog', { name: 'Edit session' })
    const input = within(dialog).getByLabelText(/^Date/)
    await user.clear(input)
    await user.type(input, '31/11/2026{Enter}')
    expect(await within(dialog).findByText('Enter a valid date in dd/MM/yyyy format.')).toBeInTheDocument()
    expect(api.calls.some((call) => call.path === '/manager/sessions/7' && call.method === 'PUT')).toBe(false)
  })

  it('shows the regenerated code and follows a move outside the displayed week', async () => {
    const target = addDays(date, 8)
    const code = `${target.slice(8, 10)}${target.slice(5, 7)}${target.slice(2, 4)}BM02`
    backend(false, false, [session], { ...session, date: target, sessionId: code, scheduleRevision: 3 })
    const user = userEvent.setup()
    renderApp(`/manager/schedule?week=${monday(date)}`)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Edit' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog', { name: 'Edit session' })
    setCalendarDate(within(dialog).getByLabelText(/^Date/), target)
    await waitFor(() => expect(within(dialog).getByLabelText(/^Coach/)).toBeEnabled())
    await user.click(within(dialog).getByRole('button', { name: 'Save session' }))
    expect(await screen.findByText(`Session ${code} saved for ${displayDate(target)}`)).toBeInTheDocument()
    expect(await screen.findByText(`${displayDate(monday(target))} - ${displayDate(addDays(monday(target), 6))}`)).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('keeps the date and reason draft after a stale revision conflict', async () => {
    backend(false, false, [session], backendError(409, 'The session has changed. Reload it before saving again.'))
    const user = userEvent.setup()
    renderApp(`/manager/schedule?week=${monday(date)}`)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Edit' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog', { name: 'Edit session' })
    setCalendarDate(within(dialog).getByLabelText(/^Date/), addDays(date, 1))
    await user.type(within(dialog).getByLabelText('Rescheduling reason (optional)'), 'Member availability')
    await waitFor(() => expect(within(dialog).getByLabelText(/^Coach/)).toBeEnabled())
    await user.click(within(dialog).getByRole('button', { name: 'Save session' }))
    expect(await within(dialog).findByText(/The session has changed/)).toBeInTheDocument()
    expect(within(dialog).getByLabelText(/^Date/)).toHaveValue(displayDate(addDays(date, 1)))
    expect(within(dialog).getByLabelText('Rescheduling reason (optional)')).toHaveValue('Member availability')
    expect(screen.queryByText('Schedule updated')).not.toBeInTheDocument()
  })

})
