import { Link } from 'react-router-dom'
import { Button } from './ui/Button'
import { HOME_SPORTS } from '../pages/public/homeContent'

export interface FooterProps {
  /** Callback khi nguoi dung nhan Contact reception */
  onContactReception?: () => void
  /** Callback khi nguoi dung nhan AI Assistant */
  onOpenAssistant?: () => void
  /** Che do thu gon (chi hien thanh bottom bar) */
  compact?: boolean
}

/** Component Footer dung chung cho cac trang cong khai */
export function Footer({ onContactReception, onOpenAssistant, compact = false }: FooterProps) {
  return (
    <footer className={`home-footer${compact ? ' home-footer--compact' : ''}`}>
      {!compact && (
        <div className="home-wrap home-footer__grid">
          <div>
            <p className="home-footer__logo">Sportify</p>
            <p>One center. Six sports. More ways to grow.</p>
          </div>
          <nav aria-label="Footer explore">
            <h2>Explore</h2>
            <ul>
              <li>
                <a href="/#finder">Find a class</a>
              </li>
              <li>
                <a href="/#sports">Sports</a>
              </li>
              <li>
                <a href="/#facilities">Facilities</a>
              </li>
              <li>
                <a href="/#coaches">Coaches</a>
              </li>
              <li>
                <a href="/#packages">Packages</a>
              </li>
            </ul>
          </nav>
          <nav aria-label="Footer sports">
            <h2>Sports</h2>
            <ul>
              {HOME_SPORTS.map((sport) => (
                <li key={sport.name}>
                  <a href="/#sports">{sport.name}</a>
                </li>
              ))}
            </ul>
          </nav>
          <div className="home-footer__help">
            <h2>Need help?</h2>
            {onContactReception ? (
              <Button onClick={onContactReception}>Contact reception</Button>
            ) : (
              <Link to="/terms#contact-section" className="home-footer__help-link">
                Contact reception
              </Link>
            )}
            {onOpenAssistant && (
              <Button variant="secondary" onClick={onOpenAssistant}>
                AI Assistant
              </Button>
            )}
          </div>
        </div>
      )}
      <div className="home-wrap home-footer__bottom">
        <small>Copyright 2026 Sportify Sports Center</small>
        <div className="home-footer__links">
          <Link to="/terms">Terms of Service</Link>
          <Link to="/terms#privacy">Privacy Policy</Link>
          <Link to="/terms#contact-section">Contact</Link>
          <Link to="/credits">Photo credits</Link>
        </div>
      </div>
    </footer>
  )
}
