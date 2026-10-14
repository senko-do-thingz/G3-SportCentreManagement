import { useAuth } from '../auth/useAuth'

/**
 * Notice shown at the top of every signed in layout (member and staff) after this tab followed
 * another tab of the browser to a different account. Dismissible; the auth context also clears it
 * on the next login, sign up, logout or account switch. The accessible name "Account switched"
 * tells it apart from other status regions on the page, such as loading spinners.
 */
export function AccountSwitchNotice() {
  const { accountSwitched, dismissAccountSwitch } = useAuth()
  if (!accountSwitched) {
    return null
  }
  return (
    <div className="notice notice--warning account-switch-notice" role="status" aria-label="Account switched">
      <p>
        You are now signed in as <strong>{accountSwitched.fullName}</strong> because another account signed in in
        this browser.
      </p>
      <button type="button" className="link-button" onClick={dismissAccountSwitch}>
        Dismiss
      </button>
    </div>
  )
}
