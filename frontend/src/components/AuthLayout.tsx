import type { ReactNode } from 'react'
import { DarkModeToggle } from './ui/DarkModeToggle'

function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-surface-secondary px-4 py-8">
      <div className="absolute top-4 right-4">
        <DarkModeToggle />
      </div>
      <div className="w-full max-w-md rounded-xl border border-border bg-surface p-8 shadow-sm">
        {children}
      </div>
    </div>
  )
}

export default AuthLayout
export { AuthLayout }