import { describe, expect, it } from 'vitest'
import { checkPassword, isStrongPassword, passwordProgress, utf8ByteLength } from './passwordRules'
import {
  applyFieldChange,
  EMPTY_REGISTER_FORM,
  nextConfirmPasswordError,
  normalizePhone,
  toRegisterRequest,
  validateRegister,
} from './registerValidation'

/** Cases copied from the rules of com.sportify.core.validation.StrongPasswordValidator. */
describe('password rules (same as backend StrongPassword)', () => {
  it.each([
    ['Train202', true, 'exactly 8 characters with a letter and a digit'],
    ['Train20', false, '7 characters is too short'],
    [`a1${'b'.repeat(62)}`, true, 'exactly 64 characters'],
    [`a1${'b'.repeat(63)}`, false, '65 characters is too long'],
    ['abcdefgh', false, 'letters only'],
    ['12345678', false, 'digits only'],
    ['!!!!!!!a1', true, 'symbols are allowed but do not count'],
    ['matkhau\u00E91', true, 'an accented letter counts as a letter'],
    ['\u0661\u0662\u0663abcde', true, 'any Unicode decimal digit counts, like Character.isDigit'],
  ])('%s -> %s (%s)', (password, expected) => {
    expect(isStrongPassword(password)).toBe(expected)
  })

  it('rejects more than 72 UTF-8 bytes even when the length is below 64', () => {
    const password = `${'\u00E9'.repeat(36)}1` // 37 characters, 73 bytes
    expect(password.length).toBe(37)
    expect(utf8ByteLength(password)).toBe(73)
    expect(isStrongPassword(password)).toBe(false)
    expect(checkPassword(password).find((rule) => rule.id === 'bytes')?.met).toBe(false)
    expect(isStrongPassword(`${'\u00E9'.repeat(35)}1`)).toBe(true) // 71 bytes
  })

  it('counts UTF-16 code units for the length, like Java String.length', () => {
    // One character outside the BMP is 2 code units in both Java and JavaScript.
    const password = `${'\u{1F3C0}'.repeat(3)}a1` // 8 code units
    expect(password.length).toBe(8)
    expect(isStrongPassword(password)).toBe(true)
  })

  it('does not count a letter outside the BMP, like Character.isLetter(char) on a surrogate', () => {
    const password = `${'\u{1D400}'.repeat(4)}1234` // 4 math letters (8 code units) + 4 digits
    expect(checkPassword(password).find((rule) => rule.id === 'letter')?.met).toBe(false)
    expect(isStrongPassword(password)).toBe(false)
  })

  it('an empty password meets no rule, not even the byte limit (N6)', () => {
    expect(checkPassword('').every((rule) => !rule.met)).toBe(true)
    expect(checkPassword('a').find((rule) => rule.id === 'bytes')?.met).toBe(true)
  })

  it('reports progress from the number of rules met, and never calls a password strong', () => {
    expect(passwordProgress('')).toBe('empty')
    expect(passwordProgress('abc')).toBe('weak')
    expect(passwordProgress('abcdefgh')).toBe('fair')
    // A simple password that passes every backend rule only "meets the requirements".
    expect(passwordProgress('abcdefg1')).toBe('valid')
  })
})

describe('sign up form checks', () => {
  const valid = {
    ...EMPTY_REGISTER_FORM,
    fullName: 'Jamie Tran',
    phone: '0901234599',
    email: 'jamie@sportify.demo',
    password: 'Train2026',
    confirmPassword: 'Train2026',
    acceptTerms: true,
  }

  it('accepts a complete form', () => {
    expect(validateRegister(valid)).toEqual({})
  })

  it('uses common sign up wording for length limits and phone format', () => {
    expect(validateRegister({ ...valid, fullName: 'a'.repeat(101) }).fullName).toBe('Use 100 characters or fewer')
    expect(validateRegister({ ...valid, phone: '1234567' }).phone).toBe('Enter a valid phone number, like 0901 234 567')
    expect(validateRegister({ ...valid, phone: '+8412345678901234' }).phone).toBe('Enter a valid phone number, like 0901 234 567')
    expect(validateRegister({ ...valid, phone: '+841234567890123' }).phone).toBeUndefined() // 15 digits
    expect(validateRegister({ ...valid, phone: '+84901234567' }).phone).toBeUndefined()
    expect(validateRegister({ ...valid, email: 'jamie@sportify' }).email).toBe('Enter a valid email, like name@example.com')
  })

  it('removes separators from the phone number before checking and sending it', () => {
    expect(normalizePhone('(+84) 901-234.567')).toBe('+84901234567')
    expect(toRegisterRequest({ ...valid, fullName: ' Jamie ', email: ' j@s.vn ', phone: '0901 234 599' })).toEqual({
      fullName: 'Jamie',
      email: 'j@s.vn',
      password: 'Train2026',
      phone: '0901234599',
    })
  })

  it('keeps the password exactly as typed', () => {
    expect(toRegisterRequest({ ...valid, password: ' Train2026 ' }).password).toBe(' Train2026 ')
  })
})

describe('applyFieldChange works from the latest state (N3)', () => {
  const start = { form: { ...EMPTY_REGISTER_FORM }, fieldErrors: {} as Record<string, string> }

  it('chains two edits without losing the first one', () => {
    const afterPassword = applyFieldChange(start, 'password', 'Train2026')
    const afterConfirm = applyFieldChange(afterPassword, 'confirmPassword', 'Train2026')
    expect(afterConfirm.form.password).toBe('Train2026')
    expect(afterConfirm.form.confirmPassword).toBe('Train2026')
  })

  it('computes the Confirm password error from the same next values', () => {
    const shown = { form: { ...EMPTY_REGISTER_FORM, password: 'Train2026', confirmPassword: 'Train2027' }, fieldErrors: { confirmPassword: "Passwords don't match. Try again.", password: 'x' } }
    const fixed = applyFieldChange(shown, 'password', 'Train2027')
    expect(fixed.fieldErrors).toEqual({})
  })
})

describe('Confirm password error after editing either password field (R4)', () => {
  it('adds nothing while no error is shown', () => {
    expect(nextConfirmPasswordError({ password: 'Train2026', confirmPassword: 'Train2' }, undefined)).toBeUndefined()
  })

  it('clears a shown mismatch as soon as the two values match', () => {
    expect(nextConfirmPasswordError({ password: 'Train2027', confirmPassword: 'Train2027' }, "Passwords don't match. Try again.")).toBeUndefined()
  })

  it('keeps the mismatch while the values still differ', () => {
    expect(nextConfirmPasswordError({ password: 'Train2028', confirmPassword: 'Train2027' }, "Passwords don't match. Try again.")).toBe(
      "Passwords don't match. Try again.",
    )
  })

  it('asks for the confirmation again when the field is emptied', () => {
    expect(nextConfirmPasswordError({ password: 'Train2026', confirmPassword: '' }, "Passwords don't match. Try again.")).toBe(
      'Confirm your password',
    )
  })
})
