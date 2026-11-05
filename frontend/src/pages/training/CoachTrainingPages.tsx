import { SessionContext } from '../../components/SessionContext'
import { sessionIdText, sessionRouteReference } from '../../api/sessionIdentity'
import { formatCalendarDate } from '../../utils/date'
import { useCallback, useState, type FormEvent } from 'react'
import { staffTrainingApi as training, type BookingCandidate, type StaffPlan, type StaffSession, type TrainingClass, type TrainingMetric, type SessionMember, type DrillDraft, type DrillSuggestions } from '../../api/staffTrainingApi'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { EmptyState } from '../../components/ui/EmptyState'
import { Input } from '../../components/ui/Input'
import { PageHeader } from '../../components/ui/PageHeader'
import { Select } from '../../components/ui/Select'
import { TrainingData } from '../member/TrainingData'
import { ActionMessage, TrainingTextarea } from './TrainingFormFields'
import { attendanceLabel, useTrainingAction } from './trainingFormState'
import '../member/training.css'

export function CoachTrainingPlansPage() {
  return (
    <div className="training-page">
      <PageHeader title="Assign plans and feedback" description="Assign a personal plan using a confirmed booking in a class you coach. Members can view plans and feedback as soon as you save them." />
      <TrainingData load={training.coachClasses}>{classes => <CoachPlansWorkspace classes={classes} />}</TrainingData>
    </div>
  )
}

function CoachPlansWorkspace({ classes }: { classes: TrainingClass[] }) {
  const [classId, setClassId] = useState('')
  const [revision, refresh] = useState(0)
  const loadMembers = useCallback((signal: AbortSignal) => training.bookingCandidates(classId, signal), [classId])
  return (
    <>
      <Card title="Create a personal plan">
        <Select label="Assigned class" value={classId} onChange={event => setClassId(event.target.value)} placeholder="Select a class"
          options={classes.map(item => ({ value: String(item.id), label: item.name }))} />
        {!classes.length && <p>You have no assigned classes.</p>}
        {classId && <TrainingData load={loadMembers}>{members => <CreatePlanForm key={classId} classId={Number(classId)} members={members} onSaved={() => refresh(value => value + 1)} />}</TrainingData>}
      </Card>
      <Card title="Assigned plans">
        <TrainingData key={revision} load={training.plans}>{plans => <OwnPlans plans={plans} />}</TrainingData>
      </Card>
    </>
  )
}

const blankStep = () => ({ title: '', durationMinutes: '10', instructions: '' })
function CreatePlanForm({ classId, members, onSaved }: { classId: number; members: BookingCandidate[]; onSaved: () => void }) {
  const [steps, setSteps] = useState([blankStep()])
  const [bookingId, setBookingId] = useState('')
  const action = useTrainingAction()
  if (!members.length) return <EmptyState title="No confirmed bookings" description="A confirmed booking in this class is required to assign a plan." />

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)
    void action.run(async () => {
      const title = String(values.get('title')).trim()
      const candidate = members.find(member => member.bookingId === Number(values.get('bookingId')))
      if (!candidate) throw new Error('Select a booking for the member receiving this plan.')
      if (!title || steps.some(step => !step.title.trim() || !Number.isInteger(Number(step.durationMinutes)) || Number(step.durationMinutes) <= 0 || Number(step.durationMinutes) > 1440)) {
        throw new Error('Enter a plan title, exercise titles and durations between 1 and 1440 minutes.')
      }
      await training.createPlan({
        bookingId: candidate.bookingId, classId, memberId: candidate.memberId, title,
        description: String(values.get('description')).trim() || null,
        steps: steps.map(step => ({ title: step.title.trim(), durationMinutes: Number(step.durationMinutes), instructions: step.instructions.trim() || null })),
      })
      form.reset()
      setBookingId('')
      setSteps([blankStep()])
      onSaved()
    }, 'Plan assigned. The member can view it immediately.')
  }

  function updateStep(index: number, field: keyof ReturnType<typeof blankStep>, value: string) {
    setSteps(current => current.map((step, position) => position === index ? { ...step, [field]: value } : step))
  }

  return (
    <form className="training-form" onSubmit={submit}>
      <fieldset disabled={action.pending} className="training-fieldset">
        <Select label="Member booking" name="bookingId" required placeholder="Select a confirmed booking" value={bookingId} onChange={event => { setBookingId(event.target.value); setSteps([blankStep()]) }}
          options={members.map(member => ({ value: String(member.bookingId), label: `${member.memberName} - Booking #${member.bookingId} - ${formatCalendarDate(member.sessionDate)}` }))} />
        {bookingId && <CoachDrillDrafts key={bookingId} bookingId={Number(bookingId)} onUse={drafts => setSteps(drafts.map(draft => ({ title: draft.title, durationMinutes: String(draft.durationMinutes), instructions: draft.instructions })))} />}
        <Input label="Plan title" name="title" required maxLength={150} />
        <TrainingTextarea label="Plan description" name="description" maxLength={1000} />
        {steps.map((step, index) => (
          <fieldset key={index} className="training-step-form">
            <legend>Exercise {index + 1}</legend>
            <Input label={`Exercise title ${index + 1}`} value={step.title} required maxLength={150} onChange={event => updateStep(index, 'title', event.target.value)} />
            <Input label={`Exercise duration ${index + 1} (minutes)`} type="number" min={1} max={1440} step={1} required value={step.durationMinutes} onChange={event => updateStep(index, 'durationMinutes', event.target.value)} />
            <TrainingTextarea label={`Exercise instructions ${index + 1}`} value={step.instructions} maxLength={1000} onChange={event => updateStep(index, 'instructions', event.target.value)} />
            {steps.length > 1 && <Button variant="secondary" onClick={() => setSteps(current => current.filter((_, position) => position !== index))}>Remove exercise {index + 1}</Button>}
          </fieldset>
        ))}
        <div className="training-actions">
          <Button variant="secondary" disabled={steps.length >= 100} onClick={() => setSteps(current => [...current, blankStep()])}>Add exercise</Button>
          <Button type="submit" loading={action.pending}>Assign plan</Button>
        </div>
      </fieldset>
      <ActionMessage {...action} />
    </form>
  )
}

function CoachDrillDrafts({ bookingId, onUse }: { bookingId: number; onUse: (drafts: DrillDraft[]) => void }) {
  const [suggestions, setSuggestions] = useState<DrillSuggestions | null>(null)
  const [selected, setSelected] = useState<number[]>([])
  const action = useTrainingAction()
  return <div className="training-form"><h3>Supplementary drill drafts</h3>
    <p>RULE_BASED drafts use this booking's sport, level and available feedback. Nothing is assigned until you review the plan and select Assign plan.</p>
    <Button variant="secondary" loading={action.pending} onClick={() => { void action.run(async () => { setSuggestions(await training.drillSuggestions(bookingId)); setSelected([]) }, 'Drafts loaded for Coach review. Nothing assigned.') }}>Suggest practice drills</Button>
    <ActionMessage {...action} />
    {suggestions && (suggestions.drafts.length ? <><ul className="training-list">{suggestions.drafts.map((draft,index) => <li key={draft.title}>
      <label><input type="checkbox" checked={selected.includes(index)} onChange={event => setSelected(current => event.target.checked ? [...current,index] : current.filter(value => value !== index))} /> Select draft: {draft.title}</label>
      <p>{draft.goal} - {draft.level} - {draft.durationMinutes} minutes</p><p>{draft.instructions}</p><p>{draft.reason}</p>
    </li>)}</ul><Button variant="secondary" disabled={!selected.length} onClick={() => onUse(suggestions.drafts.filter((_,index) => selected.includes(index)))}>Review selected drafts in plan form</Button></>
      : <EmptyState title="No curated drafts for this sport and level" description="Create a Coach-authored plan using the exercise fields below." />)}
  </div>
}

function OwnPlans({ plans }: { plans: StaffPlan[] }) {
  const [planId, setPlanId] = useState('')
  const load = useCallback((signal: AbortSignal) => training.plan(planId, signal), [planId])
  if (!plans.length) return <EmptyState title="You have not assigned any plans yet" />
  return (
    <div className="training-form">
      <Select label="Assigned plans" value={planId} onChange={event => setPlanId(event.target.value)} placeholder="Select a plan to view and review"
        options={plans.map(plan => ({ value: String(plan.id), label: `${plan.title} - ${plan.memberName} - ${plan.className}` }))} />
      {planId && <TrainingData load={load}>{({ plan, steps }) => (
        <div className="training-form">
          <h3>{plan.title} - {plan.memberName}</h3>
          {plan.description && <p className="training-copy">{plan.description}</p>}
          <ol className="training-steps">{steps.map(step => <li key={step.id}><strong>{step.title} - {step.durationMinutes} minutes</strong><p className="training-copy">{step.instructions}</p></li>)}</ol>
          <PublishFeedbackForm key={plan.id} planId={plan.id} />
        </div>
      )}</TrainingData>}
    </div>
  )
}

function PublishFeedbackForm({ planId }: { planId: number }) {
  const action = useTrainingAction()
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)
    void action.run(async () => {
      const comments = String(values.get('comments')).trim()
      if (!comments) throw new Error('Enter feedback before submitting.')
      await training.feedback(planId, {
        rating: Number(values.get('rating')), comments,
        strengths: String(values.get('strengths')).trim() || null,
        areasForImprovement: String(values.get('areasForImprovement')).trim() || null,
      })
      form.reset()
    }, 'Plan feedback saved. The member can view it immediately.')
  }
  return (
    <form className="training-form" onSubmit={submit}>
      <h3>Plan feedback</h3>
      <fieldset className="training-fieldset" disabled={action.pending}>
        <Select label="Progress rating" name="rating" required defaultValue="4" options={[1, 2, 3, 4, 5].map(value => ({ value: String(value), label: `${value}/5` }))} />
        <TrainingTextarea label="Feedback" name="comments" required maxLength={1000} />
        <TrainingTextarea label="Strengths" name="strengths" maxLength={500} />
        <TrainingTextarea label="Areas for improvement" name="areasForImprovement" maxLength={500} />
        <Button type="submit" loading={action.pending}>Save feedback</Button>
      </fieldset>
      <ActionMessage {...action} />
    </form>
  )
}

export function CoachTrainingResultsPage() {
  return (
    <div className="training-page">
      <PageHeader title="Record training results" description="Record results for members marked Present or Late in sessions you coach. Results are visible as soon as you save them." />
      <TrainingData load={training.coachSessions}>{sessions => <CoachResultsWorkspace sessions={sessions} />}</TrainingData>
    </div>
  )
}

function CoachResultsWorkspace({ sessions }: { sessions: StaffSession[] }) {
  const [sessionId, setSessionId] = useState('')
  const session = sessions.find(item => sessionRouteReference(item) === sessionId)
  const load = useCallback(async (signal: AbortSignal) => {
    const [members, metrics] = await Promise.all([training.coachRoster(sessionId, signal), training.metrics(session!.sportId, signal)])
    return { members, metrics }
  }, [sessionId, session])
  return (
    <Card title="Select a session">
      <Select label="Assigned session" value={sessionId} onChange={event => setSessionId(event.target.value)} placeholder="Select a session"
        options={sessions.map(item => ({ value: sessionRouteReference(item), label: `${sessionIdText(item.sessionId)} - ${item.className} - ${formatCalendarDate(item.date)} - ${item.startTime.slice(0, 5)}` }))} />
      {!sessions.length && <p>You have no assigned sessions.</p>}
      {session && <><SessionContext {...session} /><TrainingData load={load}>{data => <ResultForm key={session.id} session={session} {...data} />}</TrainingData></>}
    </Card>
  )
}

function ResultForm({ session, members, metrics }: { session: StaffSession; members: SessionMember[]; metrics: TrainingMetric[] }) {
  const action = useTrainingAction()
  const [savedMemberIds, setSavedMemberIds] = useState<number[]>([])
  const saved = members.filter(member => member.resultId || savedMemberIds.includes(member.memberId))
  const eligible = members.filter(member => !member.resultId && !savedMemberIds.includes(member.memberId) && (member.status === 'PRESENT' || member.status === 'LATE'))
  if (!eligible.length) return <><ActionMessage {...action} /><EmptyState title={saved.length ? "Results already saved" : "No members eligible for results"} description={saved.length ? "All eligible members already have a result. Duplicate submissions are disabled." : "Record Present or Late attendance before saving results."} /></>
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)
    void action.run(async () => {
      const notes = String(values.get('notes')).trim()
      const scores = metrics.flatMap(metric => {
        const raw = String(values.get(`metric-${metric.id}`) ?? '').trim()
        if (!raw) return []
        const score = Number(raw)
        if (!Number.isFinite(score) || score < 0 || score > metric.maxScore) throw new Error(`The score for ${metric.name} must be between 0 and ${metric.maxScore}.`)
        return [{ metricId: metric.id, score }]
      })
      if (!notes && !scores.length) throw new Error('Enter result notes or at least one skill score.')
      const memberId = Number(values.get('memberId'))
      if (session.sessionId) await training.resultByCode(session.sessionId, { memberId, notes: notes || null, scores })
      else await training.result({ sessionId: session.id, memberId, notes: notes || null, scores })
      setSavedMemberIds(current => [...current, memberId])
      form.reset()
    }, 'Training results saved. The member can view them immediately.')
  }
  return (
    <form className="training-form" onSubmit={submit}>
      {saved.length > 0 && <p role="status">Results already saved for: {saved.map(member => member.memberName).join(', ')}</p>}
      <fieldset className="training-fieldset training-result-fields" disabled={action.pending}>
        <Select label="Member receiving results" name="memberId" required placeholder="Select a member"
          options={eligible.map(member => ({ value: String(member.memberId), label: `${member.memberName} - ${attendanceLabel(member.status)}` }))} />
        <TrainingTextarea label="Result notes" name="notes" maxLength={1000} required={!metrics.length} />
        {metrics.length ? metrics.map(metric => (
          <Input key={metric.id} label={`${metric.name} (0-${metric.maxScore})`} name={`metric-${metric.id}`} type="number" min={0} max={metric.maxScore} step="0.01" />
        )) : <p>No skill metrics are configured for this sport. You can still save result notes.</p>}
        <Button type="submit" loading={action.pending}>Save results</Button>
      </fieldset>
      <ActionMessage {...action} />
    </form>
  )
}
