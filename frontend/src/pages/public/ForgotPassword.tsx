import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../../api/client'
import { getErrorMessage } from '../../api/errors'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { PasswordInput } from '../../components/ui/PasswordInput'
import { AuthShell } from './AuthShell'

type Step = 'email' | 'otp' | 'password' | 'done'

const STEP_TITLES: Record<Step, string> = {
  email: 'Step 1 of 3: Enter your email',
  otp: 'Step 2 of 3: Verify the code',
  password: 'Step 3 of 3: Choose a new password',
  done: 'Password reset complete',
}

/**
 * Forgot Password for every role: email, a six-digit code sent by Gmail (valid five minutes, five attempts, resend
 * after sixty seconds), then a new password. The server enforces every rule; this screen only guides the steps.
 */
export function ForgotPassword({ initialEmail, onBack }: { initialEmail: string; onBack: () => void }) {
  const [step, setStep] = useState<Step>('email')
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

  async function requestCode() {
    await api.post('/auth/forgot-password/request', { email: email.trim() }, { auth: false })
    setStep('otp'); setOtp(''); setCountdown(60)
  }

  async function resend() {
    setBusy(true); setError('')
    try { await requestCode() } catch (caught) { setError(getErrorMessage(caught)) } finally { setBusy(false) }
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      if (step === 'email') await requestCode()
      if (step === 'otp') {
        const result = await api.post<{ authorization: string }>('/auth/forgot-password/verify', { email: email.trim(), otp }, { auth: false })
        setAuthorization(result.authorization); setStep('password')
      }
      if (step === 'password') {
        if (password !== confirm) throw new Error('Passwords do not match.')
        await api.post('/auth/forgot-password/reset', { authorization, newPassword: password, confirmPassword: confirm }, { auth: false })
        setAuthorization(''); setPassword(''); setConfirm(''); setStep('done')
      }
    } catch (caught) { setError(getErrorMessage(caught)) }
    finally { setBusy(false) }
  }

  return <AuthShell className="auth-shell--login" title="Reset password" subtitle={STEP_TITLES[step]}>
    {error && <p role="alert" className="notice notice--danger">{error}</p>}
    {step === 'done'
      ? <p role="status" className="notice notice--success">Your password has been reset. You can log in with your new password now.</p>
      : <form className="auth-form" onSubmit={submit} aria-busy={busy}>
        <fieldset className="auth-form__fields" disabled={busy}>
          {step === 'email' && <>
            <Input label="Email address" type="email" name="email" autoComplete="email" required value={email}
              hint="We will email you a six-digit verification code." onChange={e => setEmail(e.target.value)} />
            <Button type="submit" size="lg" fullWidth loading={busy}>Send code</Button>
          </>}
          {step === 'otp' && <>
            <p role="status">If an account exists for {email.trim()}, a six-digit code is on its way. It expires in five minutes.</p>
            <Input label="Verification code" name="otp" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6}
              required value={otp} onChange={e => setOtp(e.target.value.replace(/\D/g, ''))} />
            <Button type="submit" size="lg" fullWidth loading={busy}>Verify code</Button>
            <Button type="button" variant="ghost" disabled={countdown > 0 || busy} onClick={() => void resend()}>
              {countdown > 0 ? `Resend code in ${countdown}s` : 'Resend code'}
            </Button>
          </>}
          {step === 'password' && <>
            <PasswordInput label="New password" name="newPassword" autoComplete="new-password" required minLength={8}
              hint="At least 8 characters with a letter and a number." value={password} onChange={e => setPassword(e.target.value)} />
            <PasswordInput label="Confirm new password" name="confirmPassword" autoComplete="new-password" required
              value={confirm} onChange={e => setConfirm(e.target.value)} />
            <Button type="submit" size="lg" fullWidth loading={busy}>Reset password</Button>
          </>}
        </fieldset>
      </form>}
    <button type="button" className="link-button" onClick={onBack}>Return to Login</button>
  </AuthShell>
}
