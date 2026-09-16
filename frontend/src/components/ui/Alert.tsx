import type { ReactNode } from 'react'

interface AlertProps {
  type: 'error' | 'success'
  children: ReactNode
  onDismiss?: () => void
}

const typeClasses = {
  error: 'bg-error-bg border-error text-error',
  success: 'bg-success-bg border-success text-success',
}

function Alert({ type, children, onDismiss }: AlertProps) {
  return (
    <div
      className={`mt-4 flex items-start justify-between gap-2 rounded-lg border px-4 py-3 text-sm ${typeClasses[type]}`}
      role="alert"
      aria-live="assertive"
    >
      <span>{children}</span>
      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          className="ml-2 text-xs font-semibold underline hover:no-underline"
          aria-label="Dismiss notification"
        >
          Dismiss
        </button>
      )}
    </div>
  )
}

export default Alert
export { Alert }