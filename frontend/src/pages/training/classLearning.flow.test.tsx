import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
const learningClass = { id: 1, name: 'Class skills', sportName: 'Badminton', coachName: 'Quan', level: 'BEGINNER', capacity: 10, active: true }
function login(role: string, handler: (call: RecordedCall) => Response) {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  return installFakeFetch(call => call.path === '/users/me' ? json(200, { ...MEMBER, role }) : handler(call))
}
afterEach(() => vi.unstubAllGlobals())
describe('Class learning Round 02', () => {
  it('R2-FE-01 shows actual class information and personal attendance', async () => {
    login('MEMBER', () => json(200, { learningClass, sessions: [{ bookingId: 3, sessionId: 5, date: '2026-11-02', startTime: '09:00', endTime: '10:00', bookingStatus: 'CONFIRMED', attendanceStatus: 'LATE', notes: 'Arrived late', resultId: 9, sessionStatus: 'PUBLISHED', trainingFormat: 'COACH_LED', capacity: 10, freeSeats: 0 }] }))
    renderApp('/member/training/classes/1')
    expect(await screen.findByRole('heading', { name: 'Class skills' })).toBeInTheDocument()
    expect(screen.getByText('Quan')).toBeInTheDocument()
    expect(screen.getByText('Late')).toBeInTheDocument()
    expect(screen.getByText(/Coach-led - PUBLISHED - 0 of 10 places available - Full/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View session availability and booking rules' })).toHaveAttribute('href', '/member/sessions/5')
    expect(screen.getByRole('link', { name: 'View session assessment' })).toHaveAttribute('href', '/member/training/results/9')
    expect(screen.getByRole('link', { name: 'Ask your coach about supplementary practice' })).toHaveAttribute('href', '/member/training/questions?classId=1')
  })
  it('R2-FE-02 sends a class question and opens the private conversation', async () => {
    const question = { id: 7, classId: 1, className: 'Class skills', memberName: 'Member', subject: 'Footwork', description: 'How to practise?', status: 'OPEN', createdAt: '2026-11-02T10:00:00' }
    const { calls } = login('MEMBER', call => {
      if (call.path === '/member/training/classes') return json(200, [learningClass])
      if (call.path === '/member/training/questions' && call.method === 'POST') return json(201, { id: 7 })
      if (call.path === '/member/training/questions') return json(200, [question])
      if (call.path === '/member/training/questions/7') return json(200, { question, messages: [] })
      return backendError(404, 'Unexpected request')
    })
    renderApp('/member/training/questions?classId=1')
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText(/Question subject/), 'Footwork')
    await user.type(screen.getByLabelText('Practice question'), 'How to practise?')
    await user.click(screen.getByRole('button', { name: 'Ask coach' }))
    expect(await screen.findByText('Question sent to your coach.')).toBeInTheDocument()
    expect(await screen.findByLabelText('Follow-up question')).toBeInTheDocument()
    expect(calls.find(call => call.method === 'POST')?.body).toEqual({ classId: 1, subject: 'Footwork', body: 'How to practise?' })
  })
  it('R2-FE-03 blocks repeat results already recorded by the server', async () => {
    login('COACH', call => {
      if (call.path === '/coach/training/sessions') return json(200, [{ id: 5, classId: 1, className: 'Class skills', sportId: 1, date: '2026-11-02', startTime: '09:00', endTime: '10:00', trainingType: 'COACH_LED' }])
      if (call.path === '/coach/training/sessions/5/members') return json(200, [{ bookingId: 3, memberId: 42, memberName: 'Member', attendanceId: 8, status: 'PRESENT', notes: null, resultId: 9 }])
      return json(200, [])
    })
    renderApp('/coach/training/results')
    await userEvent.selectOptions(await screen.findByLabelText('Assigned session'), '5')
    expect(await screen.findByText('Results already saved')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save results' })).not.toBeInTheDocument()
  })
  it('R2-FE-04 enables coach attendance only through the coach endpoint', async () => {
    const { calls } = login('COACH', call => {
      if (call.path === '/coach/training/sessions') return json(200, [{ id: 5, className: 'Class skills', date: '2026-11-02', startTime: '09:00', endTime: '10:00' }])
      if (call.path === '/coach/training/sessions/5/members') return json(200, [{ bookingId: 3, memberId: 42, memberName: 'Member', attendanceId: null, status: null, notes: null, resultId: null }])
      if (call.path === '/coach/training/attendance') return json(201, { id: 8 })
      return backendError(404, 'Unexpected request')
    })
    renderApp('/coach/training/attendance')
    await userEvent.selectOptions(await screen.findByLabelText('Session to mark attendance'), '5')
    await userEvent.selectOptions(await screen.findByLabelText('Booking to mark attendance'), '3')
    await userEvent.click(screen.getByRole('button', { name: 'Save attendance' }))
    expect(await screen.findByText('Attendance saved and the list updated.')).toBeInTheDocument()
    expect(calls.find(call => call.method === 'POST')?.path).toBe('/coach/training/attendance')
    expect(calls.some(call => call.path.startsWith('/reception'))).toBe(false)
  })
  it('R2-FE-05 labels automated consultation honestly and never calls a model endpoint', async () => {
    const { calls } = login('MEMBER', () => json(200, { mode: 'KNOWLEDGE_BASED', status: 'NO_ANSWER', answer: 'No grounded answer for this question.', historyStored: false, sources: [] }))
    renderApp('/member/assistant')
    expect(await screen.findByRole('heading', { name: 'Center assistant' })).toBeInTheDocument()
    await userEvent.click(screen.getByLabelText('Enable automated center consultation'))
    await userEvent.type(screen.getByRole('textbox', { name: /Center question/ }), 'Unknown center question')
    await userEvent.click(screen.getByRole('button', { name: 'Ask center assistant' }))
    expect(await screen.findByText('KNOWLEDGE_BASED - No grounded answer for this question.')).toBeInTheDocument()
    expect(calls.some(call => call.path === '/member/training/consultation')).toBe(true)
    expect(calls.some(call => /openai|gemini|chat\/completions/.test(call.path))).toBe(false)
  })
})
