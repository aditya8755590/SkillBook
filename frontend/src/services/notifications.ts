import api from './api'
import type { ApiResponse, AppNotification } from '../types'

export async function listNotifications(): Promise<AppNotification[]> {
  const { data } = await api.get<ApiResponse<AppNotification[]>>('/notifications')
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to load notifications')
  }
  return data.data
}

export async function getUnreadCount(): Promise<number> {
  const { data } = await api.get<ApiResponse<{ count: number }>>(
    '/notifications/unread-count',
  )
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to load unread count')
  }
  return data.data.count
}

export async function markNotificationRead(id: string): Promise<void> {
  const { data } = await api.post<ApiResponse<AppNotification>>(
    `/notifications/${id}/read`,
  )
  if (!data.success) {
    throw new Error(data.error ?? 'Failed to mark notification as read')
  }
}

export async function markAllNotificationsRead(): Promise<void> {
  const { data } = await api.post<ApiResponse<{ count: number }>>(
    '/notifications/read-all',
  )
  if (!data.success) {
    throw new Error(data.error ?? 'Failed to mark notifications as read')
  }
}
