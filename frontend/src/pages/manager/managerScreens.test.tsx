import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { ApiError } from '../../api/errors'
import { managerUsersApi } from '../../api/managerUsersApi'
import { managerPackagesApi } from '../../api/managerPackagesApi'
import { managerCardTiersApi } from '../../api/managerCardTiersApi'
import { ACTIVITY_ACTIONS, managerActivityLogsApi } from '../../api/managerActivityLogsApi'
import { packagesApi, type Sport } from '../../api/packagesApi'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MANAGER, MEMBER } from '../../test/fakeBackend'
import type { ActivityLog, ManagerCardTier, ManagerPackage, ManagerUser } from '../../types/manager'
import { formatManagerDateTime } from './managerFormat'

const staff: ManagerUser = { id: 3, fullName: 'Daniel Smith', email: 'daniel@sportify.demo', phone: '0901234568', role: 'COACH', status: 'ACTIVE', createdAt: '2026-10-14T09:00:00' }
const sports: Sport[] = [ { id: 1, name: 'Football', code: 'FOOTBALL', venueType: 'FIELD', description: null, imageUrl: null, displayOrder: 1 }, { id: 2, name: 'Basketball', code: 'BASKETBALL', venueType: 'COURT', description: null, imageUrl: null, displayOrder: 2 } ]
const sportPackage: ManagerPackage = { id: 1, code: 'PK-001', name: 'Football 30-day package', sportId: 1, sportName: 'Football', sportCode: 'FOOTBALL', trainingFormat: 'COACH_LED', durationDays: 30, sessionCount: 8, sessionMinutes: 90, priceAmount: 900000, description: null, isActive: true }
const standard: ManagerCardTier = { id: 1, tierRank: 0, code: 'STANDARD', name: 'Standard', price: 0, durationMonths: 0, discountPercentage: 0, description: null, isActive: true }
const vip: ManagerCardTier = { id: 3, tierRank: 2, code: 'VIP', name: 'VIP', price: 600000, durationMonths: 12, discountPercentage: 10, description: null, isActive: true }
const activity: ActivityLog = { id: 1, createdAt: '2026-10-14T09:30:00', actorId: 1, actorName: 'Morgan Lee', actorRole: 'MANAGER', action: 'PACKAGE_CREATED', entityType: 'SPORT_PACKAGE', entityId: 1, shortDetail: 'Package created' }
const systemActivity: ActivityLog = { ...activity, actorId: null, actorName: 'System / unavailable actor', actorRole: null, entityId: null }
function paged<T>(content: T[], page = 0, totalElements = content.length) { return { content, page, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) } }
function session() { tokenStorage.startSession({ accessToken: 'manager-test', refreshToken: 'refresh-test' }) }
function authOnly(profile = MANAGER) {
  return installFakeFetch((call) => call.path === '/users/me' ? json(200, profile) : backendError(404, 'Unexpected test request'))
}

beforeEach(() => { vi.stubEnv('VITE_MANAGER_PREVIEW', 'false'); session() })
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs() })

describe('FE-06 manager access and accounts', () => {
  it('redirects anonymous visitors to login without requesting manager data', async () => {
    tokenStorage.clear()
    const backend = authOnly()
    renderApp('/manager')
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(backend.calls).toHaveLength(0)
  })
  it('shows 403 for a member and never requests manager data', async () => {
    const backend = authOnly(MEMBER)
    renderApp('/manager')
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(backend.calls.every((call) => call.path === '/users/me')).toBe(true)
  })
  it('loads pages and combines search, role and status while resetting the page', async () => {
    const backend = installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MANAGER)
      if (call.path.startsWith('/manager/users?')) return json(200, paged([staff], Number(new URLSearchParams(call.path.split('?')[1]).get('page')), 45))
      return backendError(404, 'Unexpected request')
    })
    const user = userEvent.setup()
    renderApp('/manager')
    await screen.findByText('Daniel Smith')
    await user.click(screen.getByRole('button', { name: 'Next' }))
    await waitFor(() => expect(backend.calls.some((call) => call.path.includes('page=1'))).toBe(true))
    await user.selectOptions(screen.getByLabelText('Role'), 'COACH')
    await user.selectOptions(screen.getByLabelText('Account status'), 'ACTIVE')
    await user.type(screen.getByLabelText('Search accounts'), 'Daniel +')
    await waitFor(() => expect(backend.calls.some((call) => call.path.includes('page=0') && call.path.includes('role=COACH') && call.path.includes('status=ACTIVE') && call.path.includes('search=Daniel+%2B'))).toBe(true))
    expect(backend.calls.find((call) => call.path.startsWith('/manager/users'))?.authorization).toBe('Bearer manager-test')
  })
  it('shows the server-reported directory total, identity initials, role badges, and scrollable table region', async () => {
    authOnly()
    vi.spyOn(managerUsersApi, 'list').mockResolvedValue(paged([staff], 0, 45))
    renderApp('/manager')

    const table = await screen.findByRole('table', { name: 'Center accounts' })
    expect(await screen.findByText('45 accounts found')).toBeInTheDocument()
    expect(within(table).getByText('DS')).toBeInTheDocument()
    expect(within(table).getByText('Coach')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Scrollable center accounts table' })).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('button', { name: '+ Add staff' })).toHaveClass('btn--primary')
  })

  it('opens the coach profile disclosure with Enter and Space, restores focus on Escape, and tabs back into the table', async () => {
    const receptionist: ManagerUser = { ...staff, id: 4, fullName: 'Jamie Tran', role: 'RECEPTIONIST' }
    authOnly()
    vi.spyOn(managerUsersApi, 'list').mockResolvedValue(paged([staff, receptionist], 0, 45))
    const user = userEvent.setup()
    renderApp('/manager')

    const trigger = await screen.findByRole('button', { name: 'More actions for Daniel Smith' })
    const receptionistRow = screen.getByText('Jamie Tran').closest('tr')!
    expect(screen.queryByRole('button', { name: 'More actions for Jamie Tran' })).not.toBeInTheDocument()

    trigger.focus()
    await user.keyboard('{Enter}')
    let popover = await screen.findByRole('group', { name: 'More actions for Daniel Smith' })
    expect(within(popover).getByRole('button', { name: 'Edit coach profile' })).toHaveFocus()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('group', { name: 'More actions for Daniel Smith' })).not.toBeInTheDocument())
    expect(trigger).toHaveFocus()

    await user.keyboard(' ')
    popover = await screen.findByRole('group', { name: 'More actions for Daniel Smith' })
    expect(within(popover).getByRole('button', { name: 'Edit coach profile' })).toHaveFocus()
    await user.keyboard('{Tab}')
    expect(within(receptionistRow).getByRole('button', { name: 'View profile' })).toHaveFocus()

    trigger.focus()
    await user.keyboard(' ')
    await screen.findByRole('group', { name: 'More actions for Daniel Smith' })
    await user.keyboard('{Shift>}{Tab}{/Shift}')
    await waitFor(() => expect(screen.queryByRole('group', { name: 'More actions for Daniel Smith' })).not.toBeInTheDocument())
    expect(trigger).toHaveFocus()

    await user.click(trigger)
    await screen.findByRole('group', { name: 'More actions for Daniel Smith' })
    await user.click(screen.getByLabelText('Search accounts'))
    expect(screen.queryByRole('group', { name: 'More actions for Daniel Smith' })).not.toBeInTheDocument()
  })

  it('opens only the selected coach profile after choosing the overflow action', async () => {
    authOnly()
    vi.spyOn(managerUsersApi, 'list').mockResolvedValue(paged([staff]))
    const coachProfile = vi.spyOn(managerUsersApi, 'coachProfile').mockResolvedValue({
      userId: staff.id,
      fullName: staff.fullName,
      avatarUrl: null,
      headline: null,
      bio: null,
      yearsExperience: null,
      specialties: null,
      isPublic: true,
      displayOrder: 0,
    })
    const user = userEvent.setup()
    renderApp('/manager')

    await user.click(await screen.findByRole('button', { name: 'More actions for Daniel Smith' }))
    const popover = await screen.findByRole('group', { name: 'More actions for Daniel Smith' })
    await user.click(within(popover).getByRole('button', { name: 'Edit coach profile' }))

    expect(await screen.findByRole('dialog', { name: 'Edit coach profile' })).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'More actions for Daniel Smith' })).not.toBeInTheDocument()
    expect(coachProfile).toHaveBeenCalledTimes(1)
    expect(coachProfile).toHaveBeenCalledWith(staff.id, expect.any(AbortSignal))
  })

  it('dismisses the coach disclosure on scroll, resize, and filter transitions', async () => {
    authOnly()
    vi.spyOn(managerUsersApi, 'list').mockResolvedValue(paged([staff], 0, 45))
    const user = userEvent.setup()
    renderApp('/manager')
    const trigger = await screen.findByRole('button', { name: 'More actions for Daniel Smith' })

    await user.click(trigger)
    await screen.findByRole('group', { name: 'More actions for Daniel Smith' })
    fireEvent(window, new Event('scroll'))
    await waitFor(() => expect(screen.queryByRole('group', { name: 'More actions for Daniel Smith' })).not.toBeInTheDocument())

    await user.click(trigger)
    await screen.findByRole('group', { name: 'More actions for Daniel Smith' })
    fireEvent(window, new Event('resize'))
    await waitFor(() => expect(screen.queryByRole('group', { name: 'More actions for Daniel Smith' })).not.toBeInTheDocument())

    await user.click(trigger)
    await screen.findByRole('group', { name: 'More actions for Daniel Smith' })
    fireEvent.change(screen.getByLabelText('Role'), { target: { value: 'COACH' } })
    await waitFor(() => expect(screen.queryByRole('group', { name: 'More actions for Daniel Smith' })).not.toBeInTheDocument())
  })

  it('clamps the coach disclosure to a short, narrow viewport', async () => {
    vi.stubGlobal('innerWidth', 390)
    vi.stubGlobal('innerHeight', 400)
    authOnly()
    vi.spyOn(managerUsersApi, 'list').mockResolvedValue(paged([staff]))
    const nativeBounds = HTMLElement.prototype.getBoundingClientRect
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
      if (this.classList.contains('manager-users-more')) {
        return { x: 350, y: 340, width: 36, height: 36, top: 340, right: 386, bottom: 376, left: 350, toJSON: () => ({}) } as DOMRect
      }
      if (this.classList.contains('manager-user-actions-popover')) {
        return { x: 0, y: 0, width: 208, height: 52, top: 0, right: 208, bottom: 52, left: 0, toJSON: () => ({}) } as DOMRect
      }
      return nativeBounds.call(this)
    })
    const user = userEvent.setup()
    renderApp('/manager')

    await user.click(await screen.findByRole('button', { name: 'More actions for Daniel Smith' }))
    const popover = await screen.findByRole('group', { name: 'More actions for Daniel Smith' })
    await waitFor(() => expect(popover).toHaveStyle({ left: '174px', top: '280px' }))
  })

  it('offers only account statuses supported by the backend', async () => {
    authOnly()
    vi.spyOn(managerUsersApi, 'list').mockResolvedValue(paged([]))
    renderApp('/manager')
    const status = await screen.findByLabelText('Account status')
    expect(Array.from(status.querySelectorAll('option')).map((option) => option.value)).toEqual(['', 'ACTIVE', 'INACTIVE'])
    expect(screen.queryByRole('option', { name: 'Locked' })).not.toBeInTheDocument()
  })
  it('distinguishes loading, server errors with retry, and an empty list', async () => {
    authOnly()
    let finish: (value: ReturnType<typeof paged<ManagerUser>>) => void = () => {}
    const list = vi.spyOn(managerUsersApi, 'list').mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    list.mockRejectedValueOnce(new ApiError(500, 'The user service is unavailable.')).mockResolvedValueOnce(paged([]))
    const user = userEvent.setup()
    renderApp('/manager')
    expect(await screen.findByText('Loading rows')).toBeInTheDocument()
    await act(async () => finish(paged([staff])))
    await screen.findByText('Daniel Smith')
    await user.selectOptions(screen.getByLabelText('Role'), 'COACH')
    expect(await screen.findByText('The user service is unavailable.')).toBeInTheDocument()
    expect(screen.queryByText('No matching accounts')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('No matching accounts')).toBeInTheDocument()
  })
  it('ignores an older response after the filters change', async () => {
    authOnly()
    let finish: (value: ReturnType<typeof paged<ManagerUser>>) => void = () => {}
    vi.spyOn(managerUsersApi, 'list').mockImplementationOnce(() => new Promise((resolve) => { finish = resolve })).mockResolvedValue(paged([{ ...staff, fullName: 'Newer result' }]))
    const user = userEvent.setup()
    renderApp('/manager')
    await screen.findByLabelText('Role')
    await user.selectOptions(screen.getByLabelText('Role'), 'COACH')
    await screen.findByText('Newer result')
    await act(async () => finish(paged([{ ...staff, fullName: 'Stale result' }])))
    expect(screen.queryByText('Stale result')).not.toBeInTheDocument()
  })
  it('validates staff fields and sends exactly the supported coach payload once', async () => {
    authOnly()
    vi.spyOn(managerUsersApi, 'list').mockResolvedValue(paged([staff]))
    vi.spyOn(packagesApi, 'sports').mockResolvedValue(sports)
    const create = vi.spyOn(managerUsersApi, 'create').mockResolvedValue(staff)
    const user = userEvent.setup()
    renderApp('/manager')
    await user.click(await screen.findByRole('button', { name: '+ Add staff' }))
    let dialog = within(screen.getByRole('dialog'))
    await user.click(dialog.getByRole('button', { name: 'Save account' }))
    expect(await dialog.findByText('Full name is required.')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
    expect(dialog.queryByRole('option', { name: 'Member' })).not.toBeInTheDocument()
    await user.type(dialog.getByLabelText(/Full name/), 'Coach Example')
    await user.type(dialog.getByLabelText(/Email address/), 'coach@example.com')
    await user.type(dialog.getByLabelText(/Phone number/), '+84987654321')
    await user.type(dialog.getByLabelText(/Temporary password/), 'M\u1EADtKh\u1EA9u123')
    await user.selectOptions(dialog.getByLabelText(/Role/), 'COACH')
    await user.click(await dialog.findByLabelText('Basketball'))
    await user.click(dialog.getByLabelText('Football'))
    dialog = within(screen.getByRole('dialog'))
    const form = dialog.getByRole('button', { name: 'Save account' }).closest('form')!
    fireEvent.submit(form)
    fireEvent.submit(form)
    await waitFor(() => expect(create).toHaveBeenCalledTimes(1))
    expect(create).toHaveBeenCalledWith({ fullName: 'Coach Example', email: 'coach@example.com', phone: '+84987654321', temporaryPassword: 'M\u1EADtKh\u1EA9u123', role: 'COACH', sportIds: [2, 1] })
    await screen.findByText('Staff account created')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('retains inputs and shows backend field errors for duplicate staff emails', async () => {
    authOnly()
    vi.spyOn(managerUsersApi, 'list').mockResolvedValue(paged([staff]))
    const create = vi.spyOn(managerUsersApi, 'create').mockRejectedValue(new ApiError(409, 'Email already exists.', { email: 'Choose another email.' }))
    const user = userEvent.setup()
    renderApp('/manager')
    await user.click(await screen.findByRole('button', { name: '+ Add staff' }))
    const dialog = within(screen.getByRole('dialog'))
    await user.type(dialog.getByLabelText(/Full name/), 'Reception Example')
    await user.type(dialog.getByLabelText(/Email address/), 'existing@example.com')
    await user.type(dialog.getByLabelText(/Phone number/), '0901234567')
    await user.type(dialog.getByLabelText(/Temporary password/), 'Example123')
    await user.click(dialog.getByRole('button', { name: 'Save account' }))
    expect(await dialog.findByText('Choose another email.')).toBeInTheDocument()
    expect(dialog.getByLabelText(/Full name/)).toHaveValue('Reception Example')
    expect(create).toHaveBeenCalledWith(expect.objectContaining({ role: 'RECEPTIONIST', sportIds: [] }))
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('edits a coach profile, uploads a photo, validates input and sends the supported payload once', async () => {
    const profile = { userId: 3, fullName: 'Daniel Smith', avatarUrl: null, headline: 'Old headline', bio: null, yearsExperience: 3, specialties: null, isPublic: true, displayOrder: 0 }
    const receptionist = { ...staff, id: 4, fullName: 'Jamie Tran', role: 'RECEPTIONIST' }
    const backend = installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MANAGER)
      if (call.path.startsWith('/manager/users?')) return json(200, paged([staff, receptionist]))
      if (call.path === '/manager/users/3/coach-profile' && call.method === 'GET') return json(200, profile)
      if (call.path === '/manager/users/3/coach-profile/photo' && call.method === 'POST') return json(200, { ...profile, avatarUrl: '/api/v1/users/3/avatar?v=99' })
      if (call.path === '/manager/users/3/coach-profile' && call.method === 'PUT') return json(200, { ...profile, ...(call.body as object) })
      return backendError(404, 'Unexpected request')
    })
    const user = userEvent.setup({ applyAccept: false })
    renderApp('/manager')
    await screen.findByText('Jamie Tran')
    expect(screen.queryByRole('button', { name: 'More actions for Jamie Tran' })).not.toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'More actions for Daniel Smith' }))
    const actions = await screen.findByRole('group', { name: 'More actions for Daniel Smith' })
    await user.click(within(actions).getByRole('button', { name: 'Edit coach profile' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit coach profile' })
    const headline = await within(dialog).findByLabelText('Headline')
    expect(headline).toHaveValue('Old headline')
    expect(dialog.querySelector('.manager-photo-preview')).toHaveTextContent('DS')
    expect(within(dialog).queryByRole('button', { name: 'Remove photo' })).not.toBeInTheDocument()

    const picker = within(dialog).getByLabelText('Profile photo')
    await user.upload(picker, new File(['GIF89a'], 'coach.gif', { type: 'image/gif' }))
    expect(await within(dialog).findByText('Choose a JPEG, PNG or WebP photo.')).toBeInTheDocument()
    expect(backend.calls.some((call) => call.path.endsWith('/photo'))).toBe(false)

    await user.upload(picker, new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0])], 'coach.jpg', { type: 'image/jpeg' }))
    expect(await within(dialog).findByText('Photo uploaded and saved.')).toBeInTheDocument()
    const uploads = backend.calls.filter((call) => call.path === '/manager/users/3/coach-profile/photo')
    expect(uploads).toHaveLength(1)
    expect(uploads[0].body).toBeInstanceOf(FormData)
    expect((uploads[0].body as FormData).get('file')).toBeInstanceOf(Blob)
    expect(dialog.querySelector('.manager-photo-preview img')?.getAttribute('src')).toMatch(/\/users\/3\/avatar\?v=99$/)
    expect(within(dialog).getByRole('button', { name: 'Change photo' })).toBeInTheDocument()

    await user.clear(within(dialog).getByLabelText('Years of coaching'))
    await user.type(within(dialog).getByLabelText('Years of coaching'), '61')
    await user.click(within(dialog).getByRole('button', { name: 'Save profile' }))
    expect(within(dialog).getByText('Years of coaching must not exceed 60.')).toBeInTheDocument()
    expect(backend.calls.some((call) => call.method === 'PUT')).toBe(false)

    await user.clear(within(dialog).getByLabelText('Years of coaching'))
    await user.type(within(dialog).getByLabelText('Years of coaching'), '7')
    await user.clear(headline)
    await user.type(headline, '  Badminton coach for every level ')
    await user.type(within(dialog).getByLabelText('Specialties'), 'Beginners, Doubles')
    await user.clear(within(dialog).getByLabelText(/Display order/))
    await user.type(within(dialog).getByLabelText(/Display order/), '2')
    await user.click(within(dialog).getByLabelText('Show on the Home page'))
    await user.click(within(dialog).getByRole('button', { name: 'Save profile' }))

    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Edit coach profile' })).not.toBeInTheDocument())
    const puts = backend.calls.filter((call) => call.method === 'PUT')
    expect(puts).toHaveLength(1)
    expect(puts[0].body).toEqual({ avatarUrl: '/api/v1/users/3/avatar?v=99', headline: 'Badminton coach for every level', bio: '', yearsExperience: 7, specialties: 'Beginners, Doubles', isPublic: false, displayOrder: 2 })
    expect(await screen.findByText('Coach profile updated')).toBeInTheDocument()
  })
  it('removes an existing coach photo on save and reports a rejected upload', async () => {
    const profile = { userId: 3, fullName: 'Daniel Smith', avatarUrl: 'https://images.example.test/daniel.jpg', headline: null, bio: null, yearsExperience: null, specialties: null, isPublic: true, displayOrder: 0 }
    const backend = installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MANAGER)
      if (call.path.startsWith('/manager/users?')) return json(200, paged([staff]))
      if (call.path === '/manager/users/3/coach-profile/photo') return backendError(400, 'Photo must be a JPEG, PNG or WebP image')
      if (call.path === '/manager/users/3/coach-profile' && call.method === 'GET') return json(200, profile)
      if (call.path === '/manager/users/3/coach-profile' && call.method === 'PUT') return json(200, { ...profile, ...(call.body as object) })
      return backendError(404, 'Unexpected request')
    })
    const user = userEvent.setup()
    renderApp('/manager')
    await user.click(await screen.findByRole('button', { name: 'More actions for Daniel Smith' }))
    const actions = await screen.findByRole('group', { name: 'More actions for Daniel Smith' })
    await user.click(within(actions).getByRole('button', { name: 'Edit coach profile' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit coach profile' })
    await within(dialog).findByLabelText('Headline')
    expect(dialog.querySelector('.manager-photo-preview img')).toHaveAttribute('src', 'https://images.example.test/daniel.jpg')
    await user.upload(within(dialog).getByLabelText('Profile photo'), new File(['x'], 'fake.png', { type: 'image/png' }))
    expect(await within(dialog).findByText('Photo must be a JPEG, PNG or WebP image')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Remove photo' }))
    expect(dialog.querySelector('.manager-photo-preview')).toHaveTextContent('DS')
    await user.click(within(dialog).getByRole('button', { name: 'Save profile' }))
    await waitFor(() => expect(backend.calls.filter((call) => call.method === 'PUT')).toHaveLength(1))
    expect((backend.calls.find((call) => call.method === 'PUT')?.body as { avatarUrl: string }).avatarUrl).toBe('')
  })
  it('shows a retryable error when the coach profile cannot be loaded', async () => {
    let fail = true
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MANAGER)
      if (call.path.startsWith('/manager/users?')) return json(200, paged([staff]))
      if (call.path === '/manager/users/3/coach-profile') return fail ? backendError(404, 'Coach profile not found') : json(200, { userId: 3, fullName: 'Daniel Smith', avatarUrl: null, headline: null, bio: null, yearsExperience: null, specialties: null, isPublic: true, displayOrder: 0 })
      return backendError(404, 'Unexpected request')
    })
    const user = userEvent.setup()
    renderApp('/manager')
    await user.click(await screen.findByRole('button', { name: 'More actions for Daniel Smith' }))
    const actions = await screen.findByRole('group', { name: 'More actions for Daniel Smith' })
    await user.click(within(actions).getByRole('button', { name: 'Edit coach profile' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit coach profile' })
    expect(await within(dialog).findByText('Coach profile not found')).toBeInTheDocument()
    fail = false
    await user.click(within(dialog).getByRole('button', { name: 'Try again' }))
    expect(await within(dialog).findByLabelText('Headline')).toHaveValue('')
  })
  it('protects self-deactivation and requires confirmation before a single status write', async () => {
    authOnly()
    vi.spyOn(managerUsersApi, 'list').mockResolvedValue(paged([{ ...MANAGER, createdAt: null }, staff]))
    const change = vi.spyOn(managerUsersApi, 'changeStatus').mockResolvedValue({ ...staff, status: 'INACTIVE' })
    const user = userEvent.setup()
    renderApp('/manager')
    const selfRow = (await screen.findByText('manager@sportify.com')).closest('tr')!
    expect(within(selfRow).getByRole('button', { name: 'Deactivate' })).toBeDisabled()
    const staffRow = screen.getByText('Daniel Smith').closest('tr')!
    await user.click(within(staffRow).getByRole('button', { name: 'Deactivate' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }))
    expect(change).not.toHaveBeenCalled()
    await user.click(within(staffRow).getByRole('button', { name: 'Deactivate' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Deactivate account' }))
    await waitFor(() => expect(change).toHaveBeenCalledExactlyOnceWith(3, 'INACTIVE'))
  })
  it('reports a failed refresh after creation without leaving the creation modal open', async () => {
    authOnly()
    vi.spyOn(managerUsersApi, 'list').mockResolvedValueOnce(paged([staff])).mockRejectedValue(new ApiError(500, 'Refresh failed after saving.'))
    vi.spyOn(managerUsersApi, 'create').mockResolvedValue(staff)
    const user = userEvent.setup()
    renderApp('/manager')
    await user.click(await screen.findByRole('button', { name: '+ Add staff' }))
    const dialog = within(screen.getByRole('dialog'))
    await user.type(dialog.getByLabelText(/Full name/), 'New Staff')
    await user.type(dialog.getByLabelText(/Email address/), 'new@example.com')
    await user.type(dialog.getByLabelText(/Phone number/), '0901234567')
    await user.type(dialog.getByLabelText(/Temporary password/), 'Example123')
    await user.click(dialog.getByRole('button', { name: 'Save account' }))
    expect(await screen.findByText('Refresh failed after saving.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('Staff account created')).toBeInTheDocument()
  })
})

describe('FE-06 packages and membership cards', () => {
  it('uses the released BE-04 endpoint and exact response shape for a real manager', async () => {
    vi.stubEnv('VITE_MANAGER_PREVIEW', 'true')
    const backend = installFakeFetch((call) => call.path.startsWith('/manager/activity-logs') ? json(200, paged([systemActivity])) : backendError(404, 'Unexpected request'))
    const response = await managerActivityLogsApi.list({ page: 0, size: 20, action: 'PACKAGE_CREATED' })
    expect(response).toEqual(paged([systemActivity]))
    expect(backend.calls[0]).toMatchObject({ path: '/manager/activity-logs?page=0&size=20&action=PACKAGE_CREATED', method: 'GET', authorization: 'Bearer manager-test' })
  })
  it('uses the released BE-03 live catalog endpoints with authenticated requests and exact wire fields', async () => {
    vi.stubEnv('VITE_MANAGER_PREVIEW', 'true')
    const backend = installFakeFetch((call) => {
      if (call.path.startsWith('/manager/packages')) return json(200, [sportPackage])
      if (call.path === '/manager/card-tiers') return json(200, [standard, vip])
      if (call.path === '/packages/1') return json(200, sportPackage)
      if (call.path === '/membership-cards/tiers/3') return json(200, vip)
      return backendError(404, 'Unexpected request')
    })
    const packageUpdate = { name: sportPackage.name, durationDays: 30, sessionCount: 8, sessionMinutes: 90, price: 950000.25, active: false }
    const tierUpdate = { price: 650000, durationMonths: 12, discountPercentage: 15, description: '', active: true }
    expect(await managerPackagesApi.list({ sportId: '1', format: 'COACH_LED', active: 'false' })).toEqual([sportPackage])
    expect(await managerCardTiersApi.list()).toEqual([standard, vip])
    await managerPackagesApi.update(1, packageUpdate)
    await managerCardTiersApi.update(3, tierUpdate)
    expect(backend.calls[0].path).toBe('/manager/packages?sportId=1&format=COACH_LED&active=false')
    expect(backend.calls[2]).toMatchObject({ path: '/packages/1', method: 'PUT', body: packageUpdate })
    expect(backend.calls[3]).toMatchObject({ path: '/membership-cards/tiers/3', method: 'PUT', body: tierUpdate })
    expect(backend.calls.every((call) => call.authorization === 'Bearer manager-test')).toBe(true)
  })
  it('uses the complete manager catalog, filters it, and validates package edits', async () => {
    authOnly()
    vi.spyOn(packagesApi, 'sports').mockResolvedValue(sports)
    const list = vi.spyOn(managerPackagesApi, 'list').mockResolvedValue([sportPackage])
    const update = vi.spyOn(managerPackagesApi, 'update').mockResolvedValue(sportPackage)
    const user = userEvent.setup()
    renderApp('/manager/packages')
    await screen.findByText('900.000 VND')
    await user.selectOptions(screen.getByLabelText('Sport'), '1')
    await user.selectOptions(screen.getByLabelText('Training type'), 'COACH_LED')
    await user.selectOptions(screen.getByLabelText('Status'), 'false')
    await waitFor(() => expect(list).toHaveBeenLastCalledWith({ sportId: '1', format: 'COACH_LED', active: 'false' }, expect.any(AbortSignal)))
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const dialog = within(screen.getByRole('dialog'))
    expect(dialog.getByLabelText('Package code', { exact: false })).toHaveAttribute('readonly')
    expect(dialog.getByLabelText('Sport', { exact: true })).toHaveAttribute('readonly')
    await user.clear(dialog.getByLabelText(/Price/))
    await user.type(dialog.getByLabelText(/Price/), '0')
    await user.click(dialog.getByRole('button', { name: 'Save package' }))
    expect(await dialog.findByText('Enter a price greater than 0 VND.')).toBeInTheDocument()
    expect(update).not.toHaveBeenCalled()
    await user.clear(dialog.getByLabelText(/Price/))
    await user.type(dialog.getByLabelText(/Price/), '950000')
    await user.click(dialog.getByRole('button', { name: 'Save package' }))
    await waitFor(() => expect(update).toHaveBeenCalledExactlyOnceWith(1, { name: sportPackage.name, durationDays: 30, sessionCount: 8, sessionMinutes: 90, price: 950000, active: true }))
  })
  it('creates a package with verified fields and does not add an invented active field', async () => {
    authOnly()
    vi.spyOn(packagesApi, 'sports').mockResolvedValue(sports)
    vi.spyOn(managerPackagesApi, 'list').mockResolvedValue([])
    const create = vi.spyOn(managerPackagesApi, 'create').mockResolvedValue(sportPackage)
    const user = userEvent.setup()
    renderApp('/manager/packages')
    await screen.findByText('No sport packages yet')
    await user.click(screen.getByRole('button', { name: '+ Create package' }))
    const dialog = within(screen.getByRole('dialog'))
    await user.type(dialog.getByLabelText(/Package code/), 'PK-NEW')
    await user.type(dialog.getByLabelText(/Package name/), 'New package')
    await user.selectOptions(dialog.getByLabelText(/Sport/, { exact: false }), '1')
    await user.type(dialog.getByLabelText(/Price/), '500000')
    await user.clear(dialog.getByLabelText(/Included sessions/))
    await user.type(dialog.getByLabelText(/Included sessions/), '1.5')
    await user.click(dialog.getByRole('button', { name: 'Save package' }))
    expect(create).not.toHaveBeenCalled()
    expect(dialog.queryByRole('option', { name: '45 minutes' })).not.toBeInTheDocument()
    await user.clear(dialog.getByLabelText(/Included sessions/))
    await user.type(dialog.getByLabelText(/Included sessions/), '8')
    await user.click(dialog.getByRole('button', { name: 'Save package' }))
    await waitFor(() => expect(create).toHaveBeenCalledExactlyOnceWith({ code: 'PK-NEW', name: 'New package', sportId: 1, trainingFormat: 'SELF_TRAINING', durationDays: 30, sessionCount: 8, sessionMinutes: 60, priceAmount: 500000 }))
  })
  it('rejects BE-03 package limits and maps price and active server errors to the displayed fields', async () => {
    authOnly()
    vi.spyOn(packagesApi, 'sports').mockResolvedValue(sports)
    vi.spyOn(managerPackagesApi, 'list').mockResolvedValue([sportPackage])
    const update = vi.spyOn(managerPackagesApi, 'update').mockRejectedValue(new ApiError(400, 'Invalid package', { price: 'Price rejected by the service.', active: 'Status rejected by the service.' }))
    const user = userEvent.setup()
    renderApp('/manager/packages')
    await user.click(await screen.findByRole('button', { name: 'Edit' }))
    const dialog = within(screen.getByRole('dialog'))
    fireEvent.change(dialog.getByLabelText(/Duration/), { target: { value: '3651' } })
    fireEvent.change(dialog.getByLabelText(/Included sessions/), { target: { value: '1001' } })
    fireEvent.change(dialog.getByLabelText(/Price/), { target: { value: '100.123' } })
    await user.click(dialog.getByRole('button', { name: 'Save package' }))
    expect(dialog.getByText('Duration must not exceed 3650.')).toBeInTheDocument()
    expect(dialog.getByText('Session count must not exceed 1000.')).toBeInTheDocument()
    expect(dialog.getByText('Price must have at most 12 integer digits and 2 decimal places.')).toBeInTheDocument()
    expect(update).not.toHaveBeenCalled()
    fireEvent.change(dialog.getByLabelText(/Duration/), { target: { value: '3650' } })
    fireEvent.change(dialog.getByLabelText(/Included sessions/), { target: { value: '1000' } })
    fireEvent.change(dialog.getByLabelText(/Price/), { target: { value: '100.12' } })
    await user.click(dialog.getByRole('button', { name: 'Save package' }))
    expect(await dialog.findByText('Price rejected by the service.')).toBeInTheDocument()
    expect(dialog.getByText('Status rejected by the service.')).toBeInTheDocument()
    expect(dialog.getByLabelText(/Price/)).toHaveValue(100.12)
  })
  it('rejects fractional discounts, excessive validity and long card descriptions before a live update', async () => {
    authOnly()
    vi.spyOn(managerCardTiersApi, 'list').mockResolvedValue([standard, vip])
    const update = vi.spyOn(managerCardTiersApi, 'update').mockResolvedValue(vip)
    const user = userEvent.setup()
    renderApp('/manager/cards')
    await user.click(await screen.findByRole('button', { name: 'Edit card benefits' }))
    const dialog = within(screen.getByRole('dialog'))
    fireEvent.change(dialog.getByLabelText(/Package discount/), { target: { value: '5.5' } })
    fireEvent.change(dialog.getByLabelText(/Validity/), { target: { value: '121' } })
    fireEvent.change(dialog.getByLabelText('Description'), { target: { value: 'x'.repeat(501) } })
    await user.click(dialog.getByRole('button', { name: 'Save benefits' }))
    expect(dialog.getByText('Discount must be a whole number from 0 to 100.')).toBeInTheDocument()
    expect(dialog.getByText('Validity must not exceed 120.')).toBeInTheDocument()
    expect(dialog.getByText('Description must be at most 500 characters.')).toBeInTheDocument()
    expect(update).not.toHaveBeenCalled()
  })
  it('shows protected Standard and additional inactive tiers and validates discount bounds', async () => {
    authOnly()
    vi.spyOn(managerCardTiersApi, 'list').mockResolvedValue([standard, { ...vip, name: 'Platinum', code: 'PLATINUM', isActive: false }])
    const update = vi.spyOn(managerCardTiersApi, 'update').mockResolvedValue(vip)
    const user = userEvent.setup()
    renderApp('/manager/cards')
    expect(await screen.findByText('Default account tier')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Edit card benefits' })).toHaveLength(1)
    expect(screen.queryByRole('button', { name: /Create tier/ })).not.toBeInTheDocument()
    expect(screen.getByText('Inactive')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Edit card benefits' }))
    const dialog = within(screen.getByRole('dialog'))
    expect(dialog.getByLabelText('Tier name')).toHaveAttribute('readonly')
    await user.clear(dialog.getByLabelText(/Package discount/))
    await user.type(dialog.getByLabelText(/Package discount/), '101')
    await user.click(dialog.getByRole('button', { name: 'Save benefits' }))
    expect(await dialog.findByText('Discount must be a whole number from 0 to 100.')).toBeInTheDocument()
    expect(update).not.toHaveBeenCalled()
    await user.clear(dialog.getByLabelText(/Package discount/))
    await user.type(dialog.getByLabelText(/Package discount/), '100')
    await user.click(dialog.getByRole('button', { name: 'Save benefits' }))
    await waitFor(() => expect(update).toHaveBeenCalledWith(3, expect.objectContaining({ discountPercentage: 100, active: false })))
  })
  it('leaves the card catalog unchanged when an editor is cancelled', async () => {
    authOnly()
    vi.spyOn(managerCardTiersApi, 'list').mockResolvedValue([standard, vip])
    const update = vi.spyOn(managerCardTiersApi, 'update')
    const user = userEvent.setup()
    renderApp('/manager/cards')
    await user.click(await screen.findByRole('button', { name: 'Edit card benefits' }))
    const dialog = within(screen.getByRole('dialog'))
    await user.clear(dialog.getByLabelText(/Card fee/))
    await user.type(dialog.getByLabelText(/Card fee/), '900000')
    await user.click(dialog.getByRole('button', { name: 'Cancel' }))
    expect(screen.getByText('600.000 VND')).toBeInTheDocument()
    expect(update).not.toHaveBeenCalled()
  })
})

describe('FE-06 read-only activity', () => {
  it('combines filters, keeps calendar dates local and blocks a reversed date range', async () => {
    authOnly()
    const list = vi.spyOn(managerActivityLogsApi, 'list').mockResolvedValue(paged([activity]))
    vi.spyOn(managerUsersApi, 'list').mockResolvedValue(paged([staff]))
    const user = userEvent.setup()
    renderApp('/manager/activity')
    await screen.findByText('14/10/2026, 09:30')
    await user.click(screen.getByRole('button', { name: 'Performed by All actors' }))
    await user.click(await screen.findByRole('button', { name: 'Use Daniel Smith' }))
    fireEvent.change(screen.getByLabelText('From date'), { target: { value: '2026-10-14' } })
    fireEvent.change(screen.getByLabelText('To date'), { target: { value: '2026-10-14' } })
    const actionSelect = screen.getByLabelText('Action')
    expect(Array.from(actionSelect.querySelectorAll('option')).map((option) => option.value).filter(Boolean)).toEqual(ACTIVITY_ACTIONS)
    await user.selectOptions(actionSelect, 'PACKAGE_CREATED')
    await user.type(screen.getByLabelText('Search activity'), 'PK-001')
    await waitFor(() => expect(list).toHaveBeenLastCalledWith({ page: 0, size: 20, actorId: '3', action: 'PACKAGE_CREATED', from: '2026-10-14', to: '2026-10-14', search: 'PK-001' }, expect.any(AbortSignal)))
    const callsBefore = list.mock.calls.length
    fireEvent.change(screen.getByLabelText('To date'), { target: { value: '2026-10-13' } })
    expect(await screen.findByText('End date must be on or after the start date.')).toBeInTheDocument()
    expect(list).toHaveBeenCalledTimes(callsBefore)
  })
  it('opens row details without another request and safely displays absent values', async () => {
    authOnly()
    const list = vi.spyOn(managerActivityLogsApi, 'list').mockResolvedValue(paged([systemActivity]))
    const user = userEvent.setup()
    renderApp('/manager/activity')
    expect(await screen.findByText('System / unavailable actor')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'View details' }))
    expect(within(screen.getByRole('dialog')).getByText('PACKAGE_CREATED')).toBeInTheDocument()
    expect(within(screen.getByRole('dialog')).getByText('Package created')).toBeInTheDocument()
    expect(list).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: /Delete|Edit/ })).not.toBeInTheDocument()
  })
  it('formats center timestamps correctly regardless of the browser timezone', () => {
    expect(formatManagerDateTime('2026-10-14T00:15:00')).toBe('14/10/2026, 00:15')
    expect(formatManagerDateTime('2026-10-13T17:15:00Z')).toBe('14/10/2026, 00:15')
    expect(formatManagerDateTime(null)).toBe('-')
  })
})
