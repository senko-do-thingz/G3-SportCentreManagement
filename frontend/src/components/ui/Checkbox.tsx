import { useId, type InputHTMLAttributes, type ReactNode } from 'react'

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: ReactNode
  /** Message shown under the checkbox, for example "You must accept the terms". */
  error?: string
}

export function Checkbox({
  label,
  error,
  id,
  className,
  'aria-describedby': extraDescribedBy,
  ...rest
}: CheckboxProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const errorId = error ? `${inputId}-error` : undefined
  // Same rule as Input: the caller's ids and the error id are both kept.
  const describedBy = [errorId, extraDescribedBy].filter(Boolean).join(' ') || undefined
  return (
    <div className={['checkbox-field', className ?? ''].filter(Boolean).join(' ')}>
      <label className="checkbox" htmlFor={inputId}>
        <input
          id={inputId}
          type="checkbox"
          className="checkbox__input"
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          {...rest}
        />
        <span className="checkbox__label">{label}</span>
      </label>
      {error && (
        <p className="field__error" id={errorId} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
