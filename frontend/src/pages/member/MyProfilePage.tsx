import { useCallback, useId, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, getErrorMessage, splitFieldErrors } from '../../api/errors'
import { memberApi } from '../../api/memberApi'
import type { UserProfile } from '../../api/types'
import { ROLE_LABELS, roleOf } from '../../auth/roles'
import { useAuth } from '../../auth/useAuth'
import { PasswordRules } from '../../components/PasswordRules'
import { Button, Card, ErrorState, Input, PageHeader, PasswordInput, Select, useToast } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { initials } from '../../utils/format'
import { useFocusFirstInvalid } from '../../utils/useFocusFirstInvalid'
import { MEMBER_PATHS, toIsoDate } from './dashboardModel'
import {
  ADDRESS_MAX_LENGTH,
  CURRENT_PASSWORD_REJECTED_MESSAGE,
  EMPTY_PASSWORD_FORM,
  FULL_NAME_MAX_LENGTH,
  GOAL_OPTIONS,
  latestDateOfBirth,
  LEVEL_OPTIONS,
  MIN_DATE_OF_BIRTH,
  PASSWORD_CHANGED_MESSAGE,
  PASSWORD_FIELDS,
  PHONE_INPUT_MAX_LENGTH,
  PROFILE_FIELDS,
  PROFILE_SAVED_MESSAGE,
  profileFormFrom,
  toChangePasswordRequest,
  toUpdateProfileRequest,
  validatePasswordChange,
  validateProfile,
  type PasswordForm,
  type ProfileForm,
} from './profileValidation'
import { useMemberResource } from './useMemberResource'

function loadProfile(signal: AbortSignal): Promise<UserProfile> {
  return memberApi.me({ signal })
}

/** Result of a failed request, split between field messages and one message above the form. */
interface FormFailure {
  fields: Record<string, string>
  form: string | null
}

function profileFailure(error: unknown): FormFailure {
  if (error instanceof ApiError && error.hasFieldErrors) {
    const { fields, formMessage } = splitFieldErrors(error.fieldErrors, PROFILE_FIELDS)
    return { fields, form: formMessage }
  }
  return { fields: {}, form: getErrorMessage(error) }
}

/**
 * Decides where a failed PUT /users/me/password is shown, from the HTTP status and the backend
 * field keys only, never from the message text:
 * - 401: under Current password. The request uses authFailure 'refresh-then-throw', so the client
 *   has already refreshed the token and retried once: this 401 came with a fresh token, and the
 *   session is kept;
 * - 400 with details: each key under its field (currentPassword, newPassword, confirmPassword),
 *   unknown keys above the form;
 * - anything else, including a 400 without details: above the form.
 */
function passwordFailure(error: unknown): FormFailure {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return { fields: { currentPassword: CURRENT_PASSWORD_REJECTED_MESSAGE }, form: null }
    }
    if (error.hasFieldErrors) {
      const { fields, formMessage } = splitFieldErrors(error.fieldErrors, PASSWORD_FIELDS)
      return { fields, form: formMessage }
    }
  }
  return { fields: {}, form: getErrorMessage(error) }
}

/**
 * My Profile (Figma F1-08): view and edit the personal details (PUT /users/me) and change the
 * password (PUT /users/me/password). Both forms show a success message and the backend errors.
 */
export function MyProfilePage() {
  const [profile, reload] = useMemberResource(loadProfile)

  return (
    <div className="page">
      <PageHeader
        eyebrow="Your sport, your way"
        title="My Profile"
        description="Update your personal details, training preferences and password."
      />
      {profile.status === 'loading' && <Spinner label="Loading your profile" />}
      {profile.status === 'error' && (
        <ErrorState title="We could not load your profile" error={profile.error} onRetry={reload} />
      )}
      {profile.status === 'ready' && <ProfileContent key={profile.data.id} initial={profile.data} />}
    </div>
  )
}

function ProfileContent({ initial }: { initial: UserProfile }) {
  const [saved, setSaved] = useState<UserProfile>(initial)
  const role = roleOf(saved)
  return (
    <div className="profile-layout">
      <div className="profile-layout__main">
        <PersonalInfoCard initial={initial} onSaved={setSaved} />
        <ChangePasswordCard />
      </div>
      <aside className="profile-layout__side" aria-label="Profile summary">
        <Card title="Profile summary">
          <div className="profile-summary">
            <span className="avatar avatar--lg" aria-hidden="true">
              {initials(saved.fullName)}
            </span>
            <p className="profile-summary__name">{saved.fullName}</p>
            <p className="profile-summary__role">{role ? ROLE_LABELS[role] : saved.role}</p>
            {saved.memberCode && <p className="profile-summary__code">Member code {saved.memberCode}</p>}
            <p className="profile-summary__email">{saved.email}</p>
          </div>
          <p className="notice notice--info profile-summary__note">
            Your goal and level help us suggest relevant classes. They do not register you for a sport.
          </p>
          <Link className={buttonClassName('secondary', 'sm')} to={MEMBER_PATHS.packages}>
            Explore packages
          </Link>
        </Card>
      </aside>
    </div>
  )
}

function PersonalInfoCard({ initial, onSaved }: { initial: UserProfile; onSaved: (user: UserProfile) => void }) {
  const { reloadUser } = useAuth()
  const toast = useToast()
  const [form, setForm] = useState<ProfileForm>(() => profileFormFrom(initial))
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const sendingRef = useRef(false)
  const formRef = useRef<HTMLFormElement>(null)
  const focusFirstInvalid = useFocusFirstInvalid(formRef)
  const [today] = useState(() => toIsoDate(new Date()))

  const update = useCallback(<K extends keyof ProfileForm>(field: K, value: ProfileForm[K]) => {
    setForm((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => {
      if (!(field in current)) {
        return current
      }
      const next = { ...current }
      delete next[field]
      return next
    })
    setSuccess(null)
  }, [])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (sendingRef.current) {
      return
    }
    setFormError(null)
    setSuccess(null)
    const errors = validateProfile(form, today)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      focusFirstInvalid()
      return
    }
    sendingRef.current = true
    setSaving(true)
    try {
      const request = toUpdateProfileRequest(form)
      const response = await memberApi.updateProfile(request)
      // The backend returns the saved profile; when it returns no body the sent values are kept.
      const savedUser: UserProfile = response && typeof response === 'object' ? response : { ...initial, ...request }
      setForm(profileFormFrom(savedUser))
      onSaved(savedUser)
      setSuccess(PROFILE_SAVED_MESSAGE)
      toast.success('Profile saved', PROFILE_SAVED_MESSAGE)
      // Refreshes the name in the header. A failure here does not undo the save.
      void reloadUser()
    } catch (error) {
      const failure = profileFailure(error)
      setFieldErrors(failure.fields)
      setFormError(failure.form)
      if (Object.keys(failure.fields).length > 0) {
        focusFirstInvalid()
      }
    } finally {
      sendingRef.current = false
      setSaving(false)
    }
  }

  return (
    <Card title="Personal information">
      {success && (
        // Not a live region: the success toast is the single announcement for screen readers.
        <p className="notice notice--success">{success}</p>
      )}
      {formError && (
        <p className="notice notice--danger" role="alert">
          {formError}
        </p>
      )}
      <form ref={formRef} onSubmit={handleSubmit} noValidate aria-busy={saving} aria-label="Personal information">
        <fieldset className="profile-form" disabled={saving}>
          <div className="profile-form__grid">
            <Input
              label="Full name"
              name="fullName"
              autoComplete="name"
              maxLength={FULL_NAME_MAX_LENGTH}
              value={form.fullName}
              onChange={(event) => update('fullName', event.target.value)}
              error={fieldErrors.fullName}
              required
            />
            <Input
              label="Date of birth"
              type="date"
              name="dateOfBirth"
              autoComplete="bday"
              min={MIN_DATE_OF_BIRTH}
              max={latestDateOfBirth(today)}
              value={form.dateOfBirth}
              onChange={(event) => update('dateOfBirth', event.target.value)}
              error={fieldErrors.dateOfBirth}
            />
            <Input
              label="Email address"
              type="email"
              name="email"
              value={initial.email}
              readOnly
              hint="Your login email. Ask the front desk to change it."
            />
            <Input
              label="Phone number"
              type="tel"
              name="phone"
              autoComplete="tel"
              inputMode="tel"
              maxLength={PHONE_INPUT_MAX_LENGTH}
              value={form.phone}
              onChange={(event) => update('phone', event.target.value)}
              error={fieldErrors.phone}
            />
            <Select
              label="Training goal"
              name="mainGoal"
              placeholder="Not set"
              options={GOAL_OPTIONS}
              value={form.mainGoal}
              onChange={(event) => update('mainGoal', event.target.value)}
              error={fieldErrors.mainGoal}
            />
            <Select
              label="Experience level"
              name="currentLevel"
              placeholder="Not set"
              options={LEVEL_OPTIONS}
              value={form.currentLevel}
              onChange={(event) => update('currentLevel', event.target.value)}
              error={fieldErrors.currentLevel}
            />
            <Input
              className="profile-form__wide"
              label="Address"
              name="address"
              autoComplete="street-address"
              maxLength={ADDRESS_MAX_LENGTH}
              value={form.address}
              onChange={(event) => update('address', event.target.value)}
              error={fieldErrors.address}
            />
          </div>
          <div className="profile-form__actions">
            <Button type="submit" loading={saving}>
              Save changes
            </Button>
            <Link className={buttonClassName('secondary', 'md')} to="/member">
              Back to dashboard
            </Link>
          </div>
        </fieldset>
      </form>
    </Card>
  )
}

function ChangePasswordCard() {
  const toast = useToast()
  const rulesId = useId()
  const [form, setForm] = useState<PasswordForm>(EMPTY_PASSWORD_FORM)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const sendingRef = useRef(false)
  const formRef = useRef<HTMLFormElement>(null)
  const focusFirstInvalid = useFocusFirstInvalid(formRef)

  const update = (field: keyof PasswordForm, value: string) => {
    setForm((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => {
      if (!(field in current)) {
        return current
      }
      const next = { ...current }
      delete next[field]
      return next
    })
    setSuccess(null)
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (sendingRef.current) {
      return
    }
    setFormError(null)
    setSuccess(null)
    const errors = validatePasswordChange(form)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      focusFirstInvalid()
      return
    }
    sendingRef.current = true
    setSaving(true)
    try {
      await memberApi.changePassword(toChangePasswordRequest(form))
      setForm(EMPTY_PASSWORD_FORM)
      setSuccess(PASSWORD_CHANGED_MESSAGE)
      toast.success('Password changed', PASSWORD_CHANGED_MESSAGE)
    } catch (error) {
      const failure = passwordFailure(error)
      setFieldErrors(failure.fields)
      setFormError(failure.form)
      if (Object.keys(failure.fields).length > 0) {
        focusFirstInvalid()
      }
    } finally {
      sendingRef.current = false
      setSaving(false)
    }
  }

  const passwordsMatch = form.confirmPassword.length > 0 && form.confirmPassword === form.newPassword

  return (
    <Card title="Change password">
      {success && (
        // Not a live region: the success toast is the single announcement for screen readers.
        <p className="notice notice--success">{success}</p>
      )}
      {formError && (
        <p className="notice notice--danger" role="alert">
          {formError}
        </p>
      )}
      <form ref={formRef} onSubmit={handleSubmit} noValidate aria-busy={saving} aria-label="Change password">
        <fieldset className="profile-form" disabled={saving}>
          <div className="profile-form__grid">
            <PasswordInput
              className="profile-form__wide"
              label="Current password"
              name="currentPassword"
              autoComplete="current-password"
              value={form.currentPassword}
              onChange={(event) => update('currentPassword', event.target.value)}
              error={fieldErrors.currentPassword}
              required
            />
            <div className="field-group">
              <PasswordInput
                label="New password"
                name="newPassword"
                autoComplete="new-password"
                value={form.newPassword}
                onChange={(event) => update('newPassword', event.target.value)}
                error={fieldErrors.newPassword}
                aria-describedby={rulesId}
                required
              />
              <PasswordRules id={rulesId} password={form.newPassword} />
            </div>
            <PasswordInput
              label="Confirm new password"
              name="confirmPassword"
              autoComplete="new-password"
              value={form.confirmPassword}
              onChange={(event) => update('confirmPassword', event.target.value)}
              error={fieldErrors.confirmPassword}
              hint={passwordsMatch ? 'Passwords match' : undefined}
              required
            />
          </div>
          <div className="profile-form__actions">
            <Button type="submit" loading={saving}>
              Change password
            </Button>
          </div>
        </fieldset>
      </form>
    </Card>
  )
}
