import { useMemo } from 'react'
import type { CoachSessionRecord } from '../../api/coachScheduleApi'
import { sessionRouteReference, sessionIdText } from '../../api/sessionIdentity'
import { browseDay, browseDate } from '../member/memberScheduleDates'
import '../../styles/coachTimeGrid.css'

interface CoachTimeGridScheduleProps {
  dates: string[]
  sessions: CoachSessionRecord[]
  selectedSession: CoachSessionRecord | null
  onSelectSession: (session: CoachSessionRecord) => void
  today: string
}

const START_HOUR = 6 // 06:00 sang
const END_HOUR = 22  // 22:00 toi
const HOUR_HEIGHT = 96 // 96px cho moi gio -> 1.6px tuong ung 1 phut
const PIXELS_PER_MINUTE = HOUR_HEIGHT / 60
const TOTAL_HOURS = END_HOUR - START_HOUR // 16 gio = 960 phut
const BASE_MINUTES = START_HOUR * 60 // 360 phut

function timeToMinutes(timeStr: string): number {
  if (!timeStr) return 0
  const [hours, minutes] = timeStr.slice(0, 5).split(':').map(Number)
  return (hours || 0) * 60 + (minutes || 0)
}

function displayTime(value: string): string {
  return value.slice(0, 5)
}

function sessionTitle(session: CoachSessionRecord): string {
  return session.className || `${session.sportName} session`
}

interface LayoutSessionItem {
  session: CoachSessionRecord
  top: number
  height: number
  leftPercent: number
  widthPercent: number
  isConflicted: boolean
}

/**
 * Thuat toan gom nhom va chia cot con cho cac ca bi trung lap thoi gian (Clustered Overlap Layout).
 * Dam bao cac ca trung gio hien thi song song (side-by-side) thay vi de mat noi dung.
 */
function layoutDaySessions(daySessions: CoachSessionRecord[]): LayoutSessionItem[] {
  if (!daySessions || daySessions.length === 0) {
    return []
  }

  // Sap xep cac ca theo gio bat dau tang dan, neu cung gio thi uu tien gio ket thuc
  const sorted = [...daySessions].sort((a, b) =>
    a.startTime.localeCompare(b.startTime) || a.endTime.localeCompare(b.endTime) || a.id - b.id
  )

  // 1. Chia thanh cac cum xung dot (Clusters)
  const clusters: CoachSessionRecord[][] = []
  let currentCluster: CoachSessionRecord[] = []
  let clusterEnd = 0

  for (const session of sorted) {
    const start = timeToMinutes(session.startTime)
    const end = timeToMinutes(session.endTime)

    if (currentCluster.length === 0) {
      currentCluster.push(session)
      clusterEnd = end
    } else {
      if (start < clusterEnd) {
        // Co giao thoa thoi gian voi cum hien tai
        currentCluster.push(session)
        clusterEnd = Math.max(clusterEnd, end)
      } else {
        // Khong trung, tao cum moi
        clusters.push(currentCluster)
        currentCluster = [session]
        clusterEnd = end
      }
    }
  }
  if (currentCluster.length > 0) {
    clusters.push(currentCluster)
  }

  const results: LayoutSessionItem[] = []

  // 2. Tinh toan cot con (Sub-column allocation) cho tung ca trong cum
  for (const cluster of clusters) {
    const isConflicted = cluster.length > 1
    const columnEnds: number[] = []
    const sessionAssignments: { session: CoachSessionRecord; colIndex: number }[] = []

    for (const session of cluster) {
      const start = timeToMinutes(session.startTime)
      const end = timeToMinutes(session.endTime)
      let placedColumn = -1

      for (let col = 0; col < columnEnds.length; col++) {
        if (start >= columnEnds[col]) {
          placedColumn = col
          columnEnds[col] = end
          break
        }
      }

      if (placedColumn === -1) {
        placedColumn = columnEnds.length
        columnEnds.push(end)
      }

      sessionAssignments.push({ session, colIndex: placedColumn })
    }

    const totalColumns = columnEnds.length

    for (const { session, colIndex } of sessionAssignments) {
      const startMin = Math.max(BASE_MINUTES, timeToMinutes(session.startTime))
      const endMin = Math.min(END_HOUR * 60, Math.max(startMin + 15, timeToMinutes(session.endTime)))

      const top = Math.round((startMin - BASE_MINUTES) * PIXELS_PER_MINUTE)
      const height = Math.max(44, Math.round((endMin - startMin) * PIXELS_PER_MINUTE))
      const widthPercent = 100 / totalColumns
      const leftPercent = colIndex * widthPercent

      results.push({
        session,
        top,
        height,
        leftPercent,
        widthPercent,
        isConflicted,
      })
    }
  }

  return results
}

export function CoachTimeGridSchedule({
  dates,
  sessions,
  selectedSession,
  onSelectSession,
  today,
}: CoachTimeGridScheduleProps) {
  const hours = useMemo(() => Array.from({ length: TOTAL_HOURS }, (_, i) => START_HOUR + i), [])
  const selectedKey = selectedSession ? sessionRouteReference(selectedSession) : null

  return (
    <div className="coach-timegrid-wrapper">
      {/* Thanh tieu de chua 7 ngay */}
      <div className="coach-timegrid-header">
        <div className="coach-timegrid-time-gutter-header" aria-hidden="true" />
        <div className="coach-timegrid-header-columns">
          {dates.map((date) => {
            const isToday = date === today
            return (
              <div
                key={date}
                className={`coach-timegrid-col-header${isToday ? ' is-today' : ''}`}
                id={`coach-schedule-day-${date}`}
              >
                <span className="coach-timegrid-day-name">{browseDay(date).toUpperCase()}</span>
                <time className="coach-timegrid-date-text" dateTime={date}>
                  {browseDate(date)}
                </time>
              </div>
            )
          })}
        </div>
      </div>

      {/* Than bang lich gio va cac cot ca hoc */}
      <div
        className="coach-timegrid-body"
        style={{ height: `${TOTAL_HOURS * HOUR_HEIGHT}px`, minHeight: `${TOTAL_HOURS * HOUR_HEIGHT}px` }}
      >
        {/* Truc Y ben trai hien thi cac moc gio 06:00 den 22:00 */}
        <div className="coach-timegrid-time-gutter" aria-hidden="true">
          {hours.map((hour, idx) => (
            <span
              key={hour}
              className="coach-timegrid-hour-marker"
              style={{ top: `${idx * HOUR_HEIGHT}px` }}
            >
              {String(hour).padStart(2, '0')}:00
            </span>
          ))}
        </div>

        {/* Luoi 7 cot ngay */}
        <div className="coach-timegrid-days-container">
          {/* Cac duong ke ngang moc gio va nua gio */}
          <div className="coach-timegrid-gridlines" aria-hidden="true">
            {hours.map((hour, idx) => (
              <div key={hour}>
                <div
                  className="coach-timegrid-gridline"
                  style={{ top: `${idx * HOUR_HEIGHT}px` }}
                />
                <div
                  className="coach-timegrid-gridline coach-timegrid-gridline--half"
                  style={{ top: `${idx * HOUR_HEIGHT + HOUR_HEIGHT / 2}px` }}
                />
              </div>
            ))}
          </div>

          {/* 7 cot ngay tuong ung */}
          {dates.map((date) => {
            const isToday = date === today
            const dailySessions = sessions.filter((s) => s.sessionDate === date)
            const layoutItems = layoutDaySessions(dailySessions)

            return (
              <div
                key={date}
                className={`coach-timegrid-day-column${isToday ? ' is-today' : ''}`}
                aria-labelledby={`coach-schedule-day-${date}`}
              >
                {layoutItems.map(({ session, top, height, leftPercent, widthPercent, isConflicted }) => {
                  const sessionKey = sessionRouteReference(session)
                  const isSelected = selectedKey !== null && selectedKey === sessionKey

                  return (
                    <button
                      key={session.id}
                      type="button"
                      className={`coach-timegrid-card${isSelected ? ' is-selected' : ''}${
                        isConflicted ? ' is-conflicted' : ''
                      }`}
                      style={{
                        top: `${top}px`,
                        height: `${height}px`,
                        left: `${leftPercent}%`,
                        width: `calc(${widthPercent}% - 3px)`,
                      }}
                      aria-pressed={isSelected}
                      onClick={() => onSelectSession(session)}
                      title={isConflicted ? 'Time conflict: Overlapping teaching session!' : undefined}
                    >
                      <span className="coach-timegrid-card__time">
                        {displayTime(session.startTime)} - {displayTime(session.endTime)}
                      </span>
                      <span className="coach-timegrid-card__title">{sessionTitle(session)}</span>
                      <span className="coach-timegrid-card__id">{sessionIdText(session.sessionId)}</span>
                      {isConflicted && (
                        <span className="coach-timegrid-conflict-tag">CONFLICT</span>
                      )}
                    </button>
                  )
                })}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
