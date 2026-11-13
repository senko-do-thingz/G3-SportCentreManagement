import { useCallback } from 'react'
import { operationsReport } from '../../api/operationsReportApi'
import { Card, StatCard } from '../../components/ui'
import { formatCalendarDate } from '../../utils/dateDisplay'
import { TrainingData } from '../member/TrainingData'

export function OperationsSummary({ from, to }: { from: string; to: string }) {
  const load = useCallback((signal: AbortSignal) => operationsReport(from, to, signal), [from, to])
  return <Card title="Operations" >
    <p>Registrations and class bookings in {formatCalendarDate(from)} - {formatCalendarDate(to)}. Revenue and CSV export use the same date controls below.</p>
    <TrainingData load={load}>{report => <>
      <section className="finance-kpis" aria-label="Member registrations">
        <StatCard icon="users" label="Members registered in range" value={report.registeredMembers.toLocaleString('en-US')} />
        <StatCard icon="user-check" label="Currently enabled among those registered" value={report.currentlyEnabledMembersRegistered.toLocaleString('en-US')} />
      </section>
      <p>The enabled count reflects current account status, not historical status at the end of the range.</p>
      <dl className="browse-facts" aria-label="Class bookings">
        {Object.entries(report.classBookings).map(([key, count]) => <div key={key}><dt>{key[0].toUpperCase() + key.slice(1)} bookings</dt><dd>{count.toLocaleString('en-US')}</dd></div>)}
      </dl>
      <p>Upcoming and completed bookings are subsets of confirmed bookings. Do not add them to the total.</p>
      <p>Reporting time zone: {report.timeZone}. Booking counts use booking creation dates.</p>
    </>}</TrainingData>
  </Card>
}
