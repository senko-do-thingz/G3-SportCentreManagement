import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
const learningClass = { id: 1, name: 'Class skills', sportName: 'Badminton', coachName: 'Quan', level: 'BEGINNER', capacity: 10, active: true }
function login(role: string, handler: (call: RecordedCall) => Response | Promise<Response>) {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  return installFakeFetch(call => call.path === '/users/me' ? json(200, { ...MEMBER, role }) : handler(call))
}
afterEach(() => vi.unstubAllGlobals())
describe('Class learning Round 02', () => {
  it('keeps the current conversation fixed until its reply finishes saving', async () => {
    const question = { id: 7, classId: 1, className: 'Class skills', memberName: 'Member', subject: 'Footwork', description: 'How to practise?', status: 'OPEN', createdAt: '2026-11-02T10:00:00' }
    let finish!: (response: Response) => void
    const { calls } = login('COACH', call => {
      if (call.method === 'POST') return new Promise<Response>(resolve => { finish = resolve })
      return call.path.endsWith('/7') ? json(200, { question, messages: [] }) : json(200, [question])
    })
    renderApp('/coach/training/questions')
    const user = userEvent.setup()
    const questionOption = await screen.findByRole('button', { name: /Footwork/ })
    await user.click(questionOption)
    await user.type(await screen.findByLabelText('Coach reply and practice guidance'), 'Review the assigned plan.')
    await user.click(screen.getByRole('button', { name: 'Send message' }))
    expect(questionOption).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Refresh conversations' })).toBeDisabled()
    await act(async () => finish(json(201, { id: 9 })))
    expect(await screen.findByText('Message sent.')).toBeInTheDocument()
    expect(screen.getByLabelText('Coach reply and practice guidance')).toHaveValue('')
    expect(screen.getByRole('button', { name: /Footwork/ })).toBeEnabled()
    expect(calls.filter(call => call.method === 'POST').map(call => call.path)).toEqual(['/coach/training/questions/7/messages'])
  })

  it('shows a genuine empty coach inbox without fabricated questions', async () => {
    const { calls } = login('COACH', () => json(200, []))
    renderApp('/coach/training/questions')
    expect(await screen.findByRole('heading', { name: 'Member Questions' })).toBeInTheDocument()
    expect(await screen.findAllByText('No class questions yet')).not.toHaveLength(0)
    expect(screen.queryByRole('button', { name: /Footwork/ })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Coach reply and practice guidance')).not.toBeInTheDocument()
    expect(calls.filter(call => call.method === 'GET' && call.path === '/coach/training/questions')).toHaveLength(1)
  })

  it('loads the selected real thread and keeps messages in API order', async () => {
    const question = { id: 7, classId: 1, className: 'Class skills', memberName: 'Member', subject: 'Footwork', description: 'How to practise?', status: 'OPEN', createdAt: '2026-11-02T10:00:00' }
    login('COACH', call => call.path.endsWith('/7')
      ? json(200, { question, messages: [
        { id: 10, senderName: 'Coach', body: 'Second message returned first.', createdAt: '2026-11-02T10:02:00' },
        { id: 9, senderName: 'Member', body: 'First message returned second.', createdAt: '2026-11-02T10:01:00' },
      ] })
      : json(200, [question]))
    renderApp('/coach/training/questions')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Footwork/ }))
    expect(await screen.findByText('How to practise?')).toBeInTheDocument()
    const first = screen.getByText('Second message returned first.')
    const second = screen.getByText('First message returned second.')
    expect(first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(screen.getByText('Status: OPEN')).toBeInTheDocument()
    expect(screen.getByLabelText('Coach reply and practice guidance')).toBeInTheDocument()
  })

  it('refreshes a coach reply while preserving the member follow-up draft', async () => {
    const question = { id: 7, classId: 1, className: 'Class skills', memberName: 'Member', subject: 'Footwork', description: 'How to practise?', status: 'OPEN', createdAt: '2026-11-02T10:00:00' }
    let messages: { id: number; senderName: string; body: string }[] = []
    const { calls } = login('MEMBER', call => {
      if (call.path === '/member/training/classes') return json(200, [learningClass])
      if (call.path === '/member/training/questions') return json(200, [question])
      return json(200, { question, messages })
    })
    renderApp('/member/training/questions')
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Class question'), '7')
    await user.type(await screen.findByLabelText('Follow-up question'), 'My unsent follow-up')
    messages = [{ id: 9, senderName: 'Quan', body: 'Review the footwork steps in your assigned plan.' }]
    await user.click(screen.getByRole('button', { name: 'Refresh conversations' }))
    expect(await screen.findByText('Review the footwork steps in your assigned plan.')).toBeInTheDocument()
    expect(screen.getByLabelText('Follow-up question')).toHaveValue('My unsent follow-up')
    expect(screen.getByLabelText('Class question')).toHaveValue('7')
    expect(calls.every(call => call.method === 'GET')).toBe(true)
  })

  it('clears reply feedback and draft when choosing another conversation', async () => {
    const questions = [7, 8].map(id => ({ id, classId: 1, className: 'Class skills', memberName: 'Member', subject: `Practice ${id}`, description: `Question ${id}`, status: 'OPEN', createdAt: '2026-11-02T10:00:00' }))
    login('COACH', call => {
      if (call.method === 'POST') return backendError(503, 'Reply could not be saved')
      const question = questions.find(item => call.path.endsWith(`/${item.id}`))
      return question ? json(200, { question, messages: [] }) : json(200, questions)
    })
    renderApp('/coach/training/questions')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Practice 7/ }))
    await user.type(await screen.findByLabelText('Coach reply and practice guidance'), 'Draft for first member question')
    await user.click(screen.getByRole('button', { name: 'Send message' }))
    expect(await screen.findByText('Reply could not be saved')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Practice 8/ }))
    expect(await screen.findByText('Question 8')).toBeInTheDocument()
    expect(screen.getByLabelText('Coach reply and practice guidance')).toHaveValue('')
    expect(screen.queryByText('Reply could not be saved')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send message' })).toBeDisabled()
  })

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
      if (call.path === '/coach/training/sessions') return json(200, [{ id: 5, sessionId: '021126BM01', className: 'Class skills', date: '2026-11-02', startTime: '09:00', endTime: '10:00', attendanceDateOpen: true, bookedCount: 1, markedCount: 0 }])
      if (call.path === '/coach/training/sessions/by-code/021126BM01/members') return json(200, [{ bookingId: 3, memberId: 42, memberName: 'Member', attendanceId: null, status: null, notes: null, resultId: null }])
      if (call.path === '/coach/training/attendance') return json(201, { id: 8 })
      return backendError(404, 'Unexpected request')
    })
    renderApp('/coach/training/attendance')
    await userEvent.click(await screen.findByRole('button', { name: /Select session 021126BM01/ }))
    await screen.findByRole('form', { name: /Record attendance for Member/ })
    await userEvent.click(screen.getByRole('button', { name: 'Save attendance' }))
    expect(await screen.findByText('Attendance saved and the list updated.')).toBeInTheDocument()
    expect(calls.find(call => call.method === 'POST')?.path).toBe('/coach/training/attendance')
    expect(calls.some(call => call.path.startsWith('/reception'))).toBe(false)
  })
  it('R05-FE-01 retains the operation key on question retry and renews it after success', async () => {
    const question = { id: 7, classId: 1, className: 'Class skills', memberName: 'Member', subject: 'Practice', description: 'Same question', status: 'OPEN', createdAt: '2026-11-02T10:00:00' }
    let attempts = 0
    const { fetchMock } = login('MEMBER', call => {
      if (call.path === '/member/training/classes') return json(200, [learningClass])
      if (call.path === '/member/training/questions' && call.method === 'POST') return ++attempts === 1 ? backendError(503, 'Try again') : json(201, { id: 7 })
      if (call.path === '/member/training/questions') return json(200, [question])
      return json(200, { question, messages: [] })
    })
    renderApp('/member/training/questions?classId=1')
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText(/Question subject/), 'Practice')
    await user.type(screen.getByLabelText('Practice question'), 'Same question')
    await user.click(screen.getByRole('button', { name: 'Ask coach' }))
    expect(await screen.findByText('Try again')).toBeInTheDocument()
    expect(screen.getByLabelText('Practice question')).toHaveValue('Same question')
    await user.click(screen.getByRole('button', { name: 'Ask coach' }))
    expect(await screen.findByText('Question sent to your coach.')).toBeInTheDocument()
    await user.type(screen.getByLabelText(/Question subject/), 'Practice')
    await user.type(screen.getByLabelText('Practice question'), 'Same question')
    await user.click(screen.getByRole('button', { name: 'Ask coach' }))
    expect(await screen.findByText('Question sent to your coach.')).toBeInTheDocument()
    const keys = fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST').map(([, init]) => new Headers(init?.headers).get('Idempotency-Key'))
    expect(keys).toHaveLength(3)
    expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/)
    expect(keys[1]).toBe(keys[0])
    expect(keys[2]).not.toBe(keys[0])
  })
  it('R05-FE-02 retains reply identity after failure and gives edited content a new key', async () => {
    const question = { id: 7, classId: 1, className: 'Class skills', memberName: 'Member', subject: 'Practice', description: 'Same question', status: 'OPEN', createdAt: '2026-11-02T10:00:00' }
    const { fetchMock } = login('COACH', call => call.method === 'POST' ? backendError(503, 'Retry reply') :
      call.path.endsWith('/7') ? json(200, { question, messages: [] }) : json(200, [question]))
    renderApp('/coach/training/questions')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Practice/ }))
    await user.type(await screen.findByLabelText('Coach reply and practice guidance'), 'Original')
    await user.click(screen.getByRole('button', { name: 'Send message' }))
    expect(await screen.findByText('Retry reply')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Send message' }))
    expect(await screen.findByText('Retry reply')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Coach reply and practice guidance'), ' edited')
    await user.click(screen.getByRole('button', { name: 'Send message' }))
    expect(await screen.findByText('Retry reply')).toBeInTheDocument()
    const keys = fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST').map(([, init]) => new Headers(init?.headers).get('Idempotency-Key'))
    expect(keys).toHaveLength(3)
    expect(keys[1]).toBe(keys[0])
    expect(keys[2]).not.toBe(keys[0])
  })
  it('R2-FE-05 labels automated consultation honestly and never calls a model endpoint', async () => {
    const { calls } = login('MEMBER', () => json(200, { mode: 'KNOWLEDGE_BASED', status: 'NO_ANSWER', answer: 'No grounded answer for this question.', historyStored: false, sources: [] }))
    renderApp('/member/assistant')
    expect(await screen.findByRole('heading', { name: 'Center assistant' })).toBeInTheDocument()
    await userEvent.type(screen.getByRole('textbox', { name: /Your question/ }), 'Unknown center question')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
    expect(await screen.findByText('No grounded answer for this question.')).toBeInTheDocument()
    expect(calls.some(call => call.path === '/assistant/chat')).toBe(true)
    expect(calls.some(call => /openai|gemini|chat\/completions/.test(call.path))).toBe(false)
  })
  it('renders contextual SVG icons for Member Questions queue metrics and omits decorative square markers', async () => {
    const question = { id: 7, classId: 1, className: 'Class skills', memberName: 'Member', subject: 'Footwork', description: 'How to practise?', status: 'OPEN', createdAt: '2026-11-02T10:00:00' }
    login('COACH', () => json(200, [question]))
    renderApp('/coach/training/questions')

    expect(await screen.findByRole('heading', { name: 'Member Questions' })).toBeInTheDocument()
    expect(await screen.findByText('Questions in queue')).toBeInTheDocument()
    const questionSummary = screen.getByRole('region', { name: 'Question queue summary' })
    expect(questionSummary.querySelectorAll('svg.coach-schedule-metric__icon')).toHaveLength(2)
    expect(questionSummary.querySelectorAll('.coach-schedule-metric__mark')).toHaveLength(0)
  })
})
