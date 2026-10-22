import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'

export interface HomeSelectOption { value: string; label: string }

interface HomeSelectProps {
  label: string
  placeholder: string
  value: string
  options: readonly HomeSelectOption[]
  onChange: (value: string) => void
}

/**
 * Themed single-select for the Home class finder (listbox pattern).
 * Keyboard: Enter, Space or ArrowDown opens; ArrowUp and ArrowDown move; Home and End jump; Enter selects; Escape closes.
 */
export function HomeSelect({ label, placeholder, value, options, onChange }: HomeSelectProps) {
  const id = useId()
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const root = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)
  const list = useRef<HTMLUListElement>(null)
  const items = [{ value: '', label: placeholder }, ...options]
  const selected = items.find((item) => item.value === value) ?? items[0]

  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', close)
    list.current?.focus()
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  function show() {
    setActive(Math.max(0, items.findIndex((item) => item.value === value)))
    setOpen(true)
  }
  function choose(index: number) {
    onChange(items[index].value)
    setOpen(false)
    button.current?.focus()
  }
  function onButtonKey(event: KeyboardEvent) {
    if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) { event.preventDefault(); show() }
  }
  function onListKey(event: KeyboardEvent) {
    const last = items.length - 1
    const moves: Record<string, () => void> = {
      ArrowDown: () => setActive((index) => Math.min(last, index + 1)),
      ArrowUp: () => setActive((index) => Math.max(0, index - 1)),
      Home: () => setActive(0),
      End: () => setActive(last),
      Enter: () => choose(active),
      ' ': () => choose(active),
      Escape: () => { setOpen(false); button.current?.focus() },
      Tab: () => setOpen(false),
    }
    const move = moves[event.key]
    if (!move) return
    if (event.key !== 'Tab') event.preventDefault()
    move()
  }

  return (
    <div className={`home-select${open ? ' home-select--open' : ''}`} ref={root}>
      <span className="home-select__label" id={`${id}-label`}>{label}</span>
      <button
        ref={button}
        type="button"
        className="home-select__button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-labelledby={`${id}-label ${id}-value`}
        onClick={() => (open ? setOpen(false) : show())}
        onKeyDown={onButtonKey}
      >
        <span id={`${id}-value`} className={value ? undefined : 'home-select__placeholder'}>{selected.label}</span>
        <svg className="home-select__chevron" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M6 9l6 6 6-6" /></svg>
      </button>
      {open && (
        <ul
          ref={list}
          className="home-select__list"
          role="listbox"
          tabIndex={-1}
          aria-label={label}
          aria-activedescendant={`${id}-option-${active}`}
          onKeyDown={onListKey}
        >
          {items.map((item, index) => (
            <li
              key={item.value || 'all'}
              id={`${id}-option-${index}`}
              role="option"
              aria-selected={item.value === value}
              className={`home-select__option${index === active ? ' home-select__option--active' : ''}`}
              onMouseEnter={() => setActive(index)}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => choose(index)}
            >
              {item.label}
              {item.value === value && <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
