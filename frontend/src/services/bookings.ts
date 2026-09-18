import api from './api'
import type {
  ApiResponse,
  Booking,
  BookingCreatePayload,
  CheckoutResponse,
} from '../types'

export async function listMyBookings(): Promise<Booking[]> {
  const { data } = await api.get<ApiResponse<Booking[]>>('/bookings')
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to load bookings')
  }
  return data.data
}

export async function createBooking(
  payload: BookingCreatePayload,
): Promise<Booking> {
  const { data } = await api.post<ApiResponse<Booking>>('/bookings', payload)
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to create booking')
  }
  return data.data
}

export async function cancelBooking(bookingId: string): Promise<Booking> {
  const { data } = await api.post<ApiResponse<Booking>>(
    `/bookings/${bookingId}/cancel`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to cancel booking')
  }
  return data.data
}

export async function createCheckout(bookingId: string): Promise<CheckoutResponse> {
  const { data } = await api.post<ApiResponse<CheckoutResponse>>(
    `/bookings/${bookingId}/checkout`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.error ?? 'Failed to start payment')
  }
  return data.data
}