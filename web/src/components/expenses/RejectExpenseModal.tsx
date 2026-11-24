import React, { useState } from 'react';
import {
  rejectExpense,
  type ExpenseResponseDto,
} from '../../services/expenseService';
import { formatCurrency, formatDate } from '../../payroll/payrollUtils';
import './expenses.css';

interface RejectExpenseModalProps {
  isOpen: boolean;
  onClose: () => void;
  expense: ExpenseResponseDto | null;
  onSuccess: (updated: ExpenseResponseDto) => void;
}

export const RejectExpenseModal: React.FC<RejectExpenseModalProps> = ({
  isOpen,
  onClose,
  expense,
  onSuccess,
}) => {
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  if (!isOpen || !expense) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const trimmed = reason.trim();
    if (!trimmed) {
      setError('A rejection reason is mandatory.');
      return;
    }
    if (trimmed.length < 5) {
      setError('Please provide a more descriptive reason (at least 5 characters).');
      return;
    }

    try {
      setLoading(true);
      const res = await rejectExpense(expense.id, trimmed);
      if (res.success && res.data) {
        onSuccess(res.data);
        onClose();
      } else {
        setError(res.message || 'Failed to reject expense claim');
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Error rejecting expense');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-backdrop">
      <div className="modal-content" style={{ maxWidth: '500px', width: '90%' }}>
        <div className="modal-header">
          <div>
            <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 700, color: '#991b1b' }}>
              ⚠️ Reject Expense Claim
            </h3>
            <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: '#64748b' }}>
              The employee will be notified and can view the reason for rejection.
            </p>
          </div>
          <button
            type="button"
            className="exp-tab-btn"
            style={{ fontSize: '18px', padding: '4px 8px' }}
            onClick={onClose}
            disabled={loading}
          >
            ✕
          </button>
        </div>

        {error && (
          <div className="exp-rejection-alert" style={{ margin: '16px 0 0 0' }}>
            {error}
          </div>
        )}

        <div
          style={{
            background: '#f8fafc',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            padding: '12px 16px',
            marginTop: '16px',
            fontSize: '13px',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
            <span style={{ color: '#64748b' }}>Employee:</span>
            <strong>{expense.employeeName || 'Unknown'} ({expense.employeeCode || '—'})</strong>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
            <span style={{ color: '#64748b' }}>Category / Date:</span>
            <span>{expense.category} • {formatDate(expense.expenseDate)}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: '#64748b' }}>Claim Amount:</span>
            <strong style={{ color: '#0f172a' }}>{formatCurrency(expense.amount)}</strong>
          </div>
        </div>

        <form onSubmit={handleSubmit} style={{ marginTop: '16px' }}>
          <div className="exp-form-group">
            <label htmlFor="rejection-reason">
              Rejection Reason <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <textarea
              id="rejection-reason"
              rows={4}
              placeholder="e.g. Duplicate claim, missing itemized GST invoice, non-allowable expense policy item..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              className="exp-input"
              maxLength={1000}
              disabled={loading}
              autoFocus
            />
            <span style={{ fontSize: '11px', color: '#94a3b8', textAlign: 'right' }}>
              {reason.length}/1000 characters
            </span>
          </div>

          <div
            className="modal-actions"
            style={{
              marginTop: '20px',
              display: 'flex',
              justifyContent: 'flex-end',
              gap: '10px',
            }}
          >
            <button
              type="button"
              className="secondary-btn"
              onClick={onClose}
              disabled={loading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="exp-btn-reject"
              style={{ padding: '8px 18px', fontSize: '13px' }}
              disabled={loading}
            >
              {loading ? 'Rejecting...' : 'Confirm Rejection'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
