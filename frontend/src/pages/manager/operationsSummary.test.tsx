import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json } from '../../test/fakeBackend'
import { OperationsSummary } from './OperationsSummary'

const response = (from: string, count: number) => ({ from, to: '2026-11-05', timeZone: 'Asia/Ho_Chi_Minh', registeredMembers: count, currentlyEnabledMembersRegistered: count - 1, classBookings: { total: 9, confirmed: 7, cancelled: 2, upcoming: 3, completed: 4 } })
afterEach(() => vi.unstubAllGlobals())
describe('Operations period report', () => {
  it('uses the selected range and clears the previous response while another range loads', async () => {
    tokenStorage.startSession({ accessToken: 'manager', refreshToken: 'refresh' })
    let resolve!: (response: Response) => void
    const { calls } = installFakeFetch(call => call.path.includes('from=2026-11-01') ? json(200, response('2026-11-01', 50)) : new Promise<Response>(done => { resolve = done }))
    const view = render(<OperationsSummary from="2026-11-01" to="2026-11-05" />)
    expect(await screen.findByText('50')).toBeInTheDocument()
    expect(screen.getByText(/subsets of confirmed/)).toBeInTheDocument()
    view.rerender(<OperationsSummary from="2026-11-03" to="2026-11-05" />)
    expect(screen.queryByText('50')).not.toBeInTheDocument()
    await waitFor(() => expect(calls).toHaveLength(2))
    resolve(json(200, response('2026-11-03', 5)))
    expect(await screen.findByText('5')).toBeInTheDocument()
  })
  it('shows backend date rejection without fabricated zeros', async () => {
    installFakeFetch(() => backendError(400, 'Invalid range'))
    render(<OperationsSummary from="2026-11-06" to="2026-11-05" />)
    expect(await screen.findByText('Invalid range')).toBeInTheDocument()
    expect(screen.queryByText('0')).not.toBeInTheDocument()
  })
})
