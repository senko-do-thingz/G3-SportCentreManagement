import { formatCalendarDate } from '../../utils/date'
import { sessionIdText } from '../../api/sessionIdentity'
import { useCallback, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { trainingGuidanceApi, type ClassRecommendation, type RecommendationPreferences, type SavedRecommendation, type SportLevel } from '../../api/trainingGuidanceApi'
import { packagesApi } from '../../api/packagesApi'
import { Button, Card, EmptyState, Input, PageHeader, Select } from '../../components/ui'
import { ActionMessage } from '../training/TrainingFormFields'
import { useTrainingAction } from '../training/trainingFormState'
import { TrainingData } from './TrainingData'
import { GOAL_OPTIONS, LEVEL_OPTIONS } from './profileValidation'

const loadSports = (signal: AbortSignal) => packagesApi.sports({ signal })
const loadSportLevels = (signal: AbortSignal) => trainingGuidanceApi.sportLevels(signal)
const loadSaved = (signal: AbortSignal) => trainingGuidanceApi.savedRecommendations(signal)
const levelLabel = (value: string | null) => LEVEL_OPTIONS.find(option => option.value === value)?.label ?? value ?? ''

export function ClassRecommendationsPage() {
  const [preferences, setPreferences] = useState<RecommendationPreferences>({ page: 0, size: 10 })
  const [savedRevision, refreshSaved] = useState(0)
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
    <Card title="Your level per sport">
      <p>Set a level for a sport you train. When Preferred level is left blank, recommendations for that sport use it instead of your general profile level.</p>
      <TrainingData load={loadSportLevels}>{levels => <SportLevelsForm levels={levels} onSaved={() => setPreferences(current => ({ ...current }))} />}</TrainingData>
    </Card>
    <TrainingData load={load}>{data => <Card title="Recommended classes"><p>{data.mode} - {data.message}</p>
      {data.items.length ? <ul className="training-list">{data.items.map(item => <li key={item.classId}><h2>{item.className}</h2>
        <p>{item.sportName} - {item.level} - {item.coachName ?? 'Self-training'} - {formatCalendarDate(item.date)} {item.startTime.slice(0,5)}-{item.endTime.slice(0,5)}</p>
        <p>{item.freeSeats} available places - Preference score: {item.score}</p><ul>{item.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul>
        <Link to={`/member/sessions/${item.sessionCode ?? item.sessionId}`}>View details and booking rules: {item.className} - {sessionIdText(item.sessionCode)}</Link>
        <SaveRecommendationButton item={item} onSaved={() => refreshSaved(value => value + 1)} /></li>)}</ul>
        : <EmptyState title="No matching available classes" description="Try another date range or sport. A full or unpublished class is not recommended." />}
      <div className="training-actions"><Button variant="secondary" disabled={data.page === 0} onClick={() => setPreferences(p => ({ ...p, page: data.page - 1 }))}>Previous recommendations</Button>
        <Button variant="secondary" disabled={(data.page+1)*data.size >= data.totalMatches} onClick={() => setPreferences(p => ({ ...p, page: data.page + 1 }))}>Next recommendations</Button></div>
    </Card>}</TrainingData>
    <Card title="Saved recommendations">
      <p>Saved recommendations are not bookings. Availability, schedule and package eligibility are checked again when you choose a session.</p>
      <TrainingData key={savedRevision} load={loadSaved}>{saved => <SavedRecommendations saved={saved} onChanged={() => refreshSaved(value => value + 1)} />}</TrainingData>
    </Card>
  </div>
}

function SportLevelsForm({ levels, onSaved }: { levels: SportLevel[]; onSaved: () => void }) {
  const [values, setValues] = useState(() => Object.fromEntries(levels.map(level => [level.sportId, level.level ?? ''])))
  const action = useTrainingAction()
  if (!levels.length) return <EmptyState title="No active sports" description="Sport levels can be set once the center publishes a sport." />
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void action.run(async () => {
      for (const level of levels) {
        const next = values[level.sportId] ?? ''
        if (next === (level.level ?? '')) continue
        if (next) await trainingGuidanceApi.saveSportLevel(level.sportId, next)
        else await trainingGuidanceApi.clearSportLevel(level.sportId)
      }
      onSaved()
    }, 'Sport levels saved. Recommendations use them from now on.')
  }
  return <form className="training-form" onSubmit={submit}>
    <fieldset disabled={action.pending} className="training-fieldset">
      {levels.map(level => <Select key={level.sportId} label={`${level.sportName} level`} placeholder="Use my general level" options={LEVEL_OPTIONS}
        value={values[level.sportId] ?? ''} onChange={event => setValues(current => ({ ...current, [level.sportId]: event.target.value }))} />)}
      <Button type="submit" loading={action.pending}>Save sport levels</Button>
    </fieldset>
    <ActionMessage {...action} />
  </form>
}

function SaveRecommendationButton({ item, onSaved }: { item: ClassRecommendation; onSaved: () => void }) {
  const [saved, setSaved] = useState(false)
  const action = useTrainingAction()
  // The first reason only states availability; the rest explain why the class fits.
  const reason = item.reasons.slice(1).join('; ').slice(0, 500) || null
  return <div className="training-actions">
    <Button variant="secondary" disabled={saved} loading={action.pending} onClick={() => { void action.run(async () => {
      await trainingGuidanceApi.saveRecommendation(item.classId, reason)
      setSaved(true)
      onSaved()
    }, `${item.className} saved to your recommendations. Nothing was booked.`) }}>{saved ? `Saved: ${item.className}` : `Save recommendation: ${item.className}`}</Button>
    <ActionMessage {...action} />
  </div>
}

function SavedRecommendations({ saved, onChanged }: { saved: SavedRecommendation[]; onChanged: () => void }) {
  const action = useTrainingAction()
  if (!saved.length) return <EmptyState title="No saved recommendations" description="Use Save recommendation on a class to keep it here for later. Saving is not a booking." />
  return <>
    <ul className="training-list">{saved.map(item => <li key={item.id}><h3>{item.className}</h3>
      <p>{item.sportName} - {levelLabel(item.level)} - {item.coachName ?? 'Self-training'}</p>
      {item.reason && <p>Why saved: {item.reason}</p>}
      {item.nextSession
        ? <Link to={`/member/sessions/${item.nextSession.sessionCode ?? item.nextSession.sessionId}`}>Next session: {item.className} - {formatCalendarDate(item.nextSession.date)} {item.nextSession.startTime.slice(0,5)}-{item.nextSession.endTime.slice(0,5)} - {item.nextSession.freeSeats} available places</Link>
        : <p>No upcoming session with available places right now.</p>}
      <div className="training-actions"><Button variant="secondary" disabled={action.pending} onClick={() => { void action.run(async () => {
        await trainingGuidanceApi.removeSavedRecommendation(item.id)
        onChanged()
      }, `${item.className} removed from your saved recommendations.`) }}>Remove saved recommendation: {item.className}</Button></div>
    </li>)}</ul>
    <ActionMessage {...action} />
  </>
}
