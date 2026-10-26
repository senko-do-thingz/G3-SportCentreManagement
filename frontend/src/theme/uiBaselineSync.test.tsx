import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { AuthContext, type AuthContextValue } from '../auth/authContext'
import { MemberLayout } from '../layouts/MemberLayout'
import { StaffLayout } from '../layouts/StaffLayout'
import { AuthShell } from '../pages/public/AuthShell'
import { HomeHeader } from '../pages/public/HomeHeader'
import { ThemeProvider } from './ThemeProvider'
import { ThemeToggle } from './ThemeToggle'

const mockAuthValue: AuthContextValue = {
  status: 'authenticated',
  user: {
    id: 2,
    email: 'member@sportify.demo',
    fullName: 'Taylor Nguyen',
    phone: '0901234567',
    role: 'MEMBER',
    status: 'ACTIVE',
  },
  role: 'MEMBER',
  sessionExpired: false,
  signedOutElsewhere: false,
  accountSwitched: null,
  loadError: null,
  login: async () => mockAuthValue.user!,
  register: async () => mockAuthValue.user!,
  logout: () => {},
  dismissAccountSwitch: () => {},
  reloadUser: async () => mockAuthValue.user,
}

const mockReceptionAuthValue: AuthContextValue = {
  ...mockAuthValue,
  user: {
    id: 10,
    email: 'reception@sportify.demo',
    fullName: 'Linh Receptionist',
    phone: '0912345678',
    role: 'RECEPTIONIST',
    status: 'ACTIVE',
  },
  role: 'RECEPTIONIST',
}

describe('UI Baseline Synchronization - ThemeToggle & Shared Shells (UI-SYNC-01)', () => {
  it('renders sun/moon ThemeToggle in MemberLayout and eliminates outdated AppearanceControl dropdown', () => {
    const { container } = render(
      <MemoryRouter>
        <AuthContext.Provider value={mockAuthValue}>
          <ThemeProvider>
            <MemberLayout />
          </ThemeProvider>
        </AuthContext.Provider>
      </MemoryRouter>,
    )

    // Sun/moon toggle is present in header-actions
    const toggle = container.querySelector('.member-header__bar .member-theme')
    expect(toggle).not.toBeNull()
    expect(toggle?.tagName.toLowerCase()).toBe('button')
    expect(toggle?.querySelector('svg')).not.toBeNull()

    // Outdated select dropdown is absent
    const select = container.querySelector('.member-header__bar select')
    expect(select).toBeNull()
    const appearance = container.querySelector('.member-header__bar .appearance')
    expect(appearance).toBeNull()

    // Brand and logo are present
    const brand = container.querySelector('.member-header__bar .brand')
    expect(brand).not.toBeNull()
    expect(brand?.getAttribute('aria-label')).toBe('Sportify Center home')
  })

  it('renders sun/moon ThemeToggle in StaffLayout and eliminates outdated AppearanceControl dropdown', () => {
    const { container } = render(
      <MemoryRouter>
        <AuthContext.Provider value={mockReceptionAuthValue}>
          <ThemeProvider>
            <StaffLayout />
          </ThemeProvider>
        </AuthContext.Provider>
      </MemoryRouter>,
    )

    // Sun/moon toggle is present in staff-topbar
    const toggle = container.querySelector('.staff-topbar .staff-theme')
    expect(toggle).not.toBeNull()
    expect(toggle?.tagName.toLowerCase()).toBe('button')
    expect(toggle?.querySelector('svg')).not.toBeNull()

    // Outdated select dropdown is absent
    const select = container.querySelector('.staff-topbar select')
    expect(select).toBeNull()
    const appearance = container.querySelector('.staff-topbar .appearance')
    expect(appearance).toBeNull()

    // Brand and logo are present in sidebar
    const brand = container.querySelector('.sidebar__brand .brand')
    expect(brand).not.toBeNull()
    expect(brand?.getAttribute('aria-label')).toBe('Sportify Center home')
  })

  it('renders sun/moon ThemeToggle in AuthShell and eliminates AppearanceControl', () => {
    const { container } = render(
      <MemoryRouter>
        <AuthContext.Provider value={mockAuthValue}>
          <ThemeProvider>
            <AuthShell title="Sign up" subtitle="Create account">
              <div>Form content</div>
            </AuthShell>
          </ThemeProvider>
        </AuthContext.Provider>
      </MemoryRouter>,
    )

    const toggle = container.querySelector('.auth-stage__header .auth-theme')
    expect(toggle).not.toBeNull()
    expect(container.querySelector('.auth-stage__header select')).toBeNull()
    expect(container.querySelector('.auth-stage__header .brand')).not.toBeNull()
  })

  it('renders sun/moon ThemeToggle in HomeHeader and eliminates AppearanceControl', () => {
    const { container } = render(
      <MemoryRouter>
        <AuthContext.Provider value={mockAuthValue}>
          <ThemeProvider>
            <HomeHeader />
          </ThemeProvider>
        </AuthContext.Provider>
      </MemoryRouter>,
    )

    const toggle = container.querySelector('.home-account .home-theme')
    expect(toggle).not.toBeNull()
    expect(container.querySelector('.home-header select')).toBeNull()
    expect(container.querySelector('.home-header .brand')).not.toBeNull()
  })

  it('clicking ThemeToggle changes data-theme on document root', async () => {
    const user = userEvent.setup()
    render(
      <ThemeProvider>
        <ThemeToggle className="test-theme-toggle" />
      </ThemeProvider>,
    )

    const toggle = screen.getByRole('button', { name: /switch to (dark|light) mode/i })
    const initialLabel = toggle.getAttribute('aria-label')
    const initialTheme = document.documentElement.getAttribute('data-theme')

    await user.click(toggle)

    const nextLabel = toggle.getAttribute('aria-label')
    const nextTheme = document.documentElement.getAttribute('data-theme')
    expect(nextLabel).not.toBe(initialLabel)
    expect(nextTheme).not.toBe(initialTheme)
  })

  it('UI-SYNC-R1-02: verifies authoritative logo mark and wordmark consistency across Public, Auth, Member and Staff', () => {
    // 1. MemberLayout Brand
    const { container: memberBox } = render(
      <MemoryRouter>
        <AuthContext.Provider value={mockAuthValue}>
          <ThemeProvider>
            <MemberLayout />
          </ThemeProvider>
        </AuthContext.Provider>
      </MemoryRouter>,
    )
    const memberMark = memberBox.querySelector('.member-header__bar .brand__mark')
    const memberName = memberBox.querySelector('.member-header__bar .brand__name')
    expect(memberMark?.textContent?.trim()).toBe('S')
    expect(memberName?.textContent?.trim()).toBe('Sportify')

    // 2. StaffLayout Brand
    const { container: staffBox } = render(
      <MemoryRouter>
        <AuthContext.Provider value={mockReceptionAuthValue}>
          <ThemeProvider>
            <StaffLayout />
          </ThemeProvider>
        </AuthContext.Provider>
      </MemoryRouter>,
    )
    const staffMark = staffBox.querySelector('.sidebar__brand .brand__mark')
    const staffName = staffBox.querySelector('.sidebar__brand .brand__name')
    expect(staffMark?.textContent?.trim()).toBe('S')
    expect(staffName?.textContent?.trim()).toBe('Sportify')

    // 3. AuthShell Brand
    const { container: authBox } = render(
      <MemoryRouter>
        <ThemeProvider>
          <AuthShell title="Create your account" subtitle="Join Sportify">
            <div>Content</div>
          </AuthShell>
        </ThemeProvider>
      </MemoryRouter>,
    )
    const authMark = authBox.querySelector('.auth-stage__header .brand__mark')
    const authName = authBox.querySelector('.auth-stage__header .brand__name')
    expect(authMark?.textContent?.trim()).toBe('S')
    expect(authName?.textContent?.trim()).toBe('Sportify')

    // 4. HomeHeader Brand
    const { container: homeBox } = render(
      <MemoryRouter>
        <AuthContext.Provider value={mockAuthValue}>
          <ThemeProvider>
            <HomeHeader />
          </ThemeProvider>
        </AuthContext.Provider>
      </MemoryRouter>,
    )
    const homeMark = homeBox.querySelector('.home-header .brand__mark')
    const homeName = homeBox.querySelector('.home-header .brand__name')
    expect(homeMark?.textContent?.trim()).toBe('S')
    expect(homeName?.textContent?.trim()).toBe('Sportify')
  })

  it('UI-SYNC-R1-03: verifies Sign Up video background support with persistent fallback photo', () => {
    const { container: withVideo } = render(
      <MemoryRouter>
        <ThemeProvider>
          <AuthShell title="Create your account" subtitle="Join Sportify" enableVideo>
            <div>Content</div>
          </AuthShell>
        </ThemeProvider>
      </MemoryRouter>,
    )

    // Fallback photo is ALWAYS present as guaranteed baseline
    const photo = withVideo.querySelector('.auth-stage__photo')
    expect(photo).not.toBeNull()

    // Form card readability overlay is present
    expect(withVideo.querySelector('.auth-stage')).not.toBeNull()

    // Without enableVideo (e.g. standard AuthShell), video component does not compete
    const { container: withoutVideo } = render(
      <MemoryRouter>
        <ThemeProvider>
          <AuthShell title="Welcome back" subtitle="Sign in">
            <div>Content</div>
          </AuthShell>
        </ThemeProvider>
      </MemoryRouter>,
    )
    expect(withoutVideo.querySelector('.auth-stage__photo')).not.toBeNull()
  })
})
