import { useId, type InputHTMLAttributes, type ReactNode } from 'react'
import { formatCalendarDate, formatMonthRange, formatWeekRange } from '../../utils/dateDisplay'

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: ReactNode
  hint?: ReactNode
  /** Message shown under the field, usually from ApiError.fieldErrors. */
  error?: string
  /** Reserve one line below the input so an inline validation message does not move the form. */
  reserveErrorSpace?: boolean
  /** Element shown on the right of the label, for example a "Forgot password?" link. */
  labelAction?: ReactNode
  /** Element shown inside the field on the right, for example the show or hide password button. */
  trailing?: ReactNode
}

export function Input({
  label,
  hint: suppliedHint,
  error,
  reserveErrorSpace = false,
  labelAction,
  trailing,
  id,
  className,
  required,
  'aria-describedby': extraDescribedBy,
  ...rest
}: InputProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const selectedValue = rest.value ?? rest.defaultValue
  const calendarValue = typeof selectedValue === 'string' ? selectedValue : ''
  const dateHint = rest.type === 'date' ? (calendarValue ? `Selected date: ${formatCalendarDate(calendarValue)}` : 'Date format: dd/MM/yyyy')
    : rest.type === 'month' && calendarValue ? `Selected period: ${formatMonthRange(calendarValue)}`
      : rest.type === 'week' && calendarValue ? `Selected week: ${formatWeekRange(calendarValue)}` : null
  const hint = suppliedHint && dateHint ? <>{suppliedHint}<br />{' '}{dateHint}</> : suppliedHint || dateHint
  const hintId = hint ? `${inputId}-hint` : undefined
  const errorId = error ? `${inputId}-error` : undefined
  const describedBy = [hintId, errorId, extraDescribedBy].filter(Boolean).join(' ') || undefined

  const input = (
    <input
      id={inputId}
      className={`input${error ? ' input--invalid' : ''}`}
      aria-invalid={error ? true : undefined}
      aria-describedby={describedBy}
      required={required}
      {...rest}
    />
  )

  return (
    <div className={['field', className ?? ''].filter(Boolean).join(' ')}>
      {(label || labelAction) && (
        <div className="field__label-row">
          {label && (
            <label className="field__label" htmlFor={inputId}>
              {label}
              {required && <span className="field__required"> *</span>}
            </label>
          )}
          {labelAction}
        </div>
      )}
      {trailing ? (
        <div className="input-wrap input-wrap--trailing">
          {input}
          <span className="input-wrap__trailing">{trailing}</span>
        </div>
      ) : (
        input
      )}
      {reserveErrorSpace ? (
        <div className="field__feedback field__feedback--reserved">
          {hint && !error && (
            <p className="field__hint" id={hintId}>
              {hint}
            </p>
          )}
          {error && (
            <p className="field__error" id={errorId} role="alert">
              {error}
            </p>
          )}
        </div>
      ) : (
        <>
          {hint && !error && (
            <p className="field__hint" id={hintId}>
              {hint}
            </p>
          )}
          {error && (
            <p className="field__error" id={errorId} role="alert">
              {error}
            </p>
          )}
        </>
      )}
    </div>
  )
}
