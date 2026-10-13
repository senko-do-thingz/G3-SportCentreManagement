import type { ChangePasswordRequest, UpdateProfileRequest } from '../../api/memberApi'
import type { UserProfile } from '../../api/types'
import { isStrongPassword, STRONG_PASSWORD_MESSAGE } from '../../auth/passwordRules'
import {
  CONFIRM_PASSWORD_REQUIRED,
  FULL_NAME_MAX_LENGTH,
  normalizePhone,
  PASSWORDS_DO_NOT_MATCH,
  PHONE_PATTERN,
} from '../../auth/registerValidation'
import type { SelectOption } from '../../components/ui/Select'

/**
 * Client side checks of My Profile (F1-08). Messages reuse the sign up messages so a member sees
 * the same text on both screens. The backend (BE-02) stays the source of truth: its 400 details
 * are shown under the matching field.
 */

export const PROFILE_SAVED_MESSAGE = 'Your profile was saved.'
export const PASSWORD_CHANGED_MESSAGE = 'Your password was changed. Use the new password next time you log in.'

export { FULL_NAME_MAX_LENGTH }
export const ADDRESS_MAX_LENGTH = 255
/** Oldest accepted date of birth; anything earlier is a typing mistake. */
export const MIN_DATE_OF_BIRTH = '1900-01-01'
/** Longest phone the input accepts as typed, separators included; the digits are checked by PHONE_PATTERN. */
export const PHONE_INPUT_MAX_LENGTH = 24

export const DATE_OF_BIRTH_PAST_MESSAGE = 'Date of birth must be before today'

/**
 * Latest accepted date of birth: the day before today. A date of birth must be strictly in the
 * past, so today itself is rejected, and the date picker uses this value as its max.
 */
export function latestDateOfBirth(today: string): string {
  const [year, month, day] = today.split('-').map(Number)
  const previous = new Date(year, month - 1, day - 1)
  const mm = String(previous.getMonth() + 1).padStart(2, '0')
  const dd = String(previous.getDate()).padStart(2, '0')
  return `${previous.getFullYear()}-${mm}-${dd}`
}

/** Values of member_profile.main_goal (docs/database/01-identity-and-access.md). */
export const GOAL_OPTIONS: SelectOption[] = [
  { value: 'IMPROVE_FITNESS', label: 'Improve fitness' },
  { value: 'BUILD_SKILLS', label: 'Build skills' },
  { value: 'COMPETITION', label: 'Competition' },
]

/** Values of member_profile.current_level. */
export const LEVEL_OPTIONS: SelectOption[] = [
  { value: 'BEGINNER', label: 'Beginner' },
  { value: 'INTERMEDIATE', label: 'Intermediate' },
  { value: 'ADVANCED', label: 'Advanced' },
]

export interface ProfileForm {
  fullName: string
  phone: string
  dateOfBirth: string
  address: string
  mainGoal: string
  currentLevel: string
}

/** Inputs of the profile form; backend detail keys outside this list are shown above the form. */
export const PROFILE_FIELDS = ['fullName', 'phone', 'dateOfBirth', 'address', 'mainGoal', 'currentLevel'] as const

/** Keeps only a value the select can show, so an unknown code never leaves the select blank. */
function knownOption(value: string | null | undefined, options: SelectOption[]): string {
  return value && options.some((option) => option.value === value) ? value : ''
}

export function profileFormFrom(user: UserProfile): ProfileForm {
  return {
    fullName: user.fullName ?? '',
    phone: user.phone ?? '',
    dateOfBirth: user.dateOfBirth ?? '',
    address: user.address ?? '',
    mainGoal: knownOption(user.mainGoal, GOAL_OPTIONS),
    currentLevel: knownOption(user.currentLevel, LEVEL_OPTIONS),
  }
}

export function validateProfile(form: ProfileForm, today: string): Record<string, string> {
  const errors: Record<string, string> = {}

  const fullName = form.fullName.trim()
  if (!fullName) {
    errors.fullName = 'Full name is required'
  } else if (fullName.length > FULL_NAME_MAX_LENGTH) {
    errors.fullName = `Full name must be at most ${FULL_NAME_MAX_LENGTH} characters`
  }

  // Phone is optional, as in RegisterRequest: a member who signed up without one must still be
  // able to save other changes. The pattern is checked only when a value is entered.
  const phone = normalizePhone(form.phone)
  if (phone && !PHONE_PATTERN.test(phone)) {
    errors.phone = 'Phone must be 8-15 digits with optional leading +'
  }

  if (form.dateOfBirth) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(form.dateOfBirth)) {
      errors.dateOfBirth = 'Date of birth is invalid'
    } else if (form.dateOfBirth >= today) {
      errors.dateOfBirth = DATE_OF_BIRTH_PAST_MESSAGE
    } else if (form.dateOfBirth < MIN_DATE_OF_BIRTH) {
      errors.dateOfBirth = 'Date of birth is invalid'
    }
  }

  if (form.address.trim().length > ADDRESS_MAX_LENGTH) {
    errors.address = `Address must be at most ${ADDRESS_MAX_LENGTH} characters`
  }

  return errors
}

function orNull(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

export function toUpdateProfileRequest(form: ProfileForm): UpdateProfileRequest {
  return {
    fullName: form.fullName.trim(),
    phone: orNull(normalizePhone(form.phone)),
    dateOfBirth: orNull(form.dateOfBirth),
    address: orNull(form.address),
    mainGoal: orNull(form.mainGoal),
    currentLevel: orNull(form.currentLevel),
  }
}

export interface PasswordForm {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export const EMPTY_PASSWORD_FORM: PasswordForm = { currentPassword: '', newPassword: '', confirmPassword: '' }

export const PASSWORD_FIELDS = ['currentPassword', 'newPassword', 'confirmPassword'] as const

/**
 * Shown under Current password when PUT /users/me/password answers 401. The request is sent with
 * skipAuthRefresh, so a 401 here describes the typed password and never ends the session.
 */
export const CURRENT_PASSWORD_REJECTED_MESSAGE = 'Current password is incorrect'

export const SAME_PASSWORD_MESSAGE = 'New password must be different from the current password'

export function validatePasswordChange(form: PasswordForm): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!form.currentPassword) {
    errors.currentPassword = 'Current password is required'
  }
  if (!form.newPassword) {
    errors.newPassword = 'New password is required'
  } else if (!isStrongPassword(form.newPassword)) {
    errors.newPassword = STRONG_PASSWORD_MESSAGE
  } else if (form.currentPassword && form.newPassword === form.currentPassword) {
    errors.newPassword = SAME_PASSWORD_MESSAGE
  }
  if (!form.confirmPassword) {
    errors.confirmPassword = CONFIRM_PASSWORD_REQUIRED
  } else if (form.confirmPassword !== form.newPassword) {
    errors.confirmPassword = PASSWORDS_DO_NOT_MATCH
  }
  return errors
}

export function toChangePasswordRequest(form: PasswordForm): ChangePasswordRequest {
  return { currentPassword: form.currentPassword, newPassword: form.newPassword }
}
