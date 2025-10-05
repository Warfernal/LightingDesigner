import axios from 'axios'

const baseURL = (import.meta.env.VITE_API_BASE_URL as string | undefined) || '/api'

export const apiClient = axios.create({
  baseURL,
  withCredentials: false,
  headers: {
    'Content-Type': 'application/json',
  },
})

export const extractStatusMessage = (data: unknown): string | undefined => {
  if (!data) return undefined
  if (typeof data === 'string') return data
  if (typeof data === 'object') {
    const record = data as Record<string, unknown>
    if (typeof record.status === 'string') return record.status
    if (typeof record.message === 'string') return record.message
  }
  return undefined
}
