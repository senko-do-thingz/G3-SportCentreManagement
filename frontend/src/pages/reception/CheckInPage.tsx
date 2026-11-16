import { Suspense, lazy, useCallback, useRef, useState, type FormEvent } from 'react'
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
import { ReceptionScreen, ReceptionStat, ReceptionStats } from './ReceptionConcept'

/**
 * Check-in (Figma F1-15 with the Recorded and Rejected states): the desk types a member code or scans
 * the member's fixed check-in QR (its content is the member code), POST /check-ins records the arrival and the backend decides ALLOWED or DENIED with
 * the reason. An unknown code (404) is shown as Rejected with "Member not found". GET /check-ins?date=
 * lists persisted check-ins for the centre calendar day. Failed loads show an error with Retry.
 */
const IDENTIFIER_ID = 'check-in-identifier'

// Chi tai thu vien quet QR (@zxing) khi le tan bam "Scan QR".
const CameraQrScanner = lazy(() => import('./CameraQrScanner').then((module) => ({ default: module.CameraQrScanner })))

/** Ma loi backend khi tai khoan hoi vien khong ACTIVE (CheckInServiceImpl). */
const ACCOUNT_INACTIVE_CODE = 'CHECK_IN_ACCOUNT_INACTIVE'
const CHECK_IN_SUCCESS_MESSAGE = 'Check-in successful - member may enter the centre'

/** Client-only feedback for a rejected scan that saved nothing; never inserted into the persisted history. */
function unsavedRejectionRecord(code: string, reason: string): CheckInRecord {
  return {
    id: -1,
    memberId: null,
    memberName: code,
    bookingId: null,
    bookingCode: null,
    packageRegistrationId: null,
    checkedInAt: new Date().toISOString(),
    result: 'DENIED',
    denialReason: reason,
    note: null,
  }
}

/** Loi nghiep vu "khong luu ban ghi" duoc hien nhu ket qua Rejected thay vi thong bao loi ky thuat. */
function unsavedRejectionReason(failure: unknown): string | null {
  if (isMemberNotFound(failure)) return MEMBER_NOT_FOUND_MESSAGE
  if (failure instanceof ApiError && failure.code === ACCOUNT_INACTIVE_CODE) return failure.message
  return null
}

type ArrivalSource = 'manual' | 'camera'

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
  const [last, setLast] = useState<{ record: CheckInRecord; identifier: string; source: ArrivalSource } | null>(null)
  const [scanning, setScanning] = useState(false)

  const today = receptionDate(new Date())
  const loadToday = useCallback((signal: AbortSignal) => receptionApi.checkInsOn(today, { signal }), [today])
  const [todayList, reloadToday] = useResource(loadToday)

  /**
   * Mot duong ghi nhan duy nhat cho ca nhap tay va quet camera: POST /check-ins hien co.
   * Tra ve true khi backend da luu ban ghi (ALLOWED hoac DENIED).
   */
  const recordArrival = useCallback(
    async (code: string, source: ArrivalSource, arrivalNote: string | null): Promise<boolean> => {
      setRequestError(null)
      try {
        const record = await receptionApi.recordCheckIn({ identifier: code, note: arrivalNote })
        setLast({ record, identifier: code, source })
        reloadToday()
        return true
      } catch (failure) {
        setLast(null)
        const reason = unsavedRejectionReason(failure)
        if (reason) {
          // Rejected with the reason. Any other 404 (for example "Resource not found") is a technical
          // error and goes to the notice below.
          setLast({ record: unsavedRejectionRecord(code, reason), identifier: code, source })
        } else if (source === 'manual' && failure instanceof ApiError && failure.fieldErrors.identifier) {
          setFieldError(failure.fieldErrors.identifier)
        } else {
          setRequestError(getErrorMessage(failure))
        }
        return false
      }
    },
    [reloadToday],
  )

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
      if (await recordArrival(code, 'manual', note.trim() || null)) {
        setIdentifier('')
        setNote('')
      }
    } finally {
      inFlight.current = false
      setSaving(false)
      // Ready for the next scan.
      focusIdentifier()
    }
  }

  const scan = useCallback(
    async (code: string) => {
      // Ma tu QR da duoc trim; dung chung khoa voi form nhap tay de khong gui hai yeu cau cung luc.
      if (inFlight.current || validateIdentifier(code)) return
      inFlight.current = true
      try {
        await recordArrival(code, 'camera', null)
      } finally {
        inFlight.current = false
      }
    },
    [recordArrival],
  )

  const closeScanner = () => {
    setScanning(false)
    focusIdentifier()
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
    <ReceptionScreen className="reception-page reception-ui18-page home-page rcp-checkin">
      <PageHeader
        eyebrow="Receptionist workspace"
        title="Check-in"
        description="Record member arrivals and review today's check-ins."
      />
      {todayList.status === 'ready' && (
        <ReceptionStats label="Today's check-in summary">
          <ReceptionStat mark="T" label="Arrivals logged today" value={todayList.data.length} />
          <ReceptionStat mark="OK" tone="success" label="Recorded today"
            value={todayList.data.filter((row) => checkInVerdict(row) === 'recorded').length} />
          <ReceptionStat mark="X" tone="danger" label="Rejected today"
            value={todayList.data.filter((row) => checkInVerdict(row) !== 'recorded').length} />
        </ReceptionStats>
      )}
      <div className="reception-checkin">
        <Card
          title="Record arrival"
          actions={
            !scanning && (
              <Button type="button" variant="secondary" size="sm" onClick={() => setScanning(true)}>
                Scan QR
              </Button>
            )
          }
        >
          {scanning && (
            <Suspense fallback={<p role="status">Loading the QR scanner...</p>}>
              <CameraQrScanner
                onScan={scan}
                onClose={closeScanner}
                result={
                  last?.source === 'camera' ? (
                    <CheckInResult record={last.record} identifier={last.identifier} />
                  ) : undefined
                }
              />
            </Suspense>
          )}
          <form className="reception-form" onSubmit={submit} noValidate aria-label="Check-in">
            <div className="reception-form__grid">
              <Input
                id={IDENTIFIER_ID}
                label="Member code or QR scan"
                hint="Scan the member check-in QR, or type the member code (for example MEM-0016)."
                placeholder="e.g. MEM-0016"
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
          {last && !(scanning && last.source === 'camera') && (
            <CheckInResult record={last.record} identifier={last.identifier} />
          )}
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
    </ReceptionScreen>
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
      {recorded && <p className="checkin-result__verdict">{CHECK_IN_SUCCESS_MESSAGE}</p>}
      {record.id < 0 && (
        <p>
          {record.denialReason === MEMBER_NOT_FOUND_MESSAGE
            ? 'No check-in was saved for this unknown code.'
            : 'No check-in was saved.'}
        </p>
      )}
      <dl className="summary-list">
        <div>
          <dt>Member</dt>
          <dd>{who}</dd>
        </div>
        {record.memberCode && (
          <div>
            <dt>Member code</dt>
            <dd>{record.memberCode}</dd>
          </div>
        )}
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
