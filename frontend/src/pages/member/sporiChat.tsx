import { Fragment, type KeyboardEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { type Consultation } from '../../api/trainingGuidanceApi'
import { SporiRobotMark } from './SporiRobotMark'

/** Message pieces shared by the floating Spori widget and the full assistant page. */

/** Plain-text replies with "- " bullet lines and **bold**, as the AI writer is told to produce. */
export function SporiText({ text }: { text: string }) {
  const blocks: ReactNode[] = []
  let bullets: string[] = []
  const flush = () => {
    if (!bullets.length) return
    blocks.push(<ul key={`ul-${blocks.length}`}>{bullets.map((item, index) => <li key={index}>{inline(item)}</li>)}</ul>)
    bullets = []
  }
  for (const line of text.split('\n')) {
    const bullet = /^\s*(?:[-•*]|\d+[.)])\s+(.*)$/.exec(line)
    if (bullet) { bullets.push(bullet[1]); continue }
    flush()
    if (line.trim()) blocks.push(<p key={`p-${blocks.length}`}>{inline(line)}</p>)
  }
  flush()
  return <div className="spori-text">{blocks}</div>
}

function inline(text: string): ReactNode {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, index) => part.startsWith('**') && part.endsWith('**') && part.length > 4
    ? <strong key={index}>{part.slice(2, -2)}</strong>
    : <Fragment key={index}>{part}</Fragment>)
}

export function SporiAvatar({ size = 'sm' }: { size?: 'sm' | 'md' | 'lg' }) {
  return <span className={`spori-avatar spori-avatar--${size}`} aria-hidden="true"><SporiRobotMark /></span>
}

export function SporiTyping({ label }: { label: string }) {
  return (
    <div className="spori-row spori-row--bot" role="status">
      <SporiAvatar />
      <div className="spori-bubble spori-typing"><span /><span /><span /><span className="member-concept__sr-only">{label}</span></div>
    </div>
  )
}

/** Text area that sends on Enter and adds a line on Shift+Enter, with a round send button. */
export function SporiComposer({ id, label, buttonLabel, placeholder, value, pending, onChange, onSubmit, inputRef }: {
  id: string; label: string; buttonLabel: string; placeholder: string; value: string; pending: boolean
  onChange: (value: string) => void; onSubmit: () => void; inputRef?: React.RefObject<HTMLTextAreaElement | null>
}) {
  function keyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      event.currentTarget.form?.requestSubmit()
    }
  }
  return (
    <form className="spori-composer" onSubmit={event => { event.preventDefault(); onSubmit() }}>
      <label className="member-concept__sr-only" htmlFor={id}>{label}</label>
      <textarea ref={inputRef} id={id} name="question" rows={1} maxLength={500} placeholder={placeholder} value={value}
        disabled={pending} onKeyDown={keyDown} onChange={event => onChange(event.target.value)} />
      <button type="submit" aria-label={buttonLabel} title={buttonLabel} disabled={!value.trim() || pending}>
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M3.4 20.4 21 12 3.4 3.6l.1 6.5L15 12 3.5 13.9z" fill="currentColor" /></svg>
      </button>
    </form>
  )
}

/** One source record under an answer, as a small card linking to where the Member can act on it. */
export function SporiSources({ sources, kindLabel, onNavigate }: {
  sources: Consultation['sources']; kindLabel?: (kind: string) => string; onNavigate?: () => void
}) {
  if (!sources.length) return null
  return (
    <ul className="spori-sources">
      {sources.map(source => (
        <li key={`${source.kind}-${source.id}`}>
          <Link to={source.path} onClick={onNavigate}>{source.title}</Link>
          {kindLabel && <span className="spori-sources__kind">{kindLabel(source.kind)}</span>}
          {source.detail && <small>{source.detail}</small>}
        </li>
      ))}
    </ul>
  )
}
