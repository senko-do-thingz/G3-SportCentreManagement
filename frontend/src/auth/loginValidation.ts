/** Basic email shape: something@something.something without spaces. The backend does the full check. */
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** Client side checks run before the request. Messages match the backend validation messages. */
export function validateLogin(email: string, password: string): Record<string, string> {
  const errors: Record<string, string> = {}
  const trimmedEmail = email.trim()
  if (!trimmedEmail) {
    errors.email = 'Email is required'
  } else if (!EMAIL_PATTERN.test(trimmedEmail)) {
    errors.email = 'Email is invalid'
  }
  if (!password) {
    errors.password = 'Password is required'
  }
  return errors
}
