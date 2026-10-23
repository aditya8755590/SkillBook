import { useEffect, useState } from 'react'
import axios from 'axios'
import { AppShell } from '../components/AppShell'
import { Button } from '../components/ui/Button'
import { Alert } from '../components/ui/Alert'
import { Spinner } from '../components/ui/Spinner'
import { getStoredUser } from '../services/auth'
import {
  deleteOneOffSlot,
  fetchAvailability,
  fetchRules,
  generateSlots,
  replaceRules,
  dayName,
  formatRuleTime,
} from '../services/availability'
import type {
  AvailabilityRuleRequest,
  AvailabilitySlot,
} from '../types'

const WEEKDAYS = [
  { value: 1, label: 'Monday' },
  { value: 2, label: 'Tuesday' },
  { value: 3, label: 'Wednesday' },
  { value: 4, label: 'Thursday' },
  { value: 5, label: 'Friday' },
  { value: 6, label: 'Saturday' },
  { value: 7, label: 'Sunday' },
]

function toNextSevenStart(): string {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function TutorAvailabilityPage() {
  const user = getStoredUser()
  const userId = user?.id ?? ''

  const [rules, setRules] = useState<AvailabilityRuleRequest[]>([])
  const [loaded, setLoaded] = useState(false)
  const [savingRules, setSavingRules] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [slots, setSlots] = useState<AvailabilitySlot[]>([])
  const [loadingSlots, setLoadingSlots] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  useEffect(() => {
    fetchRules()
      .then((existing) =>
        setRules(
          existing.map((r) => ({
            dayOfWeek: r.dayOfWeek,
            startTime: r.startTime,
            endTime: r.endTime,
            timezone: r.timezone,
          })),
        ),
      )
      .catch((err) => {
        if (!axios.isAxiosError(err) || err.response?.status !== 404) {
          setError('Could not load your availability. Please try again.')
        }
      })
      .finally(() => setLoaded(true))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!userId) return
    setLoadingSlots(true)
    const from = new Date().toISOString()
    const to = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString()
    fetchAvailability(userId, from, to)
      .then(setSlots)
      .catch((err) => {
        if (!axios.isAxiosError(err) || err.response?.status !== 404) {
          setError('Could not load your sold slots.')
        }
      })
      .finally(() => setLoadingSlots(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId])

  const updateRule = (index: number, patch: Partial<AvailabilityRuleRequest>) => {
    setRules((prev) =>
      prev.map((rule, i) => (i === index ? { ...rule, ...patch } : rule)),
    )
  }

  const addRule = () => {
    setRules((prev) => [
      ...prev,
      { dayOfWeek: 1, startTime: '09:00', endTime: '10:00', timezone: 'UTC' },
    ])
  }

  const removeRule = (index: number) => {
    setRules((prev) => prev.filter((_, i) => i !== index))
  }

  const handleSaveRules = async () => {
    setError('')
    setSuccess('')
    setSavingRules(true)
    try {
      const invalid = rules.find(
        (r) => !r.startTime || !r.endTime || r.endTime <= r.startTime,
      )
      if (invalid) {
        throw new Error('Each rule needs a valid start and end time')
      }
      const seen = new Set<string>()
      const duplicate = rules.find((r) => {
        const key = `${r.dayOfWeek}:${r.startTime}`
        if (seen.has(key)) return true
        seen.add(key)
        return false
      })
      if (duplicate) {
        throw new Error(
          `Duplicate rule on ${dayName(duplicate.dayOfWeek)} at ${formatRuleTime(
            duplicate.startTime,
          )} — pick a different start time`,
        )
      }
      await replaceRules(rules)
      setSuccess('Weekly availability saved.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save rules')
    } finally {
      setSavingRules(false)
    }
  }

  const handleGenerate = async () => {
    setError('')
    setSuccess('')
    setGenerating(true)
    try {
      const result = await generateSlots(toNextSevenStart(), toNextSevenStart())
      setSuccess(
        `Created ${result.generated} slot${result.generated === 1 ? '' : 's'} for the next week.`,
      )
      refreshSlots()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to generate slots')
    } finally {
      setGenerating(false)
    }
  }

  const refreshSlots = () => {
    if (!userId) return
    const from = new Date().toISOString()
    const to = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString()
    fetchAvailability(userId, from, to).then(setSlots).catch(() => undefined)
  }

  const handleDeleteSlot = async (slot: AvailabilitySlot) => {
    try {
      await deleteOneOffSlot(slot.id)
      refreshSlots()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete slot')
    }
  }

  if (!user || user.role !== 'TUTOR') {
    return (
      <AppShell>
        <div className="mx-auto max-w-xl">
          <Alert type="error">You need a tutor account to manage availability.</Alert>
        </div>
      </AppShell>
    )
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-4xl">
        <div className="mb-6">
          <h1 className="m-0 text-2xl font-bold text-text">My availability</h1>
          <p className="mt-1 text-sm text-text-secondary">
            Set your recurring weekly hours, then generate concrete bookable
            slots for the coming week.
          </p>
        </div>

        {error && (
          <Alert type="error" onDismiss={() => setError('')}>
            {error}
          </Alert>
        )}
        {success && (
          <Alert type="success" onDismiss={() => setSuccess('')}>
            {success}
          </Alert>
        )}

        <div className="mb-6 rounded-xl border border-border bg-surface p-6 shadow-sm">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="m-0 text-lg font-semibold text-text">
              Weekly rules
            </h2>
            <Button variant="secondary" onClick={addRule}>
              Add rule
            </Button>
          </div>

          {!loaded ? (
            <div className="flex justify-center py-8 text-text-secondary">
              <Spinner size="lg" />
            </div>
          ) : rules.length === 0 ? (
            <p className="m-0 py-4 text-sm text-text-secondary">
              No weekly rules yet. Add one to start publishing your hours.
            </p>
          ) : (
            <div className="space-y-3">
              {rules.map((rule, index) => (
                <div
                  key={index}
                  className="flex flex-col gap-3 rounded-lg border border-border-input bg-surface-input/60 p-3 sm:flex-row sm:items-center"
                >
                  <label className="flex items-center gap-2 text-sm text-text">
                    <span className="sr-only">Day of week</span>
                    <select
                      value={rule.dayOfWeek}
                      onChange={(e) =>
                        updateRule(index, {
                          dayOfWeek: Number(e.target.value),
                        })
                      }
                      className="rounded-lg border border-border-input bg-surface px-2 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-primary-ring"
                    >
                      {WEEKDAYS.map((d) => (
                        <option key={d.value} value={d.value}>
                          {d.label}
                        </option>
                      ))}
                    </select>
                  </label>

                  <div className="flex items-center gap-2 text-sm text-text">
                    <input
                      type="time"
                      value={rule.startTime}
                      onChange={(e) =>
                        updateRule(index, { startTime: e.target.value })
                      }
                      aria-label="Start time"
                      className="rounded-lg border border-border-input bg-surface px-2 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-primary-ring"
                    />
                    <span className="text-text-secondary">to</span>
                    <input
                      type="time"
                      value={rule.endTime}
                      onChange={(e) =>
                        updateRule(index, { endTime: e.target.value })
                      }
                      aria-label="End time"
                      className="rounded-lg border border-border-input bg-surface px-2 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-primary-ring"
                    />
                    <span className="text-text-secondary">({rule.timezone})</span>
                  </div>

                  <button
                    type="button"
                    onClick={() => removeRule(index)}
                    className="self-end rounded-lg px-3 py-2 text-sm font-medium text-error hover:bg-error/10 sm:self-auto"
                    aria-label={`Remove ${dayName(rule.dayOfWeek)} rule`}
                  >
                    Remove
                  </button>
                </div>
              ))}
            </div>
          )}

          <div className="mt-4 flex flex-col gap-3 sm:flex-row">
            <Button
              onClick={handleSaveRules}
              loading={savingRules}
              disabled={!loaded}
            >
              Save rules
            </Button>
            <Button
              variant="secondary"
              onClick={handleGenerate}
              loading={generating}
              disabled={!loaded || rules.length === 0}
            >
              Generate next 7 days
            </Button>
          </div>
        </div>

        <div className="rounded-xl border border-border bg-surface p-6 shadow-sm">
          <h2 className="m-0 mb-4 text-lg font-semibold text-text">
            Bookable slots
          </h2>
          {loadingSlots ? (
            <div className="flex justify-center py-8 text-text-secondary">
              <Spinner size="lg" />
            </div>
          ) : slots.length === 0 ? (
            <p className="m-0 py-4 text-sm text-text-secondary">
              No bookable slots in the next 90 days. Save your rules and
              generate slots to publish them.
            </p>
          ) : (
            <ul className="m-0 list-none space-y-2 p-0">
              {slots.map((slot) => (
                <li
                  key={slot.id}
                  className="flex items-center justify-between gap-2 rounded-lg border border-border-input bg-surface-input/60 px-4 py-3 text-sm"
                >
                  <div className="text-text">
                    <span className="font-medium">
                      {new Date(slot.startAt).toLocaleDateString()}
                    </span>{' '}
                    {formatRuleTime(slot.startAt.slice(11, 16))} –{' '}
                    {formatRuleTime(slot.endAt.slice(11, 16))}
                    <span className="ml-2 rounded bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                      {slot.source === 'RULE' ? 'Weekly' : 'One-off'}
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleDeleteSlot(slot)}
                    className="rounded-lg px-3 py-2 text-sm font-medium text-error hover:bg-error/10"
                    aria-label={`Delete slot on ${new Date(
                      slot.startAt,
                    ).toLocaleDateString()}`}
                  >
                    Delete
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </AppShell>
  )
}

export default TutorAvailabilityPage