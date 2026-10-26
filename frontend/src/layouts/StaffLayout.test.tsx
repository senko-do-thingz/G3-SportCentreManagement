import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { tokenStorage } from '../api/tokenStorage'
import { backendError, installFakeFetch, json, MANAGER } from '../test/fakeBackend'
import { renderApp } from '../test/renderApp'

const RECEPTIONIST = {
  id: 9,
  email: 'reception@sportify.demo',
  fullName: 'Jamie Tran',
  phone: '0901234599',
  role: 'RECEPTIONIST',
  status: 'ACTIVE',
}
const COACH = {
  id: 8,
  email: 'coach@sportify.demo',
  fullName: 'Quan Coach',
  phone: '0901234508',
  role: 'COACH',
  status: 'ACTIVE',
}

function setup(profile: typeof MANAGER | typeof RECEPTIONIST | typeof COACH, path: string) {
  tokenStorage.clear()
  tokenStorage.startSession({ accessToken: 'staff-layout-access', refreshToken: 'staff-layout-refresh' })
  installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, profile)
    if (call.path.startsWith('/coach/sessions?')) return json(200, [])
    if (call.path === '/manager/users') return json(200, { content: [], totalElements: 0, page: 0, size: 20 })
    if (call.path.startsWith('/manager/users?')) return json(200, { content: [], totalElements: 0, page: 0, size: 20 })
    return backendError(404, `Unexpected test request: ${call.method} ${call.path}`)
  })
  return renderApp(path)
}

afterEach(() => {
  tokenStorage.clear()
  vi.unstubAllGlobals()
})

describe('staff workspace shell navigation', () => {
  it('shows manager links and the active breadcrumb without exposing other role menus', async () => {
    setup(MANAGER, '/manager/classes')
    const navigation = await screen.findByRole('navigation', { name: 'Manager workspace navigation' })
    expect(screen.getByRole('button', { name: /Switch to .* mode/ })).toHaveClass('home-theme')
    expect(navigation.closest('.staff-layout')).toHaveClass('staff-layout--manager')

    expect(within(navigation).getByRole('link', { name: 'Users' })).toHaveAttribute('href', '/manager')
    expect(within(navigation).getByRole('link', { name: 'Classes' })).toHaveAttribute('aria-current', 'page')
    expect(within(navigation).getByRole('link', { name: 'Financial overview' })).toHaveAttribute('href', '/manager/finance')
    expect(within(navigation).queryByRole('link', { name: 'Member search' })).not.toBeInTheDocument()
    expect(within(navigation).queryByRole('link', { name: 'Class roster' })).not.toBeInTheDocument()

    const breadcrumb = screen.getByRole('navigation', { name: 'Breadcrumb' })
    expect(within(breadcrumb).getByRole('link', { name: 'Sportify Center' })).toHaveAttribute('href', '/')
    expect(within(breadcrumb).getByText('Class Management')).toHaveAttribute('aria-current', 'page')
  })

  it('keeps receptionist and coach menus restricted to their own workspace links', async () => {
    const receptionist = setup(RECEPTIONIST, '/reception')
    const receptionistNavigation = await screen.findByRole('navigation', { name: 'Receptionist workspace navigation' })
    expect(within(receptionistNavigation).getByRole('link', { name: 'Member search' })).toHaveAttribute('aria-current', 'page')
    expect(within(receptionistNavigation).getByRole('link', { name: 'Book for member' })).toHaveAttribute('href', '/reception/bookings')
    expect(within(receptionistNavigation).queryByRole('link', { name: 'Financial overview' })).not.toBeInTheDocument()
    receptionist.unmount()

    setup(COACH, '/coach')
    const coachNavigation = await screen.findByRole('navigation', { name: 'Coach workspace navigation' })
    expect(within(coachNavigation).getByRole('link', { name: 'Teaching schedule' })).toHaveAttribute('aria-current', 'page')
    expect(within(coachNavigation).getByRole('link', { name: 'Class roster' })).toHaveAttribute('href', '/coach/roster')
    expect(within(coachNavigation).queryByRole('link', { name: 'Member search' })).not.toBeInTheDocument()
  })

  it('closes the responsive staff menu on Escape and returns focus to its toggle', async () => {
    const user = userEvent.setup()
    setup(COACH, '/coach')
    const toggle = await screen.findByRole('button', { name: 'Open Coach workspace navigation' })
    const navigation = screen.getByRole('navigation', { name: 'Coach workspace navigation' })

    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(navigation.closest('.staff-layout')).toHaveClass('staff-layout--menu-open')

    await user.keyboard('{Escape}')
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(navigation.closest('.staff-layout')).not.toHaveClass('staff-layout--menu-open')
    expect(toggle).toHaveFocus()
  })
})
