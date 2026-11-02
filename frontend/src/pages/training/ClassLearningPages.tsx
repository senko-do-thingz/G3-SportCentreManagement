import { useCallback, useState, type FormEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { classLearningApi as learning, type LearningClass } from '../../api/classLearningApi'
import { Button, Card, EmptyState, Input, PageHeader, Select } from '../../components/ui'
import { TrainingData } from '../member/TrainingData'
import { TrainingTextarea, ActionMessage } from './TrainingFormFields'
import { attendanceLabel, useTrainingAction } from './trainingFormState'

export function MemberClassesPage() {
  return <div className="training-page"><PageHeader title="My classes" description="Class information, your participation and conversations with your coach." />
    <TrainingData load={learning.classes}>{classes => classes.length ? <Card><ul className="training-list">{classes.map(item => <li key={item.id}><h2>{item.name}</h2><p>{item.sportName} - {item.level ?? 'All levels'} - Coach: {item.coachName ?? 'Not assigned'}</p><Link to={`/member/training/classes/${item.id}`}>View class: {item.name}</Link></li>)}</ul></Card> : <EmptyState title="No booked classes yet" action={<Link to="/member/classes">Find a class</Link>} />}</TrainingData></div>
}
export function MemberClassDetailPage() {
  const { id = '' } = useParams()
  const load = useCallback((signal: AbortSignal) => learning.detail(id, signal), [id])
  return <TrainingData load={load}>{({ learningClass: item, sessions }) => <div className="training-page"><Link to="/member/training/classes">Back to my classes</Link><PageHeader title={item.name} description={`${item.sportName} - ${item.level ?? 'All levels'}`} /><Card title="Class information"><dl><dt>Coach</dt><dd>{item.coachName ?? 'Not assigned'}</dd><dt>Capacity</dt><dd>{item.capacity}</dd></dl><Link to={`/member/training/questions?classId=${item.id}`}>Ask your coach about supplementary practice</Link></Card>
    <Card title="My sessions and attendance"><ul className="training-list">{sessions.map(session => <li key={session.bookingId}><h3>{new Intl.DateTimeFormat('en-GB').format(new Date(`${session.date}T00:00:00`))} - {session.startTime.slice(0,5)}-{session.endTime.slice(0,5)}</h3><p>{session.bookingStatus === 'CANCELLED' ? 'Booking cancelled' : attendanceLabel(session.attendanceStatus)}</p>{session.notes && <p>{session.notes}</p>}{session.resultId && <Link to={`/member/training/results/${session.resultId}`}>View session assessment</Link>}</li>)}</ul></Card><Link to="/member/training/plans">View assigned practice plans</Link></div>}</TrainingData>
}
export function ClassQuestionsPage({ coach = false }: { coach?: boolean }) {
  const [revision, refresh] = useState(0)
  const [selected, setSelected] = useState('')
  const load = useCallback((signal: AbortSignal) => learning.questions(coach, signal), [coach])
  const discussion = useCallback((signal: AbortSignal) => learning.discussion(coach, selected, signal), [coach, selected])
  const action = useTrainingAction()
  function reply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const body = String(new FormData(form).get('body')).trim()
    void action.run(async () => { await learning.reply(coach, selected, body); form.reset(); refresh(value => value + 1) }, 'Message sent.')
  }
  return <div className="training-page"><PageHeader title={coach ? 'Member practice questions' : 'Ask your coach'} description="Discuss class exercises and supplementary practice with the assigned coach." />
    {!coach && <TrainingData load={learning.classes}>{classes => <AskForm classes={classes} onSaved={id => { setSelected(String(id)); refresh(value => value + 1) }} />}</TrainingData>}
    <TrainingData key={revision} load={load}>{questions => questions.length ? <Card title="Conversations"><Select label="Class question" placeholder="Select a conversation" value={selected} onChange={event => setSelected(event.target.value)} options={questions.map(q => ({ value: String(q.id), label: `${q.className} - ${q.memberName} - ${q.subject}` }))} /></Card> : <EmptyState title="No class questions yet" />}</TrainingData>
    {selected && <TrainingData key={`${selected}-${revision}`} load={discussion}>{data => <Card title={data.question.subject}><p>{data.question.description}</p><ul className="training-list">{data.messages.map(message => <li key={message.id}><strong>{message.senderName}</strong><p className="training-copy">{message.body}</p></li>)}</ul><form onSubmit={reply} className="training-form"><TrainingTextarea label={coach ? 'Coach reply and practice guidance' : 'Follow-up question'} name="body" required maxLength={2000} disabled={action.pending} /><Button type="submit" loading={action.pending}>Send message</Button></form><ActionMessage {...action} /></Card>}</TrainingData>}
  </div>
}
function AskForm({ classes, onSaved }: { classes: LearningClass[]; onSaved: (id: number) => void }) {
  const [params] = useSearchParams()
  const action = useTrainingAction()
  if (!classes.length) return null
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)
    void action.run(async () => { const created = await learning.ask({ classId: Number(values.get('classId')), subject: String(values.get('subject')).trim(), body: String(values.get('body')).trim() }); form.reset(); onSaved(created.id) }, 'Question sent to your coach.')
  }
  return <Card title="New question"><form onSubmit={submit} className="training-form"><fieldset className="training-fieldset" disabled={action.pending}><Select label="Your class" name="classId" required defaultValue={params.get('classId') ?? ''} placeholder="Select your class" options={classes.map(c => ({ value: String(c.id), label: c.name }))} /><Input label="Question subject" name="subject" required maxLength={200} /><TrainingTextarea label="Practice question" name="body" required maxLength={2000} /><Button type="submit" loading={action.pending}>Ask coach</Button></fieldset></form><ActionMessage {...action} /></Card>
}
