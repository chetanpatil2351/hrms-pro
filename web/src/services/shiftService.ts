
import { apiRequest } from './api';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ShiftTemplate {
  id: string;
  branchId?: string;
  branchName?: string;
  name: string;
  code: string;
  description?: string;
  startTime: string;
  endTime: string;
  isOvernight: boolean;
  timezone: string;
  totalScheduledMinutes: number;
  gracePeriodMinutes: number;
  lateArrivalPolicy: string;
  earlyDeparturePolicy: string;
  breakPolicy: string;
  breakAllowedMinutes: number;
  weeklyOffDays: string[];
  effectiveFrom: string;
  effectiveTo?: string;
  isActive: boolean;
}

export interface ShiftRequest {
  branchId?: string;
  name: string;
  code: string;
  description?: string;
  startTime: string;
  endTime: string;
  isOvernight: boolean;
  timezone: string;
  gracePeriodMinutes: number;
  lateArrivalPolicy: string;
  earlyDeparturePolicy: string;
  breakPolicy: string;
  breakAllowedMinutes: number;
  weeklyOffDays: string[];
  effectiveFrom: string;
  effectiveTo?: string;
  isActive: boolean;
}

export interface ShiftAssignment {
  id: string;
  employeeId: string;
  employeeName: string;
  employeeCode: string;
  shiftTemplateId: string;
  shiftName: string;
  shiftCode: string;
  assignmentType: string;
  effectiveStart: string;
  effectiveEnd?: string;
  status: string;
  rotationCycleDays?: number;
  rotationSequence?: number;
  notes?: string;
}

export interface ShiftAssignmentRequest {
  employeeId: string;
  shiftTemplateId: string;
  assignmentType: string;
  effectiveStart: string;
  effectiveEnd?: string;
  rotationCycleDays?: number;
  rotationSequence?: number;
  notes?: string;
}

export async function getShifts(page = 0, size = 20) {
  return apiRequest<ApiResponse<Page<ShiftTemplate>>>(
    `/shifts?page=${page}&size=${size}`
  );
}

export async function createShift(data: ShiftRequest) {
  return apiRequest<ApiResponse<ShiftTemplate>>('/shifts', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateShift(id: string, data: ShiftRequest) {
  return apiRequest<ApiResponse<ShiftTemplate>>(`/shifts/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function updateShiftStatus(id: string, active: boolean) {
  return apiRequest<ApiResponse<ShiftTemplate>>(`/shifts/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  });
}

export async function getShiftAssignments(page = 0, size = 20) {
  return apiRequest<ApiResponse<Page<ShiftAssignment>>>(
    `/shifts/assignments?page=${page}&size=${size}`
  );
}

export async function assignShift(data: ShiftAssignmentRequest) {
  return apiRequest<ApiResponse<ShiftAssignment>>('/shifts/assignments', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function bulkAssignShift(data: {
  employeeIds: string[];
  shiftTemplateId: string;
  assignmentType: string;
  effectiveStart: string;
  effectiveEnd?: string;
  notes?: string;
  skipConflicting: boolean;
}) {
  return apiRequest<ApiResponse<Record<string, unknown>>>(
    '/shifts/assignments/bulk',
    {
      method: 'POST',
      body: JSON.stringify(data),
    }
  );
}

export async function cancelShiftAssignment(id: string, reason: string) {
  return apiRequest<ApiResponse<ShiftAssignment>>(
    `/shifts/assignments/${id}/cancel`,
    {
      method: 'PATCH',
      body: JSON.stringify({ reason }),
    }
  );
}
