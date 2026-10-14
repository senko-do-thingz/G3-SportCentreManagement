import { api } from './client'
import type { AuthTokens, LoginRequest, RegisterRequest, UserProfile } from './types'

/** Endpoints of AuthenticationController and UserController used by the auth context. */
export const authApi = {
  login(request: LoginRequest): Promise<AuthTokens> {
    return api.post<AuthTokens>('/auth/login', request, { auth: false })
  },

  register(request: RegisterRequest): Promise<AuthTokens> {
    return api.post<AuthTokens>('/auth/register', request, { auth: false })
  },

  me(): Promise<UserProfile> {
    return api.get<UserProfile>('/users/me')
  },
}
