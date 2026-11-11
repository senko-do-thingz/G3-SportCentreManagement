import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { memberTrainingApi, type Attendance, type CoachFeedback, type TrainingPlan, type TrainingResult } from '../../api/memberTrainingApi'
import { Card, EmptyState, ErrorState, PageHeader } from '../../components/ui'
import { formatDateTime } from '../../utils/format'
import { TrainingData } from './TrainingData'

type Source<T> = { value: T; error?: never } | { value?: never; error: unknown }
interface TrainingOverview {
  plans: Source<TrainingPlan[]>
  attendance: Source<Attendance[]>
  results: Source<TrainingResult[]>
  feedback: Source<CoachFeedback[]>
}

async function loadSource<T>(request: Promise<T>): Promise<Source<T>> {
  try {
    return { value: await request }
  } catch (error) {
    return { error }
  }
}

function count(source: Source<unknown[]>): number | string {
  return source.value !== undefined ? source.value.length : 'Unavailable'
}

function hasValue<T>(source: Source<T>): source is { value: T; error?: never } {
  return source.value !== undefined
}

const TRAINING_DESTINATIONS = [
  { to: '/member/training/classes', label: 'My classes', detail: 'Your enrolled classes and coach questions.' },
  { to: '/member/training/plans', label: 'Training plans', detail: 'Plans and exercises assigned by your coach.' },
  { to: '/member/training/feedback', label: 'Coach feedback', detail: 'Ratings and guidance saved by your coach.' },
  { to: '/member/training/attendance', label: 'Attendance history', detail: 'Recorded attendance for your booked sessions.' },
  { to: '/member/training/results', label: 'Training results', detail: 'Session assessments and recorded skill scores.' },
  { to: '/member/training/recommendations', label: 'Class recommendations', detail: 'Suggestions based on your saved preferences.' },
  { to: '/member/training/goals', label: 'Training preferences', detail: 'Sports, goals and availability saved to your profile.' },
  { to: '/member/training/questions', label: 'Ask your coach', detail: 'Questions connected to your classes.' },
] as const

/** Member overview built only from the signed-in member training endpoints. */
export function MemberTrainingHubPage() {
  const load = useCallback(async (signal: AbortSignal): Promise<TrainingOverview> => {
    const [plans, attendance, results, feedback] = await Promise.all([
      loadSource(memberTrainingApi.plans(signal)),
      loadSource(memberTrainingApi.attendance(signal)),
      loadSource(memberTrainingApi.results(signal)),
      loadSource(memberTrainingApi.feedback(signal)),
    ])
    return { plans, attendance, results, feedback }
  }, [])

  return (
    <div className="training-page member-training-hub">
      <PageHeader
        eyebrow="Member workspace"
        title="Training Hub"
        description="Your classes, coach plans, attendance and recorded progress in one place."
      />
      <TrainingData load={load} refreshLabel="Refresh training overview">
        {overview => <TrainingOverviewContent overview={overview} />}
      </TrainingData>
    </div>
  )
}

function TrainingOverviewContent({ overview }: { overview: TrainingOverview }) {
  return (
    <>
      <section className="training-hub__hero" aria-labelledby="training-hub-hero-title">
        <div>
          <p className="training-hub__eyebrow">Your training, organized</p>
          <h2 id="training-hub-hero-title">Train. Learn. Improve.</h2>
          <p>Pick up where you left off with your coach and your classes.</p>
        </div>
        <Link className="training-hub__hero-action" to="/member/training/classes">View my classes <span aria-hidden="true">&gt;</span></Link>
      </section>

      <dl className="training-hub__metrics" aria-label="Training records">
        <Metric label="Assigned plans" value={count(overview.plans)} />
        <Metric label="Attendance records" value={count(overview.attendance)} />
        <Metric label="Recorded results" value={count(overview.results)} />
        <Metric label="Coach feedback" value={count(overview.feedback)} />
      </dl>

      <div className="training-hub__overview-grid">
        <div className="training-hub__record-column">
          <Card title="Assigned plans" actions={<Link to="/member/training/plans">All plans</Link>}>
            <SourceError source={overview.plans} title="Unable to load your plans" />
            {hasValue(overview.plans) && (overview.plans.value.length === 0
              ? <EmptyState title="No assigned plans yet" description="Plans will appear here after your coach assigns them." />
              : <ul className="training-hub__record-list">
                {overview.plans.value.slice(0, 3).map(plan => (
                  <li key={plan.id}>
                    <div><h3>{plan.title}</h3><p>{plan.className} <span aria-hidden="true">·</span> Coach {plan.coachName}</p></div>
                    <Link to={`/member/training/plans/${plan.id}`} aria-label={`View plan details: ${plan.title}`}>View <span aria-hidden="true">&gt;</span></Link>
                  </li>
                ))}
              </ul>)}
          </Card>

          <Card title="Recent coach feedback" actions={<Link to="/member/training/feedback">All feedback</Link>}>
            <SourceError source={overview.feedback} title="Unable to load coach feedback" />
            {hasValue(overview.feedback) && (overview.feedback.value.length === 0
              ? <EmptyState title="No coach feedback yet" description="Feedback will appear here when your coach records it." />
              : <ul className="training-hub__record-list">
                {overview.feedback.value.slice(0, 2).map(item => (
                  <li key={item.id}>
                    <div><h3>{item.planTitle}</h3><p>{item.coachName} <span aria-hidden="true">·</span> {item.rating}/5 <span aria-hidden="true">·</span> {formatDateTime(item.createdAt)}</p>
                      <p className="training-hub__comment">{item.comments}</p></div>
                    <Link to={`/member/training/plans/${item.planId}`} aria-label={`Open ${item.planTitle}`}>Open <span aria-hidden="true">&gt;</span></Link>
                  </li>
                ))}
              </ul>)}
          </Card>

          <Card title="Recent training results" actions={<Link to="/member/training/results">All results</Link>}>
            <SourceError source={overview.results} title="Unable to load your results" />
            {hasValue(overview.results) && (overview.results.value.length === 0
              ? <EmptyState title="No training results yet" description="Results will appear here after your coach records a session assessment." />
              : <ul className="training-hub__record-list">
                {overview.results.value.slice(0, 3).map(result => (
                  <li key={result.id}>
                    <div>
                      <h3>{result.session.className}</h3>
                      <p>{result.session.sportName && <>{result.session.sportName} <span aria-hidden="true">-</span> </>}{formatDateTime(`${result.session.date}T${result.session.startTime}`)}</p>
                      {result.notes && <p className="training-hub__comment">{result.notes}</p>}
                    </div>
                    <Link to={`/member/training/results/${result.id}`} aria-label={`View result: ${result.session.className}`}>View <span aria-hidden="true">&gt;</span></Link>
                  </li>
                ))}
              </ul>)}
          </Card>
        </div>

        <Card title="Your training tools">
          <nav className="training-hub__destinations" aria-label="Training tools">
            {TRAINING_DESTINATIONS.map(destination => (
              <Link key={destination.to} to={destination.to}>
                <span><strong>{destination.label}</strong><small>{destination.detail}</small></span>
                <span className="training-hub__destination-arrow" aria-hidden="true">&gt;</span>
              </Link>
            ))}
          </nav>
        </Card>
      </div>
    </>
  )
}

function Metric({ label, value }: { label: string; value: number | string }) {
  return <div className="training-hub__metric"><dt>{label}</dt><dd>{value}</dd></div>
}

function SourceError<T>({ source, title }: { source: Source<T>; title: string }) {
  return 'error' in source ? <ErrorState title={title} error={source.error} /> : null
}
