import { SessionContext } from '../../components/SessionContext'
import { sessionIdText, sessionRouteReference } from '../../api/sessionIdentity'
import { formatCalendarDate } from '../../utils/date'
import { useCallback, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { staffTrainingApi as training, type BookingCandidate, type StaffPlan, type StaffSession, type TrainingClass, type TrainingMetric, type SessionMember, type DrillDraft, type DrillSuggestions, type DrillOptions } from '../../api/staffTrainingApi'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { EmptyState } from '../../components/ui/EmptyState'
import { Input } from '../../components/ui/Input'
import { PageHeader } from '../../components/ui/PageHeader'
import { Select } from '../../components/ui/Select'
import { TrainingData } from '../member/TrainingData'
import { ActionMessage, TrainingTextarea } from './TrainingFormFields'
import { attendanceLabel, useTrainingAction } from './trainingFormState'
import { coachProgress } from '../../api/coachProgressApi'
import { GOAL_OPTIONS, LEVEL_OPTIONS } from '../member/profileValidation'
import '../member/training.css'
import '../../styles/coachUi.css'

export function CoachTrainingPlansPage() {
  return (
    <div className="training-page coach-ui">
      <PageHeader title="Assign plans and feedback" description="Assign a personal plan using a confirmed booking in a class you coach, or a plan for the whole class. Members can view plans and feedback as soon as you save them." />
      <TrainingData load={training.coachClasses}>{classes => <CoachPlansWorkspace classes={classes} />}</TrainingData>
    </div>
  )
}

type PlanScope = 'INDIVIDUAL' | 'CLASS'
function CoachPlansWorkspace({ classes }: { classes: TrainingClass[] }) {
  const [classId, setClassId] = useState('')
  const [scope, setScope] = useState<PlanScope>('INDIVIDUAL')
  const [revision, refresh] = useState(0)
  const loadMembers = useCallback((signal: AbortSignal) => training.bookingCandidates(classId, signal), [classId])
  return (
    <>
      <Card title={scope === 'CLASS' ? 'Create a class plan' : 'Create a personal plan'}>
        <Select label="Plan scope" value={scope} onChange={event => setScope(event.target.value as PlanScope)}
          options={[{ value: 'INDIVIDUAL', label: 'Individual member' }, { value: 'CLASS', label: 'Whole class' }]} />
        <Select label="Assigned class" value={classId} onChange={event => setClassId(event.target.value)} placeholder="Select a class"
          options={classes.map(item => ({ value: String(item.id), label: item.name }))} />
        {!classes.length && <p>You have no assigned classes.</p>}
        {classId && scope === 'INDIVIDUAL' && <TrainingData load={loadMembers}>{members => <CreatePlanForm key={classId} classId={Number(classId)} members={members} onSaved={() => refresh(value => value + 1)} />}</TrainingData>}
        {classId && scope === 'CLASS' && <CreateClassPlanForm key={classId} classId={Number(classId)} />}
      </Card>
      <Card title="Assigned plans">
        <TrainingData key={revision} load={training.plans}>{plans => <OwnPlans plans={plans} />}</TrainingData>
      </Card>
    </>
  )
}

type DraftStep = ReturnType<typeof blankStep>
const blankStep = () => ({ title: '', durationMinutes: '10', instructions: '' })
const draftSteps = (drafts: DrillDraft[]) => drafts.map(draft => ({ title: draft.title, durationMinutes: String(draft.durationMinutes), instructions: draft.instructions }))
const invalidSteps = (title: string, steps: DraftStep[]) => !title || steps.some(step => !step.title.trim() || !Number.isInteger(Number(step.durationMinutes)) || Number(step.durationMinutes) <= 0 || Number(step.durationMinutes) > 1440)
const stepBody = (steps: DraftStep[]) => steps.map(step => ({ title: step.title.trim(), durationMinutes: Number(step.durationMinutes), instructions: step.instructions.trim() || null }))

function StepFields({ steps, setSteps }: { steps: DraftStep[]; setSteps: (update: (current: DraftStep[]) => DraftStep[]) => void }) {
  function updateStep(index: number, field: keyof DraftStep, value: string) {
    setSteps(current => current.map((step, position) => position === index ? { ...step, [field]: value } : step))
  }
  return <>{steps.map((step, index) => (
    <fieldset key={index} className="training-step-form">
      <legend>Exercise {index + 1}</legend>
      <Input label={`Exercise title ${index + 1}`} value={step.title} required maxLength={150} onChange={event => updateStep(index, 'title', event.target.value)} />
      <Input label={`Exercise duration ${index + 1} (minutes)`} type="number" min={1} max={1440} step={1} required value={step.durationMinutes} onChange={event => updateStep(index, 'durationMinutes', event.target.value)} />
      <TrainingTextarea label={`Exercise instructions ${index + 1}`} value={step.instructions} maxLength={1000} onChange={event => updateStep(index, 'instructions', event.target.value)} />
      {steps.length > 1 && <Button variant="secondary" onClick={() => setSteps(current => current.filter((_, position) => position !== index))}>Remove exercise {index + 1}</Button>}
    </fieldset>
  ))}</>
}

function CreatePlanForm({ classId, members, onSaved }: { classId: number; members: BookingCandidate[]; onSaved: () => void }) {
  const [steps, setSteps] = useState([blankStep()])
  const [bookingId, setBookingId] = useState('')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const action = useTrainingAction()
  if (!members.length) return <EmptyState title="No confirmed bookings" description="A confirmed booking in this class is required to assign a plan." />

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    void action.run(async () => {
      const planTitle = title.trim()
      const candidate = members.find(member => member.bookingId === Number(values.get('bookingId')))
      if (!candidate) throw new Error('Select a booking for the member receiving this plan.')
      if (invalidSteps(planTitle, steps)) throw new Error('Enter a plan title, exercise titles and durations between 1 and 1440 minutes.')
      await training.createPlan({
        bookingId: candidate.bookingId, classId, memberId: candidate.memberId, title: planTitle,
        description: description.trim() || null, steps: stepBody(steps),
      })
      chooseBooking('')
      onSaved()
    }, 'Plan assigned. The member can view it immediately.')
  }

  // A new booking starts from an empty form so one Member's plan text is never reused for another.
  function chooseBooking(value: string) {
    setBookingId(value)
    setTitle('')
    setDescription('')
    setSteps([blankStep()])
  }

  const selectedMemberId = members.find(item => item.bookingId === Number(bookingId))?.memberId
  return (
    <form className="training-form" onSubmit={submit}>
      <fieldset disabled={action.pending} className="training-fieldset">
        <Select label="Member booking" name="bookingId" required placeholder="Select a confirmed booking" value={bookingId} onChange={event => chooseBooking(event.target.value)}
          options={members.map(member => ({ value: String(member.bookingId), label: `${member.memberName} - Booking #${member.bookingId} - ${formatCalendarDate(member.sessionDate)}` }))} />
        {bookingId && <Link className="btn btn--secondary" to={`/coach/training/progress?classId=${classId}&memberId=${selectedMemberId ?? ''}`}>View selected member progress</Link>}
        {bookingId && selectedMemberId !== undefined && <CoachDrillDrafts key={bookingId}
          intro="Drafts use this member's training goal, level, recorded results and your latest feedback. When AI drafting is unavailable, RULE_BASED drafts from the curated catalog are shown. Nothing is assigned until you review the plan and select Assign plan."
          context={<MemberTrainingContext classId={classId} memberId={selectedMemberId} />}
          load={options => training.drillSuggestions(Number(bookingId), options)}
          onUse={(drafts, adjustments) => { setSteps(draftSteps(drafts)); setDescription(current => withAdjustments(current, adjustments)) }} />}
        <Input label="Plan title" name="title" required maxLength={150} value={title} onChange={event => setTitle(event.target.value)} />
        <TrainingTextarea label="Plan description" name="description" maxLength={1000} value={description} onChange={event => setDescription(event.target.value)} />
        <StepFields steps={steps} setSteps={setSteps} />
        {bookingId && <PlanDraftSummary title={title} steps={steps}
          audience={(() => { const member = members.find(item => item.bookingId === Number(bookingId)); return member ? `${member.memberName} / Individual draft (booking #${member.bookingId}, ${formatCalendarDate(member.sessionDate)})` : 'Individual draft' })()}
          shareLabel="Assign plan" />}
        <div className="training-actions">
          <Button variant="secondary" disabled={steps.length >= 100} onClick={() => setSteps(current => [...current, blankStep()])}>Add exercise</Button>
          <Button type="submit" loading={action.pending}>Assign plan</Button>
        </div>
      </fieldset>
      <ActionMessage {...action} />
    </form>
  )
}

function CreateClassPlanForm({ classId }: { classId: number }) {
  const [steps, setSteps] = useState([blankStep()])
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  // One key per intended plan: a retried submit of the same plan is not duplicated by the server.
  const [operationKey, setOperationKey] = useState(() => crypto.randomUUID())
  const action = useTrainingAction()

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void action.run(async () => {
      const planTitle = title.trim()
      if (invalidSteps(planTitle, steps)) throw new Error('Enter a plan title, exercise titles and durations between 1 and 1440 minutes.')
      await training.createClassPlan({ classId, title: planTitle, description: description.trim() || null, steps: stepBody(steps) }, operationKey)
      setTitle('')
      setDescription('')
      setSteps([blankStep()])
      setOperationKey(crypto.randomUUID())
    }, 'Class plan assigned. Members with a confirmed booking in this class can view it.')
  }

  return (
    <form className="training-form" onSubmit={submit}>
      <fieldset disabled={action.pending} className="training-fieldset">
        <CoachDrillDrafts
          intro="Class drafts use the number of members at each level and with each goal, and the class averages of recorded skills. No individual member data is used. Nothing is assigned until you review the plan and select Assign class plan."
          load={options => training.classDrillSuggestions(classId, options)}
          onUse={(drafts, adjustments) => { setSteps(draftSteps(drafts)); setDescription(current => withAdjustments(current, adjustments)) }} />
        <Input label="Plan title" name="title" required maxLength={150} value={title} onChange={event => setTitle(event.target.value)} />
        <TrainingTextarea label="Plan description" name="description" maxLength={1000} value={description} onChange={event => setDescription(event.target.value)} />
        <StepFields steps={steps} setSteps={setSteps} />
        <PlanDraftSummary title={title} steps={steps} audience="Whole class / Class draft" shareLabel="Assign class plan" />
        <div className="training-actions">
          <Button variant="secondary" disabled={steps.length >= 100} onClick={() => setSteps(current => [...current, blankStep()])}>Add exercise</Button>
          <Button type="submit" loading={action.pending}>Assign class plan</Button>
        </div>
      </fieldset>
      <ActionMessage {...action} />
    </form>
  )
}

/** F5-06 "Member training context": what the drafts are based on, shown next to the suggestion inputs. */
function MemberTrainingContext({ classId, memberId }: { classId: number; memberId: number }) {
  const load = useCallback((signal: AbortSignal) => coachProgress(classId, memberId, signal), [classId, memberId])
  return <section className="training-context" aria-label="Member training context"><h4>Member training context</h4>
    <TrainingData load={load}>{({ profile, progress }) => {
      const recent = progress.sessions.slice(-3).reverse()
      const latestScores = recent.find(session => session.scores.length)?.scores ?? []
      const latestFeedback = [...progress.feedback].sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0]
      const goal = GOAL_OPTIONS.find(option => option.value === profile.mainGoal)?.label ?? profile.mainGoal
      const level = LEVEL_OPTIONS.find(option => option.value === profile.currentLevel)?.label ?? profile.currentLevel
      return <dl className="browse-facts">
        <div><dt>Goal</dt><dd>{goal || 'Not set'}</dd></div>
        <div><dt>Member level</dt><dd>{level || 'Not set'}</dd></div>
        <div><dt>Sessions reviewed</dt><dd>{recent.length ? recent.map(session => formatCalendarDate(session.date)).join(' / ') : 'No recorded results yet'}</dd></div>
        <div><dt>Recent results</dt><dd>{latestScores.length ? latestScores.map(score => `${score.metricName} ${score.score}/${score.maxScore}`).join(', ') : 'No recorded scores yet'}</dd></div>
        <div><dt>Latest areas for improvement</dt><dd>{latestFeedback?.areasForImprovement || 'None recorded'}</dd></div>
      </dl>
    }}</TrainingData>
  </section>
}

interface DrillPreview { mode: DrillSuggestions['mode']; ruleVersion: string; drafts: DrillDraft[]; memberCount?: number }
/** F5-07 Review Suggested Exercises: drafts as a table, Coach adjustments, then add to the plan or revise the inputs. */
function CoachDrillDrafts({ intro, context, load, onUse }: { intro: string; context?: ReactNode; load: (options: DrillOptions) => Promise<DrillPreview>; onUse: (drafts: DrillDraft[], adjustments: string) => void }) {
  const [suggestions, setSuggestions] = useState<DrillPreview | null>(null)
  const [selected, setSelected] = useState<number[]>([])
  const [copied, setCopied] = useState(0)
  const [focus, setFocus] = useState('')
  const [sessionMinutes, setSessionMinutes] = useState('')
  const [adjustments, setAdjustments] = useState('')
  const focusBox = useRef<HTMLDivElement>(null)
  const action = useTrainingAction()
  const chosenMinutes = suggestions ? suggestions.drafts.filter((_, index) => selected.includes(index)).reduce((sum, draft) => sum + draft.durationMinutes, 0) : 0
  function revise() {
    setSuggestions(null)
    setSelected([])
    setCopied(0)
    action.reset()
    focusBox.current?.querySelector("input")?.focus()
  }
  function suggest() {
    void action.run(async () => {
      const minutes = sessionMinutes.trim() ? Number(sessionMinutes) : undefined
      if (minutes !== undefined && (!Number.isInteger(minutes) || minutes < 15 || minutes > 180)) throw new Error('Session duration must be a whole number between 15 and 180 minutes.')
      setSuggestions(await load({ focus: focus.trim() || undefined, sessionMinutes: minutes }))
      setSelected([])
      setCopied(0)
    }, 'Drafts loaded for Coach review. Nothing assigned.')
  }
  return <div className="training-form"><h3>Supplementary drill drafts</h3>
    <p>{intro}</p>
    {context}
    <div ref={focusBox}><Input label="Current focus (optional)" maxLength={200} value={focus} onChange={event => setFocus(event.target.value)} /></div>
    <Input label="Session duration in minutes (optional)" type="number" min={15} max={180} step={1} value={sessionMinutes} onChange={event => setSessionMinutes(event.target.value)} />
    <Button variant="secondary" loading={action.pending} onClick={suggest}>Suggest practice drills</Button>
    <ActionMessage {...action} />
    {suggestions && (suggestions.mode === 'AI_GENERATED'
      ? <p role="note"><strong>AI draft</strong> generated by {suggestions.ruleVersion} for review. Check every exercise for safety and suitability before assigning. Drafts are not saved.</p>
      : <p>Mode: {suggestions.mode}, rule version {suggestions.ruleVersion}. Drafts are not saved.</p>)}
    {suggestions?.memberCount !== undefined && <p>Based on {suggestions.memberCount === 1 ? '1 member' : `${suggestions.memberCount} members`} with a confirmed booking in this class.</p>}
    {suggestions && (suggestions.drafts.length ? <>
      <table className="table"><caption>Suggested exercises for review</caption>
        <thead><tr><th>Select</th><th>Exercise</th><th>Time</th><th>Target</th><th>Why suggested</th></tr></thead>
        <tbody>{suggestions.drafts.map((draft, index) => <tr key={draft.title}>
          <td><label><input type="checkbox" checked={selected.includes(index)} onChange={event => setSelected(current => event.target.checked ? [...current, index] : current.filter(value => value !== index))} /> Select draft: {draft.title}</label></td>
          <td><strong>{draft.title}</strong><p>{draft.instructions}</p></td>
          <td>{draft.durationMinutes} min</td>
          <td>{`${draft.goal} - ${draft.level} - ${draft.durationMinutes} minutes`}</td>
          <td>{draft.reason}</td>
        </tr>)}</tbody>
      </table>
      <p>Selected: {selected.length === 1 ? '1 exercise' : `${selected.length} exercises`}, {chosenMinutes} minutes{sessionMinutes.trim() ? ` of a ${sessionMinutes.trim()}-minute session` : ''}.</p>
      <TrainingTextarea label="Coach adjustments (optional)" maxLength={500} value={adjustments} onChange={event => setAdjustments(event.target.value)} />
      <p>AI and the curated catalog only provide suggestions. You approve content, duration and intensity before sharing.</p>
      <div className="training-actions">
        <Button variant="secondary" disabled={!selected.length} onClick={() => { const chosen = suggestions.drafts.filter((_, index) => selected.includes(index)); onUse(chosen, adjustments.trim()); setCopied(chosen.length) }}>Review selected drafts in plan form</Button>
        <Button variant="ghost" onClick={revise}>Revise inputs</Button>
      </div>
      {copied > 0 && <p role="status">{copied === 1 ? '1 draft' : copied + ' drafts'} copied into the exercise fields below. Edit them, then select Assign plan. Nothing is assigned yet.</p>}</>
      : <><EmptyState title="No curated drafts for this sport and level" description="Create a Coach-authored plan using the exercise fields below." />
        <Button variant="ghost" onClick={revise}>Revise inputs</Button></>)}
  </div>
}

/** Coach adjustments are added to the plan description once, under their own heading. */
function withAdjustments(description: string, adjustments: string) {
  if (!adjustments) return description
  const note = `Coach adjustments: ${adjustments}`
  if (description.includes(note)) return description
  return (description.trim() ? `${description.trim()}\n${note}` : note).slice(0, 1000)
}

/** F5-08 Training Plan Draft: what will be shared, before anything is saved or visible to the member. */
function PlanDraftSummary({ title, steps, audience, shareLabel }: { title: string; steps: DraftStep[]; audience: string; shareLabel: string }) {
  const filled = steps.filter(step => step.title.trim())
  if (!title.trim() && !filled.length) return null
  const total = filled.reduce((sum, step) => sum + (Number.isInteger(Number(step.durationMinutes)) ? Number(step.durationMinutes) : 0), 0)
  return <section className="training-context" aria-label="Training plan draft">
    <h4>Training plan draft</h4>
    <p><span className="badge badge--warning">Draft / Not shared</span></p>
    <dl className="browse-facts">
      <div><dt>Plan</dt><dd>{title.trim() || 'Untitled plan'}</dd></div>
      <div><dt>For</dt><dd>{audience}</dd></div>
      <div><dt>Duration</dt><dd>{total} minutes in {filled.length === 1 ? '1 exercise' : `${filled.length} exercises`}</dd></div>
    </dl>
    {filled.length > 0 && <table className="table"><caption>Exercises in this draft</caption><thead><tr><th>Exercise</th><th>Time</th></tr></thead>
      <tbody>{filled.map((step, index) => <tr key={index}><td>{step.title.trim()}</td><td>{step.durationMinutes} min</td></tr>)}</tbody></table>}
    <p>Before sharing: check every exercise, the member or class and the session. Nothing is saved and the member sees nothing until you select {shareLabel}.</p>
    <p>Member visibility: Not shared yet.</p>
  </section>
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
    <div className="training-page coach-ui">
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
