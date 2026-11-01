import { useId, type TextareaHTMLAttributes } from 'react'
export function ActionMessage({ error, success }: { error: string | null; success: string | null }) {
  return <>{error && <p className="field__error" role="alert">{error}</p>}{success && <p role="status">{success}</p>}</>
}

export function TrainingTextarea({ label, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string }) {
  const id = useId()
  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>{label}</label>
      <textarea className="input training-textarea" id={id} rows={3} {...props} />
    </div>
  )
}


