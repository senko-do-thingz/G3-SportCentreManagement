import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MANAGER, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
import { readAssistantChoice } from './assistantPreference'

const COACH = { id: 3, email: 'coach@sportify.demo', fullName: 'Daniel Smith', phone: '0901234568', role: 'COACH', status: 'ACTIVE' }
const RECEPTIONIST = { id: 5, email: 'jamie@sportify.demo', fullName: 'Jamie Tran', phone: '0901234570', role: 'RECEPTIONIST', status: 'ACTIVE' }
const OTHER_MEMBER = { ...MEMBER, id: 9, email: 'other@sportify.demo', fullName: 'Other Member' }
const FROM = '/member/assistant?returnTo=%2Fmember%2Ftraining%2Fplans%3Fview%3Dweek'

function backend(profile: Record<string, unknown> = MEMBER) {
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, profile)
    if (call.method !== 'GET') return backendError(404, 'Unexpected test request')
    return json(200, [])
  })
}

beforeEach(() => {
  window.localStorage.clear()
  vi.stubEnv('VITE_ASSISTANT_ENABLED', 'true')
  tokenStorage.startSession({ accessToken: 'member-test', refreshToken: 'refresh-test' })
})
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); window.localStorage.clear() })

describe('NX-31 member assistant opt-in', () => {
  it('asks for consent first, then opens the chat slot, and remembers it for this account only', async () => {
    backend()
    const user = userEvent.setup()
    renderApp(FROM)
    const card = await screen.findByRole('region', { name: 'Before you start' })
    expect(card).toHaveTextContent('does not book sessions, take payments or change anything')
    expect(screen.queryByText('Chat is not connected yet')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Not now' })).toHaveAttribute('href', '/member/training/plans?view=week')
    await user.click(screen.getByRole('button', { name: 'Turn on the assistant' }))
    expect(await screen.findByText('Chat is not connected yet')).toBeInTheDocument()
    expect(readAssistantChoice(MEMBER.id)).toBe('on')
    expect(readAssistantChoice(OTHER_MEMBER.id)).toBeNull()
  })

  it('does not carry one member choice over to another account on the same browser', async () => {
    window.localStorage.setItem('sportify.assistant.optIn.' + MEMBER.id, 'on')
    backend(OTHER_MEMBER)
    renderApp(FROM)
    expect(await screen.findByRole('region', { name: 'Before you start' })).toBeInTheDocument()
    expect(screen.queryByText('Chat is not connected yet')).not.toBeInTheDocument()
  })

  it('turns off from the page and returns to where the member came from', async () => {
    window.localStorage.setItem('sportify.assistant.optIn.' + MEMBER.id, 'on')
    backend()
    const user = userEvent.setup()
    renderApp(FROM)
    await user.click(await screen.findByRole('button', { name: 'Turn off the assistant' }))
    expect(await screen.findByRole('heading', { name: 'Training plans' })).toBeInTheDocument()
    expect(readAssistantChoice(MEMBER.id)).toBe('off')
    renderApp(FROM)
    expect(await screen.findAllByText(/You turned the assistant off/)).not.toHaveLength(0)
  })

  it('hides the header link and sends the route to the member home when the feature is off', async () => {
    vi.stubEnv('VITE_ASSISTANT_ENABLED', 'false')
    backend()
    const training = renderApp('/member/training/plans')
    const header = await screen.findByRole('navigation', { name: 'Member navigation' })
    expect(within(header).queryByRole('link', { name: 'Open AI Assistant' })).not.toBeInTheDocument()
    training.unmount()
    renderApp(FROM)
    expect(await screen.findByRole('heading', { name: 'Member Dashboard' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Before you start' })).not.toBeInTheDocument()
  })

  it('shows the header link when the feature is on', async () => {
    backend()
    renderApp('/member/training/plans')
    const header = await screen.findByRole('navigation', { name: 'Member navigation' })
    expect(within(header).getByRole('link', { name: 'Open AI Assistant' })).toBeInTheDocument()
  })
})

describe('NX-31 route protection', () => {
  it('sends a signed-out visitor to Log in without any request', async () => {
    tokenStorage.clear()
    const { calls } = backend()
    renderApp(FROM)
    expect(await screen.findByLabelText(/email address/i)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Before you start' })).not.toBeInTheDocument()
    expect(calls).toHaveLength(0)
  })

  it.each([
    ['Coach', COACH],
    ['Receptionist', RECEPTIONIST],
    ['Manager', MANAGER],
  ])('shows Access denied to the %s role', async (_name, profile) => {
    backend(profile)
    renderApp(FROM)
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Before you start' })).not.toBeInTheDocument()
    expect(screen.queryByText('Chat is not connected yet')).not.toBeInTheDocument()
  })

  it('falls back to the member home for an unsafe return target', async () => {
    backend()
    renderApp('/member/assistant?returnTo=%2F%2Fevil.example%2Fsteal')
    expect(await screen.findByRole('link', { name: 'Not now' })).toHaveAttribute('href', '/member')
  })
})
