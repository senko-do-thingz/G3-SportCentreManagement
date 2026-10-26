import { useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react'

export interface HomeSelectOption { value: string; label: string }

interface HomeSelectProps {
  label: string
  placeholder: string
  value: string
  options: readonly HomeSelectOption[]
  onChange: (value: string) => void
  layout?: 'list' | 'grid'
}

/**
 * Themed single-select for the Home class finder (listbox pattern).
 * Keyboard: Enter, Space or ArrowDown opens; ArrowUp and ArrowDown move; Home and End jump; Enter selects; Escape closes.
 */
export function HomeSelect({ label, placeholder, value, options, onChange, layout = 'list' }: HomeSelectProps) {
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
    const close = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false) }
    document.addEventListener('pointerdown', close)
    list.current?.focus()
    return () => document.removeEventListener('pointerdown', close)
  }, [open])

  useLayoutEffect(() => {
    if (!open || layout !== 'grid') return
    function place() {
      const menu = list.current
      const field = root.current
      if (!menu || !field) return
      const rect = field.getBoundingClientRect()
      const form = field.closest('form')?.getBoundingClientRect() ?? rect
      const headerBottom = document.querySelector('.home-header')?.getBoundingClientRect().bottom ?? 0
      const floor = Math.max(8, headerBottom + 8)
      // Anchor outside the whole form so tablet rows and the submit action stay clear.
      const below = Math.max(0, window.innerHeight - form.bottom - 16)
      const above = Math.max(0, form.top - floor - 8)
      menu.style.width = `${Math.min(rect.width - 24, window.innerWidth - 32)}px`
      menu.style.left = `${Math.max(16, rect.left + 12)}px`
      const desired = menu.scrollHeight
      const up = desired > below && above > below
      const available = up ? above : below
      const sheet = window.innerWidth <= 640 && Math.max(above, below) < 88
      menu.style.maxHeight = `${sheet ? Math.max(44, window.innerHeight - floor - 16) : available}px`
      menu.style.top = `${sheet ? floor : up ? Math.max(floor, form.top - Math.min(desired, available) - 8) : form.bottom + 8}px`
      menu.dataset.placement = sheet ? 'sheet' : up ? 'above' : 'below'
    }
    place()
    window.addEventListener('resize', place)
    window.addEventListener('scroll', place, { passive: true })
    return () => { window.removeEventListener('resize', place); window.removeEventListener('scroll', place) }
  }, [open, layout, options.length])

  useEffect(() => {
    const menu = list.current
    const option = menu?.children[active] as HTMLElement | undefined
    if (!menu || !option) return
    if (option.offsetTop < menu.scrollTop) menu.scrollTop = option.offsetTop
    else if (option.offsetTop + option.offsetHeight > menu.scrollTop + menu.clientHeight) menu.scrollTop = option.offsetTop + option.offsetHeight - menu.clientHeight
  }, [active, open])

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
      // Restore the trigger before native Tab traversal so removal of the list does not lose its position.
      Tab: () => { button.current?.focus(); setOpen(false) },
    }
    const move = moves[event.key]
    if (!move) return
    if (event.key !== 'Tab') event.preventDefault()
    move()
  }

  return (
    <div className={`home-select${open ? ' home-select--open' : ''}`} ref={root}
      onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget as Node)) setOpen(false) }}>
      <span className="home-select__label" id={`${id}-label`}>{label}</span>
      <button
        ref={button}
        type="button"
        className="home-select__button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? `${id}-list` : undefined}
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
          id={`${id}-list`}
          className={`home-select__list${layout === 'grid' ? ' home-select__list--grid' : ''}`}
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
