import { useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../../api/errors'
import { AccountCreatedError } from '../../auth/authContext'
import { checkPassword, passwordStrength } from '../../auth/passwordRules'
import {
  EMAIL_MAX_LENGTH,
  EMPTY_REGISTER_FORM,
  FULL_NAME_MAX_LENGTH,
  toRegisterRequest,
  validateRegister,
  type RegisterForm,
} from '../../auth/registerValidation'
import { homePathFor } from '../../auth/roles'
import { useAuth } from '../../auth/useAuth'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { Input } from '../../components/ui/Input'
import { PasswordInput } from '../../components/ui/PasswordInput'
import { focusFirstInvalid } from '../../utils/focus'
import { AuthShell } from './AuthShell'
import type { LoginPrefillState } from './LoginPage'

const STRENGTH_LABELS = { empty: '', weak: 'Weak', fair: 'Fair', strong: 'Strong' } as const

/** Field names of the form. Backend validation keys use the same names. */
type FieldName = keyof RegisterForm

/**
 * Maps a failed POST /auth/register to messages: 409 (email already registered) goes under the
 * email field, 400 validation details go under their fields, anything else is shown above the form.
 */
function registerErrors(error: unknown): { fields: Record<string, string>; form: string | null } {
  if (error instanceof ApiError) {
    if (error.status === 409) {
      return { fields: { email: error.message }, form: null }
    }
    if (error.hasFieldErrors) {
      return { fields: error.fieldErrors, form: null }
    }
  }
  return { fields: {}, form: getErrorMessage(error) }
}

function PasswordRules({ password }: { password: string }) {
  const rules = checkPassword(password)
  const strength = passwordStrength(password)
  return (
    <div className="password-rules" id="password-rules">
      {strength !== 'empty' && (
        <p className={`password-rules__strength password-rules__strength--${strength}`} aria-live="polite">
          Password strength: {STRENGTH_LABELS[strength]}
        </p>
      )}
      <ul className="password-rules__list">
        {rules.map((rule) => (
          <li
            key={rule.id}
            className={`password-rules__item${rule.met ? ' password-rules__item--met' : ''}`}
            data-rule={rule.id}
            data-met={rule.met}
          >
            <span>{rule.label}</span>
            <span className="visually-hidden">{rule.met ? ' - met' : ' - not met'}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Sign up screen (Figma F1-06). A new account is always a MEMBER; it opens the member dashboard. */
export function RegisterPage() {
  const { status, user, register } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState<RegisterForm>(EMPTY_REGISTER_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const formRef = useRef<HTMLFormElement>(null)
  const sendingRef = useRef(false)

  if (status === 'authenticated' && user && !submitting) {
    return <Navigate to={homePathFor(user)} replace />
  }

  const update = <K extends FieldName>(field: K, value: RegisterForm[K]) => {
    setForm((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => {
      if (!current[field]) {
        return current
      }
      const next = { ...current }
      delete next[field]
      return next
    })
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (sendingRef.current) {
      return
    }
    setFormError(null)
    const clientErrors = validateRegister(form)
    setFieldErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) {
      focusFirstInvalid(formRef.current)
      return
    }
    sendingRef.current = true
    setSubmitting(true)
    try {
      const member = await register(toRegisterRequest(form))
      navigate(homePathFor(member), { replace: true })
    } catch (error) {
      if (error instanceof AccountCreatedError) {
        const state: LoginPrefillState = { email: form.email.trim(), notice: error.message }
        navigate('/login', { replace: true, state })
        return
      }
      const { fields, form: message } = registerErrors(error)
      setFieldErrors(fields)
      setFormError(message)
      if (Object.keys(fields).length > 0) {
        focusFirstInvalid(formRef.current)
      }
      sendingRef.current = false
      setSubmitting(false)
    }
  }

  const passwordsMatch = form.confirmPassword.length > 0 && form.confirmPassword === form.password

  return (
    <AuthShell title="Create your account" subtitle="Your journey starts here.">
      {formError && (
        <p className="notice notice--danger" role="alert">
          {formError}
        </p>
      )}

      <form ref={formRef} className="auth-form" onSubmit={handleSubmit} noValidate aria-busy={submitting}>
        <fieldset className="auth-form__fields" disabled={submitting}>
          <Input
            label="Full name"
            name="fullName"
            autoComplete="name"
            placeholder="Taylor Nguyen"
            maxLength={FULL_NAME_MAX_LENGTH}
            value={form.fullName}
            onChange={(event) => update('fullName', event.target.value)}
            error={fieldErrors.fullName}
            required
          />
          <Input
            label="Phone number"
            type="tel"
            name="phone"
            autoComplete="tel"
            inputMode="tel"
            placeholder="0901234567"
            maxLength={24}
            value={form.phone}
            onChange={(event) => update('phone', event.target.value)}
            error={fieldErrors.phone}
            required
          />
          <Input
            label="Email address"
            type="email"
            name="email"
            autoComplete="email"
            placeholder="you@example.com"
            maxLength={EMAIL_MAX_LENGTH}
            value={form.email}
            onChange={(event) => update('email', event.target.value)}
            error={fieldErrors.email}
            required
          />
          <div className="field-group">
            <PasswordInput
              label="Password"
              name="password"
              autoComplete="new-password"
              value={form.password}
              onChange={(event) => update('password', event.target.value)}
              error={fieldErrors.password}
              aria-describedby="password-rules"
              required
            />
            <PasswordRules password={form.password} />
          </div>
          <PasswordInput
            label="Confirm password"
            name="confirmPassword"
            autoComplete="new-password"
            value={form.confirmPassword}
            onChange={(event) => update('confirmPassword', event.target.value)}
            error={fieldErrors.confirmPassword}
            hint={passwordsMatch ? 'Passwords match' : undefined}
            required
          />
          <Checkbox
            name="acceptTerms"
            label="I agree to the Sportify Center terms of use and privacy policy"
            checked={form.acceptTerms}
            onChange={(event) => update('acceptTerms', event.target.checked)}
            error={fieldErrors.acceptTerms}
          />
          <Button type="submit" size="lg" fullWidth loading={submitting}>
            Create account
          </Button>
        </fieldset>
      </form>

      <p className="auth-panel__footer">
        <span>Already have an account?</span>
        <Link to="/login">Log in</Link>
      </p>
    </AuthShell>
  )
}
