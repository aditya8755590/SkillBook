import type { ReactNode } from 'react'
import { DarkModeToggle } from './ui/DarkModeToggle'

function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen bg-surface-secondary">
      <div className="relative hidden w-1/2 overflow-hidden bg-primary lg:block">
        <div className="absolute inset-0 bg-gradient-to-br from-primary to-indigo-900" />
        <div className="relative z-10 flex h-full flex-col justify-between p-12">
          <div className="flex items-center gap-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/15 font-bold text-sm text-white">
              SB
            </div>
            <span className="text-xl font-bold text-white">SkillBook</span>
          </div>

          <div>
            <h2 className="m-0 max-w-md text-3xl font-bold leading-tight text-white">
              Learn anything. From tutors who actually teach it.
            </h2>
            <ul className="mt-8 space-y-3">
              {[
                'Discover tutors by subject, price, and rating',
                'Book and pay for sessions in minutes',
                'Meet over live video with built-in chat',
              ].map((feature) => (
                <li
                  key={feature}
                  className="flex items-start gap-3 text-sm text-white/90"
                >
                  <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-white/15 text-xs text-white">
                    ✓
                  </span>
                  {feature}
                </li>
              ))}
            </ul>
          </div>

          <p className="m-0 text-sm text-white/60">
            A tutoring marketplace — built with Spring Boot, PostgreSQL & React.
          </p>
        </div>
      </div>

      <div className="relative flex w-full items-center justify-center px-4 py-8 lg:w-1/2">
        <div className="absolute top-4 right-4">
          <DarkModeToggle />
        </div>
        <div className="w-full max-w-md rounded-xl border border-border bg-surface p-8 shadow-sm">
          {children}
        </div>
      </div>
    </div>
  )
}

export default AuthLayout
export { AuthLayout }