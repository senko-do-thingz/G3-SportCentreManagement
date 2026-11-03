import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
import type { AttendanceStatus, SessionMember } from '../../api/staffTrainingApi'

const roles = ['COACH', 'RECEPTIONIST'] as const
const statuses = [['PRESENT', 'Present'], ['LATE', 'Late'], ['ABSENT', 'Absent'], ['EXCUSED', 'Excused']] as const
const session = { id: 5, sessionId: '031126BM01', classId: 1, className: 'Badminton practice', sportId: 2, sportName: 'Badminton', date: '2026-11-03', startTime: '08:00:00', endTime: '09:00:00', trainingType: 'COACH_LED', coachName: 'An', facilityName: 'Court 1', status: 'PUBLISHED', attendanceDateOpen: true, attendanceState: 'OPEN', bookedCount: 1, markedCount: 0 }
const member: SessionMember = { bookingId: 11, memberId: 42, memberName: 'Lan', attendanceId: null, status: null, notes: null, resultId: null }

function setup(role: typeof roles[number], initial: SessionMember = member) {
  const root = role === 'COACH' ? '/coach' : '/reception'
  let rows = [{ ...initial }]
  let fail = false
  let finish: (() => void) | undefined
  let deferred = false
  tokenStorage.startSession({ accessToken: 'synthetic-test-token', refreshToken: 'synthetic-refresh' })
  const { calls } = installFakeFetch(call => {
    if (call.path === '/users/me') return json(200, { ...MEMBER, role })
    if (call.path === `${root}/training/sessions`) return json(200, [{ ...session, markedCount: rows.filter(row => row.attendanceId != null).length }])
    if (call.path === `${root}/training/sessions/by-code/031126BM01/members`) return json(200, rows)
    if (call.path.startsWith(`${root}/training/attendance`) && ['POST', 'PUT'].includes(call.method)) {
      if (fail) return backendError(409, 'Attendance changed. Retry after checking the record.')
      const save = () => {
        const input = call.body as { bookingId?: number; status: AttendanceStatus; notes: string | null }
        rows = rows.map(row => row.bookingId === (input.bookingId ?? initial.bookingId) ? { ...row, attendanceId: 50, status: input.status, notes: input.notes } : row)
        return json(call.method === 'POST' ? 201 : 200, { id: 50 })
      }
      if (deferred) return new Promise<Response>(resolve => { finish = () => resolve(save()) })
      return save()
    }
    return backendError(404, `Unexpected endpoint ${call.path}`)
  })
  renderApp(`${root}/training/attendance`)
  return { calls, writes: () => calls.filter(call => ['POST', 'PUT'].includes(call.method)), setRows: (next: SessionMember[]) => { rows = next }, setFail: (value: boolean) => { fail = value }, defer: () => { deferred = true }, finish: () => finish!() }
}

afterEach(() => vi.unstubAllGlobals())

describe.each(roles)('Quick attendance choices for %s', role => {
  it.each(statuses)('selects %s locally and creates attendance only after Save', async (value, label) => {
    const api = setup(role)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Select session 031126BM01/ }))
    const row = await screen.findByRole('form', { name: /Record attendance for Lan/ })
    const group = within(row).getByRole('group', { name: /Attendance status for Lan, booking 11/ })
    expect(within(group).getAllByRole('radio')).toHaveLength(4)
    expect(within(row).queryByRole('combobox')).not.toBeInTheDocument()
    await user.click(within(group).getByRole('radio', { name: label }))
    expect(within(group).getByRole('radio', { name: label })).toBeChecked()
    expect(within(row).getByText('Not marked')).toBeInTheDocument()
    expect(api.writes()).toHaveLength(0)
    await user.type(within(row).getByLabelText(/Attendance note for Lan/), ' Short note ')
    await user.click(within(row).getByRole('button', { name: 'Save attendance' }))
    await screen.findByRole('form', { name: /Correct attendance for Lan/ })
    expect(api.writes()).toHaveLength(1)
    expect(api.writes()[0].body).toEqual({ bookingId: 11, status: value, notes: 'Short note' })
    expect(screen.getByRole('radio', { name: label })).toBeChecked()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save correction' })).toHaveFocus())
  })

  it.each(statuses)('corrects to %s only after a meaningful reason and explicit Save', async (value, label) => {
    const api = setup(role, { ...member, attendanceId: 50, status: 'PRESENT' })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Select session 031126BM01/ }))
    await user.click(await screen.findByRole('radio', { name: label }))
    await user.click(screen.getByRole('button', { name: 'Save correction' }))
    expect(api.writes()).toHaveLength(0)
    const reason = screen.getByLabelText(/Correction reason for Lan/)
    expect(reason).toHaveAttribute('minlength', '3')
    expect(reason).toHaveAttribute('maxlength', '500')
    await user.type(reason, '   ')
    await user.click(screen.getByRole('button', { name: 'Save correction' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('meaningful correction reason')
    expect(api.writes()).toHaveLength(0)
    await user.clear(reason)
    await user.type(reason, 'Verified staff observation')
    await user.click(screen.getByRole('button', { name: 'Save correction' }))
    await screen.findByText('Attendance corrected and the reason saved. The roster is updated.')
    expect(api.writes()[0].body).toEqual({ status: value, notes: null, reason: 'Verified staff observation' })
  })

  it('supports native arrow and Tab navigation without writing', async () => {
    const api = setup(role)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Select session 031126BM01/ }))
    const present = await screen.findByRole('radio', { name: 'Present' })
    present.focus()
    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('radio', { name: 'Late' })).toBeChecked()
    expect(screen.getByRole('radio', { name: 'Late' })).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(api.writes()).toHaveLength(0)
    expect(screen.getByRole('radio', { name: 'Late' })).toHaveFocus()
    await user.tab()
    expect(screen.getByLabelText(/Attendance note for Lan/)).toHaveFocus()
    expect(api.writes()).toHaveLength(0)
  })

  it('reconciles server status and newly saved results after refresh', async () => {
    const api = setup(role, { ...member, attendanceId: 50, status: 'ABSENT' })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Select session 031126BM01/ }))
    expect(await screen.findByRole('radio', { name: 'Absent' })).toBeChecked()
    api.setRows([{ ...member, attendanceId: 50, status: 'LATE', resultId: 9 }])
    await user.click(screen.getByRole('button', { name: 'Refresh roster' }))
    expect(await screen.findByRole('radio', { name: 'Late' })).toBeChecked()
    expect(screen.getAllByRole('radio').map(radio => radio.getAttribute('value'))).toEqual(['PRESENT', 'LATE'])
    expect(screen.queryByRole('radio', { name: 'Absent' })).not.toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: 'Excused' })).not.toBeInTheDocument()
    expect(api.writes()).toHaveLength(0)
  })

  it('disables the row during a pending save and ignores duplicate submits', async () => {
    const api = setup(role)
    api.defer()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Select session 031126BM01/ }))
    await user.click(await screen.findByRole('radio', { name: 'Late' }))
    await user.dblClick(screen.getByRole('button', { name: 'Save attendance' }))
    expect(api.writes()).toHaveLength(1)
    for (const radio of screen.getAllByRole('radio')) expect(radio).toBeDisabled()
    expect(screen.getByLabelText(/Attendance note for Lan/)).toBeDisabled()
    api.finish()
    await screen.findByRole('button', { name: 'Save correction' })
    expect(screen.getByRole('radio', { name: 'Late' })).toBeChecked()
  })

  it('retains the unsaved status, note and reason after an error and can retry', async () => {
    const api = setup(role, { ...member, attendanceId: 50, status: 'PRESENT' })
    api.setFail(true)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Select session 031126BM01/ }))
    await user.click(await screen.findByRole('radio', { name: 'Late' }))
    await user.type(screen.getByLabelText(/Attendance note for Lan/), 'Retain note')
    await user.type(screen.getByLabelText(/Correction reason for Lan/), 'Retain reason')
    await user.click(screen.getByRole('button', { name: 'Save correction' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Attendance changed')
    expect(screen.getByRole('radio', { name: 'Late' })).toBeChecked()
    expect(screen.getByLabelText(/Attendance note for Lan/)).toHaveValue('Retain note')
    expect(screen.getByLabelText(/Correction reason for Lan/)).toHaveValue('Retain reason')
    expect(screen.getByText('Present', { selector: '.attendance-status' })).toBeInTheDocument()
    api.setFail(false)
    await user.click(screen.getByRole('button', { name: 'Save correction' }))
    await screen.findByText('Attendance corrected and the reason saved. The roster is updated.')
    expect(api.writes()).toHaveLength(2)
    expect(await screen.findByRole('radio', { name: 'Late' })).toBeChecked()
  })

  it('keeps quick choices independent in a many-member roster', async () => {
    const api = setup(role)
    api.setRows(Array.from({ length: 40 }, (_, index) => ({ ...member, bookingId: 11 + index, memberId: 42 + index, memberName: index === 0 ? 'Lan' : `Member ${index}` })))
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Select session 031126BM01/ }))
    const first = await screen.findByRole('form', { name: /Record attendance for Lan/ })
    const second = screen.getByRole('form', { name: 'Record attendance for Member 1, booking 12' })
    expect(screen.getAllByRole('form')).toHaveLength(40)
    await user.click(within(first).getByRole('radio', { name: 'Excused' }))
    expect(within(first).getByRole('radio', { name: 'Excused' })).toBeChecked()
    expect(within(second).getByRole('radio', { name: 'Present' })).toBeChecked()
    expect(api.writes()).toHaveLength(0)
    expect(within(first).getByLabelText(/Attendance note/)).toHaveAttribute('maxlength', '500')
    expect(within(first).getByLabelText(/Attendance note/)).toHaveProperty('type', 'text')
  })
})
