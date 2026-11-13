import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CancelPayment, HANDOFF_TOOLTIP } from './PaymentCancellation'
import { paymentsApi, type Payment } from '../../api/paymentsApi'

vi.mock('../../api/paymentsApi', () => ({ paymentsApi: { cancel: vi.fn(), submit: vi.fn() } }))
const pending = { id: 10, code: 'P-10', status: 'PENDING', amount: 100, method: 'BANK_TRANSFER',
  itemDescription: 'Package', originalPrice: 100, discount: 0, paymentTime: null, memberId: 1,
  memberCode: 'M-1', createdAt: null } as Payment

describe('Cancel Payment', () => {
  it('allows cancellation before handoff', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(paymentsApi.cancel).mockResolvedValue({ ...pending, status: 'CANCELLED' })
    const changed = vi.fn()
    render(<CancelPayment payment={pending} onChanged={changed} />)
    await userEvent.click(screen.getByRole('button', { name: 'Cancel Payment' }))
    expect(paymentsApi.cancel).toHaveBeenCalledWith(10)
    expect(changed).toHaveBeenCalled()
  })
  it('keeps a disabled, focusable explanation after handoff', () => {
    render(<CancelPayment payment={{ ...pending, submittedForVerificationAt: '2026-11-13T10:00:00' }} onChanged={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'Cancel Payment' })).toBeDisabled()
    expect(screen.getByTitle(HANDOFF_TOOLTIP)).toHaveAttribute('tabindex', '0')
  })
})
