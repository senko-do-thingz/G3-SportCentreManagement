import { useCallback, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { memberTrainingApi, type AttendanceRecord, type TrainingResult } from '../../api/memberTrainingApi'
import { Badge, Card, EmptyState, ErrorState, PageHeader, Pagination, Tabs } from '../../components/ui'
import { Spinner } from '../../components/ui/Spinner'
import { EMPTY_VALUE, formatDateTime } from '../../utils/format'
import { MemberTrainingNav } from './MemberTrainingNav'
import { MemberTrainingPreviewNotice } from './MemberTrainingPreviewNotice'
import { ATTENDANCE_LABEL, ATTENDANCE_TONE, metricValue, sessionTitle, sessionWhen } from './trainingPresentation'
import { useMemberResource } from './useMemberResource'

const PAGE_SIZE = 10
const TABS = [{ id: 'attendance', label: 'Attendance' }, { id: 'results', label: 'Training results' }]

/**
 * Attendance and Training Results (NX-15, F4-REQ-05): the member's own history only. The API has
 * no member id parameter, so another member's rows cannot be requested. Not recorded is shown as
 * its own state and a missing metric is never shown as zero.
 */
export function MemberTrainingHistoryPage() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'results' ? 'results' : 'attendance'
  return (
    <div className="page member-training-page">
      <PageHeader
        eyebrow="Training"
        title="Attendance and Results"
        description="Your own attendance history and the result recorded for each session."
      />
      <MemberTrainingNav />
      <MemberTrainingPreviewNotice />
      <Tabs
        items={TABS}
        value={tab}
        onChange={(id) => setParams(id === 'results' ? { tab: 'results' } : {}, { replace: true })}
        label="Attendance and results"
      >
        {(selected) => (selected === 'results' ? <ResultsPanel /> : <AttendancePanel />)}
      </Tabs>
    </div>
  )
}

function AttendancePanel() {
  const [page, setPage] = useState(0)
  const load = useCallback((signal: AbortSignal) => memberTrainingApi.attendance({ page, size: PAGE_SIZE, signal }), [page])
  const [attendance, reload] = useMemberResource(load)
  return (
    <Card title="Attendance history" aria-label="Attendance history">
      {attendance.status === 'loading' && <Spinner label="Loading your attendance" />}
      {attendance.status === 'error' && <ErrorState title="We could not load your attendance" error={attendance.error} onRetry={reload} />}
      {attendance.status === 'ready' && attendance.data.content.length === 0 && (
        <EmptyState title="No attendance yet" description="Sessions you attend appear here after your coach or the front desk records them." />
      )}
      {attendance.status === 'ready' && attendance.data.content.length > 0 && (
        <>
          <ul className="training-list">
            {attendance.data.content.map((row) => <AttendanceItem key={row.id} row={row} />)}
          </ul>
          <Pagination page={attendance.data.page} pageSize={attendance.data.size} totalItems={attendance.data.totalElements} onPageChange={setPage} />
        </>
      )}
    </Card>
  )
}

function AttendanceItem({ row }: { row: AttendanceRecord }) {
  const arrived = row.arrivedAt ? formatDateTime(row.arrivedAt) : EMPTY_VALUE
  return (
    <li className="training-item">
      <div className="training-item__head">
        <h3 className="training-item__title">{sessionTitle(row)}</h3>
        <Badge tone={ATTENDANCE_TONE[row.state]}>{ATTENDANCE_LABEL[row.state]}</Badge>
      </div>
      <p className="training-item__meta">{sessionWhen(row)}{row.facilityName ? ` - ${row.facilityName}` : ''}{row.coachName ? ` - Coach ${row.coachName}` : ''}</p>
      {arrived !== EMPTY_VALUE && <p className="training-item__meta">Arrived {arrived}</p>}
    </li>
  )
}

function ResultsPanel() {
  const [page, setPage] = useState(0)
  const load = useCallback((signal: AbortSignal) => memberTrainingApi.results({ page, size: PAGE_SIZE, signal }), [page])
  const [results, reload] = useMemberResource(load)
  return (
    <Card title="Training results" aria-label="Training results">
      {results.status === 'loading' && <Spinner label="Loading your training results" />}
      {results.status === 'error' && <ErrorState title="We could not load your training results" error={results.error} onRetry={reload} />}
      {results.status === 'ready' && results.data.content.length === 0 && (
        <EmptyState title="No results yet" description="Results your coach records for your sessions appear here." />
      )}
      {results.status === 'ready' && results.data.content.length > 0 && (
        <>
          <ul className="training-list">
            {results.data.content.map((row) => <ResultItem key={row.id} row={row} />)}
          </ul>
          <Pagination page={results.data.page} pageSize={results.data.size} totalItems={results.data.totalElements} onPageChange={setPage} />
        </>
      )}
    </Card>
  )
}

function ResultItem({ row }: { row: TrainingResult }) {
  return (
    <li className="training-item">
      <div className="training-item__head">
        <h3 className="training-item__title">{sessionTitle(row)}</h3>
      </div>
      <p className="training-item__meta">{sessionWhen(row)}{row.coachName ? ` - Coach ${row.coachName}` : ''}</p>
      {row.headline && <p className="training-item__text"><strong>{row.headline}</strong></p>}
      {row.metrics.length > 0 && (
        <dl className="training-metrics">
          {row.metrics.map((metric) => (
            <div key={metric.label} className="training-metrics__metric">
              <dt>{metric.label}</dt>
              <dd className={metric.value === null ? 'training-metrics__missing' : undefined}>{metricValue(metric)}</dd>
            </div>
          ))}
        </dl>
      )}
      {row.comment && <p className="training-item__text">{row.comment}</p>}
      {row.recordedAt && <p className="training-item__meta">Recorded {formatDateTime(row.recordedAt)}</p>}
    </li>
  )
}
