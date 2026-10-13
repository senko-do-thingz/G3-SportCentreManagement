import { useId, type ReactNode, type TextareaHTMLAttributes } from 'react'

export interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: ReactNode
  hint?: ReactNode
  /** Message shown under the field, usually from ApiError.fieldErrors. */
  error?: string
}

/**
 * Multi line field with the same label, hint and error layout as Input. The caller's props are
 * spread first, so id, aria-invalid and aria-describedby always match what is rendered; the
 * caller's own aria-describedby ids are kept and joined.
 */
export function TextArea({
  label,
  hint,
  error,
  id,
  className,
  required,
  rows = 3,
  'aria-describedby': extraDescribedBy,
  ...rest
}: TextAreaProps) {
  const generatedId = useId()
  const fieldId = id ?? generatedId
  const showHint = Boolean(hint) && !error
  const hintId = showHint ? `${fieldId}-hint` : undefined
  const errorId = error ? `${fieldId}-error` : undefined
  const describedBy = [hintId, errorId, extraDescribedBy].filter(Boolean).join(' ') || undefined

  return (
    <div className={['field', className ?? ''].filter(Boolean).join(' ')}>
      <div className="field__label-row">
        <label className="field__label" htmlFor={fieldId}>
          {label}
          {required && <span className="field__required"> *</span>}
        </label>
      </div>
      <textarea
        rows={rows}
        {...rest}
        id={fieldId}
        className={`input textarea${error ? ' input--invalid' : ''}`}
        required={required}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
      />
      {showHint && (
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
