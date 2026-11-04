const sports = [
  ['Football', 'FB'], ['Swimming', 'SW'], ['Badminton', 'BM'],
  ['Volleyball', 'VB'], ['Basketball', 'BK'], ['Tennis', 'TE'],
]

export function SessionIdGuide() {
  return <details className="session-id-guide">
    <summary>Understand Session IDs</summary>
    <p>The date, sport and sequence identify each session. Example: <code>061126SW01</code> means Swimming on 06/11/2026, sequence 01.</p>
    <dl>{sports.map(([name, code]) => <div key={code}><dt>{name}</dt><dd><code>{code}</code></dd></div>)}</dl>
    <p>After a date change, use the new Session ID shown on the schedule.</p>
  </details>
}
