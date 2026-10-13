import { describe, expect, it } from 'vitest'
import { STRONG_PASSWORD_MESSAGE } from '../../auth/passwordRules'
import { MEMBER } from '../../test/fakeBackend'
import {
  profileFormFrom,
  SAME_PASSWORD_MESSAGE,
  toChangePasswordRequest,
  toUpdateProfileRequest,
  validatePasswordChange,
  validateProfile,
  type ProfileForm,
} from './profileValidation'

/** FE-02: client checks of My Profile (F1-08). Today is 2026-10-13. */
const TODAY = '2026-10-13'

const VALID: ProfileForm = {
  fullName: 'Taylor Nguyen',
  phone: '0901234567',
  dateOfBirth: '2004-06-15',
  address: 'Ho Chi Minh City',
  mainGoal: 'IMPROVE_FITNESS',
  currentLevel: 'BEGINNER',
}

describe('FE-02 profile validation', () => {
  it('TC-FE02-070 a complete profile has no errors; optional fields may be empty', () => {
    expect(validateProfile(VALID, TODAY)).toEqual({})
    expect(validateProfile({ ...VALID, dateOfBirth: '', address: '', mainGoal: '', currentLevel: '' }, TODAY)).toEqual({})
  })

  it('TC-FE02-071 full name is required and at most 100 characters', () => {
    expect(validateProfile({ ...VALID, fullName: '   ' }, TODAY).fullName).toBe('Full name is required')
    expect(validateProfile({ ...VALID, fullName: 'a'.repeat(101) }, TODAY).fullName).toBe('Full name must be at most 100 characters')
    expect(validateProfile({ ...VALID, fullName: 'a'.repeat(100) }, TODAY).fullName).toBeUndefined()
  })

  it('TC-FE02-072 phone follows the backend pattern after removing separators', () => {
    expect(validateProfile({ ...VALID, phone: '' }, TODAY).phone).toBe('Phone is required')
    expect(validateProfile({ ...VALID, phone: '1234567' }, TODAY).phone).toBe('Phone must be 8-15 digits with optional leading +')
    expect(validateProfile({ ...VALID, phone: '(+84) 901-234-567' }, TODAY).phone).toBeUndefined()
  })

  it('TC-FE02-073 date of birth must be a real past date not before 1900', () => {
    expect(validateProfile({ ...VALID, dateOfBirth: TODAY }, TODAY).dateOfBirth).toBeUndefined()
    expect(validateProfile({ ...VALID, dateOfBirth: '2026-10-14' }, TODAY).dateOfBirth).toBe('Date of birth must be in the past')
    expect(validateProfile({ ...VALID, dateOfBirth: '1899-12-31' }, TODAY).dateOfBirth).toBe('Date of birth is invalid')
    expect(validateProfile({ ...VALID, dateOfBirth: '15/06/2004' }, TODAY).dateOfBirth).toBe('Date of birth is invalid')
  })

  it('TC-FE02-074 address is at most 255 characters', () => {
    expect(validateProfile({ ...VALID, address: 'a'.repeat(256) }, TODAY).address).toBe('Address must be at most 255 characters')
  })

  it('TC-FE02-075 the request trims values, strips phone separators and sends empty optionals as null', () => {
    expect(
      toUpdateProfileRequest({ ...VALID, fullName: ' Taylor ', phone: '0901 234 567', address: '  ', mainGoal: '' }),
    ).toEqual({
      fullName: 'Taylor',
      phone: '0901234567',
      dateOfBirth: '2004-06-15',
      address: null,
      mainGoal: null,
      currentLevel: 'BEGINNER',
    })
  })

  it('TC-FE02-076 the form is filled from the profile and unknown codes become empty', () => {
    expect(profileFormFrom({ ...MEMBER, phone: null, mainGoal: 'OTHER', currentLevel: 'ADVANCED' })).toEqual({
      fullName: 'Taylor Nguyen',
      phone: '',
      dateOfBirth: '',
      address: '',
      mainGoal: '',
      currentLevel: 'ADVANCED',
    })
  })

  it('TC-FE02-077 password change rules', () => {
    expect(validatePasswordChange({ currentPassword: 'Demo123!', newPassword: 'NewPass2026', confirmPassword: 'NewPass2026' })).toEqual({})
    expect(validatePasswordChange({ currentPassword: 'x', newPassword: 'short', confirmPassword: 'short' }).newPassword).toBe(
      STRONG_PASSWORD_MESSAGE,
    )
    expect(
      validatePasswordChange({ currentPassword: 'Demo123!', newPassword: 'Demo123!', confirmPassword: 'Demo123!' }).newPassword,
    ).toBe(SAME_PASSWORD_MESSAGE)
    expect(
      validatePasswordChange({ currentPassword: 'Demo123!', newPassword: 'NewPass2026', confirmPassword: 'NewPass2027' }).confirmPassword,
    ).toBe('Passwords do not match')
    expect(toChangePasswordRequest({ currentPassword: ' a ', newPassword: 'b', confirmPassword: 'b' })).toEqual({
      currentPassword: ' a ',
      newPassword: 'b',
    })
  })
})
