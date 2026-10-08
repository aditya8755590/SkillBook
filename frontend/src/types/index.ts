interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: string
  timestamp: string
}

interface User {
  id: string
  email: string
  name: string
  role: 'STUDENT' | 'TUTOR' | 'ADMIN'
}

interface AuthResponse {
  token: string
  tokenType: string
  expiresIn: number
  user: User
}

interface Skill {
  id: string
  name: string
  category?: string
}

interface TutorSummary {
  id: string
  userId: string
  name: string
  bio?: string
  experienceYears: number
  hourlyRate: number
  rating: number
  skills: Skill[]
  createdAt?: string
}

interface PageData<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

interface TutorSearchParams {
  q?: string
  skill?: string
  minRate?: number
  maxRate?: number
  sort?: string
  page?: number
  size?: number
}

interface TutorProfilePayload {
  bio?: string
  experienceYears?: number
  hourlyRate?: number
}

interface TutorProfile extends TutorProfilePayload {
  id: string
  userId: string
  name: string
  email: string
  rating: number
  skills?: Skill[]
  createdAt?: string
}

interface AvailabilityRule {
  id: string
  dayOfWeek: number
  startTime: string
  endTime: string
  timezone: string
  active: boolean
}

interface AvailabilityRuleRequest {
  dayOfWeek: number
  startTime: string
  endTime: string
  timezone: string
}

interface AvailabilitySlot {
  id: string
  startAt: string
  endAt: string
  status: 'AVAILABLE' | 'BOOKED'
  source: 'RULE' | 'ONE_OFF'
}

interface SlotGenerationResult {
  generated: number
  skipped: number
  startDate: string
  endDate: string
}

interface BookingSlotInfo {
  id: string
  startAt: string
  endAt: string
}

interface BookingStudentInfo {
  id: string
  name: string
}

interface BookingTutorInfo {
  id: string
  name: string
}

interface Booking {
  id: string
  status: 'CONFIRMED' | 'CANCELLED' | 'COMPLETED'
  message: string | null
  amount: number
  currency: string
  refundAmount: number | null
  cancellationFee: number | null
  cancelledAt: string | null
  completedAt: string | null
  createdAt: string | null
  slot: BookingSlotInfo
  student: BookingStudentInfo
  tutor: BookingTutorInfo
  payment: PaymentInfo | null
}

type PaymentStatus =
  | 'PENDING'
  | 'PAID'
  | 'FAILED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED'

interface PaymentInfo {
  id: string
  status: PaymentStatus
  amount: number
  currency: string
  refundAmount: number | null
}

interface CheckoutResponse {
  checkoutUrl: string | null
  status: PaymentStatus
}

interface BookingCreatePayload {
  slotId: string
  message?: string
}

interface AppNotification {
  id: string
  type: string
  message: string
  read: boolean
  bookingId: string | null
  createdAt: string | null
}

export type {
  ApiResponse,
  User,
  AuthResponse,
  Skill,
  TutorSummary,
  TutorProfile,
  PageData,
  TutorSearchParams,
  TutorProfilePayload,
  AvailabilityRule,
  AvailabilityRuleRequest,
  AvailabilitySlot,
  SlotGenerationResult,
  Booking,
  BookingSlotInfo,
  BookingStudentInfo,
  BookingTutorInfo,
  PaymentStatus,
  PaymentInfo,
  CheckoutResponse,
  BookingCreatePayload,
  AppNotification,
}