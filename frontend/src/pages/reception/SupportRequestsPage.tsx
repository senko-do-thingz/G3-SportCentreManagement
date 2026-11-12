import { useCallback, useRef, useState, type FormEvent } from 'react'
import { receptionSupportApi as api, supportCategories, supportStatuses, supportTransitions, type SupportIntake, type SupportStatus, type SupportTicket } from '../../api/receptionSupportApi'
import { receptionApi } from '../../api/receptionApi'
import { tokenStorage } from '../../api/tokenStorage'
import { Badge, Button, Input, PageHeader, Select, TextArea } from '../../components/ui'
import { formatCalendarDate, formatDateTime } from '../../utils/dateDisplay'
import { ReceptionScreen } from './ReceptionConcept'
import { TrainingData } from '../member/TrainingData'
import { useTrainingAction } from '../training/trainingFormState'
import { ActionMessage } from '../training/TrainingFormFields'
import '../member/training.css'

const options = (values: readonly string[]) => values.map(value => ({ value, label: value.replaceAll('_', ' ') }))
const statusTone = (status: SupportStatus) => status === 'OPEN' ? 'warning' : status === 'IN_PROGRESS' ? 'info' : status === 'RESOLVED' ? 'success' : 'neutral'
const label = (value: string) => value.replaceAll('_', ' ')

/** Flow 6 F6-06 Support Request Inbox and F6-07 Detail & Reply. */
export function SupportRequestsPage() {
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')
  const [selected, select] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [revision, refresh] = useState(0)
  const load = useCallback((signal: AbortSignal) => api.list('', status, signal, query), [status, query])
  const detail = useCallback((signal: AbortSignal) => api.detail(selected!, signal), [selected])

  if (selected !== null) {
    return <ReceptionScreen className="training-page rcp-support">
      <Button variant="ghost" size="sm" onClick={() => select(null)}>Back to requests</Button>
      <TrainingData key={`${selected}-${revision}`} load={detail} refreshLabel="Refresh request details">
        {ticket => <TicketDetail ticket={ticket} onSaved={() => refresh(value => value + 1)} />}
      </TrainingData>
    </ReceptionScreen>
  }

  return <ReceptionScreen className="training-page rcp-support">
    <PageHeader title="Support requests" actions={!creating && <Button onClick={() => setCreating(true)}>+ New request</Button>} />
    {creating && <CreateTicket onCancel={() => setCreating(false)} onSaved={ticket => { setCreating(false); select(ticket.id); refresh(value => value + 1) }} />}
    <form className="rcp-support__toolbar" onSubmit={event => { event.preventDefault(); setQuery(search) }}>
      <Input label="Find request code, member or subject" maxLength={100} value={search} onChange={event => setSearch(event.target.value)} />
      <Select label="Status filter" placeholder="All statuses" options={options(supportStatuses)} value={status} onChange={event => setStatus(event.target.value)} />
      <Button type="submit" variant="secondary">Search requests</Button>
    </form>
    <TrainingData key={revision} load={load}>{tickets => tickets.length === 0
      ? <p role="status">No support requests match these filters.</p>
      : <ul className="rcp-support__list" aria-label="Member support requests">{tickets.map(ticket => <li key={ticket.id}>
        <button type="button" className="rcp-support__row" aria-label={`View ${ticket.code}`} onClick={() => select(ticket.id)}>
          <span className="rcp-support__row-main"><strong>{ticket.code}</strong> {ticket.subject}</span>
          <span className="rcp-support__row-meta">{ticket.memberName}{ticket.createdAt ? ` · ${formatDateTime(ticket.createdAt)}` : ''}</span>
          <Badge tone={statusTone(ticket.status)}>{label(ticket.status)}</Badge>
        </button>
      </li>)}</ul>}
    </TrainingData>
  </ReceptionScreen>
}

function CreateTicket({ onSaved, onCancel }: { onSaved: (ticket: SupportTicket) => void; onCancel: () => void }) {
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')
  const [member, setMember] = useState('')
  const [category, setCategory] = useState('GENERAL')
  const [subject, setSubject] = useState('')
  const [description, setDescription] = useState('')
  const intent = useRef<{ serialized: string; key: string } | null>(null)
  const action = useTrainingAction()
  const members = useCallback((signal: AbortSignal) => receptionApi.searchMembers({ query, signal }), [query])
  function submit(event: FormEvent) {
    event.preventDefault()
    void action.run(async () => {
      if (!member || !subject.trim() || !description.trim()) throw new Error('Select a member and provide a subject and issue details.')
      const body: SupportIntake = { memberId: Number(member), category, subject: subject.trim(), description: description.trim() }
      const serialized = JSON.stringify(body)
      if (intent.current?.serialized !== serialized) intent.current = { serialized, key: crypto.randomUUID() }
      const epoch = tokenStorage.getEpoch()
      const ticket = await api.create(body, intent.current.key)
      if (epoch !== tokenStorage.getEpoch()) return
      intent.current = null
      setSubject(''); setDescription('')
      onSaved(ticket)
    }, 'Support request saved.')
  }
  return <section className="rcp-support__panel" aria-labelledby="new-support-request">
    <h2 id="new-support-request" className="rcp-support__heading">New request</h2>
    <form className="rcp-support__toolbar" onSubmit={event => { event.preventDefault(); setMember(''); setQuery(search.trim()) }}>
      <Input label="Search existing member" value={search} onChange={event => setSearch(event.target.value)} />
      <Button type="submit" variant="secondary">Search members</Button>
    </form>
    <form onSubmit={submit} className="training-form">
      <TrainingData load={members}>{page => <Select label="Member" required placeholder="Select a member" options={page.content.map(item => ({ value: String(item.userId), label: `${item.memberCode ?? item.userId} - ${item.fullName}` }))} value={member} onChange={event => setMember(event.target.value)} />}</TrainingData>
      <Select label="Category" options={options(supportCategories)} value={category} onChange={event => setCategory(event.target.value)} />
      <Input label="Subject" required maxLength={200} value={subject} onChange={event => setSubject(event.target.value)} />
      <TextArea label="Issue details" required maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} />
      <ActionMessage {...action} />
      <div className="rcp-support__actions">
        <Button type="submit" loading={action.pending}>Create request</Button>
        <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
      </div>
    </form>
  </section>
}

function TicketDetail({ ticket, onSaved }: { ticket: SupportTicket; onSaved: () => void }) {
  const replies = ticket.replies ?? []
  const active = ticket.status === 'OPEN' || ticket.status === 'IN_PROGRESS'
  return <section className="rcp-support__detail" aria-labelledby="support-request-title">
    <header className="rcp-support__detail-head">
      <h2 id="support-request-title" className="rcp-support__heading">Request {ticket.code}</h2>
      <Badge tone={statusTone(ticket.status)}>{label(ticket.status)}</Badge>
    </header>
    <p className="rcp-support__subject"><strong>{ticket.subject}</strong> · {ticket.memberName}{ticket.memberCode ? ` (${ticket.memberCode})` : ''}{ticket.sessionCode ? ` · session ${ticket.sessionCode}` : ''}</p>
    <ol className="rcp-support__thread" aria-label="Conversation">
      <li className="rcp-support__message rcp-support__message--member">
        <p className="rcp-support__message-meta">{ticket.memberSubmitted ? ticket.memberName : 'Recorded at the desk'}{ticket.createdAt ? ` · ${formatDateTime(ticket.createdAt)}` : ''}</p>
        <p>{ticket.description}</p>
      </li>
      {replies.map(reply => <li key={reply.id} className={'rcp-support__message' + (reply.fromMember ? ' rcp-support__message--member' : '')}>
        <p className="rcp-support__message-meta">{reply.fromMember ? `Member: ${reply.staffName}` : reply.staffName} · {formatDateTime(reply.createdAt)}</p>
        <p>{reply.message}</p>
      </li>)}
    </ol>
    {active && <ReplyForm ticket={ticket} onSaved={onSaved} />}
    <StatusForm ticket={ticket} onSaved={onSaved} />
    <VerificationPanel ticketId={ticket.id} />
  </section>
}

function ReplyForm({ ticket, onSaved }: { ticket: SupportTicket; onSaved: () => void }) {
  const [message, setMessage] = useState('')
  const action = useTrainingAction()
  return <form className="rcp-support__reply" onSubmit={event => {
    event.preventDefault()
    void action.run(async () => {
      if (!message.trim()) throw new Error('Enter a reply for the member.')
      const epoch = tokenStorage.getEpoch()
      await api.reply(ticket.id, message)
      if (epoch !== tokenStorage.getEpoch()) return
      setMessage('')
      onSaved()
    }, 'Reply sent. The member is notified.')
  }}>
    <TextArea label="Reply to member" required maxLength={2000} value={message} onChange={event => setMessage(event.target.value)} />
    <ActionMessage {...action} />
    <Button type="submit" loading={action.pending}>Send reply</Button>
  </form>
}

function StatusForm({ ticket, onSaved }: { ticket: SupportTicket; onSaved: () => void }) {
  const transitions = supportTransitions[ticket.status]
  const [status, setStatus] = useState<SupportStatus>(transitions[0] ?? ticket.status)
  const [notes, setNotes] = useState(ticket.resolutionNotes ?? '')
  const [notesOpen, setNotesOpen] = useState(false)
  const action = useTrainingAction()
  const replies = ticket.replies ?? []
  if (transitions.length === 0) {
    return <details className="rcp-support__section">
      <summary>Internal outcome note</summary>
      <p>{ticket.resolutionNotes || 'No internal note recorded.'}</p>
    </details>
  }
  return <form className="rcp-support__status" onSubmit={event => {
    event.preventDefault()
    void action.run(async () => {
      if ((status === 'RESOLVED' || status === 'CLOSED') && notes.trim().length < 3) { setNotesOpen(true); throw new Error('Provide an internal outcome note of at least 3 characters.') }
      if (status === 'RESOLVED' && ticket.memberSubmitted && !replies.some(reply => !reply.fromMember)) throw new Error('Send a reply to the member before resolving a request they submitted.')
      const epoch = tokenStorage.getEpoch()
      await api.update(ticket.id, status, notes)
      if (epoch === tokenStorage.getEpoch()) onSaved()
    }, 'Request status saved.')
  }}>
    <div className="rcp-support__toolbar">
      <Select label="Next status" options={options(transitions)} value={status} onChange={event => {
        const next = event.target.value as SupportStatus
        setStatus(next)
        if (next === 'RESOLVED' || next === 'CLOSED') setNotesOpen(true)
      }} />
      <Button type="submit" variant="secondary" loading={action.pending}>Update status</Button>
    </div>
    <details className="rcp-support__section" open={notesOpen} onToggle={event => setNotesOpen(event.currentTarget.open)}>
      <summary>Internal outcome note (staff only)</summary>
      <TextArea label="Internal outcome note (staff only, never shown to the member)" maxLength={2000} value={notes} onChange={event => setNotes(event.target.value)} />
    </details>
    <ActionMessage {...action} />
  </form>
}

function VerificationPanel({ ticketId }: { ticketId: number }) {
  const [open, setOpen] = useState(false)
  const verification = useCallback((signal: AbortSignal) => api.verification(ticketId, signal), [ticketId])
  return <details className="rcp-support__section" open={open} onToggle={event => setOpen(event.currentTarget.open)}>
    <summary>Member verification</summary>
    {open && <TrainingData load={verification}>{facts => <div>
      <p>Member: {facts.memberName}{facts.memberCode ? ` / ${facts.memberCode}` : ''} - account {facts.memberStatus}</p>
      <p>Membership card: {facts.cardTier ? `${facts.cardTier}, ${facts.cardDiscount ?? 0}% discount${facts.cardEndDate ? `, valid until ${formatCalendarDate(facts.cardEndDate)}` : ''}` : 'No active card'}</p>
      {facts.activePackages.length === 0 ? <p>No active sport package.</p>
        : <ul className="training-list">{facts.activePackages.map((item, index) => <li key={index}>{item.sportName} - {item.name}: {item.remainingSessions} of {item.totalSessions} sessions, valid until {formatCalendarDate(item.endDate)}</li>)}</ul>}
      <p className="rcp-support__hint">For checking only. This does not record a payment or activate a package.</p>
    </div>}</TrainingData>}
  </details>
}
