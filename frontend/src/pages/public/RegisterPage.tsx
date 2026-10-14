import { useId, useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError, getErrorMessage, splitFieldErrors } from '../../api/errors'
import { AccountCreatedError } from '../../auth/authContext'
import {
  applyFieldChange,
  EMAIL_MAX_LENGTH,
  EMAIL_TAKEN_MESSAGE,
  EMPTY_REGISTER_FORM,
  FULL_NAME_MAX_LENGTH,
  REGISTER_FIELDS,
  toRegisterRequest,
  validateRegister,
  type RegisterForm,
  type RegisterFormState,
} from '../../auth/registerValidation'
import { homePathFor } from '../../auth/roles'
import { useAuth } from '../../auth/useAuth'
import { PasswordRules } from '../../components/PasswordRules'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { Input } from '../../components/ui/Input'
import { PasswordInput } from '../../components/ui/PasswordInput'
import { useFocusFirstInvalid } from '../../utils/useFocusFirstInvalid'
import { AuthShell } from './AuthShell'
import type { LoginPrefillState } from './LoginPage'

/** Field names of the form. Backend validation keys use the same names. */
type FieldName = keyof RegisterForm

/**
 * Maps a failed POST /auth/register to messages:
 * - 409 "Email is already registered" goes under the Email field;
 * - any other 409 (for example a database conflict) goes above the form;
 * - 400 validation details go under their field, and details for a key that has no input on this
 *   form go above it, so no message is lost;
 * - anything else goes above the form.
 */
function registerErrors(error: unknown): { fields: Record<string, string>; form: string | null } {
  if (error instanceof ApiError) {
    if (error.status === 409) {
      return error.message === EMAIL_TAKEN_MESSAGE
        ? { fields: { email: error.message }, form: null }
        : { fields: {}, form: error.message }
    }
    if (error.hasFieldErrors) {
      const { fields, formMessage } = splitFieldErrors(error.fieldErrors, REGISTER_FIELDS)
      return { fields, form: formMessage }
    }
  }
  return { fields: {}, form: getErrorMessage(error) }
}

/** Sign up screen (Figma F1-06). A new account is always a MEMBER; it opens the member dashboard. */
export function RegisterPage() {
  const { status, user, register } = useAuth()
  const navigate = useNavigate()
  const rulesId = useId()

  const [{ form, fieldErrors }, setFormState] = useState<RegisterFormState>({
    form: EMPTY_REGISTER_FORM,
    fieldErrors: {},
  })
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const setFieldErrors = (errors: Record<string, string>) =>
    setFormState((current) => ({ ...current, fieldErrors: errors }))
  const formRef = useRef<HTMLFormElement>(null)
  const focusFirstInvalid = useFocusFirstInvalid(formRef)
  const sendingRef = useRef(false)

  if (status === 'authenticated' && user && !submitting) {
    return <Navigate to={homePathFor(user)} replace />
  }

  // Functional update: every edit starts from the latest form and errors, never from this render.
  const update = <K extends FieldName>(field: K, value: RegisterForm[K]) => {
    setFormState((current) => applyFieldChange(current, field, value))
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
      focusFirstInvalid()
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
        focusFirstInvalid()
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
              aria-describedby={rulesId}
              required
            />
            <PasswordRules id={rulesId} password={form.password} />
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
