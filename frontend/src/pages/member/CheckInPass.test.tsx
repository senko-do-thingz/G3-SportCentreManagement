import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { CheckInPassPage } from './CheckInPassPage'
import { CheckInPassReader } from '../reception/CheckInPassReader'
import { checkInPassApi } from '../../api/checkInPassApi'
import QRCode from 'qrcode'

const identity = vi.hoisted(() => ({ user: { id: 1 } }))
vi.mock('../../auth/useAuth', () => ({ useAuth: () => identity }))
vi.mock('../../api/checkInPassApi', () => ({ checkInPassApi: { issue: vi.fn(), redeem: vi.fn() } }))
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn() } }))
const pass = { memberCode: 'MEM-TEST', token: `SCIN1.${'A'.repeat(43)}`, serverTime: '2026-11-02T03:00:00Z', expiresAt: '2026-11-02T03:02:00Z' }
beforeEach(() => {
  vi.resetAllMocks()
  identity.user = { id: 1 }
  vi.mocked(checkInPassApi.issue).mockResolvedValue(pass)
  vi.mocked(QRCode.toDataURL).mockResolvedValue('data:image/png;base64,fixture')
})
afterEach(() => vi.useRealTimers())

it('generates locally and clears the capability when the signed-in identity changes', async () => {
  const page = render(<MemoryRouter><CheckInPassPage /></MemoryRouter>)
  expect(checkInPassApi.issue).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Generate check-in pass' }))
  expect(await screen.findByAltText('Member arrival check-in QR')).toBeInTheDocument()
  expect(screen.getByText('MEM-TEST').style.fontSize).toBe('2rem')
  expect(QRCode.toDataURL).toHaveBeenCalledWith(pass.token, expect.any(Object))
  identity.user = { id: 2 }
  page.rerender(<MemoryRouter><CheckInPassPage /></MemoryRouter>)
  expect(screen.queryByAltText('Member arrival check-in QR')).not.toBeInTheDocument()
  expect(screen.queryByText('MEM-TEST')).not.toBeInTheDocument()
})

it('preserves the readable Member code if QR rendering fails', async () => {
  vi.mocked(QRCode.toDataURL).mockRejectedValue(new Error('QR image unavailable'))
  render(<MemoryRouter><CheckInPassPage /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Generate check-in pass' }))
  expect(await screen.findByText('MEM-TEST')).toBeInTheDocument()
  expect(await screen.findByText('QR image unavailable')).toBeInTheDocument()
  expect(screen.queryByAltText('Member arrival check-in QR')).not.toBeInTheDocument()
})

it('expires the image using elapsed time while retaining the manual code', async () => {
  vi.useFakeTimers()
  render(<MemoryRouter><CheckInPassPage /></MemoryRouter>)
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Generate check-in pass' })) })
  expect(screen.getByAltText('Member arrival check-in QR')).toBeInTheDocument()
  await act(async () => { vi.advanceTimersByTime(120500) })
  expect(screen.queryByAltText('Member arrival check-in QR')).not.toBeInTheDocument()
  expect(screen.getByText('MEM-TEST')).toBeInTheDocument()
  expect(screen.getByText('Pass expired. Generate a new pass.')).toBeInTheDocument()
})

it('rejects a payment QR before calling the arrival API', () => {
  render(<CheckInPassReader onRecorded={vi.fn()} />)
  fireEvent.change(screen.getByLabelText('Scanned arrival pass'), { target: { value: '000201010212VietQR' } })
  fireEvent.submit(screen.getByRole('form', { name: 'Member QR check-in' }))
  expect(checkInPassApi.redeem).not.toHaveBeenCalled()
  expect(screen.getByText(/Scan or paste a Member check-in pass/)).toBeInTheDocument()
})

it('submits one request during rapid scanner retries and clears the credential afterward', async () => {
  let complete!: (value: Awaited<ReturnType<typeof checkInPassApi.redeem>>) => void
  vi.mocked(checkInPassApi.redeem).mockReturnValue(new Promise(resolve => { complete = resolve }))
  const recorded = vi.fn()
  render(<CheckInPassReader onRecorded={recorded} />)
  fireEvent.change(screen.getByLabelText('Scanned arrival pass'), { target: { value: pass.token } })
  const form = screen.getByRole('form', { name: 'Member QR check-in' })
  fireEvent.submit(form)
  fireEvent.submit(form)
  expect(checkInPassApi.redeem).toHaveBeenCalledTimes(1)
  await act(async () => { complete({ id: 1, memberId: 1, memberName: 'Member', memberCode: pass.memberCode, bookingId: 1, bookingCode: 'BOOK-TEST', packageRegistrationId: 1, checkedInAt: null, result: 'ALLOWED', denialReason: null, note: null }) })
  await waitFor(() => expect(recorded).toHaveBeenCalledTimes(1))
  expect(screen.getByLabelText('Scanned arrival pass')).toHaveValue('')
})

it('clears a replayed credential and shows the server rejection', async () => {
  vi.mocked(checkInPassApi.redeem).mockRejectedValue(new Error('Check-in pass was already used; request a new pass'))
  render(<CheckInPassReader onRecorded={vi.fn()} />)
  fireEvent.change(screen.getByLabelText('Scanned arrival pass'), { target: { value: pass.token } })
  fireEvent.submit(screen.getByRole('form', { name: 'Member QR check-in' }))
  expect(await screen.findByText('Check-in pass was already used; request a new pass')).toBeInTheDocument()
  expect(screen.getByLabelText('Scanned arrival pass')).toHaveValue('')
})
