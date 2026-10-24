import { useRef, type ReactNode } from 'react'
import { Button } from './Button'

export interface FilterBarProps {
  /** Accessible name of the search landmark, for example "Filter accounts". */
  label: string
  /** Filter fields: Input, Select or any field component. */
  children: ReactNode
  /** Shows a "Clear filters" button; focus returns to the first field after clearing. */
  onClear?: () => void
  /** True when at least one filter is set; the clear button is only shown then. */
  active?: boolean
  clearLabel?: string
  /** Short result text, for example "12 accounts", announced politely when it changes. */
  resultText?: ReactNode
  className?: string
}

/**
 * Standard filter row for lists and tables, built from the manager screens' filter layout. It is a
 * search landmark so screen reader users can jump to it, the fields wrap to one column on phones,
 * and clearing moves focus back to the first field instead of leaving it on a vanished button.
 */
export function FilterBar({
  label,
  children,
  onClear,
  active = true,
  clearLabel = 'Clear filters',
  resultText,
  className,
}: FilterBarProps) {
  const fieldsRef = useRef<HTMLDivElement>(null)

  const clear = () => {
    onClear?.()
    fieldsRef.current?.querySelector<HTMLElement>('input, select, textarea, button')?.focus()
  }

  return (
    <div className={['filter-bar', className ?? ''].filter(Boolean).join(' ')} role="search" aria-label={label}>
      <div className="filter-bar__fields" ref={fieldsRef}>
        {children}
        {onClear && active && (
          <div className="filter-bar__clear">
            <Button variant="ghost" onClick={clear}>
              {clearLabel}
            </Button>
          </div>
        )}
      </div>
      {resultText !== undefined && (
        <p className="filter-bar__result" aria-live="polite">
          {resultText}
        </p>
      )}
    </div>
  )
}
