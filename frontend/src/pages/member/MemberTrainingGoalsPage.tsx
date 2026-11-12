import { useCallback, useContext, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { AuthContext } from '../../auth/authContext'
import { memberApi } from '../../api/memberApi'
import { packagesApi } from '../../api/packagesApi'
import { trainingGuidanceApi, type SportLevel, type TrainingContext } from '../../api/trainingGuidanceApi'
import type { UserProfile } from '../../api/types'
import { Button, Card, Input, PageHeader, Select } from '../../components/ui'
import { formatCalendarDate } from '../../utils/dateDisplay'
import { ActionMessage } from '../training/TrainingFormFields'
import { useTrainingAction } from '../training/trainingFormState'
import { GOAL_OPTIONS, LEVEL_OPTIONS } from './profileValidation'
import { RecommendationTabs } from './RecommendationTabs'
import { TrainingData } from './TrainingData'
import { availabilityQuery, FORMAT_OPTIONS, loadAvailability, saveAvailability, WEEKDAYS, type TrainingAvailability } from './trainingAvailability'
import './training.css'

interface GoalsData { profile: UserProfile; context: TrainingContext; levels: SportLevel[]; sports: { id: number; name: string }[] }
const label = (options: { value: string; label: string }[], value: string | null | undefined) => options.find(option => option.value === value)?.label ?? 'Not set'

/** Flow 5 F5-01 My Training Goals: goal, levels per sport, preferred sports, format and available times. */
export function MemberTrainingGoalsPage() {
  const auth = useContext(AuthContext)
  const memberId = auth?.user?.id
  const load = useCallback(async (signal: AbortSignal): Promise<GoalsData> => {
    const [profile, context, levels, sports] = await Promise.all([memberApi.me({ signal }), trainingGuidanceApi.context(signal),
      trainingGuidanceApi.sportLevels(signal), packagesApi.sports({ signal })])
    return { profile, context, levels, sports }
  }, [])
  return <div className="training-page">
    <RecommendationTabs />
    <PageHeader title="My training goals" description="Tell us what you want to achieve and when you can train. Recommendations use these preferences; you always choose what to book." />
    <TrainingData key={memberId} load={load}>{data => <div className="training-form-grid">
      <GoalsForm data={data} memberId={memberId} />
      <Card title="Your training context">
        <dl className="browse-facts">
          <div><dt>Member</dt><dd>{data.context.memberName}</dd></div>
          <div><dt>Goal</dt><dd>{label(GOAL_OPTIONS, data.context.mainGoal)}</dd></div>
          <div><dt>General level</dt><dd>{label(LEVEL_OPTIONS, data.context.currentLevel)}</dd></div>
          <div><dt>Age group check</dt><dd>{data.context.dateOfBirthKnown ? 'Date of birth on file' : 'Add your date of birth in My profile'}</dd></div>
          {data.context.activePackages.map((item, index) => <div key={index}><dt>{item.sportName}</dt>
            <dd>{item.trainingFormat === 'COACH_LED' ? 'Coach-led' : 'Self-training'} / {item.remainingSessions} of {item.totalSessions} sessions until {formatCalendarDate(item.endDate)}</dd></div>)}
          {data.context.activePackages.length === 0 && <div><dt>Packages</dt><dd>No active sport package</dd></div>}
        </dl>
        <p>Suggestions use your goals and availability. You choose what to book; a recommendation never books or uses a session.</p>
      </Card>
    </div>}</TrainingData>
  </div>
}

function GoalsForm({ data, memberId }: { data: GoalsData; memberId: number | undefined }) {
  const navigate = useNavigate()
  const action = useTrainingAction()
  const [goal, setGoal] = useState(data.profile.mainGoal ?? '')
  const [level, setLevel] = useState(data.profile.currentLevel ?? '')
  const [preferred, setPreferred] = useState<number[]>(data.context.preferredSportIds)
  const [levels, setLevels] = useState<Record<number, string>>(() => Object.fromEntries(data.levels.map(item => [item.sportId, item.level ?? ''])))
  const [availability, setAvailability] = useState<TrainingAvailability>(() => loadAvailability(memberId))
  const toggle = <T,>(list: T[], value: T, on: boolean) => on ? [...list.filter(item => item !== value), value] : list.filter(item => item !== value)

  async function save() {
    if (availability.earliest && availability.latest && availability.earliest >= availability.latest)
      throw new Error('The earliest start time must be before the latest end time.')
    const { profile } = data
    if (goal !== (profile.mainGoal ?? '') || level !== (profile.currentLevel ?? '')) {
      await memberApi.updateProfile({ fullName: profile.fullName, phone: profile.phone, dateOfBirth: profile.dateOfBirth ?? null,
        address: profile.address ?? null, mainGoal: goal || null, currentLevel: level || null })
    }
    await trainingGuidanceApi.savePreferredSports(preferred)
    for (const item of data.levels) {
      const next = levels[item.sportId] ?? ''
      if (next === (item.level ?? '')) continue
      if (next) await trainingGuidanceApi.saveSportLevel(item.sportId, next)
      else await trainingGuidanceApi.clearSportLevel(item.sportId)
    }
    saveAvailability(memberId, availability)
  }
  function submit(event: FormEvent) {
    event.preventDefault()
    void action.run(save, 'Training preferences saved.')
  }
  return <Card title="Training preferences"><form className="training-form" onSubmit={submit}>
    <Select label="Main goal" placeholder="Not set" options={GOAL_OPTIONS} value={goal} onChange={event => setGoal(event.target.value)} />
    <Select label="General level" placeholder="Not set" options={LEVEL_OPTIONS} value={level} onChange={event => setLevel(event.target.value)} />
    <fieldset><legend>Preferred sports and level per sport</legend>
      <div className="training-sport-list">
        {data.sports.map(sport => <div key={sport.id} className="training-sport-row">
          <label className="training-sport-checkbox">
            <input type="checkbox" checked={preferred.includes(sport.id)} onChange={event => setPreferred(list => toggle(list, sport.id, event.target.checked))} />
            <span>{sport.name}</span>
          </label>
          {data.levels.some(item => item.sportId === sport.id) && (
            <div className="training-sport-select">
              <Select aria-label={`${sport.name} level`} placeholder="Use general level" options={LEVEL_OPTIONS}
                value={levels[sport.id] ?? ''} onChange={event => setLevels(current => ({ ...current, [sport.id]: event.target.value }))} />
            </div>
          )}
        </div>)}
      </div>
    </fieldset>
    <Select label="Training format" options={FORMAT_OPTIONS} value={availability.format}
      onChange={event => setAvailability(current => ({ ...current, format: event.target.value as TrainingAvailability['format'] }))} />
    <fieldset><legend>Available days</legend><div className="training-days-grid">
      {WEEKDAYS.map(day => <label key={day.value} className="training-day-chip"><input type="checkbox" checked={availability.days.includes(day.value)}
        onChange={event => setAvailability(current => ({ ...current, days: toggle(current.days, day.value, event.target.checked) }))} /> <span>{day.label}</span></label>)}
    </div></fieldset>
    <div className="training-time-range">
      <Input label="Earliest start time" type="time" value={availability.earliest} onChange={event => setAvailability(current => ({ ...current, earliest: event.target.value }))} />
      <Input label="Latest end time" type="time" value={availability.latest} onChange={event => setAvailability(current => ({ ...current, latest: event.target.value }))} />
    </div>
    <p>Goal and levels are saved to your profile. Levels are recorded per sport rather than one level for all sports. Training format and available times are kept on this device.</p>
    <ActionMessage {...action} />
    <div className="training-actions">
      <Button type="submit" variant="secondary" loading={action.pending}>Save preferences</Button>
      <Button type="button" disabled={action.pending} onClick={() => { void action.run(async () => {
        await save()
        const firstSport = preferred[0]
        const query = availabilityQuery(availability, {
          sportId: firstSport,
          level: (firstSport && levels[firstSport]) || level || undefined,
          goal: goal || undefined,
        })
        navigate(`/member/training/recommendations${query ? `?${query}` : ''}`)
      }, 'Training preferences saved.') }}>Get recommendations</Button>
    </div>
  </form></Card>
}
