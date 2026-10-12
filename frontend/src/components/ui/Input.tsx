import { useId, type InputHTMLAttributes, type ReactNode } from 'react'

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: ReactNode
  hint?: ReactNode
  /** Message shown under the field, usually from ApiError.fieldErrors. */
  error?: string
  /** Element shown on the right of the label, for example a "Forgot password?" link. */
  labelAction?: ReactNode
  /** Element shown inside the field on the right, for example the show or hide password button. */
  trailing?: ReactNode
}

export function Input({
  label,
  hint,
  error,
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
  )
}
