/** Accepted photo types, matching the backend (it checks the file content, not the name). */
export const PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const
/** Largest original the browser will try to read; the result is resized well below the 2 MB upload limit. */
export const MAX_SOURCE_BYTES = 15 * 1024 * 1024
export const MAX_UPLOAD_BYTES = 2 * 1024 * 1024
const SIZE = 640

/** Returns a user-facing problem with the chosen file, or null when it can be prepared. */
export function photoFileProblem(file: File): string | null {
  if (!PHOTO_TYPES.includes(file.type as (typeof PHOTO_TYPES)[number])) return 'Choose a JPEG, PNG or WebP photo.'
  if (file.size > MAX_SOURCE_BYTES) return 'This photo is too large. Choose one under 15 MB.'
  return null
}

/**
 * Crops the photo to a centred square (faces are usually in the middle of a portrait) and scales it to 640 x 640 JPEG,
 * so every coach card looks the same and the upload stays small. Falls back to the original file when the browser
 * cannot draw images; the upload limit still applies.
 */
export async function preparePhoto(file: File): Promise<Blob> {
  if (typeof createImageBitmap !== 'function' || typeof document === 'undefined') return file
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d')
  if (!context) return file
  const bitmap = await createImageBitmap(file)
  try {
    const side = Math.min(bitmap.width, bitmap.height)
    const x = (bitmap.width - side) / 2
    const y = Math.max(0, (bitmap.height - side) / 3)
    canvas.width = SIZE
    canvas.height = SIZE
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, SIZE, SIZE)
    context.drawImage(bitmap, x, y, side, side, 0, 0, SIZE, SIZE)
  } finally {
    bitmap.close?.()
  }
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.86))
  return blob ?? file
}
