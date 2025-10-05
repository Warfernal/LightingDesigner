import { apiClient, extractStatusMessage } from './client'

export type RawColor = number | string | null | undefined

export interface LightingOverridesPayload {
  hpRow?: number
  hpFirstCol?: number
  hpLastCol?: number
  resourceRow?: number
  resourceFirstCol?: number
  resourceLastCol?: number
  hpColor?: RawColor
  resourceColor?: RawColor
  backgroundColor?: RawColor
  resourceColors?: Record<string, RawColor>
}

export interface StatusResponse {
  status: string
}

export const fetchOverrides = async (): Promise<LightingOverridesPayload> => {
  const { data } = await apiClient.get<LightingOverridesPayload>('/overrides')
  return data
}

export const persistOverrides = async (payload: LightingOverridesPayload): Promise<string | undefined> => {
  const { data } = await apiClient.put('/overrides', payload)
  return extractStatusMessage(data)
}

export const startLighting = async (): Promise<string | undefined> => {
  const { data } = await apiClient.post('/start')
  return extractStatusMessage(data)
}

export const stopLighting = async (): Promise<string | undefined> => {
  const { data } = await apiClient.post('/stop')
  return extractStatusMessage(data)
}

export const defineCaptureArea = async (): Promise<string | undefined> => {
  const { data } = await apiClient.post('/define-area')
  return extractStatusMessage(data)
}
