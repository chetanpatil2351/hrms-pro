import { apiRequest } from './api';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  errors?: Record<string, string>;
}

export interface Permission {
  id: string;
  code: string;
  module: string;
  description: string;
}

export interface Role {
  id: string;
  tenantId?: string;
  name: string;
  description?: string;
  isSystemRole: boolean;
  permissions: Permission[];
  createdAt?: string;
}

export interface CreateRoleRequest {
  name: string;
  description?: string;
  permissionIds: string[];
}

export interface UpdateRoleRequest {
  name: string;
  description?: string;
  permissionIds: string[];
}

export interface RoleAuditLog {
  id: string;
  roleId?: string;
  roleName: string;
  actorId?: string;
  actorName: string;
  action: string;
  detailsJson?: string;
  createdAt: string;
}

export async function getPermissions() {
  return apiRequest<ApiResponse<Permission[]>>('/rbac/permissions');
}

export async function getRoles() {
  return apiRequest<ApiResponse<Role[]>>('/rbac/roles');
}

export async function getRoleById(id: string) {
  return apiRequest<ApiResponse<Role>>(`/rbac/roles/${id}`);
}

export async function createRole(data: CreateRoleRequest) {
  return apiRequest<ApiResponse<Role>>('/rbac/roles', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateRole(id: string, data: UpdateRoleRequest) {
  return apiRequest<ApiResponse<Role>>(`/rbac/roles/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function deleteRole(id: string) {
  return apiRequest<ApiResponse<void>>(`/rbac/roles/${id}`, {
    method: 'DELETE',
  });
}

export async function getRoleAuditLogs(page = 0, size = 50) {
  return apiRequest<ApiResponse<{ content: RoleAuditLog[]; totalElements: number; totalPages: number }>>(
    `/rbac/audit-logs?page=${page}&size=${size}`
  );
}
