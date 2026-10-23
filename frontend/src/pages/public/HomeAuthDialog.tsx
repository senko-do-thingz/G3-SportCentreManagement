import { Link } from 'react-router-dom'
import { Button, Modal } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'

function actionDescription(path: string) {
  if (path.startsWith('/member/classes')) return 'Sign in to explore current classes with your selected filters.'
  if (path.startsWith('/member/schedule')) return 'Sign in to view the member schedule and current sessions.'
  if (path.startsWith('/member/membership/cards')) return 'Sign in to view membership card options in your Member workspace.'
  return 'Sign in to review package options in your Member workspace. Registration requires your confirmation.'
}

/** A single Home gate; authentication only resumes navigation, never a booking or purchase. */
export function HomeAuthDialog({ destination, onClose }: { destination: string | null; onClose: () => void }) {
  return <Modal
    open={destination !== null}
    onClose={onClose}
    title="Sign in to continue"
    description={destination ? actionDescription(destination) : undefined}
    size="sm"
    footer={<div className="home-auth-actions">
      <Link className={buttonClassName('primary', 'md')} to="/login" state={{ from: destination }}>Log in</Link>
      <Link className={buttonClassName('secondary', 'md')} to="/register" state={{ from: destination }}>Create account</Link>
      <Button variant="secondary" onClick={onClose}>Continue browsing</Button>
    </div>}
  />
}
