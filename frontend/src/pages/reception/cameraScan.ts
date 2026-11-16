/** Bo qua lan quet lap lai cung mot ma trong khoang thoi gian nay de tranh tao check-in trung. */
export const SAME_CODE_COOLDOWN_MS = 4000

export const CAMERA_MESSAGES = {
  unsupported:
    'This browser cannot open the camera here. Use a current browser on HTTPS or localhost, or type the member code.',
  permissionDenied:
    'Camera permission was denied. Allow camera access for this site in the browser settings, or type the member code.',
  notFound: 'No camera was found on this computer. Connect a webcam, or type the member code.',
  inUse: 'The camera is already in use by another application. Close that application and try again.',
  failed: 'The camera could not be started. Try again, or type the member code.',
} as const

/** Doi loi getUserMedia (DOMException.name) sang thong bao de hieu cho le tan. */
export function cameraErrorMessage(error: unknown): string {
  const name = error instanceof Error || error instanceof DOMException ? error.name : ''
  switch (name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return CAMERA_MESSAGES.permissionDenied
    case 'NotFoundError':
    case 'OverconstrainedError':
      return CAMERA_MESSAGES.notFound
    case 'NotReadableError':
    case 'AbortError':
      return CAMERA_MESSAGES.inUse
    case 'NotSupportedError':
      return CAMERA_MESSAGES.unsupported
    default:
      return CAMERA_MESSAGES.failed
  }
}
