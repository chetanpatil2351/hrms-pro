import { apiRequest } from './api';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  errors?: unknown;
  timestamp: string;
}

export interface AttendanceResponseDto {
  id: string;
  employeeId: string;
  employeeName: string;
  employeeCode: string;
  branchId: string;
  branchName: string;
  attendanceDate: string;
  checkInTime?: string;
  checkOutTime?: string;
  status: string;
  workDurationMinutes?: number;
  breakDurationMinutes?: number;
  netWorkDurationMinutes?: number;
  isLate?: boolean;
  isEarlyDeparture?: boolean;
  isGeofenceVerified?: boolean;
  isSelfieVerified?: boolean;
  sessionNumber?: number;
  assignedShiftId?: string;
  scheduledWorkMinutes?: number;
  lateByMinutes?: number;
  earlyDepartureByMinutes?: number;
  punchDistanceMeters?: number;
  geofenceStatus?: string;
  validationResult?: string;
  breaks?: any[];
}

export interface CheckInRequest {
  idempotencyKey?: string;
  latitude: number;
  longitude: number;
  accuracyMeters?: number;
  isMockLocation?: boolean;
  locationId?: string;
  selfieFilePath?: string;
  selfieMimeType?: string;
  deviceInfo?: string;
  ipAddress?: string;
}

export interface CheckOutRequest {
  idempotencyKey?: string;
  latitude: number;
  longitude: number;
  accuracyMeters?: number;
  isMockLocation?: boolean;
  locationId?: string;
  selfieFilePath?: string;
  selfieMimeType?: string;
  deviceInfo?: string;
  ipAddress?: string;
}

export interface AttendancePage {
  content: AttendanceResponseDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/**
 * Get the authenticated employee's attendance for today.
 */
export async function getTodayAttendance(): Promise<ApiResponse<AttendanceResponseDto>> {
  return apiRequest<ApiResponse<AttendanceResponseDto>>('/attendance/me/today');
}

/**
 * Get paginated attendance history for the authenticated employee.
 */
export async function getAttendanceHistory(page = 0, size = 20): Promise<ApiResponse<AttendancePage>> {
  return apiRequest<ApiResponse<AttendancePage>>(`/attendance/me/history?page=${page}&size=${size}`);
}

/**
 * Perform a check-in with GPS location and device metadata.
 */
export async function checkIn(payload: CheckInRequest): Promise<ApiResponse<AttendanceResponseDto>> {
  return apiRequest<ApiResponse<AttendanceResponseDto>>('/attendance/check-in', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

/**
 * Perform a check-out with GPS location and device metadata.
 */
export async function checkOut(payload: CheckOutRequest): Promise<ApiResponse<AttendanceResponseDto>> {
  return apiRequest<ApiResponse<AttendanceResponseDto>>('/attendance/check-out', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

