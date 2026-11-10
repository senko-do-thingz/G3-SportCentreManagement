import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { staffTrainingApi, type TrainingClass } from '../../api/staffTrainingApi'
import { coachProgress, type LearnerProfile, type LearnerProgress } from '../../api/coachProgressApi'
import { HEALTH_OUTCOME_LABELS, memberHealthApi, type CoachHealthSummary } from '../../api/memberHealthApi'
import { Badge, Button, Card, EmptyState, PageHeader, Select } from '../../components/ui'
import { formatCalendarDate } from '../../utils/date'
import { formatDateTime } from '../../utils/format'
import { TrainingData } from '../member/TrainingData'
import '../member/training.css'
import '../../styles/coachUi.css'

export function CoachProgressPage() {
  return <div className="training-page coach-ui coach-progress-page">
    <PageHeader title="Member progress" description="Review the goals and recorded outcomes of members in your assigned classes." />
    <TrainingData load={staffTrainingApi.coachClasses}>{classes => <ProgressWorkspace classes={classes} />}</TrainingData>
  </div>
}

function ProgressWorkspace({ classes }: { classes: TrainingClass[] }) {
  const [params] = useSearchParams()
  const requestedClassId = params.get('classId') ?? ''
  const requestedMemberId = params.get('memberId') ?? ''
  const initialClassId = classes.some(item => String(item.id) === requestedClassId) ? requestedClassId : ''
  const [classId, setClassId] = useState(initialClassId)
  const [selectedMember, setSelectedMember] = useState(() => initialClassId ? requestedMemberId : '')
  const selectedClass = classes.find(item => String(item.id) === classId)
  const load = useCallback((signal: AbortSignal) => staffTrainingApi.bookingCandidates(classId, signal), [classId])
  return <>
    <section className="coach-schedule-hero" aria-label="Member progress introduction">
      <div className="coach-schedule-hero__copy">
        <p className="coach-schedule-hero__eyebrow">Sportify coach experience</p>
        <h2>Track development.<br /><span>One class at a time.</span></h2>
        <p>Review saved training outcomes and member-shared notes.</p>
      </div>
      <Button variant="accent" size="sm" className="coach-schedule-hero__action" disabled={!classes.length} onClick={() => document.getElementById('coach-progress-assigned-class')?.focus()}>Select a class</Button>
    </section>
    <section className="coach-progress-summary" aria-label="Progress selection summary">
      <div><span>Assigned classes</span><strong>{classes.length}</strong></div>
      <div><span>Class in focus</span><strong>{selectedClass?.name ?? 'Choose a class'}</strong></div>
    </section>
    <div className="coach-progress-layout">
      <Card className="coach-progress-class-picker" title="Choose an assigned class">
        <p className="coach-progress-guidance">Progress is limited to members with confirmed bookings in your assigned classes.</p>
        <Select id="coach-progress-assigned-class" label="Assigned class" value={classId} placeholder="Choose a class" options={classes.map(item => ({ value: String(item.id), label: item.name }))} onChange={event => { setClassId(event.target.value); setSelectedMember('') }} />
        {!classes.length && <p role="status">No assigned classes.</p>}
        {requestedClassId && !initialClassId && <p className="coach-progress-unavailable" role="status">The linked class is not available in your assigned classes. Choose an assigned class to continue.</p>}
      </Card>
      {selectedClass ? <TrainingData key={classId} load={load}>{candidates => {
        const members = [...new Map(candidates.map(item => [item.memberId, item])).values()]
        const member = members.find(item => String(item.memberId) === selectedMember)
        const unavailableLinkedMember = classId === initialClassId && Boolean(requestedMemberId) && !member
        const progressLoad = (signal: AbortSignal) => coachProgress(Number(classId), Number(selectedMember), signal)
        return <>
          <Card className="coach-progress-member-picker" title="Choose a class member">
            <Select label="Member" value={member ? selectedMember : ''} placeholder="Choose a member" options={members.map(item => ({ value: String(item.memberId), label: item.memberName }))} onChange={event => setSelectedMember(event.target.value)} />
            {members.length > 0 && <p className="coach-progress-picker-note">{members.length} eligible {members.length === 1 ? 'member' : 'members'} in this class.</p>}
            {!members.length && <p role="status">No eligible confirmed members in this class.</p>}
            {unavailableLinkedMember && <p className="coach-progress-unavailable" role="status">The linked member is not available in this assigned class. Choose an eligible member from the list.</p>}
          </Card>
          {member ? <TrainingData key={`${classId}-${selectedMember}`} load={progressLoad} refreshLabel="Refresh member progress">{({ profile, progress }) => <ProgressDetails classId={Number(classId)} profile={profile} progress={progress} />}</TrainingData> : <section className="coach-progress-detail coach-progress-detail--empty" aria-label="Progress workspace">
            <EmptyState title={members.length ? 'Choose a member to view progress' : 'No eligible members to review'} description={members.length ? 'Member progress and shared training notes appear here after you select an eligible member.' : 'Only members with confirmed bookings in this assigned class can be reviewed.'} />
          </section>}
        </>
      }}</TrainingData> : <section className="coach-progress-detail coach-progress-detail--empty" aria-label="Progress workspace">
        <EmptyState title={classes.length ? 'Choose a class to view progress' : 'No assigned classes'} description={classes.length ? 'Select an assigned class to find eligible members and review their saved training data.' : 'Assigned classes will appear here when they are available to your Coach account.'} />
      </section>}
    </div>
  </>
}

function ProgressDetails({ classId, profile, progress }: {
  classId: number
  profile: LearnerProfile
  progress: LearnerProgress
}) {
  return <Card className="coach-progress-detail" title="Progress workspace" actions={<span className="coach-progress-result-count">{progress.sessions.length} {progress.sessions.length === 1 ? 'recorded result' : 'recorded results'}</span>}>
    <header className="coach-progress-member-heading">
      <div><p>Member profile</p><h3>{profile.memberName}</h3></div>
      <span>Member code <strong>{profile.memberCode ?? '-'}</strong></span>
    </header>
    <section className="coach-progress-section">
      <h3>Profile and goals</h3>
      <dl className="coach-progress-facts"><div><dt>Current level</dt><dd>{profile.currentLevel ?? 'Not provided'}</dd></div><div><dt>Training goal</dt><dd>{profile.mainGoal ?? 'Not provided'}</dd></div></dl>
    </section>
    <HealthSummarySection classId={classId} memberId={profile.memberId} />
    <section className="coach-progress-section">
      <div className="coach-progress-section-heading"><h3>Recorded outcomes</h3><span>{progress.sessions.length} {progress.sessions.length === 1 ? 'result' : 'results'}</span></div>
      <p>{progress.interpretation}</p>
      <p className="coach-progress-caveat">Attendance shown here belongs to recorded results. This is not the complete attendance ledger. Compare only the same metric ID and scale.</p>
      {progress.sessions.length === 0 ? <p className="coach-progress-empty" role="status">No recorded results for this member in your class.</p> : <div className="table-scroll coach-progress-table-scroll"><table className="table"><caption>Chronological session outcomes</caption><thead><tr><th>Session</th><th>Attendance</th><th>Scores</th><th>Notes</th></tr></thead><tbody>{progress.sessions.map(item => <tr key={item.resultId}><td>{item.sessionCode}<br />{formatCalendarDate(item.date)}</td><td>{item.attendanceStatus ?? 'Unmarked'}</td><td>{item.notesOnly || !item.scores.length ? 'Notes only - no numeric trend' : item.scores.map(score => <div key={score.metricId}>{score.metricName} (metric {score.metricId}): {score.score} / {score.maxScore}</div>)}</td><td>{item.notes ?? '-'}</td></tr>)}</tbody></table></div>}
    </section>
    <section className="coach-progress-section coach-progress-feedback">
      <div className="coach-progress-section-heading"><h3>Saved feedback</h3><Link to="/coach/training/plans">Open plans and feedback</Link></div>
      {!progress.feedback.length ? <p className="coach-progress-empty">No saved feedback for this member in your class.</p> : progress.feedback.map(feedback => <article key={feedback.id}>
        <div className="coach-progress-feedback-heading"><h4>{feedback.planTitle}</h4><time dateTime={feedback.createdAt}>{formatDateTime(feedback.createdAt)}</time></div>
        <p>{feedback.comments}</p><p>Rating: {feedback.rating}. {feedback.strengths} {feedback.areasForImprovement}</p>
      </article>)}
      <p>Use the existing plan feedback form to save guidance, then refresh this page to read the saved record.</p>
    </section>
  </Card>
}

/** MW-02: only the Member's consented training summary is available to Coaches. */
function HealthSummarySection({ classId, memberId }: { classId: number; memberId: number }) {
  const load = useCallback((signal: AbortSignal) => memberHealthApi.coachSummary(classId, memberId, signal), [classId, memberId])
  return <section className="coach-progress-section coach-progress-health">
    <h3>Health and training note</h3>
    <TrainingData load={load}>{(summary: CoachHealthSummary) => <>
    {summary.shared && summary.outcome ? <>
      <p><Badge tone={summary.outcome === 'COACH_DISCUSSION_RECOMMENDED' ? 'warning' : summary.outcome === 'NO_REPORTED_CONCERNS' ? 'success' : 'neutral'}>{HEALTH_OUTCOME_LABELS[summary.outcome]}</Badge></p>
      {summary.consultationRequested && <p>The member would like to talk with you before training.</p>}
      {summary.updatedAt && <p>Shared by the member, last updated {formatDateTime(summary.updatedAt)}.</p>}
    </> : <p role="status">The member has not shared a health and training note.</p>}
    <p>{summary.disclaimer}</p>
    </>}</TrainingData>
  </section>
}
