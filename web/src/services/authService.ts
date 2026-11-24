
import { apiRequest, setAccessToken, setRefreshToken, clearTokens } from './api';

interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: {
    id?: string;
    firstName?: string;
    lastName?: string;
    email?: string;
  };
  activeTenantId: string | null;
  activeTenantName: string | null;
  roles: string[];
}

export interface TenantMembership {
  membershipId: string;
  tenantId: string;
  tenantName: string;
  tenantSubdomain?: string;
  companyCode?: string;
  status: string;
  designation?: string;
  employeeCode?: string;
  roles: string[];
}

export async function login(email: string, password: string) {
  const response = await apiRequest<ApiResponse<AuthResponse>>(
    '/auth/login',
    {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }
  );

  if (!response.success) {
    throw new Error(response.message || 'Login failed');
  }

  saveSession(response.data);
  return response.data;
}

export async function getMemberships() {
  const response = await apiRequest<ApiResponse<TenantMembership[]>>(
    '/auth/memberships'
  );

  if (!response.success) {
    throw new Error(response.message || 'Unable to load companies');
  }

  return response.data;
}

export async function switchTenant(tenantId: string) {
  const response = await apiRequest<ApiResponse<AuthResponse>>(
    '/auth/switch-tenant',
    {
      method: 'POST',
      body: JSON.stringify({ tenantId }),
    }
  );

  if (!response.success) {
    throw new Error(response.message || 'Company selection failed');
  }

  saveSession(response.data);
  return response.data;
}

export function saveSession(data: AuthResponse) {
  setAccessToken(data.accessToken);
  setRefreshToken(data.refreshToken);
  if (data.roles) {
    localStorage.setItem('userRoles', JSON.stringify(data.roles));
  }
}

export function getUserRoles(): string[] {
  try {
    const cached = localStorage.getItem('userRoles');
    if (cached) {
      const parsed = JSON.parse(cached);
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    }
    const token = localStorage.getItem('accessToken');
    if (!token) return [];
    const payload = JSON.parse(atob(token.split('.')[1]));
    if (payload.roles && Array.isArray(payload.roles)) return payload.roles;
  } catch {}
  return [];
}

export function hasRole(roles: string | string[]): boolean {
  const userRoles = getUserRoles();
  const targetRoles = Array.isArray(roles) ? roles : [roles];
  return targetRoles.some(r => userRoles.includes(r));
}

export function logout() {
  clearTokens();
  localStorage.removeItem('userRoles');
}
