import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../../api/tokenStorage'
import { backendError, installFakeFetch, json, MANAGER, MEMBER } from '../../../test/fakeBackend'
import { renderApp } from '../../../test/renderApp'
import { ASSISTANT_PATH, readAssistantChoice, safeReturnPath } from './assistantPreference'

const COACH = { id: 3, email: 'coach@sportify.demo', fullName: 'Daniel Smith', phone: '0901234568', role: 'COACH', status: 'ACTIVE' }
const RECEPTIONIST = { id: 5, email: 'jamie@sportify.demo', fullName: 'Jamie Tran', phone: '0901234570', role: 'RECEPTIONIST', status: 'ACTIVE' }
const OTHER_MEMBER = { ...MEMBER, id: 9, email: 'other@sportify.demo', fullName: 'Other Member' }

function backend(profile: Record<string, unknown> = MEMBER) {
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, profile)
    if (call.path.startsWith('/users/me/') || call.method !== 'GET') return backendError(404, 'Unexpected test request')
    return json(200, [])
  })
}

beforeEach(() => {
  window.localStorage.clear()
  vi.stubEnv('VITE_ASSISTANT_ENABLED', 'true')
  tokenStorage.startSession({ accessToken: 'member-test', refreshToken: 'refresh-test' })
})
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); window.localStorage.clear() })

describe('NX-31 member assistant entry points', () => {
  it('shows an opt-in card on My Profile and opens the consent screen with a way back', async () => {
    backend()
    const user = userEvent.setup()
    renderApp('/member/profile')
    const card = await screen.findByRole('region', { name: 'Assistant' })
    expect(card).toHaveTextContent('You choose whether to use it')
    await user.click(screen.getByRole('link', { name: 'Learn more' }))
    expect(await screen.findByRole('heading', { name: 'Member Assistant' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Before you start' })).toHaveTextContent('does not book sessions, take payments or change anything')
    expect(screen.queryByText('The assistant chat is not available yet')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back' })).toHaveAttribute('href', '/member/profile')
    expect(screen.getByRole('link', { name: 'Not now' })).toHaveAttribute('href', '/member/profile')
  })

  it('turning it on is stored for this account only and opens the chat slot', async () => {
    backend()
    const user = userEvent.setup()
    renderApp('/member/profile')
    await user.click(await screen.findByRole('link', { name: 'Learn more' }))
    await user.click(await screen.findByRole('button', { name: 'Turn on the assistant' }))
    expect(await screen.findByText('The assistant chat is not available yet')).toBeInTheDocument()
    expect(readAssistantChoice(MEMBER.id)).toBe('on')
    expect(readAssistantChoice(OTHER_MEMBER.id)).toBeNull()
  })

  it('does not carry one member choice over to another account on the same browser', async () => {
    window.localStorage.setItem('sportify.assistant.optIn.' + MEMBER.id, 'on')
    backend(OTHER_MEMBER)
    renderApp(ASSISTANT_PATH)
    expect(await screen.findByRole('region', { name: 'Before you start' })).toBeInTheDocument()
    expect(screen.queryByText('The assistant chat is not available yet')).not.toBeInTheDocument()
  })

  it('turning it off returns to the page the member came from and hides the entry points', async () => {
    window.localStorage.setItem('sportify.assistant.optIn.' + MEMBER.id, 'on')
    backend()
    const user = userEvent.setup()
    renderApp('/member/profile')
    await user.click(await screen.findByRole('link', { name: 'Open assistant' }))
    await user.click(await screen.findByRole('button', { name: 'Turn off the assistant' }))
    expect(await screen.findByRole('heading', { name: 'My Profile' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Assistant' })).not.toBeInTheDocument()
    expect(readAssistantChoice(MEMBER.id)).toBe('off')
  })

  it('adds no entry point and sends the route to the member home when the feature is off', async () => {
    vi.stubEnv('VITE_ASSISTANT_ENABLED', 'false')
    backend()
    const profile = renderApp('/member/profile')
    await screen.findByRole('heading', { name: 'My Profile' })
    expect(screen.queryByRole('region', { name: 'Assistant' })).not.toBeInTheDocument()
    profile.unmount()
    renderApp(ASSISTANT_PATH)
    expect(await screen.findByRole('heading', { name: 'Member Dashboard' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Member Assistant' })).not.toBeInTheDocument()
  })

  it('ignores an unsafe return target and falls back to the member home', async () => {
    backend()
    renderApp(ASSISTANT_PATH, { from: '//evil.example/steal' })
    expect(await screen.findByRole('link', { name: 'Back' })).toHaveAttribute('href', '/member')
  })
})

describe('NX-31 route protection', () => {
  it('sends a signed-out visitor to Log in', async () => {
    tokenStorage.clear()
    backend()
    renderApp(ASSISTANT_PATH)
    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Member Assistant' })).not.toBeInTheDocument()
  })

  it.each([
    ['Coach', COACH],
    ['Receptionist', RECEPTIONIST],
    ['Manager', MANAGER],
  ])('shows Access denied to the %s role', async (_name, profile) => {
    backend(profile)
    renderApp(ASSISTANT_PATH)
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Member Assistant' })).not.toBeInTheDocument()
  })
})

describe('NX-31 return path rules', () => {
  it('accepts only member area paths', () => {
    expect(safeReturnPath('/member')).toBe('/member')
    expect(safeReturnPath('/member/profile?tab=a')).toBe('/member/profile?tab=a')
    for (const bad of ['//evil.example', 'https://evil.example', '/manager', '/memberx', '/member\\evil', '', ASSISTANT_PATH, ASSISTANT_PATH + '?x=1', null, 7, 'a'.repeat(400)]) {
      expect(safeReturnPath(bad)).toBeNull()
    }
  })
})

describe('NX-31 booking and payment flows are not touched', () => {
  it('places the entry point only on the dashboard and My Profile', () => {
    const root = join(process.cwd(), 'src/pages')
    const users: string[] = []
    const walk = (dir: string) => {
      for (const name of readdirSync(dir)) {
        const path = join(dir, name)
        if (statSync(path).isDirectory()) walk(path)
        else if (/\.tsx$/.test(name) && !/\.test\.tsx$/.test(name) && readFileSync(path, 'utf8').includes('<AssistantEntry')) users.push(name)
      }
    }
    walk(root)
    expect(users.sort()).toEqual(['MemberDashboardPage.tsx', 'MyProfilePage.tsx'])
  })
})
