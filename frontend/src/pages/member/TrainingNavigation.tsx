import { NavLink } from 'react-router-dom'

const trainingLinks = [
  { path: 'classes', label: 'My classes' },
  { path: 'questions', label: 'Ask your coach' },
  { path: 'plans', label: 'Training plans' },
  { path: 'feedback', label: 'Coach feedback' },
  { path: 'attendance', label: 'Attendance history' },
  { path: 'results', label: 'Training results' },
  { path: 'recommendations', label: 'Class recommendations' },
]

export function TrainingNavigation() {
  return (
    <nav className="training-navigation" aria-label="Training navigation">
      {trainingLinks.map(item => (
        <NavLink key={item.path} to={`/member/training/${item.path}`}>
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}
