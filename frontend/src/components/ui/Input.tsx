import type { InputHTMLAttributes } from 'react'
import { useId } from 'react'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  hint?: string
}

function Input({ label, error, hint, id, className = '', ...props }: InputProps) {
  const autoId = useId()
  const inputId = id ?? autoId
  const errorId = `${inputId}-error`
  const hintId = `${inputId}-hint`
  const describedBy = [
    error ? errorId : '',
    hint && !error ? hintId : '',
  ].filter(Boolean).join(' ') || undefined

  return (
    <div className="w-full">
      <label
        htmlFor={inputId}
        className="mb-1 block text-sm font-medium text-text"
      >
        {label}
      </label>
      <input
        id={inputId}
        className={`w-full rounded-lg border px-3 py-2 text-sm transition-colors duration-150 bg-surface-input placeholder:text-text-secondary/70 focus:outline-none focus:ring-2 focus:ring-primary-ring ${
          error
            ? 'border-error focus:border-error'
            : 'border-border-input hover:border-border'
        } ${className}`}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        {...props}
      />
      {hint && !error && (
        <p id={hintId} className="mt-1 text-xs text-text-secondary">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="mt-1 text-xs text-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

export default Input
export { Input }