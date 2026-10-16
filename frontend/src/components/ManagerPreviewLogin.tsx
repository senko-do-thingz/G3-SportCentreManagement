import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { getErrorMessage } from '../api/errors'
import { Button } from './ui'

/** Mounted only in an explicitly enabled development preview. */
export function ManagerPreviewLogin() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  async function open(role: 'manager' | 'member') {
    if (busy) return
    setBusy(true)
    setError(null)
    try {
      await login({ email: `${role}@preview.sportify.local`, password: 'preview' }, { remember: false })
      navigate('/manager', { replace: true })
    } catch (cause) { setError(getErrorMessage(cause)); setBusy(false) }
  }
  return <section className="manager-preview-login" aria-label="Frontend preview">
    <p><strong>FE-06 frontend preview</strong></p>
    <p className="manager-muted">Review manager screens with sample data. No backend account or password is needed.</p>
    {error && <p className="notice notice--danger" role="alert">{error}</p>}
    <div className="manager-row-actions"><Button variant="secondary" disabled={busy} onClick={() => void open('manager')}>Open manager preview</Button><Button variant="ghost" disabled={busy} onClick={() => void open('member')}>Check member access</Button></div>
  </section>
}
