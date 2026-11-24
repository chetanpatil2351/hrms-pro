// Salary Advance API Service
// Reuses the shared apiRequest helper for HTTP calls.
// Types are based on backend DTOs in com.hrmspro.modules.payroll.dto.

import { apiRequest } from "./api";
import type { ApiResponse } from "./employeeService"; // generic API wrapper

/** Request DTO for creating a salary advance */
export interface SalaryAdvanceRequestDto {
  /** Amount the employee is requesting */
  requestedAmount: number; // using number (JSON uses decimal)
  /** Optional free‑form notes or reason */
  notes?: string;
  /** Number of equal installments the employee prefers */
  installmentCount?: number;
}

/** Response DTO for a salary advance */
export interface SalaryAdvanceResponseDto {
  id: string;
  employeeId: string;
  requestedAmount: number;
  approvedAmount?: number;
  status: string; // matches SalaryAdvanceStatus enum values
  requestDate: string; // ISO instant
  decisionDate?: string;
  repaymentStartDate?: string; // ISO date
  installmentCount?: number;
  installmentAmount?: number;
  outstandingBalance?: number;
  notes?: string;
  approverId?: string;
}

/** Repayment (installment) record */
export interface SalaryAdvanceRepayment {
  id: string;
  // advance reference is not needed here – already scoped by endpoint
  amount: number;
  installmentNumber?: number;
  deductionDate: string; // ISO date
}

/**
 * Submit a new salary‑advance request (employee only).
 */
export const submitAdvance = async (
  payload: SalaryAdvanceRequestDto
): Promise<ApiResponse<SalaryAdvanceResponseDto>> => {
  return apiRequest<ApiResponse<SalaryAdvanceResponseDto>>("/payroll/salary-advances", {
    method: "POST",
    body: JSON.stringify(payload),
  });
};

/** List the authenticated employee's own salary advances */
export const getMyAdvances = async (): Promise<
  ApiResponse<SalaryAdvanceResponseDto[]>
> => {
  return apiRequest<ApiResponse<SalaryAdvanceResponseDto[]>>(
    "/payroll/salary-advances/my"
  );
};

/** Get a specific advance belonging to the authenticated employee */
export const getMyAdvance = async (
  id: string
): Promise<ApiResponse<SalaryAdvanceResponseDto>> => {
  return apiRequest<ApiResponse<SalaryAdvanceResponseDto>>(
    `/payroll/salary-advances/${id}`
  );
};

/** Get repayment history for a specific advance of the authenticated employee */
export const getMyRepayments = async (
  id: string
): Promise<ApiResponse<SalaryAdvanceRepayment[]>> => {
  return apiRequest<ApiResponse<SalaryAdvanceRepayment[]>>(
    `/payroll/salary-advances/${id}/repayments`
  );
};

/** List all salary advances for the current tenant (HR/Admin) */
export const listAllForTenant = async (): Promise<
  ApiResponse<SalaryAdvanceResponseDto[]>
> => {
  return apiRequest<ApiResponse<SalaryAdvanceResponseDto[]>>(
    "/payroll/salary-advances"
  );
};

/** Approve a salary‑advance request (HR/Admin) */
export const approveAdvance = async (
  id: string
): Promise<ApiResponse<SalaryAdvanceResponseDto>> => {
  return apiRequest<ApiResponse<SalaryAdvanceResponseDto>>(
    `/payroll/salary-advances/${id}/approve`,
    { method: "POST" }
  );
};

/** Reject a salary‑advance request (HR/Admin) */
export const rejectAdvance = async (
  id: string
): Promise<ApiResponse<SalaryAdvanceResponseDto>> => {
  return apiRequest<ApiResponse<SalaryAdvanceResponseDto>>(
    `/payroll/salary-advances/${id}/reject`,
    { method: "POST" }
  );
};
