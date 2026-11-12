import { formatCalendarDate } from '../../utils/date'
import { sessionIdText } from '../../api/sessionIdentity'
import { useCallback, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { RecommendationTabs } from './RecommendationTabs'
import { FORMAT_OPTIONS, WEEKDAYS } from './trainingAvailability'
import { trainingGuidanceApi, type ClassRecommendation, type RecommendationPreferences, type SavedRecommendation } from '../../api/trainingGuidanceApi'
import { packagesApi } from '../../api/packagesApi'
import { Button, CalendarDateField, Card, EmptyState, Input, PageHeader, Select } from '../../components/ui'
import { ActionMessage } from '../training/TrainingFormFields'
import { useTrainingAction } from '../training/trainingFormState'
import { TrainingData } from './TrainingData'
import { GOAL_OPTIONS, LEVEL_OPTIONS } from './profileValidation'

const loadSports = (signal: AbortSignal) => packagesApi.sports({ signal })
const loadSaved = (signal: AbortSignal) => trainingGuidanceApi.savedRecommendations(signal)
const levelLabel = (value: string | null) => LEVEL_OPTIONS.find(option => option.value === value)?.label ?? value ?? ''

/** Filters handed over from My goals (F5-01) in the query string; anything unexpected is ignored. */
function initialPreferences(params: URLSearchParams): RecommendationPreferences {
  const format = params.get('format')
  const days = (params.get('days') ?? '').split(',').filter(day => WEEKDAYS.some(item => item.value === day))
  const time = (key: string) => /^([01]\d|2[0-3]):[0-5]\d$/.test(params.get(key) ?? '') ? params.get(key)! : undefined
  const sportId = params.get('sportId') && Number(params.get('sportId')) ? Number(params.get('sportId')) : undefined
  const level = params.get('level') || undefined
  const goal = params.get('goal') || undefined
  return { page: 0, size: 10,
    ...(sportId ? { sportId } : {}),
    ...(level ? { level } : {}),
    ...(goal ? { goal } : {}),
    ...(format === 'COACH_LED' || format === 'SELF_TRAINING' ? { format } : {}),
    ...(days.length ? { days: days.join(',') } : {}),
    ...(time('earliest') ? { earliest: time('earliest') } : {}),
    ...(time('latest') ? { latest: time('latest') } : {}) }
}

export function ClassRecommendationsPage() {
  const [params] = useSearchParams()
  const [initial] = useState(() => initialPreferences(params))
  const [preferences, setPreferences] = useState<RecommendationPreferences>(initial)
  const [from, setFrom] = useState(initial.from ?? '')
  const [to, setTo] = useState(initial.to ?? '')
  const [savedRevision, refreshSaved] = useState(0)
  const load = useCallback((signal: AbortSignal) => trainingGuidanceApi.recommendations(preferences, signal), [preferences])
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    const text = (key: string) => String(values.get(key) ?? '').trim() || undefined
    const days = values.getAll('days').map(String)
    setPreferences({ sportId: text('sportId') ? Number(text('sportId')) : undefined, level: text('level'), goal: text('goal'),
      from: from || undefined, to: to || undefined, earliest: text('earliest'), latest: text('latest'), page: 0, size: 10,
      ...(text('format') ? { format: text('format') } : {}), ...(days.length ? { days: days.join(',') } : {}) })
  }
  const initialDays = initial.days?.split(',') ?? []
  return <div className="training-page"><RecommendationTabs /><PageHeader title="Class recommendations" description="Personalized class and session recommendations based on your sport preferences, skill level and training goals." />
    <Card title="Your preferences"><form className="training-form" onSubmit={submit}>
      <TrainingData load={loadSports}>{sports => <Select label="Preferred sport" name="sportId" placeholder="All sports" defaultValue={initial.sportId ? String(initial.sportId) : ''} options={sports.map(s => ({ value: String(s.id), label: s.name }))} />}</TrainingData>
      <Select label="Preferred level" name="level" placeholder="Use saved level" defaultValue={initial.level ?? ''} options={LEVEL_OPTIONS} />
      <Select label="Training goal" name="goal" placeholder="Use saved goal" defaultValue={initial.goal ?? ''} options={GOAL_OPTIONS} />
      <Select label="Training format" name="format" options={FORMAT_OPTIONS} defaultValue={initial.format || 'COACH_LED'} />
      <fieldset><legend>Available days (leave all unticked for any day)</legend><div className="training-days-grid">
        {WEEKDAYS.map(day => <label key={day.value} className="training-day-chip"><input type="checkbox" name="days" value={day.value} defaultChecked={initialDays.includes(day.value)} /> <span>{day.label}</span></label>)}
      </div></fieldset>
      <div className="training-time-range">
        <CalendarDateField label="Available from date" name="from" value={from} max={to || undefined} onChange={setFrom} />
        <CalendarDateField label="Available to date" name="to" value={to} min={from || undefined} onChange={setTo} />
      </div>
      <div className="training-time-range">
        <Input label="Earliest start time" name="earliest" type="time" defaultValue={initial.earliest} />
        <Input label="Latest end time" name="latest" type="time" defaultValue={initial.latest} />
      </div>
      <p>Use a future range of up to 30 days. Times use Vietnam time (UTC+7). Set your goals, preferred sports and usual availability in <Link to="/member/training/goals">My goals</Link>.</p><Button type="submit">Find recommendations</Button>
    </form></Card>
    <TrainingData load={load}>{data => <Card title="Recommended classes">
      <p className="training-mode-banner">{data.mode} - {data.message}</p>
      {data.items.length ? <div className="training-card-grid">{data.items.map(item => <article key={item.classId} className="training-card-item">
        <header className="training-card-header">
          <div>
            <h2 className="training-card-title">{item.className}</h2>
            <div className="training-badges">
              <span className="training-badge">{item.sportName}</span>
              <span className="training-badge training-badge--level">{item.level}</span>
              <span className="training-badge training-badge--coach">{item.coachName ? `Coach ${item.coachName}` : 'Self-training'}</span>
            </div>
          </div>
          <div className="training-score-badge" title="Match score based on your goals and schedule">
            <span className="training-score-label">Match score</span>
            <span className="training-score-value">{item.score}<small>/100</small></span>
          </div>
        </header>

        <div className="training-session-banner">
          <span>Date: <strong>{formatCalendarDate(item.date)}</strong></span>
          <span>Time: <strong>{item.startTime.slice(0,5)} - {item.endTime.slice(0,5)}</strong></span>
          <span>Seats: <strong>{item.freeSeats}</strong> available places</span>
        </div>

        {item.reasons.length > 0 && <div className="training-reasons-box">
          <div className="training-reasons-title">Why this class fits you</div>
          <ul className="training-reasons-list">{item.reasons.map(reason => <li key={reason}>
            <span className="training-check-icon">•</span>
            <span>{reason}</span>
          </li>)}</ul>
        </div>}

        <footer className="training-card-actions">
          <Link
            className="btn btn--primary btn--md"
            to={`/member/sessions/${item.sessionCode ?? item.sessionId}`}
            aria-label={`View details and booking rules: ${item.className} - ${sessionIdText(item.sessionCode)}`}
          >
            View details & booking rules
          </Link>
          <SaveRecommendationButton item={item} onSaved={() => refreshSaved(value => value + 1)} />
          <Link className="training-link-subtle" to={`/member/training/recommendations/classes/${item.classId}`}>All suitable sessions</Link>
        </footer>
      </article>)}</div>
        : <EmptyState title="No matching available classes" description="Try another date range or sport. A full or unpublished class is not recommended." />}
      <div className="training-actions"><Button variant="secondary" disabled={data.page === 0} onClick={() => setPreferences(p => ({ ...p, page: data.page - 1 }))}>Previous recommendations</Button>
        <Button variant="secondary" disabled={(data.page+1)*data.size >= data.totalMatches} onClick={() => setPreferences(p => ({ ...p, page: data.page + 1 }))}>Next recommendations</Button></div>
    </Card>}</TrainingData>
    <div id="saved" />
    <Card title="Saved recommendations">
      <p>Saved recommendations are not bookings. Availability, schedule and package eligibility are checked again when you choose a session.</p>
      <TrainingData key={savedRevision} load={loadSaved}>{saved => <SavedRecommendations saved={saved} onChanged={() => refreshSaved(value => value + 1)} />}</TrainingData>
    </Card>
  </div>
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
    <div className="training-card-grid">{saved.map(item => <article key={item.id} className="training-card-item training-card-item--saved">
      <header className="training-card-header">
        <div>
          <h3 className="training-card-title">{item.className}</h3>
          <div className="training-badges">
            <span className="training-badge">{item.sportName}</span>
            <span className="training-badge training-badge--level">{levelLabel(item.level)}</span>
            <span className="training-badge training-badge--coach">{item.coachName ? `Coach ${item.coachName}` : 'Self-training'}</span>
          </div>
          <p className="sr-only">{item.sportName} - {levelLabel(item.level)} - {item.coachName ?? 'Self-training'}</p>
        </div>
        <span className="training-saved-tag">Saved</span>
      </header>

      {item.reason && <div className="training-reasons-box">
        <div className="training-reasons-title">Why saved</div>
        <p className="training-saved-reason">Why saved: {item.reason}</p>
      </div>}

      <div className="training-session-banner">
        {item.available === false
          ? <p role="status">This class is no longer offered. Remove it from your saved recommendations.</p>
          : item.nextSession
            ? <Link className="training-next-session-link" to={`/member/sessions/${item.nextSession.sessionCode ?? item.nextSession.sessionId}`}>Next session: {item.className} - {formatCalendarDate(item.nextSession.date)} {item.nextSession.startTime.slice(0,5)}-{item.nextSession.endTime.slice(0,5)} - {item.nextSession.freeSeats} available places</Link>
            : <p>No upcoming session you can book right now (already booked, overlapping one of your bookings, outside the age group or full).</p>}
      </div>

      <footer className="training-card-actions">
        <Button variant="secondary" disabled={action.pending} onClick={() => { void action.run(async () => {
          await trainingGuidanceApi.removeSavedRecommendation(item.id)
          onChanged()
        }, `${item.className} removed from your saved recommendations.`) }}>Remove saved recommendation: {item.className}</Button>
      </footer>
    </article>)}</div>
    <ActionMessage {...action} />
  </>
}
