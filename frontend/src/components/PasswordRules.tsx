import { checkPassword, PASSWORD_MAX_LENGTH } from '../auth/passwordRules'

/**
 * Live checklist of the password rules (same rules as the backend @StrongPassword), shown under
 * a new password field. Used by Sign up (F1-06) and the change password form of My Profile (F1-08).
 * Only the three rules people need to know are listed (at least 8 characters, 1 letter, 1 number), with a check mark
 * as each one is met, like most sign up forms; there is no strength label because the rules are not a strength score.
 * The 64 character and 72 byte limits (BCrypt) appear only when a password breaks them, as error lines.
 */
export function PasswordRules({ id, password }: { id: string; password: string }) {
  const rules = checkPassword(password)
  return (
    <div className="password-rules" id={id}>
      <ul className="password-rules__list">
        {rules.filter((rule) => rule.id !== 'bytes' || (password !== '' && !rule.met)).map((rule) => (
          <li
            key={rule.id}
            className={`password-rules__item${rule.met ? ' password-rules__item--met' : ''}${rule.id === 'bytes' || (rule.id === 'length' && password.length > PASSWORD_MAX_LENGTH) ? ' password-rules__item--error' : ''}`}
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
