import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { Brand } from '../../components/Brand'
import { ThemeToggle } from '../../theme/ThemeToggle'
import '@fontsource/be-vietnam-pro/800.css'
import '@fontsource/be-vietnam-pro/800-italic.css'
import '../../styles/home.css'
import { buildCredits } from './creditsContent'

/** Public "Photo credits" page linked from the Home footer: source and license of every third-party asset. */
export function CreditsPage() {
  const groups = buildCredits()
  useEffect(() => {
    document.title = 'Photo credits - Sportify Center'
    document.documentElement.scrollTop = 0
    document.body.scrollTop = 0
  }, [])
  return (
    <div className="home-page credits-page">
      <header className="credits-bar">
        <div className="home-wrap credits-bar__inner">
          <Brand compact />
          <div className="credits-bar__actions">
            <ThemeToggle className="home-theme" />
            <Link to="/" className="credits-bar__back">Back to Home</Link>
          </div>
        </div>
      </header>
      <main className="home-wrap credits-main" id="main-content">
        <span className="home-kicker">Sources</span>
        <h1>Photo <span>credits</span></h1>
        <p className="credits-intro">The photos, videos and font on this site come from free libraries. Each item below links to its source; the license of each library is linked in its heading.</p>
        {groups.map((group) => (
          <section key={group.title} className="credits-group" aria-labelledby={`credits-${group.title}`}>
            <h2 id={`credits-${group.title}`}>{group.title}</h2>
            <p className="credits-meta">{group.provider} - <a href={group.license.url} target="_blank" rel="noopener noreferrer">{group.license.name}</a>. {group.note}</p>
            <ul className="credits-grid">
              {group.items.map((item) => (
                <li key={item.usedFor} className="credits-item">
                  {item.thumb ? <img src={item.thumb} alt="" width="320" height="200" loading="lazy" decoding="async" /> : <span className="credits-item__placeholder" aria-hidden="true">{group.title === 'Font' ? 'Aa' : 'Video'}</span>}
                  <div>
                    <p className="credits-item__use">{item.usedFor}</p>
                    <a href={item.link} target="_blank" rel="noopener noreferrer" aria-label={`${item.linkLabel} - ${item.usedFor}`}>{item.linkLabel}</a>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        ))}
        <p className="credits-intro">The project also keeps small copies of the credited photos below, shown only when an online photo cannot load.</p>
      </main>
      <footer className="home-footer credits-footer">
        <div className="home-wrap home-footer__bottom"><small>Copyright 2026 Sportify Sports Center</small><Link to="/">Back to Home</Link></div>
      </footer>
    </div>
  )
}
