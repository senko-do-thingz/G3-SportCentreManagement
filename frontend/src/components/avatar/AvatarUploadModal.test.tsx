import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AvatarUploadModal } from './AvatarUploadModal'
import { avatarApi } from '../../api/avatarApi'
import { ToastProvider } from '../ui/ToastProvider'
import * as avatarValidation from './avatarValidation'

const mockReloadUser = vi.fn().mockResolvedValue(null)

vi.mock('../../auth/useAuth', () => ({
  useAuth: () => ({
    user: { id: 1, fullName: 'Taylor Tran', avatarUrl: null },
    role: 'MEMBER',
    reloadUser: mockReloadUser,
  }),
}))

vi.mock('../../api/avatarApi', () => ({
  avatarApi: {
    uploadAvatar: vi.fn().mockResolvedValue({}),
    deleteAvatar: vi.fn().mockResolvedValue(undefined),
    getAvatarBlob: vi.fn().mockResolvedValue(new Blob(['test-blob'], { type: 'image/webp' })),
  },
}))

describe('AvatarUploadModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.URL.createObjectURL = vi.fn(() => 'blob:mock-avatar-preview')
    window.URL.revokeObjectURL = vi.fn()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function renderModal(props: Partial<Parameters<typeof AvatarUploadModal>[0]> = {}) {
    return render(
      <ToastProvider>
        <AvatarUploadModal
          open={true}
          onClose={vi.fn()}
          currentAvatarUrl={null}
          fullName="Taylor Tran"
          {...props}
        />
      </ToastProvider>,
    )
  }

  it('renders modal with fallback initials when no avatar exists', () => {
    renderModal()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Profile Avatar')).toBeInTheDocument()
    expect(screen.getByText('TT')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Choose photo' })).toBeInTheDocument()
  })

  it('validates file size over 5 MB and shows error', async () => {
    renderModal()

    const largeFile = new File(['x'.repeat(avatarValidation.MAX_AVATAR_BYTES + 10)], 'huge.jpg', {
      type: 'image/jpeg',
    })

    const input = screen.getByTestId('avatar-file-input')
    fireEvent.change(input, { target: { files: [largeFile] } })

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Photo must be at most 5 MB')
    })
    expect(screen.queryByRole('button', { name: 'Upload avatar' })).not.toBeInTheDocument()
  })

  it('validates unsupported format and shows error', async () => {
    renderModal()

    const badFile = new File(['animation'], 'animation.gif', { type: 'image/gif' })
    const input = screen.getByTestId('avatar-file-input')
    fireEvent.change(input, { target: { files: [badFile] } })

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Photo must be a JPEG, PNG or WebP image')
    })
    expect(screen.queryByRole('button', { name: 'Upload avatar' })).not.toBeInTheDocument()
  })

  it('previews selected image and allows canceling selection', async () => {
    vi.spyOn(avatarValidation, 'readImageDimensions').mockResolvedValue({ width: 256, height: 256 })
    const user = userEvent.setup()
    renderModal()

    const validFile = new File(['valid'], 'avatar.png', { type: 'image/png' })
    const input = screen.getByTestId('avatar-file-input')
    await user.upload(input, validFile)

    // Preview image is rendered
    expect(await screen.findByAltText('Avatar preview')).toHaveAttribute(
      'src',
      'blob:mock-avatar-preview',
    )
    expect(screen.getByRole('button', { name: 'Upload avatar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel selection' })).toBeInTheDocument()

    // Cancel selection restores initial state
    await user.click(screen.getByRole('button', { name: 'Cancel selection' }))
    expect(screen.queryByAltText('Avatar preview')).not.toBeInTheDocument()
    expect(screen.getByText('TT')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Upload avatar' })).not.toBeInTheDocument()
  })

  it('uploads valid photo successfully and closes modal', async () => {
    vi.spyOn(avatarValidation, 'readImageDimensions').mockResolvedValue({ width: 300, height: 300 })
    vi.mocked(avatarApi.uploadAvatar).mockResolvedValue({
      id: 1,
      email: 'member@sportify.com',
      fullName: 'Taylor Tran',
      role: 'MEMBER',
      status: 'ACTIVE',
      phone: null,
      avatarUrl: '/api/v1/users/me/avatar?v=999',
    })

    const onClose = vi.fn()
    const onAvatarUpdated = vi.fn()
    const user = userEvent.setup()

    renderModal({ onClose, onAvatarUpdated })

    const validFile = new File(['valid'], 'avatar.webp', { type: 'image/webp' })
    const input = screen.getByTestId('avatar-file-input')
    await user.upload(input, validFile)

    const uploadButton = await screen.findByRole('button', { name: 'Upload avatar' })
    await user.click(uploadButton)

    await waitFor(() => {
      expect(avatarApi.uploadAvatar).toHaveBeenCalledWith(validFile)
      expect(mockReloadUser).toHaveBeenCalled()
      expect(onAvatarUpdated).toHaveBeenCalledWith('/api/v1/users/me/avatar?v=999')
      expect(onClose).toHaveBeenCalled()
    })
  })

  it('handles remove photo when avatar exists', async () => {
    vi.mocked(avatarApi.deleteAvatar).mockResolvedValue()
    const onClose = vi.fn()
    const onAvatarUpdated = vi.fn()
    const user = userEvent.setup()

    renderModal({
      currentAvatarUrl: '/api/v1/users/me/avatar?v=1',
      onClose,
      onAvatarUpdated,
    })

    const removeBtn = screen.getByRole('button', { name: 'Remove photo' })
    await user.click(removeBtn)

    await waitFor(() => {
      expect(avatarApi.deleteAvatar).toHaveBeenCalled()
      expect(mockReloadUser).toHaveBeenCalled()
      expect(onAvatarUpdated).toHaveBeenCalledWith(null)
      expect(onClose).toHaveBeenCalled()
    })
  })
})
