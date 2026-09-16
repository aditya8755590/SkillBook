import { useEffect, useState } from 'react'
import { isAxiosError } from 'axios'
import { AppShell } from '../components/AppShell'
import { Input } from '../components/ui/Input'
import { Button } from '../components/ui/Button'
import { Alert } from '../components/ui/Alert'
import { Spinner } from '../components/ui/Spinner'
import { upsertProfile, replaceSkills, fetchOwnProfile } from '../services/tutors'

function TutorSetup() {
  const [bio, setBio] = useState('')
  const [experienceYears, setExperienceYears] = useState('')
  const [hourlyRate, setHourlyRate] = useState('')
  const [skills, setSkills] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  useEffect(() => {
    fetchOwnProfile()
      .then((profile) => {
        setBio(profile.bio ?? '')
        setExperienceYears(profile.experienceYears?.toString() ?? '')
        setHourlyRate(profile.hourlyRate?.toString() ?? '')
      })
      .catch((err) => {
        if (!isAxiosError(err) || err.response?.status !== 404) {
          setError('Could not load your profile. Please try again.')
        }
      })
      .finally(() => setLoading(false))
  }, [])

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    setSuccess('')

    const years = experienceYears === '' ? undefined : Number(experienceYears)
    const rate = hourlyRate === '' ? undefined : Number(hourlyRate)

    if (years !== undefined && years < 0) {
      setError('Experience years must be 0 or more')
      setSaving(false)
      return
    }
    if (rate !== undefined && rate < 0) {
      setError('Hourly rate must be 0 or more')
      setSaving(false)
      return
    }

    const skillNames = skills
      .split(',')
      .map((name) => name.trim())
      .filter(Boolean)

    try {
      await upsertProfile({ bio, experienceYears: years, hourlyRate: rate })
      if (skillNames.length > 0) {
        await replaceSkills(skillNames)
      }
      setSuccess(
        'Profile saved! You can now be discovered by students on the Find a Tutor page.',
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save profile')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <AppShell>
        <div className="flex justify-center py-16 text-text-secondary">
          <Spinner size="lg" />
        </div>
      </AppShell>
    )
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-xl">
        <div className="mb-6">
          <h1 className="m-0 text-2xl font-bold text-text">
            Set up your tutor profile
          </h1>
          <p className="mt-1 text-sm text-text-secondary">
            Tell students what you teach and what you charge. You can update
            this any time.
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

        <form
          onSubmit={handleSubmit}
          className="rounded-xl border border-border bg-surface p-6 shadow-sm"
        >
          <div className="mb-4">
            <label
              className="mb-1 block text-sm font-medium text-text"
              htmlFor="bio"
            >
              Bio
            </label>
            <textarea
              id="bio"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              rows={4}
              maxLength={2000}
              placeholder="e.g. I'm a 5-year Math tutor for high school students…"
              className="w-full rounded-lg border border-border-input bg-surface-input px-3 py-2 text-sm text-text placeholder:text-text-secondary/70 focus:outline-none focus:ring-2 focus:ring-primary-ring"
            />
          </div>

          <div className="mb-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input
              label="Experience (years)"
              type="number"
              min={0}
              value={experienceYears}
              onChange={(e) => setExperienceYears(e.target.value)}
              placeholder="0"
            />
            <Input
              label="Hourly rate ($)"
              type="number"
              min={0}
              step="0.01"
              value={hourlyRate}
              onChange={(e) => setHourlyRate(e.target.value)}
              placeholder="25"
            />
          </div>

          <div className="mb-4">
            <Input
              label="Skills"
              type="text"
              value={skills}
              onChange={(e) => setSkills(e.target.value)}
              placeholder="e.g. Algebra, Calculus, SAT Math"
              hint="Comma-separated subjects/skills you can teach"
            />
          </div>

          <Button type="submit" loading={saving} className="w-full sm:w-auto">
            Save profile
          </Button>
        </form>
      </div>
    </AppShell>
  )
}

export default TutorSetup