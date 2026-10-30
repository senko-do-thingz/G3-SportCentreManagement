import { useCallback } from 'react'
import { memberTrainingApi, type CoachNote, type TrainingPlan } from '../../api/memberTrainingApi'
import { Badge, Card, EmptyState, ErrorState, PageHeader } from '../../components/ui'
import { Spinner } from '../../components/ui/Spinner'
import { formatDateTime } from '../../utils/format'
import { MemberTrainingNav } from './MemberTrainingNav'
import { MemberTrainingPreviewNotice } from './MemberTrainingPreviewNotice'
import { useMemberResource } from './useMemberResource'

/**
 * Training Plans and Coach Notes (NX-14, F4-REQ-06): read-only list of the member's own published
 * plans and the comments coaches shared. There is no edit control on purpose; members never write
 * plans or notes. Plans and notes load separately so one failure does not hide the other.
 */
export function MemberTrainingPlansPage() {
  return (
    <div className="page member-training-page">
      <PageHeader
        eyebrow="Training"
        title="My Training Plans"
        description="Plans your coach has published for you, and the notes your coach shared."
      />
      <MemberTrainingNav />
      <MemberTrainingPreviewNotice />
      <PlansPanel />
      <NotesPanel />
    </div>
  )
}

function PlansPanel() {
  const load = useCallback((signal: AbortSignal) => memberTrainingApi.plans(signal), [])
  const [plans, reload] = useMemberResource(load)
  return (
    <Card title="Training plans" aria-label="Training plans">
      {plans.status === 'loading' && <Spinner label="Loading your training plans" />}
      {plans.status === 'error' && <ErrorState title="We could not load your training plans" error={plans.error} onRetry={reload} />}
      {plans.status === 'ready' && plans.data.length === 0 && (
        <EmptyState
          title="No training plan yet"
          description="When your coach publishes a plan for you or your class, it appears here."
        />
      )}
      {plans.status === 'ready' && plans.data.length > 0 && (
        <ul className="training-list">
          {plans.data.map((plan) => <PlanItem key={plan.id} plan={plan} />)}
        </ul>
      )}
    </Card>
  )
}

function PlanItem({ plan }: { plan: TrainingPlan }) {
  return (
    <li className="training-item">
      <div className="training-item__head">
        <h3 className="training-item__title">{plan.title}</h3>
        <Badge tone={plan.audience === 'CLASS' ? 'info' : 'success'}>{plan.audience === 'CLASS' ? 'Class plan' : 'Personal plan'}</Badge>
      </div>
      <p className="training-item__meta">
        {plan.coachName ? `Coach ${plan.coachName}` : 'Coach not shown'}
        {' - revision '}{plan.revision}
        {plan.publishedAt ? ` - published ${formatDateTime(plan.publishedAt)}` : ''}
      </p>
      {plan.summary && <p className="training-item__text">{plan.summary}</p>}
      {plan.steps.length === 0 ? (
        <p className="training-item__meta">This plan has no steps yet.</p>
      ) : (
        <ol className="training-steps" aria-label={`Steps of ${plan.title}`}>
          {plan.steps.map((step) => (
            <li key={`${step.order}-${step.title}`} className="training-steps__step">
              <strong>{step.title}</strong>
              {step.details && <span className="training-item__text">{step.details}</span>}
            </li>
          ))}
        </ol>
      )}
    </li>
  )
}

function NotesPanel() {
  const load = useCallback((signal: AbortSignal) => memberTrainingApi.notes(signal), [])
  const [notes, reload] = useMemberResource(load)
  return (
    <Card title="Coach notes" aria-label="Coach notes">
      {notes.status === 'loading' && <Spinner label="Loading your coach notes" />}
      {notes.status === 'error' && <ErrorState title="We could not load your coach notes" error={notes.error} onRetry={reload} />}
      {notes.status === 'ready' && notes.data.length === 0 && (
        <EmptyState title="No coach notes yet" description="Notes your coach shares with you appear here." />
      )}
      {notes.status === 'ready' && notes.data.length > 0 && (
        <ul className="training-list">
          {notes.data.map((note) => <NoteItem key={note.id} note={note} />)}
        </ul>
      )}
    </Card>
  )
}

function NoteItem({ note }: { note: CoachNote }) {
  return (
    <li className="training-item">
      <p className="training-item__text">{note.comment}</p>
      <p className="training-item__meta">
        {note.coachName ? `Coach ${note.coachName}` : 'Coach'}
        {note.createdAt ? ` - ${formatDateTime(note.createdAt)}` : ''}
      </p>
    </li>
  )
}
