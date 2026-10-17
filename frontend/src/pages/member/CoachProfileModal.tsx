import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { coachProfilesApi } from '../../api/coachProfilesApi'
import { Badge, Button, Modal, PageLoader, buttonClassName } from '../../components/ui'
import { BrowseError } from './BrowseError'
import { browseDate, browseToday } from './memberScheduleDates'
import { useMemberResource } from './useMemberResource'

export function CoachProfileModal({ coachId, onClose }: { coachId: number; onClose: () => void }) {
  const loader = useCallback((signal: AbortSignal) => coachProfilesApi.get(coachId, signal), [coachId])
  const [resource, reload] = useMemberResource(loader)
  const profile = resource.status === 'ready' ? resource.data : null
  const sports = profile?.sports ?? []
  const certifications = profile?.certifications ?? []
  const upcomingSessions = profile?.upcomingSessions?.content ?? []
  return <Modal open title="Coach Profile" size="lg" onClose={onClose} footer={<Button variant="secondary" onClick={onClose}>Close profile</Button>}>
    {resource.status === 'loading' ? <PageLoader label="Loading coach profile" /> : resource.status === 'error' ? <BrowseError error={resource.error} onRetry={reload} /> : <div className="browse-detail">
      <section><div className="browse-avatar" aria-hidden="true">{resource.data.name.split(' ').filter(Boolean).slice(0, 2).map((name) => name[0]).join('')}</div><h3>{resource.data.name}</h3><p>{resource.data.headline || 'Sports coach'}</p><p className="browse-muted">{resource.data.yearsExperience === null ? 'Experience not provided' : `${resource.data.yearsExperience} years of experience`}</p><p>{resource.data.bio || 'Biography not provided.'}</p><p>Sports: {sports.map((sport) => sport.name).join(', ') || 'Not provided'}</p></section>
      <section><h3>Skills & qualifications</h3><p>{resource.data.specialties || 'Specialties not provided.'}</p>
        {!certifications.length && <p className="browse-muted">No certifications listed.</p>}
        {certifications.map((certificate) => <article className="browse-certificate" key={certificate.id}><h4>{certificate.name}</h4><p>{certificate.issuer || 'Issuer not provided'}</p><Badge tone={certificate.verified ? 'success' : 'neutral'}>{certificate.verified ? 'Verified' : 'Not verified'}</Badge>{certificate.issuedOn && <p>Issued: {browseDate(certificate.issuedOn)}</p>}{certificate.expiresOn && <p>Expires: {browseDate(certificate.expiresOn)} {certificate.expiresOn < browseToday() ? '(Expired)' : ''}</p>}</article>)}
        <h3>Upcoming sessions</h3>
        {upcomingSessions.length ? <ul>{upcomingSessions.map((session) => <li key={session.id}><Link to={`/member/sessions/${session.id}?${new URLSearchParams({ returnTo: `/member/classes?coachId=${coachId}` })}`}>{session.className || session.sportName} - {browseDate(session.sessionDate)} {session.startTime.slice(0, 5)}</Link></li>)}</ul> : <p className="browse-muted">No upcoming sessions.</p>}
        <Link className={buttonClassName('secondary')} to={`/member/classes?coachId=${coachId}`}>Find this coach's sessions</Link>
      </section>
    </div>}
  </Modal>
}
