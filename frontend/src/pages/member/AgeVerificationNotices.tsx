import { Link } from 'react-router-dom'
import { buttonClassName } from '../../components/ui'
import { MEMBER_PATHS } from './dashboardModel'

// Thong bao dung chung cho viec xac minh ngay sinh: mua the/goi tap va dat lich lop co nhom tuoi.
export const DATE_OF_BIRTH_PURCHASE_MESSAGE = 'Please update your date of birth in your profile before purchasing a subscription plan.'

export function DateOfBirthNotice({ message, testId, className }: { message: string; testId: string; className?: string }) {
  return (
    <div className={['notice notice--warning', className].filter(Boolean).join(' ')} role="alert" data-testid={testId}>
      <p>{message}</p>
      <Link className={buttonClassName('secondary', 'sm')} to={MEMBER_PATHS.profile}>
        Update date of birth
      </Link>
    </div>
  )
}

/**
 * Explains an AGE_RESTRICTED eligibility result. Without a date of birth on file the member cannot be outside the age
 * group yet, so they are asked to add it instead of being told their age does not fit.
 */
export function AgeRestrictionNotice({ ageGroupLabel, hasDateOfBirth }: { ageGroupLabel?: string | null; hasDateOfBirth: boolean }) {
  const requirement = ageGroupLabel ? `This class is for ${ageGroupLabel}.` : 'This class has an age requirement.'
  if (!hasDateOfBirth) {
    return (
      <DateOfBirthNotice
        testId="age-restriction-notice"
        message={`${requirement} Add your date of birth in your profile so we can confirm your age on the session date.`}
      />
    )
  }
  return (
    <div className="notice notice--warning" role="alert" data-testid="age-restriction-notice">
      <strong>Age requirement not met</strong>
      <p>{requirement}</p>
    </div>
  )
}
