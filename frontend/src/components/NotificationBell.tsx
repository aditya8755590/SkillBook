import { useCallback, useEffect, useRef, useState } from 'react'
import type { AppNotification } from '../types'
import {
  getUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '../services/notifications'

const POLL_INTERVAL_MS = 60_000

function formatTime(createdAt: string | null): string {
  if (!createdAt) return ''
  const parsed = new Date(createdAt)
  if (Number.isNaN(parsed.getTime())) return ''
  return parsed.toLocaleString()
}

function NotificationBell() {
  const [unreadCount, setUnreadCount] = useState(0)
  const [open, setOpen] = useState(false)
  const [notifications, setNotifications] = useState<AppNotification[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const panelRef = useRef<HTMLDivElement | null>(null)

  const refreshCount = useCallback(async () => {
    try {
      setUnreadCount(await getUnreadCount())
      setError(null)
    } catch {
      setError('Could not load notifications')
    }
  }, [])

  useEffect(() => {
    refreshCount()
    const id = window.setInterval(refreshCount, POLL_INTERVAL_MS)
    return () => window.clearInterval(id)
  }, [refreshCount])

  useEffect(() => {
    if (!open) return
    const handleClickOutside = (event: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  const openPanel = async () => {
    setOpen((prev) => !prev)
    setLoading(true)
    setError(null)
    try {
      setNotifications(await listNotifications())
    } catch {
      setError('Could not load notifications')
    } finally {
      setLoading(false)
    }
  }

  const handleMarkRead = async (notification: AppNotification) => {
    if (notification.read) return
    try {
      await markNotificationRead(notification.id)
      setNotifications((prev) =>
        prev.map((item) =>
          item.id === notification.id ? { ...item, read: true } : item,
        ),
      )
      setUnreadCount((prev) => Math.max(0, prev - 1))
    } catch {
      setError('Could not update notification')
    }
  }

  const handleMarkAllRead = async () => {
    try {
      await markAllNotificationsRead()
      setNotifications((prev) => prev.map((item) => ({ ...item, read: true })))
      setUnreadCount(0)
    } catch {
      setError('Could not update notifications')
    }
  }

  return (
    <div className="relative" ref={panelRef}>
      <button
        type="button"
        onClick={openPanel}
        className="relative rounded-lg p-2 text-text-secondary transition-colors hover:bg-surface-input hover:text-text"
        aria-label={
          unreadCount > 0
            ? `Notifications (${unreadCount} unread)`
            : 'Notifications'
        }
        aria-expanded={open}
      >
        <svg className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
          <path d="M10 2a6 6 0 00-6 6v3.586l-.707.707A1 1 0 004 14h12a1 1 0 00.707-1.707L16 11.586V8a6 6 0 00-6-6zM8 16a2 2 0 114 0H8z" />
        </svg>
        {unreadCount > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-bold text-text-on-primary">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-50 mt-2 w-80 rounded-lg border border-border bg-surface shadow-lg">
          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <span className="text-sm font-semibold text-text">
              Notifications
            </span>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={handleMarkAllRead}
                className="text-xs font-medium text-primary hover:underline"
              >
                Mark all as read
              </button>
            )}
          </div>

          <div className="max-h-96 overflow-y-auto">
            {error && (
              <p className="m-0 px-4 py-3 text-sm text-text-secondary">
                {error}
              </p>
            )}
            {loading && (
              <p className="m-0 px-4 py-3 text-sm text-text-secondary">
                Loading…
              </p>
            )}
            {!loading && !error && notifications.length === 0 && (
              <p className="m-0 px-4 py-3 text-sm text-text-secondary">
                No notifications yet
              </p>
            )}
            {!loading &&
              notifications.map((notification) => (
                <button
                  key={notification.id}
                  type="button"
                  onClick={() => handleMarkRead(notification)}
                  className={`block w-full border-b border-border px-4 py-3 text-left transition-colors last:border-b-0 hover:bg-surface-input ${
                    notification.read ? 'opacity-70' : 'bg-primary/5'
                  }`}
                >
                  <span className="block text-sm text-text">
                    {notification.message}
                  </span>
                  <span className="mt-0.5 block text-xs text-text-secondary/70">
                    {formatTime(notification.createdAt)}
                  </span>
                </button>
              ))}
          </div>
        </div>
      )}
    </div>
  )
}

export default NotificationBell
export { NotificationBell }
