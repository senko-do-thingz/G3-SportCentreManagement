import { sessionIdText } from '../../api/sessionIdentity'
import { useCallback, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { trainingGuidanceApi, type RecommendationPreferences } from '../../api/trainingGuidanceApi'
import { packagesApi } from '../../api/packagesApi'
import { Button, Card, EmptyState, Input, PageHeader, Select } from '../../components/ui'
import { TrainingData } from './TrainingData'
import { GOAL_OPTIONS, LEVEL_OPTIONS } from './profileValidation'

const loadSports = (signal: AbortSignal) => packagesApi.sports({ signal })
export function ClassRecommendationsPage() {
  const [preferences, setPreferences] = useState<RecommendationPreferences>({ page: 0, size: 10 })
  const load = useCallback((signal: AbortSignal) => trainingGuidanceApi.recommendations(preferences, signal), [preferences])
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    const text = (key: string) => String(values.get(key) ?? '').trim() || undefined
    setPreferences({ sportId: text('sportId') ? Number(text('sportId')) : undefined, level: text('level'), goal: text('goal'),
      from: text('from'), to: text('to'), earliest: text('earliest'), latest: text('latest'), page: 0, size: 10 })
  }
  return <div className="training-page"><PageHeader title="Class recommendations" description="RULE_BASED suggestions from published center sessions. Saved level and goal are used when left blank. No generative AI provider is configured." />
    <Card title="Your preferences"><form className="training-form" onSubmit={submit}>
      <TrainingData load={loadSports}>{sports => <Select label="Preferred sport" name="sportId" placeholder="All sports" options={sports.map(s => ({ value: String(s.id), label: s.name }))} />}</TrainingData>
      <Select label="Preferred level" name="level" placeholder="Use saved level" options={LEVEL_OPTIONS} />
      <Select label="Training goal" name="goal" placeholder="Use saved goal" options={GOAL_OPTIONS} />
      <Input label="Available from date" name="from" type="date" /><Input label="Available to date" name="to" type="date" />
      <Input label="Earliest start time" name="earliest" type="time" /><Input label="Latest end time" name="latest" type="time" />
      <p>Use a future range of up to 30 days. Times use Vietnam time (UTC+7).</p><Button type="submit">Find recommendations</Button>
    </form></Card>
    <TrainingData load={load}>{data => <Card title="Recommended classes"><p>{data.mode} - {data.message}</p>
      {data.items.length ? <ul className="training-list">{data.items.map(item => <li key={item.classId}><h2>{item.className}</h2>
        <p>{item.sportName} - {item.level} - {item.coachName ?? 'Self-training'} - {item.date} {item.startTime.slice(0,5)}-{item.endTime.slice(0,5)}</p>
        <p>{item.freeSeats} available places - Preference score: {item.score}</p><ul>{item.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul>
        <Link to={`/member/sessions/${item.sessionCode ?? item.sessionId}`}>View details and booking rules: {item.className} - {sessionIdText(item.sessionCode)}</Link></li>)}</ul>
        : <EmptyState title="No matching available classes" description="Try another date range or sport. A full or unpublished class is not recommended." />}
      <div className="training-actions"><Button variant="secondary" disabled={data.page === 0} onClick={() => setPreferences(p => ({ ...p, page: data.page - 1 }))}>Previous recommendations</Button>
        <Button variant="secondary" disabled={(data.page+1)*data.size >= data.totalMatches} onClick={() => setPreferences(p => ({ ...p, page: data.page + 1 }))}>Next recommendations</Button></div>
    </Card>}</TrainingData>
  </div>
}
