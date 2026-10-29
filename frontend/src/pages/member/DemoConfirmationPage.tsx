import { useCallback, useEffect, useLayoutEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { demoQrApi, type DemoSession } from '../../api/demoQrApi'
import { validateLogin } from '../../auth/loginValidation'
import { useAuth } from '../../auth/useAuth'
import { homePathFor } from '../../auth/roles'
import { Brand } from '../../components/Brand'
import { PaymentWorkspace } from '../../components/PaymentWorkspace'
import { DemoSessionSummary } from '../../components/qr/DemoSessionSummary'
import { Button, Card, Input, PasswordInput } from '../../components/ui'
import { ThemeToggle } from '../../theme/ThemeToggle'
import { qrPaymentDemoEnabled } from '../../config/env'
import '../../components/qr/demoSession.css'

import { confirmationToken } from '../../components/qr/confirmationLink'

export function DemoConfirmationPage() {
  const location = useLocation()
  const navigate = useNavigate()
  useLayoutEffect(() => {
    if (!qrPaymentDemoEnabled() && location.hash) navigate({ pathname: location.pathname, search: '', hash: '' }, { replace: true, state: null })
  }, [location.hash, location.pathname, navigate])
  if (!qrPaymentDemoEnabled()) return <DemoConfirmationDisabled />
  return <DemoConfirmationContent />
}

/** Reuse the approved Home/workspace brand composition for every public demo state. */
function DemoConfirmationLayout({ children }: { children: ReactNode }) {
  return <div className="home-page payment-area"><meta name="referrer" content="no-referrer" />
    <header className="home-header workspace-header demo-confirm-header">
      <div className="home-wrap home-header__bar">
        <Brand compact />
        <div className="home-account"><ThemeToggle className="home-theme" /></div>
      </div>
    </header>
    <main id="main-content" className="container demo-confirm-page">
      <PaymentWorkspace>{children}</PaymentWorkspace>
    </main>
  </div>
}

function DemoConfirmationDisabled() {
  const { status, user } = useAuth()
  const target = status === 'authenticated' ? homePathFor(user) : '/login'
  return <DemoConfirmationLayout>
    <h1>Page not found</h1>
    <p>The page you are looking for does not exist or has been moved.</p>
    <p><Link to={target === '/403' ? '/login' : target}>{status === 'authenticated' ? 'Back to my home page' : 'Go to log in'}</Link></p>
  </DemoConfirmationLayout>
}

function DemoConfirmationContent() {
  const location = useLocation()
  const navigate = useNavigate()
  const { status, role, user, reloadUser } = useAuth()
  const [capture, setCapture] = useState(() => ({ hash: location.hash, token: confirmationToken(location.hash), generation: 0 }))
  // Consume each explicit fragment navigation, including rescans in the same tab. Clearing
  // the address retains the token only in memory; another link gets a fresh owner summary.
  if (capture.hash !== location.hash) {
    setCapture(location.hash ? { hash: location.hash, token: confirmationToken(location.hash), generation: capture.generation + 1 }
      : { ...capture, hash: '' })
  }
  const token = capture.token
  useLayoutEffect(() => {
    if (location.hash) navigate({ pathname: location.pathname, search: '', hash: '' }, { replace: true, state: null })
  }, [location.hash, location.pathname, navigate])
  return <DemoConfirmationLayout>
        <h1>QR Payment (Simulation)</h1>
        <p className="notice notice--warning">DEMO ONLY - NO MONEY TRANSFER</p>
        <p>This is a time-limited simulation link, not a payment request. Opening it never confirms anything.</p>
        {!token ? <Card title="Demo link unavailable"><p role="alert">The token is missing or malformed. Rescan the current desktop demo link. Reloading clears a link for your privacy.</p></Card>
          : status === 'anonymous' ? <DemoOwnerLogin />
          : status === 'loading' ? <p role="status">Restoring your session...</p>
          : status === 'error' ? <Card title="Sign-in verification unavailable"><Button onClick={() => void reloadUser()}>Retry sign-in verification</Button></Card>
          : role !== 'MEMBER' ? <Card title="Owner sign-in required"><p role="alert">Only the member who owns this demo session can access it. Sign out and rescan with the owner account.</p></Card>
          : <DemoSessionControls key={`${user!.id}:${capture.generation}`} token={token} />}
        <p><Link to="/">Back to Home</Link></p>
  </DemoConfirmationLayout>
}

/** A different account gets a fresh component, so no previous owner's snapshot can render. */
function DemoSessionControls({ token }: { token: string }) {
  const [session, setSession] = useState<DemoSession | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(true)
  const [deadline, setDeadline] = useState(false)
  const sending = useRef(false)
  const generation = useRef(0)
  const summary = useRef<HTMLDivElement>(null)
  const reachedDeadline = useCallback(() => setDeadline(true), [])
  useEffect(() => {
    const current = ++generation.current
    void demoQrApi.capability().then((enabled) => {
      if (!enabled) throw new Error('Simulation unavailable')
      return demoQrApi.preview(token)
    }).then((result) => {
      if (generation.current === current) setSession(result)
    }).catch(() => {
      if (generation.current === current) setError('Demo link unavailable. It may be invalid, revoked, legacy, or simulation may be disabled. Start a new session when available. No payment was changed.')
    }).finally(() => { if (generation.current === current) setBusy(false) })
    return () => { generation.current += 1 }
  }, [token])

  async function action(kind: 'confirm' | 'cancel' | 'refresh') {
    if (sending.current) return
    sending.current = true
    setBusy(true); setError('')
    const current = generation.current
    try {
      const result = kind === 'confirm' ? await demoQrApi.confirm(token)
        : kind === 'cancel' && session ? await demoQrApi.cancel(session.id) : await demoQrApi.preview(token)
      if (current === generation.current) { setSession(result); setDeadline(false); summary.current?.focus() }
    } catch {
      if (current === generation.current) {
        setError('Could not complete the demo action. Refresh the demo status before retrying. The real payment remains unchanged.')
        // A timeout or conflict is not success. Hide stale pending controls until the owner refreshes.
        setSession(null)
      }
    } finally {
      sending.current = false
      if (current === generation.current) setBusy(false)
    }
  }
  return <Card title="Owner-authorized demo session">
    <div ref={summary} tabIndex={-1} role="region" aria-label="Demo session result">{session && <DemoSessionSummary session={session} onDeadline={reachedDeadline} />}</div>
    {busy && <p role="status">Loading demo session...</p>}
    {deadline && session?.state === 'PENDING' && <p>The displayed deadline has passed. Refresh the server status.</p>}
    {error && <p role="alert" className="notice notice--danger">{error}</p>}
    <div className="demo-session-actions">
      {session?.state === 'PENDING' && !deadline && <>
        <Button disabled={busy} onClick={() => void action('confirm')}>Record confirmation intent</Button>
        <Button variant="secondary" disabled={busy} onClick={() => void action('cancel')}>Cancel Simulation Session</Button>
      </>}
      <Button variant="secondary" disabled={busy} onClick={() => void action('refresh')}>Refresh Simulation Status</Button>
    </div>
  </Card>
}

/** Keep the token only in the parent component during login; no redirect or token storage. */
function DemoOwnerLogin() {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  const sending = useRef(false)
  const form = useRef<HTMLFormElement>(null)
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (sending.current) return
    const invalid = validateLogin(email, password)
    setFields(invalid)
    if (Object.keys(invalid).length) { requestAnimationFrame(() => form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()); return }
    sending.current = true; setBusy(true); setError('')
    try { await login({ email, password }, { remember: false }) }
    catch { setError('Could not sign in as the payment owner. Check your credentials and connection.'); setPassword('') }
    finally { sending.current = false; setBusy(false) }
  }
  return <Card title="Sign in as the payment owner">
    <p>No demo details are disclosed before owner authorization. Your link stays only in this page memory while you sign in.</p>
    <form className="demo-confirm-form" ref={form} onSubmit={(event) => void submit(event)} noValidate>
      <Input label="Email" type="email" autoComplete="username" value={email} required error={fields.email} disabled={busy} onChange={(event) => setEmail(event.target.value)} />
      <PasswordInput label="Password" autoComplete="current-password" value={password} required error={fields.password} disabled={busy} onChange={(event) => setPassword(event.target.value)} />
      {error && <p role="alert" className="notice notice--danger">{error}</p>}
      <Button type="submit" loading={busy}>Sign in to review simulation</Button>
    </form>
  </Card>
}
