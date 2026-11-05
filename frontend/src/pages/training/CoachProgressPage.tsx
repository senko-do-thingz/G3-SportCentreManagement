import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { staffTrainingApi, type BookingCandidate, type TrainingClass } from '../../api/staffTrainingApi'
import { coachProgress } from '../../api/coachProgressApi'
import { Card, PageHeader, Select } from '../../components/ui'
import { formatCalendarDate } from '../../utils/date'
import { formatDateTime } from '../../utils/format'
import { TrainingData } from '../member/TrainingData'
import '../member/training.css'

export function CoachProgressPage() {
  return <div className="training-page">
    <PageHeader title="Member progress" description="Review the goals and recorded outcomes of members in your assigned classes." />
    <TrainingData load={staffTrainingApi.coachClasses}>{classes => <ProgressWorkspace classes={classes} />}</TrainingData>
  </div>
}

function ProgressWorkspace({ classes }: { classes: TrainingClass[] }) {
  const [params] = useSearchParams()
  const [classId, setClassId] = useState(() => classes.some(item => String(item.id) === params.get('classId')) ? params.get('classId')! : '')
  const load = useCallback((signal: AbortSignal) => staffTrainingApi.bookingCandidates(classId, signal), [classId])
  return <>
    <Card title="Select assigned class">
      <Select label="Assigned class" value={classId} placeholder="Choose a class" options={classes.map(item => ({ value: String(item.id), label: item.name }))} onChange={event => setClassId(event.target.value)} />
      {!classes.length && <p role="status">No assigned classes.</p>}
    </Card>
    {classId && <TrainingData load={load}>{candidates => <Members key={classId} classId={Number(classId)} candidates={candidates} initialMember={params.get('memberId') ?? ''} />}</TrainingData>}
  </>
}

function Members({ classId, candidates, initialMember }: { classId: number; candidates: BookingCandidate[]; initialMember: string }) {
  const members = [...new Map(candidates.map(item => [item.memberId, item])).values()]
  const [selected, select] = useState(initialMember)
  const member = members.find(item => String(item.memberId) === selected)
  const load = useCallback((signal: AbortSignal) => coachProgress(classId, Number(selected), signal), [classId, selected])
  return <>
    <Card title="Select class member">
      <Select label="Member" value={member ? selected : ''} placeholder="Choose a member" options={members.map(item => ({ value: String(item.memberId), label: item.memberName }))} onChange={event => select(event.target.value)} />
      {!members.length && <p role="status">No eligible confirmed members in this class.</p>}
    </Card>
    {member && <TrainingData load={load} refreshLabel="Refresh member progress">{({ profile, progress }) => <>
      <Card title={`${profile.memberName} - Profile and goals`}>
        <dl className="browse-facts"><div><dt>Member code</dt><dd>{profile.memberCode ?? '-'}</dd></div><div><dt>Current level</dt><dd>{profile.currentLevel ?? 'Not provided'}</dd></div><div><dt>Training goal</dt><dd>{profile.mainGoal ?? 'Not provided'}</dd></div></dl>
      </Card>
      <Card title="Recorded progress">
        <p>{progress.interpretation}</p>
        <p>Attendance shown here belongs to recorded results. This is not the complete attendance ledger. Compare only the same metric ID and scale.</p>
        {progress.sessions.length === 0 ? <p role="status">No recorded results for this member in your class.</p> : <div className="table-scroll"><table className="table"><caption>Chronological session outcomes</caption><thead><tr><th>Session</th><th>Attendance</th><th>Scores</th><th>Notes</th></tr></thead><tbody>{progress.sessions.map(item => <tr key={item.resultId}><td>{item.sessionCode}<br />{formatCalendarDate(item.date)}</td><td>{item.attendanceStatus ?? 'Unmarked'}</td><td>{item.notesOnly || !item.scores.length ? 'Notes only - no numeric trend' : item.scores.map(score => <div key={score.metricId}>{score.metricName} (metric {score.metricId}): {score.score} / {score.maxScore}</div>)}</td><td>{item.notes ?? '-'}</td></tr>)}</tbody></table></div>}
      </Card>
      <Card title="Saved feedback">
        {!progress.feedback.length ? <p>No saved feedback for this member in your class.</p> : progress.feedback.map(feedback => <article key={feedback.id}><h3>{feedback.planTitle}</h3><p>{feedback.comments}</p><p>Rating: {feedback.rating}. {feedback.strengths} {feedback.areasForImprovement}</p><p>{formatDateTime(feedback.createdAt)}</p></article>)}
        <Link className="btn btn--secondary" to="/coach/training/plans">Open plans and feedback</Link>
        <p>Use the existing plan feedback form to save guidance, then refresh this page to read the saved record.</p>
      </Card>
    </>}</TrainingData>}
  </>
}
