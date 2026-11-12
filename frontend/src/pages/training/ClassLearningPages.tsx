import { SessionContext } from '../../components/SessionContext'
import { useCallback, useRef, useState, type FormEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { classLearningApi as learning, type LearningClass } from '../../api/classLearningApi'
import { Button, Card, EmptyState, Input, PageHeader, Select } from '../../components/ui'
import { TrainingData } from '../member/TrainingData'
import { TrainingTextarea, ActionMessage } from './TrainingFormFields'
import { attendanceLabel, useTrainingAction } from './trainingFormState'
import { formatDateTime } from '../../utils/format'
import '../../styles/coachUi.css'

export function MemberClassesPage() {
  return <div className="training-page"><PageHeader title="My classes" description="Class information, your participation and conversations with your coach." />
    <TrainingData load={learning.classes}>{classes => classes.length ? <Card><ul className="training-list">{classes.map(item => <li key={item.id}><h2>{item.name}</h2><p>{item.sportName} - {item.level ?? 'All levels'} - Coach: {item.coachName ?? 'Not assigned'}</p><Link to={`/member/training/classes/${item.id}`}>View class: {item.name}</Link></li>)}</ul></Card> : <EmptyState title="No booked classes yet" action={<Link to="/member/classes">Find a class</Link>} />}</TrainingData></div>
}
export function MemberClassDetailPage() {
  const { id = '' } = useParams()
  const load = useCallback((signal: AbortSignal) => learning.detail(id, signal), [id])
  return <TrainingData load={load}>{({ learningClass: item, sessions }) => (
    <div className="training-page">
      <Link to="/member/training/classes">Back to my classes</Link>
      <PageHeader title={item.name} description={`${item.sportName} - ${item.level ?? 'All levels'}`} />
      <Card title="Class information">
        <dl className="class-facts">
          <div><dt>Coach</dt><dd>{item.coachName ?? 'Not assigned'}</dd></div>
          <div><dt>Capacity</dt><dd>{item.capacity}</dd></div>
          <div><dt>Class status</dt><dd>{item.active === false ? 'Inactive' : 'Active'}</dd></div>
        </dl>
        <Link to={`/member/training/questions?classId=${item.id}`}>Ask your coach about supplementary practice</Link>
      </Card>
      <Card title="My sessions and attendance">
        <ul className="training-list class-participation">{sessions.map(session => (
          <li key={session.bookingId}>
            <SessionContext {...session} sessionId={session.sessionCode} className={item.name} trainingType={session.trainingFormat} />
            <p>{session.trainingFormat === 'SELF_TRAINING' ? 'Self-training' : 'Coach-led'} - {session.sessionStatus} - {session.freeSeats} of {session.capacity} places available{session.freeSeats === 0 ? ' - Full' : ''}</p>
            <p><strong>{session.sessionStatus === 'CANCELLED' ? 'Session cancelled' : session.bookingStatus === 'CANCELLED' ? 'Booking cancelled' : attendanceLabel(session.attendanceStatus)}</strong></p>
            {session.notes && <p className="training-copy">{session.notes}</p>}
            <div className="training-actions">
              {item.active !== false && session.sessionStatus === 'PUBLISHED' && <Link to={`/member/sessions/${session.sessionCode ?? session.sessionId}`}>View session availability and booking rules</Link>}
              {session.resultId && <Link to={`/member/training/results/${session.resultId}`}>View session assessment</Link>}
            </div>
          </li>
        ))}</ul>
      </Card>
      <Link to="/member/training/plans">View assigned practice plans</Link>
    </div>
  )}</TrainingData>
}
export function ClassQuestionsPage({ coach = false }: { coach?: boolean }) {
  return coach ? <CoachMemberQuestionsPage /> : <MemberQuestionsPage />
}

function MemberQuestionsPage() {
  const [revision, refresh] = useState(0)
  const [selected, setSelected] = useState('')
  const [replyBody, setReplyBody] = useState('')
  const load = useCallback((signal: AbortSignal) => learning.questions(false, signal), [])
  const discussion = useCallback((signal: AbortSignal) => learning.discussion(false, selected, signal), [selected])
  const action = useTrainingAction()
  const operation = useSubmissionIdentity()
  function reply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const body = replyBody.trim()
    if (!body) return
    void action.run(async () => { await learning.reply(false, selected, body, operation.key([false, selected, body])); operation.complete(); setReplyBody(''); refresh(value => value + 1) }, 'Message sent.')
  }
  function selectQuestion(id: string) {
    setSelected(id)
    setReplyBody('')
    action.reset()
  }
  return <div className="training-page"><PageHeader title="Ask your coach" description="Discuss class exercises and supplementary practice with the assigned coach." />
    <TrainingData load={learning.classes}>{classes => <AskForm classes={classes} disabled={action.pending} onSaved={id => { selectQuestion(String(id)); refresh(value => value + 1) }} />}</TrainingData>
    <div className="training-actions"><Button variant="secondary" disabled={action.pending} onClick={() => { action.reset(); refresh(value => value + 1) }}>Refresh conversations</Button></div>
    <TrainingData key={revision} load={load}>{questions => questions.length ? <Card title="Conversations"><Select label="Class question" placeholder="Select a conversation" value={selected} disabled={action.pending} onChange={event => selectQuestion(event.target.value)} options={questions.map(q => ({ value: String(q.id), label: `${q.className} - ${q.memberName} - ${q.subject}` }))} /></Card> : <EmptyState title="No class questions yet" />}</TrainingData>
    {selected && <TrainingData key={`${selected}-${revision}`} load={discussion}>{data => <Card title={data.question.subject}><p>{data.question.description}</p><ul className="training-list">{data.messages.map(message => <li key={message.id}><strong>{message.senderName}</strong><p className="training-copy">{message.body}</p></li>)}</ul><form onSubmit={reply} className="training-form"><TrainingTextarea label="Follow-up question" name="body" required maxLength={2000} value={replyBody} onChange={event => setReplyBody(event.target.value)} disabled={action.pending} /><Button type="submit" disabled={!replyBody.trim()} loading={action.pending}>Send message</Button></form><ActionMessage {...action} /></Card>}</TrainingData>}
  </div>
}

type QuestionMetricIconName = 'queue' | 'conversation'

/** Decorative icons for the Member Questions summary only. */
function QuestionMetricIcon({ name }: { name: QuestionMetricIconName }) {
  return <svg
    className="coach-schedule-metric__icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    {name === 'queue' ? <>
      <polyline points="22 12 16 12 14 15 10 15 8 12 2 12" />
      <path d="M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
    </> : <>
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
      <line x1="8" y1="9" x2="16" y2="9" />
      <line x1="8" y1="13" x2="13" y2="13" />
    </>}
  </svg>
}

function CoachMemberQuestionsPage() {
  const [revision, refresh] = useState(0)
  const [selected, setSelected] = useState('')
  const [replyBody, setReplyBody] = useState('')
  const load = useCallback((signal: AbortSignal) => learning.questions(true, signal), [])
  const discussion = useCallback((signal: AbortSignal) => learning.discussion(true, selected, signal), [selected])
  const action = useTrainingAction()
  const operation = useSubmissionIdentity()

  function selectQuestion(id: string) {
    setSelected(id)
    setReplyBody('')
    action.reset()
  }

  function reply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const body = replyBody.trim()
    if (!body) return
    void action.run(async () => {
      await learning.reply(true, selected, body, operation.key([true, selected, body]))
      operation.complete()
      setReplyBody('')
      refresh(value => value + 1)
    }, 'Message sent.')
  }

  return <div className="training-page coach-ui coach-questions-page">
    <PageHeader eyebrow="Coach workspace" title="Member Questions" description="Review member questions and share clear practice guidance." />
    <section className="coach-schedule-hero" aria-label="Member questions introduction">
      <div className="coach-schedule-hero__copy">
        <p className="coach-schedule-hero__eyebrow">Sportify coach experience</p>
        <h2>Every question.<br /><span>A clear path forward.</span></h2>
        <p>Listen closely. Coach with clarity.</p>
      </div>
      <Button variant="accent" size="sm" className="coach-schedule-hero__action" disabled={action.pending} onClick={() => { action.reset(); refresh(value => value + 1) }}>Refresh conversations</Button>
    </section>
    <TrainingData key={revision} load={load}>{questions => {
      const selectedQuestion = questions.find(question => String(question.id) === selected)
      return <>
        <section className="coach-question-metrics" aria-label="Question queue summary">
          <div className="coach-schedule-metric"><QuestionMetricIcon name="queue" /><div><p>{questions.length}</p><span>Questions in queue</span></div></div>
          <div className="coach-schedule-metric"><QuestionMetricIcon name="conversation" /><div><p>{selected ? '1' : '0'}</p><span>Conversation selected</span></div></div>
        </section>
        <div className="coach-question-layout">
          <Card className="coach-question-inbox" title="Question inbox" actions={<span className="coach-question-count">{questions.length} {questions.length === 1 ? 'question' : 'questions'}</span>}>
            {questions.length ? <ul className="coach-question-list" aria-label="Member question queue">{questions.map(question => {
              const isSelected = String(question.id) === selected
              return <li key={question.id}><button className={`coach-question-item${isSelected ? ' is-selected' : ''}`} type="button" aria-pressed={isSelected} disabled={action.pending} onClick={() => selectQuestion(String(question.id))}>
                <span className="coach-question-item__meta"><span>{question.className}</span><span>{formatDateTime(question.createdAt)}</span></span>
                <strong>{question.subject}</strong>
                <span className="coach-question-item__member">{question.memberName}</span>
                <span className="coach-question-item__status">Status: {question.status}</span>
              </button></li>
            })}</ul> : <EmptyState title="No class questions yet" description="New questions from members will appear here." />}
          </Card>
          <Card className="coach-question-workspace" title="Conversation workspace">
            {selected ? <TrainingData key={`${selected}-${revision}`} load={discussion}>{data => <>
              <div className="coach-question-discussion-header">
                <p className="coach-schedule-hero__eyebrow">Question from {data.question.memberName}</p>
                <h3>{data.question.subject}</h3>
                <dl><div><dt>Class</dt><dd>{data.question.className}</dd></div><div><dt>Submitted</dt><dd>{formatDateTime(data.question.createdAt)}</dd></div><div><dt>Status</dt><dd>{data.question.status}</dd></div></dl>
              </div>
              <div className="coach-question-prompt"><p>Member question</p><div className="coach-question-message">{data.question.description}</div></div>
              {data.messages.length ? <ol className="coach-question-thread" aria-label="Conversation messages">{data.messages.map(message => <li key={message.id}>
                <div className="coach-question-thread__meta"><strong>{message.senderName}</strong><time dateTime={message.createdAt}>{formatDateTime(message.createdAt)}</time></div>
                <p>{message.body}</p>
              </li>)}</ol> : <p className="coach-question-thread-empty">No replies in this conversation yet.</p>}
              <form onSubmit={reply} className="coach-question-reply">
                <TrainingTextarea label="Coach reply and practice guidance" name="body" required maxLength={2000} value={replyBody} onChange={event => setReplyBody(event.target.value)} disabled={action.pending} />
                <div className="coach-question-reply__footer"><span>{replyBody.length} / 2000 characters</span><Button type="submit" disabled={!replyBody.trim() || action.pending} loading={action.pending}>Send message</Button></div>
                <ActionMessage {...action} />
              </form>
            </>}</TrainingData> : selectedQuestion ? <EmptyState title="Loading selected conversation" /> : <EmptyState title={questions.length ? 'Choose a question to review' : 'No class questions yet'} description={questions.length ? 'Select a member question to read the discussion and write a reply.' : 'The conversation workspace will be available when a member question arrives.'} />}
          </Card>
        </div>
      </>
    }}</TrainingData>
  </div>
}
function AskForm({ classes, onSaved, disabled }: { classes: LearningClass[]; onSaved: (id: number) => void; disabled: boolean }) {
  const [params] = useSearchParams()
  const action = useTrainingAction()
  const operation = useSubmissionIdentity()
  if (!classes.length) return null
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)
    const request = { classId: Number(values.get('classId')), subject: String(values.get('subject')).trim(), body: String(values.get('body')).trim() }
    void action.run(async () => { const created = await learning.ask(request, operation.key(request)); operation.complete(); form.reset(); onSaved(created.id) }, 'Question sent to your coach.')
  }
  return <Card title="New question"><form onSubmit={submit} className="training-form"><fieldset className="training-fieldset" disabled={action.pending || disabled}><Select label="Your class" name="classId" required defaultValue={params.get('classId') ?? ''} placeholder="Select your class" options={classes.map(c => ({ value: String(c.id), label: c.name }))} /><Input label="Question subject" name="subject" required maxLength={200} /><TrainingTextarea label="Practice question" name="body" required maxLength={2000} /><Button type="submit" loading={action.pending}>Ask coach</Button></fieldset></form><ActionMessage {...action} /></Card>
}
// Preserve the identity after an uncertain failure. Success or edited content starts a new operation.
function useSubmissionIdentity() {
  const pending = useRef<{ payload: string; id: string } | null>(null)
  return {
    key(payload: unknown) {
      const serialized = JSON.stringify(payload)
      if (pending.current?.payload !== serialized) pending.current = { payload: serialized, id: crypto.randomUUID() }
      return pending.current.id
    },
    complete() { pending.current = null },
  }
}
