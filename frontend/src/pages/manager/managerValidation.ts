import { isStrongPassword, STRONG_PASSWORD_MESSAGE } from '../../auth/passwordRules'
import type { StaffAccountRequest } from '../../types/manager'

export function validateStaff(request: StaffAccountRequest): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!request.fullName.trim()) errors.fullName = 'Full name is required.'
  else if (request.fullName.length > 100) errors.fullName = 'Full name must be at most 100 characters.'
  if (!request.email.trim()) errors.email = 'Email is required.'
  else if (request.email.length > 255 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(request.email)) errors.email = 'Enter a valid email address.'
  if (!/^\+?\d{8,15}$/.test(request.phone)) errors.phone = 'Phone must be 8-15 digits with optional leading +.'
  if (!isStrongPassword(request.temporaryPassword)) errors.temporaryPassword = STRONG_PASSWORD_MESSAGE
  if (!['COACH', 'RECEPTIONIST', 'MANAGER'].includes(request.role)) errors.role = 'Choose a staff role.'
  if (request.role === 'COACH' && (!request.sportIds.length || request.sportIds.length > 20)) errors.sportIds = 'Choose 1-20 sports for this coach.'
  return errors
}

export function numericError(value: string, label: string, minimum: number, integer = false, maximum = Infinity): string | undefined {
  const parsed = Number(value)
  if (!value.trim() || !Number.isFinite(parsed) || parsed < minimum || (integer && !Number.isInteger(parsed))) {
    return `${label} must be ${integer ? 'a whole number' : 'a number'} of at least ${minimum}.`
  }
  if (parsed > maximum) return `${label} must not exceed ${maximum}.`
  return undefined
}

export function positivePriceError(value: string): string | undefined {
  if (!value.trim() || !Number.isFinite(Number(value)) || Number(value) <= 0) return 'Enter a price greater than 0 VND.'
  if (!/^\d{1,12}(?:\.\d{1,2})?$/.test(value.trim()) || Number(value) < 0.01) return 'Price must have at most 12 integer digits and 2 decimal places.'
  return undefined
}
