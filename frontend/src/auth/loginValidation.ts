/** Basic email shape: something@something.something without spaces. The backend does the full check. */
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * Client side checks run before the request. Messages follow common sign in forms: say what to do, give an example.
 * Server field errors keep the backend wording.
 */
export function validateLogin(email: string, password: string): Record<string, string> {
  const errors: Record<string, string> = {}
  const trimmedEmail = email.trim()
  if (!trimmedEmail) {
    errors.email = 'Enter your email'
  } else if (!EMAIL_PATTERN.test(trimmedEmail)) {
    errors.email = 'Enter a valid email, like name@example.com'
  }
  if (!password) {
    errors.password = 'Enter your password'
  }
  return errors
}
