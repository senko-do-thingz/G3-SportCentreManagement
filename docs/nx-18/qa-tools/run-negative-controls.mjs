// Negative control in a real browser. Runs flow4-qa.mjs once clean and once per injected defect on one
// screen and checks the exit codes: the clean run must exit 0 and every injected run must exit 1.
// Usage (dev server of the branch under test on port 5175): node run-negative-controls.mjs <outDir> [baseUrl]
// Exit code: 0 only when the clean run passed and every injected defect was caught.
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const OUT = process.argv[2]
const BASE = process.argv[3] ?? 'http://localhost:5175'
const SCREEN = 'coach-schedule'
const DEFECTS = ['overflow', 'h1', 'axe', 'focus', 'console', 'error-alert', 'forbidden-alert', 'empty-panel']
fs.mkdirSync(OUT, { recursive: true })

function run(label, extra) {
  const dir = path.join(OUT, label)
  const result = spawnSync(process.execPath, [path.join(here, 'flow4-qa.mjs'), dir, BASE, '--only=' + SCREEN, '--sizes=1440', ...extra], { encoding: 'utf8' })
  const lines = (result.stdout || '').split('\n')
  return { label, exitCode: result.status, firstFailure: lines.find((l) => l.startsWith('FAIL')) ?? null, resultLine: lines.find((l) => l.startsWith('RESULT')) ?? null }
}

const rows = [run('clean', [])]
for (const defect of DEFECTS) rows.push(run('inject-' + defect, ['--inject-defect=' + defect]))
const expectation = (r) => (r.label === 'clean' ? r.exitCode === 0 : r.exitCode === 1)
for (const r of rows) {
  r.ok = expectation(r)
  console.log((r.ok ? 'OK   ' : 'WRONG') + ' ' + r.label.padEnd(24) + ' exit=' + r.exitCode + (r.firstFailure ? ' | ' + r.firstFailure : ''))
}
fs.writeFileSync(path.join(OUT, 'negative-control.json'), JSON.stringify(rows, null, 1))
const bad = rows.filter((r) => !r.ok)
console.log(bad.length ? `NEGATIVE CONTROL FAILED for: ${bad.map((r) => r.label).join(', ')}` : 'NEGATIVE CONTROL PASSED: clean run exit 0, all injected defects exit 1')
process.exitCode = bad.length ? 1 : 0
