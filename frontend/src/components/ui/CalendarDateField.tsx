import { useEffect, useId, useRef, useState, type FocusEventHandler } from 'react'
import { formatDisplayCalendarDate, parseDisplayCalendarDate } from '../../utils/dateDisplay'
import { CALENDAR_DATE_VALIDATE_EVENT } from './calendarDateValidation'

export interface CalendarDateFieldProps {
  value: string
  onChange: (iso: string, valid?: boolean) => void
  onValidityChange?: (valid: boolean) => void
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
  onValidityChange,
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
  const pickerRef = useRef<HTMLInputElement>(null)
  const committedValueRef = useRef(value)
  const [display, setDisplay] = useState(() => formatDisplayCalendarDate(value))
  const [localError, setLocalError] = useState<string | null>(null)
  const syncKey = `${value}\u0000${min ?? ''}\u0000${max ?? ''}\u0000${required}`
  const [previousSyncKey, setPreviousSyncKey] = useState(syncKey)

  if (previousSyncKey !== syncKey) {
    setPreviousSyncKey(syncKey)
    setDisplay(formatDisplayCalendarDate(value))
    setLocalError(null)
  }

  useEffect(() => {
    const nextDisplay = formatDisplayCalendarDate(value)
    const nextError = validationMessage(nextDisplay, required, min, max)
    textRef.current?.setCustomValidity(nextError ?? '')
    onValidityChange?.(nextError === null)
    if (nextError === null) committedValueRef.current = value
  }, [value, required, min, max, onValidityChange])

  const validate = (nextDisplay: string, announce: boolean) => {
    const nextError = validationMessage(nextDisplay, required, min, max)
    textRef.current?.setCustomValidity(nextError ?? '')
    onValidityChange?.(nextError === null)
    if (announce) setLocalError(nextError)
    return nextError
  }

  const emitChange = (iso: string, valid: boolean) => {
    if (valid) onChange(iso)
    else onChange(iso, false)
  }

  const commit = (nextDisplay: string) => {
    const iso = nextDisplay ? parseDisplayCalendarDate(nextDisplay) : ''
    const nextError = validate(nextDisplay, true)
    if (iso === null) return false

    if (iso !== value) emitChange(iso, nextError === null)
    if (nextError === null) committedValueRef.current = iso
    return nextError === null
  }

  useEffect(() => {
    const input = textRef.current
    if (!input) return
    const validateNow = (event: Event) => {
      if (commit(input.value)) return
      event.preventDefault()
      input.focus()
    }
    input.addEventListener(CALENDAR_DATE_VALIDATE_EVENT, validateNow)
    return () => input.removeEventListener(CALENDAR_DATE_VALIDATE_EVENT, validateNow)
  })

  useEffect(() => {
    const input = textRef.current
    const form = input?.form
    if (!input || !form) return
    const blockUncommittedDate = (event: SubmitEvent) => {
      const firstUncommitted = Array.from(form.querySelectorAll<HTMLInputElement>('.calendar-date-field__text'))
        .find((field) => {
          const fieldRequired = field.required
          return field.value ? parseDisplayCalendarDate(field.value) === null : fieldRequired
        })
      if (firstUncommitted !== input) return
      event.preventDefault()
      event.stopPropagation()
      commit(input.value)
      input.focus()
    }
    form.addEventListener('submit', blockUncommittedDate, true)
    return () => form.removeEventListener('submit', blockUncommittedDate, true)
  })

  const message = error ?? localError
  const hintText = hint ? `${hint} Date format: dd/MM/yyyy.` : 'Date format: dd/MM/yyyy.'
  const hintId = `${inputId}-hint`
  const errorId = message ? `${inputId}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ')

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
            const nextDisplay = event.target.value
            const iso = nextDisplay ? parseDisplayCalendarDate(nextDisplay) : ''
            const nextError = validate(nextDisplay, false)
            setDisplay(nextDisplay)
            if (localError) setLocalError(null)
            if (iso !== null && iso !== value) emitChange(iso, nextError === null)
            if (iso && nextError === null) committedValueRef.current = iso
          }}
          onBlur={() => {
            if (!commit(display) && !display && !required && value) onChange('')
          }}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !commit(display)) event.preventDefault()
            if (event.key === 'Escape') {
              const restored = committedValueRef.current
              const restoredDisplay = formatDisplayCalendarDate(restored)
              setDisplay(restoredDisplay)
              setLocalError(null)
              textRef.current?.setCustomValidity('')
              onValidityChange?.(true)
              if (restored !== value) emitChange(restored, true)
            }
          }}
        />
        {!readOnly && (
          <>
            <button
              type="button"
              className="calendar-date-field__trigger"
              aria-label="Open calendar picker"
              disabled={disabled}
              tabIndex={disabled ? -1 : 0}
              onClick={() => {
                if (disabled || readOnly) return
                const picker = pickerRef.current
                if (!picker) return
                try {
                  if (typeof picker.showPicker === 'function') {
                    picker.showPicker()
                  } else {
                    picker.focus()
                  }
                } catch {
                  picker.focus()
                }
              }}
            >
              <span className="calendar-date-field__icon" aria-hidden="true">&#128197;</span>
            </button>
            <input
              ref={pickerRef}
              className="calendar-date-field__picker"
              type="date"
              tabIndex={-1}
              aria-label="Calendar date picker"
              value={value}
              min={min}
              max={max}
              disabled={disabled}
              onChange={(event) => {
                const iso = event.target.value
                const nextDisplay = formatDisplayCalendarDate(iso)
                const nextError = validate(nextDisplay, true)
                setDisplay(nextDisplay)
                if (nextError === null) committedValueRef.current = iso
                emitChange(iso, nextError === null)
                queueMicrotask(() => textRef.current?.focus())
              }}
            />
          </>
        )}
      </div>
      {name && <input type="hidden" name={name} value={value} />}
      <div className={reserveErrorSpace ? 'field__feedback field__feedback--reserved' : 'field__feedback'}>
        <p className="field__hint" id={hintId}>{hintText}</p>
        {message && <p className="field__error" id={errorId} role="alert">{message}</p>}
      </div>
    </div>
  )
}
