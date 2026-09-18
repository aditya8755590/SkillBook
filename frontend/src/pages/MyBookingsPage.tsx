import { useCallback, useEffect, useState } from 'react'
import { AppShell } from '../components/AppShell'
import { Button } from '../components/ui/Button'
import { Alert } from '../components/ui/Alert'
import { Spinner } from '../components/ui/Spinner'
import { getStoredUser } from '../services/auth'
import { cancelBooking, listMyBookings } from '../services/bookings'
import type { Booking } from '../types'

const STATUS_CLASSES: Record<Booking['status'], string> = {
  CONFIRMED: 'bg-success-bg text-success',
  CANCELLED: 'bg-surface-input text-text-secondary',
  COMPLETED: 'bg-primary/10 text-primary',
}

function canCancel(booking: Booking): boolean {
  return (
    booking.status === 'CONFIRMED' &&
    new Date(booking.slot.startAt).getTime() > Date.now()
  )
}

function formatMoney(value: number, currency: string): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
  }).format(value)
}

function MyBookingsPage() {
  const user = getStoredUser()

  const [bookings, setBookings] = useState<Booking[]>([])
  const [loading, setLoading] = useState(true)
  const [cancellingId, setCancellingId] = useState<string | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    listMyBookings()
      .then(setBookings)
      .catch((err) => {
        setError(
          err instanceof Error ? err.message : 'Failed to load your bookings',
        )
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (!user || user.role !== 'STUDENT') return
    load()
  }, [user, load])

  const handleCancel = async (booking: Booking) => {
    setCancellingId(booking.id)
    setError('')
    try {
      await cancelBooking(booking.id)
      load()
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Failed to cancel this booking',
      )
    } finally {
      setCancellingId(null)
    }
  }

  if (!user || user.role !== 'STUDENT') {
    return (
      <AppShell>
        <div className="mx-auto max-w-xl">
          <Alert type="error">
            You need a student account to view bookings.
          </Alert>
        </div>
      </AppShell>
    )
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-3xl">
        <h1 className="text-xl font-bold text-text">My Bookings</h1>

        {error && <Alert type="error">{error}</Alert>}

        {loading ? (
          <div className="flex justify-center py-20 text-text-secondary">
            <Spinner size="lg" />
          </div>
        ) : bookings.length === 0 ? (
          <div className="mt-4 rounded-xl border border-border bg-surface p-6 text-center text-sm text-text-secondary shadow-sm">
            You have no bookings yet. Find a tutor to book a session.
          </div>
        ) : (
          <ul className="mt-4 space-y-4">
            {bookings.map((booking) => (
              <li
                key={booking.id}
                className="rounded-xl border border-border bg-surface p-5 shadow-sm"
              >
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="flex items-center gap-2">
                      <h2 className="m-0 text-base font-semibold text-text">
                        {booking.tutor.name}
                      </h2>
                      <span
                        className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${STATUS_CLASSES[booking.status]}`}
                      >
                        {booking.status}
                      </span>
                    </div>
                    <p className="mt-1 text-sm text-text-secondary">
                      {new Date(booking.slot.startAt).toLocaleString()}
                      {' – '}
                      {new Date(booking.slot.endAt).toLocaleTimeString([], {
                        hour: 'numeric',
                        minute: '2-digit',
                      })}
                    </p>
                    <p className="mt-1 text-sm font-semibold text-text">
                      {formatMoney(booking.amount, booking.currency)}
                    </p>
                    {booking.message && (
                      <p className="mt-2 text-sm italic text-text-secondary">
                        "{booking.message}"
                      </p>
                    )}
                    {booking.status === 'CANCELLED' &&
                      booking.refundAmount !== null && (
                        <p className="mt-1 text-sm text-text-secondary">
                          Refund:{' '}
                          {formatMoney(booking.refundAmount, booking.currency)}
                          {booking.cancellationFee &&
                            booking.cancellationFee > 0 &&
                            ` (fee ${formatMoney(
                              booking.cancellationFee,
                              booking.currency,
                            )})`}
                        </p>
                      )}
                  </div>
                  {canCancel(booking) && (
                    <Button
                      variant="secondary"
                      loading={cancellingId === booking.id}
                      onClick={() => handleCancel(booking)}
                    >
                      Cancel
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </AppShell>
  )
}

export default MyBookingsPage