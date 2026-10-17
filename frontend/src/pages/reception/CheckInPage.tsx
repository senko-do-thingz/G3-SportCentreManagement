import { useCallback, useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../../api/errors'
import { isMemberNotFound, MEMBER_NOT_FOUND_MESSAGE, receptionApi, type CheckInRecord } from '../../api/receptionApi'
import { Badge, Button, Card, Input, PageHeader, Table, type TableColumn } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { useResource } from '../../hooks/useResource'
import { formatTime } from '../../utils/format'
import {
  CHECK_IN_IDENTIFIER_MAX,
  checkInVerdict,
  memberSearchPath,
  receptionDate,
  registerPath,
  validateIdentifier,
} from './receptionModel'

/**
 * Check-in (Figma F1-15 with the Recorded and Rejected states): the desk types a member code or scans
 * the member card, POST /check-ins records the arrival and the backend decides ALLOWED or DENIED with
 * the reason. An unknown code (404) is shown as Rejected with "Member not found". GET /check-ins?date=
 * lists persisted check-ins for the centre calendar day. Failed loads show an error with Retry.
 */
const IDENTIFIER_ID = 'check-in-identifier'

/** Client-only feedback for an unknown code; never inserted into the persisted history. */
function memberNotFoundRecord(code: string): CheckInRecord {
  return {
    id: -1,
    memberId: null,
    memberName: code,
    bookingId: null,
    bookingCode: null,
    packageRegistrationId: null,
    checkedInAt: new Date().toISOString(),
    result: 'DENIED',
    denialReason: MEMBER_NOT_FOUND_MESSAGE,
    note: null,
  }
}

function focusIdentifier() {
  document.getElementById(IDENTIFIER_ID)?.focus()
}

export function CheckInPage() {
  const [params] = useSearchParams()
  const [identifier, setIdentifier] = useState(params.get('code') ?? '')
  const [note, setNote] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [requestError, setRequestError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const inFlight = useRef(false)
  const [last, setLast] = useState<{ record: CheckInRecord; identifier: string } | null>(null)

  const today = receptionDate(new Date())
  const loadToday = useCallback((signal: AbortSignal) => receptionApi.checkInsOn(today, { signal }), [today])
  const [todayList, reloadToday] = useResource(loadToday)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (inFlight.current) return
    const error = validateIdentifier(identifier)
    setFieldError(error)
    setRequestError(null)
    if (error) {
      focusIdentifier()
      return
    }
    const code = identifier.trim()
    setSaving(true)
    inFlight.current = true
    try {
      const record = await receptionApi.recordCheckIn({ identifier: code, note: note.trim() || null })
      setLast({ record, identifier: code })
      setIdentifier('')
      setNote('')
      reloadToday()
    } catch (failure) {
      setLast(null)
      if (isMemberNotFound(failure)) {
        // Rejected with the reason; the code stays in the field for correction. Any other 404 (for
        // example "Resource not found") is a technical error and goes to the notice below.
        const record = memberNotFoundRecord(code)
        setLast({ record, identifier: code })
      } else if (failure instanceof ApiError && failure.fieldErrors.identifier) {
        setFieldError(failure.fieldErrors.identifier)
      } else {
        setRequestError(getErrorMessage(failure))
      }
    } finally {
      inFlight.current = false
      setSaving(false)
      // Ready for the next scan.
      focusIdentifier()
    }
  }

  const columns: TableColumn<CheckInRecord>[] = [
    { key: 'checkedInAt', header: 'Time', width: '90px', render: (row) => formatTime(row.checkedInAt) },
    {
      key: 'member',
      header: 'Member',
      render: (row) => row.memberName ?? (row.memberId !== null ? `Member #${row.memberId}` : '-'),
    },
    { key: 'bookingCode', header: 'Booking', render: (row) => row.bookingCode ?? '-' },
    {
      key: 'result',
      header: 'Result',
      render: (row) =>
        checkInVerdict(row) === 'recorded' ? <Badge tone="success">Recorded</Badge> : <Badge tone="danger">Rejected</Badge>,
    },
    { key: 'denialReason', header: 'Reason', render: (row) => row.denialReason ?? '-' },
  ]

  return (
    <div className="page">
      <PageHeader
        eyebrow="Front desk"
        title="Check-in"
        description="Verify paid training access and the session reservation."
      />
      <div className="reception-checkin">
        <Card title="Record arrival">
          <form className="reception-form" onSubmit={submit} noValidate aria-label="Check-in">
            <div className="reception-form__grid">
              <Input
                id={IDENTIFIER_ID}
                label="Member code or card scan"
                hint="Scan the member card, or type the member code (for example MB-0248)."
                required
                autoFocus
                autoComplete="off"
                maxLength={CHECK_IN_IDENTIFIER_MAX}
                value={identifier}
                readOnly={saving}
                onChange={(event) => {
                  setIdentifier(event.target.value)
                  setFieldError(null)
                }}
                error={fieldError ?? undefined}
              />
              <Input
                label="Note"
                hint="Optional, kept with the check-in record."
                maxLength={255}
                value={note}
                disabled={saving}
                onChange={(event) => setNote(event.target.value)}
              />
            </div>
            {requestError && (
              <p className="notice notice--danger" role="alert">
                {requestError}
              </p>
            )}
            <div className="reception-form__actions">
              <Button type="submit" loading={saving}>
                Confirm check-in
              </Button>
            </div>
          </form>
          {last && <CheckInResult record={last.record} identifier={last.identifier} />}
          <p className="notice notice--info">
            Check-in records the arrival. Session usage and coach attendance must not deduct the same visit twice.
          </p>
        </Card>
        <Card
          title="Today's check-ins"
          actions={
            <Button variant="secondary" size="sm" onClick={reloadToday} disabled={todayList.status === 'loading'}>
              Refresh
            </Button>
          }
        >
          <Table
            caption="Today's check-ins"
            columns={columns}
            rows={todayList.status === 'ready' ? todayList.data : []}
            rowKey={(row) => row.id}
            loading={todayList.status === 'loading'}
            error={todayList.status === 'error' ? todayList.error : undefined}
            onRetry={reloadToday}
            emptyTitle="No check-in yet today"
          />
        </Card>
      </div>
    </div>
  )
}

function CheckInResult({ record, identifier }: { record: CheckInRecord; identifier: string }) {
  const recorded = checkInVerdict(record) === 'recorded'
  const who = record.memberName ?? identifier
  return (
    <section
      className={`checkin-result checkin-result--${recorded ? 'recorded' : 'rejected'}`}
      role="status"
      aria-label={recorded ? 'Check-in recorded' : 'Check-in rejected'}
    >
      <h3 className="checkin-result__title">{recorded ? 'Check-in recorded' : 'Check-in rejected'}</h3>
      {record.id < 0 && <p>No check-in was saved for this unknown code.</p>}
      <dl className="summary-list">
        <div>
          <dt>Member</dt>
          <dd>{who}</dd>
        </div>
        <div>
          <dt>Time</dt>
          <dd>{formatTime(record.checkedInAt)}</dd>
        </div>
        {record.bookingCode && (
          <div>
            <dt>Session reservation</dt>
            <dd>{record.bookingCode}</dd>
          </div>
        )}
        {!recorded && (
          <div>
            <dt>Reason</dt>
            <dd data-testid="denial-reason">{record.denialReason ?? 'The backend did not give a reason.'}</dd>
          </div>
        )}
      </dl>
      {!recorded && record.memberId === null && (
        <p className="checkin-result__hint">Check the code in the field above, or search the member by name.</p>
      )}
      {!recorded && record.memberId !== null && (
        <div className="reception-actions">
          <Link className={buttonClassName('secondary', 'sm')} to={registerPath({ memberId: record.memberId })}>
            Register or renew
          </Link>
          <Link className={buttonClassName('ghost', 'sm')} to={memberSearchPath(record.memberId)}>
            Open member profile
          </Link>
        </div>
      )}
    </section>
  )
}
