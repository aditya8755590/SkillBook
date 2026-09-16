# Plan: Design System + Polished Auth Pages

## Goal
Add a Tailwind-based design system, then rebuild Login and Register as polished, interactive pages with loading/error/validation states and dark mode support.

## Approach
Tailwind CSS v4 via `@tailwindcss/vite` plugin (CSS-first config, no `tailwind.config.ts` needed). Zero `tailwind.config.js` — tokens go in CSS `@theme` blocks.

---

## Phase 3.1 — Tailwind v4 Setup

**Files changed:** `package.json`, `vite.config.ts`, `src/styles/index.css`

```bash
cd frontend && npm install -D tailwindcss @tailwindcss/vite
```

- Add `@tailwindcss/vite` plugin to `vite.config.ts`
- Replace `index.css` content with Tailwind import + `@theme` token block
- Remove all existing hand-written styles (replaced by Tailwind)

## Phase 3.2 — Design Tokens (via `@theme`)

Define in `src/styles/index.css` inside `@theme`:

| Token | Value | Notes |
|-------|-------|-------|
| `--color-primary` | `#2563eb` | blue-600 |
| `--color-primary-hover` | `#1d4ed8` | blue-700 |
| `--color-surface` | `#ffffff` | light bg |
| `--color-surface-secondary` | `#f9fafb` | gray-50 |
| `--color-border` | `#e5e7eb` | gray-200 |
| `--color-text` | `#111827` | gray-900 |
| `--color-text-secondary` | `#6b7280` | gray-500 |
| `--color-error` | `#dc2626` | red-600 |
| `--color-success` | `#16a34a` | green-600 |
| `--radius` | `0.5rem` | 8px |

Dark mode: `dark:` prefix (Tailwind built-in, class-based via `class` strategy).

## Phase 3.3 — UI Components

All in `src/components/ui/` (new directory):

### `Button.tsx`
- Props: `variant` (`primary` | `secondary` | `ghost`), `loading` boolean, `disabled` boolean, standard `<button>` props
- Loading state: spinner icon + disabled + `aria-busy`
- `focus-visible:ring-2` for keyboard users

### `Input.tsx`
- Props: `label` string, `error` string (optional), standard `<input>` props
- Accessible: `id` auto-generated from label, `<label htmlFor>`, `aria-invalid` + `aria-describedby` when error present
- Error state: red border + error message below

### `Spinner.tsx`
- Pure SVG inline spinner, accepts `size` and `className`
- Uses `animate-spin` from Tailwind

### `Alert.tsx`
- Props: `type` (`error` | `success`), `children`, `onDismiss` callback
- Uses `role="alert"` and `aria-live="assertive"` for screen readers
- Dismissible with X button

## Phase 3.4 — Dark Mode

- Add `darkMode: 'class'` to `@theme` block in CSS
- Root component wraps in `<html class="dark">` toggle (simple `useEffect` reading `prefers-color-scheme`, with manual override button)
- All tokens in CSS get `dark:` overrides (`--color-surface` → dark gray, etc.)
- Persist preference in `localStorage`

## Phase 3.5 — Polished Login Page

**Changes to `Login.tsx`:**

| Current state | New state |
|---------------|-----------|
| `handleSubmit` is empty stub | Mock: 800ms delay → random success/error, then show Alert |
| No loading indicator | Spinner inside button during submit |
| No validation | Inline errors (required fields enforced via HTML5, plus email format check) |
| No dark mode support | Works via Tailwind dark: tokens |
| Plain "Sign In" button | Primary Button component with loading state |

## Phase 3.6 — Polished Register Page

**Changes to `Register.tsx`:**

| Current state | New state |
|---------------|-----------|
| `handleSubmit` is empty stub | Mock: 800ms delay → success/error Alert |
| No password confirmation | "Confirm Password" field with match validation |
| No inline validation | Password strength indicator (length ≥ 8, visual bar) |
| Role selector is plain radios | Pill-style toggle buttons (STUDENT / TUTOR) with active state |
| No loading state | Spinner in button during submit |

## Phase 3.7 — Accessibility Hardening (all pages)

- All interactive elements get `focus-visible:ring-2 ring-offset-2` outlines
- Form inputs all have visible `<label>` elements (no placeholder-only inputs)
- Error messages use `aria-describedby` linked to input `id`
- Submit button uses `aria-busy={loading}` during mock-submit
- Color contrast: all text meets WCAG AA (4.5:1 minimum)

## Phase 3.8 — Dark Mode Toggle Component

- `src/components/ui/DarkModeToggle.tsx`
- Sun/Moon icon button, toggles `class="dark"` on `<html>`
- Reads `prefers-color-scheme` on mount, persists choice in `localStorage`
- Placed top-right on auth pages

---

## Files Modified / Created

| Action | File |
|--------|------|
| **Edit** | `frontend/package.json` |
| **Edit** | `frontend/vite.config.ts` |
| **Replace** | `frontend/src/styles/index.css` |
| **Edit** | `frontend/src/pages/Login.tsx` |
| **Edit** | `frontend/src/pages/Register.tsx` |
| **Create** | `frontend/src/components/ui/Button.tsx` |
| **Create** | `frontend/src/components/ui/Input.tsx` |
| **Create** | `frontend/src/components/ui/Spinner.tsx` |
| **Create** | `frontend/src/components/ui/Alert.tsx` |
| **Create** | `frontend/src/components/ui/DarkModeToggle.tsx` |

## Verification

```bash
cd frontend
npm run build          # tsc -b && vite build must pass with zero errors
npm run dev            # visual: Login + Register pages render, dark mode toggles,
                       # mock-submit shows spinner then alert
```

## Not in scope (intentionally deferred)

- Auth state management (Context/Provider) — Phase 3 backend
- Route guards / protected routes — Phase 3 backend
- Real API calls (still mock-submit) — Phase 3 backend
- Dashboard pages — separate `/frontend` call
- Toast library (keep Alert component simple for now)
