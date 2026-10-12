import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { homePathFor } from '../auth/roles'
import { useAuth } from '../auth/useAuth'

/**
 * After this tab followed another tab to a different account, opens the home page of the new
 * user's role (replacing the history entry), so no page or form of the previous user stays on
 * screen and a member never lands on the 403 page of a manager URL. Runs once per switch.
 */
export function AccountSwitchRedirect() {
  const { accountSwitched, user } = useAuth()
  const navigate = useNavigate()
  const seq = accountSwitched?.seq ?? 0
  // Last switch already handled: later profile reloads of the same user never redirect again.
  const handledSeq = useRef(0)

  useEffect(() => {
    if (seq > handledSeq.current && user) {
      handledSeq.current = seq
      navigate(homePathFor(user), { replace: true })
    }
  }, [seq, user, navigate])

  return null
}
