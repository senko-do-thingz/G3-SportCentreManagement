import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MANAGER, MEMBER } from '../../test/fakeBackend'
import { tokenStorage } from '../../api/tokenStorage'

const overviewDto = {
  month: '2026-10',
  from: '2026-10-01',
  to: '2026-10-31',
  paidRevenue: '16110000',
  paidTransactions: 12,
  averagePayment: '1342500',
  pendingAmount: '900000',
  pendingTransactions: 2,
  refundedAmount: '150000',
  refundedTransactions: 1,
  previousMonth: '2026-09',
  previousPaidRevenue: '17000000',
  revenueChange: '-890000',
  revenueChangePercent: '-5.24',
}
const trendDto = [
  { month: '2026-05', revenue: 9000000, transactions: 8 },
  { month: '2026-06', revenue: 10500000, transactions: 9 },
  { month: '2026-07', revenue: 11100000, transactions: 9 },
  { month: '2026-08', revenue: 12500000, transactions: 10 },
  { month: '2026-09', revenue: 14700000, transactions: 11 },
  { month: '2026-10', revenue: 16110000, transactions: 12 },
]
const transactionDto = {
  id: 41,
  code: 'PAY-1041',
  time: '2026-10-17T09:00:00',
  paymentTime: '2026-10-17T09:00:00',
  createdAt: '2026-10-17T08:58:00',
  status: 'SUCCESS',
  method: 'BANK_TRANSFER',
  amount: '810000',
  referenceCode: 'BANK-REF-41',
  memberId: 2,
  memberCode: 'MB-0248',
  memberName: 'Taylor Nguyen',
  itemType: 'SPORT_PACKAGE',
  itemCode: 'PK-001',
  itemName: 'Football 30-day package',
  sportName: 'Football',
  invoiceId: 82,
  invoiceNumber: 'INV-1041',
}
const transactionPageDto = {
  content: [transactionDto],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}
const pendingRefundDto = {
  id: 7,
  refundCode: 'RF-1040',
  packageRegistrationId: 10,
  paymentId: 41,
  paymentCode: 'PAY-1040',
  registrationCode: 'REG-0010',
  memberId: 2,
  memberCode: 'MB-0248',
  memberFullName: 'Taylor Nguyen',
  amountRequested: '150000',
  amountApproved: null,
  reason: 'Cancelled before the deadline',
  status: 'PENDING',
  requestedById: 2,
  requestedByName: 'Taylor Nguyen',
  reviewedById: null,
  reviewedByName: null,
  reviewedAt: null,
  managerNote: null,
  createdAt: '2026-10-17T10:00:00',
  cancelledBookings: 1,
}
const serviceBreakdownDto = {
  from: '2026-10-01',
  to: '2026-10-31',
  groupBy: 'ITEM_TYPE',
  totalRevenue: '16110000',
  totalTransactions: 12,
  rows: [
    { key: 'SPORT_PACKAGE', label: 'Sport packages', revenue: '12360000', transactions: 8, sharePercent: '76.72' },
    { key: 'MEMBERSHIP_CARD', label: 'Membership cards', revenue: '2700000', transactions: 3, sharePercent: '16.76' },
    { key: 'SINGLE_VISIT', label: 'Single visits', revenue: '1050000', transactions: 1, sharePercent: '6.52' },
  ],
}
const sportRevenueDto = {
  from: '2026-10-01',
  to: '2026-10-31',
  groupBy: 'SPORT',
  totalRevenue: '16110000',
  totalTransactions: 12,
  rows: [
    { key: '1', label: 'Football', revenue: '7200000', transactions: 6, sharePercent: '44.69' },
    { key: '2', label: 'Tennis', revenue: '8910000', transactions: 6, sharePercent: '55.31' },
  ],
}
const pendingRefundListDto = [pendingRefundDto]

function financeApi(user = MANAGER) {
  tokenStorage.startSession({ accessToken: 'finance-manager-token', refreshToken: 'finance-refresh-token' })
  const { calls } = installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, user)
    if (call.path.startsWith('/manager/finance/overview')) {
      return call.path.includes('2026-09')
        ? json(200, { ...overviewDto, month: '2026-09', from: '2026-09-01', to: '2026-09-30', paidRevenue: '9100000' })
        : json(200, overviewDto)
    }
    if (call.path === '/manager/finance/trend?months=6') return json(200, trendDto)
    if (call.path.startsWith('/manager/finance/transactions')) return json(200, { ...transactionPageDto, size: 5 })
    if (call.path.startsWith('/manager/finance/revenue')) return json(200, serviceBreakdownDto)
    if (call.path === '/refunds/pending') return json(200, pendingRefundListDto)
    return backendError(404, `Unexpected request: ${call.method} ${call.path}`)
  })
  return { calls }
}

function transactionsApi(options: { refundRows?: typeof pendingRefundDto[]; transactionError?: boolean; uncertainReviewStatus?: number } = {}) {
  tokenStorage.startSession({ accessToken: 'finance-manager-token', refreshToken: 'finance-refresh-token' })
  let pendingRows = options.refundRows ?? [pendingRefundDto]
  const { calls } = installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, MANAGER)
    if (call.path.startsWith('/manager/finance/transactions')) {
      if (options.transactionError) return backendError(404, 'Finance transaction endpoint is not available')
      return json(200, transactionPageDto)
    }
    if (call.path === '/refunds/pending' && call.method === 'GET') return json(200, pendingRows)
    if (call.path === '/refunds/7/review' && call.method === 'PUT') {
      const request = call.body as { approved: boolean; amountApproved: number | null; managerNote: string }
      pendingRows = []
      if (options.uncertainReviewStatus) return backendError(options.uncertainReviewStatus, 'The outcome of the review could not be confirmed')
      return json(200, { ...pendingRefundDto, status: request.approved ? 'APPROVED' : 'REJECTED', amountApproved: request.amountApproved, managerNote: request.managerNote })
    }
    return backendError(404, `Unexpected request: ${call.method} ${call.path}`)
  })
  return { calls }
}

function revenueApi() {
  tokenStorage.startSession({ accessToken: 'finance-manager-token', refreshToken: 'finance-refresh-token' })
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, MANAGER)
    if (call.path.startsWith('/manager/finance/revenue/export')) {
      return new Response('Code,Amount\r\nPAY-1041,810000\r\n', {
        status: 200,
        headers: {
          'Content-Type': 'text/csv;charset=UTF-8',
          'Content-Disposition': 'attachment; filename="revenue_2026-10-01_2026-10-31.csv"',
        },
      })
    }
    if (call.path.startsWith('/manager/finance/revenue')) {
      if (call.path.includes('groupBy=SPORT')) return json(200, sportRevenueDto)
      if (call.path.includes('groupBy=METHOD')) return json(200, {
        ...sportRevenueDto,
        groupBy: 'METHOD',
        rows: [{ key: 'BANK_TRANSFER', label: 'Bank transfer', revenue: '16110000', transactions: 12, sharePercent: '100.00' }],
      })
      return json(200, serviceBreakdownDto)
    }
    return backendError(404, `Unexpected request: ${call.method} ${call.path}`)
  })
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
  tokenStorage.clear()
})

describe('FE-13 financial overview', () => {
  it('renders BE-22 KPI, revenue grouping, attention and transaction DTO values', async () => {
    const { calls } = financeApi()
    renderApp('/manager/finance?month=2026-10')

    const kpis = await screen.findByRole('region', { name: 'Financial key performance indicators' })
    expect(within(kpis).getByText('16.110.000 VND')).toBeInTheDocument()
    expect(within(kpis).getByText('900.000 VND')).toBeInTheDocument()
    expect(within(kpis).getByText('150.000 VND')).toBeInTheDocument()
    expect(within(kpis).getByText('12 successful transactions')).toBeInTheDocument()
    const serviceCard = screen.getByRole('heading', { name: 'Collected by service' }).closest('section')
    expect(within(serviceCard!).getByText('Sport packages')).toBeInTheDocument()
    expect(within(serviceCard!).getByText('12.360.000 VND')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '1 requests' })).toHaveAttribute('href', '/manager/transactions#refund-queue')
    expect(screen.getByRole('img', { name: 'Six-month collected revenue through current month' })).toBeInTheDocument()
    expect(await screen.findByText('INV-1041')).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/manager/finance/overview?month=2026-10')).toBe(true)
    expect(calls.some((call) => call.path === '/manager/finance/trend?months=6')).toBe(true)
    expect(calls.some((call) => call.path === '/manager/finance/transactions?from=2026-10-01&to=2026-10-31&page=0&size=5')).toBe(true)
    expect(calls.some((call) => call.path === '/manager/finance/revenue?from=2026-10-01&to=2026-10-31&groupBy=ITEM_TYPE')).toBe(true)
    expect(calls.some((call) => call.path === '/refunds/pending')).toBe(true)
    expect(screen.queryByRole('link', { name: /approved.*not yet returned/i })).not.toBeInTheDocument()
    expect(calls.some((call) => call.path.includes('status=APPROVED'))).toBe(false)
  })

  it('updates the selected-month overview and transaction range while keeping trend semantics honest', async () => {
    const { calls } = financeApi()
    renderApp('/manager/finance?month=2026-10')
    const kpis = await screen.findByRole('region', { name: 'Financial key performance indicators' })
    expect(within(kpis).getByText('16.110.000 VND')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Reporting period'), { target: { value: '2026-09' } })

    await waitFor(() => expect(calls.some((call) => call.path === '/manager/finance/overview?month=2026-09')).toBe(true))
    expect(calls.some((call) => call.path === '/manager/finance/trend?months=6')).toBe(true)
    expect(calls.some((call) => call.path === '/manager/finance/transactions?from=2026-09-01&to=2026-09-30&page=0&size=5')).toBe(true)
    expect(calls.some((call) => call.path === '/manager/finance/revenue?from=2026-09-01&to=2026-09-30&groupBy=ITEM_TYPE')).toBe(true)
    const updatedKpis = await screen.findByRole('region', { name: 'Financial key performance indicators' })
    expect(within(updatedKpis).getByText('9.100.000 VND')).toBeInTheDocument()
  })

  it('keeps manager-only routing and surfaces the backend denial without showing finance data', async () => {
    financeApi(MEMBER)
    renderApp('/manager/finance?month=2026-10')

    expect(await screen.findByText(/do not have access|access denied|forbidden/i)).toBeInTheDocument()
    expect(screen.queryByText('Paid revenue')).not.toBeInTheDocument()
  })
})

describe('FE-13 transaction ledger and refund review', () => {
  it('maps transaction query state to BE-22 date and search parameters and shows BE-21 pending refunds', async () => {
    const { calls } = transactionsApi()
    renderApp('/manager/transactions?month=2026-10&query=Taylor&status=SUCCESS&page=1')

    expect(await screen.findByText('INV-1041')).toBeInTheDocument()
    expect(screen.getByText('RF-1040')).toBeInTheDocument()
    expect(screen.getByText('Football 30-day package')).toBeInTheDocument()
    expect(screen.getByLabelText('Search transactions')).toHaveValue('Taylor')
    expect(calls.some((call) => call.path === '/manager/finance/transactions?from=2026-10-01&to=2026-10-31&search=Taylor&status=SUCCESS&page=1&size=20')).toBe(true)
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

  it.each([408, 500])('does not invite a duplicate decision after an uncertain HTTP %i review result', async (status) => {
    const { calls } = transactionsApi({ uncertainReviewStatus: status })
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

describe('FE-13 revenue reports and backend CSV export', () => {
  it('uses backend revenue fields and sends only supported date and grouping filters', async () => {
    const { calls } = revenueApi()
    renderApp('/manager/reports?from=2026-10-01&to=2026-10-31&groupBy=SPORT&sportId=3')

    const totals = await screen.findByRole('region', { name: 'Revenue report totals' })
    expect(within(totals).getByText('16.110.000 VND')).toBeInTheDocument()
    expect(within(totals).getByText('12')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /revenue by category/i })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Revenue report by category' })).toBeInTheDocument()
    expect(screen.getByText('44.69%')).toBeInTheDocument()
    expect(screen.queryByLabelText('Sport')).not.toBeInTheDocument()
    expect(calls.some((call) => call.path === '/manager/finance/revenue?from=2026-10-01&to=2026-10-31&groupBy=SPORT')).toBe(true)
    expect(calls.some((call) => call.path.includes('sportId=3'))).toBe(false)
    expect(calls.some((call) => call.path === '/sports')).toBe(false)
    expect(screen.queryByText('Net collected')).not.toBeInTheDocument()
  })

  it('updates the report request using METHOD, the BE-22 enum value', async () => {
    const { calls } = revenueApi()
    renderApp('/manager/reports?from=2026-10-01&to=2026-10-31')
    await screen.findByRole('table', { name: 'Revenue report by category' })

    fireEvent.change(screen.getByLabelText('Group by'), { target: { value: 'METHOD' } })

    await waitFor(() => expect(calls.some((call) => call.path === '/manager/finance/revenue?from=2026-10-01&to=2026-10-31&groupBy=METHOD')).toBe(true))
    expect(calls.some((call) => call.path.includes('PAYMENT_METHOD'))).toBe(false)
  })

  it('downloads the authenticated backend CSV and uses its filename header', async () => {
    const { calls } = revenueApi()
    const NativeURL = URL
    const createObjectURL = vi.fn((blob: Blob) => {
      expect(blob.type).toMatch(/^text\/csv;charset=utf-8$/i)
      return 'blob:finance-report'
    })
    const revokeObjectURL = vi.fn()
    class URLWithBlobDownload extends NativeURL {
      static createObjectURL = createObjectURL
      static revokeObjectURL = revokeObjectURL
    }
    vi.stubGlobal('URL', URLWithBlobDownload)
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    renderApp('/manager/reports?from=2026-10-01&to=2026-10-31&groupBy=SPORT')
    await screen.findByRole('table', { name: 'Revenue report by category' })

    fireEvent.click(screen.getByRole('button', { name: 'Export CSV' }))

    expect(await screen.findByText('CSV export ready: revenue_2026-10-01_2026-10-31.csv')).toHaveAttribute('role', 'status')
    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(click).toHaveBeenCalledOnce()
    expect(click.mock.instances[0]).toHaveProperty('download', 'revenue_2026-10-01_2026-10-31.csv')
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:finance-report')
    const exportCall = calls.find((call) => call.path.startsWith('/manager/finance/revenue/export'))
    expect(exportCall).toMatchObject({
      method: 'GET',
      path: '/manager/finance/revenue/export?from=2026-10-01&to=2026-10-31',
      authorization: 'Bearer finance-manager-token',
    })
  })
})
