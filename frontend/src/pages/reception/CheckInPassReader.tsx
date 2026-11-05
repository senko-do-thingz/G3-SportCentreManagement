import { useRef, useState, type FormEvent } from 'react'
import { checkInPassApi } from '../../api/checkInPassApi'
import { getErrorMessage } from '../../api/errors'
import type { CheckInRecord } from '../../api/receptionApi'
import { Button, Card, Input, Notice } from '../../components/ui'

export function CheckInPassReader({ onRecorded }: { onRecorded: (record: CheckInRecord) => void }) {
  const [token, setToken] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const inFlight = useRef(false)
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (inFlight.current) return
    setError(null)
    if (!/^SCIN1\.[A-Za-z0-9_-]{43}$/.test(token.trim())) {
      setError('Scan or paste a Member check-in pass. Generate a new pass if it has expired.')
      return
    }
    inFlight.current = true
    setBusy(true)
    try {
      const record = await checkInPassApi.redeem(token)
      setToken('')
      onRecorded(record)
    } catch (failure) {
      setToken('')
      setError(getErrorMessage(failure))
    } finally {
      inFlight.current = false
      setBusy(false)
    }
  }
  return <Card title="Read Member check-in QR">
    <p>Use a hardware reader or paste the Member's short-lived arrival pass, then press Enter.</p>
    {error && <Notice tone="danger" live="assertive">{error}</Notice>}
    <form onSubmit={submit} aria-label="Member QR check-in">
      <Input label="Scanned arrival pass" autoComplete="off" spellCheck={false} maxLength={128} value={token} readOnly={busy} onChange={(event) => setToken(event.target.value)} />
      <Button type="submit" disabled={busy}>{busy ? 'Verifying pass...' : 'Verify and record arrival'}</Button>
    </form>
    <p>The manual Member code form remains available below.</p>
  </Card>
}
