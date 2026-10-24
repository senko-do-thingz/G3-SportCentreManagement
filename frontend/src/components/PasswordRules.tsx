import { checkPassword, passwordProgress } from '../auth/passwordRules'

const PROGRESS_LABELS = {
  empty: '',
  weak: 'Password strength: Weak',
  fair: 'Password strength: Fair',
  valid: 'Meets requirements',
} as const

/**
 * Live checklist of the password rules (same rules as the backend @StrongPassword), shown under
 * a new password field. Used by Sign up (F1-06) and the change password form of My Profile (F1-08).
 * Only the three rules people need to know are listed (length, a letter, a number), like most sign up forms.
 * The 72 byte limit (BCrypt) appears only when a password breaks it, as an error line.
 */
export function PasswordRules({ id, password }: { id: string; password: string }) {
  const rules = checkPassword(password)
  const progress = passwordProgress(password)
  return (
    <div className="password-rules" id={id}>
      {progress !== 'empty' && (
        <p className={`password-rules__strength password-rules__strength--${progress}`} aria-live="polite">
          {PROGRESS_LABELS[progress]}
        </p>
      )}
      <ul className="password-rules__list">
        {rules.filter((rule) => rule.id !== 'bytes' || (password !== '' && !rule.met)).map((rule) => (
          <li
            key={rule.id}
            className={`password-rules__item${rule.met ? ' password-rules__item--met' : ''}${rule.id === 'bytes' ? ' password-rules__item--error' : ''}`}
            data-rule={rule.id}
            data-met={rule.met}
          >
            <span>{rule.label}</span>
            <span className="visually-hidden">{rule.met ? ' - met' : ' - not met'}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
