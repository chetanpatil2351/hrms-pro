import { apiRequest } from './api';
import type { ApiResponse } from './employeeService';

// ==========================================
export interface FailedEmployeeDto {
  employeeCode: string;
  reason: string;
}

export interface PayrollRunResultDto {
  runId: string;
  periodId: string;
  runNumber: number;
  runType: string;
  status: string;
  processedEmployees: number;
  totalGross: number;
  totalDeductions: number;
  totalNet: number;
  employeeSummaries: EmployeePayrollSummaryDto[];
  failedEmployees?: FailedEmployeeDto[];
}
// ==========================================

export interface PayrollPeriodDto {
  id: string;
  year: number;
  month: number;
  startDate: string;
  endDate: string;
  status: 'DRAFT' | 'PROCESSING' | 'REVIEW' | 'APPROVED' | 'FINALIZED' | 'PAID' | string;
  totalEmployees: number;
  totalGrossPay: number;
  totalNetPay: number;
  totalEmployerCost: number;
  finalizedBy?: string | null;
  finalizedAt?: string | null;
}

export interface LineItemDto {
  componentCode: string;
  componentName: string;
  type: 'EARNING' | 'DEDUCTION' | 'EMPLOYER_CONTRIBUTION' | string;
  amount: number;
}

export interface EmployeePayrollSummaryDto {
  employeeId: string;
  employeeCode: string;
  employeeName: string;
  departmentName?: string;
  totalDays: number;
  payableDays: number;
  unpaidLeaveDays: number;
  grossEarnings: number;
  totalDeductions: number;
  employerContributions: number;
  netSalary: number;
  lineItems: LineItemDto[];
}

export interface PayrollPreviewDto {
  periodId: string;
  status: string;
  processedEmployees: number;
  totalGross: number;
  totalDeductions: number;
  totalNet: number;
  employeeSummaries: EmployeePayrollSummaryDto[];
}

export interface PayrollRunDto {
  runId: string;
  periodId: string;
  runNumber: number;
  runType: string;
  status: string;
  createdAt: string;
  completedAt?: string | null;
  processedEmployees: number;
  totalGross: number;
  totalDeductions: number;
  totalNet: number;
}


export interface SalaryComponentDto {
  id: string;
  name: string;
  code: string;
  type: 'EARNING' | 'DEDUCTION' | 'EMPLOYER_CONTRIBUTION';
  calculationType: 'FIXED_AMOUNT' | 'PERCENTAGE';
  calculationBase?: string;
  percentageRate?: number;
  isTaxable?: boolean;
  includeInGross?: boolean;
  includeInCTC?: boolean;
  isStatutory?: boolean;
  isActive?: boolean;
}

export interface ComponentItemDto {
  componentId: string;
  componentCode?: string;
  componentName?: string;
  componentType?: string;
  defaultAmount?: number;
  percentageRate?: number;
  displayOrder?: number;
}

export interface SalaryStructureDto {
  id: string;
  name: string;
  code: string;
  description?: string;
  isActive: boolean;
  components: ComponentItemDto[];
}

export interface EmployeeSalaryStructureDto {
  id?: string;
  employeeId: string;
  employeeCode?: string;
  employeeName?: string;
  salaryStructureId: string;
  salaryStructureName?: string;
  effectiveFrom: string;
  effectiveTo?: string;
  annualCtc: number;
  monthlyGross?: number;
  monthlyBaseSalary?: number;
  isActive?: boolean;
  revisionReason?: string;
}

export interface SalaryRevisionDto {
  id: string;
  employeeId: string;
  salaryStructureId: string;
  effectiveFrom: string;
  effectiveTo?: string;
  createdAt: string;
  createdBy: string;
  revisionReason?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ==========================================
// Payroll Operations Services
// ==========================================

export const getPeriods = async (page = 0, size = 10, sort = 'startDate,desc') => {
  return apiRequest<ApiResponse<PageResponse<PayrollPeriodDto>>>(
    `/payroll/periods?page=${page}&size=${size}&sort=${sort}`
  );
};

export const getPeriod = async (id: string) => {
  return apiRequest<ApiResponse<PayrollPeriodDto>>(`/payroll/periods/${id}`);
};

export const createPeriod = async (payload: {
  year: number;
  month: number;
  startDate?: string;
  endDate?: string;
}) => {
  return apiRequest<ApiResponse<PayrollPeriodDto>>('/payroll/periods', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
};

export const previewPayroll = async (periodId: string) => {
  return apiRequest<ApiResponse<PayrollPreviewDto>>(
    `/payroll/periods/${periodId}/preview`,
    {
      method: 'POST',
    }
  );
};

export const calculatePayroll = async (periodId: string) => {
  return apiRequest<ApiResponse<PayrollRunResultDto>>(
    `/payroll/periods/${periodId}/calculate`,
    {
      method: 'POST',
    }
  );
};

export const approvePeriod = async (periodId: string) => {
  return apiRequest<ApiResponse<PayrollPeriodDto>>(
    `/payroll/periods/${periodId}/approve`,
    {
      method: 'POST',
    }
  );
};

export const finalizePeriod = async (periodId: string) => {
  return apiRequest<ApiResponse<PayrollPeriodDto>>(
    `/payroll/periods/${periodId}/finalize`,
    {
      method: 'POST',
    }
  );
};

export const getPeriodRuns = async (periodId: string) => {
  return apiRequest<ApiResponse<PayrollRunDto[]>>(
    `/payroll/periods/${periodId}/runs`
  );
};

export const getRunDetails = async (runId: string) => {
  return apiRequest<ApiResponse<PayrollRunResultDto>>(`/payroll/runs/${runId}`);
};

// ==========================================
// Salary Structure & Components Services
// ==========================================

export const getComponents = async () => {
  return apiRequest<ApiResponse<SalaryComponentDto[]>>('/payroll/components');
};

export const createComponent = async (data: Partial<SalaryComponentDto>) => {
  return apiRequest<ApiResponse<SalaryComponentDto>>('/payroll/components', {
    method: 'POST',
    body: JSON.stringify(data),
  });
};

export const getStructures = async (page = 0, size = 20) => {
  return apiRequest<ApiResponse<PageResponse<SalaryStructureDto>>>(
    `/payroll/structures?page=${page}&size=${size}`
  );
};

export const getStructureById = async (id: string) => {
  return apiRequest<ApiResponse<SalaryStructureDto>>(`/payroll/structures/${id}`);
};

export const createStructure = async (data: Partial<SalaryStructureDto>) => {
  return apiRequest<ApiResponse<SalaryStructureDto>>('/payroll/structures', {
    method: 'POST',
    body: JSON.stringify(data),
  });
};

export const assignStructure = async (payload: Partial<EmployeeSalaryStructureDto>) => {
  return apiRequest<ApiResponse<EmployeeSalaryStructureDto>>('/payroll/assignments', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
};

export const getEmployeeAssignments = async (employeeId: string) => {
  return apiRequest<ApiResponse<EmployeeSalaryStructureDto[]>>(
    `/payroll/assignments/employee/${employeeId}`
  );
};

export const getEmployeeRevisions = async (employeeId: string) => {
  return apiRequest<ApiResponse<SalaryRevisionDto[]>>(
    `/payroll/revisions/employee/${employeeId}`
  );
};
