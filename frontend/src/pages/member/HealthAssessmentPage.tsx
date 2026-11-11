import { useCallback, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { getErrorMessage } from '../../api/errors'
import {
  HEALTH_OUTCOME_LABELS,
  memberHealthApi,
  type HealthAnswer,
  type HealthAssessment,
} from '../../api/memberHealthApi'
import { Badge, Button, Card, Checkbox, ErrorState, Notice, Spinner, buttonClassName } from '../../components/ui'
import { formatDateTime } from '../../utils/dateDisplay'
import { MemberCommerceHeader, MemberCommerceHero } from './MemberCommerceComponents'
import { useMemberResource } from './useMemberResource'

const CHOICES: { value: HealthAnswer; label: string }[] = [
  { value: 'YES', label: 'Yes' },
  { value: 'NO', label: 'No' },
  { value: 'PREFER_NOT_TO_ANSWER', label: 'Prefer not to answer' },
]

/** MW-02: optional, advisory-only questionnaire. Every question can be skipped. */
export function HealthAssessmentPage() {
  const load = useCallback((signal: AbortSignal) => memberHealthApi.get(signal), [])
  const [resource, reload] = useMemberResource(load)
  return (
    <div className="page member-commerce">
      <MemberCommerceHeader
        eyebrow="My Profile"
        title="Health & Training"
        description="An optional questionnaire that helps your Coach plan safe training. You can skip any question."
        actions={<Link className={buttonClassName('secondary')} to="/member/profile">Back to My Profile</Link>}
      />
      <MemberCommerceHero
        kicker="Coach Advisory"
        title="TRAIN SAFELY. SHARE ADVISORY NOTES WITH YOUR COACH."
        description="An optional, advisory-only questionnaire. Your Coach receives only a summary note if you consent to sharing."
      />
      {resource.status === 'loading' && <Spinner label="Loading your questionnaire" />}
      {resource.status === 'error' && <ErrorState title="We could not load your questionnaire" error={resource.error} onRetry={reload} />}
      {resource.status === 'ready' && <HealthForm key={resource.data.updatedAt ?? 'new'} initial={resource.data} />}
    </div>
  )
}

function HealthForm({ initial }: { initial: HealthAssessment }) {
  const [saved, setSaved] = useState(initial)
  const [answers, setAnswers] = useState<Record<string, HealthAnswer | undefined>>({ ...initial.answers })
  const [share, setShare] = useState(initial.shareWithCoach)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setMessage(null)
    try {
      const next = await memberHealthApi.save({ answers, shareWithCoach: share })
      setSaved(next)
      setMessage({ tone: 'success', text: 'Your answers were saved.' })
    } catch (error) {
      setMessage({ tone: 'danger', text: getErrorMessage(error) })
    } finally {
      setBusy(false)
    }
  }

  const clear = async () => {
    setBusy(true)
    setMessage(null)
    try {
      const next = await memberHealthApi.clear()
      setSaved(next)
      setAnswers({})
      setShare(false)
      setMessage({ tone: 'success', text: 'Your answers were cleared and Coach sharing is off.' })
    } catch (error) {
      setMessage({ tone: 'danger', text: getErrorMessage(error) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="health-layout">
      <Card title="Questionnaire" actions={<span className="health-version">Version {saved.version}</span>}>
        <form onSubmit={submit} aria-label="Health questionnaire" aria-busy={busy}>
          {saved.questions.map((question, index) => (
            <fieldset key={question.key} className="health-question">
              <legend>{index + 1}. {question.text}</legend>
              <div className="health-question__choices" role="radiogroup" aria-label={question.text}>
                {CHOICES.map((choice) => (
                  <label key={choice.value} className="health-choice">
                    <input
                      type="radio"
                      name={question.key}
                      value={choice.value}
                      checked={answers[question.key] === choice.value}
                      onChange={() => setAnswers((current) => ({ ...current, [question.key]: choice.value }))}
                    />
                    {choice.label}
                  </label>
                ))}
                <button
                  type="button"
                  className="health-skip"
                  disabled={answers[question.key] === undefined}
                  onClick={() => setAnswers((current) => ({ ...current, [question.key]: undefined }))}
                >
                  Skip
                </button>
              </div>
            </fieldset>
          ))}
          <Checkbox
            label="Share a short summary (the result below, not my answers) with the Coach of classes I book"
            checked={share}
            onChange={(event) => setShare(event.target.checked)}
          />
          {message && <Notice tone={message.tone} live="polite">{message.text}</Notice>}
          <div className="profile-form__actions">
            <Button type="submit" loading={busy}>Save answers</Button>
            <Button type="button" variant="secondary" disabled={busy || !saved.saved} onClick={clear}>Clear my answers</Button>
          </div>
        </form>
      </Card>

      <div className="health-side">
        <Card title="Training note">
          <p>
            <Badge tone={saved.outcome === 'COACH_DISCUSSION_RECOMMENDED' ? 'warning' : saved.outcome === 'NO_REPORTED_CONCERNS' ? 'success' : 'neutral'}>
              {HEALTH_OUTCOME_LABELS[saved.outcome]}
            </Badge>
          </p>
          <p className="health-note">{outcomeText(saved.outcome)}</p>
          {saved.updatedAt && <p className="health-meta">Last saved {formatDateTime(saved.updatedAt)}</p>}
          <p className="health-meta">Coach sharing: {saved.shareWithCoach ? 'On' : 'Off'}</p>
        </Card>
        <Card title="Privacy">
          <p className="health-note">{saved.disclaimer}</p>
          <ul className="health-list">
            <li>Only you can see your answers.</li>
            <li>With sharing on, the Coach of a class you booked sees only the training note above.</li>
            <li>Reception and managers cannot see your answers.</li>
            <li>You can change or clear your answers and turn sharing off at any time.</li>
          </ul>
        </Card>
      </div>
    </div>
  )
}

function outcomeText(outcome: HealthAssessment['outcome']): string {
  if (outcome === 'COACH_DISCUSSION_RECOMMENDED') {
    return 'You answered yes to at least one question. Talk with your Coach before training hard. This is not a medical decision.'
  }
  if (outcome === 'NO_REPORTED_CONCERNS') {
    return 'You did not report any concern. This does not mean you are medically cleared.'
  }
  return 'Some questions are not answered. That is fine; you can finish them later.'
}
