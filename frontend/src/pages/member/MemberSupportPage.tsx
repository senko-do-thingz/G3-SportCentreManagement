import { useCallback, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { memberSupportApi, memberSupportTopics, type MemberSupportIntake, type MemberSupportTicket } from '../../api/memberSupportApi'
import { tokenStorage } from '../../api/tokenStorage'
import { Button, Card, Input, PageHeader, Select, StatusBadge, TextArea } from '../../components/ui'
import { formatDateTime } from '../../utils/dateDisplay'
import { TrainingData } from './TrainingData'
import { useTrainingAction } from '../training/trainingFormState'
import { ActionMessage } from '../training/TrainingFormFields'
import './training.css'

const topicLabel = (value: string) => memberSupportTopics.find(topic => topic.value === value)?.label ?? value

/** Flow 6 F6-05 Contact Reception, S6-Submitted and S6-MemberReply: the Member's own support requests. */
export function MemberSupportPage() {
  const location = useLocation()
  const routeState = location.state as { excerpt?: unknown; question?: unknown } | null
  const excerpt = typeof routeState?.excerpt === 'string' ? routeState.excerpt : ''
  const initialQuestion = typeof routeState?.question === 'string' ? routeState.question : ''
  const [revision, refresh] = useState(0)
  const [selected, select] = useState<number | null>(null)
  const [submitted, setSubmitted] = useState<MemberSupportTicket | null>(null)
  const load = useCallback((signal: AbortSignal) => memberSupportApi.list(signal), [])
  return <div className="training-page member-support-page">
    <Link to="/member/assistant">Back to the center assistant</Link>
    <PageHeader eyebrow="Member workspace" title="Contact reception" description="Send a question that needs a staff member's help. Reception replies here; replies are not instant." />
    {submitted ? <Card title="Support request submitted">
      <p role="status">Your request {submitted.code} was sent to reception. Status: {submitted.status.replaceAll('_', ' ')}.</p>
      <p>Reception will reply in My support requests below. Sending a request does not book, cancel, pay or change any package.</p>
      <Button variant="secondary" onClick={() => setSubmitted(null)}>Send another request</Button>
    </Card> : <SupportForm excerpt={excerpt} initialQuestion={initialQuestion} onSubmitted={ticket => { setSubmitted(ticket); refresh(value => value + 1) }} />}
    <Card title="My support requests">
      <div id="my-requests" />
      <TrainingData key={revision} load={load} refreshLabel="Refresh my support requests">{tickets => tickets.length === 0
        ? <p role="status">You have not sent any support requests.</p>
        : <div className="member-support-table" role="region" aria-label="Support requests table" tabIndex={0}><table className="table"><caption>My support requests</caption>
          <thead><tr><th scope="col">Request</th><th scope="col">Topic</th><th scope="col">Status</th><th scope="col">Replies</th><th scope="col">Action</th></tr></thead>
          <tbody>{tickets.map(ticket => <tr key={ticket.id}>
            <td>{ticket.code}: {ticket.subject}</td><td>{topicLabel(ticket.category)}</td>
            <td><StatusBadge status={ticket.status} /></td>
            <td>{ticket.unreadReplies === 0 ? '-' : ticket.unreadReplies === 1 ? '1 new reply' : `${ticket.unreadReplies} new replies`}</td>
            <td><Button variant="secondary" onClick={() => select(ticket.id)}>View {ticket.code}</Button></td>
          </tr>)}</tbody></table></div>}</TrainingData>
    </Card>
    {selected !== null && <RequestDetail key={selected} id={selected} onRead={() => refresh(value => value + 1)} />}
  </div>
}

function SupportForm({ excerpt, initialQuestion = '', onSubmitted }: { excerpt: string; initialQuestion?: string; onSubmitted: (ticket: MemberSupportTicket) => void }) {
  const [category, setCategory] = useState<string>(excerpt ? 'MEMBERSHIP' : 'GENERAL')
  const [subject, setSubject] = useState('')
  const [description, setDescription] = useState(() => initialQuestion ? initialQuestion.slice(0, 1000) : '')
  const [includeExcerpt, setIncludeExcerpt] = useState(excerpt.length > 0)
  const intent = useRef<{ serialized: string; key: string } | null>(null)
  const action = useTrainingAction()
  function submit(event: FormEvent) {
    event.preventDefault()
    void action.run(async () => {
      if (!subject.trim() || !description.trim()) throw new Error('Provide a subject and describe the issue.')
      const body: MemberSupportIntake = { category, subject: subject.trim(), description: description.trim(),
        ...(includeExcerpt && excerpt ? { assistantExcerpt: excerpt } : {}) }
      const serialized = JSON.stringify(body)
      // The same content keeps the same key, so a retry after a network error cannot create a second request.
      if (intent.current?.serialized !== serialized) intent.current = { serialized, key: crypto.randomUUID() }
      const epoch = tokenStorage.getEpoch()
      const ticket = await memberSupportApi.create(body, intent.current.key)
      if (epoch !== tokenStorage.getEpoch()) return
      intent.current = null
      setSubject(''); setDescription('')
      onSubmitted(ticket)
    }, 'Support request sent.')
  }
  return <Card title="Support request">
    <form className="training-form" onSubmit={submit}>
      <Select label="Topic" options={[...memberSupportTopics]} value={category} onChange={event => setCategory(event.target.value)} />
      <Input label="Subject" required maxLength={200} value={subject} onChange={event => setSubject(event.target.value)} />
      <TextArea label="Describe the issue" required maxLength={1000} value={description} onChange={event => setDescription(event.target.value)} />
      {excerpt && <>
        <label><input type="checkbox" checked={includeExcerpt} onChange={event => setIncludeExcerpt(event.target.checked)} /> Include the assistant answer with my request</label>
        {includeExcerpt && <pre className="training-excerpt">{excerpt}</pre>}
      </>}
      <p>What happens next: reception receives your question and you get a request reference. Replies appear in My support requests. You can have up to 5 open requests at a time.</p>
      <ActionMessage {...action} />
      <Button type="submit" loading={action.pending}>Submit request</Button>
    </form>
  </Card>
}

function RequestDetail({ id, onRead }: { id: number; onRead: () => void }) {
  const marked = useRef(false)
  const load = useCallback(async (signal: AbortSignal) => {
    const ticket = await memberSupportApi.detail(id, signal)
    if (ticket.unreadReplies > 0 && !marked.current) {
      marked.current = true
      await memberSupportApi.markRead(id)
      onRead()
    }
    return ticket
  }, [id, onRead])
  return <TrainingData load={load} refreshLabel="Refresh request">{(ticket, refresh) => <Card title={`Request ${ticket.code}`}>
    <p><StatusBadge status={ticket.status} /> {topicLabel(ticket.category)}{ticket.sessionCode ? ` - session ${ticket.sessionCode}` : ''}</p>
    <h2>{ticket.subject}</h2>
    <p className="training-excerpt">{ticket.description}</p>
    <h3>Replies from reception</h3>
    {ticket.replies.length === 0 ? <p role="status">No reply yet. Reception will answer here.</p>
      : <ul className="training-list">{ticket.replies.map((reply, index) => <li key={index}>
        <p><strong>{reply.fromMe ? 'You' : 'Reception'}</strong> - {formatDateTime(reply.createdAt)}</p><p>{reply.message}</p></li>)}</ul>}
    {ticket.status === 'CLOSED'
      ? <p role="status">This request is closed. Send a new support request if you need more help.</p>
      : <FollowUpForm id={ticket.id} resolved={ticket.status === 'RESOLVED'} onSent={refresh} />}
  </Card>}</TrainingData>
}

/** Helpdesk follow-up: a message on a resolved request reopens it within 7 days of the resolution. */
function FollowUpForm({ id, resolved, onSent }: { id: number; resolved: boolean; onSent: () => void }) {
  const [message, setMessage] = useState('')
  const action = useTrainingAction()
  function submit(event: FormEvent) {
    event.preventDefault()
    void action.run(async () => {
      if (!message.trim()) throw new Error('Write a message for reception.')
      await memberSupportApi.reply(id, message.trim())
      setMessage('')
      onSent()
    }, resolved ? 'Message sent. Your request is open again.' : 'Message sent to reception.')
  }
  return <form className="training-form" onSubmit={submit}>
    <TextArea label={resolved ? 'Still need help? Reply to reopen' : 'Add a message'} required maxLength={2000} value={message} onChange={event => setMessage(event.target.value)} />
    {resolved && <p>Replying within 7 days of the resolution reopens this request. After that, send a new request.</p>}
    <ActionMessage {...action} />
    <Button type="submit" variant="secondary" loading={action.pending}>{resolved ? 'Reply and reopen' : 'Send message'}</Button>
  </form>
}
