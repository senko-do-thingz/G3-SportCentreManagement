import { api } from './client'

export interface OperationsReport {
  from: string; to: string; timeZone: string
  registeredMembers: number; currentlyEnabledMembersRegistered: number
  classBookings: { total: number; confirmed: number; cancelled: number; upcoming: number; completed: number }
}
export async function operationsReport(from: string, to: string, signal: AbortSignal): Promise<OperationsReport> {
  const report = await api.get<OperationsReport>('/manager/reports/operations', { signal, query: { from, to } })
  if (!report || report.from !== from || report.to !== to || !report.classBookings ||
    ![report.registeredMembers, report.currentlyEnabledMembersRegistered, ...['total', 'confirmed', 'cancelled', 'upcoming', 'completed'].map(key => report.classBookings[key as keyof OperationsReport['classBookings']])].every(value => Number.isSafeInteger(value) && value >= 0)) {
    throw new Error('The operations report returned an unexpected response.')
  }
  return report
}
