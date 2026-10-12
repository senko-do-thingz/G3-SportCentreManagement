import type { ReactNode } from 'react'
import { EmptyState } from './EmptyState'
import { ErrorState } from './ErrorState'
import { Pagination, type PaginationProps } from './Pagination'
import { Spinner } from './Spinner'

export interface TableColumn<T> {
  /** Unique key; also used to read row[key] when render is not given. */
  key: string
  header: ReactNode
  render?: (row: T, rowIndex: number) => ReactNode
  align?: 'left' | 'center' | 'right'
  width?: string
}

export interface TableProps<T> {
  columns: TableColumn<T>[]
  rows: T[]
  rowKey: (row: T, rowIndex: number) => string | number
  loading?: boolean
  error?: unknown
  onRetry?: () => void
  emptyTitle?: ReactNode
  emptyDescription?: ReactNode
  /** Accessible name of the table. */
  caption?: string
  pagination?: PaginationProps
}

function cellValue<T>(row: T, column: TableColumn<T>, rowIndex: number): ReactNode {
  if (column.render) {
    return column.render(row, rowIndex)
  }
  const value = (row as Record<string, unknown>)[column.key]
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

export function Table<T>({
  columns,
  rows,
  rowKey,
  loading = false,
  error,
  onRetry,
  emptyTitle = 'No data yet',
  emptyDescription,
  caption,
  pagination,
}: TableProps<T>) {
  if (error !== undefined && error !== null) {
    return <ErrorState error={error} onRetry={onRetry} />
  }

  const showEmpty = !loading && rows.length === 0
  // While loading another page, keep the previous rows visible (dimmed) under a spinner overlay,
  // so the table does not flash empty. The full row spinner is only for the very first load.
  const showRowSpinner = loading && rows.length === 0
  const showOverlay = loading && rows.length > 0

  return (
    <div className="table-block">
      <div className={`table-scroll${showOverlay ? ' table-scroll--loading' : ''}`}>
        <table className="table">
          {caption && <caption className="visually-hidden">{caption}</caption>}
          <thead>
            <tr>
              {columns.map((column) => (
                <th
                  key={column.key}
                  scope="col"
                  style={{ width: column.width, textAlign: column.align ?? 'left' }}
                >
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody aria-busy={loading || undefined}>
            {showRowSpinner && (
              <tr>
                <td colSpan={columns.length} className="table__state-cell">
                  <Spinner label="Loading rows" />
                </td>
              </tr>
            )}
            {rows.map((row, rowIndex) => (
              <tr key={rowKey(row, rowIndex)}>
                {columns.map((column) => (
                  <td key={column.key} style={{ textAlign: column.align ?? 'left' }}>
                    {cellValue(row, column, rowIndex)}
                  </td>
                ))}
              </tr>
            ))}
            {showEmpty && (
              <tr>
                <td colSpan={columns.length} className="table__state-cell">
                  <EmptyState title={emptyTitle} description={emptyDescription} variant="plain" />
                </td>
              </tr>
            )}
          </tbody>
        </table>
        {showOverlay && (
          <div className="table__overlay">
            <Spinner label="Loading rows" inline />
          </div>
        )}
      </div>
      {pagination && pagination.totalItems > 0 && <Pagination {...pagination} disabled={loading || pagination.disabled} />}
    </div>
  )
}
