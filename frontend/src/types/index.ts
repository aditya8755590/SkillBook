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
  tokenType: string
  expiresIn: number
  user: User
}

interface ApiError {
  error?: string
  message?: string
}

export type { ApiResponse, User, AuthResponse, ApiError }