import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/errors'
import { managerActivityLogsApi } from '../../api/managerActivityLogsApi'
import { managerUsersApi } from '../../api/managerUsersApi'
import { tokenStorage } from '../../api/tokenStorage'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MANAGER } from '../../test/fakeBackend'
import type { ActivityLog, ManagerUser } from '../../types/manager'

const activity: ActivityLog = {
  id: 501,
  createdAt: '2026-10-14T09:30:00',
  actorId: 3,
  actorName: 'Morgan Lee',
  actorRole: 'MANAGER',
  action: 'PACKAGE_CREATED',
  entityType: 'SPORT_PACKAGE',
  entityId: 41,
  shortDetail: 'Created a package for the tennis program',
}

const actor: ManagerUser = {
  id: 3,
  fullName: 'Morgan Lee',
  email: 'morgan@example.test',
  phone: '0901234567',
  role: 'COACH',
  status: 'ACTIVE',
  createdAt: '2026-10-14T09:00:00',
}

const originalMatchMedia = Object.getOwnPropertyDescriptor(window, 'matchMedia')

function page<T>(content: T[], number = 0, totalElements = content.length) {
  return { content, page: number, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) }
}

function actors(count: number, start = 1): ManagerUser[] {
  return Array.from({ length: count }, (_, index) => ({
    ...actor,
    id: start + index,
    fullName: 'Actor ' + String(start + index).padStart(2, '0'),
  }))
}

function authenticateManager() {
  tokenStorage.startSession({ accessToken: 'manager-test', refreshToken: 'refresh-test' })
  return installFakeFetch((call) => call.path === '/users/me'
    ? json(200, MANAGER)
    : backendError(404, 'Unexpected request in activity-log test'))
}

function mockDesktopQuery(initialDesktop: boolean) {
  const listeners = new Set<(event: MediaQueryListEvent) => void>()
  const query = {
    matches: initialDesktop,
    media: '(min-width: 901px)',
    addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => listeners.add(listener),
    removeEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => listeners.delete(listener),
  } as unknown as MediaQueryList
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: vi.fn(() => query) })
  return (desktop: boolean) => {
    Object.defineProperty(query, 'matches', { configurable: true, value: desktop })
    const event = { matches: desktop, media: query.media } as MediaQueryListEvent
    act(() => listeners.forEach((listener) => listener(event)))
  }
}

beforeEach(() => {
  vi.stubEnv('VITE_MANAGER_PREVIEW', 'false')
  tokenStorage.startSession({ accessToken: 'manager-test', refreshToken: 'refresh-test' })
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  if (originalMatchMedia) Object.defineProperty(window, 'matchMedia', originalMatchMedia)
  else Reflect.deleteProperty(window, 'matchMedia')
})

describe('UI-16 manager activity log', () => {
  it('keeps filter state and clearing accessible when the panel is collapsed', async () => {
    authenticateManager()
    vi.spyOn(managerActivityLogsApi, 'list').mockResolvedValue(page([activity]))
    const setDesktop = mockDesktopQuery(false)
    const user = userEvent.setup()

    renderApp('/manager/activity')

    const toggle = await screen.findByRole('button', { name: 'Filters' })
    const panel = document.getElementById('manager-activity-filters')!
    expect(toggle).toHaveAttribute('aria-controls', 'manager-activity-filters')
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(panel).toHaveAttribute('hidden')

    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    await user.selectOptions(screen.getByLabelText('Action'), 'PACKAGE_CREATED')
    expect(screen.getByRole('button', { name: /Filters1/ })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Filters1/ }))
    expect(panel).toHaveAttribute('hidden')
    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(screen.getByRole('button', { name: 'Filters' })).toHaveAttribute('aria-expanded', 'false')
    expect(panel).toHaveAttribute('hidden')
    expect(panel.querySelector('select')).toHaveValue('')
    expect(screen.queryByRole('button', { name: /Filters1/ })).not.toBeInTheDocument()

    setDesktop(true)
    expect(screen.getByRole('button', { name: 'Filters' })).toHaveAttribute('aria-expanded', 'true')
    setDesktop(false)
    expect(screen.getByRole('button', { name: 'Filters' })).toHaveAttribute('aria-expanded', 'false')
  })

  it('starts expanded on desktop and follows the 900/901 pixel breakpoint', async () => {
    authenticateManager()
    vi.spyOn(managerActivityLogsApi, 'list').mockResolvedValue(page([]))
    const setDesktop = mockDesktopQuery(true)

    renderApp('/manager/activity')

    const toggle = await screen.findByRole('button', { name: 'Filters' })
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByLabelText('Action')).toBeVisible()
    setDesktop(false)
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    setDesktop(true)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
  })

  it('shows loading for the new activity query instead of a stale error', async () => {
    authenticateManager()
    const list = vi.spyOn(managerActivityLogsApi, 'list')
      .mockRejectedValueOnce(new ApiError(500, 'Activity service temporarily unavailable.'))
      .mockResolvedValue(page([activity]))
    const user = userEvent.setup()

    renderApp('/manager/activity')

    expect(await screen.findByText('Activity service temporarily unavailable.')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Search activity'), 'tennis')
    expect(screen.queryByText('Activity service temporarily unavailable.')).not.toBeInTheDocument()
    expect(await screen.findByText('Loading rows')).toBeInTheDocument()
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(
      expect.objectContaining({ search: 'tennis' }),
      expect.any(AbortSignal),
    ))
    expect(await screen.findByText(activity.shortDetail)).toBeInTheDocument()
  })

  it('keeps an actor picker error scoped to its settled search and describes its actual filters', async () => {
    authenticateManager()
    vi.spyOn(managerActivityLogsApi, 'list').mockResolvedValue(page([activity]))
    const list = vi.spyOn(managerUsersApi, 'list')
      .mockRejectedValueOnce(new ApiError(500, 'Actor service temporarily unavailable.'))
      .mockResolvedValue(page([actor]))
    const user = userEvent.setup()

    renderApp('/manager/activity')
    await user.click(await screen.findByRole('button', { name: 'Performed by All actors' }))

    const dialog = await screen.findByRole('dialog', { name: 'Choose an actor' })
    expect(await within(dialog).findByText('Actor service temporarily unavailable.')).toBeInTheDocument()
    expect(within(dialog).getByText('Search actors by name. Roles are shown for context; contact details are not shown.')).toBeInTheDocument()

    await user.type(within(dialog).getByLabelText('Search actors'), 'morgan')
    expect(within(dialog).queryByText('Actor service temporarily unavailable.')).not.toBeInTheDocument()
    expect(await within(dialog).findByText('Loading actors')).toBeInTheDocument()
    expect(await within(dialog).findByRole('button', { name: 'Use Morgan Lee' })).toBeInTheDocument()
    expect(list).toHaveBeenLastCalledWith(
      expect.objectContaining({ search: 'morgan' }),
      expect.any(AbortSignal),
    )
  })

  it('keeps pagination available on an empty page that still has actors', async () => {
    authenticateManager()
    vi.spyOn(managerActivityLogsApi, 'list').mockResolvedValue(page([activity]))
    const first = actors(20)
    const list = vi.spyOn(managerUsersApi, 'list').mockImplementation(({ page: requested = 0 }) =>
      Promise.resolve(requested === 1 ? page([], 1, 40) : page(first, 0, 40)))
    const user = userEvent.setup()

    renderApp('/manager/activity')
    await user.click(await screen.findByRole('button', { name: 'Performed by All actors' }))
    await within(screen.getByRole('dialog')).findByRole('button', { name: 'Use Actor 01' })
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Next' }))

    const dialog = within(screen.getByRole('dialog'))
    expect(await dialog.findByText('No actors are available on this page. Use the pagination controls to move between pages.')).toBeInTheDocument()
    expect(dialog.getByRole('navigation', { name: 'Pagination' })).toBeInTheDocument()
    expect(dialog.getByRole('button', { name: 'Previous' })).toBeEnabled()

    await user.click(dialog.getByRole('button', { name: 'Previous' }))
    expect(await dialog.findByRole('button', { name: 'Use Actor 01' })).toBeInTheDocument()
    expect(list.mock.calls.map(([query]) => query.page)).toEqual([0, 1, 0])
  })

  it('returns to the last valid actor page after removal makes the current page out of range', async () => {
    authenticateManager()
    vi.spyOn(managerActivityLogsApi, 'list').mockResolvedValue(page([activity]))
    const beforeRemoval = actors(20)
    const afterRemoval = actors(20)
    let pageZeroCalls = 0
    const list = vi.spyOn(managerUsersApi, 'list').mockImplementation(({ page: requested = 0 }) => {
      if (requested === 1) return Promise.resolve(page([], 1, 20))
      pageZeroCalls += 1
      return Promise.resolve(page(pageZeroCalls === 1 ? beforeRemoval : afterRemoval, 0, pageZeroCalls === 1 ? 21 : 20))
    })
    const user = userEvent.setup()

    renderApp('/manager/activity')
    await user.click(await screen.findByRole('button', { name: 'Performed by All actors' }))
    await within(screen.getByRole('dialog')).findByRole('button', { name: 'Use Actor 01' })
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Next' }))

    const dialog = within(screen.getByRole('dialog'))
    expect(await dialog.findByText('No matching actors')).toBeInTheDocument()
    await user.click(dialog.getByRole('button', { name: 'Return to page 1' }))
    expect(await dialog.findByRole('button', { name: 'Use Actor 01' })).toBeInTheDocument()
    expect(list.mock.calls.map(([query]) => query.page)).toEqual([0, 1, 0])

  })

  it('shows a truly empty actor result without unnecessary pagination', async () => {
    authenticateManager()
    vi.spyOn(managerActivityLogsApi, 'list').mockResolvedValue(page([activity]))
    vi.spyOn(managerUsersApi, 'list').mockResolvedValue(page([], 0, 0))
    const user = userEvent.setup()

    renderApp('/manager/activity')
    await user.click(await screen.findByRole('button', { name: 'Performed by All actors' }))

    const dialog = within(screen.getByRole('dialog'))
    expect(await dialog.findByText('No matching actors')).toBeInTheDocument()
    expect(dialog.queryByRole('navigation', { name: 'Pagination' })).not.toBeInTheDocument()
  })

  it('announces actor loading, offers retry after an error, and recovers', async () => {
    authenticateManager()
    vi.spyOn(managerActivityLogsApi, 'list').mockResolvedValue(page([activity]))
    let rejectLoad: (error: unknown) => void = () => {}
    const list = vi.spyOn(managerUsersApi, 'list')
      .mockImplementationOnce(() => new Promise((_resolve, reject) => { rejectLoad = reject }))
      .mockResolvedValueOnce(page([actor]))
    const user = userEvent.setup()

    renderApp('/manager/activity')
    await user.click(await screen.findByRole('button', { name: 'Performed by All actors' }))
    const dialog = within(screen.getByRole('dialog'))
    expect(await dialog.findByText('Loading actors')).toBeInTheDocument()

    await act(async () => rejectLoad(new ApiError(503, 'Actor lookup failed.')))
    expect(await dialog.findByText('Actor lookup failed.')).toBeInTheDocument()
    await user.click(dialog.getByRole('button', { name: 'Try again' }))
    expect(await dialog.findByRole('button', { name: 'Use Morgan Lee' })).toBeInTheDocument()
    expect(list).toHaveBeenCalledTimes(2)
  })

  it('wraps long actor names, omits contact data, and selects the correct actor', async () => {
    authenticateManager()
    vi.spyOn(managerActivityLogsApi, 'list').mockResolvedValue(page([activity]))
    const longName = 'Morgan Alexandra Lee With A Deliberately Long Display Name For Narrow Screens'
    const longActor = { ...actor, fullName: longName }
    vi.spyOn(managerUsersApi, 'list').mockResolvedValue(page([longActor]))
    const user = userEvent.setup()

    renderApp('/manager/activity')
    await user.click(await screen.findByRole('button', { name: 'Performed by All actors' }))
    const dialog = within(screen.getByRole('dialog'))
    expect(await dialog.findByRole('button', { name: 'Use ' + longName })).toBeInTheDocument()
    expect(dialog.getByText('Coach')).toBeInTheDocument()
    expect(dialog.queryByText('morgan@example.test')).not.toBeInTheDocument()
    expect(dialog.queryByText('0901234567')).not.toBeInTheDocument()

    await user.click(dialog.getByRole('button', { name: 'Use ' + longName }))
    expect(screen.getByRole('button', { name: 'Performed by ' + longName })).toBeInTheDocument()
    await waitFor(() => expect(managerActivityLogsApi.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ actorId: String(longActor.id) }),
      expect.any(AbortSignal),
    ))
  })

  it('preserves table semantics, keyboard scrolling, long details, and modal focus return', async () => {
    authenticateManager()
    const longDetail = 'Created package for ' + 'tennis '.repeat(40)
    const row = { ...activity, entityType: 'SPORT_PACKAGE_WITH_A_VERY_LONG_IDENTIFIER', entityId: 123456789012345, shortDetail: longDetail }
    vi.spyOn(managerActivityLogsApi, 'list').mockResolvedValue(page([row]))
    mockDesktopQuery(false)
    const user = userEvent.setup()

    renderApp('/manager/activity')

    const table = await screen.findByRole('table', { name: 'Activity history' })
    expect(within(table).getByRole('columnheader', { name: 'When' })).toBeInTheDocument()
    expect(within(table).getByRole('columnheader', { name: 'Activity' })).toBeInTheDocument()
    expect(within(table).getByRole('columnheader', { name: 'Target' })).toBeInTheDocument()
    expect(within(table).getByRole('columnheader', { name: 'Details' })).toBeInTheDocument()

    const scrollRegion = screen.getByRole('region', { name: 'Activity history table. Scroll horizontally to view all columns.' })
    expect(scrollRegion).toHaveAttribute('tabindex', '0')
    scrollRegion.focus()
    expect(scrollRegion).toHaveFocus()

    const detailsButton = await screen.findByRole('button', { name: 'View details for Package created by Morgan Lee' })
    expect(within(table).getByText(/Sport package with a very long identifier #123456789012345/)).toBeInTheDocument()
    await user.tab()
    expect(detailsButton).toHaveFocus()
    await user.click(detailsButton)
    const dialog = within(screen.getByRole('dialog', { name: 'Activity details' }))
    expect(dialog.getByText(/Created package for tennis/)).toHaveTextContent(longDetail.trim())
    expect(dialog.getByText('PACKAGE_CREATED')).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(detailsButton).toHaveFocus()
  })
  it('refreshes the activity list and updates its reported total', async () => {
    authenticateManager()
    const list = vi.spyOn(managerActivityLogsApi, 'list')
      .mockResolvedValueOnce(page([activity], 0, 21))
      .mockResolvedValueOnce(page([], 0, 0))
    const user = userEvent.setup()

    renderApp('/manager/activity')
    expect(await screen.findByText('21 events')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByText('0 events')).toBeInTheDocument()
    expect(list).toHaveBeenCalledTimes(2)
  })
})
