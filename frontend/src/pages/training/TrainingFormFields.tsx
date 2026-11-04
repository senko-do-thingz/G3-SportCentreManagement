import { useId, type TextareaHTMLAttributes } from 'react'
export function ActionMessage({ error, success, conflict = false }: { error: string | null; success: string | null; conflict?: boolean }) {
  return <>{error && <div className={`notice notice--${conflict ? 'warning' : 'danger'}`} role="alert">
    {conflict && <strong>Record changed</strong>}<p>{error}</p>
    {conflict && <p>Refresh the current data and review the saved record before trying again.</p>}
  </div>}{success && <p className="notice notice--success" role="status">{success}</p>}</>
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
