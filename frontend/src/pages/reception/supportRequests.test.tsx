import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

const ticket = { id: 8, code: 'SR-8', memberId: 42, memberName: 'Lan', category: 'GENERAL', subject: 'Opening hours', description: 'Weekend hours', status: 'OPEN', resolutionNotes: null }
afterEach(() => vi.unstubAllGlobals())
function setup(role = 'RECEPTIONIST') {
  tokenStorage.startSession({ accessToken: 'support-test', refreshToken: 'refresh' })
  let created = false
  let fail = true
  const recorder = installFakeFetch(call => {
    if (call.path === '/users/me') return json(200, { ...MEMBER, role })
    if (call.path.startsWith('/members?')) return json(200, { content: [{ userId: 42, fullName: 'Lan', memberCode: 'M42' }], totalElements: 1, totalPages: 1, number: 0, size: 10 })
    if (call.path === '/reception/support-requests' && call.method === 'POST') {
      if (fail) { fail = false; return backendError(500, 'Try again') }
      created = true; return json(201, ticket)
    }
    if (call.path.startsWith('/reception/support-requests?') || call.path === '/reception/support-requests') return json(200, created ? [ticket] : [])
    if (call.path === '/reception/support-requests/8') return json(200, ticket)
    return backendError(404, 'Not found')
  })
  renderApp('/reception/support')
  return recorder
}
describe('Reception support desktop', () => {
  it('retains an intake key on retry and reads the persisted detail', async () => {
    const { calls, fetchMock } = setup()
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText(/^Member/), '42')
    await user.type(screen.getByLabelText(/^Subject/), ticket.subject)
    await user.type(screen.getByLabelText(/^Issue details/), ticket.description)
    await user.click(screen.getByRole('button', { name: 'Create request' }))
    await screen.findByText(/Try again/)
    await user.click(screen.getByRole('button', { name: 'Create request' }))
    expect(await screen.findByText('Request SR-8')).toBeInTheDocument()
    const posts = calls.filter(call => call.method === 'POST')
    expect(posts).toHaveLength(2)
    const requests = fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')
    const keys = requests.map(([, init]) => new Headers(init?.headers).get('Idempotency-Key'))
    expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/)
    expect(keys[1]).toBe(keys[0])
    expect(await screen.findByLabelText('Next status')).toHaveValue('IN_PROGRESS')
    const before = calls.filter(call => call.path === '/reception/support-requests/8').length
    await user.click(screen.getByRole('button', { name: 'Refresh request details' }))
    await screen.findByText('Request SR-8')
    expect(calls.filter(call => call.path === '/reception/support-requests/8')).toHaveLength(before + 1)
  })
  it('loads empty results and passes status filters to the server', async () => {
    const { calls } = setup()
    expect(await screen.findByText('No support requests match these filters.')).toBeInTheDocument()
    await userEvent.setup().selectOptions(screen.getByLabelText('Status filter'), 'CLOSED')
    await waitFor(() => expect(calls.some(call => call.path === '/reception/support-requests?status=CLOSED')).toBe(true))
  })
  it('blocks a Member from the staff screen', async () => {
    const { calls } = setup('MEMBER')
    expect(await screen.findByRole('heading', { name: /access denied|forbidden/i })).toBeInTheDocument()
    expect(calls.some(call => call.path.startsWith('/reception/support'))).toBe(false)
  })
})
