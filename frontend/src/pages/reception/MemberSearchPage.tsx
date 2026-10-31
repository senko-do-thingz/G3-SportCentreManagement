import { useCallback, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { ApiError, splitFieldErrors } from '../../api/errors'
import type { MemberCard, PackageRegistration } from '../../api/memberApi'
import { isMemberNotFound, receptionApi, type CreatedMember, type MemberOverview, type MemberSummary } from '../../api/receptionApi'
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
    <Button onClick={() => setCreating(true)}>Create member</Button>
  )

  return (
    <div className="page reception-page">
      <PageHeader
        eyebrow="Front desk"
        title="Member Search and Profile"
        description="Find a member and use their existing profile."
        actions={createButton}
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
    </div>
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
        <p className="notice notice--info reception-side-note">
          Use an existing online request for payment. Do not create a second registration for the same request.
        </p>
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
  const [uncertain, setUncertain] = useState(false)

  const close = () => {
    if (saving) return
    if (created) {
      const member = created.member
      setCreated(null)
      setRevealed(false)
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
      <Modal key="created-member" open onClose={close} title="Member created" description="Give the temporary sign-in details to the member securely.">
        <p>Member code: {created.member.memberCode ?? '-'}</p>
        <p>The temporary password is available only in this dialog. Ask the member to change it after signing in.</p>
        <Input label="Temporary password" type={revealed ? 'text' : 'password'} value={created.temporaryPassword} readOnly autoComplete="off" />
        <Button variant="secondary" onClick={() => setRevealed((value) => !value)}>
          {revealed ? 'Hide temporary password' : 'Reveal temporary password'}
        </Button>
        <Button onClick={close}>Continue to member profile</Button>
      </Modal>
    )
  }

  return (
    <Modal
      open={open}
      onClose={close}
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
