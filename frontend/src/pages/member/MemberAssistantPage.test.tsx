import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })
it('requires opt-in, shows grounded sources and clears ephemeral answers when disabled', async () => {
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  const { calls } = installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) : json(200, {
    mode: 'KNOWLEDGE_BASED', status: 'ANSWERED', answer: 'Active catalog package prices.', historyStored: false,
    sources: [{ kind: 'SPORT_PACKAGE', id: 3, title: 'Practice package', detail: '120000 VND base price.', path: '/member/packages/3' }],
  }))
  renderApp('/member/assistant')
  const ask = await screen.findByRole('button', { name: 'Ask center assistant' })
  expect(ask).toBeDisabled()
  expect(calls.filter(call => call.path.includes('/consultation'))).toHaveLength(0)
  await userEvent.click(screen.getByLabelText('Enable automated center consultation'))
  await userEvent.type(screen.getByRole('textbox', { name: /Center question/ }), 'What are package prices?')
  await userEvent.click(ask)
  expect(await screen.findByText('KNOWLEDGE_BASED - Active catalog package prices.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Open source: Practice package' })).toHaveAttribute('href', '/member/packages/3')
  expect(calls.find(call => call.path.includes('/consultation'))?.body).toEqual({ question: 'What are package prices?', optIn: true })
  await userEvent.click(screen.getByLabelText('Enable automated center consultation'))
  expect(screen.queryByText('120000 VND base price.')).not.toBeInTheDocument()
  expect(screen.getByRole('textbox', { name: /Center question/ })).toHaveValue('')
})
it('makes no consultation request when the feature flag is off', async () => {
  vi.stubEnv('VITE_TRAINING_CONSULTATION_ENABLED', 'false')
  tokenStorage.startSession({ accessToken: 'test', refreshToken: 'refresh' })
  const { calls } = installFakeFetch(() => json(200, MEMBER))
  renderApp('/member/assistant')
  expect(await screen.findByText('Automated consultation is disabled')).toBeInTheDocument()
  expect(calls.filter(call => call.path.includes('/consultation'))).toHaveLength(0)
})
