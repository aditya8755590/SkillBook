import api from './api'
import type { ApiResponse, AuthResponse, User } from '../types'

const TOKEN_KEY = 'accessToken'
const USER_KEY = 'currentUser'

interface ApiError {
  error?: string
  message?: string
}

export interface RegisterPayload {
  name: string
  email: string
  password: string
  role: 'STUDENT' | 'TUTOR'
}

export interface LoginPayload {
  email: string
  password: string
}

function storeSession(data: AuthResponse): void {
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(data.user))
}

export function getStoredUser(): User | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as User
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (typeof error === 'object' && error !== null) {
    const apiError = error as ApiError
    if (apiError.error) return apiError.error
    if (apiError.message) return apiError.message
  }
  return fallback
}

async function authRequest(
  proceed: () => Promise<{ data: ApiResponse<AuthResponse> }>,
): Promise<User> {
  try {
    const { data } = await proceed()
    if (!data.success || !data.data) {
      throw new Error(data.error ?? 'Something went wrong')
    }
    storeSession(data.data)
    return data.data.user
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Something went wrong'))
  }
}

export async function register(payload: RegisterPayload): Promise<User> {
  return authRequest(() => api.post('/auth/register', payload))
}

export async function login(payload: LoginPayload): Promise<User> {
  return authRequest(() => api.post('/auth/login', payload))
}

export async function fetchMe(): Promise<User> {
  try {
    const { data } = await api.get<ApiResponse<User>>('/auth/me')
    if (!data.success || !data.data) {
      throw new Error(data.error ?? 'Session expired')
    }
    return data.data
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Session expired'))
  }
}