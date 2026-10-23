import { Link } from 'react-router-dom'
import type { BrowseSession } from '../../api/memberSessionsApi'
import { Badge, buttonClassName } from '../../components/ui'
import { browseDate } from './memberScheduleDates'
import { sportImageFor } from './sportImages'

const LEVELS = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED']

function levelLabel(value: string | null): string {
  if (!value) return 'Level not provided'
  return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

export function SessionCard({ session, returnTo }: { session: BrowseSession; returnTo: string }) {
  const self = session.trainingFormat === 'SELF_TRAINING'
  const level = session.level?.toUpperCase() ?? ''
  const levelIndex = LEVELS.indexOf(level)
  return <article className="browse-session class-session-card">
    <div className="class-session-card__image">
      <img src={sportImageFor(session.sportName)} alt={`${session.sportName} training session`} loading="lazy" />
      <span className="class-session-card__image-shade" aria-hidden="true" />
      <span className="class-session-card__sport">{session.sportName}</span>
      <span className={`class-session-card__level${levelIndex >= 0 ? ` class-session-card__level--${level.toLowerCase()}` : ''}`}>
        <span className="class-session-card__level-bars" aria-hidden="true">
          {LEVELS.map((item, index) => <i className={index <= levelIndex ? 'is-active' : ''} key={item} />)}
        </span>
        {levelLabel(session.level)}
      </span>
    </div>
    <div className="class-session-card__body">
      <div className="class-session-card__heading">
        <p className="class-session-card__format">{self ? 'Self-training' : 'Coach-led'}</p>
        <h3>{session.className || `${session.sportName} ${self ? 'Self-training' : 'Session'}`}</h3>
      </div>
      <div className="class-session-card__details">
        <p><span className="class-session-card__detail-label">When</span>{browseDate(session.sessionDate)} · {session.startTime.slice(0, 5)}–{session.endTime.slice(0, 5)}</p>
        <p><span className="class-session-card__detail-label">Coach</span>{self ? 'No coach required' : session.coachName || 'Not provided'}</p>
        <p><span className="class-session-card__detail-label">Facility</span>{session.facilityName || 'Not provided'}</p>
      </div>
      <div className="class-session-card__footer">
        <Badge tone={session.full || session.freeSeats <= 0 ? 'warning' : 'success'}>{session.full || session.freeSeats <= 0 ? 'Fully booked' : `${session.freeSeats} ${session.freeSeats === 1 ? 'spot' : 'spots'} left`}</Badge>
        <Link className={buttonClassName('secondary')} to={`/member/sessions/${session.id}?${new URLSearchParams({ returnTo })}`}>View session</Link>
      </div>
    </div>
  </article>
}
