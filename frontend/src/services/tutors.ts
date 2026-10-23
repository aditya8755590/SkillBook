import api from './api'
import type {
  ApiResponse,
  PageData,
  Skill,
  TutorProfile,
  TutorProfilePayload,
  TutorSearchParams,
  TutorSummary,
} from '../types'

export async function searchTutors(
  params: TutorSearchParams = {},
): Promise<PageData<TutorSummary>> {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value))
    }
  }

  const { data } = await api.get<ApiResponse<PageData<TutorSummary>>>(
    `/tutors${query.toString() ? `?${query.toString()}` : ''}`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to load tutors')
  }
  return data.data
}

export async function fetchSkills(): Promise<Skill[]> {
  const { data } = await api.get<ApiResponse<Skill[]>>('/skills')
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to load skills')
  }
  return data.data
}

export async function upsertProfile(payload: TutorProfilePayload) {
  const { data } = await api.put<ApiResponse<TutorProfile>>(
    '/tutors/profile',
    payload,
  )
  if (!data.success) {
    throw new Error(data.error ?? 'Failed to save profile')
  }
}

export async function replaceSkills(skillNames: string[]) {
  const { data } = await api.put<ApiResponse<Skill[]>>('/tutors/profile/skills', {
    skills: skillNames,
  })
  if (!data.success) {
    throw new Error(data.error ?? 'Failed to save skills')
  }
}

export async function fetchOwnProfile(): Promise<TutorProfile | null> {
  const { data } = await api.get<ApiResponse<TutorProfile>>('/tutors/profile')
  if (!data.success) {
    throw new Error(data.error ?? 'Failed to load profile')
  }
  return data.data ?? null
}

export async function fetchTutorProfile(userId: string): Promise<TutorProfile> {
  const { data } = await api.get<ApiResponse<TutorProfile>>(`/tutors/${userId}`)
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to load tutor profile')
  }
  return data.data
}