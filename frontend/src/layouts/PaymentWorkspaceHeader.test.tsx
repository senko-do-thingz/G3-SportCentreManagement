import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { tokenStorage } from '../api/tokenStorage'
import { Brand } from '../components/Brand'
import { ThemeProvider } from '../theme/ThemeProvider'
import { ThemeToggle } from '../theme/ThemeToggle'
import { installFakeFetch, json, MEMBER } from '../test/fakeBackend'
import { payment } from '../test/memberFixtures'
import { renderApp } from '../test/renderApp'

describe('payment workspace canonical Home header', () => {
  it.each([
    ['/member/payments/52', 'MEMBER'],
    ['/member/payments/52/status', 'MEMBER'],
    ['/reception/refunds/11?memberId=2', 'RECEPTIONIST'],
  ])('%s uses the exact compact Brand and shared toggle without a legacy selector', async (route, role) => {
    localStorage.setItem('sportify.themePreference', 'light')
    tokenStorage.startSession({ accessToken: 'header-test-access', refreshToken: 'header-test-refresh' })
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, { ...MEMBER, role })
      if (call.path === '/payments/52') return json(200, payment())
      if (call.path === '/refunds/member/2') return json(200, [{ id: 11, refundCode: 'REF-0011', status: 'APPROVED', memberId: 2, amountRequested: 450000, amountApproved: 450000 }])
      return json(200, [])
    })
    const app = renderApp(route)
    await screen.findByRole('heading', { level: 1 })
    const header = app.container.querySelector('.payment-workspace-header') as HTMLElement
    expect(header).toBeInTheDocument()
    expect(header.querySelector('select')).not.toBeInTheDocument()
    expect(header.querySelector('.brand__tagline')).not.toBeInTheDocument()
    const reference = render(<MemoryRouter><ThemeProvider><Brand compact /><ThemeToggle className="home-theme" /></ThemeProvider></MemoryRouter>)
    expect(header.querySelector('.brand')?.innerHTML).toBe(reference.container.querySelector('.brand')?.innerHTML)
    expect(header.querySelector('.home-theme svg')?.innerHTML).toBe(reference.container.querySelector('.home-theme svg')?.innerHTML)
    reference.unmount()
    const user = userEvent.setup()
    await user.click(within(header).getByRole('button', { name: 'Switch to dark mode' }))
    expect(localStorage.getItem('sportify.themePreference')).toBe('dark')
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
    expect(within(header).getByRole('button', { name: 'Switch to light mode' })).toBeInTheDocument()
    await user.click(within(header).getByRole('link', { name: 'Sportify Center home' }))
    expect(await screen.findByRole('heading', { name: /Find your sport/i })).toBeInTheDocument()
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
  })
})
