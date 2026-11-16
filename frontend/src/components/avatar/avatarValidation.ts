export const MAX_AVATAR_BYTES = 5 * 1024 * 1024 // 5 MB
export const MIN_AVATAR_DIMENSION = 128
export const SUPPORTED_AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const
export const SUPPORTED_AVATAR_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.webp'] as const

export interface ImageDimensions {
  width: number
  height: number
}

/**
 * Validates file size and MIME type.
 * Returns an error message string or null if valid.
 */
export function validateAvatarFile(file: File): string | null {
  if (!file) {
    return 'Choose a photo to upload'
  }
  if (file.size > MAX_AVATAR_BYTES) {
    return 'Photo must be at most 5 MB'
  }
  const isTypeSupported = SUPPORTED_AVATAR_TYPES.includes(file.type as (typeof SUPPORTED_AVATAR_TYPES)[number])
  const lowerName = file.name.toLowerCase()
  const hasSupportedExt = SUPPORTED_AVATAR_EXTENSIONS.some((ext) => lowerName.endsWith(ext))
  if (!isTypeSupported && !hasSupportedExt) {
    return 'Photo must be a JPEG, PNG or WebP image'
  }
  return null
}

/**
 * Reads image dimensions from a file object.
 * Rejects if the image is corrupted or cannot be read.
 */
export function readImageDimensions(file: File): Promise<ImageDimensions> {
  return new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(objectUrl)
      resolve({ width: img.naturalWidth, height: img.naturalHeight })
    }
    img.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new Error('Photo must be a valid JPEG, PNG or WebP image'))
    }
    img.src = objectUrl
  })
}

/**
 * Validates dimensions of an image.
 */
export function validateAvatarDimensions(dimensions: ImageDimensions): string | null {
  if (dimensions.width < MIN_AVATAR_DIMENSION || dimensions.height < MIN_AVATAR_DIMENSION) {
    return 'Image dimensions must be at least 128x128 pixels'
  }
  return null
}
