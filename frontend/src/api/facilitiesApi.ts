import { api } from './client'

export interface Facility { id: number; name: string; active?: boolean; sportId?: number; capacity?: number }
// Main does not expose this lookup yet. Surface its error; never invent production facility IDs.
export const facilitiesApi = {
  list: (signal?: AbortSignal) => api.get<Facility[]>('/facilities', { signal }),
}
