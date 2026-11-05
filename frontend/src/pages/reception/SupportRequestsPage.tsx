import { useCallback, useRef, useState, type FormEvent } from 'react'
import { receptionSupportApi as api, supportCategories, supportStatuses, supportTransitions, type SupportIntake, type SupportStatus, type SupportTicket } from '../../api/receptionSupportApi'
import { receptionApi } from '../../api/receptionApi'
import { tokenStorage } from '../../api/tokenStorage'
import { Button, Card, Input, PageHeader, Select, TextArea } from '../../components/ui'
import { TrainingData } from '../member/TrainingData'
import { useTrainingAction } from '../training/trainingFormState'
import { ActionMessage } from '../training/TrainingFormFields'
import '../member/training.css'

const options = (values: readonly string[]) => values.map(value => ({ value, label: value.replaceAll('_', ' ') }))

export function SupportRequestsPage() {
  const [memberId, setMemberId] = useState('')
  const [status, setStatus] = useState('')
  const [selected, select] = useState<number | null>(null)
  const [revision, refresh] = useState(0)
  const load = useCallback((signal: AbortSignal) => api.list(memberId, status, signal), [memberId, status])
  const detail = useCallback((signal: AbortSignal) => api.detail(selected!, signal), [selected])
  return <div className="training-page">
    <PageHeader title="Support requests" description="Record member issues and outcomes. Requests do not change payments or packages automatically." />
    <Card title="Support inbox">
      <div className="training-form-grid">
        <Input label="Filter member ID" type="number" min="1" value={memberId} onChange={event => { setMemberId(event.target.value); select(null) }} />
        <Select label="Status filter" placeholder="All statuses" options={options(supportStatuses)} value={status} onChange={event => { setStatus(event.target.value); select(null) }} />
      </div>
      <TrainingData key={revision} load={load}>{tickets => <>
        <p>Showing up to 100 newest matching requests.</p>
        {tickets.length === 0 ? <p role="status">No support requests match these filters.</p> : <table className="table"><caption>Member support requests</caption><thead><tr><th>Request</th><th>Member</th><th>Category</th><th>Status</th><th>Action</th></tr></thead><tbody>{tickets.map(ticket => <tr key={ticket.id}><td>{ticket.code}: {ticket.subject}</td><td>{ticket.memberName}</td><td>{ticket.category}</td><td>{ticket.status}</td><td><Button variant="secondary" onClick={() => select(ticket.id)}>View {ticket.code}</Button></td></tr>)}</tbody></table>}
      </>}</TrainingData>
    </Card>
    {selected !== null && <TrainingData key={`${selected}-${revision}`} load={detail} refreshLabel="Refresh request details">{ticket => <TicketDetail ticket={ticket} onSaved={() => refresh(value => value + 1)} />}</TrainingData>}
    <CreateTicket onSaved={ticket => { select(ticket.id); refresh(value => value + 1) }} />
  </div>
}

function CreateTicket({ onSaved }: { onSaved: (ticket: SupportTicket) => void }) {
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
  return <Card title="New support request">
    <form onSubmit={event => { event.preventDefault(); setMember(''); setQuery(search.trim()) }}>
      <Input label="Search existing member" value={search} onChange={event => setSearch(event.target.value)} />
      <Button type="submit" variant="secondary">Search members</Button>
    </form>
    <form onSubmit={submit} className="training-form">
      <TrainingData load={members}>{page => <Select label="Member" required placeholder="Select a member" options={page.content.map(item => ({ value: String(item.userId), label: `${item.memberCode ?? item.userId} - ${item.fullName}` }))} value={member} onChange={event => setMember(event.target.value)} />}</TrainingData>
      <p>Search by name, phone or email. Refine the search if the first page does not contain the member.</p>
      <Select label="Category" options={options(supportCategories)} value={category} onChange={event => setCategory(event.target.value)} />
      <Input label="Subject" required maxLength={200} value={subject} onChange={event => setSubject(event.target.value)} />
      <TextArea label="Issue details" required maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} />
      <ActionMessage {...action} />
      <Button type="submit" loading={action.pending}>Create request</Button>
    </form>
  </Card>
}

function TicketDetail({ ticket, onSaved }: { ticket: SupportTicket; onSaved: () => void }) {
  const transitions = supportTransitions[ticket.status]
  const [status, setStatus] = useState<SupportStatus>(transitions[0] ?? ticket.status)
  const [notes, setNotes] = useState(ticket.resolutionNotes ?? '')
  const action = useTrainingAction()
  return <Card title={`Request ${ticket.code}`}>
    <p>{ticket.memberName} - {ticket.subject}</p><p>{ticket.description}</p>
    <p>Current status: {ticket.status}</p><p>{ticket.resolutionNotes}</p>
    {transitions.length > 0 ? <form onSubmit={event => {
      event.preventDefault()
      void action.run(async () => {
        if ((status === 'RESOLVED' || status === 'CLOSED') && notes.trim().length < 3) throw new Error('Provide resolution notes of at least 3 characters.')
        const epoch = tokenStorage.getEpoch()
        await api.update(ticket.id, status, notes)
        if (epoch === tokenStorage.getEpoch()) onSaved()
      }, 'Request status saved.')
    }}>
      <Select label="Next status" options={options(transitions)} value={status} onChange={event => setStatus(event.target.value as SupportStatus)} />
      <TextArea label="Resolution notes" maxLength={2000} value={notes} onChange={event => setNotes(event.target.value)} />
      <ActionMessage {...action} /><Button type="submit" loading={action.pending}>Update status</Button>
    </form> : <p role="status">This request is closed.</p>}
  </Card>
}
