import { useEffect, useRef, useState, type ReactNode } from 'react'
import { BrowserQRCodeReader, type IScannerControls } from '@zxing/browser'
import { Button, Notice } from '../../components/ui'
import { CAMERA_MESSAGES, SAME_CODE_COOLDOWN_MS, cameraErrorMessage } from './cameraScan'

type ScannerState = { status: 'starting' } | { status: 'scanning' } | { status: 'error'; message: string }

interface CameraQrScannerProps {
  /** Returns true only when the backend saved the check-in. */
  onScan: (code: string) => Promise<boolean>
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
  const [retryCode, setRetryCode] = useState('')
  const retryCodeRef = useRef('')
  const lastCodeRef = useRef('')
  const lastAtRef = useRef(0)
  const onScanRef = useRef(onScan)
  useEffect(() => {
    onScanRef.current = onScan
  }, [onScan])

  useEffect(() => {
    let cancelled = false
    let stream: MediaStream | null = null
    let controls: IScannerControls | null = null
    let inFlight = false

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
      // A failed request stays paused until staff explicitly retries, avoiding a request on every
      // decode frame while the same QR remains in view.
      if (code === retryCodeRef.current) return
      if (code === lastCodeRef.current && now - lastAtRef.current < SAME_CODE_COOLDOWN_MS) return
      inFlight = true
      retryCodeRef.current = ''
      if (!cancelled) setRetryCode('')
      setBusy(true)
      try {
        const saved = await onScanRef.current(code)
        if (saved) {
          lastCodeRef.current = code
          lastAtRef.current = Date.now()
        } else {
          retryCodeRef.current = code
          if (!cancelled) setRetryCode(code)
        }
      } catch {
        retryCodeRef.current = code
        if (!cancelled) setRetryCode(code)
      } finally {
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

  const retry = () => {
    retryCodeRef.current = ''
    lastCodeRef.current = ''
    lastAtRef.current = 0
    setRetryCode('')
  }

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
                : retryCode
                  ? 'No check-in was saved for this QR. Check the result below, then retry or enter the member code.'
                : 'Hold the member QR in front of the camera.'}
          </p>
          {retryCode && (
            <Button type="button" variant="secondary" size="sm" onClick={retry}>
              Retry this QR
            </Button>
          )}
        </>
      )}
      {result}
    </section>
  )
}
