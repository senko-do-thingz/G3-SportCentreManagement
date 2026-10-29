/**
 * Copies text to the clipboard. Uses the async Clipboard API when the page is in a secure context
 * (https or localhost) and falls back to a hidden textarea with document.execCommand('copy'),
 * which still works on a plain http demo server. Rejects when both ways fail, so the caller can
 * tell the member to copy the value by hand.
 * Demo callers can disable the legacy fallback to report permission denial without moving focus.
 */
export async function copyText(text: string, { allowFallback = true }: { allowFallback?: boolean } = {}): Promise<void> {
  if (typeof navigator !== 'undefined' && navigator.clipboard && (allowFallback ? window.isSecureContext !== false : window.isSecureContext === true)) {
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch {
      if (!allowFallback) throw new Error('Copy is not available in this browser.')
      // Permission denied or not focused: try the fallback below.
    }
  }
  if (!allowFallback || !copyWithTextarea(text)) {
    throw new Error('Copy is not available in this browser.')
  }
}

function copyWithTextarea(text: string): boolean {
  if (typeof document === 'undefined' || typeof document.execCommand !== 'function') {
    return false
  }
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '-1000px'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  textarea.select()
  let copied: boolean
  try {
    copied = document.execCommand('copy')
  } catch {
    copied = false
  }
  document.body.removeChild(textarea)
  previousFocus?.focus()
  return copied
}
