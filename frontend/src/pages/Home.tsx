import { Navigate } from 'react-router-dom'
import { getStoredUser } from '../services/auth'

function Home() {
  const user = getStoredUser()

  if (!user) {
    return <Navigate to="/login" replace />
  }

  if (user.role === 'TUTOR') {
    return <Navigate to="/tutor/setup" replace />
  }

  return <Navigate to="/discover" replace />
}

export default Home