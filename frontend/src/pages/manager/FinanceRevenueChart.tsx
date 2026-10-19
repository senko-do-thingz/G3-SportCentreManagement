import type { FinanceRevenueRow } from '../../api/managerFinanceApi'

function shortMoney(value: number): string {
  if (Math.abs(value) >= 1_000_000) return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 }).format(value / 1_000_000)}m`
  if (Math.abs(value) >= 1_000) return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(value / 1_000)}k`
  return String(value)
}

export function FinanceRevenueChart({ rows }: { rows: FinanceRevenueRow[] }) {
  if (rows.length === 0) return <p className="finance-empty">No revenue categories are available for this range.</p>

  const width = 900
  const left = 190
  const right = 28
  const top = 26
  const rowHeight = 58
  const height = top + rows.length * rowHeight + 22
  const chartWidth = width - left - right
  const max = Math.max(1, ...rows.map((row) => row.revenue))
  const grid = [0, 0.5, 1]

  return <div className="finance-revenue-chart-wrap">
    <ul className="finance-revenue-legend" aria-label="Revenue chart legend">
      <li><span className="finance-revenue-chart__bar--revenue" />Revenue</li>
    </ul>
    <svg className="finance-revenue-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`Revenue by category for ${rows.length} categories`}>
      {grid.map((fraction) => {
        const x = left + fraction * chartWidth
        return <g key={fraction}>
          <line className="finance-revenue-chart__grid" x1={x} x2={x} y1={top - 10} y2={height - 18} />
          <text className="finance-revenue-chart__axis" x={x} y={height - 2} textAnchor={fraction === 0 ? 'start' : fraction === 1 ? 'end' : 'middle'}>{shortMoney(max * fraction)}</text>
        </g>
      })}
      {rows.map((row, index) => {
        const y = top + index * rowHeight
        return <g key={row.key ?? row.label}>
          <text className="finance-revenue-chart__category" x={left - 12} y={y + 27} textAnchor="end">{row.label}</text>
          <rect className="finance-revenue-chart__bar finance-revenue-chart__bar--revenue" x={left} y={y + 8} width={Math.max(0, row.revenue / max * chartWidth)} height="12" rx="6">
            <title>{`${row.label}: ${new Intl.NumberFormat('vi-VN').format(row.revenue)} VND; ${row.transactions} transactions`}</title>
          </rect>
        </g>
      })}
    </svg>
  </div>
}
