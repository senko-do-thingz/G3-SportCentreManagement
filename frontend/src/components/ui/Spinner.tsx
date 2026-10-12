export interface SpinnerProps {
  size?: 'sm' | 'md' | 'lg'
  /** Read by screen readers. */
  label?: string
  /** Render inside text or a button instead of as a centered block. */
  inline?: boolean
}

export function Spinner({ size = 'md', label = 'Loading', inline = false }: SpinnerProps) {
  const spinner = (
    <span className={`spinner spinner--${size}`} role="status" aria-live="polite">
      <span className="visually-hidden">{label}</span>
    </span>
  )
  return inline ? spinner : <div className="spinner-block">{spinner}</div>
}

/** Full height loader used while the session is being restored. */
export function PageLoader({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="page-loader">
      <Spinner size="lg" label={label} inline />
      <p className="page-loader__text">{label}</p>
    </div>
  )
}
