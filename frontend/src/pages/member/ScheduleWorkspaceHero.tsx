import type { ReactNode } from 'react'

type ScheduleWorkspaceHeroProps = {
  title: string
  description: string
  imageSrc: string
  imageAlt: string
  fadeImage?: boolean
  actions?: ReactNode
}

export function ScheduleWorkspaceHero({
  title,
  description,
  imageSrc,
  imageAlt,
  fadeImage = false,
  actions,
}: ScheduleWorkspaceHeroProps) {
  const titleId = `schedule-workspace-${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}-title`

  return (
    <section className={`schedule-workspace__hero${fadeImage ? ' schedule-workspace__hero--fade-image' : ''}`} aria-labelledby={titleId}>
      <div className="schedule-workspace__hero-copy">
        <p className="schedule-workspace__hero-label">Member / Schedules &amp; bookings</p>
        <h1 id={titleId}>{title}</h1>
        <p className="schedule-workspace__hero-description">{description}</p>
        {actions && <div className="schedule-workspace__hero-actions">{actions}</div>}
      </div>
      <div className="schedule-workspace__hero-image">
        <img src={imageSrc} alt={imageAlt} />
      </div>
    </section>
  )
}
