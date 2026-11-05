import { useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'
import { Link } from 'react-router-dom'
import { checkInPassApi, type MemberCheckInPass } from '../../api/checkInPassApi'
import { getErrorMessage } from '../../api/errors'
import { Button, Card, Notice, PageHeader } from '../../components/ui'
import { useAuth } from '../../auth/useAuth'

/** A dedicated arrival capability, rendered locally and kept only in component memory. */
export function CheckInPassPage() {
  const { user } = useAuth()
  return <CheckInPassContent key={user?.id ?? 'signed-out'} />
}

function CheckInPassContent() {
  const [pass, setPass] = useState<MemberCheckInPass | null>(null)
  const [memberCode, setMemberCode] = useState('')
  const [expired, setExpired] = useState(false)
  const [image, setImage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [remaining, setRemaining] = useState(0)
  const controller = useRef<AbortController | null>(null)
  const generation = useRef(0)
  const inFlight = useRef(false)
  const deadline = useRef(0)

  useEffect(() => () => {
    generation.current += 1
    controller.current?.abort()
  }, [])

  useEffect(() => {
    if (!pass) return
    const update = () => {
      const seconds = Math.max(0, Math.ceil((deadline.current - performance.now()) / 1000))
      setRemaining(seconds)
      if (seconds === 0) {
        setImage(null)
        setPass(null)
        setExpired(true)
      }
    }
    const timer = window.setInterval(update, 500)
    return () => window.clearInterval(timer)
  }, [pass])

  const issue = async () => {
    if (inFlight.current) return
    inFlight.current = true
    const current = ++generation.current
    controller.current?.abort()
    const next = new AbortController()
    controller.current = next
    setBusy(true)
    setImage(null)
    setPass(null)
    setRemaining(0)
    setError(null)
    setExpired(false)
    const started = performance.now()
    try {
      const value = await checkInPassApi.issue(next.signal)
      if (generation.current !== current || next.signal.aborted) return
      setMemberCode(value.memberCode)
      if (!/^SCIN1\.[A-Za-z0-9_-]{43}$/.test(value.token)) throw new Error('No valid arrival pass was returned. Try again.')
      const duration = Date.parse(value.expiresAt) - Date.parse(value.serverTime)
      deadline.current = started + (Number.isFinite(duration) ? Math.max(0, Math.min(duration, 120000)) : 0)
      const qr = await QRCode.toDataURL(value.token, { width: 320, margin: 4, errorCorrectionLevel: 'M' })
      if (generation.current !== current || next.signal.aborted) return
      const seconds = Math.max(0, Math.ceil((deadline.current - performance.now()) / 1000))
      setRemaining(seconds)
      setPass(seconds > 0 ? value : null)
      setImage(seconds > 0 ? qr : null)
      setExpired(seconds === 0)
    } catch (failure) {
      if (generation.current === current && !next.signal.aborted) setError(getErrorMessage(failure))
    } finally {
      if (generation.current === current) {
        setBusy(false)
        inFlight.current = false
      }
    }
  }

  return <div className="page">
    <PageHeader title="Member check-in pass" description="Present this short-lived arrival pass to Reception." actions={<Link to="/member/profile">My profile</Link>} />
    <Card title="Arrival pass">
      <p>This pass records arrival through Reception. Your session attendance is recorded separately.</p>
      {error && <Notice tone="danger" live="assertive">{error}</Notice>}
      {memberCode && <>
        <p>Member code</p>
        <p style={{ fontSize: '2rem', fontWeight: 700 }}>{memberCode}</p>
        {image && remaining > 0 && <img src={image} width={320} height={320} alt="Member arrival check-in QR" />}
        {expired && <p role="status">Pass expired. Generate a new pass.</p>}
        {remaining > 0 && <p role="status">Valid for {remaining} seconds. Reception can use it once.</p>}
      </>}
      <Button type="button" onClick={() => void issue()} disabled={busy}>{busy ? 'Generating pass...' : memberCode ? 'Generate a new pass' : 'Generate check-in pass'}</Button>
      <p>Your Member code remains available as the manual fallback at Reception.</p>
    </Card>
  </div>
}
