import { useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { tabIds } from './tabIds'

export interface TabItem {
  id: string
  label: ReactNode
  disabled?: boolean
}

export interface TabsProps {
  items: TabItem[]
  /** Id of the selected tab. Keep it in state or in the query string, as the screens do. */
  value: string
  onChange: (id: string) => void
  /** Accessible name of the tab list, for example "My membership". */
  label: string
  /** Prefix for the tab and panel ids; a generated one is used when it is not given. */
  idPrefix?: string
  /** Content of the selected panel. Only the selected panel renders, so hidden tabs load nothing. */
  children?: (selectedId: string) => ReactNode
  className?: string
}

/**
 * WAI-ARIA tabs with automatic activation: Left and Right arrows move to the previous or next tab
 * and select it, Home and End jump to the first or last one, and disabled tabs are skipped. Only
 * the selected tab is in the tab order. Every tab keeps a panel element, so aria-controls always
 * points at something, but only the selected panel has content.
 */
export function Tabs({ items, value, onChange, label, idPrefix, children, className }: TabsProps) {
  const generatedPrefix = useId().replace(/:/g, '')
  const prefix = idPrefix ?? `tabs-${generatedPrefix}`
  const refs = useRef(new Map<string, HTMLButtonElement>())
  const enabled = items.filter((item) => !item.disabled)
  // If the value does not match an enabled tab, the first enabled tab stays reachable by keyboard.
  const selected = enabled.some((item) => item.id === value) ? value : enabled[0]?.id

  const select = (id: string) => {
    if (id !== value) {
      onChange(id)
    }
    refs.current.get(id)?.focus()
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (enabled.length === 0) {
      return
    }
    const index = enabled.findIndex((item) => item.id === selected)
    let next: TabItem | undefined
    if (event.key === 'ArrowRight') {
      next = enabled[(index + 1) % enabled.length]
    } else if (event.key === 'ArrowLeft') {
      next = enabled[(index - 1 + enabled.length) % enabled.length]
    } else if (event.key === 'Home') {
      next = enabled[0]
    } else if (event.key === 'End') {
      next = enabled[enabled.length - 1]
    }
    if (next) {
      event.preventDefault()
      select(next.id)
    }
  }

  return (
    <div className={['tabs-block', className ?? ''].filter(Boolean).join(' ')}>
      <div className="tabs" role="tablist" aria-label={label}>
        {items.map((item) => {
          const ids = tabIds(prefix, item.id)
          const isSelected = item.id === selected
          return (
            <button
              key={item.id}
              ref={(element) => {
                if (element) {
                  refs.current.set(item.id, element)
                } else {
                  refs.current.delete(item.id)
                }
              }}
              type="button"
              role="tab"
              id={ids.tab}
              aria-selected={isSelected}
              aria-controls={ids.panel}
              tabIndex={isSelected ? 0 : -1}
              disabled={item.disabled}
              className={`tabs__tab${isSelected ? ' tabs__tab--active' : ''}`}
              onClick={() => select(item.id)}
              onKeyDown={handleKeyDown}
            >
              {item.label}
            </button>
          )
        })}
      </div>
      {items.map((item) => {
        const ids = tabIds(prefix, item.id)
        const isSelected = item.id === selected
        return (
          <div
            key={item.id}
            role="tabpanel"
            id={ids.panel}
            aria-labelledby={ids.tab}
            hidden={!isSelected}
            tabIndex={isSelected ? 0 : undefined}
            className="tabs__panel"
          >
            {isSelected && children?.(item.id)}
          </div>
        )
      })}
    </div>
  )
}
