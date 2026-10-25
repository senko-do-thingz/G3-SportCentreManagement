import { useCallback, useEffect, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'
import { memberSessionsApi } from '../../api/memberSessionsApi'
import { packagesApi, type Sport } from '../../api/packagesApi'
import { Button, Card, EmptyState, Input, PageLoader, Pagination, Select } from '../../components/ui'
import { BrowseError } from './BrowseError'
import { MemberScheduleNav } from './MemberScheduleNav'
import { SessionCard } from './SessionCard'
import { browseToday } from './memberScheduleDates'
import { Photo } from '../../media/Photo'
import { sportPhotoFor } from './sportImages'
import { BROWSE_FORMATS, BROWSE_LEVELS, browseFilters, browseRangeError, updateBrowseParams } from './scheduleFilters'
import { useMemberResource } from './useMemberResource'

const loadSports = (signal: AbortSignal) => packagesApi.sports({ signal })

function SportPicker({ sports, selected, onChange }: { sports: Sport[]; selected?: number; onChange: (value: string) => void }) {
  return <fieldset className="class-discovery__picker">
    <legend>Sport</legend>
    <div className="class-discovery__sport-grid">
      <button type="button" className={`class-discovery__sport-choice class-discovery__sport-choice--all${selected === undefined ? ' is-selected' : ''}`} aria-pressed={selected === undefined} onClick={() => onChange('')}>
        <span className="class-discovery__all-mark" aria-hidden="true">ALL</span>
        <span>All sports</span>
      </button>
      {sports.map((sport) => <button type="button" className={`class-discovery__sport-choice${selected === sport.id ? ' is-selected' : ''}`} aria-pressed={selected === sport.id} key={sport.id} onClick={() => onChange(String(sport.id))}>
        <Photo {...sportPhotoFor(sport.name, 480)} alt="" sizes="160px" width="480" height="320" loading="lazy" />
        <span>{sport.name}</span>
      </button>)}
    </div>
  </fieldset>
}

function LevelPicker({ selected, onChange }: { selected?: string; onChange: (value: string) => void }) {
  return <fieldset className="class-discovery__picker class-discovery__level-picker">
    <legend>Level</legend>
    <div className="class-discovery__level-grid">
      {[{ value: '', label: 'All levels' }, ...BROWSE_LEVELS].map((option, index) => <button type="button" className={`class-discovery__level-choice${(selected ?? '') === option.value ? ' is-selected' : ''}`} aria-pressed={(selected ?? '') === option.value} key={option.value || 'all'} onClick={() => onChange(option.value)}>
        <span className="class-discovery__level-mark" aria-hidden="true">{index === 0 ? <span>ALL</span> : <>{[0, 1, 2].map((bar) => <i className={bar < index ? 'is-active' : ''} key={bar} />)}</>}</span>
        <span>{option.label}</span>
      </button>)}
    </div>
  </fieldset>
}

export function FindClassesPage() {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const [today] = useState(browseToday)
  const { sportId, format, level, coachId, from = today, to = today, page = 0 } = browseFilters(params, today)
  const rangeError = browseRangeError(from, to)
  const [sports, retrySports] = useMemberResource(loadSports)
  const sportRows = sports.status === 'ready' ? sports.data : []
  const loader = useCallback((signal: AbortSignal) => rangeError ? Promise.resolve({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }) : memberSessionsApi.list({ sportId, from, to, format, level, coachId, page, size: 20 }, signal), [rangeError, sportId, from, to, format, level, coachId, page])
  const [resource, reload] = useMemberResource(loader)
  const coachesLoader = useCallback(async (signal: AbortSignal) => {
    if (rangeError || format === 'SELF_TRAINING') return []
    const rows = await memberSessionsApi.all({ sportId, from, to, format, level }, signal)
    return [...new Map(rows.filter((row) => row.coachId !== null).map((row) => [row.coachId!, { value: String(row.coachId), label: row.coachName || `Coach #${row.coachId}` }])).values()].sort((a, b) => a.label.localeCompare(b.label))
  }, [rangeError, sportId, from, to, format, level])
  const [coaches, retryCoaches] = useMemberResource(coachesLoader)
  const coachOptions = coaches.status === 'ready' ? coaches.data : []
  const options = coachId && !coachOptions.some((option) => option.value === String(coachId)) ? [{ value: String(coachId), label: `Coach #${coachId} (selected)` }, ...coachOptions] : coachOptions
  const returnTo = location.pathname + location.search
  function change(key: string, value: string) { setParams((previous) => updateBrowseParams(previous, key, value)) }
  useEffect(() => {
    if (!rangeError && resource.status === 'ready' && page > 0 && page >= resource.data.totalPages) setParams((previous) => updateBrowseParams(previous, 'page', String(Math.max(0, resource.data.totalPages - 1))), { replace: true })
  }, [rangeError, resource, page, setParams])
  return <div className="page browse-page class-discovery">
    <MemberScheduleNav />
    <section className="class-discovery__hero" aria-labelledby="class-discovery-title">
      <div className="class-discovery__hero-copy">
        <p className="class-discovery__eyebrow">Member / Classes &amp; schedule</p>
        <h1 id="class-discovery-title">Find Classes</h1>
        <p>Find your sport, choose your level, and discover a session that fits your schedule.</p>
      </div>
      <div className="class-discovery__hero-art" aria-hidden="true">
        {sportRows.slice(0, 3).map((sport) => <Photo {...sportPhotoFor(sport.name, 480)} alt="" key={sport.id} sizes="170px" width="480" height="320" />)}
      </div>
    </section>
    <Card className="class-discovery__filter-card" title="Find your next session">
      <div className="class-discovery__filters">
        <SportPicker sports={sportRows} selected={sportId} onChange={(value) => change('sportId', value)} />
        <LevelPicker selected={level} onChange={(value) => change('level', value)} />
        <div className="browse-filters class-discovery__fields">
          <Input label="From date" type="date" value={from} onChange={(event) => change('from', event.target.value)} error={rangeError ?? undefined} />
          <Input label="To date" type="date" value={to} onChange={(event) => change('to', event.target.value)} />
          <Select label="Training format" placeholder="All formats" value={format ?? ''} options={BROWSE_FORMATS} onChange={(event) => change('format', event.target.value)} />
          <Select label="Coach" placeholder={coaches.status === 'loading' ? 'Loading coaches...' : 'All coaches'} value={coachId ? String(coachId) : ''} options={options} disabled={format === 'SELF_TRAINING'} hint="Coaches with sessions in the selected date range and filters." onChange={(event) => change('coachId', event.target.value)} />
        </div>
      </div>
      <div className="browse-actions"><Button variant="secondary" onClick={() => { const next = new URLSearchParams(params); for (const key of ['sportId', 'from', 'to', 'format', 'level', 'coachId', 'page']) next.delete(key); setParams(next) }}>Clear filters</Button><Button variant="ghost" onClick={reload}>Refresh results</Button></div>
      {sports.status === 'error' && <BrowseError error={sports.error} onRetry={retrySports} />}
      {coaches.status === 'error' && <div><p className="browse-muted">Coach filter options are unavailable. Session results are still shown.</p><BrowseError error={coaches.error} onRetry={retryCoaches} /></div>}
      {!rangeError && (resource.status === 'loading' ? <PageLoader label="Finding sessions" /> : resource.status === 'error' ? <BrowseError error={resource.error} onRetry={reload} /> : <>
        <div className="class-discovery__results-heading"><div><p className="class-discovery__eyebrow">Your next session</p><h2>Explore the lineup</h2></div><p className="browse-muted" role="status">{resource.data.totalElements} matching sessions</p></div>
        {!resource.data.content.length ? <EmptyState title="No matching sessions" description="Try different dates or clear your filters." /> : <div className="browse-grid">{resource.data.content.map((session) => <SessionCard session={session} returnTo={returnTo} key={session.id} />)}</div>}
        <Pagination page={resource.data.page} pageSize={resource.data.size} totalItems={resource.data.totalElements} onPageChange={(value) => change('page', String(value))} />
      </>)}
    </Card>
  </div>
}
