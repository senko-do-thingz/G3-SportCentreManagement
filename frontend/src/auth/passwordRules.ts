/**
 * Password rules shared by the sign up form. They mirror the backend validator
 * com.sportify.core.validation.StrongPasswordValidator exactly, so a password accepted here is
 * also accepted by POST /auth/register:
 * - length 8 to 64 (Java String.length, which counts UTF-16 code units like JavaScript)
 * - at most 72 bytes in UTF-8 (the BCrypt input limit)
 * - at least one letter (Character.isLetter) and one digit (Character.isDigit), checked per
 *   UTF-16 code unit like the Java loop over toCharArray()
 */

export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 64
export const PASSWORD_MAX_BYTES = 72

/** Same text as the default message of the backend @StrongPassword annotation. */
export const STRONG_PASSWORD_MESSAGE =
  'Password must be 8 to 64 characters long, contain at least one letter and one digit, and not exceed 72 bytes'

export type PasswordRuleId = 'length' | 'letter' | 'digit' | 'bytes'

export interface PasswordRuleResult {
  id: PasswordRuleId
  label: string
  met: boolean
}

const LETTER = /^\p{L}$/u
const DIGIT = /^\p{Nd}$/u

/** A UTF-16 surrogate half is never a letter or digit for Java's Character.isLetter(char). */
function isSurrogate(codeUnit: number): boolean {
  return codeUnit >= 0xd800 && codeUnit <= 0xdfff
}

function codeUnitMatches(value: string, pattern: RegExp): boolean {
  for (let index = 0; index < value.length; index += 1) {
    const codeUnit = value.charCodeAt(index)
    if (!isSurrogate(codeUnit) && pattern.test(String.fromCharCode(codeUnit))) {
      return true
    }
  }
  return false
}

export function utf8ByteLength(value: string): number {
  return new TextEncoder().encode(value).length
}

/**
 * Result of every rule, in the order shown under the password field. An empty password meets no
 * rule, so the list starts neutral instead of showing the byte limit as already met.
 */
export function checkPassword(value: string): PasswordRuleResult[] {
  const empty = value.length === 0
  return [
    {
      id: 'length',
      // Common wording; the upper limit is only mentioned once a password goes past it.
      label: value.length > PASSWORD_MAX_LENGTH ? `No more than ${PASSWORD_MAX_LENGTH} characters` : `At least ${PASSWORD_MIN_LENGTH} characters`,
      met: value.length >= PASSWORD_MIN_LENGTH && value.length <= PASSWORD_MAX_LENGTH,
    },
    { id: 'letter', label: '1 letter', met: codeUnitMatches(value, LETTER) },
    { id: 'digit', label: '1 number', met: codeUnitMatches(value, DIGIT) },
    {
      id: 'bytes',
      // Only shown when broken (see PasswordRules): the BCrypt byte limit is a technical rule people rarely reach.
      label: 'Too long: use fewer accented letters or symbols',
      met: !empty && utf8ByteLength(value) <= PASSWORD_MAX_BYTES,
    },
  ]
}

export function isStrongPassword(value: string): boolean {
  return checkPassword(value).every((rule) => rule.met)
}

/**
 * Progress label shown above the rules: how many of the four backend rules are met. It is not a
 * real strength score, so the top level is called "Meets requirements" instead of "Strong".
 */
export type PasswordProgress = 'empty' | 'weak' | 'fair' | 'valid'

export function passwordProgress(value: string): PasswordProgress {
  if (!value) {
    return 'empty'
  }
  const met = checkPassword(value).filter((rule) => rule.met).length
  if (met === 4) {
    return 'valid'
  }
  return met >= 3 ? 'fair' : 'weak'
}
