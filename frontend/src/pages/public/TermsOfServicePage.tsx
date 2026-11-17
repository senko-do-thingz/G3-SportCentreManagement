import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Brand } from '../../components/Brand'
import { ThemeToggle } from '../../theme/ThemeToggle'
import { TOS_SECTIONS } from './termsOfServiceContent'
import '@fontsource/be-vietnam-pro/700.css'
import '@fontsource/be-vietnam-pro/800.css'
import '../../styles/termsOfService.css'

/** Trang Terms of Service (Dieu khoan dich vu) voi 9 muc accordion */
export function TermsOfServicePage() {
  const location = useLocation()

  // Khoi tao muc mo dau tien la true theo mac dinh, va tu dong mo privacy neu co hash
  const [openSections, setOpenSections] = useState<Record<string, boolean>>(() => {
    const hash = typeof window !== 'undefined' ? window.location.hash.replace('#', '') : ''
    const initial: Record<string, boolean> = {}
    TOS_SECTIONS.forEach((section, index) => {
      initial[section.id] = index === 0 || ((hash === 'privacy' || hash === 'privacy-and-data') && section.id === 'privacy-and-data')
    })
    return initial
  })

  useEffect(() => {
    document.title = 'Terms of Service - Sportify Sports Center'
    const hash = window.location.hash.replace('#', '')
    if (hash === 'privacy' || hash === 'privacy-and-data') {
      const targetElement = document.getElementById('privacy-and-data')
      targetElement?.scrollIntoView({ behavior: 'smooth' })
    } else if (hash === 'contact' || hash === 'contact-section') {
      const contactElement = document.getElementById('contact-section')
      contactElement?.scrollIntoView({ behavior: 'smooth' })
    } else {
      window.scrollTo(0, 0)
    }
  }, [location.hash])

  const toggleSection = (id: string) => {
    setOpenSections((current) => ({
      ...current,
      [id]: !current[id],
    }))
  }

  const allExpanded = TOS_SECTIONS.every((section) => openSections[section.id])

  const toggleAll = () => {
    const targetState = !allExpanded
    const updated: Record<string, boolean> = {}
    TOS_SECTIONS.forEach((section) => {
      updated[section.id] = targetState
    })
    setOpenSections(updated)
  }

  return (
    <div className="tos-page">
      <header className="tos-header">
        <div className="home-wrap tos-header__inner">
          <Brand compact />
          <div className="tos-header__actions">
            <ThemeToggle className="home-theme" />
            <Link to="/" className="tos-header__back">
              Back to Home
            </Link>
          </div>
        </div>
      </header>

      <main className="home-wrap tos-main" id="main-content">
        <span className="tos-kicker">Legal and Policies</span>
        <h1 className="tos-title">Terms of Service</h1>
        <p className="tos-meta">Effective date: November 17, 2026 - Sportify Sports Center</p>

        <p className="tos-intro">
          Welcome to Sportify Sports Center. These Terms of Service outline the rules, obligations, and standards
          governing the use of our sports facilities, training classes, membership packages, and online portal. Please
          review these terms carefully before participating in any activities.
        </p>

        <div className="tos-controls">
          <button
            type="button"
            className="tos-toggle-all-btn"
            onClick={toggleAll}
            aria-expanded={allExpanded}
          >
            {allExpanded ? 'Collapse all' : 'Expand all'}
          </button>
        </div>

        <div className="tos-accordion-list" role="region" aria-label="Terms of Service Sections">
          {TOS_SECTIONS.map((section) => {
            const isExpanded = Boolean(openSections[section.id])
            const headerId = `heading-${section.id}`
            const panelId = `panel-${section.id}`

            return (
              <div
                key={section.id}
                id={section.id}
                className={`tos-accordion-item${isExpanded ? ' tos-accordion-item--expanded' : ''}`}
              >
                <h2 className="tos-accordion-header" id={headerId}>
                  <button
                    type="button"
                    className="tos-accordion-trigger"
                    onClick={() => toggleSection(section.id)}
                    aria-expanded={isExpanded}
                    aria-controls={panelId}
                  >
                    <span>{section.title}</span>
                    <span className="tos-accordion-icon" aria-hidden="true">
                      {isExpanded ? '-' : '+'}
                    </span>
                  </button>
                </h2>
                {isExpanded && (
                  <div
                    id={panelId}
                    role="region"
                    aria-labelledby={headerId}
                    className="tos-accordion-panel"
                  >
                    {section.content.map((paragraph, index) => (
                      <p key={index}>{paragraph}</p>
                    ))}
                  </div>
                )}
              </div>
            )
          })}
        </div>

        <section id="contact-section" className="tos-contact-section" aria-labelledby="contact-heading">
          <h2 id="contact-heading" className="tos-contact-title">
            Questions or Need Assistance?
          </h2>
          <p className="tos-contact-desc">
            If you have questions regarding these Terms of Service, require help with package rules, or need dispute
            resolution, please contact our team directly through the channels below.
          </p>

          <div className="tos-contact-grid">
            <div className="tos-contact-card">
              <h3>Receptionist Desk</h3>
              <p>For check-in assistance, bookings, card issues, and counter inquiries.</p>
              <p>
                <strong>Location:</strong> Ground Floor Lobby, Sportify Center
              </p>
              <p>
                <strong>Operating hours:</strong> 06:00 - 22:00 (Daily)
              </p>
              <p>
                <strong>Hotline:</strong> <a href="tel:+842839148888">(+84) 28 3914 8888</a>
              </p>
              <p>
                <strong>Email:</strong> <a href="mailto:reception@sportify.vn">reception@sportify.vn</a>
              </p>
            </div>

            <div className="tos-contact-card">
              <h3>Center Management</h3>
              <p>For formal feedback, dispute escalations, partner inquiries, and legal policies.</p>
              <p>
                <strong>Location:</strong> Executive Suite, Level 2, Sportify Center
              </p>
              <p>
                <strong>Office hours:</strong> 08:30 - 17:30 (Monday - Friday)
              </p>
              <p>
                <strong>Support email:</strong> <a href="mailto:management@sportify.vn">management@sportify.vn</a>
              </p>
            </div>
          </div>
        </section>
      </main>

      <footer className="tos-footer">
        <div className="home-wrap tos-footer__inner">
          <small>Copyright 2026 Sportify Sports Center</small>
          <ul className="tos-footer__links">
            <li>
              <Link to="/terms">Terms of Service</Link>
            </li>
            <li>
              <Link to="/terms#privacy">Privacy Policy</Link>
            </li>
            <li>
              <a href="#contact-section">Contact</a>
            </li>
            <li>
              <Link to="/credits">Photo credits</Link>
            </li>
            <li>
              <Link to="/">Home</Link>
            </li>
          </ul>
        </div>
      </footer>
    </div>
  )
}
