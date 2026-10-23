import { useCallback, useRef, useState, type ChangeEvent } from 'react'
import { avatarSrc } from '../../api/avatarUrl'
import { getErrorMessage } from '../../api/errors'
import { managerUsersApi } from '../../api/managerUsersApi'
import { Button, Checkbox, Input, Modal, PageLoader, TextArea } from '../../components/ui'
import type { ManagerCoachProfile } from '../../types/manager'
import { ManagerError } from './ManagerShared'
import { MAX_UPLOAD_BYTES, PHOTO_TYPES, photoFileProblem, preparePhoto } from './avatarImage'
import { validateCoachProfile } from './managerValidation'
import { useManagerForm, useResource } from './useManagerTools'

const FIELDS = ['avatarUrl', 'headline', 'bio', 'yearsExperience', 'specialties', 'isPublic', 'displayOrder'] as const

interface Draft { avatarUrl: string; headline: string; bio: string; yearsExperience: string; specialties: string; isPublic: boolean; displayOrder: string }

const toDraft = (profile: ManagerCoachProfile): Draft => ({
  avatarUrl: profile.avatarUrl ?? '',
  headline: profile.headline ?? '',
  bio: profile.bio ?? '',
  yearsExperience: profile.yearsExperience === null ? '' : String(profile.yearsExperience),
  specialties: profile.specialties ?? '',
  isPublic: profile.isPublic,
  displayOrder: String(profile.displayOrder),
})

/** Manager edit of a coach's public profile (photo link, Home card text, visibility and order). */
export function EditCoachProfileModal({ userId, name, onClose, onSuccess }: { userId: number; name: string; onClose: () => void; onSuccess: () => void }) {
  const load = useCallback((signal: AbortSignal) => managerUsersApi.coachProfile(userId, signal), [userId])
  const [profile, retry] = useResource(load)
  return <Modal open title="Edit coach profile" description={`${name} - shown on the Home page coach cards.`} onClose={onClose}>
    {profile.status === 'loading' && <PageLoader label="Loading coach profile" />}
    {profile.status === 'error' && <ManagerError error={profile.error} onRetry={retry} />}
    {profile.status === 'ready' && <CoachProfileForm userId={userId} initial={toDraft(profile.data)} name={profile.data.fullName} onClose={onClose} onSuccess={onSuccess} />}
  </Modal>
}

function CoachProfileForm({ userId, initial, name, onClose, onSuccess }: { userId: number; initial: Draft; name: string; onClose: () => void; onSuccess: () => void }) {
  const [draft, setDraft] = useState<Draft>(initial)
  const formRef = useRef<HTMLFormElement>(null)
  const form = useManagerForm(formRef, FIELDS, () => validateCoachProfile(draft), () => managerUsersApi.updateCoachProfile(userId, {
    avatarUrl: draft.avatarUrl.trim(),
    headline: draft.headline.trim(),
    bio: draft.bio.trim(),
    yearsExperience: draft.yearsExperience.trim() ? Number(draft.yearsExperience) : null,
    specialties: draft.specialties.trim(),
    isPublic: draft.isPublic,
    displayOrder: Number(draft.displayOrder),
  }), onSuccess)
  function change<K extends keyof Draft>(key: K, value: Draft[K]) {
    setDraft((current) => ({ ...current, [key]: value }))
    form.clear(key)
  }
  return <form ref={formRef} className="manager-form" onSubmit={form.submit} noValidate aria-busy={form.saving}>
    {form.error && <p className="notice notice--danger" role="alert">{form.error}</p>}
    <fieldset className="manager-form-grid" disabled={form.saving}>
      <PhotoField userId={userId} name={name} url={draft.avatarUrl} error={form.errors.avatarUrl} onChange={(url) => change('avatarUrl', url)} />
      <div className="manager-form-full">
        <Input label="Headline" name="headline" value={draft.headline} onChange={(event) => change('headline', event.target.value)} error={form.errors.headline} maxLength={150} hint="Who the coach teaches, in 4-8 words." />
      </div>
      <div className="manager-form-full">
        <TextArea label="Short bio" name="bio" value={draft.bio} onChange={(event) => change('bio', event.target.value)} error={form.errors.bio} maxLength={1000} rows={3} hint="1-2 short sentences: teaching style and what members get. The card shows three lines." />
      </div>
      <Input label="Years of coaching" name="yearsExperience" type="number" inputMode="numeric" min={0} max={60} step={1} value={draft.yearsExperience} onChange={(event) => change('yearsExperience', event.target.value)} error={form.errors.yearsExperience} />
      <Input label="Specialties" name="specialties" value={draft.specialties} onChange={(event) => change('specialties', event.target.value)} error={form.errors.specialties} maxLength={255} hint="Up to three, separated by commas." />
      <Input label="Display order" name="displayOrder" type="number" inputMode="numeric" min={0} max={999} step={1} value={draft.displayOrder} onChange={(event) => change('displayOrder', event.target.value)} error={form.errors.displayOrder} hint="Lower numbers appear first." required />
      <div className="manager-form-check">
        <Checkbox label="Show on the Home page" name="isPublic" checked={draft.isPublic} onChange={(event) => change('isPublic', event.target.checked)} />
      </div>
    </fieldset>
    <div className="manager-form-actions"><Button type="submit" loading={form.saving}>Save profile</Button><Button variant="secondary" disabled={form.saving} onClick={onClose}>Cancel</Button></div>
  </form>
}

/**
 * Profile photo: Upload photo opens the file picker, the browser crops and resizes the photo and it is saved at once.
 * Remove photo clears the link; the stored photo is deleted when the profile is saved.
 */
function PhotoField({ userId, name, url, error, onChange }: { userId: number; name: string; url: string; error?: string; onChange: (url: string) => void }) {
  const input = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [message, setMessage] = useState<{ tone: 'error' | 'success'; text: string } | null>(null)

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    const problem = photoFileProblem(file)
    if (problem) { setMessage({ tone: 'error', text: problem }); return }
    setUploading(true)
    setMessage(null)
    try {
      const photo = await preparePhoto(file)
      if (photo.size > MAX_UPLOAD_BYTES) throw new Error('This photo is still larger than 2 MB after resizing. Choose a smaller photo.')
      const saved = await managerUsersApi.uploadCoachPhoto(userId, photo)
      onChange(saved.avatarUrl ?? '')
      setMessage({ tone: 'success', text: 'Photo uploaded and saved.' })
    } catch (cause) {
      setMessage({ tone: 'error', text: getErrorMessage(cause) })
    } finally {
      setUploading(false)
    }
  }

  return <div className="manager-form-full manager-photo-field">
    <PhotoPreview key={url} url={url} name={name} />
    <div className="manager-photo-controls">
      <span className="manager-photo-label" id={`coach-photo-${userId}`}>Profile photo</span>
      <div className="manager-photo-actions">
        <input ref={input} type="file" accept={PHOTO_TYPES.join(',')} className="manager-photo-input" aria-labelledby={`coach-photo-${userId}`} tabIndex={-1} onChange={upload} />
        <Button variant="secondary" size="sm" loading={uploading} onClick={() => input.current?.click()}>{url ? 'Change photo' : 'Upload photo'}</Button>
        {url && <Button variant="ghost" size="sm" disabled={uploading} onClick={() => { onChange(''); setMessage({ tone: 'success', text: 'Photo will be removed when you save the profile.' }) }}>Remove photo</Button>}
      </div>
      <p className="manager-muted">JPEG, PNG or WebP. A clear, front-facing photo of the coach, used with their permission. It is cropped to a square and saved as soon as it is uploaded.</p>
      {(message || error) && <p className={message?.tone === 'success' && !error ? 'manager-photo-status' : 'field__error'} role={message?.tone === 'success' && !error ? 'status' : 'alert'}>{error ?? message?.text}</p>}
    </div>
  </div>
}

/** Preview of the current photo; shows initials when there is no photo or it does not load. */
function PhotoPreview({ url, name }: { url: string; name: string }) {
  const [failed, setFailed] = useState(false)
  const src = avatarSrc(url)
  const initials = name.split(/\s+/).filter(Boolean).map((part, index, parts) => (index === 0 || index === parts.length - 1 ? part[0] : '')).join('').toUpperCase()
  return <span className="manager-photo-preview" aria-hidden="true">
    {src && !failed ? <img src={src} alt="" width="96" height="96" onError={() => setFailed(true)} /> : initials}
  </span>
}
