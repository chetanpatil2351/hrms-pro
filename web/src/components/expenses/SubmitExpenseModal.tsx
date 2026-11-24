import React, { useState, useRef } from 'react';
import {
  submitExpense,
  type ExpenseCategory,
  type ExpenseResponseDto,
} from '../../services/expenseService';
import { formatCurrency } from '../../payroll/payrollUtils';
import './expenses.css';

interface SubmitExpenseModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (expense: ExpenseResponseDto) => void;
}

const CATEGORIES: { value: ExpenseCategory; label: string }[] = [
  { value: 'TRAVEL', label: '✈️ Travel & Conveyance' },
  { value: 'MEALS', label: '🍽️ Food & Meals' },
  { value: 'ACCOMMODATION', label: '🏨 Hotel & Accommodation' },
  { value: 'COMMUNICATION', label: '📱 Phone & Internet' },
  { value: 'SUPPLIES', label: '📦 Office Supplies' },
  { value: 'TRAINING', label: '🎓 Training & Certification' },
  { value: 'OTHER', label: '📝 Other Expenses' },
];

const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
const ALLOWED_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png', 'webp'];

export const SubmitExpenseModal: React.FC<SubmitExpenseModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState<ExpenseCategory>('TRAVEL');
  const [expenseDate, setExpenseDate] = useState(() => new Date().toISOString().split('T')[0]);
  const [description, setDescription] = useState('');
  const [receiptFile, setReceiptFile] = useState<File | null>(null);
  const [filePreviewUrl, setFilePreviewUrl] = useState<string | null>(null);

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  if (!isOpen) return null;

  const validate = (): boolean => {
    const errs: Record<string, string> = {};
    const parsedAmount = parseFloat(amount);

    if (!amount.trim()) {
      errs.amount = 'Expense amount is required';
    } else if (isNaN(parsedAmount) || parsedAmount <= 0) {
      errs.amount = 'Amount must be greater than zero';
    } else if (parsedAmount > 1000000) {
      errs.amount = 'Amount cannot exceed ₹10,00,000 per claim';
    }

    if (!category) {
      errs.category = 'Category is required';
    }

    if (!expenseDate) {
      errs.expenseDate = 'Expense date is required';
    } else {
      const selected = new Date(expenseDate);
      const today = new Date();
      today.setHours(23, 59, 59, 999);
      if (selected > today) {
        errs.expenseDate = 'Expense date cannot be in the future';
      }
    }

    if (receiptFile) {
      const ext = receiptFile.name.split('.').pop()?.toLowerCase();
      if (!ext || !ALLOWED_EXTENSIONS.includes(ext)) {
        errs.receipt = `Invalid file format. Allowed: ${ALLOWED_EXTENSIONS.join(', ').toUpperCase()}`;
      } else if (receiptFile.size > MAX_FILE_SIZE_BYTES) {
        errs.receipt = 'File size exceeds 5 MB limit';
      }
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      processSelectedFile(file);
    }
  };

  const processSelectedFile = (file: File) => {
    const ext = file.name.split('.').pop()?.toLowerCase();
    if (!ext || !ALLOWED_EXTENSIONS.includes(ext)) {
      setErrors((prev) => ({
        ...prev,
        receipt: `Invalid file format. Allowed: ${ALLOWED_EXTENSIONS.join(', ').toUpperCase()}`,
      }));
      return;
    }
    if (file.size > MAX_FILE_SIZE_BYTES) {
      setErrors((prev) => ({
        ...prev,
        receipt: 'File size exceeds 5 MB limit',
      }));
      return;
    }

    setErrors((prev) => {
      const next = { ...prev };
      delete next.receipt;
      return next;
    });

    setReceiptFile(file);

    if (file.type.startsWith('image/')) {
      const url = URL.createObjectURL(file);
      setFilePreviewUrl(url);
    } else {
      setFilePreviewUrl(null);
    }
  };

  const handleRemoveFile = () => {
    setReceiptFile(null);
    if (filePreviewUrl) {
      URL.revokeObjectURL(filePreviewUrl);
      setFilePreviewUrl(null);
    }
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      processSelectedFile(e.dataTransfer.files[0]);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setApiError(null);

    if (!validate()) return;

    try {
      setLoading(true);
      const res = await submitExpense(
        {
          amount: parseFloat(amount),
          category,
          expenseDate,
          description: description.trim() || undefined,
        },
        receiptFile
      );

      if (res.success && res.data) {
        onSuccess(res.data);
        onClose();
      } else {
        setApiError(res.message || 'Failed to submit expense claim');
      }
    } catch (err: unknown) {
      setApiError(err instanceof Error ? err.message : 'Error submitting expense');
    } finally {
      setLoading(false);
    }
  };

  const parsedAmount = parseFloat(amount);

  return (
    <div className="modal-backdrop">
      <div className="modal-content" style={{ maxWidth: '600px', width: '95%' }}>
        <div className="modal-header">
          <div>
            <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 700 }}>
              🧾 Submit New Expense Claim
            </h3>
            <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: '#64748b' }}>
              Claims are submitted for HR/Admin approval and reimbursement.
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

        {apiError && (
          <div className="exp-rejection-alert" style={{ margin: '16px 0 0 0' }}>
            <strong>Error:</strong> {apiError}
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ marginTop: '16px' }}>
          <div className="exp-modal-grid">
            <div className="exp-form-group">
              <label htmlFor="expense-category">
                Category <span style={{ color: '#ef4444' }}>*</span>
              </label>
              <select
                id="expense-category"
                value={category}
                onChange={(e) => setCategory(e.target.value as ExpenseCategory)}
                className="exp-select"
                disabled={loading}
              >
                {CATEGORIES.map((cat) => (
                  <option key={cat.value} value={cat.value}>
                    {cat.label}
                  </option>
                ))}
              </select>
              {errors.category && (
                <span style={{ color: '#ef4444', fontSize: '12px' }}>{errors.category}</span>
              )}
            </div>

            <div className="exp-form-group">
              <label htmlFor="expense-date">
                Expense Date <span style={{ color: '#ef4444' }}>*</span>
              </label>
              <input
                id="expense-date"
                type="date"
                value={expenseDate}
                onChange={(e) => setExpenseDate(e.target.value)}
                className="exp-input"
                max={new Date().toISOString().split('T')[0]}
                disabled={loading}
              />
              {errors.expenseDate && (
                <span style={{ color: '#ef4444', fontSize: '12px' }}>{errors.expenseDate}</span>
              )}
            </div>

            <div className="exp-form-group full-width">
              <label htmlFor="expense-amount">
                Amount (INR) <span style={{ color: '#ef4444' }}>*</span>
              </label>
              <input
                id="expense-amount"
                type="number"
                step="0.01"
                min="0.01"
                placeholder="0.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="exp-input"
                disabled={loading}
              />
              {errors.amount && (
                <span style={{ color: '#ef4444', fontSize: '12px' }}>{errors.amount}</span>
              )}
              {!isNaN(parsedAmount) && parsedAmount > 0 && (
                <span style={{ fontSize: '12px', color: '#16a34a', fontWeight: 600 }}>
                  Estimated Claim: {formatCurrency(parsedAmount)}
                </span>
              )}
            </div>

            <div className="exp-form-group full-width">
              <label htmlFor="expense-description">Description / Purpose</label>
              <textarea
                id="expense-description"
                rows={3}
                placeholder="Provide details about the business purpose of this expense..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="exp-input"
                maxLength={500}
                disabled={loading}
              />
              <span style={{ fontSize: '11px', color: '#94a3b8', textAlign: 'right' }}>
                {description.length}/500
              </span>
            </div>

            <div className="exp-form-group full-width">
              <label>Receipt Document (Optional but Recommended)</label>
              <div
                className="exp-receipt-box"
                onDragOver={handleDragOver}
                onDrop={handleDrop}
                onClick={() => fileInputRef.current?.click()}
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".pdf,image/png,image/jpeg,image/webp"
                  style={{ display: 'none' }}
                  onChange={handleFileChange}
                  disabled={loading}
                />
                {!receiptFile ? (
                  <div>
                    <div style={{ fontSize: '28px', marginBottom: '4px' }}>📎</div>
                    <div style={{ fontSize: '13px', fontWeight: 600, color: '#334155' }}>
                      Click to upload or drag & drop receipt
                    </div>
                    <div style={{ fontSize: '11px', color: '#94a3b8', marginTop: '4px' }}>
                      Supports PDF, PNG, JPG, WEBP (Max 5 MB)
                    </div>
                  </div>
                ) : (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: '12px',
                    }}
                    onClick={(e) => e.stopPropagation()}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      {filePreviewUrl ? (
                        <img
                          src={filePreviewUrl}
                          alt="Receipt Preview"
                          style={{
                            width: '48px',
                            height: '48px',
                            objectFit: 'cover',
                            borderRadius: '4px',
                            border: '1px solid #e2e8f0',
                          }}
                        />
                      ) : (
                        <span style={{ fontSize: '24px' }}>📄</span>
                      )}
                      <div style={{ textAlign: 'left' }}>
                        <div style={{ fontSize: '13px', fontWeight: 600, color: '#0f172a' }}>
                          {receiptFile.name}
                        </div>
                        <div style={{ fontSize: '11px', color: '#64748b' }}>
                          {(receiptFile.size / 1024).toFixed(1)} KB
                        </div>
                      </div>
                    </div>
                    <button
                      type="button"
                      className="exp-btn-action"
                      style={{ color: '#dc2626' }}
                      onClick={handleRemoveFile}
                      disabled={loading}
                    >
                      Remove
                    </button>
                  </div>
                )}
              </div>
              {errors.receipt && (
                <span style={{ color: '#ef4444', fontSize: '12px' }}>{errors.receipt}</span>
              )}
            </div>
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
              className="primary-btn"
              disabled={loading}
            >
              {loading ? 'Submitting...' : 'Submit Claim'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
