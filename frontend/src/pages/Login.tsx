import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Alert } from '../components/ui/Alert'

function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [error, setError] = useState<{ email?: string; password?: string }>({})

  const validate = () => {
    const next: { email?: string; password?: string } = {}
    if (!email.trim()) {
      next.email = 'Email is required'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      next.email = 'Enter a valid email address'
    }
    if (!password) {
      next.password = 'Password is required'
    }
    setError(next)
    return Object.keys(next).length === 0
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setFormError('')
    if (!validate()) return

    setSubmitting(true)
    // Phase 3 will replace this with POST /api/auth/login
    await new Promise((resolve) => setTimeout(resolve, 800))
    setSubmitting(false)
    setFormError(
      'Demo only — authentication is coming in Phase 3. Try again later.',
    )
  }

  return (
    <AuthLayout>
      <div className="mb-6">
        <h1 className="m-0 text-2xl font-bold text-text">SkillBook</h1>
        <p className="m-0 mt-1 text-sm text-text-secondary">
          Sign in to continue
        </p>
      </div>

      {formError && (
        <Alert type="error" onDismiss={() => setFormError('')}>
          {formError}
        </Alert>
      )}

      <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-4">
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
          autoComplete="current-password"
          placeholder="••••••••"
          error={error.password}
          required
        />

        <Button type="submit" loading={submitting} className="w-full">
          {submitting ? 'Signing in…' : 'Sign In'}
        </Button>

        <p className="m-0 text-center text-sm text-text-secondary">
          No account?{' '}
          <Link
            to="/register"
            className="font-semibold text-primary underline-offset-2 hover:underline"
          >
            Register
          </Link>
        </p>
      </form>
    </AuthLayout>
  )
}

export default Login