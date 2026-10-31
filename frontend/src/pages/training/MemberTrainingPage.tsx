import { useState } from 'react'
import { trainingApi, type TrainingPlan, type TrainingResult } from '../../api/trainingApi'
import { Badge, Card, EmptyState, ErrorState, PageHeader, PageLoader, StatCard, Tabs } from '../../components/ui'
import { useResource } from '../../hooks/useResource'

const loadPlans = (signal: AbortSignal) => trainingApi.memberPlans(signal)
const loadResults = (signal: AbortSignal) => trainingApi.memberResults(signal)
const loadAttendance = (signal: AbortSignal) => trainingApi.memberAttendance(signal)

function PlanCard({ plan }: { plan: TrainingPlan }) {
  return <Card title={plan.title} actions={<Badge tone={plan.status === 'PUBLISHED' ? 'success' : 'neutral'}>{plan.status}</Badge>}>
    <p className="training-goal">{plan.goal}</p>
    <p className="training-meta">{plan.startDate} – {plan.endDate || 'Ongoing'} · Revision {plan.revision}</p>
    <div className="training-exercises">{plan.exercises.map((exercise, index) => <article key={exercise.id ?? `${exercise.name}-${index}`}>
      <strong>{exercise.name}</strong>
      <span>{exercise.sets ? `${exercise.sets} sets` : ''}{exercise.reps ? ` × ${exercise.reps} reps` : ''}{exercise.durationMinutes ? `${exercise.durationMinutes} minutes` : ''}</span>
      {exercise.instructions && <p>{exercise.instructions}</p>}
    </article>)}</div>
  </Card>
}

function History({ rows, empty }: { rows: TrainingResult[]; empty: string }) {
  if (!rows.length) return <EmptyState title={empty} description="Completed training information will appear here." />
  return <div className="training-history">{rows.map((row) => <Card key={row.id} title={`${row.sportName} · ${row.sessionDate}`} actions={<Badge tone={row.attendance === 'PRESENT' ? 'success' : 'neutral'}>{row.attendance}</Badge>}>
    {row.planTitle && <p><strong>Plan:</strong> {row.planTitle}</p>}
    {row.summary && <p>{row.summary}</p>}
    {row.metrics.length > 0 && <dl className="training-metrics">{row.metrics.map((metric) => <div key={metric.label}><dt>{metric.label}</dt><dd>{metric.value}</dd></div>)}</dl>}
    {row.coachFeedback && <blockquote className="training-feedback">“{row.coachFeedback}”</blockquote>}
  </Card>)}</div>
}

export function MemberTrainingPage() {
  const [tab, setTab] = useState('plans')
  const [plans, reloadPlans] = useResource(loadPlans)
  const [results, reloadResults] = useResource(loadResults)
  const [attendance, reloadAttendance] = useResource(loadAttendance)
  const planRows = plans.status === 'ready' ? plans.data : []
  const resultRows = results.status === 'ready' ? results.data : []
  const attendanceRows = attendance.status === 'ready' ? attendance.data : []
  const selected = tab === 'plans' ? plans : tab === 'results' ? results : attendance
  const reload = tab === 'plans' ? reloadPlans : tab === 'results' ? reloadResults : reloadAttendance

  return <div className="page training-page">
    <PageHeader eyebrow="Member" title="My Training" description="Follow your coach-assigned plans, session outcomes, attendance and feedback." />
    <div className="training-stats"><StatCard label="Published plans" value={planRows.filter((item) => item.status === 'PUBLISHED').length} /><StatCard label="Recorded sessions" value={resultRows.length} /><StatCard label="Present" value={attendanceRows.filter((item) => item.attendance === 'PRESENT').length} /></div>
    <Tabs label="My training records" value={tab} onChange={setTab} items={[{ id: 'plans', label: 'Training plans' }, { id: 'results', label: 'Results & feedback' }, { id: 'attendance', label: 'Attendance' }]}>
      {() => selected.status === 'loading' ? <PageLoader label="Loading training records" /> : selected.status === 'error' ? <ErrorState title="We could not load your training records" error={selected.error} onRetry={reload} /> : tab === 'plans' ? (planRows.length ? <div className="training-card-list">{planRows.map((plan) => <PlanCard key={plan.id} plan={plan} />)}</div> : <EmptyState title="No training plan yet" description="A published plan from your coach will appear here." />) : tab === 'results' ? <History rows={resultRows} empty="No training results yet" /> : <History rows={attendanceRows} empty="No attendance records yet" />}
    </Tabs>
  </div>
}
