import { Fragment, useState, type KeyboardEvent, type ReactNode } from 'react'
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

/**
 * The records an answer was built from, as compact rows linking to where the Member can act on them. More than two
 * sources fold into a "Sources (n)" disclosure so the answer itself stays in view, as in most support chats.
 */
export function SporiSources({ sources, kindLabel, onNavigate, vi = false }: {
  sources: Consultation['sources']; kindLabel?: (kind: string) => string; onNavigate?: () => void; vi?: boolean
}) {
  if (!sources.length) return null
  const list = (
    <ul className="spori-sources">
      {sources.map(source => (
        <li key={`${source.kind}-${source.id}`}>
          <Link to={source.path} onClick={onNavigate}>
            <span className="spori-sources__title">{source.title}</span>
            <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path d="M9 6l6 6-6 6" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" /></svg>
          </Link>
          {kindLabel && <span className="spori-sources__kind">{kindLabel(source.kind)}</span>}
          {source.detail && <small>{source.detail}</small>}
        </li>
      ))}
    </ul>
  )
  if (sources.length <= 2) return list
  return (
    <details className="spori-sources-fold">
      <summary>
        <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h10" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" /></svg>
        {vi ? `Nguồn dữ liệu (${sources.length})` : `Sources (${sources.length})`}
      </summary>
      {list}
    </details>
  )
}

/** Copies an answer to the clipboard, with a short "Copied" confirmation. */
export function SporiCopyButton({ text, vi = false }: { text: string; vi?: boolean }) {
  const [copied, setCopied] = useState(false)
  if (typeof navigator === 'undefined' || !navigator.clipboard) return null
  const label = copied ? (vi ? 'Đã sao chép' : 'Copied') : (vi ? 'Sao chép' : 'Copy')
  return (
    <button type="button" className={'spori-action' + (copied ? ' is-done' : '')} aria-label={label} title={label}
      onClick={() => navigator.clipboard.writeText(text).then(() => { setCopied(true); window.setTimeout(() => setCopied(false), 1600) }, () => undefined)}>
      {copied
        ? <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" /></svg>
        : <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><rect x="9" y="9" width="11" height="11" rx="2.5" fill="none" stroke="currentColor" strokeWidth="2" /><path d="M15 9V6.5A2.5 2.5 0 0 0 12.5 4h-6A2.5 2.5 0 0 0 4 6.5v6A2.5 2.5 0 0 0 6.5 15H9" fill="none" stroke="currentColor" strokeWidth="2" /></svg>}
    </button>
  )
}

/** Small "Gemini" badge on replies the AI wrote, so Members can tell them from fixed answers. */
export function SporiAiBadge() {
  return <span className="spori-ai-badge" title="Written by Gemini AI from Sportify data">
    <svg viewBox="0 0 24 24" width="11" height="11" aria-hidden="true"><path d="M12 2c.6 4.9 3.1 7.4 8 8-4.9.6-7.4 3.1-8 8-.6-4.9-3.1-7.4-8-8 4.9-.6 7.4-3.1 8-8z" fill="currentColor" /></svg>
    Gemini
  </span>
}
