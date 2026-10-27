import { readFileSync } from 'node:fs'
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

  it('UI-SYNC-COLOR-01: canonical action button tokens match Home primary blue across Light and Dark themes', () => {
    const tokensCss = readFileSync('src/styles/tokens.css', 'utf8')
    const componentsCss = readFileSync('src/styles/components.css', 'utf8')
    const homeCss = readFileSync('src/styles/home.css', 'utf8')

    // 1. Light theme tokens
    expect(tokensCss).toMatch(/--color-action-primary:\s*#1f4fd8;/)
    expect(tokensCss).toMatch(/--color-action-primary-hover:\s*#1a42b5;/)
    expect(tokensCss).toMatch(/--color-action-primary-active:\s*#16389a;/)
    expect(tokensCss).toMatch(/--color-action-primary-text:\s*#ffffff;/)

    // 2. Dark theme tokens match Home dark theme
    expect(tokensCss).toMatch(/--color-action-primary:\s*#3d6cf0;/)
    expect(tokensCss).toMatch(/--color-action-primary-hover:\s*#5a83f3;/)
    expect(tokensCss).toMatch(/--color-action-primary-text:\s*#ffffff;/)

    // 3. components.css btn--primary consumes canonical action tokens
    expect(componentsCss).toMatch(/\.btn--primary\s*\{[^}]*background:\s*var\(--color-action-primary\);/)
    expect(componentsCss).toMatch(/\.btn--primary\s*\{[^}]*color:\s*var\(--color-action-primary-text\);/)
    expect(componentsCss).toMatch(/\.btn--primary:hover:not\(:disabled\)\s*\{[^}]*background:\s*var\(--color-action-primary-hover\);/)

    // 4. Home page Find classes and Membership offers use the exact same primary blue
    expect(homeCss).toMatch(/--h-primary:\s*var\(--color-primary\);/)
    expect(homeCss).toMatch(/--h-primary:\s*#3d6cf0;/)
  })

  it('UI-SYNC-BRAND-02: canonical brand mark has no decorative gold border and is immutable across themes', () => {
    const tokensCss = readFileSync('src/styles/tokens.css', 'utf8')
    const componentsCss = readFileSync('src/styles/components.css', 'utf8')
    const layoutsCss = readFileSync('src/styles/layouts.css', 'utf8')
    const authCss = readFileSync('src/styles/auth.css', 'utf8')

    // Immutable brand mark tokens defined in :root
    expect(tokensCss).toMatch(/--color-brand-mark-bg:\s*#1f4fd8;/)
    expect(tokensCss).toMatch(/--color-brand-mark-fg:\s*#ffffff;/)

    // Brand mark has no gold border, border is 0 and radius is 0
    expect(componentsCss).toMatch(/\.brand__mark\s*\{[^}]*border:\s*0;/)
    expect(componentsCss).toMatch(/\.brand__mark\s*\{[^}]*border-radius:\s*0;/)
    expect(componentsCss).toMatch(/\.brand__mark\s*\{[^}]*background:\s*var\(--color-brand-mark-bg\);/)

    // Member layout header brand mark has no gold border or glow
    expect(layoutsCss).not.toMatch(/\.member-layout__header \.brand__mark\s*\{[^}]*border:\s*1px solid rgba\(255,\s*200,\s*61/)
    expect(layoutsCss).toMatch(/\.member-layout__header \.brand__mark\s*\{[^}]*border:\s*0;/)

    // Auth stage brand mark uses immutable tokens
    expect(authCss).toMatch(/\.auth-stage \.brand__mark\s*\{[^}]*background:\s*var\(--color-brand-mark-bg\);/)
  })

  it('UI-SYNC-HEADER-03: Log out button is rectangular and outlined with readable contrast over video headers', () => {
    const layoutsCss = readFileSync('src/styles/layouts.css', 'utf8')
    const homeCss = readFileSync('src/styles/home.css', 'utf8')

    // Default logout is rectangular (var(--radius-md)), not pill
    expect(layoutsCss).toMatch(/\.user-menu__logout\s*\{[^}]*border:\s*1px solid var\(--color-border\);/)
    expect(layoutsCss).toMatch(/\.user-menu__logout\s*\{[^}]*border-radius:\s*var\(--radius-md\);/)
    expect(layoutsCss).not.toMatch(/\.member-layout__header \.user-menu__logout\s*\{[^}]*border-radius:\s*var\(--radius-pill\);/)

    // Member layout header logout is rectangular, outlined, and high contrast white
    expect(layoutsCss).toMatch(/\.member-layout__header \.user-menu__logout\s*\{[^}]*border-radius:\s*var\(--radius-md\);/)
    expect(layoutsCss).toMatch(/\.member-layout__header \.user-menu__logout\s*\{[^}]*color:\s*#ffffff;/)
    expect(layoutsCss).toMatch(/\.member-layout__header \.user-menu__name\s*\{[^}]*color:\s*#ffffff;/)

    // Home account logout is high-contrast white and rectangular
    expect(homeCss).toMatch(/\.home-account \.user-menu__logout\s*\{[^}]*border-radius:\s*var\(--radius-md\);/)
    expect(homeCss).toMatch(/\.home-account \.user-menu__logout\s*\{[^}]*color:\s*#ffffff;/)
  })

  it('UI-SYNC-AUTH-04: floating video toggle is suppressed on AuthShell and present on Home', () => {
    // 1. AuthShell with enableVideo does NOT render floating video playback button
    const { container: authBox } = render(
      <MemoryRouter>
        <ThemeProvider>
          <AuthShell title="Create account" subtitle="Join Sportify" enableVideo>
            <div>Form content</div>
          </AuthShell>
        </ThemeProvider>
      </MemoryRouter>,
    )
    expect(authBox.querySelector('.home-hero__video-toggle')).toBeNull()

    // 2. HomeHeader / Home has no unintended playback toggle outside hero
    const { container: homeHeaderBox } = render(
      <MemoryRouter>
        <AuthContext.Provider value={mockAuthValue}>
          <ThemeProvider>
            <HomeHeader />
          </ThemeProvider>
        </AuthContext.Provider>
      </MemoryRouter>,
    )
    expect(homeHeaderBox.querySelector('.home-hero__video-toggle')).toBeNull()
  })
})
