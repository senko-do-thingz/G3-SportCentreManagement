import type { ManagerRevenueReport, RevenueGroup } from '../../api/managerFinanceApi'

const GROUP_LABELS: Record<RevenueGroup, string> = {
  ITEM_TYPE: 'Service / item type',
  PAYMENT_METHOD: 'Payment method',
  SPORT: 'Sport',
}

/** Builds spreadsheet-safe CSV while preserving exact VND values from the API. */
export function revenueReportToCsv(report: ManagerRevenueReport): string {
  const quote = (value: string | number) => {
    let text = String(value)
    if (/^[=+\-@\t\r]/.test(text)) text = `'${text}`
    return `"${text.replaceAll('"', '""')}"`
  }
  const lines = [
    ['Date range', `${report.from} to ${report.to}`],
    ['Grouped by', GROUP_LABELS[report.groupBy]],
    [],
    ['Category', 'Collected (VND)', 'Refunded (VND)', 'Net collected (VND)'],
    ...report.rows.map((row) => [row.category, row.collected, row.refunded, row.netCollected]),
    ['Total', report.totals.collected, report.totals.refunded, report.totals.netCollected],
  ]
  return `${lines.map((line) => line.map(quote).join(',')).join('\r\n')}\r\n`
}
