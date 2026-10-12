import { Button } from './Button'
import { pageWindow } from './pageWindow'

export interface PaginationProps {
  /** Zero based page index, the same convention as Spring Data Page.number. */
  page: number
  pageSize: number
  totalItems: number
  onPageChange: (page: number) => void
  disabled?: boolean
}

export function Pagination({ page, pageSize, totalItems, onPageChange, disabled = false }: PaginationProps) {
  const totalPages = Math.max(1, Math.ceil(totalItems / Math.max(pageSize, 1)))
  const current = Math.min(Math.max(page, 0), totalPages - 1)
  const firstItem = totalItems === 0 ? 0 : current * pageSize + 1
  const lastItem = Math.min(totalItems, (current + 1) * pageSize)

  return (
    <nav className="pagination" aria-label="Pagination">
      <p className="pagination__summary">
        Showing {firstItem}-{lastItem} of {totalItems}
      </p>
      <div className="pagination__controls">
        <Button
          variant="secondary"
          size="sm"
          onClick={() => onPageChange(current - 1)}
          disabled={disabled || current === 0}
        >
          Previous
        </Button>
        {pageWindow(current, totalPages).map((value, index) =>
          value === null ? (
            <span key={`gap-${index}`} className="pagination__gap" aria-hidden="true">
              ...
            </span>
          ) : (
            <button
              key={value}
              type="button"
              className={`pagination__page${value === current ? ' pagination__page--active' : ''}`}
              aria-current={value === current ? 'page' : undefined}
              aria-label={`Page ${value + 1}`}
              onClick={() => onPageChange(value)}
              disabled={disabled}
            >
              {value + 1}
            </button>
          ),
        )}
        <Button
          variant="secondary"
          size="sm"
          onClick={() => onPageChange(current + 1)}
          disabled={disabled || current >= totalPages - 1}
        >
          Next
        </Button>
      </div>
    </nav>
  )
}
