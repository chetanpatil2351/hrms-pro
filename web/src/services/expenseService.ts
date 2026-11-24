// Expense & Reimbursement API Service
// Connects to /api/v1/expenses endpoints using shared apiRequest helper

import { apiRequest, downloadFile, getAccessToken } from "./api";
import type { ApiResponse } from "./employeeService";

export type ExpenseCategory =
  | 'TRAVEL'
  | 'MEALS'
  | 'SUPPLIES'
  | 'ACCOMMODATION'
  | 'COMMUNICATION'
  | 'TRAINING'
  | 'OTHER';

export type ExpenseStatus =
  | 'SUBMITTED'
  | 'PENDING'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'REIMBURSED'
  | 'CANCELLED';

export interface ExpenseResponseDto {
  id: string;
  tenantId: string;
  employeeId: string;
  employeeName?: string;
  employeeCode?: string;
  departmentName?: string;
  branchName?: string;
  amount: number;
  category: ExpenseCategory;
  status: ExpenseStatus;
  expenseDate: string;
  description?: string;
  hasReceipt: boolean;
  receiptFileName?: string;
  receiptContentType?: string;
  rejectionReason?: string;
  createdAt: string;
  approvedAt?: string;
  approvedById?: string;
  approvedByName?: string;
  reimbursedAt?: string;
}

export interface ExpenseCreateRequest {
  amount: number;
  category: ExpenseCategory;
  expenseDate?: string;
  description?: string;
}

export interface ExpenseSummaryDto {
  totalClaims: number;
  pendingCount: number;
  pendingAmount: number;
  approvedCount: number;
  approvedAmount: number;
  reimbursedCount: number;
  reimbursedAmount: number;
  rejectedCount: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/**
 * Submit a new expense claim. Supports JSON or FormData (with receipt file).
 */
export async function submitExpense(
  payload: ExpenseCreateRequest,
  receiptFile?: File | null
): Promise<ApiResponse<ExpenseResponseDto>> {
  if (receiptFile) {
    const formData = new FormData();
    formData.append('amount', payload.amount.toString());
    formData.append('category', payload.category);
    if (payload.expenseDate) {
      formData.append('expenseDate', payload.expenseDate);
    }
    if (payload.description) {
      formData.append('description', payload.description);
    }
    formData.append('receipt', receiptFile);

    return apiRequest<ApiResponse<ExpenseResponseDto>>('/expenses', {
      method: 'POST',
      body: formData,
    });
  }

  return apiRequest<ApiResponse<ExpenseResponseDto>>('/expenses', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

/**
 * Upload or replace a receipt for an existing expense claim.
 */
export async function uploadReceipt(
  expenseId: string,
  receiptFile: File
): Promise<ApiResponse<ExpenseResponseDto>> {
  const formData = new FormData();
  formData.append('file', receiptFile);

  return apiRequest<ApiResponse<ExpenseResponseDto>>(`/expenses/${expenseId}/receipt`, {
    method: 'POST',
    body: formData,
  });
}

/**
 * List the current employee's submitted expenses.
 */
export async function getMyExpenses(params: {
  category?: string;
  status?: string;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
} = {}): Promise<ApiResponse<PageResponse<ExpenseResponseDto>>> {
  const query = new URLSearchParams();
  if (params.category) query.append('category', params.category);
  if (params.status) query.append('status', params.status);
  if (params.startDate) query.append('startDate', params.startDate);
  if (params.endDate) query.append('endDate', params.endDate);
  if (params.page !== undefined) query.append('page', params.page.toString());
  if (params.size !== undefined) query.append('size', params.size.toString());

  const qs = query.toString();
  return apiRequest<ApiResponse<PageResponse<ExpenseResponseDto>>>(
    `/expenses/my${qs ? `?${qs}` : ''}`
  );
}

/**
 * Get detailed view of a specific expense.
 */
export async function getExpenseById(
  expenseId: string
): Promise<ApiResponse<ExpenseResponseDto>> {
  return apiRequest<ApiResponse<ExpenseResponseDto>>(`/expenses/${expenseId}`);
}

/**
 * Cancel own submitted expense claim.
 */
export async function cancelExpense(
  expenseId: string
): Promise<ApiResponse<ExpenseResponseDto>> {
  return apiRequest<ApiResponse<ExpenseResponseDto>>(`/expenses/${expenseId}/cancel`, {
    method: 'POST',
  });
}

/**
 * List all tenant expenses for HR/Admin with multi-criteria filtering.
 */
export async function listAllExpenses(params: {
  employeeId?: string;
  category?: string;
  status?: string;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
} = {}): Promise<ApiResponse<PageResponse<ExpenseResponseDto>>> {
  const query = new URLSearchParams();
  if (params.employeeId) query.append('employeeId', params.employeeId);
  if (params.category) query.append('category', params.category);
  if (params.status) query.append('status', params.status);
  if (params.startDate) query.append('startDate', params.startDate);
  if (params.endDate) query.append('endDate', params.endDate);
  if (params.page !== undefined) query.append('page', params.page.toString());
  if (params.size !== undefined) query.append('size', params.size.toString());

  const qs = query.toString();
  return apiRequest<ApiResponse<PageResponse<ExpenseResponseDto>>>(
    `/expenses${qs ? `?${qs}` : ''}`
  );
}

/**
 * Retrieve aggregated expense metrics for the dashboard.
 */
export async function getSummaryMetrics(): Promise<ApiResponse<ExpenseSummaryDto>> {
  return apiRequest<ApiResponse<ExpenseSummaryDto>>('/expenses/summary');
}

/**
 * Approve an expense claim (HR/Admin).
 */
export async function approveExpense(
  expenseId: string
): Promise<ApiResponse<ExpenseResponseDto>> {
  return apiRequest<ApiResponse<ExpenseResponseDto>>(`/expenses/${expenseId}/approve`, {
    method: 'POST',
  });
}

/**
 * Reject an expense claim with mandatory reason (HR/Admin).
 */
export async function rejectExpense(
  expenseId: string,
  rejectionReason: string
): Promise<ApiResponse<ExpenseResponseDto>> {
  return apiRequest<ApiResponse<ExpenseResponseDto>>(`/expenses/${expenseId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ rejectionReason }),
  });
}

/**
 * Mark an approved expense as reimbursed (HR/Admin/Accountant).
 */
export async function reimburseExpense(
  expenseId: string
): Promise<ApiResponse<ExpenseResponseDto>> {
  return apiRequest<ApiResponse<ExpenseResponseDto>>(`/expenses/${expenseId}/reimburse`, {
    method: 'POST',
  });
}

/**
 * Fetch receipt blob URL with Authorization header.
 */
export async function fetchReceiptBlobUrl(expenseId: string): Promise<string> {
  const token = getAccessToken();
  const response = await fetch(`http://localhost:8080/api/v1/expenses/${expenseId}/receipt`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) {
    throw new Error(`Failed to download receipt: ${response.status}`);
  }
  const blob = await response.blob();
  return URL.createObjectURL(blob);
}

/**
 * Download attached receipt file directly.
 */
export async function downloadReceiptFile(
  expenseId: string,
  fallbackFileName?: string
): Promise<void> {
  await downloadFile(`/expenses/${expenseId}/receipt`, fallbackFileName || `receipt_${expenseId}`);
}

