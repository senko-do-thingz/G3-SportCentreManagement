import { api } from './client'
import { ApiError } from './errors'
import { isAuthTokens, type AuthTokens, type LoginRequest, type RegisterRequest, type UserProfile } from './types'

function requireTokens(value: unknown): AuthTokens {
  if (!isAuthTokens(value)) {
    throw new ApiError(502, 'The authentication service returned an invalid response. Please try again later.')
  }
  return value
}

/** Endpoints of AuthenticationController and UserController used by the auth context. */
export const authApi = {
  async login(request: LoginRequest): Promise<AuthTokens> {
    return requireTokens(await api.post<unknown>('/auth/login', request, { auth: false }))
  },

  async register(request: RegisterRequest): Promise<AuthTokens> {
    return requireTokens(await api.post<unknown>('/auth/register', request, { auth: false }))
  },

  async me(): Promise<UserProfile> {
    const profile = await api.get<UserProfile | null>('/users/me')
    if (
      !profile || !Number.isSafeInteger(profile.id) || profile.id <= 0 ||
      typeof profile.email !== 'string' || typeof profile.fullName !== 'string' ||
      typeof profile.role !== 'string' || typeof profile.status !== 'string'
    ) {
      throw new ApiError(502, 'The account service returned an invalid profile. Please try again later.')
    }
    return profile
  },
}
