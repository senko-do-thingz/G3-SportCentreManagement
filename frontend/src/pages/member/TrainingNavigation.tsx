import { NavLink } from 'react-router-dom'

const trainingLinks = [
  { path: '', label: 'Overview', end: true },
  { path: 'classes', label: 'My classes' },
  { path: 'questions', label: 'Ask your coach' },
  { path: 'plans', label: 'Training plans' },
  { path: 'feedback', label: 'Coach feedback' },
  { path: 'attendance', label: 'Attendance history' },
  { path: 'results', label: 'Training results' },
  { path: 'recommendations', label: 'Class recommendations' },
  { path: 'goals', label: 'Training preferences' },
]

export function TrainingNavigation() {
  return (
    <nav className="training-navigation" aria-label="Training navigation">
      {trainingLinks.map(item => (
        <NavLink key={item.path || 'overview'} to={item.path ? `/member/training/${item.path}` : '/member/training'} end={item.end}>
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}
