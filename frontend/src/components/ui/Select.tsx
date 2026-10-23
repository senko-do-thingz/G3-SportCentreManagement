import { useId, type ReactNode, type SelectHTMLAttributes } from 'react'

export interface SelectOption {
  value: string
  label: string
  disabled?: boolean
}

export interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'children'> {
  label?: ReactNode
  hint?: ReactNode
  error?: string
  options: SelectOption[]
  /** Adds a first option with an empty value, for example "All roles". */
  placeholder?: string
}

export function Select({
  label,
  hint,
  error,
  options,
  placeholder,
  id,
  className,
  required,
  'aria-describedby': extraDescribedBy,
  ...rest
}: SelectProps) {
  const generatedId = useId()
  const selectId = id ?? generatedId
  const hintId = hint ? `${selectId}-hint` : undefined
  const errorId = error ? `${selectId}-error` : undefined
  // Same rule as Input: the caller's ids and the hint and error ids are all kept.
  const describedBy = [hintId, errorId, extraDescribedBy].filter(Boolean).join(' ') || undefined

  return (
    <div className={['field', className ?? ''].filter(Boolean).join(' ')}>
      {label && (
        <div className="field__label-row">
          <label className="field__label" htmlFor={selectId}>
            {label}
            {required && <span className="field__required"> *</span>}
          </label>
        </div>
      )}
      <div className="select-wrap">
        <select
          id={selectId}
          className={`input select${error ? ' input--invalid' : ''}`}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          required={required}
          {...rest}
        >
          {placeholder !== undefined && <option value="">{placeholder}</option>}
          {options.map((option) => (
            <option key={option.value} value={option.value} disabled={option.disabled}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
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
