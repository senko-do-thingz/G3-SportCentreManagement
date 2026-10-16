import { useCallback, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../../api/client'
import { managerClassesApi, type ClassLevel, type ManagerClass } from '../../api/managerClassesApi'
import { packagesApi } from '../../api/packagesApi'
import { Button, Card, Input, PageHeader, PageLoader, Select, useToast } from '../../components/ui'
import { ManagerError } from './ManagerShared'
import { useManagerForm, useResource } from './useManagerTools'

const loadLookups = async (signal: AbortSignal) => {
  const [sports, ages] = await Promise.all([packagesApi.sports({ signal }), managerClassesApi.ageGroups(signal)])
  return { sports, ages }
}
interface CoachProfile { name: string; headline: string | null; specialties: string | null; yearsExperience: number | null; sports: { id: number; name: string }[]; certifications: { id: number; name: string; issuer: string; verified: boolean }[] }
const FIELDS = ['name', 'sportId', 'ageGroupId', 'level', 'coachId', 'maxMembers'] as const
const levelOptions = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'].map((value) => ({ value, label: value[0] + value.slice(1).toLowerCase() }))

export function ClassEditorPage() {
  const { classId } = useParams()
  const loader = useCallback((signal: AbortSignal) => classId ? managerClassesApi.get(Number(classId), signal) : Promise.resolve(null), [classId])
  const [item, retry] = useResource(loader)
  const [lookups, retryLookups] = useResource(loadLookups)
  if (item.status === 'error') return <ManagerError error={item.error} onRetry={retry} />
  if (lookups.status === 'error') return <ManagerError error={lookups.error} onRetry={retryLookups} />
  if (item.status !== 'ready' || lookups.status !== 'ready') return <PageLoader label="Loading class editor" />
  return <ClassForm key={classId ?? 'new'} item={item.data} lookups={lookups.data} />
}

function ClassForm({ item, lookups }: { item: ManagerClass | null; lookups: Awaited<ReturnType<typeof loadLookups>> }) {
  const navigate = useNavigate()
  const toast = useToast()
  const ref = useRef<HTMLFormElement>(null)
  const savedId = useRef<number | null>(null)
  const [draft, setDraft] = useState({ name: item?.name ?? '', sportId: item ? String(item.sportId) : '', ageGroupId: item ? String(item.ageGroupId) : '', level: item?.level ?? 'BEGINNER', coachId: item ? String(item.coachId) : '', maxMembers: String(item?.maxMembers ?? 20) })
  const coachLoader = useCallback((signal: AbortSignal) => managerClassesApi.coaches(draft.sportId ? Number(draft.sportId) : undefined, signal), [draft.sportId])
  const [coaches, retryCoaches] = useResource(coachLoader)
  const profileLoader = useCallback((signal: AbortSignal) => draft.coachId ? api.get<CoachProfile>(`/coaches/${draft.coachId}`, { signal }) : Promise.resolve(null), [draft.coachId])
  const [profile, retryProfile] = useResource(profileLoader)
  const form = useManagerForm(ref, FIELDS, () => {
    const errors: Record<string, string> = {}
    if (!draft.name.trim() || draft.name.trim().length > 100) errors.name = 'Enter a class name of 1 to 100 characters.'
    if (!lookups.sports.some((sport) => sport.id === Number(draft.sportId))) errors.sportId = 'Choose a sport.'
    if (!lookups.ages.some((age) => age.id === Number(draft.ageGroupId))) errors.ageGroupId = 'Choose an age group.'
    if (coaches.status !== 'ready' || !coaches.data.some((coach) => coach.id === Number(draft.coachId))) errors.coachId = 'Choose an available coach qualified for this sport.'
    if (!Number.isInteger(Number(draft.maxMembers)) || Number(draft.maxMembers) < 1 || Number(draft.maxMembers) > 100) errors.maxMembers = 'Capacity must be a whole number from 1 to 100.'
    return errors
  }, async () => {
    const body = { name: draft.name.trim(), sportId: Number(draft.sportId), ageGroupId: Number(draft.ageGroupId), level: draft.level as ClassLevel, coachId: Number(draft.coachId), maxMembers: Number(draft.maxMembers) }
    const saved = item ? await managerClassesApi.update(item.id, body) : await managerClassesApi.create(body)
    savedId.current = saved.id
  }, () => { toast.success('Class saved'); navigate(item ? '/manager/classes' : `/manager/schedule?classId=${savedId.current}&create=1`) })
  function change(key: keyof typeof draft, value: string) { setDraft((current) => ({ ...current, [key]: value, ...(key === 'sportId' ? { coachId: '' } : {}) })); form.clear(key) }
  return <div className="page manager-page">
    <PageHeader eyebrow="Class management" title={item ? 'Edit Class' : 'Create Class'} description="Define the class and assign a qualified coach before scheduling sessions." />
    <div className="f2-editor-layout">
      <Card title="Class details">
        <form ref={ref} onSubmit={form.submit} noValidate className="manager-form">
          {form.error && <p role="alert" className="notice notice--danger">{form.error}</p>}
          <fieldset disabled={form.saving} className="manager-form-grid">
            <Input label="Class name" name="name" value={draft.name} onChange={(event) => change('name', event.target.value)} error={form.errors.name} required />
            <Select label="Sport" name="sportId" placeholder="Choose a sport" value={draft.sportId} options={lookups.sports.map((sport) => ({ value: String(sport.id), label: sport.name }))} onChange={(event) => change('sportId', event.target.value)} disabled={Boolean(item?.upcomingSessions)} error={form.errors.sportId} hint={item?.upcomingSessions ? 'Sport cannot change while upcoming sessions exist.' : undefined} required />
            <Select label="Age group" name="ageGroupId" placeholder="Choose an age group" value={draft.ageGroupId} options={lookups.ages.map((age) => ({ value: String(age.id), label: age.label }))} onChange={(event) => change('ageGroupId', event.target.value)} error={form.errors.ageGroupId} required />
            <Select label="Level" name="level" value={draft.level} options={levelOptions} onChange={(event) => change('level', event.target.value)} error={form.errors.level} required />
            <Select label="Coach" name="coachId" placeholder={coaches.status === 'loading' ? 'Loading coaches...' : coaches.status === 'ready' && !coaches.data.length ? 'No qualified coaches available' : draft.sportId ? 'Choose a qualified coach' : 'Choose a coach'} value={draft.coachId} options={coaches.status === 'ready' ? coaches.data.map((coach) => ({ value: String(coach.id), label: coach.fullName })) : []} onChange={(event) => change('coachId', event.target.value)} error={form.errors.coachId} hint={draft.sportId ? 'Only active coaches qualified for the selected sport are listed.' : 'Choose a sport to filter coaches by qualification.'} disabled={coaches.status !== 'ready'} required />
            <Input label="Maximum members" name="maxMembers" type="number" min="1" max="100" step="1" value={draft.maxMembers} onChange={(event) => change('maxMembers', event.target.value)} error={form.errors.maxMembers} required />
          </fieldset>
          {coaches.status === 'error' && <ManagerError error={coaches.error} onRetry={retryCoaches} />}
          <Button variant="ghost" disabled={form.saving || coaches.status === 'loading'} onClick={retryCoaches}>Refresh coaches</Button>
          {draft.sportId && coaches.status === 'ready' && !coaches.data.length && <p className="notice notice--info">No qualified coaches are available. Check coach assignments in <Link to="/manager">User Management</Link>.</p>}
          <div className="manager-form-actions"><Button type="submit" loading={form.saving}>{item ? 'Save class' : 'Save & schedule'}</Button><Button variant="secondary" disabled={form.saving} onClick={() => navigate('/manager/classes')}>Cancel</Button></div>
        </form>
      </Card>
      <Card title="Coach qualification">
        {!draft.coachId ? <p>Select a coach to view qualifications.</p> : profile.status === 'loading' ? <PageLoader label="Loading qualifications" /> : profile.status === 'error' ? <ManagerError error={profile.error} onRetry={retryProfile} /> : profile.data && <div className="manager-cell"><h3>{profile.data.name}</h3><p>{profile.data.headline || 'Coach profile'}</p><p>{profile.data.yearsExperience ?? 0} years of experience</p><p>{profile.data.specialties || 'No specialties listed.'}</p><p>Sports: {profile.data.sports.map((sport) => sport.name).join(', ') || 'None listed'}</p><h4>Certifications</h4>{profile.data.certifications.length ? profile.data.certifications.map((certificate) => <p key={certificate.id}>{certificate.name} - {certificate.issuer} ({certificate.verified ? 'Verified' : 'Not verified'})</p>) : <p>No certifications listed.</p>}</div>}
      </Card>
    </div>
  </div>
}
