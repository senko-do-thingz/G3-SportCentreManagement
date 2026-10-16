import { Link } from 'react-router-dom'
import type { BrowseSession } from '../../api/memberSessionsApi'
import { Badge, buttonClassName } from '../../components/ui'
import { browseDate } from './memberScheduleDates'

export function SessionCard({ session, returnTo }: { session: BrowseSession; returnTo: string }) {
  const self = session.trainingFormat === 'SELF_TRAINING'
  return <article className="browse-session">
    <p className="browse-sport">{session.sportName}</p>
    <h3>{session.className || `${session.sportName} ${self ? 'Self-training' : 'Session'}`}</h3>
    <p className="browse-muted">{self ? 'Self-training' : 'Coach-led'}{session.level ? ` / ${session.level[0] + session.level.slice(1).toLowerCase()}` : ''}</p>
    <p>{browseDate(session.sessionDate)} / {session.startTime.slice(0, 5)} - {session.endTime.slice(0, 5)}</p>
    <p>{self ? 'No coach required' : `Coach: ${session.coachName || 'Not provided'}`}</p>
    <p className="browse-muted">{session.facilityName || 'Facility not provided'}</p>
    <Badge tone={session.full || session.freeSeats <= 0 ? 'warning' : 'success'}>{session.full || session.freeSeats <= 0 ? 'Fully booked' : `${session.freeSeats} ${session.freeSeats === 1 ? 'spot' : 'spots'} left`}</Badge>
    <Link className={buttonClassName('secondary')} to={`/member/sessions/${session.id}?${new URLSearchParams({ returnTo })}`}>View session</Link>
  </article>
}
