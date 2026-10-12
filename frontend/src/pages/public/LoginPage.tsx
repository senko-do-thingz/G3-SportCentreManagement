import { useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../../api/errors'
import type { LoginRedirectState } from '../../auth/RequireAuth'
import { validateLogin } from '../../auth/loginValidation'
import { homePathFor, postLoginPath } from '../../auth/roles'
import { useAuth } from '../../auth/useAuth'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { Input } from '../../components/ui/Input'
import { PasswordInput } from '../../components/ui/PasswordInput'
import { focusFirstInvalid } from '../../utils/focus'
import { AuthShell } from './AuthShell'

/** Shown for HTTP 403 from POST /auth/login (DisabledException or LockedException on the backend). */
export const ACCOUNT_DISABLED_MESSAGE =
  'This account is locked or inactive. Please contact the front desk to reactivate it.'

/** Shown for HTTP 401 from POST /auth/login. Same meaning as the backend message, as a sentence. */
export const INVALID_CREDENTIALS_MESSAGE = 'Invalid email or password. Please try again.'

export const FORGOT_PASSWORD_MESSAGE =
  'Online password reset is not available yet. Please ask the front desk to reset your password.'

/** Seeded by migration V4. Shown only in development builds, like the sample account box in Figma. */
const DEMO_ACCOUNT_HINT = 'Sample account: manager@sportify.com - password'

/** State passed by the sign up page when the account exists but the automatic sign in failed. */
export interface LoginPrefillState {
  email?: string
  notice?: string
}

function loginErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return INVALID_CREDENTIALS_MESSAGE
    }
    if (error.status === 403) {
      return ACCOUNT_DISABLED_MESSAGE
    }
  }
  return getErrorMessage(error)
}

/** Log in screen (Figma F1-05). */
export function LoginPage() {
  const { status, user, login, sessionExpired } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const locationState = location.state as (LoginRedirectState & LoginPrefillState) | null
  const requestedPath = locationState?.from ?? null

  const [email, setEmail] = useState(locationState?.email ?? '')
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [infoMessage, setInfoMessage] = useState<string | null>(locationState?.notice ?? null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const formRef = useRef<HTMLFormElement>(null)
  // Guards against a second submit before React has re-rendered the disabled button.
  const sendingRef = useRef(false)

  const clearFieldError = (field: string) => {
    setFieldErrors((current) => {
      if (!current[field]) {
        return current
      }
      const next = { ...current }
      delete next[field]
      return next
    })
  }

  if (status === 'authenticated' && user && !submitting) {
    return <Navigate to={homePathFor(user)} replace />
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (sendingRef.current) {
      return
    }
    setFormError(null)
    const clientErrors = validateLogin(email, password)
    setFieldErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) {
      focusFirstInvalid(formRef.current)
      return
    }
    sendingRef.current = true
    setSubmitting(true)
    try {
      const signedIn = await login({ email, password }, { remember })
      navigate(postLoginPath(signedIn, requestedPath), { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.hasFieldErrors) {
        // Backend validation messages take precedence over the client side checks.
        setFieldErrors(error.fieldErrors)
        focusFirstInvalid(formRef.current)
      } else {
        setFormError(loginErrorMessage(error))
      }
      sendingRef.current = false
      setSubmitting(false)
    }
  }

  return (
    <AuthShell title="Welcome back" subtitle="Log in to manage your training.">
      {import.meta.env.DEV && <p className="notice notice--info auth-sample">{DEMO_ACCOUNT_HINT}</p>}

      {sessionExpired && !formError && (
        <p className="notice notice--warning" role="status">
          Your session has expired. Please log in again.
        </p>
      )}
      {infoMessage && !formError && (
        <p className="notice notice--info" role="status">
          {infoMessage}
        </p>
      )}
      {formError && (
        <p className="notice notice--danger" role="alert">
          {formError}
        </p>
      )}

      <form ref={formRef} className="auth-form" onSubmit={handleSubmit} noValidate aria-busy={submitting}>
        <fieldset className="auth-form__fields" disabled={submitting}>
          <Input
            label="Email address"
            type="email"
            name="email"
            autoComplete="email"
            placeholder="you@example.com"
            value={email}
            onChange={(event) => {
              setEmail(event.target.value)
              clearFieldError('email')
            }}
            error={fieldErrors.email}
            required
          />
          <PasswordInput
            label="Password"
            name="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => {
              setPassword(event.target.value)
              clearFieldError('password')
            }}
            error={fieldErrors.password}
            required
          />
          <div className="auth-form__options">
            <Checkbox
              label="Remember me"
              name="remember"
              checked={remember}
              onChange={(event) => setRemember(event.target.checked)}
            />
            <button
              type="button"
              className="link-button"
              onClick={() => {
                setFormError(null)
                setInfoMessage(FORGOT_PASSWORD_MESSAGE)
              }}
            >
              Forgot password?
            </button>
          </div>
          <Button type="submit" size="lg" fullWidth loading={submitting}>
            Log in
          </Button>
        </fieldset>
      </form>

      <p className="auth-panel__footer">
        <span>New to Sportify?</span>
        <Link to="/register">Create an account</Link>
      </p>
    </AuthShell>
  )
}
