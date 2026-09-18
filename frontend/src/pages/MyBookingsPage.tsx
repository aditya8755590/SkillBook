import { useCallback, useEffect, useState } from 'react'
import { AppShell } from '../components/AppShell'
import { Button } from '../components/ui/Button'
import { Alert } from '../components/ui/Alert'
import { Spinner } from '../components/ui/Spinner'
import { getStoredUser } from '../services/auth'
import {
  cancelBooking,
  createCheckout,
  listMyBookings,
} from '../services/bookings'
import type { Booking, PaymentStatus } from '../types'

const STATUS_CLASSES: Record<Booking['status'], string> = {
  CONFIRMED: 'bg-success-bg text-success',
  CANCELLED: 'bg-surface-input text-text-secondary',
  COMPLETED: 'bg-primary/10 text-primary',
}

const PAYMENT_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Payment pending',
  PAID: 'Paid',
  FAILED: 'Payment failed',
  REFUNDED: 'Refunded',
  PARTIALLY_REFUNDED: 'Partially refunded',
}

const PAYMENT_CLASSES: Record<PaymentStatus, string> = {
  PENDING: 'text-text-secondary',
  PAID: 'text-success',
  FAILED: 'text-danger',
  REFUNDED: 'text-text-secondary',
  PARTIALLY_REFUNDED: 'text-text-secondary',
}

function canCancel(booking: Booking): boolean {
  return (
    booking.status === 'CONFIRMED' &&
    new Date(booking.slot.startAt).getTime() > Date.now()
  )
}

function canPay(booking: Booking): boolean {
  if (booking.status !== 'CONFIRMED' || booking.amount <= 0) return false
  if (!booking.payment) return true
  return booking.payment.status === 'PENDING' || booking.payment.status === 'FAILED'
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
  const [payingId, setPayingId] = useState<string | null>(null)
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

  const handlePay = async (booking: Booking) => {
    setPayingId(booking.id)
    setError('')
    try {
      const result = await createCheckout(booking.id)
      if (result.checkoutUrl) {
        window.location.assign(result.checkoutUrl)
      } else {
        load()
      }
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Failed to start payment',
      )
    } finally {
      setPayingId(null)
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
                    {booking.payment && (
                      <p
                        className={`text-xs font-medium ${PAYMENT_CLASSES[booking.payment.status]}`}
                      >
                        {PAYMENT_LABELS[booking.payment.status]}
                        {booking.payment.refundAmount !== null &&
                          booking.payment.refundAmount > 0 &&
                          ` – refunded ${formatMoney(
                            booking.payment.refundAmount,
                            booking.payment.currency,
                          )}`}
                      </p>
                    )}
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
                  <div className="flex shrink-0 flex-col items-end gap-2">
                    {canPay(booking) && (
                      <Button
                        variant="primary"
                        loading={payingId === booking.id}
                        onClick={() => handlePay(booking)}
                      >
                        Pay
                      </Button>
                    )}
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