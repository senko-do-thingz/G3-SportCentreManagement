import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { localDemoPaymentApi } from '../../api/localDemoPaymentApi'
import { payment } from '../../test/memberFixtures'
import { DemoQrCard } from './DemoQrCard'

describe('DemoQrCard Local Demo MVP', () => {
  beforeEach(() => {
    vi.spyOn(localDemoPaymentApi, 'capability').mockResolvedValue(true)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders single QR card with external QRServer image and server amount', async () => {
    render(
      <MemoryRouter>
        <DemoQrCard payment={payment({ id: 52, amount: 810000, code: 'PAY-52' })} />
      </MemoryRouter>
    )

    expect(screen.getByRole('heading', { name: 'Simulated QR Payment' })).toBeVisible()
    expect(screen.getByText(/Simulated QR Payment - Demo only - No real money transferred/)).toBeVisible()
    expect(screen.getByText('PAY-52')).toBeVisible()
    expect(screen.getByText(/810[.,]000 VND/)).toBeVisible()
    expect(screen.getByText(/PENDING/i)).toBeVisible()

    const img = screen.getByRole('img', { name: /Non-payable demo QR for payment 52/ })
    expect(img).toBeVisible()
    expect(img).toHaveAttribute('src', expect.stringContaining('api.qrserver.com/v1/create-qr-code'))
    expect(img).toHaveAttribute('src', expect.stringContaining(encodeURIComponent('SPORTIFY_DEMO|REF=PAY-52|NON_PAYABLE=1')))
    expect(img).toHaveAttribute('referrerpolicy', 'no-referrer')

    await waitFor(() => {
      const button = screen.getByRole('button', { name: 'Simulate Payment Confirmation' })
      expect(button).toBeEnabled()
    })
  })

  it('disables confirmation button and shows warning when local demo capability is false', async () => {
    vi.spyOn(localDemoPaymentApi, 'capability').mockResolvedValue(false)

    render(
      <MemoryRouter>
        <DemoQrCard payment={payment({ id: 52, amount: 810000 })} />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(
        screen.getByText(/Simulation is unavailable in local demo mode/i)
      ).toBeVisible()
    })

    const button = screen.getByRole('button', { name: 'Simulate Payment Confirmation' })
    expect(button).toBeDisabled()
  })

  it('handles successful simulated payment confirmation and calls onSettled', async () => {
    const user = userEvent.setup()
    const onSettled = vi.fn()
    const confirmSpy = vi.spyOn(localDemoPaymentApi, 'confirm').mockResolvedValueOnce({
      paymentId: 52,
      status: 'SUCCESS',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: 101,
      referenceCode: 'SIM-QR-52-TEST',
      invoiceId: 301,
      invoiceNumber: 'INV-2026-00301',
    })

    render(
      <MemoryRouter>
        <DemoQrCard payment={payment({ id: 52, amount: 810000 })} onSettled={onSettled} />
      </MemoryRouter>
    )

    const button = await screen.findByRole('button', { name: 'Simulate Payment Confirmation' })
    expect(button).toBeEnabled()

    await user.click(button)

    expect(confirmSpy).toHaveBeenCalledWith(52)
    expect(onSettled).toHaveBeenCalledOnce()

    await waitFor(() => {
      expect(screen.getByText(/Paid - Simulated. Payment successfully confirmed/i)).toBeVisible()
      const receiptLink = screen.getByRole('link', { name: 'View payment receipt' })
      expect(receiptLink).toBeVisible()
      expect(receiptLink).toHaveAttribute('href', '/member/payments/receipts/301')
    })
  })

  it('displays error notice when confirmation fails', async () => {
    const user = userEvent.setup()
    vi.spyOn(localDemoPaymentApi, 'confirm').mockRejectedValueOnce(
      new Error('Simulation failed. Rate limit exceeded.')
    )

    render(
      <MemoryRouter>
        <DemoQrCard payment={payment({ id: 52, amount: 810000 })} />
      </MemoryRouter>
    )

    const button = await screen.findByRole('button', { name: 'Simulate Payment Confirmation' })
    await user.click(button)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Simulation failed. Rate limit exceeded.')
  })

  it('falls back to offline SVG if third-party image fails to load', async () => {
    render(
      <MemoryRouter>
        <DemoQrCard payment={payment({ id: 52, amount: 810000 })} />
      </MemoryRouter>
    )

    const img = screen.getByRole('img', { name: /Non-payable demo QR for payment 52/ })
    fireEvent.error(img)

    await waitFor(() => {
      // SVG with role="img" replaces the broken image
      const svg = screen.getByRole('img', { name: /Non-payable demo QR for payment 52/ })
      expect(svg.tagName.toLowerCase()).toBe('svg')
    })
  })

  it('renders error notice for invalid payment amount', () => {
    render(
      <MemoryRouter>
        <DemoQrCard payment={payment({ id: 52, amount: 0 })} />
      </MemoryRouter>
    )

    expect(screen.getByText(/The demo QR could not be generated/i)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Simulate Payment Confirmation' })).not.toBeInTheDocument()
  })
})
