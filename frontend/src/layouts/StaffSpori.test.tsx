import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../api/tokenStorage'
import { installFakeFetch, json, MANAGER } from '../test/fakeBackend'
import { renderApp } from '../test/renderApp'

afterEach(() => vi.unstubAllGlobals())

const COACH = { ...MANAGER, id: 9, email: 'coach@sportify.demo', fullName: 'Casey Tran', role: 'COACH' }

function staffBackend(user: typeof MANAGER, answer: object) {
  tokenStorage.startSession({ accessToken: 'staff-access', refreshToken: 'staff-refresh' })
  return installFakeFetch(call => call.path === '/users/me' ? json(200, user)
    : call.path === '/assistant/chat' ? json(200, answer)
    : json(200, []))
}

describe('Spori in staff workspaces', () => {
  it('lets a Manager chat in the floating panel without leaving the page', async () => {
    const { calls } = staffBackend(MANAGER, {
      mode: 'AI_GENERATED', status: 'ANSWERED', answer: 'Reception handles refunds; I cannot approve them.', historyStored: false,
      sources: [{ kind: 'STAFF_OVERVIEW', id: 0, title: 'Open support requests', detail: '3', path: '' }], language: 'en', handoff: 'RECEPTIONIST',
    })
    const user = userEvent.setup()
    renderApp('/manager')

    await user.click(await screen.findByRole('button', { name: 'Ask Spori AI' }))
    const dialog = screen.getByRole('dialog', { name: 'Spori AI' })
    expect(within(dialog).queryByRole('link', { name: 'Open full assistant' })).not.toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Give me an overview of the center today' })).toBeInTheDocument()
    await user.type(within(dialog).getByRole('textbox', { name: 'Ask Spori a question' }), 'How do refunds work?')
    await user.click(within(dialog).getByRole('button', { name: 'Send' }))

    expect(await within(dialog).findByText('Reception handles refunds; I cannot approve them.')).toBeInTheDocument()
    expect(within(dialog).getByText('Handled by: Reception team')).toBeInTheDocument()
    expect(within(dialog).getByText('Open support requests')).toBeInTheDocument()
    expect(within(dialog).queryByRole('link', { name: /Open support requests/ })).not.toBeInTheDocument()
    expect(calls.find(call => call.path === '/assistant/chat')?.body).toEqual({ question: 'How do refunds work?' })
    expect(screen.queryByRole('heading', { name: 'Center assistant' })).not.toBeInTheDocument()
  })

  it('shows Coach prompts and routes a training concern to the coaching team', async () => {
    staffBackend(COACH, {
      mode: 'AI_GENERATED', status: 'ANSWERED', answer: 'Attendance corrections are made by coaches in the session page.', historyStored: false,
      sources: [], language: 'en', handoff: 'COACH',
    })
    const user = userEvent.setup()
    renderApp('/coach')

    await user.click(await screen.findByRole('button', { name: 'Ask Spori AI' }))
    const dialog = screen.getByRole('dialog', { name: 'Spori AI' })
    await user.click(within(dialog).getByRole('button', { name: 'Show my sessions for the next 7 days' }))
    await user.click(within(dialog).getByRole('button', { name: 'Send' }))

    expect(await within(dialog).findByText('Handled by: Coaching team')).toBeInTheDocument()
  })
})
