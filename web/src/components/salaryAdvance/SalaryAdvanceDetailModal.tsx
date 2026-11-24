import React, { useState, useEffect, useCallback } from 'react';
import {
  getMyRepayments,
  type SalaryAdvanceResponseDto,
  type SalaryAdvanceRepayment,
} from '../../services/salaryAdvanceService';
import { formatCurrency, formatDate, formatDateTime } from '../../payroll/payrollUtils';

interface SalaryAdvanceDetailModalProps {
  isOpen: boolean;
  advance: SalaryAdvanceResponseDto | null;
  onClose: () => void;
}

export const SalaryAdvanceDetailModal: React.FC<SalaryAdvanceDetailModalProps> = ({
  isOpen,
  advance,
  onClose,
}) => {
  const [repayments, setRepayments] = useState<SalaryAdvanceRepayment[]>([]);
  const [loadingRepayments, setLoadingRepayments] = useState(false);
  const [repaymentError, setRepaymentError] = useState<string | null>(null);

  const loadRepayments = useCallback(async (id: string) => {
    try {
      setLoadingRepayments(true);
      setRepaymentError(null);
      const res = await getMyRepayments(id);
      if (res.success && res.data) {
        setRepayments(res.data);
      } else {
        setRepayments([]);
      }
    } catch (err: unknown) {
      setRepaymentError(err instanceof Error ? err.message : 'Failed to load repayments');
    } finally {
      setLoadingRepayments(false);
    }
  }, []);

  useEffect(() => {
    if (isOpen && advance?.id) {
      loadRepayments(advance.id);
    } else {
      setRepayments([]);
      setRepaymentError(null);
    }
  }, [isOpen, advance?.id, loadRepayments]);

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

  const approved = advance.approvedAmount || 0;
  const outstanding = advance.outstandingBalance !== undefined ? advance.outstandingBalance : approved;
  const repaid = approved > 0 ? Math.max(0, approved - outstanding) : 0;
  const progressPercent = approved > 0 ? Math.min(100, Math.round((repaid / approved) * 100)) : 0;

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <div className="employee-modal" style={{ maxWidth: '680px' }}>
        <div className="modal-header">
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <h2>Advance Details</h2>
              <span className={`sa-badge sa-badge-${advance.status}`}>
                {getStatusLabel(advance.status)}
              </span>
            </div>
            <p>ID: {advance.id}</p>
          </div>
          <button className="close-btn" onClick={onClose} aria-label="Close modal">
            &times;
          </button>
        </div>

        {/* Repayment Progress for approved / active advances */}
        {approved > 0 && advance.status !== 'REJECTED' && (
          <div className="sa-repayment-progress">
            <div className="sa-progress-header">
              <strong>Repayment Progress</strong>
              <span>{progressPercent}% Complete</span>
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

        {/* General Overview Section */}
        <div className="sa-detail-section">
          <h4>Financial Overview</h4>
          <div className="sa-detail-grid">
            <div className="sa-detail-item">
              <label>Requested Amount</label>
              <span className="value">{formatCurrency(advance.requestedAmount)}</span>
            </div>
            <div className="sa-detail-item">
              <label>Approved Amount</label>
              <span className="value" style={{ color: approved > 0 ? '#10b981' : '#64748b' }}>
                {approved > 0 ? formatCurrency(approved) : 'Pending Review'}
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
                {formatCurrency(advance.installmentAmount)} / month
              </span>
            </div>
            <div className="sa-detail-item">
              <label>Repayment Start Date</label>
              <span className="value">{formatDate(advance.repaymentStartDate)}</span>
            </div>
          </div>
        </div>

        {/* Dates & Timeline Section */}
        <div className="sa-detail-section">
          <h4>Request Timeline</h4>
          <div className="sa-detail-grid">
            <div className="sa-detail-item">
              <label>Submission Date</label>
              <span className="value">{formatDateTime(advance.requestDate)}</span>
            </div>
            <div className="sa-detail-item">
              <label>Decision Date</label>
              <span className="value">{formatDateTime(advance.decisionDate)}</span>
            </div>
            {advance.notes && (
              <div className="sa-detail-item" style={{ gridColumn: '1 / -1' }}>
                <label>Reason / Notes</label>
                <div style={{
                  padding: '10px 12px',
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

        {/* Repayment History Section */}
        <div className="sa-detail-section">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
            <h4 style={{ margin: 0, border: 'none' }}>Repayment History</h4>
            {loadingRepayments && <small style={{ color: '#64748b' }}>Refreshing...</small>}
          </div>

          {repaymentError && (
            <div className="error-banner" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>{repaymentError}</span>
              <button
                className="secondary-btn"
                style={{ padding: '4px 8px', fontSize: '11px' }}
                onClick={() => loadRepayments(advance.id)}
              >
                Retry
              </button>
            </div>
          )}

          {loadingRepayments && repayments.length === 0 ? (
            <div className="loading-state" style={{ padding: '24px' }}>
              Loading repayment schedule...
            </div>
          ) : repayments.length === 0 ? (
            <div style={{
              textAlign: 'center',
              padding: '24px',
              background: '#f8fafc',
              border: '1px dashed #cbd5e1',
              borderRadius: '8px',
              color: '#64748b',
              fontSize: '13px',
            }}>
              {advance.status === 'REQUESTED'
                ? 'Repayments will commence following HR approval.'
                : advance.status === 'REJECTED'
                ? 'No repayments recorded for rejected requests.'
                : 'No deduction records yet. Installments will be recorded automatically when payroll runs.'}
            </div>
          ) : (
            <div className="table-scroll">
              <table className="sa-table">
                <thead>
                  <tr>
                    <th>Installment #</th>
                    <th>Deduction Date</th>
                    <th>Amount Deducted</th>
                    <th>Type</th>
                  </tr>
                </thead>
                <tbody>
                  {repayments.map((rep, idx) => (
                    <tr key={rep.id || idx}>
                      <td>
                        <strong>#{rep.installmentNumber !== undefined ? rep.installmentNumber : idx + 1}</strong>
                      </td>
                      <td>{formatDate(rep.deductionDate)}</td>
                      <td style={{ fontWeight: 600, color: '#10b981' }}>
                        {formatCurrency(rep.amount)}
                      </td>
                      <td>
                        <span className="sa-badge sa-badge-APPROVED" style={{ fontSize: '10px' }}>
                          Payroll Deduction
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="modal-actions">
          <button type="button" className="secondary-btn" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
