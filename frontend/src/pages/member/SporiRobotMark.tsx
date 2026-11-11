/** Small, code-native Spori robot mark for the Member assistant surfaces. */
export function SporiRobotMark({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false">
      <path d="M24 5v6" stroke="currentColor" strokeWidth="2.5" strokeLinecap="square" />
      <circle cx="24" cy="4" r="2" fill="currentColor" />
      <rect x="8" y="12" width="32" height="29" rx="5" stroke="currentColor" strokeWidth="2.5" />
      <path d="M8 21H4v11h4m32-11h4v11h-4" stroke="currentColor" strokeWidth="2.5" strokeLinejoin="miter" />
      <circle cx="18" cy="25" r="2" fill="currentColor" />
      <circle cx="30" cy="25" r="2" fill="currentColor" />
      <path d="M18 33h12" stroke="currentColor" strokeWidth="2.5" strokeLinecap="square" />
    </svg>
  )
}
