import { useRef, useState, useEffect, type ChangeEvent } from 'react'
import { getErrorMessage } from '../../api/errors'
import { avatarApi } from '../../api/avatarApi'
import { useAuth } from '../../auth/useAuth'
import { Button, Modal, useToast } from '../ui'
import { initials } from '../../utils/format'
import { useAvatarSrc } from './useAvatarSrc'
import {
  validateAvatarFile,
  readImageDimensions,
  validateAvatarDimensions,
  SUPPORTED_AVATAR_TYPES,
} from './avatarValidation'

export interface AvatarUploadModalProps {
  open: boolean
  onClose: () => void
  currentAvatarUrl?: string | null
  fullName?: string
  onAvatarUpdated?: (newAvatarUrl: string | null) => void
}

function sanitizeAvatarSrc(src: string | null | undefined): string | null {
  if (!src) return null
  const trimmed = src.trim()
  if (
    trimmed.startsWith('blob:') ||
    trimmed.startsWith('data:image/') ||
    trimmed.startsWith('http://') ||
    trimmed.startsWith('https://') ||
    trimmed.startsWith('/images/') ||
    trimmed.startsWith('/api/')
  ) {
    if (/[<>"']|javascript:/i.test(trimmed)) {
      return null
    }
    return trimmed
  }
  return null
}

export function AvatarUploadModal({
  open,
  onClose,
  currentAvatarUrl,
  fullName,
  onAvatarUpdated,
}: AvatarUploadModalProps) {
  const { reloadUser } = useAuth()
  const toast = useToast()

  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [removing, setRemoving] = useState(false)

  const fileInputRef = useRef<HTMLInputElement>(null)
  const previewUrlRef = useRef<string | null>(null)

  const currentSrc = useAvatarSrc(currentAvatarUrl)

  useEffect(() => {
    previewUrlRef.current = previewUrl
  }, [previewUrl])

  // Cleanup object URL on unmount
  useEffect(() => {
    return () => {
      if (previewUrlRef.current) {
        URL.revokeObjectURL(previewUrlRef.current)
      }
    }
  }, [])

  const handleClose = () => {
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl)
    }
    setSelectedFile(null)
    setPreviewUrl(null)
    setFileError(null)
    setUploading(false)
    setRemoving(false)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
    onClose()
  }

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const input = event.currentTarget || event.target
    const file = input?.files?.[0]
    if (!file) return

    setFileError(null)

    // 1. File size and format check
    const problem = validateAvatarFile(file)
    if (problem) {
      setFileError(problem)
      toast.error(problem)
      if (fileInputRef.current) fileInputRef.current.value = ''
      return
    }

    // 2. Read dimensions & verify validity
    try {
      const dimensions = await readImageDimensions(file)
      const dimError = validateAvatarDimensions(dimensions)
      if (dimError) {
        setFileError(dimError)
        toast.error(dimError)
        if (fileInputRef.current) fileInputRef.current.value = ''
        return
      }

      // 3. Create preview from safe binary array buffer
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl)
      }
      const buffer = await file.arrayBuffer()
      const safeMimeType = SUPPORTED_AVATAR_TYPES.includes(file.type as (typeof SUPPORTED_AVATAR_TYPES)[number])
        ? file.type
        : 'image/jpeg'
      const safeBlob = new Blob([buffer], { type: safeMimeType })
      const objectUrl = URL.createObjectURL(safeBlob)
      setSelectedFile(file)
      setPreviewUrl(objectUrl)
    } catch (err) {
      const msg = getErrorMessage(err) || 'Photo must be a valid JPEG, PNG or WebP image'
      setFileError(msg)
      toast.error(msg)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const handleCancelSelection = () => {
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl)
    }
    setSelectedFile(null)
    setPreviewUrl(null)
    setFileError(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  const handleUpload = async () => {
    if (!selectedFile) return
    setUploading(true)
    setFileError(null)

    try {
      const updatedProfile = await avatarApi.uploadAvatar(selectedFile)
      await reloadUser()
      toast.success('Avatar uploaded successfully')
      onAvatarUpdated?.(updatedProfile.avatarUrl ?? null)
      handleClose()
    } catch (err) {
      const msg = getErrorMessage(err) || 'Failed to upload avatar'
      setFileError(msg)
      toast.error(msg)
    } finally {
      setUploading(false)
    }
  }

  const handleRemove = async () => {
    setRemoving(true)
    setFileError(null)

    try {
      await avatarApi.deleteAvatar()
      await reloadUser()
      toast.success('Avatar removed successfully')
      onAvatarUpdated?.(null)
      handleClose()
    } catch (err) {
      const msg = getErrorMessage(err) || 'Failed to remove avatar'
      setFileError(msg)
      toast.error(msg)
    } finally {
      setRemoving(false)
    }
  }

  const displaySrc = previewUrl || currentSrc
  const safeDisplaySrc = sanitizeAvatarSrc(displaySrc)
  const userInitials = initials(fullName)
  const isBusy = uploading || removing
  const hasExistingAvatar = Boolean(currentAvatarUrl)

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="Profile Avatar"
      description="Upload a custom photo for your profile (JPG, PNG, or WebP. At least 128x128 px, up to 5 MB)."
      dismissible={!isBusy}
    >
      <div className="avatar-upload-content" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {fileError && (
          <p className="notice notice--danger" role="alert" style={{ margin: 0 }}>
            {fileError}
          </p>
        )}

        <div style={{ display: 'flex', gap: '20px', alignItems: 'center' }}>
          <div
            className="avatar-preview-box"
            style={{
              width: '128px',
              height: '128px',
              background: 'var(--color-surface-soft)',
              border: '2px solid var(--color-border)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
              flexShrink: 0,
              fontSize: '36px',
              fontWeight: 700,
              color: 'var(--color-primary)',
            }}
          >
            {safeDisplaySrc ? (
              <img
                src={safeDisplaySrc}
                alt="Avatar preview"
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
            ) : (
              <span>{userInitials}</span>
            )}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', flexGrow: 1 }}>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              style={{ display: 'none' }}
              data-testid="avatar-file-input"
              aria-label="Upload photo file"
              onChange={handleFileChange}
              disabled={isBusy}
            />

            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                disabled={isBusy}
                onClick={() => fileInputRef.current?.click()}
              >
                {selectedFile ? 'Choose another photo' : hasExistingAvatar ? 'Change photo' : 'Choose photo'}
              </Button>

              {selectedFile && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={isBusy}
                  onClick={handleCancelSelection}
                >
                  Cancel selection
                </Button>
              )}

              {!selectedFile && hasExistingAvatar && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  loading={removing}
                  disabled={isBusy}
                  onClick={handleRemove}
                >
                  Remove photo
                </Button>
              )}
            </div>

            <p style={{ margin: 0, fontSize: '13px', color: 'var(--color-text-muted)' }}>
              Images will be automatically center-cropped and resized to 256x256 px WebP.
            </p>
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '16px' }}>
          <Button type="button" variant="secondary" disabled={isBusy} onClick={handleClose}>
            Close
          </Button>
          {selectedFile && (
            <Button
              type="button"
              variant="primary"
              loading={uploading}
              disabled={isBusy}
              onClick={handleUpload}
            >
              Upload avatar
            </Button>
          )}
        </div>
      </div>
    </Modal>
  )
}
