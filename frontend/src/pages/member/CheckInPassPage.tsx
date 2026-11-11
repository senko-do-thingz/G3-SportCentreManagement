import { useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'
import { Link } from 'react-router-dom'
import { checkInPassApi, type MemberCheckInPass } from '../../api/checkInPassApi'
import { getErrorMessage } from '../../api/errors'
import { useAuth } from '../../auth/useAuth'
import { Button, Card, Notice, buttonClassName } from '../../components/ui'
import {
  MemberCommerceCard,
  MemberCommerceHeader,
  MemberCommerceHero,
} from './MemberCommerceComponents'

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
      if (!/^SCIN1\.[A-Za-z0-9_-]{43}$/.test(value.token))
        throw new Error('No valid arrival pass was returned. Try again.')
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

  return (
    <div className="page member-commerce">
      <MemberCommerceHeader
        eyebrow="Member Workspace"
        title="Check-in"
        highlight="Pass"
        description="Present this short-lived arrival pass to Reception."
        actions={
          <Link className={buttonClassName('secondary')} to="/member/profile">
            My profile
          </Link>
        }
      />
      <MemberCommerceHero
        kicker="SPORTIFY FAST ARRIVAL"
        title="DIGITAL CHECK-IN PASS."
        highlight="INSTANT AND SECURE."
        description="Single-use dynamic QR pass for speedy arrival verification at the Sportify reception desk."
      />

      <div className="member-commerce-grid">
        <div className="member-commerce-column">
          <Card title="Arrival pass">
            <p style={{ margin: '0 0 var(--space-3)', color: 'var(--color-text-muted)', fontSize: 'var(--text-sm)' }}>
              This pass records arrival through Reception. Your session attendance is recorded separately.
            </p>
            {error && (
              <Notice tone="danger" live="assertive">
                {error}
              </Notice>
            )}

            {memberCode && (
              <div className="member-commerce-qr-box" style={{ margin: 'var(--space-4) 0' }}>
                <span className="member-commerce-kpi__label">Member code</span>
                <p
                  className="member-commerce-qr-box__code"
                  style={{ fontSize: '2rem', fontWeight: 700, margin: 'var(--space-2) 0' }}
                >
                  {memberCode}
                </p>

                {image && remaining > 0 && (
                  <div className="member-commerce-qr-box__frame">
                    <img
                      src={image}
                      width={320}
                      height={320}
                      alt="Member arrival check-in QR"
                      style={{ display: 'block' }}
                    />
                  </div>
                )}

                {expired && (
                  <p role="status" className="member-commerce-badge member-commerce-badge--warning">
                    Pass expired. Generate a new pass.
                  </p>
                )}

                {remaining > 0 && (
                  <p role="status" className="member-commerce-qr-box__timer">
                    Valid for {remaining} seconds. Reception can use it once.
                  </p>
                )}
              </div>
            )}

            <div style={{ marginTop: 'var(--space-4)' }}>
              <Button type="button" onClick={() => void issue()} disabled={busy}>
                {busy ? 'Generating pass...' : memberCode ? 'Generate a new pass' : 'Generate check-in pass'}
              </Button>
            </div>
            <p style={{ marginTop: 'var(--space-4)', fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
              Your Member code remains available as the manual fallback at Reception.
            </p>
          </Card>
        </div>

        <div className="member-commerce-column">
          <MemberCommerceCard title="Check-In Guidelines">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
              <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
                <span className="member-commerce-badge member-commerce-badge--info" aria-hidden="true">
                  1
                </span>
                <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                  Each QR code is generated securely in browser memory and valid for up to 120 seconds.
                </p>
              </div>
              <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
                <span className="member-commerce-badge member-commerce-badge--info" aria-hidden="true">
                  2
                </span>
                <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                  Show the QR pass directly to the optical scanner or receptionist counter tablet on arrival.
                </p>
              </div>
              <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
                <span className="member-commerce-badge member-commerce-badge--neutral" aria-hidden="true">
                  3
                </span>
                <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                  Class and court attendance is marked separately by your coach upon session commencement.
                </p>
              </div>
            </div>
          </MemberCommerceCard>
        </div>
      </div>
    </div>
  )
}
