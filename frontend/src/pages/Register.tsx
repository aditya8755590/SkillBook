import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Alert } from '../components/ui/Alert'
import { register, apiErrorMessage } from '../services/auth'

type Role = 'STUDENT' | 'TUTOR'

function Register() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [role, setRole] = useState<Role>('STUDENT')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [error, setError] = useState<{
    name?: string
    email?: string
    password?: string
    confirmPassword?: string
  }>({})

  const validate = () => {
    const next: typeof error = {}
    if (!name.trim()) next.name = 'Full name is required'
    if (!email.trim()) {
      next.email = 'Email is required'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      next.email = 'Enter a valid email address'
    }
    if (!password) {
      next.password = 'Password is required'
    } else if (password.length < 8) {
      next.password = 'Must be at least 8 characters'
    }
    if (!confirmPassword) {
      next.confirmPassword = 'Please confirm your password'
    } else if (confirmPassword !== password) {
      next.confirmPassword = 'Passwords do not match'
    }
    setError(next)
    return Object.keys(next).length === 0
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setFormError('')
    if (!validate()) return

    setSubmitting(true)
    try {
      await register({
        name: name.trim(),
        email: email.trim(),
        password,
        role,
      })
      navigate('/', { replace: true })
    } catch (err) {
      setFormError(
        apiErrorMessage(err, 'Unable to create account. Please try again.'),
      )
    } finally {
      setSubmitting(false)
    }
  }

  const passwordStrength =
    password.length >= 8 ? 100 : Math.min((password.length / 8) * 100, 100)

  return (
    <AuthLayout>
      <div className="mb-6">
        <h1 className="m-0 text-2xl font-bold text-text">Create Account</h1>
        <p className="m-0 mt-1 text-sm text-text-secondary">
          Join SkillBook as a student or tutor
        </p>
      </div>

      {formError && (
        <Alert type="error" onDismiss={() => setFormError('')}>
          {formError}
        </Alert>
      )}

      <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-4">
        <Input
          label="Full Name"
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          autoComplete="name"
          placeholder="Alex Johnson"
          error={error.name}
          required
        />

        <Input
          label="Email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
          placeholder="you@example.com"
          error={error.email}
          required
        />

        <Input
          label="Password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="new-password"
          placeholder="••••••••"
          minLength={8}
          hint="At least 8 characters"
          error={error.password}
          required
        />

        {password && passwordStrength < 100 && (
          <div
            className="h-1.5 w-full overflow-hidden rounded-full bg-surface-input"
            aria-hidden="true"
          >
            <div
              className="h-full rounded-full bg-primary transition-all duration-300"
              style={{ width: `${passwordStrength}%` }}
            />
          </div>
        )}

        <Input
          label="Confirm Password"
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          autoComplete="new-password"
          placeholder="••••••••"
          error={error.confirmPassword}
          required
        />

        <fieldset className="m-0 space-y-2 border-0 p-0">
          <legend className="mb-1 block text-sm font-medium text-text">
            I want to join as
          </legend>
          <div className="grid grid-cols-2 gap-2" role="group" aria-label="Join as">
            {(['STUDENT', 'TUTOR'] as const).map((r) => (
              <button
                key={r}
                type="button"
                aria-pressed={role === r}
                onClick={() => setRole(r)}
                className={`rounded-lg border px-4 py-2.5 text-sm font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-ring ${
                  role === r
                    ? 'border-primary bg-primary text-text-on-primary'
                    : 'border-border-input bg-surface text-text-secondary hover:bg-surface-input'
                }`}
              >
                {r === 'STUDENT' ? 'Student' : 'Tutor'}
              </button>
            ))}
          </div>
        </fieldset>

        <Button type="submit" loading={submitting} className="w-full">
          {submitting ? 'Creating account…' : 'Register'}
        </Button>

        <p className="m-0 text-center text-sm text-text-secondary">
          Already have an account?{' '}
          <Link
            to="/login"
            className="font-semibold text-primary underline-offset-2 hover:underline"
          >
            Sign in
          </Link>
        </p>
      </form>
    </AuthLayout>
  )
}

export default Register