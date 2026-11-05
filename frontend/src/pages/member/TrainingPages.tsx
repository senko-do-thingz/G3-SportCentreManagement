import { formatDateTime } from '../../utils/format'
import { formatCalendarDate } from '../../utils/date'
import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { memberTrainingApi as training, type CoachFeedback, type TrainingSession } from '../../api/memberTrainingApi'
import { SessionContext } from '../../components/SessionContext'
import { Card } from '../../components/ui/Card'
import { EmptyState } from '../../components/ui/EmptyState'
import { PageHeader } from '../../components/ui/PageHeader'
import { Button } from '../../components/ui/Button'
import { TrainingData } from './TrainingData'
import './training.css'

const attendanceLabels: Record<string, string> = {
  PRESENT: 'Present',
  ABSENT: 'Absent',
  LATE: 'Late',
  EXCUSED: 'Excused',
  UNMARKED: 'Not marked',
  CANCELLED: 'Cancelled',
}

function SessionText({ session }: { session: TrainingSession }) {
  return <div className="training-session"><SessionContext {...session} /></div>
}

function FeedbackList({ feedback }: { feedback: CoachFeedback[] }) {
  if (!feedback.length) {
    return <EmptyState title="No coach feedback yet" description="Your coach's feedback and ratings will appear here once saved." />
  }

  return (
    <ul className="training-list">
      {feedback.map(item => (
        <li key={item.id}>
          <h3>{item.coachName} - Rating {item.rating}/5</h3>
          <p><Link to={`/member/training/plans/${item.planId}`}>{item.planTitle}</Link></p>
          <p><time dateTime={item.createdAt}>{formatDateTime(item.createdAt)}</time></p>
          <p className="training-copy">{item.comments}</p>
          {item.strengths && <p className="training-copy"><strong>Strengths: </strong>{item.strengths}</p>}
          {item.areasForImprovement && <p className="training-copy"><strong>Areas for improvement: </strong>{item.areasForImprovement}</p>}
        </li>
      ))}
    </ul>
  )
}

export function TrainingPlansPage() {
  return (
    <div className="training-page">
      <PageHeader title="Training plans" description="View the personal plans and exercise instructions assigned by your coach." />
      <TrainingData load={training.plans} refreshLabel="Refresh training plans">
        {plans => plans.length ? (
          <Card>
            <ul className="training-list">
              {plans.map(plan => (
                <li key={plan.id}>
                  <h2>{plan.title}</h2>
                  <p>{plan.className} - Coach {plan.coachName}</p>
                  {plan.description && <p className="training-copy">{plan.description}</p>}
                  <Link to={`/member/training/plans/${plan.id}`} aria-label={`View plan details: ${plan.title}`}>
                    View plan details
                  </Link>
                </li>
              ))}
            </ul>
          </Card>
        ) : <EmptyState title="No assigned plans yet" description="Personal plans will appear here once your coach saves and assigns them." />}
      </TrainingData>
    </div>
  )
}

export function TrainingPlanDetailPage() {
  const { id = '' } = useParams()
  const load = useCallback((signal: AbortSignal) => training.plan(id, signal), [id])

  return (
    <TrainingData load={load}>
      {({ plan, steps, feedback }, refresh) => (
        <div className="training-page">
          <div className="training-actions">
            <Link to="/member/training/plans">Back to training plans</Link>
            <Button variant="secondary" onClick={refresh}>Refresh plan and feedback</Button>
          </div>
          <PageHeader title={plan.title} description={`${plan.className} - Coach ${plan.coachName} - Read only`} />
          {plan.description && <p className="training-copy">{plan.description}</p>}
          <Card title="Exercises">
            {steps.length ? (
              <ol className="training-steps">
                {steps.map(step => (
                  <li key={step.id}>
                    <h3>{step.title} - {step.durationMinutes} minutes</h3>
                    <p className="training-copy">{step.instructions || 'Your coach has not added instructions yet.'}</p>
                  </li>
                ))}
              </ol>
            ) : <EmptyState title="No exercises yet" description="Your coach will add exercises to this plan." />}
          </Card>
          <Card title="Plan feedback"><FeedbackList feedback={feedback} /></Card>
          <Link to="/member/training/feedback">View your coach feedback</Link>
        </div>
      )}
    </TrainingData>
  )
}

export function CoachFeedbackPage() {
  return (
    <div className="training-page">
      <PageHeader title="Coach feedback" description="Personal feedback, progress ratings and guidance from your coach." />
      <TrainingData load={training.feedback} refreshLabel="Refresh coach feedback">
        {feedback => <Card title="Progress rating"><FeedbackList feedback={feedback} /></Card>}
      </TrainingData>
    </div>
  )
}

export function AttendancePage() {
  return (
    <div className="training-page">
      <PageHeader title="Attendance history" description="View your session dates, times and attendance status." />
      <TrainingData load={training.attendance} refreshLabel="Refresh attendance history">
        {rows => rows.length ? (
          <Card>
            <ul className="training-list">
              {rows.map(row => (
                <li key={row.bookingId ?? `attendance-${row.id}`}>
                  <SessionText session={row.session} />
                  <p><strong>{attendanceLabels[row.status] ?? row.status}</strong></p>
                  {row.status === 'CANCELLED' && row.recordedStatus && <p>Recorded participation: {attendanceLabels[row.recordedStatus] ?? row.recordedStatus}</p>}
                  {row.notes && <p className="training-copy">{row.notes}</p>}
                </li>
              ))}
            </ul>
          </Card>
        ) : <EmptyState title="No attendance history yet" description="Your booked sessions and recorded participation will appear here. Unmarked sessions do not mean absent." />}
      </TrainingData>
    </div>
  )
}

export function TrainingResultsPage() {
  return (
    <div className="training-page">
      <PageHeader title="Training results" description="View your session results, skill scores and coach feedback." />
      <TrainingData load={training.results} refreshLabel="Refresh training results">
        {rows => rows.length ? (
          <Card>
            <ul className="training-list">
              {rows.map(row => (
                <li key={row.id}>
                  <SessionText session={row.session} />
                  {row.notes && <p className="training-copy">{row.notes}</p>}
                  <Link
                    to={`/member/training/results/${row.id}`}
                    aria-label={`View session results: ${row.session.className}, ${formatCalendarDate(row.session.date)}, ${row.session.startTime.slice(0, 5)}`}
                  >
                    View session results
                  </Link>
                </li>
              ))}
            </ul>
          </Card>
        ) : <EmptyState title="No training results yet" description="Results will appear once your coach records your session assessment." />}
      </TrainingData>
    </div>
  )
}

export function TrainingResultDetailPage() {
  const { id = '' } = useParams()
  const load = useCallback((signal: AbortSignal) => training.result(id, signal), [id])

  return (
    <TrainingData load={load}>
      {({ result, scores }, refresh) => (
        <div className="training-page">
          <div className="training-actions">
            <Link to="/member/training/results">Back to training results</Link>
            <Button variant="secondary" onClick={refresh}>Refresh session results</Button>
          </div>
          <PageHeader title="Session result details" />
          <SessionText session={result.session} />
          {result.notes && <p className="training-copy">{result.notes}</p>}
          <Card title="Skill scores">
            {scores.length ? (
              <dl className="training-scores">
                {scores.map(score => (
                  <div key={score.id}>
                    <dt>{score.metricName}{score.description && <p>{score.description}</p>}</dt>
                    <dd>{score.score} / {score.maxScore}</dd>
                  </div>
                ))}
              </dl>
            ) : <EmptyState title="No skill scores yet" description="Your coach has not recorded skill scores for this session." />}
          </Card>
          <Link to="/member/training/feedback">View plan feedback</Link>
        </div>
      )}
    </TrainingData>
  )
}
