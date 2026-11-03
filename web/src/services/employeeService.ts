import { apiRequest } from './api';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  errors?: unknown;
  timestamp: string;
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

export async function getEmployees(search = '', page = 0, size = 20) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: 'createdAt,desc',
  });

  if (search.trim()) params.set('search', search.trim());

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