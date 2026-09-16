interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: string
  timestamp: string
}

interface User {
  id: string
  email: string
  name: string
  role: 'STUDENT' | 'TUTOR' | 'ADMIN'
}

interface AuthResponse {
  token: string
  user: User
}

export type { ApiResponse, User, AuthResponse }