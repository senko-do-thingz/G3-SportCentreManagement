import { describe, expect, it } from 'vitest'
import {
  MAX_AVATAR_BYTES,
  validateAvatarDimensions,
  validateAvatarFile,
} from './avatarValidation'

describe('avatarValidation', () => {
  it('rejects files larger than 5 MB', () => {
    const largeFile = new File(['x'.repeat(MAX_AVATAR_BYTES + 1)], 'photo.jpg', {
      type: 'image/jpeg',
    })
    expect(validateAvatarFile(largeFile)).toBe('Photo must be at most 5 MB')
  })

  it('accepts supported formats under 5 MB', () => {
    const jpg = new File(['bytes'], 'profile.jpg', { type: 'image/jpeg' })
    const jpeg = new File(['bytes'], 'profile.jpeg', { type: 'image/jpeg' })
    const png = new File(['bytes'], 'profile.png', { type: 'image/png' })
    const webp = new File(['bytes'], 'profile.webp', { type: 'image/webp' })

    expect(validateAvatarFile(jpg)).toBeNull()
    expect(validateAvatarFile(jpeg)).toBeNull()
    expect(validateAvatarFile(png)).toBeNull()
    expect(validateAvatarFile(webp)).toBeNull()
  })

  it('rejects unsupported formats such as GIF or SVG', () => {
    const gif = new File(['bytes'], 'animation.gif', { type: 'image/gif' })
    const svg = new File(['bytes'], 'vector.svg', { type: 'image/svg+xml' })
    const txt = new File(['bytes'], 'document.txt', { type: 'text/plain' })

    expect(validateAvatarFile(gif)).toBe('Photo must be a JPEG, PNG or WebP image')
    expect(validateAvatarFile(svg)).toBe('Photo must be a JPEG, PNG or WebP image')
    expect(validateAvatarFile(txt)).toBe('Photo must be a JPEG, PNG or WebP image')
  })

  it('rejects dimensions smaller than 128x128', () => {
    expect(validateAvatarDimensions({ width: 127, height: 128 })).toBe(
      'Image dimensions must be at least 128x128 pixels',
    )
    expect(validateAvatarDimensions({ width: 128, height: 127 })).toBe(
      'Image dimensions must be at least 128x128 pixels',
    )
    expect(validateAvatarDimensions({ width: 100, height: 100 })).toBe(
      'Image dimensions must be at least 128x128 pixels',
    )
  })

  it('accepts dimensions >= 128x128', () => {
    expect(validateAvatarDimensions({ width: 128, height: 128 })).toBeNull()
    expect(validateAvatarDimensions({ width: 512, height: 512 })).toBeNull()
    expect(validateAvatarDimensions({ width: 1024, height: 768 })).toBeNull()
  })
})
