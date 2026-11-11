import { useCallback, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { ApiError, splitFieldErrors } from '../../api/errors'
import type { MemberCard, PackageRegistration } from '../../api/memberApi'
import { isMemberNotFound, receptionApi, type CreatedMember, type MemberOverview, type MemberSummary } from '../../api/receptionApi'
import { ReceptionCallout, ReceptionHero, ReceptionScreen } from './ReceptionConcept'
import { Button, Card, EmptyState, ErrorState, Input, Modal, PageHeader, StatusBadge } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { useResource } from '../../hooks/useResource'
import { formatDate, formatMoney, humanizeCode } from '../../utils/format'
import { currentCard, registrationTitle } from '../member/dashboardModel'
import { MemberHeader, MemberPicker } from './ReceptionMember'
import {
  afterPaymentReference,
  checkInPath,
  EMPTY_MEMBER_FORM,
  MEMBER_FULL_NAME_MAX,
  pendingCards,
  positiveId,
  receptionPaymentPath,
  receptionDate,
  registerPath,
  sortedRegistrations,
  validateMemberForm,
  type CreateMemberForm,
  type MemberFormErrors,
} from './receptionModel'

/**
 * Member Search and Profile (Figma F1-13): GET /members, GET /members/{id}/overview, the Create Member
 * overlay (POST /members) and the Payment Handoff overlay. The URL keeps the search (q) and the
 * selected member (memberId), so Back and Refresh work. The After Payment notice comes only from the
 * router state set by the confirmation screen, never from the URL.
 */
export function MemberSearchPage() {
  const [params, setParams] = useSearchParams()
  const query = params.get('q') ?? ''
  const memberId = positiveId(params.get('memberId'))
  const paidReference = afterPaymentReference(useLocation().state)
  const [creating, setCreating] = useState(false)

  const selectMember = useCallback(
    (member: MemberSummary, how: 'auto' | 'click') => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current)
          next.set('memberId', String(member.userId))
          return next
        },
        // The automatic single-result selection replaces the search entry, so Back skips it.
        { replace: how === 'auto' },
      )
    },
    [setParams],
  )

  const onSearch = useCallback(
    (nextQuery: string) => {
      setParams(new URLSearchParams({ q: nextQuery }))
    },
    [setParams],
  )

  const createButton = (
    <Button onClick={() => setCreating(true)}>
      <span aria-hidden="true" style={{ fontSize: '1.35rem', fontWeight: 800, marginRight: '4px', lineHeight: 1 }}>+</span>
      Create member
    </Button>
  )

  return (
    <ReceptionScreen className="reception-page reception-member-search-page">
      <PageHeader
        eyebrow="Receptionist workspace"
        title="Member Search and Profile"
        description="Find a member and use their existing profile."
        actions={createButton}
      />
      <ReceptionHero
        kicker="Front desk"
        title="Find the member."
        highlight="Use the existing profile."
        description="Search by member code, name, phone or email. Payments continue from the member's existing requests."
      />
      <Card title="Find member">
        {/* Keyed by the query so Back to an older search restores that search. */}
        <MemberPicker
          key={query}
          initialQuery={query}
          onSearch={onSearch}
          onSelect={selectMember}
          autoSelectSingle
          notFoundAction={
            <Button variant="secondary" onClick={() => setCreating(true)}>
              Create member
            </Button>
          }
        />
        {memberId !== null && <MemberOverviewPanel key={memberId} memberId={memberId} paidReference={paidReference} />}
      </Card>
      {creating && (
        <CreateMemberModal
          open
          onClose={() => setCreating(false)}
          onCreated={(member) => {
            setCreating(false)
            setParams(new URLSearchParams({ q: member.memberCode ?? member.fullName, memberId: String(member.userId) }))
          }}
        />
      )}
    </ReceptionScreen>
  )
}

function MemberOverviewPanel({ memberId, paidReference }: { memberId: number; paidReference: string | null }) {
  const load = useCallback((signal: AbortSignal) => receptionApi.memberOverview(memberId, { signal }), [memberId])
  const [overview, reload] = useResource(load)
  const [handoff, setHandoff] = useState<PackageRegistration | MemberCard | null>(null)

  if (overview.status === 'loading') {
    return <Spinner label="Loading the member profile" />
  }
  if (overview.status === 'error') {
    // Only the canonical missing-member 404 means an absent profile.
    if (isMemberNotFound(overview.error)) {
      return <EmptyState title="Member not found" description="This member profile does not exist any more." />
    }
    return <ErrorState title="We could not load the member profile" error={overview.error} onRetry={reload} />
  }
  return (
    <section className="member-overview" aria-label="Member profile">
      {paidReference && (
        <p className="notice notice--success">
          Returned from payment {paidReference}. Check the current service status below.
        </p>
      )}
      <OverviewBody data={overview.data} onHandoff={setHandoff} />
      <PaymentHandoffModal member={overview.data.member} item={handoff} onClose={() => setHandoff(null)} />
    </section>
  )
}

function OverviewBody({
  data,
  onHandoff,
}: {
  data: MemberOverview
  onHandoff: (item: PackageRegistration | MemberCard) => void
}) {
  const today = receptionDate(new Date())
  const { member } = data
  const card = currentCard(data.cards, today)
  const waitingCards = pendingCards(data.cards)
  const registrations = sortedRegistrations(data.registrations)
  const discount = card?.discountPercentage ?? 0

  return (
    <>
      <MemberHeader member={member} />
      <div className="reception-grid">
        <div>
          <dl className="summary-list">
            <div>
              <dt>Membership card</dt>
              <dd>{card ? `${card.tierName} - ${humanizeCode(card.status)}` : 'No card in force'}</dd>
            </div>
            <div>
              <dt>Card expiry</dt>
              <dd>{card?.endDate ? formatDate(card.endDate) : '-'}</dd>
            </div>
            <div>
              <dt>Package discount</dt>
              <dd>{discount > 0 ? `${discount}% on eligible packages` : 'No discount'}</dd>
            </div>
          </dl>
          <div className="reception-actions">
            <Link className={buttonClassName('primary', 'md')} to={registerPath({ memberId: member.userId })}>
              Register or renew
            </Link>
            <Link className={buttonClassName('secondary', 'md')} to={checkInPath(member.memberCode ?? String(member.userId))}>
              Check-in
            </Link>
          </div>
        </div>
        <div className="reception-side-note">
          <ReceptionCallout>
            Use an existing online request for payment. Do not create a second registration for the same request.
          </ReceptionCallout>
        </div>
      </div>

      {waitingCards.length > 0 && (
        <>
          <h3 className="reception-subtitle">Card requests waiting for payment</h3>
          <ul className="package-list" aria-label="Card requests">
            {waitingCards.map((item) => (
              <li key={item.id} className="package-item" aria-label={`${item.cardCode} ${item.tierName}`}>
                <div className="package-item__head">
                  <div>
                    <p className="package-item__code">{item.cardCode}</p>
                    <p className="package-item__title">Membership card - {item.tierName}</p>
                    <p className="package-item__meta">{formatMoney(item.pricePaid)}</p>
                  </div>
                  <StatusBadge status={item.status} />
                </div>
                <div className="package-item__actions">
                  <Button onClick={() => onHandoff(item)}>Continue to payment</Button>
                </div>
              </li>
            ))}
          </ul>
        </>
      )}

      <h3 className="reception-subtitle">Sport registrations</h3>
      {registrations.length === 0 ? (
        <EmptyState
          title="No sport package yet"
          description="Register a package at the desk for this member."
          action={
            <Link className={buttonClassName('secondary', 'md')} to={registerPath({ memberId: member.userId })}>
              Register a package
            </Link>
          }
        />
      ) : (
        <ul className="package-list" aria-label="Sport registrations">
          {registrations.map((registration) => (
            <li
              key={registration.id}
              className="package-item"
              aria-label={`${registration.registrationCode} ${registrationTitle(registration)}`}
            >
              <div className="package-item__head">
                <div>
                  <p className="package-item__code">{registration.registrationCode}</p>
                  <p className="package-item__title">{registrationTitle(registration)}</p>
                  <p className="package-item__meta">
                    {[
                      registration.packageName,
                      registration.totalSessions !== null ? `${registration.totalSessions} sessions` : 'No session limit',
                      formatMoney(registration.paidAmount),
                    ].join(' - ')}
                  </p>
                </div>
                <StatusBadge status={registration.status} />
              </div>
              <dl className="summary-list">
                <div>
                  <dt>Validity</dt>
                  <dd>
                    {formatDate(registration.startDate)} to {formatDate(registration.endDate)}
                  </dd>
                </div>
                {registration.remainingSessions !== null && registration.status === 'ACTIVE' && (
                  <div>
                    <dt>Remaining sessions</dt>
                    <dd>{registration.remainingSessions}</dd>
                  </div>
                )}
              </dl>
              <div className="package-item__actions">
                {registration.status === 'PENDING_PAYMENT' ? (
                  <Button onClick={() => onHandoff(registration)}>Continue to payment</Button>
                ) : (
                  registration.status !== 'CANCELLED' && (
                    <Link
                      className={buttonClassName('secondary', 'md')}
                      to={registerPath({ memberId: member.userId, packageId: registration.packageId })}
                    >
                      Renew package
                    </Link>
                  )
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

function isCard(item: PackageRegistration | MemberCard): item is MemberCard {
  return 'cardCode' in item
}

/** Payment Handoff overlay (F1-13): reviews an online request before the desk records its payment. */
function PaymentHandoffModal({
  member,
  item,
  onClose,
}: {
  member: MemberSummary
  item: PackageRegistration | MemberCard | null
  onClose: () => void
}) {
  if (!item) {
    return null
  }
  const card = isCard(item)
  const code = card ? item.cardCode : item.registrationCode
  const amount = card ? item.pricePaid : item.paidAmount
  const original = card ? item.pricePaid : item.originalPrice
  const discount = Math.max(0, Number(original ?? 0) - Number(amount ?? 0))
  const target =
    item.paymentId !== null
      ? receptionPaymentPath(item.paymentId, member.userId)
      : null

  return (
    <Modal
      open
      onClose={onClose}
      title="Review payment request"
      className="reception-member-search__modal"
      description={`${member.fullName} - ${code}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Return to member profile
          </Button>
          {target ? (
            <Link className={buttonClassName('primary', 'md')} to={target}>
              Open payment confirmation
            </Link>
          ) : (
            <Button disabled>Open payment confirmation</Button>
          )}
        </>
      }
    >
      <dl className="summary-list">
        <div>
          <dt>Service</dt>
          <dd>{card ? `Membership card - ${item.tierName}` : registrationTitle(item)}</dd>
        </div>
        {!card && (
          <div>
            <dt>Package</dt>
            <dd>{item.packageName}</dd>
          </div>
        )}
        <div>
          <dt>Start date</dt>
          <dd>{formatDate(item.startDate)}</dd>
        </div>
        <div>
          <dt>Original price</dt>
          <dd>{formatMoney(original)}</dd>
        </div>
        <div>
          <dt>Discount</dt>
          <dd>{formatMoney(discount)}</dd>
        </div>
        <div className="summary-list__total">
          <dt>Amount payable</dt>
          <dd>{formatMoney(amount)}</dd>
        </div>
        <div>
          <dt>Payment reference</dt>
          <dd>{item.paymentCode ?? '-'}</dd>
        </div>
      </dl>
      <p className="notice notice--info">
        {target
          ? 'Record the payment and issue the receipt on the confirmation screen. Do not activate until the payment is received.'
          : 'No payment is linked to this request yet. Ask a manager to check the request before taking money.'}
      </p>
    </Modal>
  )
}

const MEMBER_FIELDS = ['fullName', 'phone', 'email'] as const

/** Create Member overlay (F1-13): POST /members. */
function CreateMemberModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  onCreated: (member: MemberSummary) => void
}) {
  const [form, setForm] = useState<CreateMemberForm>(EMPTY_MEMBER_FORM)
  const [errors, setErrors] = useState<MemberFormErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const inFlight = useRef(false)
  const [created, setCreated] = useState<CreatedMember | null>(null)
  const [revealed, setRevealed] = useState(false)
  const [copied, setCopied] = useState(false)
  const [copiedCode, setCopiedCode] = useState(false)
  const [uncertain, setUncertain] = useState(false)

  const close = () => {
    if (saving) return
    if (created) {
      const member = created.member
      setCreated(null)
      setRevealed(false)
      setCopied(false)
      setCopiedCode(false)
      onCreated(member)
      return
    }
    setForm(EMPTY_MEMBER_FORM)
    setErrors({})
    setFormError(null)
    onClose()
  }

  const update = (field: keyof CreateMemberForm) => (value: string) => {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (inFlight.current || uncertain) return
    const found = validateMemberForm(form)
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length > 0) {
      return
    }
    setSaving(true)
    inFlight.current = true
    try {
      const result = await receptionApi.createMember({
        fullName: form.fullName.trim(),
        phone: form.phone.trim(),
        email: form.email.trim(),
      })
      setCreated(result)
      setForm(EMPTY_MEMBER_FORM)
    } catch (error) {
      setSaving(false)
      if (error instanceof ApiError && error.hasFieldErrors) {
        const split = splitFieldErrors(error.fieldErrors, MEMBER_FIELDS)
        setErrors(split.fields)
        setFormError(split.formMessage)
      } else if (error instanceof ApiError && error.status === 409) {
        setFormError(`${error.message} Search for the existing profile instead of creating a second one.`)
      } else if (error instanceof ApiError && error.status > 0 && error.status < 500) {
        setFormError(error.message)
      } else {
        setUncertain(true)
        setFormError('Creation could not be verified. Close this dialog and search for the member before trying again.')
      }
    } finally {
      inFlight.current = false
      setSaving(false)
    }
  }

  if (created) {
    return (
      <Modal
        key="created-member"
        open
        onClose={close}
        title="Member created"
        description="Give the temporary sign-in details to the member securely."
        className="reception-member-search__modal"
        footer={
          <Button
            variant="primary"
            onClick={close}
            style={{
              minHeight: '42px',
              padding: '0 24px',
              fontSize: '0.95rem',
              fontWeight: 700,
              borderRadius: '10px',
              background: 'linear-gradient(135deg, #1f4fd8 0%, #163bb0 100%)',
              color: '#ffffff',
              border: 'none',
              boxShadow: '0 4px 14px rgba(31, 79, 216, 0.25)',
              cursor: 'pointer',
            }}
          >
            Continue to member profile
          </Button>
        }
      >
        {/* High-visibility Check-in ID Card */}
        <div
          style={{
            background: 'linear-gradient(135deg, #0b1736 0%, #152e69 100%)',
            borderRadius: '12px',
            padding: '16px 20px',
            marginBottom: '16px',
            color: '#ffffff',
            boxShadow: '0 6px 18px rgba(11, 23, 54, 0.18)',
            border: '1px solid #1f3a7a',
            position: 'relative',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '10px' }}>
            <div>
              <span
                style={{
                  display: 'inline-block',
                  fontSize: '0.72rem',
                  fontWeight: 800,
                  letterSpacing: '0.12em',
                  color: '#ffc83d',
                  textTransform: 'uppercase',
                  marginBottom: '2px',
                }}
              >
                CHECK-IN ID (MÃ HỘI VIÊN)
              </span>
              <div
                style={{
                  fontSize: '1.75rem',
                  fontWeight: 850,
                  fontFamily: 'Consolas, Monaco, monospace',
                  letterSpacing: '2px',
                  color: '#ffffff',
                  textShadow: '0 2px 4px rgba(0,0,0,0.3)',
                }}
              >
                {created.member.memberCode ?? `MEM-#${created.member.userId}`}
              </div>
            </div>

            <Button
              type="button"
              size="sm"
              variant="secondary"
              onClick={async () => {
                const codeToCopy = created.member.memberCode ?? String(created.member.userId)
                try {
                  await navigator.clipboard.writeText(codeToCopy)
                  setCopiedCode(true)
                  setTimeout(() => setCopiedCode(false), 2000)
                } catch {
                  /* clipboard fallback */
                }
              }}
              style={{
                fontSize: '0.8rem',
                fontWeight: 700,
                padding: '5px 12px',
                height: '32px',
                borderRadius: '8px',
                background: copiedCode ? '#10b981' : 'rgba(255, 255, 255, 0.16)',
                color: '#ffffff',
                border: '1px solid rgba(255, 255, 255, 0.3)',
                cursor: 'pointer',
              }}
            >
              {copiedCode ? 'Copied ID!' : 'Copy ID'}
            </Button>
          </div>

          <div
            style={{
              paddingTop: '8px',
              borderTop: '1px solid rgba(255, 255, 255, 0.15)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              fontSize: '0.86rem',
            }}
          >
            <div>
              <span style={{ color: '#94a3b8', fontSize: '0.78rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                Hội viên:{' '}
              </span>
              <strong style={{ color: '#f8fafc', fontWeight: 600 }}>{created.member.fullName}</strong>
            </div>
            {(created.member.phone || created.member.email) && (
              <span style={{ color: '#cbd5e1', fontSize: '0.82rem' }}>
                {created.member.phone || created.member.email}
              </span>
            )}
          </div>
        </div>

        <div style={{ marginBottom: '16px' }}>
          <Input
            label="Temporary password"
            type={revealed ? 'text' : 'password'}
            value={created.temporaryPassword}
            readOnly
            autoComplete="off"
            style={{
              fontFamily: 'Consolas, Monaco, monospace',
              fontSize: '1rem',
              letterSpacing: revealed ? '0.5px' : '3px',
              fontWeight: 600,
              background: '#f8fafc',
              border: '1px solid #cbd5e1',
              borderRadius: '8px',
              padding: '10px 14px',
              color: '#0f172a',
              width: '100%',
              boxSizing: 'border-box',
            }}
          />
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '8px' }}>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => setRevealed((value) => !value)}
              style={{
                fontSize: '0.82rem',
                padding: '4px 10px',
                height: '30px',
                color: '#1f4fd8',
                fontWeight: 600,
                borderRadius: '6px',
              }}
            >
              {revealed ? 'Hide temporary password' : 'Reveal temporary password'}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="secondary"
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(created.temporaryPassword)
                  setCopied(true)
                  setTimeout(() => setCopied(false), 2000)
                } catch {
                  /* clipboard fallback */
                }
              }}
              style={{
                fontSize: '0.82rem',
                padding: '4px 14px',
                height: '30px',
                fontWeight: 600,
                borderRadius: '6px',
                borderColor: copied ? '#86efac' : undefined,
                background: copied ? '#f0fdf4' : undefined,
                color: copied ? '#15803d' : undefined,
              }}
            >
              {copied ? 'Copied!' : 'Copy password'}
            </Button>
          </div>
        </div>

        <div
          style={{
            background: '#fffbeb',
            border: '1px solid #fef08a',
            borderRadius: '10px',
            padding: '12px 14px',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '10px',
          }}
        >
          <p style={{ fontSize: '0.84rem', color: '#854d0e', lineHeight: 1.5, margin: 0 }}>
            The temporary password is available only in this dialog. Ask the member to change it after signing in.
          </p>
        </div>
      </Modal>
    )
  }

  return (
    <Modal
      open={open}
      onClose={close}
      className="reception-member-search__modal"
      dismissible={!saving}
      size="lg"
      title="Create member"
      description="Check existing phone and email to avoid duplicate profiles."
    >
      <form className="reception-form" onSubmit={submit} noValidate aria-label="Create member">
        {formError && (
          <p className="notice notice--danger" role="alert">
            {formError}
          </p>
        )}
        <div className="reception-form__grid">
          <Input
            label="Full name"
            required
            maxLength={MEMBER_FULL_NAME_MAX}
            value={form.fullName}
            onChange={(event) => update('fullName')(event.target.value)}
            error={errors.fullName}
          />
          <Input
            label="Phone number"
            required
            inputMode="tel"
            maxLength={16}
            value={form.phone}
            onChange={(event) => update('phone')(event.target.value)}
            error={errors.phone}
          />
          <Input
            label="Email address"
            required
            type="email"
            maxLength={255}
            value={form.email}
            onChange={(event) => update('email')(event.target.value)}
            error={errors.email}
          />
        </div>
        <div className="reception-form__actions">
          <Button type="submit" loading={saving} disabled={uncertain}>
            Save member
          </Button>
          <Button variant="secondary" onClick={close} disabled={saving}>
            Cancel
          </Button>
        </div>
      </form>
    </Modal>
  )
}
