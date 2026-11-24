import { apiRequest } from './api';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  errors?: Record<string, string>;
}

export interface TenantDetails {
  id: string;
  name: string;
  legalName?: string;
  subdomain: string;
  companyCode: string;
  email: string;
  phone?: string;
  address?: string;
  city?: string;
  state?: string;
  country?: string;
  postalCode?: string;
  industry?: string;
  website?: string;
  logoUrl?: string;
  timezone?: string;
  subscriptionPlan: string;
  subscriptionStatus: string;
  maxEmployees: number;
  currentEmployeeCount: number;
  active: boolean;
  createdAt: string;
}

export interface UpdateTenantRequest {
  name: string;
  legalName?: string;
  email: string;
  phone?: string;
  address?: string;
  city?: string;
  state?: string;
  country?: string;
  postalCode?: string;
  industry?: string;
  website?: string;
  logoUrl?: string;
  timezone?: string;
}

export interface Branch {
  id: string;
  name: string;
  code: string;
  address?: string;
  city?: string;
  state?: string;
  country?: string;
  active: boolean;
}

export interface Department {
  id: string;
  branchId?: string;
  branchName?: string;
  name: string;
  code: string;
  active: boolean;
}

export interface Member {
  membershipId: string;
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  branchId?: string;
  branchName?: string;
  departmentId?: string;
  departmentName?: string;
  designation?: string;
  employeeCode?: string;
  status: string;
  roles: string[];
  roleIds: string[];
  joinedAt?: string;
}

export interface Invitation {
  id: string;
  tenantId: string;
  email: string;
  roleName?: string;
  branchName?: string;
  departmentName?: string;
  designation?: string;
  employeeCode?: string;
  status: string;
  expiresAt: string;
}

export interface InviteMemberRequest {
  email: string;
  roleId: string;
  branchId?: string;
  departmentId?: string;
  designation?: string;
  employeeCode?: string;
}

export async function getCurrentTenant() {
  return apiRequest<ApiResponse<TenantDetails>>('/tenants/current');
}

export async function updateCurrentTenant(data: UpdateTenantRequest) {
  return apiRequest<ApiResponse<TenantDetails>>('/tenants/current', {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function getBranches() {
  return apiRequest<ApiResponse<Branch[]>>('/branches');
}

export async function createBranch(data: Partial<Branch>) {
  return apiRequest<ApiResponse<Branch>>('/branches', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateBranch(id: string, data: Partial<Branch>) {
  return apiRequest<ApiResponse<Branch>>(`/branches/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function toggleBranchStatus(id: string, active: boolean) {
  return apiRequest<ApiResponse<Branch>>(`/branches/${id}/status?active=${active}`, {
    method: 'PATCH',
  });
}

export async function getDepartments() {
  return apiRequest<ApiResponse<Department[]>>('/departments');
}

export async function createDepartment(data: Partial<Department>) {
  return apiRequest<ApiResponse<Department>>('/departments', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateDepartment(id: string, data: Partial<Department>) {
  return apiRequest<ApiResponse<Department>>(`/departments/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function toggleDepartmentStatus(id: string, active: boolean) {
  return apiRequest<ApiResponse<Department>>(`/departments/${id}/status?active=${active}`, {
    method: 'PATCH',
  });
}

export async function getMembers(page = 0, size = 50) {
  return apiRequest<ApiResponse<{ content: Member[]; totalElements: number; totalPages: number }>>(
    `/members?page=${page}&size=${size}`
  );
}

export async function updateMemberStatus(membershipId: string, status: string) {
  return apiRequest<ApiResponse<void>>(`/members/${membershipId}/status?status=${status}`, {
    method: 'PATCH',
  });
}

export async function updateMemberRoles(membershipId: string, roleIds: string[]) {
  return apiRequest<ApiResponse<Member>>(`/members/${membershipId}/roles`, {
    method: 'PUT',
    body: JSON.stringify({ roleIds }),
  });
}

export async function getInvitations(status?: string) {
  const query = status ? `?status=${status}` : '';
  return apiRequest<ApiResponse<Invitation[]>>(`/members/invitations${query}`);
}

export async function inviteMember(data: InviteMemberRequest) {
  return apiRequest<ApiResponse<Invitation & { rawToken?: string }>>('/members/invite', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function revokeInvitation(id: string) {
  return apiRequest<ApiResponse<void>>(`/members/invitations/${id}`, {
    method: 'DELETE',
  });
}
