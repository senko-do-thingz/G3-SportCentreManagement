import { useEffect, useRef, useState, type ReactNode } from 'react'
import { BrowserQRCodeReader, type IScannerControls } from '@zxing/browser'
import { Button, Notice } from '../../components/ui'
import { CAMERA_MESSAGES, SAME_CODE_COOLDOWN_MS, cameraErrorMessage } from './cameraScan'

type ScannerState = { status: 'starting' } | { status: 'scanning' } | { status: 'error'; message: string }

interface CameraQrScannerProps {
  /** Goi endpoint check-in hien co; scanner tam dung nhan dien cho den khi promise ket thuc. */
  onScan: (code: string) => Promise<void>
  onClose: () => void
  /** Ket qua check-in moi nhat tu camera, hien ngay duoi khung camera. */
  result?: ReactNode
}

/**
 * Khung quet QR bang webcam cho man hinh check-in cua le tan.
 * Noi dung QR la member code; camera luon duoc giai phong khi dong, roi trang hoac unmount.
 */
export function CameraQrScanner({ onScan, onClose, result }: CameraQrScannerProps) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [state, setState] = useState<ScannerState>({ status: 'starting' })
  const [busy, setBusy] = useState(false)
  const onScanRef = useRef(onScan)
  useEffect(() => {
    onScanRef.current = onScan
  }, [onScan])

  useEffect(() => {
    let cancelled = false
    let stream: MediaStream | null = null
    let controls: IScannerControls | null = null
    let inFlight = false
    let lastCode = ''
    let lastAt = 0

    const release = () => {
      controls?.stop()
      controls = null
      stream?.getTracks().forEach((track) => track.stop())
      stream = null
      if (videoRef.current) videoRef.current.srcObject = null
    }

    const handleCode = async (raw: string) => {
      const code = raw.trim()
      const now = Date.now()
      if (!code || inFlight || cancelled) return
      if (code === lastCode && now - lastAt < SAME_CODE_COOLDOWN_MS) return
      inFlight = true
      lastCode = code
      setBusy(true)
      try {
        await onScanRef.current(code)
      } finally {
        // Tinh thoi gian cho tu luc co ket qua, de cung mot QR van nam truoc camera khong bi gui lai ngay.
        lastAt = Date.now()
        inFlight = false
        if (!cancelled) setBusy(false)
      }
    }

    const start = async () => {
      if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
        setState({ status: 'error', message: CAMERA_MESSAGES.unsupported })
        return
      }
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false })
        if (cancelled) {
          release()
          return
        }
        const reader = new BrowserQRCodeReader()
        controls = await reader.decodeFromStream(stream, videoRef.current ?? undefined, (decoded) => {
          if (decoded) void handleCode(decoded.getText())
        })
        if (cancelled) {
          release()
          return
        }
        setState({ status: 'scanning' })
      } catch (error) {
        release()
        if (!cancelled) setState({ status: 'error', message: cameraErrorMessage(error) })
      }
    }

    void start()
    window.addEventListener('pagehide', release)
    return () => {
      cancelled = true
      window.removeEventListener('pagehide', release)
      release()
    }
  }, [])

  return (
    <section className="reception-camera-scanner" aria-label="Camera QR scanner">
      <div className="reception-camera-scanner__header">
        <h3 className="reception-camera-scanner__title">Scan member QR</h3>
        <Button type="button" variant="secondary" size="sm" onClick={onClose}>
          Close scanner
        </Button>
      </div>
      {state.status === 'error' ? (
        <Notice tone="danger" live="assertive">
          {state.message}
        </Notice>
      ) : (
        <>
          <video
            ref={videoRef}
            className="reception-camera-scanner__preview"
            aria-label="Camera preview"
            muted
            playsInline
          />
          <p role="status" className="reception-camera-scanner__status">
            {state.status === 'starting'
              ? 'Starting camera...'
              : busy
                ? 'Checking in...'
                : 'Hold the member QR in front of the camera.'}
          </p>
        </>
      )}
      {result}
    </section>
  )
}
