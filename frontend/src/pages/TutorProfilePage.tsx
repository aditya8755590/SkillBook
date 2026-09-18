import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AppShell } from '../components/AppShell'
import { Alert } from '../components/ui/Alert'
import { Spinner } from '../components/ui/Spinner'
import { Avatar } from '../components/ui/Avatar'
import { Button } from '../components/ui/Button'
import { fetchTutorProfile } from '../services/tutors'
import { fetchAvailability } from '../services/availability'
import { createBooking } from '../services/bookings'
import { getStoredUser } from '../services/auth'
import type { AvailabilitySlot, TutorProfile } from '../types'

function TutorProfilePage() {
  const { userId } = useParams<{ userId: string }>()
  const navigate = useNavigate()
  const user = getStoredUser()
  const [profile, setProfile] = useState<TutorProfile | null>(null)
  const [slots, setSlots] = useState<AvailabilitySlot[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingSlots, setLoadingSlots] = useState(false)
  const [bookingSlotId, setBookingSlotId] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [bookingError, setBookingError] = useState('')
  const [success, setSuccess] = useState('')

  useEffect(() => {
    if (!userId) {
      setError('Missing tutor identifier')
      setLoading(false)
      return
    }
    setLoading(true)
    setError('')
    fetchTutorProfile(userId)
      .then(setProfile)
      .catch((err) => {
        setError(
          err instanceof Error ? err.message : 'Failed to load tutor profile',
        )
      })
      .finally(() => setLoading(false))
  }, [userId])

  const isStudentViewer = user?.role === 'STUDENT'

  useEffect(() => {
    if (!userId || !isStudentViewer) return
    setLoadingSlots(true)
    const from = new Date().toISOString()
    const to = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString()
    fetchAvailability(userId, from, to)
      .then(setSlots)
      .catch(() => setSlots([]))
      .finally(() => setLoadingSlots(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId, isStudentViewer])

  const handleBook = async (slot: AvailabilitySlot) => {
    setBookingSlotId(slot.id)
    setBookingError('')
    setSuccess('')
    try {
      await createBooking({ slotId: slot.id })
      setSuccess(`Booked with ${profile?.name ?? 'tutor'} — confirmed.`)
      setTimeout(() => navigate(`/bookings`), 800)
    } catch (err) {
      const status = (err as { response?: { status?: number } }).response?.status
      setBookingError(
        status === 409
          ? 'This slot was just booked — pick another one.'
          : err instanceof Error
            ? err.message
            : 'Booking failed. Please try again.',
      )
    } finally {
      setBookingSlotId(null)
    }
  }

  const availableSlots = slots.filter((s) => s.status === 'AVAILABLE')

  return (
    <AppShell>
      <Link
        to="/discover"
        className="mb-4 inline-flex items-center gap-1 text-sm font-medium text-primary underline-offset-2 hover:underline"
      >
        ← Back to all tutors
      </Link>

      {loading ? (
        <div className="flex justify-center py-20 text-text-secondary">
          <Spinner size="lg" />
        </div>
      ) : error ? (
        <Alert type="error" onDismiss={() => setError('')}>
          {error}
        </Alert>
      ) : profile ? (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div className="lg:col-span-1">
            <div className="rounded-xl border border-border bg-surface p-6 shadow-sm">
              <div className="flex flex-col items-center text-center">
                <Avatar name={profile.name} size="lg" />
                <h1 className="mt-4 text-xl font-bold text-text">
                  {profile.name}
                </h1>
                <p className="text-sm text-text-secondary">
                  {profile.experienceYears} yr
                  {profile.experienceYears === 1 ? '' : 's'} experience
                </p>
                <div className="mt-3 flex items-center gap-3">
                  <span className="rounded-full bg-surface-input px-3 py-1 text-sm font-semibold text-text">
                    ★ {profile.rating.toFixed(1)}
                  </span>
                </div>
                <p className="mt-4 text-2xl font-bold text-primary">
                  ${profile.hourlyRate}
                  <span className="text-sm font-normal text-text-secondary">
                    {' '}
                    /hr
                  </span>
                </p>
              </div>
            </div>
          </div>

          <div className="lg:col-span-2">
            <div className="rounded-xl border border-border bg-surface p-6 shadow-sm">
              <h2 className="m-0 text-lg font-semibold text-text">About</h2>
              {profile.bio ? (
                <p className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-text-secondary">
                  {profile.bio}
                </p>
              ) : (
                <p className="mt-3 text-sm text-text-secondary/70">
                  This tutor hasn't added a bio yet.
                </p>
              )}

              <h2 className="mt-8 text-lg font-semibold text-text">Subjects</h2>
              {profile.skills && profile.skills.length > 0 ? (
                <ul className="mt-3 flex flex-wrap gap-2">
                  {profile.skills.map((skill) => (
                    <li
                      key={skill.id}
                      className="rounded-full border border-border-input bg-surface-input px-3 py-1 text-sm font-medium text-text-secondary"
                    >
                      {skill.name}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-3 text-sm text-text-secondary/70">
                  No subjects listed yet.
                </p>
              )}

              {isStudentViewer && (
                <>
                  <h2 className="mt-8 text-lg font-semibold text-text">
                    Available times
                  </h2>
                  {success && <Alert type="success">{success}</Alert>}
                  {bookingError && (
                    <Alert type="error">{bookingError}</Alert>
                  )}
                  {loadingSlots ? (
                    <div className="flex justify-center py-10 text-text-secondary">
                      <Spinner />
                    </div>
                  ) : availableSlots.length === 0 ? (
                    <p className="mt-3 text-sm text-text-secondary/70">
                      No upcoming availability right now.
                    </p>
                  ) : (
                    <ul className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
                      {availableSlots.map((slot) => (
                        <li
                          key={slot.id}
                          className="flex items-center justify-between gap-3 rounded-lg border border-border-input bg-surface-input px-4 py-3"
                        >
                          <div>
                            <p className="text-sm font-semibold text-text">
                              {new Date(slot.startAt).toLocaleDateString([], {
                                weekday: 'short',
                                month: 'short',
                                day: 'numeric',
                              })}
                            </p>
                            <p className="text-sm text-text-secondary">
                              {new Date(slot.startAt).toLocaleTimeString([], {
                                hour: 'numeric',
                                minute: '2-digit',
                              })}
                              {' – '}
                              {new Date(slot.endAt).toLocaleTimeString([], {
                                hour: 'numeric',
                                minute: '2-digit',
                              })}
                            </p>
                          </div>
                          <Button
                            loading={bookingSlotId === slot.id}
                            onClick={() => handleBook(slot)}
                          >
                            Book
                          </Button>
                        </li>
                      ))}
                    </ul>
                  )}
                </>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </AppShell>
  )
}

export default TutorProfilePage