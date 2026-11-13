import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../../api/client'
import { getErrorMessage } from '../../api/errors'
import { AuthShell } from './AuthShell'

export function ForgotPassword({ initialEmail, onBack }: { initialEmail: string; onBack: () => void }) {
  const [step, setStep] = useState<'email' | 'otp' | 'password' | 'done'>('email')
  const [email, setEmail] = useState(initialEmail)
  const [otp, setOtp] = useState('')
  const [authorization, setAuthorization] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [countdown, setCountdown] = useState(0)
  useEffect(() => {
    if (countdown <= 0) return
    const timer = window.setTimeout(() => setCountdown(countdown - 1), 1000)
    return () => window.clearTimeout(timer)
  }, [countdown])
  async function send() {
    setBusy(true); setError('')
    try {
      await api.post('/auth/forgot-password/request', { email })
      setStep('otp'); setCountdown(60)
    } catch (caught) { setError(getErrorMessage(caught)) }
    finally { setBusy(false) }
  }
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      if (step === 'email') await send()
      if (step === 'otp') {
        const result = await api.post<{ authorization: string }>('/auth/forgot-password/verify', { email, otp })
        setAuthorization(result.authorization); setStep('password')
      }
      if (step === 'password') {
        if (password !== confirm) throw new Error('Passwords do not match.')
        await api.post('/auth/forgot-password/reset', { authorization, newPassword: password, confirmPassword: confirm })
        setStep('done')
      }
    } catch (caught) { setError(getErrorMessage(caught)) }
    finally { setBusy(false) }
  }
  return <AuthShell className="auth-shell--login" title="Reset password" subtitle="Email ? OTP verification ? New password">
    {error && <p role="alert" className="notice notice--danger">{error}</p>}
    {step === 'done' ? <p role="status">Your password has been reset. You can log in now.</p> :
      <form className="auth-form" onSubmit={submit}>
        <fieldset className="auth-form__fields" disabled={busy}>
          {step === 'email' && <><label htmlFor="recovery-email">Email address</label><input id="recovery-email" type="email" required autoComplete="email" value={email} onChange={e => setEmail(e.target.value)} /><button type="submit">Send OTP</button></>}
          {step === 'otp' && <><p>Enter the six-digit code sent to your email. It expires in five minutes.</p><label htmlFor="recovery-otp">Verification code</label><input id="recovery-otp" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} required value={otp} onChange={e => setOtp(e.target.value)} /><button type="submit">Verify OTP</button><button type="button" disabled={countdown > 0 || busy} onClick={send}>Resend OTP {countdown > 0 ? `(${countdown}s)` : ''}</button></>}
          {step === 'password' && <><label htmlFor="recovery-password">New password</label><input id="recovery-password" type="password" required minLength={8} value={password} onChange={e => setPassword(e.target.value)} /><label htmlFor="recovery-confirm">Confirm new password</label><input id="recovery-confirm" type="password" required value={confirm} onChange={e => setConfirm(e.target.value)} /><button type="submit">Reset password</button></>}
        </fieldset>
      </form>}
    <button type="button" className="link-button" onClick={onBack}>Return to Login</button>
  </AuthShell>
}
