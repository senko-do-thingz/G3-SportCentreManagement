import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import QRCode from 'qrcode'
import { localDemoPaymentApi } from '../../api/localDemoPaymentApi'
import { payment } from '../../test/memberFixtures'
import { DemoQrCard } from './DemoQrCard'

/**
 * Reconstruct the 2D boolean module matrix from the rendered SVG path.
 * In DemoQrCard, every module is rendered as a unit square path:
 * M(col + quietZone) (row + quietZone)h1v1h-1z with quietZone = 4.
 */
function parseSvgQrMatrix(svg: SVGElement | HTMLElement): { size: number; modules: boolean[][] } {
  const path = svg.querySelector('path')
  if (!path) throw new Error('Path element not found in QR SVG')
  const d = path.getAttribute('d') ?? ''
  const viewBox = svg.getAttribute('viewBox') ?? '0 0 0 0'
  const [, , vbWidth] = viewBox.split(' ').map(Number)
  const quietZone = 4
  const qrSize = vbWidth - quietZone * 2
  const grid: boolean[][] = Array.from({ length: qrSize }, () => Array(qrSize).fill(false))

  const regex = /M(\d+)\s+(\d+)h1v1h-1z/g
  let match: RegExpExecArray | null
  while ((match = regex.exec(d)) !== null) {
    const col = parseInt(match[1], 10) - quietZone
    const row = parseInt(match[2], 10) - quietZone
    if (col >= 0 && col < qrSize && row >= 0 && row < qrSize) {
      grid[row][col] = true
    }
  }
  return { size: qrSize, modules: grid }
}

describe('DemoQrCard Local Demo MVP', () => {
  beforeEach(() => {
    vi.spyOn(localDemoPaymentApi, 'capability').mockResolvedValue(true)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders external QR and falls back to a scannable local symbol with the same public reference', async () => {
    vi.spyOn(localDemoPaymentApi, 'confirm')
    render(
      <MemoryRouter>
        <DemoQrCard payment={payment({ id: 7, amount: 807500, code: 'PAY-1007' })} />
      </MemoryRouter>
    )

    expect(screen.getByRole('heading', { name: 'Simulated QR Payment' })).toBeVisible()
    expect(screen.getByText(/Simulated QR Payment - Demo only - No real money transferred/)).toBeVisible()
    expect(screen.getByText('PAY-1007')).toBeVisible()
    expect(screen.getByText(/807[.,]500 VND/)).toBeVisible()
    expect(screen.getByText(/PENDING/i)).toBeVisible()

    const image = screen.getByRole('img', { name: /Non-payable demo QR for payment 7/ })
    expect(image).toHaveAttribute('src', expect.stringContaining('https://api.qrserver.com/v1/create-qr-code/'))
    expect(image).toHaveAttribute('src', expect.stringContaining(encodeURIComponent('SPORTIFY_DEMO|REF=PAY-1007|NON_PAYABLE=1')))
    expect(image).toHaveAttribute('referrerpolicy', 'no-referrer')
    fireEvent.error(image)
    expect(localDemoPaymentApi.confirm).not.toHaveBeenCalled()

    // Scannable local SVG rendered via pinned qrcode dependency (DMO-02)
    const svg = screen.getByRole('img', { name: /Non-payable demo QR for payment 7/ })
    expect(svg).toBeVisible()
    expect(svg.tagName.toLowerCase()).toBe('svg')
    expect(svg).toHaveAttribute('data-testid', 'demo-qr-svg')

    // Decode and verify the rendered QR output (DMO-01)
    const expectedPayload = 'SPORTIFY_DEMO|REF=PAY-1007|NON_PAYABLE=1'
    const expectedQr = QRCode.create(expectedPayload, { errorCorrectionLevel: 'M' })
    const { size, modules } = parseSvgQrMatrix(svg)

    expect(size).toBe(expectedQr.modules.size)
    for (let r = 0; r < size; r++) {
      for (let c = 0; c < size; c++) {
        expect(modules[r][c]).toBe(Boolean(expectedQr.modules.get(r, c)))
      }
    }

    // Verify 7x7 finder patterns at top-left, top-right, bottom-left
    const verifyFinder = (startRow: number, startCol: number) => {
      for (let r = 0; r < 7; r++) {
        for (let c = 0; c < 7; c++) {
          const isBorder = r === 0 || r === 6 || c === 0 || c === 6
          const isCenter = r >= 2 && r <= 4 && c >= 2 && c <= 4
          const expectedDark = isBorder || isCenter
          expect(modules[startRow + r][startCol + c]).toBe(expectedDark)
        }
      }
    }
    verifyFinder(0, 0)
    verifyFinder(0, size - 7)
    verifyFinder(size - 7, 0)

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

  it('renders truthful fallback Back to Payment Summary when confirmed settlement lacks invoiceId', async () => {
    const user = userEvent.setup()
    vi.spyOn(localDemoPaymentApi, 'confirm').mockResolvedValueOnce({
      paymentId: 52,
      status: 'SUCCESS',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: 101,
      referenceCode: 'SIM-QR-52-TEST',
      invoiceId: null as unknown as number,
      invoiceNumber: null as unknown as string,
    })

    render(
      <MemoryRouter>
        <DemoQrCard payment={payment({ id: 52, amount: 810000 })} />
      </MemoryRouter>
    )

    const button = await screen.findByRole('button', { name: 'Simulate Payment Confirmation' })
    await user.click(button)

    await waitFor(() => {
      expect(screen.getByText(/Paid - Simulated. Payment successfully confirmed/i)).toBeVisible()
      expect(screen.queryByRole('link', { name: 'View payment receipt' })).not.toBeInTheDocument()
      const summaryLink = screen.getByRole('link', { name: 'Back to Payment Summary' })
      expect(summaryLink).toBeVisible()
      expect(summaryLink).toHaveAttribute('href', '/member/payments/52')
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

  it('renders error notice for invalid payment amount', () => {
    render(
      <MemoryRouter>
        <DemoQrCard payment={payment({ id: 52, amount: 0 })} />
      </MemoryRouter>
    )

    expect(screen.getByText(/The demo QR could not be generated/i)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Simulate Payment Confirmation' })).not.toBeInTheDocument()
  })

  it('rejects a confirmation response for a different payment', async () => {
    const onSettled = vi.fn()
    vi.spyOn(localDemoPaymentApi, 'confirm').mockResolvedValueOnce({
      paymentId: 99, status: 'SUCCESS', settlementType: 'SIMULATED', settlementProvider: 'SPORTIFY_SIMULATOR',
    })
    render(<MemoryRouter><DemoQrCard payment={payment({ id: 52 })} onSettled={onSettled} /></MemoryRouter>)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Simulate Payment Confirmation' })).toBeEnabled())
    await userEvent.click(screen.getByRole('button', { name: 'Simulate Payment Confirmation' }))
    expect(await screen.findByText(/Confirmation could not be verified/)).toBeVisible()
    expect(onSettled).not.toHaveBeenCalled()
    expect(screen.queryByText(/Paid - Simulated. Payment successfully confirmed/)).not.toBeInTheDocument()
  })

  it('ignores a completed request after navigating to another payment', async () => {
    let complete!: (value: Awaited<ReturnType<typeof localDemoPaymentApi.confirm>>) => void
    const onSettled = vi.fn()
    vi.spyOn(localDemoPaymentApi, 'confirm').mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
    const { rerender } = render(<MemoryRouter><DemoQrCard payment={payment({ id: 52 })} onSettled={onSettled} /></MemoryRouter>)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Simulate Payment Confirmation' })).toBeEnabled())
    await userEvent.click(screen.getByRole('button', { name: 'Simulate Payment Confirmation' }))
    rerender(<MemoryRouter><DemoQrCard payment={payment({ id: 53 })} onSettled={onSettled} /></MemoryRouter>)
    complete({ paymentId: 52, status: 'SUCCESS', settlementType: 'SIMULATED', settlementProvider: 'SPORTIFY_SIMULATOR' })
    await waitFor(() => expect(screen.getByRole('button', { name: 'Simulate Payment Confirmation' })).toBeEnabled())
    expect(onSettled).not.toHaveBeenCalled()
    expect(screen.queryByText(/Paid - Simulated. Payment successfully confirmed/)).not.toBeInTheDocument()
  })
})
