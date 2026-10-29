import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import QRCode from 'qrcode'
import { payment } from '../../test/memberFixtures'
import { DemoQrCard } from './DemoQrCard'
import { DEMO_BANK_OPTIONS, demoDetails, normalizeDemoBank } from './demoDetails'

function clipboard(writeText = vi.fn<(text: string) => Promise<void>>().mockResolvedValue(undefined)) {
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
  return writeText
}

function deferred() {
  let resolve!: () => void
  let reject!: (error: Error) => void
  const promise = new Promise<void>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

describe('QR-02 selector and clipboard outcomes', () => {
  beforeEach(() => vi.stubGlobal('isSecureContext', true))
  afterEach(() => { vi.unstubAllGlobals(); Reflect.deleteProperty(document, 'execCommand') })

  it('uses a labeled native selector, safe account and explicit dynamic non-payable disclosure', () => {
    render(<DemoQrCard payment={payment()} />)
    const select = screen.getByRole('combobox', { name: 'Demo bank' })
    expect(select).toHaveValue('vietcombank')
    expect(select).toHaveAccessibleDescription(/QR updates.*plain text only.*cannot transfer money.*does not confirm a payment/)
    expect(within(select).getAllByRole('option').map((option) => option.textContent)).toEqual(DEMO_BANK_OPTIONS.map((option) => option.label))
    expect(screen.getByText('DEMO - NOT AVAILABLE')).toBeVisible()
    expect(screen.queryByText('0123456789')).not.toBeInTheDocument()
  })

  it.each(DEMO_BANK_OPTIONS)('selects $label and copies only its safe displayed details', async ({ value, label }) => {
    const user = userEvent.setup()
    const write = clipboard()
    render(<DemoQrCard payment={payment()} />)
    const qrBefore = screen.getByRole('img').querySelector('path')?.getAttribute('d')
    await user.selectOptions(screen.getByRole('combobox'), value)
    expect(screen.getByText(label, { selector: 'dd' })).toBeVisible()
    const qrAfter = screen.getByRole('img').querySelector('path')?.getAttribute('d')
    if (value === 'vietcombank') expect(qrAfter).toBe(qrBefore)
    else expect(qrAfter).not.toBe(qrBefore)
    expect(screen.getByRole('img')).toHaveAccessibleName(`Non-payable demo QR for payment 52, ${label} Encodes SPORTIFY_DEMO_QR version 2 plain text marked NON_PAYABLE with the selected demo bank code. No beneficiary account, payment URL or banking instruction is encoded.`)
    await user.click(screen.getByRole('button', { name: 'Copy demo details' }))
    expect(write).toHaveBeenCalledExactlyOnceWith(demoDetails(payment(), normalizeDemoBank(value)))
    expect(screen.getByRole('status')).toHaveTextContent('Demo details copied. Demo only - not a real payment.')
    await user.selectOptions(screen.getByRole('combobox'), value === 'mb' ? 'bidv' : 'mb')
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })

  it('copies the exact displayed reference with success and retains keyboard focus', async () => {
    const user = userEvent.setup()
    const write = clipboard()
    render(<DemoQrCard payment={payment()} />)
    screen.getByRole('combobox').focus()
    await user.tab()
    const button = screen.getByRole('button', { name: 'Copy demo reference' })
    expect(button).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(write).toHaveBeenCalledExactlyOnceWith(screen.getByText('DEMO-52', { selector: 'dd' }).textContent)
    expect(screen.getByRole('status')).toHaveTextContent('Demo reference copied.')
    expect(button).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('button', { name: 'Copy demo details' })).toHaveFocus()
    await user.tab({ shift: true })
    expect(button).toHaveFocus()
  })

  it('announces a rejected write as an error, never success or hidden fallback', async () => {
    const user = userEvent.setup()
    clipboard(vi.fn().mockRejectedValue(new DOMException('Denied', 'NotAllowedError')))
    const fallback = vi.fn().mockReturnValue(true)
    Object.defineProperty(document, 'execCommand', { configurable: true, value: fallback })
    render(<DemoQrCard payment={payment()} />)
    const button = screen.getByRole('button', { name: 'Copy demo reference' })
    button.focus()
    await user.keyboard(' ')
    expect(await screen.findByText('Could not copy. Select and copy the demo text manually.')).toBeVisible()
    expect(screen.getByRole('alert')).toHaveTextContent('Could not copy.')
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    expect(button).toHaveFocus()
    expect(fallback).not.toHaveBeenCalled()
  })

  it.each(['unsupported', 'insecure'])('reports %s clipboard honestly', async (mode) => {
    const user = userEvent.setup()
    const write = clipboard()
    if (mode === 'unsupported') Object.defineProperty(navigator, 'clipboard', { configurable: true, value: undefined })
    else vi.stubGlobal('isSecureContext', false)
    render(<DemoQrCard payment={payment()} />)
    await user.click(screen.getByRole('button', { name: 'Copy demo details' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Could not copy.')
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    expect(write).not.toHaveBeenCalled()
  })

  it('ignores completion after a bank switch and an older rejection after a newer copy', async () => {
    const user = userEvent.setup()
    const first = deferred(), second = deferred()
    const write = clipboard(vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise))
    render(<DemoQrCard payment={payment()} />)
    await user.click(screen.getByRole('button', { name: 'Copy demo details' }))
    await user.selectOptions(screen.getByRole('combobox'), 'bidv')
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    await user.click(screen.getByRole('button', { name: 'Copy demo details' }))
    expect(write.mock.calls[0][0]).toContain('Vietcombank (demo)')
    expect(write.mock.calls[1][0]).toContain('BIDV (demo)')
    await act(async () => second.resolve())
    expect(screen.getByRole('status')).toHaveTextContent('Demo details copied.')
    await act(async () => first.reject(new Error('Old rejected write')))
    expect(screen.getByRole('status')).toHaveTextContent('Demo details copied.')
    expect(screen.getByRole('alert')).toBeEmptyDOMElement()
  })

  it('suppresses success from old content even after switching away and back', async () => {
    const user = userEvent.setup()
    const old = deferred()
    clipboard(vi.fn().mockReturnValueOnce(old.promise))
    const view = render(<DemoQrCard payment={payment()} />)
    await user.click(screen.getByRole('button', { name: 'Copy demo reference' }))
    await user.selectOptions(screen.getByRole('combobox'), 'mb')
    await user.selectOptions(screen.getByRole('combobox'), 'vietcombank')
    await act(async () => old.resolve())
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    view.rerender(<DemoQrCard payment={payment({ id: 53, amount: 731234 })} />)
    await waitFor(() => expect(screen.getByText('DEMO-53')).toBeVisible())
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    expect(screen.getByRole('combobox')).toHaveValue('vietcombank')
  })

  it('cleans up pending writes on payment replacement', async () => {
    const user = userEvent.setup()
    const old = deferred()
    clipboard(vi.fn().mockReturnValueOnce(old.promise))
    const view = render(<DemoQrCard payment={payment()} />)
    await user.click(screen.getByRole('button', { name: 'Copy demo reference' }))
    view.rerender(<DemoQrCard payment={payment({ id: 53 })} />)
    await act(async () => old.resolve())
    expect(screen.getByText('DEMO-53')).toBeVisible()
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })

  it('reproduces matrix data on switch-back and updates it with the authorized payment', async () => {
    const user = userEvent.setup()
    const view = render(<DemoQrCard payment={payment()} />)
    const initial = screen.getByRole('img').querySelector('path')?.getAttribute('d')
    for (const bank of ['mb', 'bidv', 'techcombank', 'vietcombank']) await user.selectOptions(screen.getByRole('combobox'), bank)
    expect(screen.getByRole('img').querySelector('path')?.getAttribute('d')).toBe(initial)
    expect(screen.getByText(/Demo QR updated for Vietcombank.*payment 52/)).toHaveAttribute('aria-live', 'polite')
    view.rerender(<DemoQrCard payment={payment({ id: 53, amount: 731234 })} />)
    expect(screen.getByRole('img').querySelector('path')?.getAttribute('d')).not.toBe(initial)
    expect(screen.getByRole('img')).toHaveAccessibleName(/payment 53, Vietcombank/)
    clipboard()
    await user.click(screen.getByRole('button', { name: 'Copy demo reference' }))
    expect(navigator.clipboard.writeText).toHaveBeenCalledExactlyOnceWith('DEMO-53')
  })

  it('replaces a previous QR and copy controls with an accessible error when rendering fails', async () => {
    const user = userEvent.setup()
    const create = QRCode.create.bind(QRCode)
    vi.spyOn(QRCode, 'create').mockImplementation((...args) => {
      if (String(args[0]).includes('BIDV_DEMO')) throw new Error('Synthetic renderer failure with private diagnostics')
      return create(...args)
    })
    render(<DemoQrCard payment={payment()} />)
    expect(screen.getByRole('img')).toBeVisible()
    await user.selectOptions(screen.getByRole('combobox'), 'bidv')
    expect(screen.getByRole('alert')).toHaveTextContent('The demo QR could not be generated. No payment was made.')
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Copy demo/ })).not.toBeInTheDocument()
    expect(screen.queryByText(/private diagnostics/)).not.toBeInTheDocument()
  })

  it.each([0, -1, 1.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1])('fails safely for invalid payment ID %s', (id) => {
    render(<DemoQrCard payment={payment({ id })} />)
    expect(screen.getByRole('alert')).toHaveTextContent('The demo QR could not be generated.')
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
