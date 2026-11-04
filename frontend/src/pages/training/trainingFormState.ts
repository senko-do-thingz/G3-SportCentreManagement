import { useRef, useState } from 'react'
import { ApiError, getErrorMessage } from '../../api/errors'

/** Keep form values on failure; retry is the same explicit submit action. */
export function useTrainingAction() {
  const running = useRef(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)

  async function run(action: () => Promise<void>, message: string) {
    if (running.current) return
    running.current = true
    setPending(true)
    setError(null)
    setSuccess(null)
    setConflict(false)
    try {
      await action()
      setSuccess(message)
    } catch (failure) {
      setError(getErrorMessage(failure))
      setConflict(failure instanceof ApiError && failure.status === 409)
    } finally {
      running.current = false
      setPending(false)
    }
  }

  function reset() {
    if (running.current) return
    setError(null)
    setSuccess(null)
    setConflict(false)
  }

  return { pending, error, success, conflict, run, reset }
}

export const attendanceOptions = [
  { value: 'PRESENT', label: 'Present' },
  { value: 'LATE', label: 'Late' },
  { value: 'ABSENT', label: 'Absent' },
  { value: 'EXCUSED', label: 'Excused' },
]

export function attendanceLabel(status: string | null) {
  return attendanceOptions.find(option => option.value === status)?.label ?? 'Not marked'
}
