import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
import { CAMERA_MESSAGES, SAME_CODE_COOLDOWN_MS } from './cameraScan'

/**
 * Camera QR scan on the Reception check-in page. The webcam (getUserMedia) and the QR library
 * (@zxing/browser) are mocked; the check-in itself goes through the real page and API client.
 */

type Decoded = { getText: () => string } | undefined
const zxing = vi.hoisted(() => ({
  callback: null as ((decoded: { getText: () => string } | undefined) => void) | null,
  stop: vi.fn(),
}))
vi.mock('@zxing/browser', () => ({
  BrowserQRCodeReader: class {
    decodeFromStream(_stream: unknown, _video: unknown, callback: (decoded: Decoded) => void) {
      zxing.callback = callback
      return Promise.resolve({ stop: zxing.stop })
    }
  },
}))

const RECEPTIONIST = { id: 9, email: 'reception@sportify.demo', fullName: 'Jamie Tran', phone: '0901234599', role: 'RECEPTIONIST', status: 'ACTIVE' }
const ALLOWED = {
  id: 80, memberId: 2, memberName: 'Taylor Nguyen', memberCode: 'MB-0248', bookingId: 21, bookingCode: 'BK-0021',
  packageRegistrationId: 10, checkedInAt: '2026-10-13T17:02:00', result: 'ALLOWED', denialReason: null, note: null,
}

let track: { stop: ReturnType<typeof vi.fn> }
let getUserMedia: ReturnType<typeof vi.fn>

function installCamera(result: 'ok' | Error = 'ok') {
  track = { stop: vi.fn() }
  getUserMedia = vi.fn(async () => {
    if (result !== 'ok') throw result
    return { getTracks: () => [track] }
  })
  Object.defineProperty(window, 'isSecureContext', { configurable: true, value: true })
  Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia } })
}

function backend(onCheckIn: (call: RecordedCall) => Response = () => json(201, ALLOWED)) {
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, RECEPTIONIST)
    if (call.path === '/check-ins' && call.method === 'POST') return onCheckIn(call)
    if (call.path.startsWith('/check-ins?date=')) return json(200, [])
    return backendError(404, 'Resource not found')
  })
}

async function openScanner() {
  tokenStorage.startSession({ accessToken: 'desk-access', refreshToken: 'desk-refresh' })
  const view = renderApp('/reception/check-in')
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Scan QR' }))
  const scanner = await screen.findByRole('region', { name: 'Camera QR scanner' })
  return { view, user, scanner }
}

async function detect(text: string) {
  await waitFor(() => expect(zxing.callback).not.toBeNull())
  await act(async () => {
    zxing.callback?.({ getText: () => text })
  })
}

const checkInPosts = (calls: RecordedCall[]) => calls.filter((call) => call.path === '/check-ins' && call.method === 'POST')

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 9, 13, 9, 30))
  zxing.callback = null
  zxing.stop.mockReset()
  installCamera()
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('Reception camera QR scan', () => {
  it('the Scan QR button opens the camera scanner with a live preview', async () => {
    backend()
    const { scanner } = await openScanner()
    expect(within(scanner).getByLabelText('Camera preview')).toBeInTheDocument()
    expect(await within(scanner).findByText('Hold the member QR in front of the camera.')).toBeInTheDocument()
    expect(getUserMedia).toHaveBeenCalledWith({ video: { facingMode: 'environment' }, audio: false })
    expect(screen.getByLabelText(/Member code or QR scan/)).toBeInTheDocument()
  })

  it('a detected code is trimmed and calls POST /check-ins once, repeats are ignored for a few seconds', async () => {
    const { calls } = backend()
    const { scanner } = await openScanner()
    await detect('  MB-0248 \n')
    await detect('MB-0248')
    await detect('MB-0248')
    await within(scanner).findByRole('status', { name: 'Check-in recorded' })
    await detect('MB-0248')
    expect(checkInPosts(calls)).toHaveLength(1)
    expect(checkInPosts(calls)[0].body).toEqual({ identifier: 'MB-0248', note: null })

    // After the cooldown the next member (or the same one again) can be scanned without reloading.
    vi.setSystemTime(new Date(Date.now() + SAME_CODE_COOLDOWN_MS + 1))
    await detect('MB-0248')
    await waitFor(() => expect(checkInPosts(calls)).toHaveLength(2))
  })

  it('a successful scan shows the member, the booking and the success status', async () => {
    backend()
    const { scanner } = await openScanner()
    await detect('MB-0248')
    const result = await within(scanner).findByRole('status', { name: 'Check-in recorded' })
    expect(within(result).getByText('Check-in successful - member may enter the centre')).toBeInTheDocument()
    expect(within(result).getByText('Taylor Nguyen')).toBeInTheDocument()
    expect(within(result).getByText('MB-0248')).toBeInTheDocument()
    expect(within(result).getByText('BK-0021')).toBeInTheDocument()
  })

  it.each([
    ['inactive account', () => json(400, { status: 400, message: 'Account is inactive, check-in denied', code: 'CHECK_IN_ACCOUNT_INACTIVE' }), 'Account is inactive, check-in denied'],
    ['unknown member code', () => backendError(404, 'Member not found'), 'Member not found'],
    ['package expired', () => json(201, { ...ALLOWED, id: 81, bookingId: null, bookingCode: null, packageRegistrationId: null, result: 'DENIED', denialReason: 'Sport package registration is expired or not yet valid' }), 'Sport package registration is expired or not yet valid'],
  ])('a rejected scan (%s) shows the rejected status and the backend reason', async (_case, response, reason) => {
    backend(response)
    const { scanner } = await openScanner()
    await detect('MB-0248')
    const result = await within(scanner).findByRole('status', { name: 'Check-in rejected' })
    expect(within(result).getByTestId('denial-reason')).toHaveTextContent(reason)
    expect(within(result).queryByText('Check-in successful - member may enter the centre')).not.toBeInTheDocument()
  })

  it.each([
    ['permission denied', new DOMException('Permission denied', 'NotAllowedError'), CAMERA_MESSAGES.permissionDenied],
    ['no camera', new DOMException('Requested device not found', 'NotFoundError'), CAMERA_MESSAGES.notFound],
    ['camera in use', new DOMException('Could not start video source', 'NotReadableError'), CAMERA_MESSAGES.inUse],
  ])('camera error (%s) shows a clear message and keeps manual entry', async (_case, error, message) => {
    installCamera(error)
    const { calls } = backend()
    const { scanner } = await openScanner()
    expect(await within(scanner).findByText(message)).toBeInTheDocument()
    expect(within(scanner).queryByLabelText('Camera preview')).not.toBeInTheDocument()
    expect(screen.getByLabelText(/Member code or QR scan/)).toBeInTheDocument()
    expect(checkInPosts(calls)).toHaveLength(0)
  })

  it('an unsupported browser shows a message without asking for the camera', async () => {
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: undefined })
    backend()
    const { scanner } = await openScanner()
    expect(await within(scanner).findByText(CAMERA_MESSAGES.unsupported)).toBeInTheDocument()
  })

  it('closing the scanner releases the camera', async () => {
    backend()
    const { scanner, user } = await openScanner()
    await within(scanner).findByText('Hold the member QR in front of the camera.')
    await user.click(within(scanner).getByRole('button', { name: 'Close scanner' }))
    expect(screen.queryByRole('region', { name: 'Camera QR scanner' })).not.toBeInTheDocument()
    expect(track.stop).toHaveBeenCalled()
    expect(zxing.stop).toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Scan QR' })).toBeInTheDocument()
  })

  it('leaving the page releases the camera', async () => {
    backend()
    const { scanner, view } = await openScanner()
    await within(scanner).findByText('Hold the member QR in front of the camera.')
    view.unmount()
    expect(track.stop).toHaveBeenCalled()
    expect(zxing.stop).toHaveBeenCalled()
  })
})
