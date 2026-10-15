import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { paymentsApi } from '../../api/paymentsApi'
import { tokenStorage } from '../../api/tokenStorage'
import { AuthProvider } from '../../auth/AuthProvider'
import { ToastProvider } from '../../components/ui/ToastProvider'
import { AppRoutes } from '../../routes/AppRoutes'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { payment } from '../../test/memberFixtures'

const HISTORY = Array.from({ length: 45 }, (_, index) => payment({
  id: 100 - index, code: `HISTORY-${100 - index}`, status: index % 2 ? 'SUCCESS' : 'PENDING',
  createdAt: '2026-10-15T10:00:00', invoiceId: index % 2 ? 500 + index : null,
}))

function pageAnswer(call: RecordedCall, rows = HISTORY) {
  const params = new URL(call.path, 'http://api.test').searchParams
  const page = Number(params.get('page') ?? 0)
  const size = Number(params.get('size') ?? 20)
  const status = params.get('status')
  const filtered = status ? rows.filter(row => row.status === status) : rows
  return json(200, { content: filtered.slice(page * size, (page + 1) * size), page, size,
    totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size) })
}

function backend(handler = (call: RecordedCall): Response | Promise<Response> => pageAnswer(call)) {
  return installFakeFetch(call => {
    if (call.path === '/users/me') return json(200, MEMBER)
    if (call.path.startsWith('/payments/my?')) return handler(call)
    return backendError(404, 'Resource not found')
  })
}

function NavigationProbe() {
  const location = useLocation()
  const navigate = useNavigate()
  return <div>
    <output aria-label="Current URL">{location.pathname}{location.search}</output>
    <button onClick={() => navigate(-1)}>Browser back</button>
    <button onClick={() => navigate(1)}>Browser forward</button>
    <button onClick={() => navigate('/member/payments?page=2&size=20')}>Open last page</button>
  </div>
}

function renderHistory(url = '/member/payments') {
  return render(<MemoryRouter initialEntries={[url]}><ToastProvider><AuthProvider>
    <NavigationProbe /><AppRoutes />
  </AuthProvider></ToastProvider></MemoryRouter>)
}

function rows() {
  return within(screen.getByRole('table', { name: 'Payment history' })).getAllByRole('row').slice(1)
}

beforeEach(() => tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' }))
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })

describe('FE-11 server payment history pagination', () => {
  it('reaches first, subsequent and last pages of 45 payments without fetching all rows', async () => {
    const user = userEvent.setup()
    const { calls } = backend()
    renderHistory()
    await screen.findByText('HISTORY-100')
    expect(rows()).toHaveLength(20)
    expect(screen.getByText('Showing 1-20 of 45')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Next' }))
    await screen.findByText('HISTORY-80')
    expect(rows()).toHaveLength(20)
    expect(screen.getByLabelText('Current URL')).toHaveTextContent('page=1&size=20')
    await user.click(screen.getByRole('button', { name: 'Next' }))
    await screen.findByText('HISTORY-60')
    expect(rows()).toHaveLength(5)
    expect(screen.getByText('Showing 41-45 of 45')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
    expect(calls.filter(c => c.path.startsWith('/payments/my')).map(c => c.path)).toEqual([
      '/payments/my?page=0&size=20', '/payments/my?page=1&size=20', '/payments/my?page=2&size=20',
    ])
  })

  it('status filtering resets page and browser back/forward restores the query and rows', async () => {
    const user = userEvent.setup()
    const { calls } = backend()
    renderHistory('/member/payments?page=1&size=10')
    await screen.findByText('HISTORY-90')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Status' }), 'SUCCESS')
    await screen.findByText('HISTORY-99')
    expect(rows()).toHaveLength(10)
    expect(screen.getByLabelText('Current URL')).toHaveTextContent('page=0&size=10&status=SUCCESS')
    expect(calls.at(-1)?.path).toBe('/payments/my?status=SUCCESS&page=0&size=10')
    await user.click(screen.getByRole('button', { name: 'Next' }))
    await screen.findByText('HISTORY-79')
    await user.click(screen.getByRole('button', { name: 'Browser back' }))
    await screen.findByText('HISTORY-99')
    await user.click(screen.getByRole('button', { name: 'Browser back' }))
    await screen.findByText('HISTORY-90')
    expect(screen.getByRole('combobox', { name: 'Status' })).toHaveValue('')
    await user.click(screen.getByRole('button', { name: 'Browser forward' }))
    await screen.findByText('HISTORY-99')
    expect(screen.getByRole('combobox', { name: 'Status' })).toHaveValue('SUCCESS')
  })

  it('loads a shared URL with status, page and size and preserves unrelated query parameters', async () => {
    const { calls } = backend()
    renderHistory('/member/payments?status=pending&page=1&size=10&source=bookmark')
    await screen.findByText('HISTORY-80')
    expect(rows()).toHaveLength(10)
    await waitFor(() => expect(screen.getByLabelText('Current URL')).toHaveTextContent('status=PENDING&page=1&size=10&source=bookmark'))
    expect(calls.at(-1)?.path).toBe('/payments/my?status=PENDING&page=1&size=10')
  })

  it.each(['page=-1&size=0', 'page=1.5&size=101', 'page=2147483648&size=abc', 'page=abc&size=-2'])(
    'corrects invalid query %s before requesting', async query => {
      const { calls } = backend()
      renderHistory(`/member/payments?status=invalid&${query}`)
      await screen.findByText('HISTORY-100')
      await waitFor(() => expect(screen.getByLabelText('Current URL')).toHaveTextContent('/member/payments?page=0&size=20'))
      expect(calls.at(-1)?.path).toBe('/payments/my?page=0&size=20')
    },
  )

  it('corrects a removed or out-of-range page to the last existing page', async () => {
    const { calls } = backend()
    renderHistory('/member/payments?page=99&size=20')
    await screen.findByText('HISTORY-60')
    expect(rows()).toHaveLength(5)
    expect(screen.getByLabelText('Current URL')).toHaveTextContent('page=2&size=20')
    expect(calls.filter(c => c.path.startsWith('/payments/my')).map(c => c.path)).toEqual([
      '/payments/my?page=99&size=20', '/payments/my?page=2&size=20',
    ])
  })

  it('corrects an empty history to page zero and shows its empty state', async () => {
    backend(call => pageAnswer(call, []))
    renderHistory('/member/payments?page=8')
    await screen.findByRole('heading', { name: 'No payments yet' })
    expect(screen.getByLabelText('Current URL')).toHaveTextContent('page=0&size=20')
    expect(screen.queryByRole('navigation', { name: 'Pagination' })).not.toBeInTheDocument()
  })

  it('shows an API error and retries the same page with loading feedback', async () => {
    const user = userEvent.setup()
    let attempt = 0
    let release: () => void = () => undefined
    const { calls } = backend(call => ++attempt === 1 ? backendError(500, 'History unavailable') :
      new Promise<Response>(resolve => { release = () => resolve(pageAnswer(call)) }))
    renderHistory('/member/payments?page=1&size=20')
    expect(await screen.findByRole('alert')).toHaveTextContent('History unavailable')
    await user.click(screen.getByRole('button', { name: /Retry|Try again/ }))
    expect(screen.getByText('Loading rows').closest('[role="status"]')).toBeInTheDocument()
    await act(async () => release())
    await screen.findByText('HISTORY-80')
    expect(calls.filter(c => c.path.startsWith('/payments/my'))).toHaveLength(2)
  })

  it('aborts the old request and ignores its late response after page navigation', async () => {
    const user = userEvent.setup()
    let release: (response: Response) => void = () => undefined
    const { calls, fetchMock } = backend(call => call.path.includes('page=1&') ?
      new Promise<Response>(resolve => { release = resolve }) : pageAnswer(call))
    renderHistory('/member/payments?page=1&size=20')
    await waitFor(() => expect(calls.some(c => c.path.includes('page=1&'))).toBe(true))
    const oldCall = fetchMock.mock.calls.find(([input]) => String(input).includes('/payments/my?'))
    const signal = oldCall?.[1]?.signal
    await user.click(screen.getByRole('button', { name: 'Open last page' }))
    await screen.findByText('HISTORY-60')
    expect(signal?.aborted).toBe(true)
    await act(async () => release(pageAnswer({ method: 'GET', path: '/payments/my?page=1&size=20', authorization: null, body: null })))
    expect(rows()).toHaveLength(5)
    expect(screen.queryByText('HISTORY-80')).not.toBeInTheDocument()
  })

  it('preserves response order and invoice associations instead of sorting a partial page', async () => {
    backend(call => pageAnswer(call, [HISTORY[4], HISTORY[1], HISTORY[3]]))
    renderHistory()
    await screen.findByText('HISTORY-96')
    expect(rows().map(row => within(row).getByText(/^HISTORY-/).textContent)).toEqual(['HISTORY-96', 'HISTORY-99', 'HISTORY-97'])
    expect(screen.getByRole('link', { name: 'View receipt of HISTORY-99' })).toHaveAttribute('href', '/member/payments/receipts/501')
    expect(screen.getByRole('link', { name: 'View receipt of HISTORY-97' })).toHaveAttribute('href', '/member/payments/receipts/503')
  })

  it.each([[], { content: [] }, { content: [], page: 0, size: 0, totalElements: 0, totalPages: 0 }])(
    'rejects a history response without valid pagination metadata: %j', async answer => {
      backend(() => json(200, answer))
      await expect(paymentsApi.myPayments()).rejects.toThrow('unexpected')
    },
  )
})
