import { useNavigate } from 'react-router-dom'
import { getStoredUser, clearSession } from '../services/auth'
import { Button } from '../components/ui/Button'

function Home() {
  const navigate = useNavigate()
  const user = getStoredUser()

  if (!user) {
    navigate('/login', { replace: true })
    return null
  }

  const handleLogout = () => {
    clearSession()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-surface-secondary px-4">
      <div className="w-full max-w-lg rounded-xl border border-border bg-surface p-8 shadow-sm">
        <h1 className="m-0 text-2xl font-bold text-text">Welcome, {user.name}</h1>
        <p className="m-0 mt-1 text-sm text-text-secondary">
          {user.email} · {user.role === 'TUTOR' ? 'Tutor' : 'Student'}
        </p>
        <p className="m-0 mt-6 text-sm text-text-secondary">
          You are signed in. Skill and discovery features arrive in later phases.
        </p>
        <Button type="button" variant="secondary" className="mt-6 w-full" onClick={handleLogout}>
          Sign out
        </Button>
      </div>
    </div>
  )
}

export default Home