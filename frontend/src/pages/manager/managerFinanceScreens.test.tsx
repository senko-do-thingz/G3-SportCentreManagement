import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MANAGER, MEMBER } from '../../test/fakeBackend'
import { tokenStorage } from '../../api/tokenStorage'
import { revenueReportToCsv } from './managerRevenueCsv'

const overview = {
  paymentsCollected: 16110000,
  completedRefunds: 150000,
  netCollected: 15960000,
  collectedByService: [
    { service: 'Sport packages', collected: 12360000 },
    { service: 'Membership cards', collected: 2700000 },
    { service: 'Single visits', collected: 1050000 },
  ],
  needsAttention: { pendingPaymentsCount: 4, pendingRefundApprovalCount: 2, approvedNotReturnedCount: 0 },
}
const trend = [
  { month: '2026-05', revenue: 9000000 },
  { month: '2026-06', revenue: 10500000 },
  { month: '2026-07', revenue: 11100000 },
  { month: '2026-08', revenue: 12500000 },
  { month: '2026-09', revenue: 14700000 },
  { month: '2026-10', revenue: 16110000 },
]
const recent = {
  content: [{ id: 41, paymentCode: 'PAY-1041', invoiceNumber: 'INV-1041', memberFullName: 'Taylor Nguyen', itemDescription: 'Football / Coach-led / 30 days', amount: 810000, paymentMethod: 'BANK_TRANSFER', status: 'SUCCESS', paymentTime: '2026-10-17T09:00:00' }],
  page: 0,
  size: 5,
  totalElements: 1,
  totalPages: 1,
}
const pendingRefund = {
  id: 7,
  refundCode: 'RF-1040',
  packageRegistrationId: 10,
  registrationCode: 'REG-0010',
  memberId: 2,
  memberCode: 'MB-0248',
  memberFullName: 'Taylor Nguyen',
  amountRequested: '150000',
  amountApproved: null,
  reason: 'Cancelled before the deadline',
  status: 'PENDING',
  createdAt: '2026-10-17T10:00:00',
}
const revenueReport = {
  from: '2026-10-01',
  to: '2026-10-31',
  groupBy: 'SPORT' as const,
  totals: { collected: 16110000, refunded: 150000, netCollected: 15960000 },
  rows: [
    { category: 'Football', collected: 7200000, refunded: 0, netCollected: 7200000 },
    { category: 'Tennis', collected: 8900000, refunded: 150000, netCollected: 8750000 },
  ],
}

function financeApi(user = MANAGER) {
  tokenStorage.startSession({ accessToken: 'finance-manager-token', refreshToken: 'finance-refresh-token' })
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, user)
    if (call.path.startsWith('/manager/finance/overview')) {
      return call.path.includes('2026-09') ? json(200, { ...overview, paymentsCollected: 9100000, netCollected: 9050000 }) : json(200, overview)
    }
    if (call.path.startsWith('/manager/finance/trend')) return json(200, trend)
    if (call.path.startsWith('/manager/finance/transactions')) return json(200, recent)
    return backendError(404, `Unexpected request: ${call.method} ${call.path}`)
  })
}

function transactionsApi(options: { refundRows?: typeof pendingRefund[]; transactionError?: boolean; uncertainReview?: boolean } = {}) {
  tokenStorage.startSession({ accessToken: 'finance-manager-token', refreshToken: 'finance-refresh-token' })
  let pendingRows = options.refundRows ?? [pendingRefund]
  const { calls } = installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, MANAGER)
    if (call.path.startsWith('/manager/finance/transactions')) {
      if (options.transactionError) return backendError(404, 'Finance transaction endpoint is not available')
      return json(200, { ...recent, size: 20 })
    }
    if (call.path === '/refunds/pending' && call.method === 'GET') return json(200, pendingRows)
    if (call.path === '/refunds/7/review' && call.method === 'PUT') {
      const request = call.body as { approved: boolean; amountApproved: number | null; managerNote: string }
      pendingRows = []
      if (options.uncertainReview) return backendError(500, 'The response was lost after the review was applied')
      return json(200, { ...pendingRefund, status: request.approved ? 'APPROVED' : 'REJECTED', amountApproved: request.amountApproved, managerNote: request.managerNote })
    }
    return backendError(404, `Unexpected request: ${call.method} ${call.path}`)
  })
  return { calls }
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
})

describe('FE-13 financial overview', () => {
  it('renders exact API KPIs, service totals, attention counts, trend and recent transactions', async () => {
    const { calls } = financeApi()
    renderApp('/manager/finance?month=2026-10')

    const kpis = await screen.findByRole('region', { name: 'Financial key performance indicators' })
    expect(within(kpis).getByText('16.110.000 VND')).toBeInTheDocument()
    expect(within(kpis).getByText('150.000 VND')).toBeInTheDocument()
    expect(within(kpis).getByText('15.960.000 VND')).toBeInTheDocument()
    const serviceCard = screen.getByRole('heading', { name: 'Collected by service' }).closest('section')
    expect(within(serviceCard!).getByText('Sport packages')).toBeInTheDocument()
    expect(within(serviceCard!).getByText('12.360.000 VND')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '2 requests' })).toHaveAttribute('href', '/manager/transactions#refund-queue')
    expect(screen.getByRole('img', { name: 'Six-month collected revenue' })).toBeInTheDocument()
    expect(await screen.findByText('INV-1041')).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/manager/finance/overview?month=2026-10')).toBe(true)
    expect(calls.some((call) => call.path === '/manager/finance/trend?month=2026-10')).toBe(true)
    expect(calls.some((call) => call.path === '/manager/finance/transactions?month=2026-10&page=0&size=5')).toBe(true)
  })

  it('updates overview, trend and recent transaction queries when the month changes', async () => {
    const { calls } = financeApi()
    renderApp('/manager/finance?month=2026-10')
    const kpis = await screen.findByRole('region', { name: 'Financial key performance indicators' })
    expect(within(kpis).getByText('16.110.000 VND')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Reporting period'), { target: { value: '2026-09' } })

    await waitFor(() => expect(calls.some((call) => call.path === '/manager/finance/overview?month=2026-09')).toBe(true))
    expect(calls.some((call) => call.path === '/manager/finance/trend?month=2026-09')).toBe(true)
    expect(calls.some((call) => call.path === '/manager/finance/transactions?month=2026-09&page=0&size=5')).toBe(true)
    const updatedKpis = await screen.findByRole('region', { name: 'Financial key performance indicators' })
    expect(within(updatedKpis).getByText('9.100.000 VND')).toBeInTheDocument()
  })

  it('keeps manager-only routing and surfaces the backend denial without showing finance data', async () => {
    financeApi(MEMBER)
    renderApp('/manager/finance?month=2026-10')

    expect(await screen.findByText(/do not have access|access denied|forbidden/i)).toBeInTheDocument()
    expect(screen.queryByText('Payments collected')).not.toBeInTheDocument()
  })
})

describe('FE-13 transaction ledger and refund review', () => {
  it('loads transaction search, status, month and page from the URL and shows the pending refund queue', async () => {
    const { calls } = transactionsApi()
    renderApp('/manager/transactions?month=2026-10&query=Taylor&status=SUCCESS&page=1')

    expect(await screen.findByText('INV-1041')).toBeInTheDocument()
    expect(screen.getByText('RF-1040')).toBeInTheDocument()
    expect(screen.getByLabelText('Search transactions')).toHaveValue('Taylor')
    expect(calls.some((call) => call.path === '/manager/finance/transactions?month=2026-10&query=Taylor&status=SUCCESS&page=1&size=20')).toBe(true)
    expect(calls.some((call) => call.path === '/refunds/pending')).toBe(true)
  })

  it('approves the requested amount with a manager note and updates the queue without a page reload', async () => {
    const { calls } = transactionsApi()
    renderApp('/manager/transactions?month=2026-10')

    fireEvent.click(await screen.findByRole('button', { name: 'Review refund RF-1040' }))
    fireEvent.change(screen.getByLabelText(/Approved amount/), { target: { value: '125000' } })
    fireEvent.change(screen.getByLabelText(/Manager note/), { target: { value: 'Verified the cancellation timestamp.' } })
    fireEvent.click(screen.getByRole('button', { name: 'Approve refund' }))

    expect(await screen.findByText(/RF-1040 was approved for 125\.000 VND/)).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'PUT' && call.path === '/refunds/7/review')?.body).toEqual({
      approved: true,
      amountApproved: 125000,
      managerNote: 'Verified the cancellation timestamp.',
    })
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Review refund RF-1040' })).not.toBeInTheDocument())
    expect(calls.filter((call) => call.method === 'GET' && call.path === '/refunds/pending')).toHaveLength(2)
  })

  it('requires a rejection note and renders the rejected outcome', async () => {
    const { calls } = transactionsApi()
    renderApp('/manager/transactions')

    fireEvent.click(await screen.findByRole('button', { name: 'Review refund RF-1040' }))
    fireEvent.change(screen.getByLabelText('Decision'), { target: { value: 'REJECT' } })
    fireEvent.click(screen.getByRole('button', { name: 'Reject refund' }))
    expect(screen.getByText('Enter a manager note for this decision.')).toBeInTheDocument()
    expect(calls.some((call) => call.method === 'PUT')).toBe(false)

    fireEvent.change(screen.getByLabelText(/Manager note/), { target: { value: 'Request does not match the cancellation policy.' } })
    fireEvent.click(screen.getByRole('button', { name: 'Reject refund' }))

    expect(await screen.findByText(/RF-1040 was rejected\. Manager note: Request does not match/)).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'PUT' && call.path === '/refunds/7/review')?.body).toEqual({
      approved: false,
      amountApproved: null,
      managerNote: 'Request does not match the cancellation policy.',
    })
  })

  it('does not invite a duplicate decision when the review response is uncertain', async () => {
    const { calls } = transactionsApi({ uncertainReview: true })
    renderApp('/manager/transactions')

    fireEvent.click(await screen.findByRole('button', { name: 'Review refund RF-1040' }))
    fireEvent.change(screen.getByLabelText(/Manager note/), { target: { value: 'Verified against the payment record.' } })
    fireEvent.click(screen.getByRole('button', { name: 'Approve refund' }))

    expect(await screen.findByText(/no longer in the pending queue, but its decision could not be confirmed/i)).toBeInTheDocument()
    expect(calls.filter((call) => call.method === 'PUT' && call.path === '/refunds/7/review')).toHaveLength(1)
    expect(screen.queryByRole('button', { name: 'Review refund RF-1040' })).not.toBeInTheDocument()
  })

  it('keeps the refund queue usable when the finance transaction endpoint is unavailable', async () => {
    transactionsApi({ transactionError: true })
    renderApp('/manager/transactions')

    expect(await screen.findByText('Finance transaction endpoint is not available')).toBeInTheDocument()
    expect(await screen.findByText('RF-1040')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Refund approval queue' })).toBeInTheDocument()
  })
})

describe('FE-13 revenue reports and CSV export', () => {
  function revenueApi() {
    tokenStorage.startSession({ accessToken: 'finance-manager-token', refreshToken: 'finance-refresh-token' })
    return installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MANAGER)
      if (call.path === '/sports') return json(200, [{ id: 3, code: 'FT', name: 'Football', venueType: 'FIELD', description: null, imageUrl: null, displayOrder: 1 }])
      if (call.path.startsWith('/manager/finance/revenue')) return json(200, revenueReport)
      return backendError(404, `Unexpected request: ${call.method} ${call.path}`)
    })
  }

  it('loads the grouped report for URL date and sport filters with exact API totals', async () => {
    const { calls } = revenueApi()
    renderApp('/manager/reports?from=2026-10-01&to=2026-10-31&groupBy=SPORT&sportId=3')

    const totals = await screen.findByRole('region', { name: 'Revenue report totals' })
    expect(within(totals).getByText('16.110.000 VND')).toBeInTheDocument()
    expect(within(totals).getByText('150.000 VND')).toBeInTheDocument()
    expect(within(totals).getByText('15.960.000 VND')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /revenue by category/i })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Revenue report by category' })).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/manager/finance/revenue?from=2026-10-01&to=2026-10-31&groupBy=SPORT&sportId=3')).toBe(true)
  })

  it('updates the report request when grouping and sport filters change', async () => {
    const { calls } = revenueApi()
    renderApp('/manager/reports?from=2026-10-01&to=2026-10-31')
    await screen.findByRole('table', { name: 'Revenue report by category' })

    fireEvent.change(screen.getByLabelText('Group by'), { target: { value: 'SPORT' } })
    fireEvent.change(await screen.findByLabelText('Sport'), { target: { value: '3' } })

    await waitFor(() => expect(calls.some((call) => call.path === '/manager/finance/revenue?from=2026-10-01&to=2026-10-31&groupBy=SPORT&sportId=3')).toBe(true))
  })

  it('downloads a date-named CSV and reports that export is ready', async () => {
    const { calls } = revenueApi()
    const NativeURL = URL
    const createObjectURL = vi.fn((blob: Blob) => {
      expect(blob.type).toBe('text/csv;charset=utf-8')
      return 'blob:finance-report'
    })
    const revokeObjectURL = vi.fn()
    class URLWithBlobDownload extends NativeURL {
      static createObjectURL = createObjectURL
      static revokeObjectURL = revokeObjectURL
    }
    vi.stubGlobal('URL', URLWithBlobDownload)
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    renderApp('/manager/reports?from=2026-10-01&to=2026-10-31&groupBy=SPORT&sportId=3')
    await screen.findByRole('table', { name: 'Revenue report by category' })

    fireEvent.click(screen.getByRole('button', { name: 'Export CSV' }))

    expect(await screen.findByRole('status')).toHaveTextContent('CSV export ready: revenue-2026-10-01-2026-10-31.csv')
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob))
    expect(click).toHaveBeenCalledOnce()
    expect(click.mock.instances[0]).toHaveProperty('download', 'revenue-2026-10-01-2026-10-31.csv')
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:finance-report')
    expect(calls.some((call) => call.path.startsWith('/manager/finance/revenue'))).toBe(true)
  })

  it('quotes CSV values and neutralizes spreadsheet formula prefixes', () => {
    const csv = revenueReportToCsv({
      ...revenueReport,
      rows: [{ category: '=IMPORTXML("bad")', collected: 10, refunded: 0, netCollected: 10 }],
    })
    expect(csv).toContain('"\'=IMPORTXML(""bad"")"')
    expect(csv).toContain('"Total","16110000","150000","15960000"')
    expect(csv.startsWith('"Date range","2026-10-01 to 2026-10-31"')).toBe(true)
  })
})
