import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { CheckInQrPage } from './CheckInQrPage'
import { CHECK_IN_QR_OPTIONS, checkInQrApi } from '../../api/checkInQrApi'
import QRCode from 'qrcode'

vi.mock('../../api/checkInQrApi', async (original) => ({
  ...(await original<typeof import('../../api/checkInQrApi')>()),
  checkInQrApi: { mine: vi.fn() },
}))
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn() } }))
const fixedQr = { memberCode: 'MEM-TEST', qrContent: 'MEM-TEST' }

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(checkInQrApi.mine).mockResolvedValue(fixedQr)
  const toDataUrl = QRCode.toDataURL as unknown as ReturnType<typeof vi.fn>
  toDataUrl.mockImplementation(async (text: string) => `data:image/png;base64,${text}`)
})

const renderPage = () => render(<MemoryRouter><CheckInQrPage /></MemoryRouter>)

it('shows the fixed member QR and offers a download', async () => {
  renderPage()
  const image = await screen.findByAltText('Member check-in QR')
  expect(image).toHaveAttribute('src', 'data:image/png;base64,MEM-TEST')
  expect(screen.getByText('MEM-TEST').style.fontSize).toBe('2rem')
  expect(QRCode.toDataURL).toHaveBeenCalledWith('MEM-TEST', CHECK_IN_QR_OPTIONS)
  const download = screen.getByRole('link', { name: 'Download QR' })
  expect(download).toHaveAttribute('href', 'data:image/png;base64,MEM-TEST')
  expect(download).toHaveAttribute('download', 'sportify-check-in-MEM-TEST.png')
  expect(screen.queryByText(/expire/i)).not.toBeInTheDocument()
})

it('renders the identical QR every time the page is opened', async () => {
  const first = renderPage()
  const firstSrc = (await screen.findByAltText('Member check-in QR')).getAttribute('src')
  first.unmount()
  renderPage()
  const secondSrc = (await screen.findByAltText('Member check-in QR')).getAttribute('src')
  expect(secondSrc).toBe(firstSrc)
  expect(vi.mocked(QRCode.toDataURL).mock.calls).toEqual([
    ['MEM-TEST', CHECK_IN_QR_OPTIONS],
    ['MEM-TEST', CHECK_IN_QR_OPTIONS],
  ])
})

it('keeps the readable member code if QR rendering fails', async () => {
  vi.mocked(QRCode.toDataURL).mockRejectedValue(new Error('QR image unavailable'))
  renderPage()
  expect(await screen.findByText('MEM-TEST')).toBeInTheDocument()
  expect(await screen.findByText('QR image unavailable')).toBeInTheDocument()
  expect(screen.queryByAltText('Member check-in QR')).not.toBeInTheDocument()
})

it('shows the server error when the QR cannot be loaded', async () => {
  vi.mocked(checkInQrApi.mine).mockRejectedValue(new Error('Member not found'))
  renderPage()
  expect(await screen.findByText(/Member not found/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
})
