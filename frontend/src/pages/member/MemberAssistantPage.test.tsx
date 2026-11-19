import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { sporiApi } from '../../api/sporiApi'
import { tokenStorage } from '../../api/tokenStorage'
import { installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })
it('asks without a consent checkbox and shows grounded sources', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  const { calls } = installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) : json(200, {
    mode: 'KNOWLEDGE_BASED', status: 'ANSWERED', answer: 'Active catalog package prices.', historyStored: false,
    sources: [{ kind: 'SPORT_PACKAGE', id: 3, title: 'Practice package', detail: '120000 VND base price.', path: '/member/packages/3' }],
  }))
  renderApp('/member/assistant')
  const ask = await screen.findByRole('button', { name: 'Ask' })
  expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  expect(calls.filter(call => call.path.includes('/assistant/chat'))).toHaveLength(0)
  await userEvent.type(screen.getByRole('textbox', { name: /Your question/ }), 'What are package prices?')
  await userEvent.click(ask)
  expect(await screen.findByText('Active catalog package prices.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Practice package' })).toHaveAttribute('href', '/member/packages/3')
  expect(calls.find(call => call.path.includes('/assistant/chat'))?.body).toEqual({ question: 'What are package prices?'})
})
it('shows a short privacy note', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) : json(200, []))
  renderApp('/member/assistant')
  expect(await screen.findByLabelText('Privacy note')).toHaveTextContent('Personal details are never sent to AI.')
})
it('fills example questions and labels curated FAQ sources without calling the API early', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  const { calls } = installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) : json(200, {
    mode: 'KNOWLEDGE_BASED', status: 'ANSWERED', answer: 'General exercise FAQ (curated center guidance, not personal or medical advice): Warm up gradually.', historyStored: false,
    sources: [{ kind: 'CURATED_FAQ', id: 1, title: 'Center FAQ: Warming up', detail: 'Fixed answer from the curated center FAQ (center-faq-v1).', path: '/member/training/questions' }],
  }))
  renderApp('/member/assistant')
  await userEvent.click(await screen.findByRole('button', { name: 'Which classes are available in the next two weeks?' }))
  expect(screen.getByRole('textbox', { name: /Your question/ })).toHaveValue('Which classes are available in the next two weeks?')
  await userEvent.click(screen.getByRole('button', { name: 'How should I warm up before practice?' }))
  expect(screen.getByRole('textbox', { name: /Your question/ })).toHaveValue('How should I warm up before practice?')
  expect(calls.filter(call => call.path.includes('/assistant/chat'))).toHaveLength(0)
  await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
  expect(await screen.findByText(/^General exercise FAQ/)).toBeInTheDocument()
  expect(screen.getByText('Curated center FAQ (fixed text)')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Center FAQ: Warming up' })).toHaveAttribute('href', '/member/training/questions')
  expect(calls.filter(call => call.path.includes('/assistant/chat'))).toHaveLength(1)
})
it('shows a human handoff for NO_ANSWER and submits with Enter from the question field', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  const { calls } = installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) : json(200, {
    mode: 'KNOWLEDGE_BASED', status: 'NO_ANSWER', answer: 'I cannot assess personal training or health concerns. Ask your assigned human coach about your practice plan.', historyStored: false,
    sources: [{ kind: 'HUMAN_COACH', id: 0, title: 'Ask your coach', detail: 'Private human conversation; no automated coach reply.', path: '/member/training/questions' }],
  }))
  renderApp('/member/assistant')
  await userEvent.type(await screen.findByRole('textbox', { name: /Your question/ }), 'My knee hurts after practice{Enter}')
  expect(await screen.findByText('Answer unavailable')).toBeInTheDocument()
  expect(screen.getByText('This assistant does not answer personal, medical or private questions. Ask your human coach or reception.')).toBeInTheDocument()
  expect(screen.getByText('Human coach')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Ask your coach' })).toHaveAttribute('href', '/member/training/questions')
  expect(calls.find(call => call.path.includes('/assistant/chat'))?.body).toEqual({ question: 'My knee hurts after practice'})
})
it('does not keep a previous answer after leaving the assistant route', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) : call.path.includes('/assistant/chat') ? json(200, {
    mode: 'KNOWLEDGE_BASED', status: 'ANSWERED', answer: 'Booking guidance answer.', historyStored: false, sources: [],
  }) : json(200, []))
  renderApp('/member/assistant')
  await userEvent.type(await screen.findByRole('textbox', { name: /Your question/ }), 'How do I book?')
  await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
  expect(await screen.findByText('Booking guidance answer.')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('link', { name: 'Contact reception' }))
  await userEvent.click((await screen.findAllByRole('link', { name: 'Open center assistant' }))[0])
  expect(await screen.findByRole('textbox', { name: /Your question/ })).toHaveValue('')
  expect(screen.queryByText('Booking guidance answer.')).not.toBeInTheDocument()
})
it('makes no consultation request when the feature flag is off', async () => {
  vi.stubEnv('VITE_TRAINING_CONSULTATION_ENABLED', 'false')
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  const { calls } = installFakeFetch(() => json(200, MEMBER))
  renderApp('/member/assistant')
  expect(await screen.findByText('Automated consultation is disabled')).toBeInTheDocument()
  expect(calls.filter(call => call.path.includes('/assistant/chat'))).toHaveLength(0)
})
it('labels a Vietnamese answer in Vietnamese', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) : json(200, {
    mode: 'AI_ASSISTED', status: 'ANSWERED', answer: 'Gói tập và thẻ thành viên đang hoạt động của bạn.', historyStored: false, language: 'vi',
    sources: [{ kind: 'MY_PACKAGE', id: 5, title: 'Gói cầu lông', detail: 'Cầu lông: còn 6/8 buổi.', path: '/member/membership' }],
  }))
  renderApp('/member/assistant')
  await userEvent.type(await screen.findByRole('textbox', { name: /Your question/ }), 'Gói của tôi còn bao nhiêu buổi?')
  await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
  expect(await screen.findByText('Câu trả lời từ dữ liệu trung tâm')).toBeInTheDocument()
  expect(screen.getByText('Cách tìm ra câu trả lời: AI đã hiểu câu hỏi của bạn.')).toBeInTheDocument()
  expect(screen.getByText('Gói tập của bạn')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Gói cầu lông' })).toHaveAttribute('href', '/member/membership')
  expect(screen.getByRole('link', { name: 'Liên hệ lễ tân về câu trả lời này' })).toBeInTheDocument()
})
it('keeps a conversation, sends earlier turns as history and shows a Gemini reply with bullets', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  let turn = 0
  const { calls } = installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) : !call.path.includes('/assistant/chat') ? json(200, []) : json(200, ++turn === 1
    ? { mode: 'AI_GENERATED', status: 'ANSWERED', answer: 'We have two packages:\n- **Basic** 500000 VND\n- Pro 900000 VND', historyStored: false, sources: [], language: 'en' }
    : { mode: 'AI_GENERATED', status: 'ANSWERED', answer: 'Pro includes 12 sessions.', historyStored: false, sources: [], language: 'en' }))
  renderApp('/member/assistant')
  const box = await screen.findByRole('textbox', { name: /Your question/ })
  await userEvent.type(box, 'What packages are there?{Enter}')
  expect(await screen.findByText('Basic')).toContainHTML('strong')
  expect(screen.getByText('Pro 900000 VND').tagName).toBe('LI')
  expect(screen.getByText('How this answer was found: Written by Gemini AI from Sportify data.')).toBeInTheDocument()
  await userEvent.type(box, 'And the second one?{Enter}')
  expect(await screen.findByText('Pro includes 12 sessions.')).toBeInTheDocument()
  expect(screen.getByText('What packages are there?')).toBeInTheDocument()
  const consults = calls.filter(call => call.path.includes('/assistant/chat'))
  expect(consults[1].body).toEqual({ question: 'And the second one?', history: [
    { role: 'user', text: 'What packages are there?' },
    { role: 'assistant', text: 'We have two packages:\n- **Basic** 500000 VND\n- Pro 900000 VND' },
  ] })
  await userEvent.click(screen.getByRole('button', { name: 'New chat' }))
  expect(screen.queryByText('Pro includes 12 sessions.')).not.toBeInTheDocument()
})

it('does not duplicate "Ask your coach" source when handoff button is already present', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) : json(200, {
    mode: 'AI_GENERATED',
    status: 'ANSWERED',
    answer: 'Please speak directly with your coach.',
    historyStored: false,
    handoff: 'COACH',
    sources: [{
      kind: 'HUMAN_COACH',
      id: 0,
      title: 'Ask your coach',
      detail: 'Private human conversation; no automated coach reply.',
      path: '/member/training/questions'
    }],
  }))
  renderApp('/member/assistant')
  const box = await screen.findByRole('textbox', { name: /Your question/ })
  await userEvent.type(box, 'What is my homework?{Enter}')
  expect(await screen.findByText('Please speak directly with your coach.')).toBeInTheDocument()

  // Verify handoff button is rendered
  const links = screen.getAllByRole('link', { name: 'Ask your coach' })
  // Exactly 1 link for "Ask your coach", not duplicated
  expect(links).toHaveLength(1)
  expect(screen.queryByText('Private human conversation; no automated coach reply.')).not.toBeInTheDocument()
})

it('renders a circular send button with paper airplane icon', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) : json(200, []))
  renderApp('/member/assistant')
  const askBtn = await screen.findByRole('button', { name: 'Ask' })
  expect(askBtn).toHaveClass('spori-composer__submit')
  expect(askBtn.style.borderRadius).toBe('50%')
})

it('lists past sessions, allows selecting, renaming, and deleting', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  const mockSessions = [
    { id: 101, title: 'Workout schedule inquiry', archived: false, messageCount: 2, createdAt: '2026-11-15T10:00:00', updatedAt: '2026-11-15T10:05:00' },
  ]
  const mockSessionDetail = {
    id: 101,
    title: 'Workout schedule inquiry',
    archived: false,
    createdAt: '2026-11-15T10:00:00',
    updatedAt: '2026-11-15T10:05:00',
    messages: [
      { id: 1, senderType: 'USER', content: 'What is my schedule this week?', sentAt: '2026-11-15T10:01:00' },
      { id: 2, senderType: 'ASSISTANT', content: 'Your schedule is on Tuesday and Thursday.', sentAt: '2026-11-15T10:01:30' },
    ]
  }

  const { calls } = installFakeFetch(call => {
    if (call.path === '/users/me') return json(200, MEMBER)
    if (call.path.includes('/spori-ai/sessions/101/title')) {
      return json(200, { ...mockSessions[0], title: 'Yoga schedule' })
    }
    if (call.path === '/spori-ai/sessions/101' && call.method === 'DELETE') {
      return new Response(null, { status: 204 })
    }
    if (call.path.includes('/spori-ai/sessions/101')) {
      return json(200, mockSessionDetail)
    }
    if (call.path.includes('/spori-ai/sessions')) {
      return json(200, mockSessions)
    }
    return json(200, [])
  })

  renderApp('/member/assistant')

  // Find the session in the sidebar
  expect(await screen.findByText('Workout schedule inquiry')).toBeInTheDocument()

  // Select the session
  await userEvent.click(screen.getByText('Workout schedule inquiry'))
  expect(await screen.findByText('Your schedule is on Tuesday and Thursday.')).toBeInTheDocument()
  expect(screen.getByText('What is my schedule this week?')).toBeInTheDocument()

  // Rename session
  const renameBtn = screen.getByRole('button', { name: 'Rename chat' })
  await userEvent.click(renameBtn)
  const editInput = screen.getByDisplayValue('Workout schedule inquiry')
  await userEvent.clear(editInput)
  await userEvent.type(editInput, 'Yoga schedule{Enter}')
  expect(calls.some(c => c.path.includes('/spori-ai/sessions/101/title') && (c.body as { title?: string })?.title === 'Yoga schedule')).toBe(true)

  // Xoa hoi thoai bang Modal trong ung dung thay vi window.confirm
  const confirmSpy = vi.spyOn(window, 'confirm')
  const deleteBtn = screen.getByRole('button', { name: 'Delete chat' })
  await userEvent.click(deleteBtn)
  expect(confirmSpy).not.toHaveBeenCalled()
  expect(await screen.findByRole('heading', { name: 'Delete conversation' })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Delete' }))
  expect(calls.some(c => c.path === '/spori-ai/sessions/101' && c.method === 'DELETE')).toBe(true)
  confirmSpy.mockRestore()
})

it('prevents spamming new chat sessions when current chat is empty', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  const mockSessions = [
    { id: 201, title: 'Active Chat', archived: false, messageCount: 1, createdAt: '2026-11-15T10:00:00' },
  ]
  const mockDetail = {
    id: 201,
    title: 'Active Chat',
    archived: false,
    messages: [
      { id: 1, senderType: 'USER', content: 'Hello', sentAt: '2026-11-15T10:01:00' }
    ]
  }

  const { calls } = installFakeFetch(call => {
    if (call.path === '/users/me') return json(200, MEMBER)
    if (call.path === '/spori-ai/sessions' && call.method === 'POST') {
      return json(201, { id: 202, title: 'New conversation', archived: false, messages: [] })
    }
    if (call.path.includes('/spori-ai/sessions/201')) {
      return json(200, mockDetail)
    }
    if (call.path.includes('/spori-ai/sessions/202')) {
      return json(200, { id: 202, title: 'New conversation', archived: false, messages: [] })
    }
    if (call.path.includes('/spori-ai/sessions')) {
      return json(200, mockSessions)
    }
    return json(200, [])
  })

  renderApp('/member/assistant')
  await screen.findByText('Active Chat')

  // When current chat is empty (activeSessionId is null and no messages), clicking new conversation does not call backend
  const newConvBtn = screen.getByRole('button', { name: 'New conversation' })
  await userEvent.click(newConvBtn)
  expect(calls.filter(c => c.path === '/spori-ai/sessions' && c.method === 'POST')).toHaveLength(0)

  // Select an active session that has messages
  await userEvent.click(screen.getByText('Active Chat'))
  expect(await screen.findByText('Hello')).toBeInTheDocument()

  // Now current chat is NOT empty. Clicking new conversation should create a new session
  await userEvent.click(newConvBtn)
  expect(calls.filter(c => c.path === '/spori-ai/sessions' && c.method === 'POST')).toHaveLength(1)

  // Now that we are in the newly created empty session (id 202, 0 messages), clicking new conversation again should NOT call POST
  await userEvent.click(newConvBtn)
  expect(calls.filter(c => c.path === '/spori-ai/sessions' && c.method === 'POST')).toHaveLength(1)
})

it('aborts an in-flight request when clicking Stop generating and shows the stopped notice', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  let aborted = false
  installFakeFetch((call) => call.path === '/users/me' ? json(200, MEMBER) : json(200, []))
  const chatSpy = vi.spyOn(sporiApi, 'chat').mockImplementationOnce((_q, _h, signal) => new Promise((_resolve, reject) => {
    signal?.addEventListener('abort', () => {
      aborted = true
      reject(new DOMException('Aborted', 'AbortError'))
    })
  }))

  renderApp('/member/assistant')
  const box = await screen.findByRole('textbox', { name: /Your question/ })
  await userEvent.type(box, 'Hello Spori{Enter}')

  const stopBtn = await screen.findByRole('button', { name: 'Stop generating' })
  await userEvent.click(stopBtn)

  expect(aborted).toBe(true)
  expect(await screen.findByText('You stopped this task.')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Stop generating' })).not.toBeInTheDocument()
  chatSpy.mockRestore()
})
