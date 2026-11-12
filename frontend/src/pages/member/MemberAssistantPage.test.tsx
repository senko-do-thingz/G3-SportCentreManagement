import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
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
  expect(calls.filter(call => call.path.includes('/consultation'))).toHaveLength(0)
  await userEvent.type(screen.getByRole('textbox', { name: /Your question/ }), 'What are package prices?')
  await userEvent.click(ask)
  expect(await screen.findByText('KNOWLEDGE_BASED - Active catalog package prices.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Open source: Practice package' })).toHaveAttribute('href', '/member/packages/3')
  expect(calls.find(call => call.path.includes('/consultation'))?.body).toEqual({ question: 'What are package prices?', optIn: true })
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
  expect(calls.filter(call => call.path.includes('/consultation'))).toHaveLength(0)
  await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
  expect(await screen.findByText(/^KNOWLEDGE_BASED - General exercise FAQ/)).toBeInTheDocument()
  expect(screen.getByText('Source type: Curated center FAQ (fixed text)')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Open source: Center FAQ: Warming up' })).toHaveAttribute('href', '/member/training/questions')
  expect(calls.filter(call => call.path.includes('/consultation'))).toHaveLength(1)
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
  expect(screen.getByText('Source type: Human coach')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Open source: Ask your coach' })).toHaveAttribute('href', '/member/training/questions')
  expect(calls.find(call => call.path.includes('/consultation'))?.body).toEqual({ question: 'My knee hurts after practice', optIn: true })
})
it('does not keep a previous answer after leaving the assistant route', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) : call.path.includes('/consultation') ? json(200, {
    mode: 'KNOWLEDGE_BASED', status: 'ANSWERED', answer: 'Booking guidance answer.', historyStored: false, sources: [],
  }) : json(200, []))
  renderApp('/member/assistant')
  await userEvent.type(await screen.findByRole('textbox', { name: /Your question/ }), 'How do I book?')
  await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
  expect(await screen.findByText('KNOWLEDGE_BASED - Booking guidance answer.')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('link', { name: 'Back to previous page' }))
  await userEvent.click((await screen.findAllByRole('link', { name: 'Open center assistant' }))[0])
  expect(await screen.findByRole('textbox', { name: /Your question/ })).toHaveValue('')
  expect(screen.queryByText('KNOWLEDGE_BASED - Booking guidance answer.')).not.toBeInTheDocument()
})
it('makes no consultation request when the feature flag is off', async () => {
  vi.stubEnv('VITE_TRAINING_CONSULTATION_ENABLED', 'false')
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  const { calls } = installFakeFetch(() => json(200, MEMBER))
  renderApp('/member/assistant')
  expect(await screen.findByText('Automated consultation is disabled')).toBeInTheDocument()
  expect(calls.filter(call => call.path.includes('/consultation'))).toHaveLength(0)
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
  expect(screen.getByText('Loại nguồn: Gói tập của bạn')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Mở nguồn: Gói cầu lông' })).toHaveAttribute('href', '/member/membership')
  expect(screen.getByRole('link', { name: 'Liên hệ lễ tân về câu trả lời này' })).toBeInTheDocument()
})
