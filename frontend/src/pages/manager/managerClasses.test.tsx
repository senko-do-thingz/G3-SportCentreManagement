import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MANAGER, MEMBER } from '../../test/fakeBackend'

const item = { id: 5, code: 'CL-005', name: 'Badminton foundations', sportId: 1, sportName: 'Badminton', ageGroupId: 1, ageGroupLabel: 'Adults', level: 'BEGINNER', coachId: 3, coachName: 'Quan Coach', maxMembers: 20, upcomingSessions: 0, active: true }
function backend(profile = MANAGER) {
  return installFakeFetch((call) => {
    const path = call.path.split('?')[0]
    if (path === '/users/me') return json(200, profile)
    if (path === '/sports') return json(200, [{ id: 1, code: 'BADMINTON', name: 'Badminton', venueType: 'COURT', description: null, imageUrl: null, displayOrder: 1 }])
    if (path === '/coaches') return json(200, [{ id: 3, fullName: 'Quan Coach', sportIds: [1] }])
    if (path === '/coaches/3') return json(200, { name: 'Quan Coach', sports: [{ id: 1, name: 'Badminton' }], certifications: [{ id: 1, name: 'Coaching certificate', issuer: 'Federation', verified: true }] })
    if (path === '/age-groups') return json(200, [{ id: 1, code: 'ADULT', label: 'Adults', minAge: 18, maxAge: null }])
    if (path === '/facilities') return json(200, [{ id: 1, name: 'Court A', active: true }])
    if (path === '/manager/sessions') return json(200, [])
    if (path === '/manager/classes/5') return json(200, item)
    if (path === '/manager/classes' && call.method === 'POST') return json(201, item)
    if (path === '/manager/classes') return json(200, { content: [item], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    return backendError(404, 'Unexpected test request')
  })
}
beforeEach(() => { vi.stubEnv('VITE_MANAGER_PREVIEW', 'false'); tokenStorage.startSession({ accessToken: 'manager-test', refreshToken: 'refresh-test' }) })
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs() })
describe('FE-07 class management', () => {
  it('loads classes and sends combined server filters', async () => {
    const api = backend()
    const user = userEvent.setup()
    renderApp('/manager/classes')
    await screen.findByText(item.name)
    await user.selectOptions(screen.getByRole('combobox', { name: /^Sport/ }), '1')
    await user.selectOptions(screen.getByLabelText('Status'), 'true')
    await user.type(screen.getByLabelText('Search classes'), 'foundations')
    await waitFor(() => expect(api.calls.some((call) => call.path.includes('search=foundations') && call.path.includes('sportId=1') && call.path.includes('active=true') && call.path.includes('size=20'))).toBe(true))
  })
  it('validates the class editor and shows real coach certification details', async () => {
    const api = backend()
    const user = userEvent.setup()
    renderApp('/manager/classes/new')
    await screen.findByRole('heading', { name: 'Create Class' })
    await user.click(screen.getByRole('button', { name: 'Save & schedule' }))
    expect(await screen.findByText('Enter a class name of 1 to 100 characters.')).toBeInTheDocument()
    expect(api.calls.some((call) => call.method === 'POST')).toBe(false)
    await user.selectOptions(screen.getByRole('combobox', { name: /^Sport/ }), '1')
    await screen.findByRole('option', { name: 'Quan Coach' })
    await user.selectOptions(screen.getByLabelText(/^Coach/), '3')
    expect(await screen.findByText('Coaching certificate - Federation (Verified)')).toBeInTheDocument()
  })
  it('protects editor deep links from non-managers', async () => {
    const api = backend(MEMBER)
    renderApp('/manager/classes/5/edit')
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(api.calls.every((call) => call.path === '/users/me')).toBe(true)
  })
  it('saves a valid class and opens scheduling for that class', async () => {
    const api = backend()
    const user = userEvent.setup()
    renderApp('/manager/classes/new')
    await screen.findByRole('heading', { name: 'Create Class' })
    await user.type(screen.getByLabelText(/^Class name/), 'Badminton foundations')
    await user.selectOptions(screen.getByRole('combobox', { name: /^Sport/ }), '1')
    await user.selectOptions(screen.getByLabelText(/^Age group/), '1')
    await screen.findByRole('option', { name: 'Quan Coach' })
    await user.selectOptions(screen.getByLabelText(/^Coach/), '3')
    await user.click(screen.getByRole('button', { name: 'Save & schedule' }))
    const dialog = await screen.findByRole('dialog', { name: 'Create session' })
    expect(dialog).toBeInTheDocument()
    expect(api.calls.find((call) => call.method === 'POST')?.body).toEqual({ name: 'Badminton foundations', sportId: 1, ageGroupId: 1, level: 'BEGINNER', coachId: 3, maxMembers: 20 })
  })
  it.each(['/manager/classes', '/manager/classes/new', '/manager/classes/5/edit', '/manager/schedule'])('requires login for %s', async (route) => {
    tokenStorage.clear()
    const api = backend()
    renderApp(route)
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(api.calls).toHaveLength(0)
  })
  it.each(['/manager/classes', '/manager/classes/new', '/manager/classes/5/edit', '/manager/schedule'])('rejects member access to %s', async (route) => {
    const api = backend(MEMBER)
    renderApp(route)
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(api.calls.every((call) => call.path === '/users/me')).toBe(true)
  })
})
