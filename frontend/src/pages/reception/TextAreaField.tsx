import { useId, type TextareaHTMLAttributes } from 'react'

export interface TextAreaFieldProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string
  error?: string
  hint?: string
}

/** Multi line field with the same label, hint and error layout as the UI kit Input (FE-12). */
export function TextAreaField({ label, error, hint, id, required, rows = 3, ...rest }: TextAreaFieldProps) {
  const generatedId = useId()
  const fieldId = id ?? generatedId
  const hintId = hint ? `${fieldId}-hint` : undefined
  const errorId = error ? `${fieldId}-error` : undefined
  return (
    <div className="field">
      <div className="field__label-row">
        <label className="field__label" htmlFor={fieldId}>
          {label}
          {required && <span className="field__required"> *</span>}
        </label>
      </div>
      <textarea
        id={fieldId}
        className={`input textarea${error ? ' input--invalid' : ''}`}
        rows={rows}
        required={required}
        aria-invalid={error ? true : undefined}
        aria-describedby={[hintId, errorId].filter(Boolean).join(' ') || undefined}
        {...rest}
      />
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
