import type { RegisterRequest } from '../api/types'
import { EMAIL_PATTERN } from './loginValidation'
import { isStrongPassword, STRONG_PASSWORD_MESSAGE } from './passwordRules'

/** Values of the sign up form (Figma F1-06) as the user typed them. */
export interface RegisterForm {
  fullName: string
  phone: string
  email: string
  password: string
  confirmPassword: string
  acceptTerms: boolean
}

export const EMPTY_REGISTER_FORM: RegisterForm = {
  fullName: '',
  phone: '',
  email: '',
  password: '',
  confirmPassword: '',
  acceptTerms: false,
}

/**
 * Message of the 409 that AuthenticationServiceImpl.register throws for an existing email. Only
 * this 409 belongs under the Email field; any other 409 (for example "The request conflicts with
 * existing data" from DataIntegrityViolationException) is shown above the form.
 */
export const EMAIL_TAKEN_MESSAGE = 'Email is already registered'

/** Same pattern as RegisterRequest.phone on the backend: 8 to 15 digits with an optional leading +. */
export const PHONE_PATTERN = /^\+?\d{8,15}$/
export const FULL_NAME_MAX_LENGTH = 100
export const EMAIL_MAX_LENGTH = 255

/**
 * Removes the spaces, dots, dashes and brackets people often type in a phone number, for example
 * "0901 234 567" or "(+84) 901-234-567", so the value matches the backend pattern.
 */
export function normalizePhone(phone: string): string {
  return phone.replace(/[\s.\-()]/g, '')
}

/** Inputs rendered by the sign up form; backend validation keys outside this list go above the form. */
export const REGISTER_FIELDS = ['fullName', 'phone', 'email', 'password', 'confirmPassword', 'acceptTerms'] as const

export const PASSWORDS_DO_NOT_MATCH = 'Passwords do not match'
export const CONFIRM_PASSWORD_REQUIRED = 'Please confirm your password'

/**
 * Client side checks run before POST /auth/register. Messages match the backend validation
 * messages of RegisterRequest so the user sees the same text whichever side catches the error.
 * The phone number is required on this form even though the backend accepts it empty: the front
 * desk needs it to contact members about cards and packages.
 */
export function validateRegister(form: RegisterForm): Record<string, string> {
  const errors: Record<string, string> = {}

  const fullName = form.fullName.trim()
  if (!fullName) {
    errors.fullName = 'Full name is required'
  } else if (fullName.length > FULL_NAME_MAX_LENGTH) {
    errors.fullName = 'Full name must be at most 100 characters'
  }

  const phone = normalizePhone(form.phone)
  if (!phone) {
    errors.phone = 'Phone is required'
  } else if (!PHONE_PATTERN.test(phone)) {
    errors.phone = 'Phone must be 8-15 digits with optional leading +'
  }

  const email = form.email.trim()
  if (!email) {
    errors.email = 'Email is required'
  } else if (email.length > EMAIL_MAX_LENGTH) {
    errors.email = 'Email must be at most 255 characters'
  } else if (!EMAIL_PATTERN.test(email)) {
    errors.email = 'Email is invalid'
  }

  if (!form.password) {
    errors.password = 'Password is required'
  } else if (!isStrongPassword(form.password)) {
    errors.password = STRONG_PASSWORD_MESSAGE
  }

  if (!form.confirmPassword) {
    errors.confirmPassword = CONFIRM_PASSWORD_REQUIRED
  } else if (form.confirmPassword !== form.password) {
    errors.confirmPassword = PASSWORDS_DO_NOT_MATCH
  }

  if (!form.acceptTerms) {
    errors.acceptTerms = 'You must accept the terms to create an account'
  }

  return errors
}

/** Builds the request body: trimmed name and email, phone without separators, password as typed. */
export function toRegisterRequest(form: RegisterForm): RegisterRequest {
  return {
    fullName: form.fullName.trim(),
    email: form.email.trim(),
    password: form.password,
    phone: normalizePhone(form.phone),
  }
}

/**
 * New Confirm password error after the user edited Password or Confirm password, given the error
 * already on screen. Nothing is added while the user is still typing for the first time; an error
 * that is shown is cleared as soon as the two values match and otherwise kept up to date.
 */
export function nextConfirmPasswordError(
  form: Pick<RegisterForm, 'password' | 'confirmPassword'>,
  shownError: string | undefined,
): string | undefined {
  if (!shownError) {
    return undefined
  }
  if (!form.confirmPassword) {
    return CONFIRM_PASSWORD_REQUIRED
  }
  return form.confirmPassword === form.password ? undefined : PASSWORDS_DO_NOT_MATCH
}

/** Form values and the field errors on screen, kept in one state so they always change together. */
export interface RegisterFormState {
  form: RegisterForm
  fieldErrors: Record<string, string>
}

/**
 * Applies one edit to the sign up form. Used inside a functional state update, so it always starts
 * from the latest values (two edits in the same event batch never overwrite each other). Clears
 * the error of the edited field; when Password or Confirm password changes, recomputes the Confirm
 * password error from the same next values (see nextConfirmPasswordError).
 */
export function applyFieldChange<K extends keyof RegisterForm>(
  current: RegisterFormState,
  field: K,
  value: RegisterForm[K],
): RegisterFormState {
  const form = { ...current.form, [field]: value }
  const fieldErrors = { ...current.fieldErrors }
  delete fieldErrors[field]
  if (field === 'password' || field === 'confirmPassword') {
    const confirmError = nextConfirmPasswordError(form, current.fieldErrors.confirmPassword)
    if (confirmError) {
      fieldErrors.confirmPassword = confirmError
    } else {
      delete fieldErrors.confirmPassword
    }
  }
  return { form, fieldErrors }
}
