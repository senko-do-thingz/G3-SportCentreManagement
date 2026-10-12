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

/** Result of every rule, in the order shown under the password field. */
export function checkPassword(value: string): PasswordRuleResult[] {
  return [
    {
      id: 'length',
      label: `${PASSWORD_MIN_LENGTH} to ${PASSWORD_MAX_LENGTH} characters`,
      met: value.length >= PASSWORD_MIN_LENGTH && value.length <= PASSWORD_MAX_LENGTH,
    },
    { id: 'letter', label: 'At least one letter', met: codeUnitMatches(value, LETTER) },
    { id: 'digit', label: 'At least one digit', met: codeUnitMatches(value, DIGIT) },
    {
      id: 'bytes',
      label: `No more than ${PASSWORD_MAX_BYTES} bytes (accented letters count as 2)`,
      met: utf8ByteLength(value) <= PASSWORD_MAX_BYTES,
    },
  ]
}

export function isStrongPassword(value: string): boolean {
  return checkPassword(value).every((rule) => rule.met)
}

/** Strength label shown next to the rules: how many of the four rules are met. */
export function passwordStrength(value: string): 'empty' | 'weak' | 'fair' | 'strong' {
  if (!value) {
    return 'empty'
  }
  const met = checkPassword(value).filter((rule) => rule.met).length
  if (met === 4) {
    return 'strong'
  }
  return met >= 3 ? 'fair' : 'weak'
}
