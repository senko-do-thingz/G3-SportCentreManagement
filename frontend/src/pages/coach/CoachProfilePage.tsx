import { useCallback, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { avatarSrc } from '../../api/avatarUrl'
import { coachProfilesApi, type CoachCertificationOption, type CoachSelfProfile } from '../../api/coachProfilesApi'
import { ApiError, getErrorMessage, splitFieldErrors } from '../../api/errors'
import { Badge, Button, CalendarDateField, Card, EmptyState, ErrorState, Input, Notice, PageHeader, PageLoader, Select, TextArea, useToast } from '../../components/ui'
import { useResource } from '../../hooks/useResource'
import { formatCalendarDate } from '../../utils/dateDisplay'
import { useFocusFirstInvalid } from '../../utils/useFocusFirstInvalid'
import { MAX_UPLOAD_BYTES, PHOTO_TYPES, photoFileProblem, preparePhoto } from '../manager/avatarImage'
import { validateCoachProfile } from '../manager/managerValidation'

const PROFILE_FIELDS = ['avatarUrl', 'headline', 'bio', 'yearsExperience', 'specialties'] as const

interface ProfileDraft {
  avatarUrl: string
  headline: string
  bio: string
  yearsExperience: string
  specialties: string
}

function toDraft(profile: CoachSelfProfile): ProfileDraft {
  return {
    avatarUrl: profile.avatarUrl ?? '',
    headline: profile.headline ?? '',
    bio: profile.bio ?? '',
    yearsExperience: profile.yearsExperience === null ? '' : String(profile.yearsExperience),
    specialties: profile.specialties ?? '',
  }
}

function optionLabel(opt: CoachCertificationOption): string {
  const parts = [opt.name]
  if (opt.issuer) parts.push(opt.issuer)
  if (opt.sportName) parts.push(opt.sportName)
  return parts.join(' - ')
}

// Trang ho so tu quan ly cua Coach (/coach/profile) de cap nhat thong tin gioi thieu va chung chi hien thi cho Member
export function CoachProfilePage() {
  const load = useCallback((signal: AbortSignal) => coachProfilesApi.getMyProfile(signal), [])
  const [resource, reload] = useResource(load)

  return (
    <div className="page coach-profile-page coach-ui">
      <PageHeader
        eyebrow="Coach workspace"
        title="My Profile"
        description="Customize your coaching biography, experience, specialties and certifications displayed to members."
      />
      {resource.status === 'loading' && <PageLoader label="Loading your coach profile" />}
      {resource.status === 'error' && (
        <ErrorState title="We could not load your coach profile" error={resource.error} onRetry={reload} />
      )}
      {resource.status === 'ready' && (
        <CoachProfileContent key={resource.data.userId} initial={resource.data} />
      )}
    </div>
  )
}

function CoachProfileContent({ initial }: { initial: CoachSelfProfile }) {
  const [profile, setProfile] = useState<CoachSelfProfile>(initial)
  return (
    <div className="profile-layout">
      <div className="profile-layout__main">
        <CoachPublicInfoCard profile={profile} onSaved={setProfile} />
        <CoachCertificationsCard profile={profile} onUpdated={setProfile} />
      </div>
      <aside className="profile-layout__side" aria-label="Coach profile summary">
        <Card title="Public preview">
          <div className="profile-summary">
            <CoachAvatarPreview url={profile.avatarUrl ?? ''} name={profile.fullName} />
            <p className="profile-summary__name">{profile.fullName}</p>
            <p className="profile-summary__role">{profile.headline || 'Sports coach'}</p>
            <p className="profile-summary__email">{profile.email}</p>
          </div>
          <dl className="summary-list">
            <div>
              <dt>Experience</dt>
              <dd>{profile.yearsExperience === null ? 'Not provided' : `${profile.yearsExperience} years`}</dd>
            </div>
            <div>
              <dt>Sports taught</dt>
              <dd>{profile.sports.map((s) => s.name).join(', ') || 'Not provided'}</dd>
            </div>
            <div>
              <dt>Specialties</dt>
              <dd>{profile.specialties || 'Not provided'}</dd>
            </div>
            <div>
              <dt>Certifications</dt>
              <dd>{profile.certifications.length}</dd>
            </div>
          </dl>
          <p className="notice notice--info profile-summary__note">
            Members see this profile when browsing classes and sessions so they can evaluate your coaching background.
          </p>
        </Card>
      </aside>
    </div>
  )
}

function CoachPublicInfoCard({
  profile,
  onSaved,
}: {
  profile: CoachSelfProfile
  onSaved: (updated: CoachSelfProfile) => void
}) {
  const toast = useToast()
  const [draft, setDraft] = useState<ProfileDraft>(() => toDraft(profile))
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const formRef = useRef<HTMLFormElement>(null)
  const focusFirstInvalid = useFocusFirstInvalid(formRef)

  function updateField<K extends keyof ProfileDraft>(key: K, value: ProfileDraft[K]) {
    setDraft((current) => ({ ...current, [key]: value }))
    setFieldErrors((current) => {
      if (!(key in current)) return current
      const next = { ...current }
      delete next[key]
      return next
    })
    setSuccess(null)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving) return
    setFormError(null)
    setSuccess(null)
    // Tai su dung bo quy tac xac thuc validateCoachProfile tu managerValidation
    const errors = validateCoachProfile({
      headline: draft.headline,
      bio: draft.bio,
      yearsExperience: draft.yearsExperience,
      specialties: draft.specialties,
      displayOrder: String(profile.displayOrder ?? 0),
    })
    delete errors.displayOrder
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      focusFirstInvalid()
      return
    }
    setSaving(true)
    try {
      const updated = await coachProfilesApi.updateMyProfile({
        avatarUrl: draft.avatarUrl.trim(),
        headline: draft.headline.trim(),
        bio: draft.bio.trim(),
        yearsExperience: draft.yearsExperience.trim() ? Number(draft.yearsExperience) : null,
        specialties: draft.specialties.trim(),
      })
      setDraft(toDraft(updated))
      onSaved(updated)
      setSuccess('Your coaching profile has been updated.')
      toast.success('Profile saved', 'Your coaching profile has been updated.')
    } catch (error) {
      if (error instanceof ApiError && error.hasFieldErrors) {
        const { fields, formMessage } = splitFieldErrors(error.fieldErrors, PROFILE_FIELDS)
        setFieldErrors(fields)
        setFormError(formMessage)
        if (Object.keys(fields).length > 0) focusFirstInvalid()
      } else {
        setFormError(getErrorMessage(error))
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card title="Coaching profile">
      {success && <p className="notice notice--success">{success}</p>}
      {formError && <p className="notice notice--danger" role="alert">{formError}</p>}
      <form ref={formRef} onSubmit={handleSubmit} noValidate aria-busy={saving} aria-label="Coaching profile">
        <fieldset className="profile-form" disabled={saving}>
          <CoachPhotoField
            userId={profile.userId}
            name={profile.fullName}
            url={draft.avatarUrl}
            error={fieldErrors.avatarUrl}
            onUploaded={(saved) => {
              updateField('avatarUrl', saved.avatarUrl ?? '')
              onSaved(saved)
            }}
            onChange={(url) => updateField('avatarUrl', url)}
          />
          <div className="profile-form__grid">
            <Input
              className="profile-form__wide"
              label="Headline"
              name="headline"
              value={draft.headline}
              onChange={(event) => updateField('headline', event.target.value)}
              error={fieldErrors.headline}
              maxLength={150}
              hint="Who you coach and your focus, in 4-8 words."
            />
            <Input
              label="Years of coaching"
              name="yearsExperience"
              type="number"
              inputMode="numeric"
              min={0}
              max={60}
              step={1}
              value={draft.yearsExperience}
              onChange={(event) => updateField('yearsExperience', event.target.value)}
              error={fieldErrors.yearsExperience}
            />
            <Input
              label="Specialties"
              name="specialties"
              value={draft.specialties}
              onChange={(event) => updateField('specialties', event.target.value)}
              error={fieldErrors.specialties}
              maxLength={255}
              hint="Up to three areas, separated by commas."
            />
            <div className="profile-form__wide">
              <TextArea
                label="Biography"
                name="bio"
                value={draft.bio}
                onChange={(event) => updateField('bio', event.target.value)}
                error={fieldErrors.bio}
                maxLength={1000}
                rows={4}
                hint="Describe your coaching methodology, background and what members gain in your sessions."
              />
            </div>
          </div>
          <div className="profile-form__actions">
            <Button type="submit" loading={saving}>
              Save profile
            </Button>
          </div>
        </fieldset>
      </form>
    </Card>
  )
}

function CoachPhotoField({
  userId,
  name,
  url,
  error,
  onUploaded,
  onChange,
}: {
  userId: number
  name: string
  url: string
  error?: string
  onUploaded: (saved: CoachSelfProfile) => void
  onChange: (url: string) => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [message, setMessage] = useState<{ tone: 'error' | 'success'; text: string } | null>(null)

  async function handleUpload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    const problem = photoFileProblem(file)
    if (problem) {
      setMessage({ tone: 'error', text: problem })
      return
    }
    setUploading(true)
    setMessage(null)
    try {
      const photo = await preparePhoto(file)
      if (photo.size > MAX_UPLOAD_BYTES) {
        throw new Error('This photo is still larger than 2 MB after resizing. Choose a smaller photo.')
      }
      const saved = await coachProfilesApi.uploadMyPhoto(photo)
      onUploaded(saved)
      setMessage({ tone: 'success', text: 'Photo uploaded and saved.' })
    } catch (cause) {
      setMessage({ tone: 'error', text: getErrorMessage(cause) })
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="manager-form-full manager-photo-field" style={{ marginBottom: 'var(--space-4)' }}>
      <CoachAvatarPreview key={url} url={url} name={name} />
      <div className="manager-photo-controls">
        <span className="manager-photo-label" id={`coach-self-photo-${userId}`}>Profile photo</span>
        <div className="manager-photo-actions">
          <input
            ref={inputRef}
            type="file"
            accept={PHOTO_TYPES.join(',')}
            className="manager-photo-input"
            aria-labelledby={`coach-self-photo-${userId}`}
            tabIndex={-1}
            onChange={handleUpload}
          />
          <Button variant="secondary" size="sm" loading={uploading} onClick={() => inputRef.current?.click()}>
            {url ? 'Change photo' : 'Upload photo'}
          </Button>
          {url && (
            <Button
              variant="ghost"
              size="sm"
              disabled={uploading}
              onClick={() => {
                onChange('')
                setMessage({ tone: 'success', text: 'Photo will be removed when you save the profile.' })
              }}
            >
              Remove photo
            </Button>
          )}
        </div>
        <p className="manager-muted">JPEG, PNG or WebP up to 2 MB. Cropped to a square and saved when uploaded.</p>
        {(message || error) && (
          <p
            className={message?.tone === 'success' && !error ? 'manager-photo-status' : 'field__error'}
            role={message?.tone === 'success' && !error ? 'status' : 'alert'}
          >
            {error ?? message?.text}
          </p>
        )}
      </div>
    </div>
  )
}

function CoachAvatarPreview({ url, name }: { url: string; name: string }) {
  const [failed, setFailed] = useState(false)
  const src = avatarSrc(url)
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .map((part, index, parts) => (index === 0 || index === parts.length - 1 ? part[0] : ''))
    .join('')
    .toUpperCase()
  return (
    <span className="manager-photo-preview avatar avatar--lg" aria-hidden="true">
      {src && !failed ? <img src={src} alt="" width="64" height="64" onError={() => setFailed(true)} /> : initials}
    </span>
  )
}

function CoachCertificationsCard({
  profile,
  onUpdated,
}: {
  profile: CoachSelfProfile
  onUpdated: (updated: CoachSelfProfile) => void
}) {
  const toast = useToast()
  const [selectedKey, setSelectedKey] = useState('')
  const [issuedOn, setIssuedOn] = useState('')
  const [expiresOn, setExpiresOn] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [removingId, setRemovingId] = useState<number | null>(null)

  const availableOptions = profile.certificationOptions ?? []
  const selectOptions = availableOptions.map((opt) => ({
    value: opt.key,
    label: optionLabel(opt),
  }))

  async function handleAdd(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (adding) return
    setError(null)
    const option = availableOptions.find((opt) => opt.key === selectedKey)
    if (!option) {
      setError('Choose a certification from the list.')
      return
    }
    if (issuedOn && expiresOn && expiresOn < issuedOn) {
      setError('Expiry date must not be before the issue date.')
      return
    }
    setAdding(true)
    try {
      const updated = await coachProfilesApi.addCertification({
        name: option.name,
        issuer: option.issuer,
        sportId: option.sportId,
        issuedOn: issuedOn || null,
        expiresOn: expiresOn || null,
      })
      onUpdated(updated)
      setSelectedKey('')
      setIssuedOn('')
      setExpiresOn('')
      toast.success('Certification added', `${option.name} was added to your profile.`)
    } catch (cause) {
      setError(getErrorMessage(cause))
    } finally {
      setAdding(false)
    }
  }

  async function handleRemove(id: number, name: string) {
    if (removingId !== null) return
    setError(null)
    setRemovingId(id)
    try {
      const updated = await coachProfilesApi.removeCertification(id)
      onUpdated(updated)
      toast.success('Certification removed', `${name} was removed from your profile.`)
    } catch (cause) {
      setError(getErrorMessage(cause))
    } finally {
      setRemovingId(null)
    }
  }

  return (
    <Card title="Certifications & qualifications">
      {error && <Notice tone="danger">{error}</Notice>}
      <form onSubmit={handleAdd} noValidate aria-busy={adding} aria-label="Add certification">
        <fieldset className="profile-form" disabled={adding}>
          <div className="profile-form__grid">
            <div className="profile-form__wide">
              <Select
                label="Available center certifications"
                name="certificationOption"
                placeholder={selectOptions.length ? 'Select a certification to add' : 'No center certifications available'}
                options={selectOptions}
                value={selectedKey}
                onChange={(event) => {
                  setSelectedKey(event.target.value)
                  setError(null)
                }}
              />
            </div>
            <CalendarDateField
              label="Issue date (optional)"
              name="issuedOn"
              value={issuedOn}
              onChange={(value) => {
                setIssuedOn(value)
                setError(null)
              }}
            />
            <CalendarDateField
              label="Expiry date (optional)"
              name="expiresOn"
              value={expiresOn}
              onChange={(value) => {
                setExpiresOn(value)
                setError(null)
              }}
            />
          </div>
          <div className="profile-form__actions">
            <Button type="submit" variant="secondary" loading={adding} disabled={selectOptions.length === 0}>
              Add certification
            </Button>
          </div>
        </fieldset>
      </form>

      <div style={{ marginTop: 'var(--space-6)' }}>
        {profile.certifications.length === 0 ? (
          <EmptyState
            title="No certifications listed yet"
            description="Select a certification from the center catalog above to display it on your public profile."
          />
        ) : (
          <ul className="dashboard-list" aria-label="Your certifications">
            {profile.certifications.map((cert) => (
              <li key={cert.id} className="dashboard-list__item">
                <div className="dashboard-list__text">
                  <span className="dashboard-list__title">{cert.name}</span>
                  <span className="dashboard-list__meta">
                    {[cert.issuer || 'Issuer not provided', cert.sportName].filter(Boolean).join(' - ')}
                  </span>
                  {(cert.issuedOn || cert.expiresOn) && (
                    <span className="dashboard-list__note">
                      {cert.issuedOn ? `Issued: ${formatCalendarDate(cert.issuedOn)}` : ''}
                      {cert.issuedOn && cert.expiresOn ? ' | ' : ''}
                      {cert.expiresOn ? `Expires: ${formatCalendarDate(cert.expiresOn)}` : ''}
                    </span>
                  )}
                </div>
                <div className="dashboard-list__actions">
                  <Badge tone={cert.verified ? 'success' : 'neutral'}>
                    {cert.verified ? 'Verified' : 'Not verified'}
                  </Badge>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    loading={removingId === cert.id}
                    disabled={removingId !== null}
                    onClick={() => void handleRemove(cert.id, cert.name)}
                    aria-label={`Remove ${cert.name}`}
                  >
                    Remove
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  )
}
