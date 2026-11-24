import React, { useState } from 'react';
import { submitAdvance, type SalaryAdvanceResponseDto } from '../../services/salaryAdvanceService';
import { formatCurrency } from '../../payroll/payrollUtils';

interface SalaryAdvanceRequestModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (advance: SalaryAdvanceResponseDto) => void;
}

interface FormState {
  requestedAmount: string;
  installmentCount: string;
  notes: string;
}

interface FormErrors {
  requestedAmount?: string;
  installmentCount?: string;
  notes?: string;
}

export const SalaryAdvanceRequestModal: React.FC<SalaryAdvanceRequestModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [form, setForm] = useState<FormState>({
    requestedAmount: '',
    installmentCount: '1',
    notes: '',
  });

  const [errors, setErrors] = useState<FormErrors>({});
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);

  if (!isOpen) return null;

  const validateField = (name: keyof FormState, value: string): string | undefined => {
    switch (name) {
      case 'requestedAmount': {
        const num = parseFloat(value);
        if (!value.trim()) {
          return 'Requested amount is required';
        }
        if (isNaN(num) || num <= 0) {
          return 'Requested amount must be greater than 0';
        }
        if (num > 500000) {
          return 'Requested amount cannot exceed ₹5,00,000';
        }
        return undefined;
      }
      case 'installmentCount': {
        const count = parseInt(value, 10);
        if (!value.trim()) {
          return 'Installment count is required';
        }
        if (isNaN(count) || count < 1 || count > 24) {
          return 'Installment count must be between 1 and 24 months';
        }
        if (count !== parseFloat(value)) {
          return 'Installment count must be a whole number';
        }
        return undefined;
      }
      case 'notes': {
        const trimmed = value.trim();
        if (!trimmed) {
          return 'Reason for salary advance is required';
        }
        if (trimmed.length < 5) {
          return 'Reason must be at least 5 characters';
        }
        if (trimmed.length > 500) {
          return 'Reason cannot exceed 500 characters';
        }
        return undefined;
      }
      default:
        return undefined;
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));

    if (touched[name]) {
      const err = validateField(name as keyof FormState, value);
      setErrors(prev => ({ ...prev, [name]: err }));
    }
  };

  const handleBlur = (e: React.FocusEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setTouched(prev => ({ ...prev, [name]: true }));
    const err = validateField(name as keyof FormState, value);
    setErrors(prev => ({ ...prev, [name]: err }));
  };

  const parsedAmount = parseFloat(form.requestedAmount) || 0;
  const parsedInstallments = parseInt(form.installmentCount, 10) || 1;
  const estimatedMonthlyDeduction = parsedAmount > 0 && parsedInstallments > 0
    ? (parsedAmount / parsedInstallments).toFixed(2)
    : '0.00';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const newErrors: FormErrors = {
      requestedAmount: validateField('requestedAmount', form.requestedAmount),
      installmentCount: validateField('installmentCount', form.installmentCount),
      notes: validateField('notes', form.notes),
    };

    setErrors(newErrors);
    setTouched({ requestedAmount: true, installmentCount: true, notes: true });

    if (newErrors.requestedAmount || newErrors.installmentCount || newErrors.notes) {
      return;
    }

    try {
      setLoading(true);
      setApiError(null);

      const res = await submitAdvance({
        requestedAmount: parsedAmount,
        installmentCount: parsedInstallments,
        notes: form.notes.trim(),
      });

      if (!res.success) {
        throw new Error(res.message || 'Failed to submit salary advance request');
      }

      onSuccess(res.data);
      onClose();
    } catch (err: unknown) {
      setApiError(err instanceof Error ? err.message : 'An unexpected error occurred');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <div className="employee-modal" style={{ maxWidth: '580px' }}>
        <div className="modal-header">
          <div>
            <h2>Request Salary Advance</h2>
            <p>Submit a salary advance request for HR review and repayment approval.</p>
          </div>
          <button className="close-btn" onClick={onClose} disabled={loading} aria-label="Close modal">
            &times;
          </button>
        </div>

        {apiError && (
          <div className="error-banner" role="alert">
            {apiError}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <div className="sa-form-group">
            <label htmlFor="sa-amount">
              Requested Amount (₹) <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <input
              id="sa-amount"
              name="requestedAmount"
              type="number"
              step="0.01"
              min="1"
              max="500000"
              placeholder="e.g. 25000.00"
              value={form.requestedAmount}
              onChange={handleChange}
              onBlur={handleBlur}
              className={touched.requestedAmount && errors.requestedAmount ? 'error' : ''}
              disabled={loading}
              required
            />
            {touched.requestedAmount && errors.requestedAmount && (
              <span className="sa-field-error">{errors.requestedAmount}</span>
            )}
            <span className="sa-field-hint">Enter the total advance amount you wish to request.</span>
          </div>

          <div className="sa-form-group">
            <label htmlFor="sa-installments">
              Repayment Installments (Months) <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <input
              id="sa-installments"
              name="installmentCount"
              type="number"
              min="1"
              max="24"
              step="1"
              placeholder="e.g. 3"
              value={form.installmentCount}
              onChange={handleChange}
              onBlur={handleBlur}
              className={touched.installmentCount && errors.installmentCount ? 'error' : ''}
              disabled={loading}
              required
            />
            {touched.installmentCount && errors.installmentCount && (
              <span className="sa-field-error">{errors.installmentCount}</span>
            )}
            <span className="sa-field-hint">Number of equal monthly payroll deductions (1 to 24 months).</span>
          </div>

          <div className="sa-form-group">
            <label htmlFor="sa-notes">
              Reason / Justification <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <textarea
              id="sa-notes"
              name="notes"
              rows={3}
              placeholder="Please provide the purpose or reason for this advance request (e.g., medical emergency, relocation expenses, etc.)"
              value={form.notes}
              onChange={handleChange}
              onBlur={handleBlur}
              className={touched.notes && errors.notes ? 'error' : ''}
              disabled={loading}
              required
            />
            {touched.notes && errors.notes && (
              <span className="sa-field-error">{errors.notes}</span>
            )}
            <span className="sa-field-hint">{form.notes.length}/500 characters</span>
          </div>

          {parsedAmount > 0 && (
            <div className="sa-calc-preview">
              <h4>Repayment Estimation</h4>
              <div className="sa-calc-preview-grid">
                <div className="sa-calc-item">
                  <span>Requested Amount</span>
                  <strong>{formatCurrency(parsedAmount)}</strong>
                </div>
                <div className="sa-calc-item">
                  <span>Duration</span>
                  <strong>{parsedInstallments} {parsedInstallments === 1 ? 'Month' : 'Months'}</strong>
                </div>
                <div className="sa-calc-item highlight">
                  <span>Estimated Deduction</span>
                  <strong>{formatCurrency(Number(estimatedMonthlyDeduction))} / mo</strong>
                </div>
              </div>
              <div className="sa-notice-box">
                ℹ️ Deductions are automated: Installments will be deducted directly from your monthly payroll earnings starting the cycle after approval.
              </div>
            </div>
          )}

          <div className="modal-actions" style={{ marginTop: '24px' }}>
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
              className="primary-btn"
              disabled={loading}
            >
              {loading ? 'Submitting Request...' : 'Submit Advance Request'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
