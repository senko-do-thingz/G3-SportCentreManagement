import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, getErrorMessage, splitFieldErrors } from '../../api/errors'
import type { LoginRedirectState } from '../../auth/RequireAuth'
import { LoginProfileError } from '../../auth/authContext'
import { validateLogin } from '../../auth/loginValidation'
import { postLoginPath } from '../../auth/roles'
import { useAuth } from '../../auth/useAuth'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { Input } from '../../components/ui/Input'
import { PasswordInput } from '../../components/ui/PasswordInput'
import { managerPreviewEnabled, sampleAccount } from '../../config/env'
import { ManagerPreviewLogin } from '../../components/ManagerPreviewLogin'
import { PageLoader } from '../../components/ui/Spinner'
import { sampleAccount } from '../../config/env'
import { useFocusFirstInvalid } from '../../utils/useFocusFirstInvalid'
import { AuthShell } from './AuthShell'
import { SessionErrorPage } from './SessionErrorPage'

/** Shown for HTTP 403 from POST /auth/login (DisabledException or LockedException on the backend). */
export const ACCOUNT_DISABLED_MESSAGE =
  'This account is locked or inactive. Please contact the front desk to reactivate it.'

/** Shown for HTTP 401 from POST /auth/login. Same meaning as the backend message, as a sentence. */
export const INVALID_CREDENTIALS_MESSAGE = 'Invalid email or password. Please try again.'

export const FORGOT_PASSWORD_MESSAGE =
  'Online password reset is not available yet. Please ask the front desk to reset your password.'

/** Shown when another tab of this browser logged in or out and this tab lost its session. */
export const SIGNED_OUT_ELSEWHERE_MESSAGE =
  'You were signed out because another account signed in or out in this browser.'

/** Inputs rendered by this form; backend validation keys outside this list go above the form. */
const LOGIN_FIELDS = ['email', 'password'] as const

/** State passed by the sign up page when the account exists but the automatic sign in failed. */
export interface LoginPrefillState {
  email?: string
  notice?: string
}

type LoginLocationState = (LoginRedirectState & LoginPrefillState) | null

function loginErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return INVALID_CREDENTIALS_MESSAGE
    }
    if (error.status === 403) {
      return ACCOUNT_DISABLED_MESSAGE
    }
    if (error.isNetworkError) {
      return 'Cannot reach the server. Check your connection and API URL; a browser CORS restriction may also block the request.'
    }
    if (error.status >= 500) {
      return 'The login service is unavailable. Please try again later.'
    }
  }
  return error instanceof ApiError ? getErrorMessage(error) : 'We could not sign you in. Please try again.'
}

/** Log in screen (Figma F1-05). */
export function LoginPage() {
  const { status, user, login, sessionExpired, signedOutElsewhere } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  // History state is read once and kept here; the effect below removes the prefill values from
  // the history entry so a reload does not show the notice again.
  const [initialState] = useState(() => location.state as LoginLocationState)
  const requestedPath = initialState?.from ?? null

  const [email, setEmail] = useState(initialState?.email ?? '')
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [infoMessage, setInfoMessage] = useState<string | null>(initialState?.notice ?? null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const formRef = useRef<HTMLFormElement>(null)
  const focusFirstInvalid = useFocusFirstInvalid(formRef)
  // Guards against a second submit before React has re-rendered the disabled button.
  const sendingRef = useRef(false)
  const sample = sampleAccount()

  useEffect(() => {
    if (initialState?.email !== undefined || initialState?.notice !== undefined) {
      // Keep only the page that asked for the login, which is still needed after the redirect.
      const remaining: LoginRedirectState | null = initialState.from ? { from: initialState.from } : null
      // Same address including the query string and hash; only the history state changes.
      navigate(
        { pathname: location.pathname, search: location.search, hash: location.hash },
        { replace: true, state: remaining },
      )
    }
    // initialState is fixed for the life of the page and the address stays the same, so this runs once.
  }, [initialState, location.pathname, location.search, location.hash, navigate])

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
    return <Navigate to={postLoginPath(user, requestedPath)} replace />
  }
  if (status === 'loading') {
    return <PageLoader label="Restoring your session" />
  }
  if (status === 'error' && !submitting) {
    return <SessionErrorPage />
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
      focusFirstInvalid()
      return
    }
    sendingRef.current = true
    setSubmitting(true)
    try {
      const signedIn = await login({ email, password }, { remember })
      navigate(postLoginPath(signedIn, requestedPath), { replace: true })
    } catch (error) {
      if (error instanceof LoginProfileError) {
        setFormError(error.message)
      } else if (error instanceof ApiError && error.hasFieldErrors) {
        // Backend validation messages take precedence over the client side checks. Keys without
        // an input on this form are shown above it instead of being lost.
        const { fields, formMessage } = splitFieldErrors(error.fieldErrors, LOGIN_FIELDS)
        setFieldErrors(fields)
        setFormError(formMessage)
        focusFirstInvalid()
      } else {
        setFormError(loginErrorMessage(error))
      }
      sendingRef.current = false
      setSubmitting(false)
    }
  }

  return (
    <AuthShell title="Welcome back" subtitle="Log in to manage your training.">
      {import.meta.env.DEV && managerPreviewEnabled() && <ManagerPreviewLogin />}
      {sample && (
        <p className="notice notice--info auth-sample">
          Sample account: {sample.email} - {sample.password}
        </p>
      )}

      {sessionExpired && !formError && (
        <p className="notice notice--warning" role="status">
          Your session has expired. Please log in again.
        </p>
      )}
      {signedOutElsewhere && !sessionExpired && !formError && (
        <p className="notice notice--warning" role="status">
          {SIGNED_OUT_ELSEWHERE_MESSAGE}
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
