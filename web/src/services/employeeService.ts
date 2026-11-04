import { apiRequest, downloadFile } from './api';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  errors?: unknown;
  timestamp: string;
}

export interface EmployeeStats {
  totalEmployees: number;
  activeEmployees: number;
  inactiveEmployees: number;
  probationEmployees: number;
  terminatedEmployees: number;
  totalDepartments: number;
  totalBranches: number;
}

export interface EmployeeDocument {
  id: string;
  employeeId: string;
  documentType: string;
  fileName: string;
  filePath?: string;
  mimeType: string;
  fileSizeBytes: number;
  createdAt: string;
}

export interface EmployeeAuditLog {
  id: string;
  employeeId: string;
  actorId?: string;
  action: string;
  fieldName?: string;
  oldValue?: string;
  newValue?: string;
  details?: string;
  createdAt: string;
}

export interface OrganizationOption {
  id: string;
  name: string;
  code?: string;
  branchId?: string;
  active?: boolean;
}

export interface Employee {
  id: string;
  tenantId?: string;
  userId?: string;
  membershipId?: string;
  employeeCode: string;
  firstName: string;
  middleName?: string;
  lastName: string;
  fullName: string;
  dateOfBirth?: string;
  gender?: string;
  personalEmail?: string;
  workEmail?: string;
  phone?: string;
  address?: string;
  profilePhotoUrl?: string;
  joiningDate: string;
  employmentType: string;
  designation: string;
  employmentStatus: string;
  probationEndDate?: string;
  branchId?: string;
  branchName?: string;
  departmentId?: string;
  departmentName?: string;
  reportingManagerId?: string;
  reportingManagerName?: string;
  emergencyContactName?: string;
  emergencyContactRelation?: string;
  emergencyContactPhone?: string;
  panNumberMasked?: string;
  aadhaarNumberMasked?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateEmployeeRequest {
  firstName: string;
  middleName?: string;
  lastName: string;
  dateOfBirth?: string;
  gender?: string;
  personalEmail?: string;
  workEmail?: string;
  phone?: string;
  address?: string;
  joiningDate: string;
  employmentType: string;
  designation: string;
  branchId?: string;
  departmentId?: string;
  reportingManagerId?: string;
  probationEndDate?: string;
  employeeCode?: string;
  emergencyContactName?: string;
  emergencyContactRelation?: string;
  emergencyContactPhone?: string;
  panNumber?: string;
  aadhaarNumber?: string;
  createAccountInvitation: boolean;
  roleId?: string;
}

export interface UpdateEmployeeRequest {
  firstName: string;
  middleName?: string;
  lastName: string;
  dateOfBirth?: string;
  gender?: string;
  personalEmail?: string;
  workEmail?: string;
  phone?: string;
  address?: string;
  profilePhotoUrl?: string;
  employmentType?: string;
  designation?: string;
  branchId?: string;
  departmentId?: string;
  reportingManagerId?: string;
  probationEndDate?: string;
  emergencyContactName?: string;
  emergencyContactRelation?: string;
  emergencyContactPhone?: string;
}

export interface EmployeePage {
  content: Employee[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}


export interface EmployeeFilters {
  branchId?: string;
  departmentId?: string;
  employmentStatus?: string;
}

export async function getEmployees(
  search = '',
  page = 0,
  size = 10,
  filters: EmployeeFilters = {}
) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: 'createdAt,desc',
  });

  if (search.trim()) params.set('search', search.trim());
  if (filters.branchId) params.set('branchId', filters.branchId);
  if (filters.departmentId) params.set('departmentId', filters.departmentId);
  if (filters.employmentStatus) {
    params.set('employmentStatus', filters.employmentStatus);
  }

  return apiRequest<ApiResponse<EmployeePage>>(
    `/employees?${params.toString()}`
  );
}

export async function createEmployee(employee: CreateEmployeeRequest) {
  return apiRequest<ApiResponse<Employee>>('/employees', {
    method: 'POST',
    body: JSON.stringify(employee),
  });
}

export async function getEmployeeById(id: string) {
  return apiRequest<ApiResponse<Employee>>(`/employees/${id}`);
}

export async function updateEmployee(
  id: string,
  employee: UpdateEmployeeRequest
) {
  return apiRequest<ApiResponse<Employee>>(`/employees/${id}`, {
    method: 'PUT',
    body: JSON.stringify(employee),
  });
}

export async function getBranches() {
  return apiRequest<ApiResponse<OrganizationOption[]>>('/branches');
}

export async function getDepartments() {
  return apiRequest<ApiResponse<OrganizationOption[]>>('/departments');
}

export async function getEmployeeStats() {
  return apiRequest<ApiResponse<EmployeeStats>>('/employees/stats');
}

export async function activateEmployee(id: string) {
  return apiRequest<ApiResponse<Employee>>(`/employees/${id}/activate`, {
    method: 'PATCH',
  });
}

export async function deactivateEmployee(id: string) {
  return apiRequest<ApiResponse<Employee>>(`/employees/${id}/deactivate`, {
    method: 'PATCH',
  });
}

export async function updateEmployeeStatus(id: string, status: string) {
  return apiRequest<ApiResponse<Employee>>(`/employees/${id}/status?status=${encodeURIComponent(status)}`, {
    method: 'PATCH',
  });
}

export async function getEmployeeDocuments(id: string) {
  return apiRequest<ApiResponse<EmployeeDocument[]>>(`/employees/${id}/documents`);
}

export async function uploadEmployeeDocument(id: string, file: File, documentType = 'OTHER') {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('documentType', documentType);

  return apiRequest<ApiResponse<EmployeeDocument>>(`/employees/${id}/documents/upload`, {
    method: 'POST',
    body: formData,
  });
}

export async function downloadEmployeeDocument(id: string, documentId: string, fileName: string) {
  return downloadFile(`/employees/${id}/documents/${documentId}/download`, fileName);
}

export async function getEmployeeHistory(id: string) {
  return apiRequest<ApiResponse<EmployeeAuditLog[]>>(`/employees/${id}/history`);
}