import api from './api'
import type {
  ApiResponse,
  AvailabilityRule,
  AvailabilityRuleRequest,
  AvailabilitySlot,
  SlotGenerationResult,
} from '../types'

const DAY_NAMES = [
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
  'Sunday',
]

export function dayName(dayOfWeek: number): string {
  return DAY_NAMES[dayOfWeek - 1] ?? `Day ${dayOfWeek}`
}

export async function fetchRules(): Promise<AvailabilityRule[]> {
  const { data } = await api.get<ApiResponse<AvailabilityRule[]>>(
    '/tutors/availability/rules',
  )
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to load availability rules')
  }
  return data.data
}

export async function replaceRules(
  rules: AvailabilityRuleRequest[],
): Promise<AvailabilityRule[]> {
  const { data } = await api.put<ApiResponse<AvailabilityRule[]>>(
    '/tutors/availability/rules',
    { rules },
  )
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to save availability rules')
  }
  return data.data
}

export async function addOneOffSlot(
  startAt: string,
  endAt: string,
): Promise<AvailabilitySlot> {
  const { data } = await api.post<ApiResponse<AvailabilitySlot>>(
    '/tutors/availability/slots',
    { startAt, endAt },
  )
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to add slot')
  }
  return data.data
}

export async function deleteOneOffSlot(slotId: string): Promise<void> {
  const { data } = await api.delete<ApiResponse<null>>(
    `/tutors/availability/slots/${slotId}`,
  )
  if (!data.success) {
    throw new Error(data.error ?? 'Failed to delete slot')
  }
}

export async function generateSlots(
  startDate: string,
  endDate: string,
): Promise<SlotGenerationResult> {
  const { data } = await api.post<ApiResponse<SlotGenerationResult>>(
    '/tutors/availability/generate',
    { startDate, endDate },
  )
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to generate slots')
  }
  return data.data
}

export async function fetchAvailability(
  userId: string,
  from: string,
  to: string,
): Promise<AvailabilitySlot[]> {
  const { data } = await api.get<ApiResponse<AvailabilitySlot[]>>(
    `/tutors/${userId}/availability`,
    { params: { from, to } },
  )
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to load availability')
  }
  return data.data
}

export function formatRuleTime(time: string): string {
  const [hours, minutes] = time.split(':')
  const date = new Date()
  date.setHours(Number(hours), Number(minutes), 0)
  return date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}