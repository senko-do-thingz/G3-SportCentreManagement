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
    errors.confirmPassword = 'Please confirm your password'
  } else if (form.confirmPassword !== form.password) {
    errors.confirmPassword = 'Passwords do not match'
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
