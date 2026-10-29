import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { demoQrApi, type CreatedDemoSession } from '../../api/demoQrApi'
import { DemoSessionPanel } from './DemoSessionPanel'
import { DEMO_BANKS, type DemoBank } from './demoBanks'

function response(bank: DemoBank = 'vietcombank', paymentId = 52): CreatedDemoSession {
  return { session: {
    id: '00000000-0000-4000-8000-000000000052', paymentId, reference: `DEMO-${paymentId}`, amount: '810000.00', currency: 'VND',
    bankCode: DEMO_BANKS[bank].code, bankLabel: DEMO_BANKS[bank].label, createdAt: '2026-10-29T08:00:00Z',
    expiresAt: '2026-10-29T08:05:00Z', serverTime: '2026-10-29T08:00:00Z', state: 'PENDING', mode: 'NON_PAYABLE',
  }, confirmationUrl: `https://demo.example.test/demo/qr/confirm#token=${'a'.repeat(43)}` }
}

describe('QR-04 desktop session lifecycle', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_QR_PAYMENT_DEMO', 'true')
    vi.spyOn(demoQrApi, 'cancel').mockResolvedValue({ ...response().session, state: 'CANCELED' })
  })
  afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals() })

  it.each(Object.keys(DEMO_BANKS) as DemoBank[])('explicitly starts a separately labeled link for %s', async (bank) => {
    const create = vi.spyOn(demoQrApi, 'create').mockResolvedValue(response(bank))
    const user = userEvent.setup()
    render(<DemoSessionPanel paymentId={52} bank={bank} />)
    expect(create).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Start demo confirmation session' }))
    expect(create).toHaveBeenCalledWith(52, DEMO_BANKS[bank].code)
    expect(await screen.findByRole('img', { name: `Scan to Confirm Demo, ${DEMO_BANKS[bank].label}, payment 52` })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Open demo confirmation' })).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('bank/payment transitions hide old links immediately and revoke their sessions', async () => {
    vi.spyOn(demoQrApi, 'create').mockResolvedValue(response())
    const user = userEvent.setup()
    const view = render(<DemoSessionPanel paymentId={52} bank="vietcombank" />)
    await user.click(screen.getByRole('button', { name: 'Start demo confirmation session' }))
    await screen.findByRole('img')
    view.rerender(<DemoSessionPanel paymentId={52} bank="bidv" />)
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(demoQrApi.cancel).toHaveBeenCalledWith(response().session.id)
    view.rerender(<DemoSessionPanel paymentId={53} bank="bidv" />)
    expect(screen.queryByRole('link', { name: 'Open demo confirmation' })).not.toBeInTheDocument()
  })

  it('revokes a late create result when its owning view has unmounted', async () => {
    let complete!: (value: CreatedDemoSession) => void
    vi.spyOn(demoQrApi, 'create').mockImplementation(() => new Promise((resolve) => { complete = resolve }))
    const user = userEvent.setup()
    const view = render(<DemoSessionPanel paymentId={52} bank="vietcombank" />)
    await user.click(screen.getByRole('button', { name: 'Start demo confirmation session' }))
    view.unmount()
    complete(response())
    await waitFor(() => expect(demoQrApi.cancel).toHaveBeenCalledWith(response().session.id))
  })

  it('manual refresh reads CONFIRMED without changing the financial payment', async () => {
    vi.spyOn(demoQrApi, 'create').mockResolvedValue(response())
    const get = vi.spyOn(demoQrApi, 'get').mockResolvedValue({ ...response().session, state: 'CONFIRMED' })
    const user = userEvent.setup()
    render(<DemoSessionPanel paymentId={52} bank="vietcombank" />)
    await user.click(screen.getByRole('button', { name: 'Start demo confirmation session' }))
    await user.click(await screen.findByRole('button', { name: 'Refresh Demo Status' }))
    expect(get).toHaveBeenCalledWith(response().session.id)
    expect(await screen.findByText('Demo confirmation completed. The real payment remains unchanged.')).toBeVisible()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it.each(['CANCELED', 'EXPIRED'] as const)('refresh removes active link on terminal %s', async (state) => {
    vi.spyOn(demoQrApi, 'create').mockResolvedValue(response())
    vi.spyOn(demoQrApi, 'get').mockResolvedValue({ ...response().session, state })
    const user = userEvent.setup()
    render(<DemoSessionPanel paymentId={52} bank="vietcombank" />)
    await user.click(screen.getByRole('button', { name: 'Start demo confirmation session' }))
    await user.click(await screen.findByRole('button', { name: 'Refresh Demo Status' }))
    expect(await screen.findByText(state)).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Open demo confirmation' })).not.toBeInTheDocument()
  })

  it('reports creation failure without inventing a usable QR', async () => {
    vi.spyOn(demoQrApi, 'create').mockRejectedValue(new Error('Offline'))
    const user = userEvent.setup()
    render(<DemoSessionPanel paymentId={52} bank="vietcombank" />)
    await user.click(screen.getByRole('button', { name: 'Start demo confirmation session' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not update the demo session')
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it.each([true, false])('clipboard feedback is accessible when success=%s', async (success) => {
    vi.spyOn(demoQrApi, 'create').mockResolvedValue(response())
    const user = userEvent.setup()
    const write = success ? vi.fn().mockResolvedValue(undefined) : vi.fn().mockRejectedValue(new Error('Denied'))
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: write } })
    vi.stubGlobal('isSecureContext', true)
    render(<DemoSessionPanel paymentId={52} bank="vietcombank" />)
    await user.click(screen.getByRole('button', { name: 'Start demo confirmation session' }))
    await user.click(await screen.findByRole('button', { name: 'Copy demo confirmation link' }))
    expect(write).toHaveBeenCalledWith(response().confirmationUrl)
    expect(await screen.findByText(success ? /Time-limited demo link copied/ : /Could not copy the demo link/)).toBeVisible()
  })

  it('does not expose actions when the frontend demo flag is off', () => {
    vi.stubEnv('VITE_QR_PAYMENT_DEMO', 'false')
    render(<DemoSessionPanel paymentId={52} bank="vietcombank" />)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('clears an earlier copy success when the next copy is denied', async () => {
    vi.spyOn(demoQrApi, 'create').mockResolvedValue(response())
    const user = userEvent.setup()
    const write = vi.fn().mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('Denied'))
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: write } })
    vi.stubGlobal('isSecureContext', true)
    render(<DemoSessionPanel paymentId={52} bank="vietcombank" />)
    await user.click(screen.getByRole('button', { name: 'Start demo confirmation session' }))
    const button = await screen.findByRole('button', { name: 'Copy demo confirmation link' })
    await user.click(button)
    expect(await screen.findByText(/Time-limited demo link copied/)).toBeVisible()
    await user.click(button)
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not copy')
    expect(screen.queryByText(/Time-limited demo link copied/)).not.toBeInTheDocument()
  })

  it('does not publish a late copy success after session cancellation', async () => {
    vi.spyOn(demoQrApi, 'create').mockResolvedValue(response())
    const user = userEvent.setup()
    let complete!: () => void
    const write = vi.fn().mockImplementation(() => new Promise<void>((resolve) => { complete = resolve }))
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: write } })
    vi.stubGlobal('isSecureContext', true)
    render(<DemoSessionPanel paymentId={52} bank="vietcombank" />)
    await user.click(screen.getByRole('button', { name: 'Start demo confirmation session' }))
    await user.click(await screen.findByRole('button', { name: 'Copy demo confirmation link' }))
    await user.click(screen.getByRole('button', { name: 'Cancel Demo Session' }))
    complete()
    await waitFor(() => expect(screen.getByText('CANCELED')).toBeVisible())
    expect(screen.queryByText(/Time-limited demo link copied/)).not.toBeInTheDocument()
  })
})
