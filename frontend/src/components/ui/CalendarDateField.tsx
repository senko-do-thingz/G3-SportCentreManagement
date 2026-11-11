import { useEffect, useId, useRef, useState, type FocusEventHandler } from 'react'
import { formatDisplayCalendarDate, parseDisplayCalendarDate } from '../../utils/dateDisplay'

export interface CalendarDateFieldProps {
  value: string
  onChange: (iso: string) => void
  id?: string
  name?: string
  label: string
  hint?: string
  error?: string
  min?: string
  max?: string
  disabled?: boolean
  readOnly?: boolean
  required?: boolean
  autoFocus?: boolean
  autoComplete?: string
  className?: string
  onFocus?: FocusEventHandler<HTMLInputElement>
  reserveErrorSpace?: boolean
}

function validationMessage(display: string, required: boolean, min?: string, max?: string): string | null {
  if (!display) return required ? 'Enter a date in dd/MM/yyyy format.' : null
  const iso = parseDisplayCalendarDate(display)
  if (!iso) return 'Enter a valid date in dd/MM/yyyy format.'
  if (min && iso < min) return `Date must be on or after ${formatDisplayCalendarDate(min)}.`
  if (max && iso > max) return `Date must be on or before ${formatDisplayCalendarDate(max)}.`
  return null
}

/** Locale-independent visible date editor with an ISO-only application value. */
export function CalendarDateField({
  value,
  onChange,
  id,
  name,
  label,
  hint,
  error,
  min,
  max,
  disabled = false,
  readOnly = false,
  required = false,
  autoFocus = false,
  autoComplete = 'off',
  className,
  onFocus,
  reserveErrorSpace = false,
}: CalendarDateFieldProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const textRef = useRef<HTMLInputElement>(null)
  const [display, setDisplay] = useState(() => formatDisplayCalendarDate(value))
  const [localError, setLocalError] = useState<string | null>(null)

  useEffect(() => {
    if (document.activeElement !== textRef.current) setDisplay(formatDisplayCalendarDate(value))
  }, [value])

  const message = error ?? localError
  const hintText = hint ? `${hint} Date format: dd/MM/yyyy.` : 'Date format: dd/MM/yyyy.'
  const hintId = `${inputId}-hint`
  const errorId = message ? `${inputId}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ')

  const commit = (nextDisplay: string) => {
    const iso = nextDisplay ? parseDisplayCalendarDate(nextDisplay) : ''
    if (iso === null) {
      setLocalError(validationMessage(nextDisplay, required, min, max))
      return false
    }

    // Empty and well-formed values are canonical application state. Range rules remain
    // visible here, while the owning form receives the ISO value and can apply its
    // domain-specific message before any request is submitted.
    if (iso !== value) onChange(iso)
    const nextError = validationMessage(nextDisplay, required, min, max)
    setLocalError(nextError)
    return nextError === null
  }

  return (
    <div className={['field', 'calendar-date-field', className ?? ''].filter(Boolean).join(' ')}>
      <div className="field__label-row">
        <label className="field__label" htmlFor={inputId}>
          {label}{required && <span className="field__required" aria-hidden="true"> *</span>}
        </label>
      </div>
      <div className="calendar-date-field__control">
        <input
          ref={textRef}
          id={inputId}
          className={`input calendar-date-field__text${message ? ' input--invalid' : ''}`}
          type="text"
          inputMode="numeric"
          autoComplete={autoComplete}
          spellCheck={false}
          placeholder="dd/MM/yyyy"
          maxLength={10}
          value={display}
          disabled={disabled}
          readOnly={readOnly}
          required={required}
          autoFocus={autoFocus}
          aria-invalid={message ? true : undefined}
          aria-describedby={describedBy}
          onFocus={onFocus}
          onChange={(event) => {
            setDisplay(event.target.value)
            if (localError) setLocalError(null)
          }}
          onBlur={() => {
            if (!commit(display) && !display && !required && value) onChange('')
          }}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !commit(display)) event.preventDefault()
            if (event.key === 'Escape') {
              setDisplay(formatDisplayCalendarDate(value))
              setLocalError(null)
            }
          }}
        />
        {!readOnly && <>
          <span className="calendar-date-field__icon" aria-hidden="true">&#128197;</span>
          <input
            className="calendar-date-field__picker"
            type="date"
            tabIndex={-1}
            aria-label="Open calendar picker"
            value={value}
            min={min}
            max={max}
            disabled={disabled}
            onChange={(event) => {
              const iso = event.target.value
              setDisplay(formatDisplayCalendarDate(iso))
              setLocalError(null)
              onChange(iso)
              queueMicrotask(() => textRef.current?.focus())
            }}
          />
        </>}
      </div>
      {name && <input type="hidden" name={name} value={value} />}
      <div className={reserveErrorSpace ? 'field__feedback field__feedback--reserved' : 'field__feedback'}>
        <p className="field__hint" id={hintId}>{hintText}</p>
        {message && <p className="field__error" id={errorId} role="alert">{message}</p>}
      </div>
    </div>
  )
}
