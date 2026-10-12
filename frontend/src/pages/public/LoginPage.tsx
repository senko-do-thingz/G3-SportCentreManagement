import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../../api/errors'
import type { LoginRedirectState } from '../../auth/RequireAuth'
import { homePathFor, postLoginPath } from '../../auth/roles'
import { useAuth } from '../../auth/useAuth'
import { Brand } from '../../components/Brand'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { validateLogin } from '../../auth/loginValidation'

/**
 * Log in screen (Figma F1-05). FE-00 delivers a working form so every role can reach its layout;
 * FE-01 finishes the screen (sign up, forgot password, final copy).
 */
export function LoginPage() {
  const { status, user, login, sessionExpired } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const requestedPath = (location.state as LoginRedirectState | null)?.from ?? null

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

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
    setFormError(null)
    const clientErrors = validateLogin(email, password)
    setFieldErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) {
      return
    }
    setSubmitting(true)
    try {
      const signedIn = await login({ email, password })
      navigate(postLoginPath(signedIn, requestedPath), { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.hasFieldErrors) {
        // Backend validation messages take precedence over the client side checks.
        setFieldErrors(error.fieldErrors)
      } else {
        setFormError(getErrorMessage(error))
      }
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-shell">
      <section className="auth-hero" aria-hidden="true">
        <p className="auth-hero__eyebrow">One center. More possibilities.</p>
        <p className="auth-hero__title">
          More sport.
          <br />
          More you.
        </p>
        <p className="auth-hero__text">Find your sport, train your way, and make every session count.</p>
      </section>

      <section className="auth-panel">
        <Brand />
        <div className="auth-panel__heading">
          <h1>Welcome back</h1>
          <p>Log in to manage your training.</p>
        </div>

        {sessionExpired && !formError && (
          <p className="notice notice--warning" role="status">
            Your session has expired. Please log in again.
          </p>
        )}
        {formError && (
          <p className="notice notice--danger" role="alert">
            {formError}
          </p>
        )}

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
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
          <Input
            label="Password"
            type="password"
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
          <Button type="submit" size="lg" fullWidth loading={submitting}>
            Log in
          </Button>
        </form>

        <p className="auth-panel__footer">
          <span>New to Sportify?</span>
          <Link to="/register">Create an account</Link>
        </p>
      </section>
    </div>
  )
}
