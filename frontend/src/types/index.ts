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

interface Skill {
  id: string
  name: string
  category?: string
}

interface TutorSummary {
  id: string
  userId: string
  name: string
  bio?: string
  experienceYears: number
  hourlyRate: number
  rating: number
  skills: Skill[]
  createdAt?: string
}

interface PageData<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

interface TutorSearchParams {
  q?: string
  skill?: string
  minRate?: number
  maxRate?: number
  sort?: string
  page?: number
  size?: number
}

interface TutorProfilePayload {
  bio?: string
  experienceYears?: number
  hourlyRate?: number
}

interface TutorProfile extends TutorProfilePayload {
  id: string
  userId: string
  name: string
  email: string
  rating: number
  skills?: Skill[]
  createdAt?: string
}

export type {
  ApiResponse,
  User,
  AuthResponse,
  Skill,
  TutorSummary,
  TutorProfile,
  PageData,
  TutorSearchParams,
  TutorProfilePayload,
}