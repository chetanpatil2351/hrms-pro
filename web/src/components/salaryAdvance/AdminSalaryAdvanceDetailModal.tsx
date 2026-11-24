import React from 'react';
import type { SalaryAdvanceResponseDto } from '../../services/salaryAdvanceService';
import type { Employee } from '../../services/employeeService';
import { formatCurrency, formatDate, formatDateTime } from '../../payroll/payrollUtils';

interface AdminSalaryAdvanceDetailModalProps {
  isOpen: boolean;
  advance: SalaryAdvanceResponseDto | null;
  employee?: Employee;
  onClose: () => void;
  onApprove?: (advance: SalaryAdvanceResponseDto) => void;
  onReject?: (advance: SalaryAdvanceResponseDto) => void;
}

export const AdminSalaryAdvanceDetailModal: React.FC<AdminSalaryAdvanceDetailModalProps> = ({
  isOpen,
  advance,
  employee,
  onClose,
  onApprove,
  onReject,
}) => {
  if (!isOpen || !advance) return null;

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'REQUESTED':
        return 'Pending Approval';
      case 'APPROVED':
        return 'Approved';
      case 'ACTIVE':
        return 'Active Repayment';
      case 'PARTIALLY_REPAID':
        return 'Partially Repaid';
      case 'FULLY_REPAID':
        return 'Fully Repaid';
      case 'REJECTED':
        return 'Rejected';
      case 'CANCELLED':
        return 'Cancelled';
      default:
        return status;
    }
  };

  const getInitials = (firstName?: string, lastName?: string) => {
    const f = firstName?.[0] || '';
    const l = lastName?.[0] || '';
    return (f + l).toUpperCase() || 'EM';
  };

  const approved = advance.approvedAmount || 0;
  const outstanding = advance.outstandingBalance !== undefined ? advance.outstandingBalance : approved;
  const repaid = approved > 0 ? Math.max(0, approved - outstanding) : 0;
  const progressPercent = approved > 0 ? Math.min(100, Math.round((repaid / approved) * 100)) : 0;

  const empName = employee
    ? `${employee.firstName} ${employee.lastName}`.trim()
    : `Employee #${advance.employeeId.slice(0, 8)}`;

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <div className="employee-modal" style={{ maxWidth: '680px' }}>
        <div className="modal-header">
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <h2>Salary Advance Request</h2>
              <span className={`sa-badge sa-badge-${advance.status}`}>
                {getStatusLabel(advance.status)}
              </span>
            </div>
            <p>Advance ID: {advance.id}</p>
          </div>
          <button className="close-btn" onClick={onClose} aria-label="Close modal">
            &times;
          </button>
        </div>

        {/* Employee Banner */}
        <div className="sa-employee-banner">
          <div className="sa-employee-avatar">
            {getInitials(employee?.firstName, employee?.lastName)}
          </div>
          <div className="sa-employee-info">
            <strong style={{ fontSize: '15px' }}>{empName}</strong>
            <small>
              {employee?.employeeCode ? `${employee.employeeCode} • ` : ''}
              {employee?.designation || 'Staff Member'}
              {employee?.departmentName ? ` • ${employee.departmentName}` : ''}
              {employee?.branchName ? ` (${employee.branchName})` : ''}
            </small>
            {employee?.workEmail && (
              <small style={{ color: '#475569', marginTop: '2px' }}>
                ✉️ {employee.workEmail}
              </small>
            )}
          </div>
        </div>

        {/* Repayment Progress (for approved/active advances) */}
        {approved > 0 && advance.status !== 'REJECTED' && (
          <div className="sa-repayment-progress">
            <div className="sa-progress-header">
              <strong>Repayment Progress</strong>
              <span>{progressPercent}% Repaid</span>
            </div>
            <div className="sa-progress-track">
              <div
                className="sa-progress-fill"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
            <div className="sa-progress-footer">
              <span>Repaid: <strong>{formatCurrency(repaid)}</strong></span>
              <span>Outstanding: <strong style={{ color: '#ef4444' }}>{formatCurrency(outstanding)}</strong></span>
            </div>
          </div>
        )}

        {/* Financial Breakdown Section */}
        <div className="sa-detail-section">
          <h4>Financial Details</h4>
          <div className="sa-detail-grid">
            <div className="sa-detail-item">
              <label>Requested Amount</label>
              <span className="value">{formatCurrency(advance.requestedAmount)}</span>
            </div>
            <div className="sa-detail-item">
              <label>Approved Amount</label>
              <span className="value" style={{ color: approved > 0 ? '#10b981' : '#64748b' }}>
                {approved > 0 ? formatCurrency(approved) : 'Pending Approval'}
              </span>
            </div>
            <div className="sa-detail-item">
              <label>Outstanding Balance</label>
              <span className="value" style={{ color: outstanding > 0 ? '#ef4444' : '#10b981' }}>
                {formatCurrency(outstanding)}
              </span>
            </div>
            <div className="sa-detail-item">
              <label>Installment Schedule</label>
              <span className="value">
                {advance.installmentCount || 1} {advance.installmentCount === 1 ? 'Month' : 'Months'}
              </span>
            </div>
            <div className="sa-detail-item">
              <label>Monthly Deduction</label>
              <span className="value">
                {advance.installmentAmount && advance.installmentAmount > 0
                  ? `${formatCurrency(advance.installmentAmount)} / month`
                  : advance.requestedAmount && advance.installmentCount
                  ? `Est. ${formatCurrency(advance.requestedAmount / advance.installmentCount)} / month`
                  : '—'}
              </span>
            </div>
            <div className="sa-detail-item">
              <label>Repayment Start Date</label>
              <span className="value">{formatDate(advance.repaymentStartDate)}</span>
            </div>
          </div>
        </div>

        {/* Timeline & Audit Section */}
        <div className="sa-detail-section">
          <h4>Request Timeline & Audit</h4>
          <div className="sa-detail-grid">
            <div className="sa-detail-item">
              <label>Requested Date</label>
              <span className="value">{formatDateTime(advance.requestDate)}</span>
            </div>
            <div className="sa-detail-item">
              <label>Decision Date</label>
              <span className="value">{formatDateTime(advance.decisionDate)}</span>
            </div>
            {advance.approverId && (
              <div className="sa-detail-item">
                <label>Approver ID</label>
                <span className="value" style={{ fontSize: '12px' }}>{advance.approverId}</span>
              </div>
            )}
            {advance.notes && (
              <div className="sa-detail-item" style={{ gridColumn: '1 / -1' }}>
                <label>Employee Reason / Justification</label>
                <div style={{
                  padding: '10px 14px',
                  background: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: '6px',
                  fontSize: '13px',
                  color: '#334155',
                  whiteSpace: 'pre-wrap',
                }}>
                  {advance.notes}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Modal Actions */}
        <div className="modal-actions" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            {advance.status === 'REQUESTED' && (
              <div style={{ display: 'flex', gap: '10px' }}>
                <button
                  type="button"
                  className="sa-btn-approve"
                  style={{ padding: '8px 16px', fontSize: '13px' }}
                  onClick={() => {
                    onClose();
                    onApprove?.(advance);
                  }}
                >
                  ✓ Approve Request
                </button>
                <button
                  type="button"
                  className="sa-btn-reject"
                  style={{ padding: '8px 16px', fontSize: '13px' }}
                  onClick={() => {
                    onClose();
                    onReject?.(advance);
                  }}
                >
                  ✕ Reject Request
                </button>
              </div>
            )}
          </div>

          <button type="button" className="secondary-btn" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
