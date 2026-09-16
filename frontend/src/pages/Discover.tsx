import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AppShell } from '../components/AppShell'
import { Avatar } from '../components/ui/Avatar'
import { Input } from '../components/ui/Input'
import { Button } from '../components/ui/Button'
import { Alert } from '../components/ui/Alert'
import { Spinner } from '../components/ui/Spinner'
import { searchTutors, fetchSkills } from '../services/tutors'
import type { PageData, Skill, TutorSummary } from '../types'

const PAGE_SIZE = 6
const SORT_OPTIONS = [
  { value: 'rating_desc', label: 'Highest rated' },
  { value: 'rating_asc', label: 'Lowest rated' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
  { value: 'name_asc', label: 'Name A–Z' },
]

const DEBOUNCE_MS = 400

function TutorCard({ tutor }: { tutor: TutorSummary }) {
  const navigate = useNavigate()

  return (
    <article
      role="link"
      tabIndex={0}
      aria-label={`View ${tutor.name}'s profile`}
      onClick={() => navigate(`/tutors/${tutor.userId}`)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          navigate(`/tutors/${tutor.userId}`)
        }
      }}
      className="flex cursor-pointer flex-col gap-3 rounded-xl border border-border bg-surface p-5 shadow-sm transition-all duration-150 hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-ring"
    >
      <div className="flex items-center gap-3">
        <Avatar name={tutor.name} />
        <div className="min-w-0">
          <h3 className="truncate text-base font-semibold text-text">
            {tutor.name}
          </h3>
          <p className="text-sm text-text-secondary">
            {tutor.experienceYears} yr{tutor.experienceYears === 1 ? '' : 's'} exp
            {' · '}
            <span className="font-medium text-text">${tutor.hourlyRate}/hr</span>
          </p>
        </div>
        <span
          className="ml-auto rounded-full bg-surface-input px-2.5 py-1 text-xs font-semibold text-text-secondary"
          title={`Rating ${tutor.rating}`}
        >
          ★ {tutor.rating.toFixed(1)}
        </span>
      </div>

      {tutor.bio && (
        <p className="line-clamp-2 text-sm text-text-secondary">{tutor.bio}</p>
      )}

      {tutor.skills.length > 0 && (
        <ul className="flex flex-wrap gap-1.5">
          {tutor.skills.map((skill) => (
            <li
              key={skill.id}
              className="rounded-full border border-border-input bg-surface-input px-2.5 py-0.5 text-xs font-medium text-text-secondary"
            >
              {skill.name}
            </li>
          ))}
        </ul>
      )}
    </article>
  )
}

interface Filters {
  q: string
  skill: string
  maxRate: string
  sort: string
}

const INITIAL_FILTERS: Filters = {
  q: '',
  skill: '',
  maxRate: '',
  sort: 'rating_desc',
}

function Discover() {
  const [skills, setSkills] = useState<Skill[]>([])
  const [qInput, setQInput] = useState('')
  const [maxRateInput, setMaxRateInput] = useState('')
  const [filters, setFilters] = useState<Filters>(INITIAL_FILTERS)
  const [page, setPage] = useState(0)
  const [pageData, setPageData] = useState<PageData<TutorSummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const seqRef = useRef(0)

  useEffect(() => {
    fetchSkills()
      .then(setSkills)
      .catch(() => {
        // skills list is optional for browsing; search still works
      })
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setFilters((prev) => (prev.q === qInput ? prev : { ...prev, q: qInput }))
    }, DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [qInput])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setFilters((prev) =>
        prev.maxRate === maxRateInput ? prev : { ...prev, maxRate: maxRateInput },
      )
    }, DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [maxRateInput])

  useEffect(() => {
    const seq = ++seqRef.current
    setLoading(true)
    setError('')
    searchTutors({
      q: filters.q || undefined,
      skill: filters.skill || undefined,
      maxRate: filters.maxRate ? Number(filters.maxRate) : undefined,
      sort: filters.sort,
      page,
      size: PAGE_SIZE,
    })
      .then((data) => {
        if (seq === seqRef.current) setPageData(data)
      })
      .catch((err) => {
        if (seq === seqRef.current) {
          setError(err instanceof Error ? err.message : 'Failed to load tutors')
        }
      })
      .finally(() => {
        if (seq === seqRef.current) setLoading(false)
      })
  }, [filters, page])

  const commitFilter = (patch: Partial<Filters>) => {
    setFilters((prev) => ({ ...prev, ...patch }))
    setPage(0)
  }

  const handleReset = () => {
    setQInput('')
    setMaxRateInput('')
    setFilters(INITIAL_FILTERS)
    setPage(0)
  }

  const totalElements = pageData?.totalElements ?? 0
  const totalPages = pageData?.totalPages ?? 1
  const currentPage = pageData?.number ?? 0

  return (
    <AppShell>
      <div className="mb-6">
        <h1 className="m-0 text-2xl font-bold text-text">Find a Tutor</h1>
        <p className="m-0 mt-1 text-sm text-text-secondary">
          Search tutors by subject, skill, or keyword
        </p>
      </div>

      {error && (
        <Alert type="error" onDismiss={() => setError('')}>
          {error}
        </Alert>
      )}

      <div className="rounded-xl border border-border bg-surface p-4 shadow-sm">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <div className="lg:col-span-2">
            <Input
              label="Search"
              type="search"
              value={qInput}
              onChange={(e) => setQInput(e.target.value)}
              placeholder="Name, bio, or subject…"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-text" htmlFor="skill-filter">
              Subject
            </label>
            <select
              id="skill-filter"
              value={filters.skill}
              onChange={(e) => commitFilter({ skill: e.target.value })}
              className="w-full rounded-lg border border-border-input bg-surface-input px-3 py-2 text-sm text-text focus:ring-2 focus:ring-primary-ring"
            >
              <option value="">All subjects</option>
              {skills.map((skill) => (
                <option key={skill.id} value={skill.name}>
                  {skill.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <Input
              label="Max rate ($/hr)"
              type="number"
              min="0"
              value={maxRateInput}
              onChange={(e) => setMaxRateInput(e.target.value)}
              placeholder="Any"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-text" htmlFor="sort">
              Sort
            </label>
            <select
              id="sort"
              value={filters.sort}
              onChange={(e) => commitFilter({ sort: e.target.value })}
              className="w-full rounded-lg border border-border-input bg-surface-input px-3 py-2 text-sm text-text focus:ring-2 focus:ring-primary-ring"
            >
              {SORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <div className="mt-6">
        {loading && pageData === null ? (
          <div className="flex justify-center py-16 text-text-secondary">
            <Spinner size="lg" />
          </div>
        ) : totalElements === 0 ? (
          <div className="rounded-xl border border-dashed border-border bg-surface py-16 text-center">
            <p className="m-0 text-sm font-medium text-text-secondary">
              No tutors found
            </p>
            <p className="m-0 mt-1 text-sm text-text-secondary/70">
              Try adjusting your search or filters
            </p>
            {(filters.q || filters.skill || filters.maxRate) && (
              <Button
                type="button"
                variant="ghost"
                className="mt-4"
                onClick={handleReset}
              >
                Clear filters
              </Button>
            )}
          </div>
        ) : (
          <>
            <p className="mb-3 text-sm text-text-secondary">
              {totalElements} tutor{totalElements === 1 ? '' : 's'} found
            </p>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
              {(pageData?.content ?? []).map((tutor) => (
                <TutorCard key={tutor.userId} tutor={tutor} />
              ))}
            </div>

            {totalPages > 1 && (
              <nav
                className="mt-8 flex items-center justify-between border-t border-border pt-4"
                aria-label="Pagination"
              >
                <Button
                  type="button"
                  variant="secondary"
                  disabled={currentPage === 0}
                  onClick={() => setPage(currentPage - 1)}
                >
                  Previous
                </Button>
                <span className="text-sm text-text-secondary">
                  Page {currentPage + 1} of {totalPages}
                </span>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={currentPage >= totalPages - 1}
                  onClick={() => setPage(currentPage + 1)}
                >
                  Next
                </Button>
              </nav>
            )}
          </>
        )}
      </div>
    </AppShell>
  )
}

export default Discover