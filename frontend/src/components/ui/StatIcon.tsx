/** Line icons for KPI cards, drawn like the Coach schedule metrics and the Home icons (24px grid, 1.8 stroke). */
export type StatIconName =
  | 'users' | 'user-check' | 'badge' | 'filter' | 'list' | 'file' | 'book' | 'check' | 'sports' | 'coach'
  | 'package' | 'calendar' | 'send' | 'pin' | 'receipt' | 'rows' | 'refund' | 'card' | 'percent' | 'star'
  | 'shield' | 'clock' | 'note' | 'question'

const PATHS: Record<StatIconName, string> = {
  users: 'M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm-5.5 9v-1.2c0-3.2 2.2-5 5.5-5s5.5 1.8 5.5 5V20M16 5.2a3 3 0 0 1 0 5.6m1.5 3.2c2 .6 3 2.2 3 4.8V20',
  'user-check': 'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm-6 9a6 6 0 0 1 12 0m1-9 2 2 4-4',
  badge: 'M4 6.5A1.5 1.5 0 0 1 5.5 5h13A1.5 1.5 0 0 1 20 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5v-11ZM9 12a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm-3 4a3 3 0 0 1 6 0m3-6h3m-3 3h3',
  filter: 'M4 5h16l-6 7.5V19l-4-2v-4.5L4 5Z',
  list: 'M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01',
  file: 'M6 3h8l4 4v14H6V3Zm8 0v4h4M9 12h6m-6 4h6',
  book: 'M12 7c-2.4-1.9-5.4-2.4-9-2v14c3.6-.4 6.6.1 9 2V7Zm0 0c2.4-1.9 5.4-2.4 9-2v14c-3.6-.4-6.6.1-9 2',
  check: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm-4-9 3 3 5-6',
  sports: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Zm0 0v18M3.5 9h17M3.5 15h17',
  coach: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9a7 7 0 0 1 14 0',
  package: 'M4 7.5 12 3l8 4.5v9L12 21l-8-4.5v-9Zm0 0 8 4.5 8-4.5M12 12v9',
  calendar: 'M4 6.5A1.5 1.5 0 0 1 5.5 5h13A1.5 1.5 0 0 1 20 6.5v12a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5v-12ZM4 10h16M8 3v4m8-4v4',
  send: 'M4 12 20 4l-4 16-4-7-8-1Zm8 1 8-9',
  pin: 'M12 21s7-6.2 7-11.5A7 7 0 0 0 5 9.5C5 14.8 12 21 12 21Zm0-9a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z',
  receipt: 'M6 3h12v18l-3-2-3 2-3-2-3 2V3Zm3 5h6m-6 4h6m-6 4h3',
  rows: 'M4 5h16v4H4V5Zm0 6h16v4H4v-4Zm0 6h16v2H4v-2Z',
  refund: 'M4 12a8 8 0 1 0 2.3-5.6M4 4v4h4m4 0v8m-2.5-2.5c0 1.2 1.1 2 2.5 2s2.5-.8 2.5-2-1.1-1.7-2.5-2-2.5-.8-2.5-2 1.1-2 2.5-2 2.5.8 2.5 2',
  card: 'M3 6.5A1.5 1.5 0 0 1 4.5 5h15A1.5 1.5 0 0 1 21 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5v-11ZM3 10h18M7 15h4',
  percent: 'M19 5 5 19M7.5 9a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Zm9 9a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z',
  star: 'm12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9L12 3Z',
  shield: 'M12 3 4 6v6c0 4.5 3.4 8.2 8 9 4.6-.8 8-4.5 8-9V6l-8-3Zm-3.5 9 2.5 2.5 4.5-5',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-13v4.5l3 2',
  note: 'M5 4h14v16H5V4Zm3 4h8m-8 4h8m-8 4h5',
  question: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm-2.5-11.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6V14m0 3h.01',
}

/** Decorative: the card's own label names the value, so the icon is hidden from assistive technology. */
export function StatIcon({ name }: { name: StatIconName }) {
  return (
    <svg className="stat-icon" viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
      <path d={PATHS[name]} />
    </svg>
  )
}
