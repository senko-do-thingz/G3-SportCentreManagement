import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import type { Role } from '../../api/types'
import { backendError, installFakeFetch, json, MANAGER, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

const SPORTS = [{ id: 41, code: 'FOOTBALL', name: 'Football' }, { id: 52, code: 'BADMINTON', name: 'Badminton' }]
const PACKAGES = [{ id: 81, name: 'Football Independent', sportId: 41, sportName: 'Football', trainingFormat: 'SELF_TRAINING', durationDays: 30, sessionCount: 8, priceAmount: 500000, isActive: true }]
const COACHES = [
  { id: 21, fullName: 'Pham Thu Ha', headline: 'Swimming coach', specialties: 'Stroke technique, Water safety', yearsExperience: 6, bio: 'Helps nervous beginners feel safe in deep water.', avatarUrl: 'https://cdn.example.test/ha.jpg', sportIds: [52] },
  { id: 22, fullName: 'Le Hoang Nam', headline: null, specialties: null, yearsExperience: 4, sportIds: [41] },
  { id: 23, fullName: 'Tran Minh Quan', headline: null, specialties: null, yearsExperience: null, sportIds: [] },
]
const FACILITIES = [
  { id: 61, name: 'Badminton Court 1', facilityType: 'COURT', sportId: 52, capacity: 8 },
  { id: 62, name: 'Badminton Court 2', facilityType: 'COURT', sportId: 52, capacity: 8 },
  { id: 63, name: 'Football Field', facilityType: 'FIELD', sportId: 41, capacity: 22 },
]
const TIERS = [{ id: 1, name: 'Gold', discountPercentage: 5, isActive: true }]

function catalog(call: RecordedCall) {
  if (call.path === '/sports') return json(200, SPORTS)
  if (call.path === '/packages') return json(200, PACKAGES)
  if (call.path === '/membership-cards/tiers') return json(200, TIERS)
  if (call.path === '/coaches/public') return json(200, COACHES)
  if (call.path === '/facilities/public') return json(200, FACILITIES)
  return backendError(404, 'Not found')
}

function session(role: Role) {
  tokenStorage.startSession({ accessToken: 'home-access', refreshToken: 'home-refresh' })
  return { ...MEMBER, role }
}

afterEach(() => vi.unstubAllGlobals())

async function pick(user: ReturnType<typeof userEvent.setup>, label: string, option: string) {
  await user.click(screen.getByRole('button', { name: new RegExp(`^${label} `) }))
  await user.click(await screen.findByRole('option', { name: option }))
}

describe('public Home', () => {
  it('renders every reference section without authentication and only requests the public catalogue', async () => {
    const { calls } = installFakeFetch(catalog)
    renderApp('/')
    expect(screen.getByRole('heading', { name: /Find your sport.*Build your skills/ })).toBeInTheDocument()
    for (const name of ['Find your next class', 'Our features', 'Choose your sport', 'Train with the best', 'Choose how you train', 'Membership cards', 'Got questions?', 'Ready to play?']) {
      expect(screen.getByRole('heading', { name })).toBeInTheDocument()
    }
    expect(await screen.findByRole('link', { name: 'View options for Football Independent' })).toHaveAttribute('href', '/member/packages/81')
    expect(screen.getByText('Save on every sport package.')).toBeInTheDocument()
    expect(calls.map((call) => call.path).sort()).toEqual(['/coaches/public', '/facilities/public', '/membership-cards/tiers', '/packages', '/sports'])
    expect(calls.every((call) => call.method === 'GET' && call.authorization === null)).toBe(true)
    expect(screen.queryByRole('heading', { name: 'Welcome back' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^Training/ })).toHaveAttribute('aria-haspopup', 'listbox')
    expect(screen.queryByRole('heading', { name: 'Find a class that fits' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'All coaches' })).toBeInTheDocument()
  })

  it.each(['MEMBER', 'MANAGER', 'RECEPTIONIST', 'COACH'] as const)('keeps %s on Home and exposes its workspace', async (role) => {
    const profile = session(role)
    const { calls } = installFakeFetch((call) => call.path === '/users/me' ? json(200, profile) : catalog(call))
    renderApp('/')
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
    const paths = { MEMBER: '/member', MANAGER: '/manager', RECEPTIONIST: '/reception', COACH: '/coach' }
    expect(await screen.findByRole('link', { name: 'My workspace' })).toHaveAttribute('href', paths[role])
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
    expect(calls.filter((call) => call.path !== '/users/me').every((call) => call.authorization === null)).toBe(true)
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
  })

  it('renders primary content immediately while the stored session is still restoring', async () => {
    session('MEMBER')
    let resolveProfile: (value: Response) => void = () => undefined
    installFakeFetch((call) => call.path === '/users/me' ? new Promise<Response>((resolve) => { resolveProfile = resolve }) : catalog(call))
    renderApp('/')
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
    expect(screen.getByText('Restoring your session...')).toBeInTheDocument()
    await act(async () => resolveProfile(json(200, MEMBER)))
    expect(await screen.findByRole('link', { name: 'My workspace' })).toBeInTheDocument()
  })

  it('keeps Home visible after account restoration errors without discarding tokens', async () => {
    session('MEMBER')
    installFakeFetch((call) => call.path === '/users/me' ? backendError(503, 'Unavailable') : catalog(call))
    renderApp('/')
    await screen.findByRole('link', { name: 'Recover session' })
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
    expect(tokenStorage.hasSession()).toBe(true)
    expect(screen.queryByRole('heading', { name: 'We could not load your account' })).not.toBeInTheDocument()
  })

  it('shows recoverable catalogue errors while all main Home sections stay visible', async () => {
    let online = false
    installFakeFetch((call) => online ? catalog(call) : backendError(503, 'Unavailable'))
    const user = userEvent.setup()
    renderApp('/')
    const message = await screen.findByText('Current packages and prices are temporarily unavailable.')
    expect(screen.getByRole('heading', { name: 'Choose your sport' })).toBeInTheDocument()
    online = true
    await user.click(within(message.parentElement!).getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('link', { name: 'View options for Football Independent' })).toBeInTheDocument()
  })

  it('shows empty and malformed catalogues without presenting reference prices as current offers', async () => {
    installFakeFetch((call) => call.path === '/packages' ? json(200, []) : json(200, { unexpected: true }))
    renderApp('/')
    expect(await screen.findByText(/No packages are currently available/)).toBeInTheDocument()
    expect(await screen.findByText(/Sport filters are temporarily unavailable/)).toBeInTheDocument()
    expect(screen.queryByText('500,000 VND')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
  })

  it('rejects malformed catalogue records without crashing the public page', async () => {
    installFakeFetch(() => json(200, [null]))
    renderApp('/')
    expect(await screen.findByText(/Current packages and prices are temporarily unavailable/)).toBeInTheDocument()
    expect(await screen.findByText(/Sport filters are temporarily unavailable/)).toBeInTheDocument()
    expect(await screen.findByText(/Current card benefits are temporarily unavailable/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
  })

  it('features distinct training types from live packages and follows the existing zero-day single-visit rule', async () => {
    const coached = { ...PACKAGES[0], id: 82, name: 'Football Coached', trainingFormat: 'COACH_LED', priceAmount: 900000 }
    const single = { ...coached, id: 83, name: 'Football Day Visit', durationDays: 0, priceAmount: 150000 }
    installFakeFetch((call) => call.path === '/packages' ? json(200, [...PACKAGES, { ...PACKAGES[0], id: 85, name: 'Another self-training package' }, coached, single]) : catalog(call))
    renderApp('/')
    expect(await screen.findByRole('link', { name: 'View options for Football Coached' })).toHaveAttribute('href', '/member/packages/82')
    expect(screen.getByRole('link', { name: 'View options for Football Day Visit' })).toHaveAttribute('href', '/member/packages/83')
    expect(screen.queryByRole('link', { name: 'View options for Another self-training package' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Single visit' })).toBeInTheDocument()
    const coachCard = screen.getByRole('heading', { name: 'Coach-led' }).closest('article') as HTMLElement
    expect(within(coachCard).getByText('Popular')).toBeInTheDocument()
    expect(within(coachCard).getByText('Qualified coach')).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 3 }).map((heading) => heading.textContent).filter((text) => ['Single visit', 'Coach-led', 'Self-training'].includes(text ?? ''))).toEqual(['Single visit', 'Coach-led', 'Self-training'])
  })

  it('preserves selected sport and level through the existing login return flow', async () => {
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/auth/login') return json(200, { accessToken: 'logged-in', refreshToken: 'refresh' })
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path.startsWith('/sessions?')) return json(200, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      return catalog(call)
    })
    const user = userEvent.setup()
    renderApp('/')
    await pick(user, 'Sport', 'Football')
    await pick(user, 'Level', 'Beginner')
    await user.click(screen.getByRole('button', { name: 'Find classes' }))
    await user.click(within(screen.getByRole('dialog', { name: 'Sign in to continue' })).getByRole('link', { name: 'Log in' }))
    await screen.findByRole('heading', { name: 'Welcome back' })
    await user.type(screen.getByLabelText(/email address/i), 'member@example.test')
    await user.type(screen.getByLabelText(/^password/i), 'Test-password1!')
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    await screen.findByRole('heading', { name: 'Find Classes' })
    await waitFor(() => expect(calls.some((call) => call.path.startsWith('/sessions?') && call.path.includes('sportId=41') && call.path.includes('level=BEGINNER'))).toBe(true))
    expect(screen.getByRole('link', { name: 'Sportify Center home' })).toHaveAttribute('href', '/')
  })

  it('uses real catalogue IDs for sport exploration and package links', async () => {
    installFakeFetch(catalog)
    renderApp('/')
    expect(await screen.findByRole('link', { name: 'Explore Football' })).toHaveAttribute('href', '/member/classes?sportId=41')
    expect(screen.getByRole('link', { name: 'Explore Badminton' })).toHaveAttribute('href', '/member/classes?sportId=52')
    expect(screen.getByRole('link', { name: 'View options for Football Independent' })).toHaveAttribute('href', '/member/packages/81')
    expect(screen.getByRole('link', { name: 'View cards' })).toHaveAttribute('href', '/member/membership/cards')
    expect(within(screen.getByRole('banner')).getByRole('link', { name: 'Join now' })).toHaveAttribute('href', '/register')
  })

  it('explains member-only actions to staff without navigating to a forbidden route', async () => {
    session('MANAGER')
    installFakeFetch((call) => call.path === '/users/me' ? json(200, MANAGER) : catalog(call))
    const user = userEvent.setup()
    renderApp('/')
    await screen.findByRole('link', { name: 'My workspace' })
    await user.click(screen.getByRole('button', { name: 'Find classes' }))
    const dialog = screen.getByRole('dialog', { name: 'Member booking and purchases' })
    expect(within(dialog).getByRole('link', { name: 'Open my workspace' })).toHaveAttribute('href', '/manager')
    expect(screen.queryByRole('heading', { name: 'Access denied' })).not.toBeInTheDocument()
  })

  it.each(['All coaches', 'Contact reception', 'AI Assistant', 'View profile of Pham Thu Ha'])('opens an accessible availability dialog for %s and restores focus', async (name) => {
    installFakeFetch(catalog)
    const user = userEvent.setup()
    renderApp('/')
    const trigger = await screen.findByRole('button', { name })
    await user.click(trigger)
    expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'true')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })

  it('rotates the hero sport spotlight forwards and backwards with labelled controls', async () => {
    installFakeFetch(catalog)
    const user = userEvent.setup()
    renderApp('/')
    const strip = screen.getByRole('button', { name: 'Next sports' }).closest('.home-strip') as HTMLElement
    expect(within(strip).getAllByRole('heading').map((heading) => heading.textContent)).toEqual(['Football', 'Badminton'])
    await user.click(within(strip).getByRole('button', { name: 'Next sports' }))
    expect(within(strip).getAllByRole('heading').map((heading) => heading.textContent)).toEqual(['Badminton', 'Basketball'])
    await user.click(within(strip).getByRole('button', { name: 'Previous sports' }))
    await user.click(within(strip).getByRole('button', { name: 'Previous sports' }))
    expect(within(strip).getAllByRole('heading').map((heading) => heading.textContent)).toEqual(['Tennis', 'Football'])
  })

  it('shows the FAQ as native disclosure items with only the first answer open', async () => {
    installFakeFetch(catalog)
    const user = userEvent.setup()
    renderApp('/')
    const faq = screen.getByRole('region', { name: 'Got questions?' })
    const items = faq.querySelectorAll('details')
    expect(items).toHaveLength(4)
    expect(items[0]).toHaveAttribute('open')
    expect(items[3]).not.toHaveAttribute('open')
    await user.click(within(faq).getByText('Can I come for a single visit?'))
    expect(items[3]).toHaveAttribute('open')
    expect(within(faq).getByText(/membership card discounts do not apply/)).toBeInTheDocument()
  })

  it('operates the themed class finder dropdown with the keyboard and closes it with Escape', async () => {
    installFakeFetch(catalog)
    const user = userEvent.setup()
    renderApp('/')
    const level = screen.getByRole('button', { name: /^Level/ })
    level.focus()
    await user.keyboard('{Enter}')
    expect(level).toHaveAttribute('aria-expanded', 'true')
    const list = screen.getByRole('listbox', { name: 'Level' })
    expect(within(list).getByRole('option', { name: 'All levels' })).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{ArrowDown}{ArrowDown}{Enter}')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(level).toHaveTextContent('Intermediate')
    expect(level).toHaveFocus()
    await user.keyboard('{ArrowDown}')
    expect(within(screen.getByRole('listbox', { name: 'Level' })).getByRole('option', { name: 'Intermediate' })).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{Escape}')
    expect(level).toHaveAttribute('aria-expanded', 'false')
    expect(level).toHaveFocus()
  })

  it('shows a back to top button after scrolling', async () => {
    installFakeFetch(catalog)
    renderApp('/')
    const top = screen.getByRole('button', { name: 'Back to top', hidden: true })
    expect(top).not.toHaveClass('home-top--visible')
    act(() => { Object.defineProperty(window, 'scrollY', { value: 900, configurable: true }); window.dispatchEvent(new Event('scroll')) })
    expect(top).toHaveClass('home-top--visible')
    act(() => { Object.defineProperty(window, 'scrollY', { value: 0, configurable: true }); window.dispatchEvent(new Event('scroll')) })
  })

  it('builds facility tiles from the public facility list and preselects the sport in the class finder', async () => {
    installFakeFetch(catalog)
    const user = userEvent.setup()
    renderApp('/')
    const section = screen.getByRole('region', { name: 'Where you play' })
    await within(section).findByText('2 courts')
    const tiles = within(section).getAllByRole('listitem')
    expect(tiles.map((tile) => within(tile).getByRole('heading').textContent)).toEqual(['Badminton', 'Football Field'])
    expect(within(tiles[1]).getByText('1 field')).toBeInTheDocument()
    expect(section.querySelector('.home-venues')).toHaveClass('home-venues--even')
    expect(within(section).getByText('2 courts and 1 field, one membership card.')).toBeInTheDocument()
    expect(screen.getByText('2 sports. 1 center.')).toBeInTheDocument()
    await user.click(within(section).getByRole('button', { name: 'Find Badminton classes - Badminton, 2 courts' }))
    expect(screen.getByRole('button', { name: 'Sport Badminton' })).toHaveFocus()
  })

  it('keeps the facility photos without counts and offers Retry when the facility list fails', async () => {
    let fail = true
    installFakeFetch((call) => call.path === '/facilities/public' && fail ? backendError(503, 'Unavailable') : catalog(call))
    const user = userEvent.setup()
    renderApp('/')
    const section = screen.getByRole('region', { name: 'Where you play' })
    await within(section).findByText('Court details are temporarily unavailable.')
    expect(within(section).getAllByRole('heading', { level: 3 })).toHaveLength(6)
    expect(within(section).queryByText(/courts?$/)).not.toBeInTheDocument()
    fail = false
    await user.click(within(section).getByRole('button', { name: 'Retry' }))
    expect(await within(section).findByText('2 courts')).toBeInTheDocument()
  })

  it('shows coaches from the public coach list as photo-top cards with photo or initials, sport chip, years badge, name, headline, short bio and specialty tags', async () => {
    installFakeFetch(catalog)
    renderApp('/')
    const section = screen.getByRole('region', { name: 'Train with the best' })
    const ha = (await within(section).findByRole('heading', { name: 'Pham Thu Ha' })).closest('article') as HTMLElement
    expect(within(ha).getByText('Swimming coach')).toBeInTheDocument()
    expect(within(ha).getByText('Helps nervous beginners feel safe in deep water.')).toBeInTheDocument()
    expect(within(ha).getByText('6 years')).toBeInTheDocument()
    expect(within(within(ha).getByRole('list', { name: 'Specialties' })).getAllByRole('listitem').map((item) => item.textContent)).toEqual(['Stroke technique', 'Water safety'])
    expect(ha.querySelector('img')).toHaveAttribute('src', 'https://cdn.example.test/ha.jpg')
    await waitFor(() => expect(within(ha).getByText('Badminton')).toBeInTheDocument())
    const nam = within(section).getByRole('heading', { name: 'Le Hoang Nam' }).closest('article') as HTMLElement
    expect(within(nam).getByText('Le coaches Football at Sportify Center.')).toBeInTheDocument()
    expect(within(nam).getByText('4 years')).toBeInTheDocument()
    expect(within(nam).queryByRole('list', { name: 'Specialties' })).not.toBeInTheDocument()
    expect(within(nam).getByText('LN')).toBeInTheDocument()
    const quan = within(section).getByRole('heading', { name: 'Tran Minh Quan' }).closest('article') as HTMLElement
    expect(within(quan).getByText('Tran coaches at Sportify Center.')).toBeInTheDocument()
    expect(within(quan).getByText('Sportify Center coach')).toBeInTheDocument()
    expect(within(section).getByRole('button', { name: 'Previous coaches' })).toBeDisabled()
    expect(section).not.toHaveTextContent(/Daniel Smith|example\.com/)
  })

  it('keeps Home usable when the coach list is unavailable and retries it', async () => {
    let failCoaches = true
    installFakeFetch((call) => call.path === '/coaches/public' && failCoaches ? backendError(503, 'Unavailable') : catalog(call))
    const user = userEvent.setup()
    renderApp('/')
    const notice = await screen.findByText('Coach profiles are temporarily unavailable.')
    failCoaches = false
    await user.click(within(notice.parentElement!).getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('heading', { name: 'Le Hoang Nam' })).toBeInTheDocument()
  })

  it('loads high resolution sport photos and falls back to the bundled image when one fails', async () => {
    installFakeFetch(catalog)
    renderApp('/')
    const photo = screen.getByRole('img', { name: 'Swimming training' })
    expect(photo.getAttribute('src')).toMatch(/^https:\/\/images\.unsplash\.com\/photo-1530549387789/)
    fireEvent.error(photo)
    expect(photo.getAttribute('src')).not.toMatch(/unsplash/)
    expect(screen.getByRole('img', { name: 'Tennis training' }).getAttribute('src')).toMatch(/images\.unsplash\.com/)
  })

  it('sends the class finder sport, level and training format to Find Classes', async () => {
    installFakeFetch(catalog)
    const user = userEvent.setup()
    renderApp('/')
    await pick(user, 'Sport', 'Football')
    await pick(user, 'Training', 'Coach-led')
    expect(screen.getByRole('button', { name: /^Sport / })).toHaveTextContent('Football')
    await user.click(screen.getByRole('button', { name: 'Find classes' }))
    expect(screen.getByRole('dialog', { name: 'Sign in to continue' })).toHaveAccessibleDescription('Sign in to explore current classes with your selected filters.')
  })

  it('loads high resolution sport photos and falls back to the bundled image when one fails', async () => {
    installFakeFetch(catalog)
    renderApp('/')
    const photo = screen.getByRole('img', { name: 'Swimming training' })
    expect(photo.getAttribute('src')).toMatch(/^https:\/\/images\.unsplash\.com\/photo-1530549387789/)
    fireEvent.error(photo)
    expect(photo.getAttribute('src')).not.toMatch(/unsplash/)
    expect(screen.getByRole('img', { name: 'Tennis training' }).getAttribute('src')).toMatch(/images\.unsplash\.com/)
  })

  it('toggles the mobile navigation with keyboard controls and Escape restores toggle focus', async () => {
    installFakeFetch(catalog)
    const user = userEvent.setup()
    renderApp('/')
    const toggle = screen.getByRole('button', { name: 'Menu' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    const nav = screen.getByRole('navigation', { name: 'Home navigation' })
    within(nav).getByRole('link', { name: 'Sports' }).focus()
    await user.keyboard('{Escape}')
    expect(within(nav).getByRole('link', { name: 'Sports' })).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(toggle).toHaveFocus()
    await user.click(toggle)
    await user.click(within(nav).getByRole('link', { name: 'Sports' }))
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
  })

  it('keeps a cross-tab account switch on public Home', async () => {
    session('MANAGER')
    installFakeFetch((call) => call.path === '/users/me' ? json(200, call.authorization === 'Bearer member-access' ? MEMBER : MANAGER) : catalog(call))
    renderApp('/')
    await screen.findByText('Morgan Lee')
    const oldSession = tokenStorage.getSessionId()
    act(() => {
      localStorage.setItem('sportify.accessToken', 'member-access')
      localStorage.setItem('sportify.refreshToken', 'member-refresh')
      localStorage.setItem('sportify.sessionId', 'home-switch')
      window.dispatchEvent(new StorageEvent('storage', { key: 'sportify.sessionId', oldValue: oldSession, newValue: 'home-switch' }))
    })
    await screen.findByText('Taylor Nguyen')
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'My workspace' })).toHaveAttribute('href', '/member')
  })

  it('logs out with the existing session-clearing and login destination', async () => {
    session('MEMBER')
    installFakeFetch((call) => call.path === '/users/me' ? json(200, MEMBER) : catalog(call))
    const user = userEvent.setup()
    renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Log out' }))
    await screen.findByRole('heading', { name: 'Welcome back' })
    expect(tokenStorage.hasSession()).toBe(false)
    await user.click(screen.getByRole('link', { name: 'Sportify Center home' }))
    expect(await screen.findByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
  })
})
