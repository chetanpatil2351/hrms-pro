import React, { useState } from 'react';
import {
  downloadReceiptFile,
  fetchReceiptBlobUrl,
  type ExpenseResponseDto,
} from '../../services/expenseService';
import { formatCurrency, formatDate, formatDateTime } from '../../payroll/payrollUtils';
import './expenses.css';

interface ExpenseDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  expense: ExpenseResponseDto | null;
  isAdminView?: boolean;
  onCancelExpense?: (expenseId: string) => Promise<void>;
  onApprove?: (expenseId: string) => Promise<void>;
  onRejectPrompt?: (expense: ExpenseResponseDto) => void;
  onReimburse?: (expenseId: string) => Promise<void>;
}

export const ExpenseDetailModal: React.FC<ExpenseDetailModalProps> = ({
  isOpen,
  onClose,
  expense,
  isAdminView = false,
  onCancelExpense,
  onApprove,
  onRejectPrompt,
  onReimburse,
}) => {
  const [actionLoading, setActionLoading] = useState(false);
  const [downloadLoading, setDownloadLoading] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);

  if (!isOpen || !expense) return null;

  const handleDownload = async () => {
    try {
      setDownloadLoading(true);
      setPreviewError(null);
      await downloadReceiptFile(expense.id, expense.receiptFileName);
    } catch (err: unknown) {
      setPreviewError(err instanceof Error ? err.message : 'Failed to download receipt');
    } finally {
      setDownloadLoading(false);
    }
  };

  const handlePreviewInTab = async () => {
    try {
      setDownloadLoading(true);
      setPreviewError(null);
      const url = await fetchReceiptBlobUrl(expense.id);
      window.open(url, '_blank');
    } catch (err: unknown) {
      setPreviewError(err instanceof Error ? err.message : 'Failed to preview receipt');
    } finally {
      setDownloadLoading(false);
    }
  };

  const handleCancelClick = async () => {
    if (!onCancelExpense) return;
    if (!window.confirm('Are you sure you want to cancel this expense claim?')) return;
    try {
      setActionLoading(true);
      await onCancelExpense(expense.id);
      onClose();
    } finally {
      setActionLoading(false);
    }
  };

  const handleApproveClick = async () => {
    if (!onApprove) return;
    try {
      setActionLoading(true);
      await onApprove(expense.id);
      onClose();
    } finally {
      setActionLoading(false);
    }
  };

  const handleReimburseClick = async () => {
    if (!onReimburse) return;
    try {
      setActionLoading(true);
      await onReimburse(expense.id);
      onClose();
    } finally {
      setActionLoading(false);
    }
  };

  const canCancel =
    !isAdminView &&
    (expense.status === 'SUBMITTED' ||
      expense.status === 'PENDING' ||
      expense.status === 'UNDER_REVIEW');

  const canApprove =
    isAdminView &&
    (expense.status === 'SUBMITTED' ||
      expense.status === 'PENDING' ||
      expense.status === 'UNDER_REVIEW');

  const canReject =
    isAdminView &&
    expense.status !== 'REJECTED' &&
    expense.status !== 'REIMBURSED' &&
    expense.status !== 'CANCELLED';

  const canReimburse = isAdminView && expense.status === 'APPROVED';

  return (
    <div className="modal-backdrop">
      <div className="modal-content" style={{ maxWidth: '650px', width: '95%' }}>
        <div className="modal-header">
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 700 }}>
                Expense Claim Details
              </h3>
              <span className={`exp-badge exp-badge-${expense.status}`}>
                {expense.status.replace('_', ' ')}
              </span>
            </div>
            <p style={{ margin: '4px 0 0 0', fontSize: '12px', color: '#64748b' }}>
              ID: {expense.id}
            </p>
          </div>
          <button
            type="button"
            className="exp-tab-btn"
            style={{ fontSize: '18px', padding: '4px 8px' }}
            onClick={onClose}
            disabled={actionLoading}
          >
            ✕
          </button>
        </div>

        {/* Rejection Alert */}
        {expense.status === 'REJECTED' && expense.rejectionReason && (
          <div className="exp-rejection-alert">
            <strong>❌ Claim Rejected:</strong> {expense.rejectionReason}
          </div>
        )}

        {previewError && (
          <div className="exp-rejection-alert" style={{ marginTop: '12px' }}>
            {previewError}
          </div>
        )}

        {/* Summary Card */}
        <div
          style={{
            background: '#f8fafc',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            padding: '16px',
            marginTop: '16px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '12px',
          }}
        >
          <div>
            <div style={{ fontSize: '12px', color: '#64748b', textTransform: 'uppercase', fontWeight: 600 }}>
              Claimed Amount
            </div>
            <div style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a' }}>
              {formatCurrency(expense.amount)}
            </div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '4px' }}>
            <span className="exp-category-pill" style={{ fontSize: '12px', padding: '4px 10px' }}>
              {expense.category}
            </span>
            <span style={{ fontSize: '12px', color: '#64748b' }}>
              Expense Date: <strong>{formatDate(expense.expenseDate)}</strong>
            </span>
          </div>
        </div>

        {/* Detailed Information Grid */}
        <div className="exp-modal-grid" style={{ marginTop: '16px' }}>
          <div className="exp-form-group">
            <span style={{ fontSize: '12px', color: '#64748b' }}>Employee Name</span>
            <strong style={{ fontSize: '13px' }}>
              {expense.employeeName || '—'} {expense.employeeCode ? `(${expense.employeeCode})` : ''}
            </strong>
          </div>

          <div className="exp-form-group">
            <span style={{ fontSize: '12px', color: '#64748b' }}>Department / Branch</span>
            <strong style={{ fontSize: '13px' }}>
              {expense.departmentName || '—'} • {expense.branchName || '—'}
            </strong>
          </div>

          <div className="exp-form-group">
            <span style={{ fontSize: '12px', color: '#64748b' }}>Submission Date</span>
            <span style={{ fontSize: '13px' }}>{formatDateTime(expense.createdAt)}</span>
          </div>

          <div className="exp-form-group">
            <span style={{ fontSize: '12px', color: '#64748b' }}>Approval / Review Date</span>
            <span style={{ fontSize: '13px' }}>
              {expense.approvedAt ? formatDateTime(expense.approvedAt) : 'Pending Review'}
              {expense.approvedByName ? ` by ${expense.approvedByName}` : ''}
            </span>
          </div>

          {expense.reimbursedAt && (
            <div className="exp-form-group full-width">
              <span style={{ fontSize: '12px', color: '#64748b' }}>Reimbursement Date</span>
              <span style={{ fontSize: '13px', color: '#0284c7', fontWeight: 600 }}>
                ✅ Payout processed on {formatDateTime(expense.reimbursedAt)}
              </span>
            </div>
          )}

          <div className="exp-form-group full-width">
            <span style={{ fontSize: '12px', color: '#64748b' }}>Purpose / Description</span>
            <div
              style={{
                background: '#ffffff',
                border: '1px solid #e2e8f0',
                borderRadius: '6px',
                padding: '10px 14px',
                fontSize: '13px',
                color: expense.description ? '#1e293b' : '#94a3b8',
                minHeight: '48px',
                whiteSpace: 'pre-wrap',
              }}
            >
              {expense.description || 'No description provided.'}
            </div>
          </div>

          {/* Receipt Section */}
          <div className="exp-form-group full-width">
            <span style={{ fontSize: '12px', color: '#64748b' }}>Receipt Document</span>
            {expense.hasReceipt ? (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  background: '#f1f5f9',
                  border: '1px solid #cbd5e1',
                  borderRadius: '6px',
                  padding: '10px 14px',
                  gap: '12px',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span style={{ fontSize: '20px' }}>📎</span>
                  <div>
                    <div style={{ fontSize: '13px', fontWeight: 600, color: '#0f172a' }}>
                      {expense.receiptFileName || 'Receipt Attachment'}
                    </div>
                    {expense.receiptContentType && (
                      <div style={{ fontSize: '11px', color: '#64748b' }}>
                        {expense.receiptContentType}
                      </div>
                    )}
                  </div>
                </div>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    type="button"
                    className="exp-btn-action"
                    onClick={handlePreviewInTab}
                    disabled={downloadLoading}
                  >
                    🔍 Preview
                  </button>
                  <button
                    type="button"
                    className="exp-btn-action"
                    onClick={handleDownload}
                    disabled={downloadLoading}
                  >
                    ⬇️ Download
                  </button>
                </div>
              </div>
            ) : (
              <div
                style={{
                  background: '#f8fafc',
                  border: '1px dashed #cbd5e1',
                  borderRadius: '6px',
                  padding: '10px 14px',
                  fontSize: '13px',
                  color: '#94a3b8',
                  textAlign: 'center',
                }}
              >
                No receipt document was attached to this claim.
              </div>
            )}
          </div>
        </div>

        {/* Modal Actions */}
        <div
          className="modal-actions"
          style={{
            marginTop: '24px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '10px',
          }}
        >
          <div>
            {canCancel && (
              <button
                type="button"
                className="exp-btn-action"
                style={{ color: '#dc2626', borderColor: '#fca5a5' }}
                onClick={handleCancelClick}
                disabled={actionLoading}
              >
                {actionLoading ? 'Cancelling...' : 'Cancel Claim'}
              </button>
            )}
          </div>

          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            {canReject && (
              <button
                type="button"
                className="exp-btn-reject"
                onClick={() => {
                  onClose();
                  onRejectPrompt?.(expense);
                }}
                disabled={actionLoading}
              >
                Reject Claim
              </button>
            )}

            {canApprove && (
              <button
                type="button"
                className="exp-btn-approve"
                onClick={handleApproveClick}
                disabled={actionLoading}
              >
                {actionLoading ? 'Approving...' : 'Approve Claim'}
              </button>
            )}

            {canReimburse && (
              <button
                type="button"
                className="exp-btn-reimburse"
                onClick={handleReimburseClick}
                disabled={actionLoading}
              >
                {actionLoading ? 'Processing...' : 'Mark as Reimbursed'}
              </button>
            )}

            <button
              type="button"
              className="secondary-btn"
              onClick={onClose}
              disabled={actionLoading}
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
