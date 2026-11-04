import type { ReactNode } from 'react'
import '@fontsource/be-vietnam-pro/700.css'
import '@fontsource/be-vietnam-pro/800.css'
import '../styles/workflowSurface.css'

/** Use Home's light/dark palette for operational screens without changing global tokens. */
export function WorkflowSurface({ children }: { children: ReactNode }) {
  return <div className="home-page workflow-surface">{children}</div>
}
