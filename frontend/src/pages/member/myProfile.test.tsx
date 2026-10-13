import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { STRONG_PASSWORD_MESSAGE } from '../../auth/passwordRules'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
import {
  CURRENT_PASSWORD_REJECTED_MESSAGE,
  DATE_OF_BIRTH_PAST_MESSAGE,
  PASSWORD_CHANGED_MESSAGE,
  PROFILE_SAVED_MESSAGE,
  SAME_PASSWORD_MESSAGE,
} from './profileValidation'

/**
 * FE-02: My Profile (F1-08). The fake answers GET /users/me with the member profile fields
 * planned in BE-02 and stands in for PUT /users/me and PUT /users/me/password.
 * Today is fixed at 2026-10-13.
 */

const FULL_PROFILE = {
  ...MEMBER,
  dateOfBirth: '2004-06-15',
  address: 'Ho Chi Minh City',
  mainGoal: 'IMPROVE_FITNESS',
  currentLevel: 'BEGINNER',
  memberCode: 'MEM-0002',
}

type Handler = (call: RecordedCall) => Response | Promise<Response>

interface ProfileBackendOptions {
  profile?: Record<string, unknown>
  onUpdate?: Handler
  onPassword?: Handler
}

function profileBackend(options: ProfileBackendOptions = {}) {
  let profile: Record<string, unknown> = options.profile ?? FULL_PROFILE
  return installFakeFetch(async (call) => {
    if (call.path === '/users/me' && call.method === 'GET') {
      return json(200, profile)
    }
    if (call.path === '/users/me' && call.method === 'PUT') {
      if (options.onUpdate) {
        return options.onUpdate(call)
      }
      profile = { ...profile, ...(call.body as Record<string, unknown>) }
      return json(200, profile)
    }
    if (call.path === '/users/me/password' && call.method === 'PUT') {
      return options.onPassword ? options.onPassword(call) : new Response(null, { status: 204 })
    }
    return backendError(404, 'Resource not found')
  })
}

async function openProfile(options: ProfileBackendOptions = {}) {
  const fake = profileBackend(options)
  tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' })
  renderApp('/member/profile')
  await screen.findByRole('form', { name: 'Personal information' })
  return fake
}

/** Live regions (aria-live, role status or alert) whose text contains the message. */
function liveRegionsWith(message: string): Element[] {
  return Array.from(document.querySelectorAll('[aria-live], [role="status"], [role="alert"]')).filter(
    (element) => element.textContent?.includes(message) && element.getAttribute('aria-live') !== 'off',
  )
}

/** The inline success notice is shown but is not a live region; the toast is the one announcement. */
async function expectSingleAnnouncement(message: string) {
  const notice = await screen.findByText(message, { selector: '.notice' })
  expect(notice).not.toHaveAttribute('role')
  expect(notice.closest('[aria-live], [role="status"], [role="alert"]')).toBeNull()
  await waitFor(() => expect(liveRegionsWith(message)).toHaveLength(1))
  expect(liveRegionsWith(message)[0]).toHaveAttribute('aria-live', 'polite')
}

function personalForm() {
  return screen.getByRole('form', { name: 'Personal information' })
}

function passwordForm() {
  return screen.getByRole('form', { name: 'Change password' })
}

function deferred() {
  let release: (response: Response) => void = () => undefined
  const promise = new Promise<Response>((resolve) => {
    release = resolve
  })
  return { promise, release }
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 9, 13, 9, 30))
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('FE-02 My Profile (F1-08) - personal information', () => {
  it('TC-FE02-040 shows the profile from GET /users/me, with a read only email', async () => {
    await openProfile()
    const form = personalForm()
    expect(within(form).getByLabelText(/Full name/)).toHaveValue('Taylor Nguyen')
    expect(within(form).getByLabelText('Date of birth')).toHaveValue('2004-06-15')
    expect(within(form).getByLabelText('Email address')).toHaveValue('member@sportify.demo')
    expect(within(form).getByLabelText('Email address')).toHaveAttribute('readonly')
    expect(within(form).getByLabelText(/Phone number/)).toHaveValue('0901234567')
    expect(within(form).getByLabelText('Training goal')).toHaveValue('IMPROVE_FITNESS')
    expect(within(form).getByLabelText('Experience level')).toHaveValue('BEGINNER')
    expect(within(form).getByLabelText('Address')).toHaveValue('Ho Chi Minh City')

    const summary = screen.getByRole('complementary', { name: 'Profile summary' })
    expect(within(summary).getByText('TN')).toBeInTheDocument()
    expect(within(summary).getByText('Member code MEM-0002')).toBeInTheDocument()
  })

  it('TC-FE02-041 a profile without the BE-02 fields shows empty values and Not set', async () => {
    await openProfile({ profile: { ...MEMBER, mainGoal: 'UNKNOWN_CODE' } })
    const form = personalForm()
    expect(within(form).getByLabelText('Date of birth')).toHaveValue('')
    expect(within(form).getByLabelText('Address')).toHaveValue('')
    expect(within(form).getByLabelText('Training goal')).toHaveValue('')
    expect(within(form).getByLabelText('Experience level')).toHaveValue('')
  })

  it('TC-FE02-042 Save changes sends PUT /users/me, shows the success message and updates the header', async () => {
    const user = userEvent.setup()
    const { calls } = await openProfile()
    const form = personalForm()
    const name = within(form).getByLabelText(/Full name/)
    await user.clear(name)
    await user.type(name, '  Taylor Tran ')
    const phone = within(form).getByLabelText(/Phone number/)
    await user.clear(phone)
    await user.type(phone, '0901 234 999')
    await user.selectOptions(within(form).getByLabelText('Experience level'), 'INTERMEDIATE')
    await user.clear(within(form).getByLabelText('Address'))
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))

    await expectSingleAnnouncement(PROFILE_SAVED_MESSAGE)
    const put = calls.find((call) => call.method === 'PUT' && call.path === '/users/me')
    expect(put?.body).toEqual({
      fullName: 'Taylor Tran',
      phone: '0901234999',
      dateOfBirth: '2004-06-15',
      address: null,
      mainGoal: 'IMPROVE_FITNESS',
      currentLevel: 'INTERMEDIATE',
    })
    expect(put?.authorization).toBe('Bearer member-access')
    expect(within(form).getByLabelText(/Full name/)).toHaveValue('Taylor Tran')
    // reloadUser() reads GET /users/me again, so the header shows the new name.
    await waitFor(() => expect(document.querySelector('.user-menu__name')).toHaveTextContent('Taylor Tran'))
    expect(within(screen.getByRole('complementary', { name: 'Profile summary' })).getByText('Taylor Tran')).toBeInTheDocument()
  })

  it('TC-FE02-043 client checks block an empty name, a bad phone and a future date of birth', async () => {
    const user = userEvent.setup()
    const { calls } = await openProfile()
    const form = personalForm()
    await user.clear(within(form).getByLabelText(/Full name/))
    const phone = within(form).getByLabelText(/Phone number/)
    await user.clear(phone)
    await user.type(phone, '12ab')
    const dob = within(form).getByLabelText('Date of birth')
    await user.clear(dob)
    await user.type(dob, '2030-01-01')
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))

    expect(await within(form).findByText('Full name is required')).toBeInTheDocument()
    expect(within(form).getByText('Phone must be 8-15 digits with optional leading +')).toBeInTheDocument()
    expect(within(form).getByText(DATE_OF_BIRTH_PAST_MESSAGE)).toBeInTheDocument()
    expect(calls.some((call) => call.method === 'PUT')).toBe(false)
    await waitFor(() => expect(within(form).getByLabelText(/Full name/)).toHaveFocus())
  })

  it('TC-FE02-044 editing a field clears its error', async () => {
    const user = userEvent.setup()
    await openProfile()
    const form = personalForm()
    const name = within(form).getByLabelText(/Full name/)
    await user.clear(name)
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))
    expect(await within(form).findByText('Full name is required')).toBeInTheDocument()
    await user.type(name, 'T')
    expect(within(form).queryByText('Full name is required')).not.toBeInTheDocument()
  })

  it('TC-FE02-045 backend validation details go under their field, unknown keys above the form', async () => {
    const user = userEvent.setup()
    await openProfile({
      onUpdate: () =>
        backendError(400, 'Validation Error', { phone: 'Phone is already used by another account', gender: 'Gender is invalid' }),
    })
    const form = personalForm()
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))

    expect(await within(form).findByText('Phone is already used by another account')).toBeInTheDocument()
    const card = form.closest('.card') as HTMLElement
    expect(within(card).getByText('Gender is invalid', { selector: '.notice' })).toHaveAttribute('role', 'alert')
    expect(screen.queryByText(PROFILE_SAVED_MESSAGE, { selector: '.notice' })).not.toBeInTheDocument()
    await waitFor(() => expect(within(form).getByLabelText(/Phone number/)).toHaveFocus())
  })

  it('TC-FE02-046 a server or network error is shown above the form and the form is enabled again', async () => {
    const user = userEvent.setup()
    await openProfile({ onUpdate: () => backendError(500, 'Internal server error') })
    const form = personalForm()
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))
    const card = form.closest('.card') as HTMLElement
    expect(await within(card).findByText('Internal server error', { selector: '.notice' })).toBeInTheDocument()
    expect(within(form).getByRole('button', { name: 'Save changes' })).toBeEnabled()
  })

  it('TC-FE02-047 while saving the form is disabled and a second submit sends nothing', async () => {
    const user = userEvent.setup()
    const pending = deferred()
    const { calls } = await openProfile({ onUpdate: () => pending.promise })
    const form = personalForm()
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))

    expect(within(form).getByLabelText(/Full name/)).toBeDisabled()
    expect(form).toHaveAttribute('aria-busy', 'true')
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    expect(calls.filter((call) => call.method === 'PUT')).toHaveLength(1)

    await act(async () => {
      pending.release(json(200, FULL_PROFILE))
    })
    expect(await screen.findByText(PROFILE_SAVED_MESSAGE, { selector: '.notice' })).toBeInTheDocument()
    expect(within(form).getByLabelText(/Full name/)).toBeEnabled()
  })

  it('TC-FE02-048 a 204 answer keeps the values that were sent', async () => {
    const user = userEvent.setup()
    await openProfile({ onUpdate: () => new Response(null, { status: 204 }) })
    const form = personalForm()
    const address = within(form).getByLabelText('Address')
    await user.clear(address)
    await user.type(address, 'Thu Duc')
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByText(PROFILE_SAVED_MESSAGE, { selector: '.notice' })).toBeInTheDocument()
    expect(within(form).getByLabelText('Address')).toHaveValue('Thu Duc')
  })

  it('TC-FE02-049 a failed profile load shows an error with Retry', async () => {
    const user = userEvent.setup()
    let meCalls = 0
    installFakeFetch((call) => {
      if (call.path === '/users/me') {
        meCalls += 1
        // Call 1 is the auth context restoring the session; call 2 is the page's own load.
        if (meCalls === 2) {
          return backendError(503, 'Service unavailable')
        }
      }
      return json(200, FULL_PROFILE)
    })
    tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' })
    renderApp('/member/profile')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('We could not load your profile')
    expect(alert).toHaveTextContent('Service unavailable')
    await user.click(within(alert).getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('form', { name: 'Personal information' })).toBeInTheDocument()
    expect(meCalls).toBe(3)
  })

  // Review round 1

  it('TC-FE02-050 a member without a phone can save an address change; phone is sent as null', async () => {
    const user = userEvent.setup()
    const { calls } = await openProfile({ profile: { ...FULL_PROFILE, phone: null } })
    const form = personalForm()
    expect(within(form).getByLabelText('Phone number')).not.toBeRequired()
    const address = within(form).getByLabelText('Address')
    await user.clear(address)
    await user.type(address, 'District 7')
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText(PROFILE_SAVED_MESSAGE, { selector: '.notice' })).toBeInTheDocument()
    const put = calls.find((call) => call.method === 'PUT' && call.path === '/users/me')
    expect(put?.body).toMatchObject({ phone: null, address: 'District 7' })
  })

  it('TC-FE02-051 an invalid phone is still rejected', async () => {
    const user = userEvent.setup()
    const { calls } = await openProfile()
    const form = personalForm()
    const phone = within(form).getByLabelText('Phone number')
    await user.clear(phone)
    await user.type(phone, '1234567')
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))
    expect(await within(form).findByText('Phone must be 8-15 digits with optional leading +')).toBeInTheDocument()
    expect(calls.some((call) => call.method === 'PUT')).toBe(false)
  })

  it('TC-FE02-052 today is rejected as date of birth and the picker stops at yesterday', async () => {
    const user = userEvent.setup()
    await openProfile()
    const form = personalForm()
    const dob = within(form).getByLabelText('Date of birth')
    expect(dob).toHaveAttribute('max', '2026-10-12')
    expect(dob).toHaveAttribute('min', '1900-01-01')
    await user.clear(dob)
    await user.type(dob, '2026-10-13')
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))
    expect(await within(form).findByText(DATE_OF_BIRTH_PAST_MESSAGE)).toBeInTheDocument()
  })

  it('TC-FE02-053 maxLength of full name and address come from the constants', async () => {
    await openProfile()
    const form = personalForm()
    expect(within(form).getByLabelText(/Full name/)).toHaveAttribute('maxlength', '100')
    expect(within(form).getByLabelText('Address')).toHaveAttribute('maxlength', '255')
  })
})

describe('FE-02 My Profile (F1-08) - change password', () => {
  async function fillPassword(current: string, next: string, confirm: string) {
    const user = userEvent.setup()
    const form = passwordForm()
    if (current) {
      await user.type(within(form).getByLabelText(/Current password/), current)
    }
    if (next) {
      await user.type(within(form).getByLabelText(/^New password/), next)
    }
    if (confirm) {
      await user.type(within(form).getByLabelText(/Confirm new password/), confirm)
    }
    await user.click(within(form).getByRole('button', { name: 'Change password' }))
    return form
  }

  it('TC-FE02-060 a valid change sends PUT /users/me/password, shows success and clears the form', async () => {
    const { calls } = await openProfile()
    const form = await fillPassword('Demo123!', 'NewPass2026', 'NewPass2026')

    await expectSingleAnnouncement(PASSWORD_CHANGED_MESSAGE)
    const put = calls.find((call) => call.path === '/users/me/password')
    expect(put?.method).toBe('PUT')
    expect(put?.body).toEqual({ currentPassword: 'Demo123!', newPassword: 'NewPass2026' })
    expect(within(form).getByLabelText(/Current password/)).toHaveValue('')
    expect(within(form).getByLabelText(/^New password/)).toHaveValue('')
    expect(within(form).getByLabelText(/Confirm new password/)).toHaveValue('')
  })

  it('TC-FE02-061 a 400 with details.currentPassword is shown under Current password', async () => {
    await openProfile({
      onPassword: () => backendError(400, 'Validation Error', { currentPassword: 'Current password is incorrect' }),
    })
    const form = await fillPassword('Wrong123', 'NewPass2026', 'NewPass2026')
    expect(await within(form).findByText('Current password is incorrect')).toBeInTheDocument()
    expect(screen.queryByText(PASSWORD_CHANGED_MESSAGE, { selector: '.notice' })).not.toBeInTheDocument()
    await waitFor(() => expect(within(form).getByLabelText(/Current password/)).toHaveFocus())
    // The typed values stay so the member can fix only the wrong field.
    expect(within(form).getByLabelText(/^New password/)).toHaveValue('NewPass2026')
  })

  it('TC-FE02-062 client checks: required fields', async () => {
    const { calls } = await openProfile()
    const form = await fillPassword('', '', '')
    expect(await within(form).findByText('Current password is required')).toBeInTheDocument()
    expect(within(form).getByText('New password is required')).toBeInTheDocument()
    expect(within(form).getByText('Please confirm your password')).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/users/me/password')).toBe(false)
  })

  it('TC-FE02-063 client checks: weak password, mismatch and same as current', async () => {
    await openProfile()
    let form = await fillPassword('Demo123!', 'short', 'other')
    expect(await within(form).findByText(STRONG_PASSWORD_MESSAGE)).toBeInTheDocument()
    expect(within(form).getByText('Passwords do not match')).toBeInTheDocument()

    const user = userEvent.setup()
    form = passwordForm()
    await user.clear(within(form).getByLabelText(/^New password/))
    await user.type(within(form).getByLabelText(/^New password/), 'Demo123!')
    await user.clear(within(form).getByLabelText(/Confirm new password/))
    await user.type(within(form).getByLabelText(/Confirm new password/), 'Demo123!')
    await user.click(within(form).getByRole('button', { name: 'Change password' }))
    expect(await within(form).findByText(SAME_PASSWORD_MESSAGE)).toBeInTheDocument()
  })

  it('TC-FE02-064 backend field details on the new password go under that field', async () => {
    await openProfile({ onPassword: () => backendError(400, 'Validation Error', { newPassword: STRONG_PASSWORD_MESSAGE }) })
    const form = await fillPassword('Demo123!', 'NewPass2026', 'NewPass2026')
    expect(await within(form).findByText(STRONG_PASSWORD_MESSAGE)).toBeInTheDocument()
  })

  it('TC-FE02-065 any other error is shown above the change password form', async () => {
    await openProfile({ onPassword: () => backendError(500, 'Internal server error') })
    const form = await fillPassword('Demo123!', 'NewPass2026', 'NewPass2026')
    const card = form.closest('.card') as HTMLElement
    expect(await within(card).findByText('Internal server error', { selector: '.notice' })).toHaveAttribute('role', 'alert')
    expect(within(form).getByRole('button', { name: 'Change password' })).toBeEnabled()
  })

  it('TC-FE02-066 the live rules list follows the new password', async () => {
    const user = userEvent.setup()
    await openProfile()
    const form = passwordForm()
    await user.type(within(form).getByLabelText(/^New password/), 'abc')
    expect(form.querySelector('[data-rule="letter"]')).toHaveAttribute('data-met', 'true')
    expect(form.querySelector('[data-rule="digit"]')).toHaveAttribute('data-met', 'false')
    await user.type(within(form).getByLabelText(/^New password/), '12345')
    expect(within(form).getByText('Meets requirements')).toBeInTheDocument()
  })

  // Review round 1

  it('TC-FE02-067 a 401 is shown under Current password without refresh or sign out', async () => {
    const user = userEvent.setup()
    const { calls } = await openProfile({ onPassword: () => backendError(401, 'Unauthorized') })
    const form = await fillPassword('Wrong123', 'NewPass2026', 'NewPass2026')

    expect(await within(form).findByText(CURRENT_PASSWORD_REJECTED_MESSAGE)).toBeInTheDocument()
    await waitFor(() => expect(within(form).getByLabelText(/Current password/)).toHaveFocus())
    expect(calls.some((call) => call.path === '/auth/refresh')).toBe(false)
    expect(calls.filter((call) => call.path === '/users/me/password')).toHaveLength(1)
    expect(tokenStorage.getAccessToken()).toBe('member-access')
    expect(tokenStorage.getRefreshToken()).toBe('member-refresh')
    expect(screen.getByRole('heading', { name: 'My Profile' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /Log in|Welcome back/ })).not.toBeInTheDocument()

    // The session still works: the profile form can be saved afterwards.
    await user.click(within(personalForm()).getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByText(PROFILE_SAVED_MESSAGE, { selector: '.notice' })).toBeInTheDocument()
  })

  it('TC-FE02-068 a 400 without details is shown above the form, whatever its text says', async () => {
    await openProfile({ onPassword: () => backendError(400, 'Current password is incorrect') })
    const form = await fillPassword('Wrong123', 'NewPass2026', 'NewPass2026')
    const card = form.closest('.card') as HTMLElement
    expect(await within(card).findByText('Current password is incorrect', { selector: '.notice' })).toHaveAttribute(
      'role',
      'alert',
    )
    expect(within(form).queryByText('Current password is incorrect')).not.toBeInTheDocument()
  })

  it('TC-FE02-069 a 400 with another message and no details is shown above the form', async () => {
    await openProfile({ onPassword: () => backendError(400, 'Password change is not allowed right now') })
    const form = await fillPassword('Demo123!', 'NewPass2026', 'NewPass2026')
    const card = form.closest('.card') as HTMLElement
    expect(await within(card).findByText('Password change is not allowed right now', { selector: '.notice' })).toBeInTheDocument()
    expect(within(form).getByLabelText(/Current password/)).not.toHaveAttribute('aria-invalid')
  })
})
