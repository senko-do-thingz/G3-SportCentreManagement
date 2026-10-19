import type { FinanceTrendPoint } from '../../api/managerFinanceApi'

function shortMoney(value: number): string {
  if (Math.abs(value) >= 1_000_000) return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 }).format(value / 1_000_000)}m`
  if (Math.abs(value) >= 1_000) return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(value / 1_000)}k`
  return String(value)
}

function monthLabel(month: string): string {
  const match = /^(\d{4})-(\d{2})$/.exec(month)
  if (!match) return month
  return new Date(Number(match[1]), Number(match[2]) - 1, 1).toLocaleDateString('en', { month: 'short' })
}

export function FinanceTrendChart({ points }: { points: FinanceTrendPoint[] }) {
  const width = 900
  const height = 280
  const left = 48
  const right = 24
  const top = 22
  const bottom = 48
  const chartHeight = height - top - bottom
  const max = Math.max(1, ...points.map((point) => point.revenue))
  const coordinates = points.map((point, index) => ({
    ...point,
    x: points.length < 2 ? width / 2 : left + index * (width - left - right) / (points.length - 1),
    y: top + chartHeight - (point.revenue / max) * chartHeight,
  }))
  const line = coordinates.map((point) => `${point.x},${point.y}`).join(' ')

  if (points.length === 0) {
    return <p className="finance-empty">No revenue trend is available for this period.</p>
  }

  return <div className="finance-chart-wrap">
    <svg className="finance-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Six-month collected revenue through current month" aria-describedby="finance-trend-description">
      <title id="finance-trend-title">Six-month collected revenue</title>
      <desc id="finance-trend-description">Monthly paid revenue for the six months ending in the current month.</desc>
      {[0, 0.5, 1].map((fraction) => {
        const y = top + chartHeight * fraction
        return <g key={fraction}>
          <line className="finance-chart__grid" x1={left} x2={width - right} y1={y} y2={y} />
          <text className="finance-chart__axis" x={left - 8} y={y + 4} textAnchor="end">{shortMoney(max * (1 - fraction))}</text>
        </g>
      })}
      <polyline className="finance-chart__line" points={line} />
      {coordinates.map((point) => <g key={point.month}>
        <circle className="finance-chart__point" cx={point.x} cy={point.y} r="5">
          <title>{`${point.month}: ${new Intl.NumberFormat('vi-VN').format(point.revenue)} VND`}</title>
        </circle>
        <text className="finance-chart__month" x={point.x} y={height - 16} textAnchor="middle">{monthLabel(point.month)}</text>
      </g>)}
    </svg>
    <ul className="finance-trend-values" aria-label="Monthly collected revenue values">
      {points.map((point) => <li key={point.month}><span>{point.month}</span><strong>{new Intl.NumberFormat('vi-VN').format(point.revenue)} VND</strong></li>)}
    </ul>
  </div>
}
