import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  getMyExpenses,
  cancelExpense,
  downloadReceiptFile,
  type ExpenseResponseDto,
  type ExpenseCategory,
} from '../../services/expenseService';
import { formatCurrency, formatDate } from '../../payroll/payrollUtils';
import { SubmitExpenseModal } from './SubmitExpenseModal';
import { ExpenseDetailModal } from './ExpenseDetailModal';
import './expenses.css';

interface MyExpensesViewProps {
  onBack?: () => void;
  onNavigateToAdmin?: () => void;
}

type TabType = 'ALL' | 'PENDING' | 'APPROVED' | 'REIMBURSED' | 'REJECTED' | 'CANCELLED';

const CATEGORY_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: 'All Categories' },
  { value: 'TRAVEL', label: '✈️ Travel & Conveyance' },
  { value: 'MEALS', label: '🍽️ Food & Meals' },
  { value: 'ACCOMMODATION', label: '🏨 Hotel & Accommodation' },
  { value: 'COMMUNICATION', label: '📱 Phone & Internet' },
  { value: 'SUPPLIES', label: '📦 Office Supplies' },
  { value: 'TRAINING', label: '🎓 Training & Certification' },
  { value: 'OTHER', label: '📝 Other' },
];

export const MyExpensesView: React.FC<MyExpensesViewProps> = ({
  onBack,
  onNavigateToAdmin,
}) => {
  const [expenses, setExpenses] = useState<ExpenseResponseDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Filter States
  const [activeTab, setActiveTab] = useState<TabType>('ALL');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  // Modals
  const [isSubmitModalOpen, setIsSubmitModalOpen] = useState(false);
  const [selectedExpense, setSelectedExpense] = useState<ExpenseResponseDto | null>(null);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await getMyExpenses({
        category: categoryFilter || undefined,
        startDate: startDate || undefined,
        endDate: endDate || undefined,
        size: 100,
      });

      if (res.success && res.data) {
        setExpenses(res.data.content || []);
      } else {
        setExpenses([]);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load expense history');
    } finally {
      setLoading(false);
    }
  }, [categoryFilter, startDate, endDate]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Handle Cancel Expense
  const handleCancel = async (expenseId: string) => {
    try {
      setError(null);
      const res = await cancelExpense(expenseId);
      if (res.success && res.data) {
        setSuccessMessage('Expense claim cancelled successfully.');
        setExpenses((prev) =>
          prev.map((item) => (item.id === expenseId ? res.data : item))
        );
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to cancel expense');
    }
  };

  // Metrics calculation
  const metrics = useMemo(() => {
    let pendingCount = 0;
    let pendingAmount = 0;
    let approvedCount = 0;
    let approvedAmount = 0;
    let reimbursedCount = 0;
    let reimbursedAmount = 0;

    for (const exp of expenses) {
      const amt = Number(exp.amount) || 0;
      if (
        exp.status === 'SUBMITTED' ||
        exp.status === 'PENDING' ||
        exp.status === 'UNDER_REVIEW'
      ) {
        pendingCount++;
        pendingAmount += amt;
      } else if (exp.status === 'APPROVED') {
        approvedCount++;
        approvedAmount += amt;
      } else if (exp.status === 'REIMBURSED') {
        reimbursedCount++;
        reimbursedAmount += amt;
      }
    }

    return {
      total: expenses.length,
      pendingCount,
      pendingAmount,
      approvedCount,
      approvedAmount,
      reimbursedCount,
      reimbursedAmount,
    };
  }, [expenses]);

  // Tab & search filtering
  const filteredExpenses = useMemo(() => {
    return expenses.filter((exp) => {
      // Tab filter
      if (activeTab === 'PENDING') {
        if (
          exp.status !== 'SUBMITTED' &&
          exp.status !== 'PENDING' &&
          exp.status !== 'UNDER_REVIEW'
        ) {
          return false;
        }
      } else if (activeTab !== 'ALL') {
        if (exp.status !== activeTab) return false;
      }

      // Search query
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchesCategory = exp.category.toLowerCase().includes(q);
        const matchesDesc = exp.description?.toLowerCase().includes(q);
        const matchesAmount = exp.amount.toString().includes(q);
        if (!matchesCategory && !matchesDesc && !matchesAmount) return false;
      }

      return true;
    });
  }, [expenses, activeTab, searchQuery]);

  const clearFilters = () => {
    setCategoryFilter('');
    setSearchQuery('');
    setStartDate('');
    setEndDate('');
    setActiveTab('ALL');
  };

  return (
    <div className="exp-container">
      {/* Header */}
      <div className="exp-header">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            {onBack && (
              <button
                type="button"
                className="exp-btn-action"
                onClick={onBack}
                style={{ fontSize: '12px' }}
              >
                ← Back
              </button>
            )}
            <h2>🧾 My Expenses & Reimbursements</h2>
          </div>
          <p>
            Submit business expense claims, track approval lifecycle, and view reimbursed payouts.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          {onNavigateToAdmin && (
            <button
              type="button"
              className="exp-btn-action"
              onClick={onNavigateToAdmin}
              style={{ fontWeight: 600 }}
            >
              💼 Approvals Dashboard →
            </button>
          )}
          <button
            type="button"
            className="primary-btn"
            onClick={() => setIsSubmitModalOpen(true)}
            style={{ fontWeight: 600 }}
          >
            + Submit New Expense
          </button>
        </div>
      </div>

      {/* Messages */}
      {error && (
        <div className="exp-rejection-alert" style={{ marginTop: 0 }}>
          <strong>Error:</strong> {error}
        </div>
      )}

      {successMessage && (
        <div
          style={{
            background: '#dcfce7',
            border: '1px solid #bbf7d0',
            borderRadius: '6px',
            padding: '12px 16px',
            color: '#166534',
            fontSize: '13px',
          }}
        >
          {successMessage}
        </div>
      )}

      {/* Metrics Grid */}
      <div className="exp-metrics-grid">
        <div className="exp-metric-card accent-blue">
          <span className="label">Total Claims Submitted</span>
          <span className="value">{metrics.total}</span>
          <span className="sub-label">Lifetime personal submissions</span>
        </div>

        <div className="exp-metric-card accent-amber">
          <span className="label">Pending Review</span>
          <span className="value">{formatCurrency(metrics.pendingAmount)}</span>
          <span className="sub-label">{metrics.pendingCount} claim(s) awaiting approval</span>
        </div>

        <div className="exp-metric-card accent-purple">
          <span className="label">Approved for Payroll</span>
          <span className="value">{formatCurrency(metrics.approvedAmount)}</span>
          <span className="sub-label">{metrics.approvedCount} claim(s) queued for reimbursement</span>
        </div>

        <div className="exp-metric-card accent-green">
          <span className="label">Reimbursed to Date</span>
          <span className="value">{formatCurrency(metrics.reimbursedAmount)}</span>
          <span className="sub-label">{metrics.reimbursedCount} claim(s) successfully paid</span>
        </div>
      </div>

      {/* Toolbar & Filter Tabs */}
      <div className="exp-toolbar">
        <div className="exp-tabs">
          <button
            type="button"
            className={`exp-tab-btn ${activeTab === 'ALL' ? 'active' : ''}`}
            onClick={() => setActiveTab('ALL')}
          >
            All <span className="exp-tab-count">{expenses.length}</span>
          </button>
          <button
            type="button"
            className={`exp-tab-btn ${activeTab === 'PENDING' ? 'active' : ''}`}
            onClick={() => setActiveTab('PENDING')}
          >
            Pending <span className="exp-tab-count">{metrics.pendingCount}</span>
          </button>
          <button
            type="button"
            className={`exp-tab-btn ${activeTab === 'APPROVED' ? 'active' : ''}`}
            onClick={() => setActiveTab('APPROVED')}
          >
            Approved <span className="exp-tab-count">{metrics.approvedCount}</span>
          </button>
          <button
            type="button"
            className={`exp-tab-btn ${activeTab === 'REIMBURSED' ? 'active' : ''}`}
            onClick={() => setActiveTab('REIMBURSED')}
          >
            Reimbursed <span className="exp-tab-count">{metrics.reimbursedCount}</span>
          </button>
          <button
            type="button"
            className={`exp-tab-btn ${activeTab === 'REJECTED' ? 'active' : ''}`}
            onClick={() => setActiveTab('REJECTED')}
          >
            Rejected
          </button>
          <button
            type="button"
            className={`exp-tab-btn ${activeTab === 'CANCELLED' ? 'active' : ''}`}
            onClick={() => setActiveTab('CANCELLED')}
          >
            Cancelled
          </button>
        </div>

        <div className="exp-filters-bar">
          <input
            type="text"
            placeholder="Search description..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="exp-input"
            style={{ width: '180px' }}
          />

          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="exp-select"
          >
            {CATEGORY_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>

          <input
            type="date"
            title="Start Date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            className="exp-input"
          />

          <input
            type="date"
            title="End Date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            className="exp-input"
          />

          {(categoryFilter || searchQuery || startDate || endDate) && (
            <button
              type="button"
              className="exp-btn-action"
              onClick={clearFilters}
              title="Reset all filters"
            >
              Reset
            </button>
          )}
        </div>
      </div>

      {/* Table Card */}
      <div className="exp-table-card">
        {loading ? (
          <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
            <div style={{ fontSize: '24px', marginBottom: '8px' }}>⏳</div>
            Loading expense claims...
          </div>
        ) : filteredExpenses.length === 0 ? (
          <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
            <div style={{ fontSize: '32px', marginBottom: '8px' }}>🧾</div>
            <h4 style={{ margin: '0 0 6px 0', color: '#0f172a' }}>No Expense Claims Found</h4>
            <p style={{ margin: 0, fontSize: '13px' }}>
              {expenses.length === 0
                ? "You haven't submitted any expense claims yet."
                : 'No claims match the active filters or search criteria.'}
            </p>
            {expenses.length === 0 && (
              <button
                type="button"
                className="primary-btn"
                onClick={() => setIsSubmitModalOpen(true)}
                style={{ marginTop: '16px' }}
              >
                Submit First Expense
              </button>
            )}
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="exp-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Category</th>
                  <th>Description</th>
                  <th>Amount</th>
                  <th>Receipt</th>
                  <th>Status</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredExpenses.map((exp) => {
                  const isEligibleCancel =
                    exp.status === 'SUBMITTED' ||
                    exp.status === 'PENDING' ||
                    exp.status === 'UNDER_REVIEW';

                  return (
                    <tr
                      key={exp.id}
                      style={{ cursor: 'pointer' }}
                      onClick={() => {
                        setSelectedExpense(exp);
                        setIsDetailModalOpen(true);
                      }}
                    >
                      <td style={{ whiteSpace: 'nowrap' }}>
                        {formatDate(exp.expenseDate)}
                      </td>
                      <td>
                        <span className="exp-category-pill">{exp.category}</span>
                      </td>
                      <td style={{ maxWidth: '250px' }}>
                        <div
                          style={{
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {exp.description || '—'}
                        </div>
                      </td>
                      <td style={{ fontWeight: 600, color: '#0f172a', whiteSpace: 'nowrap' }}>
                        {formatCurrency(exp.amount)}
                      </td>
                      <td>
                        {exp.hasReceipt ? (
                          <button
                            type="button"
                            className="exp-btn-action"
                            style={{ padding: '2px 8px', fontSize: '11px' }}
                            onClick={(e) => {
                              e.stopPropagation();
                              downloadReceiptFile(exp.id, exp.receiptFileName);
                            }}
                            title="Download Receipt"
                          >
                            📎 Receipt
                          </button>
                        ) : (
                          <span style={{ color: '#cbd5e1', fontSize: '12px' }}>—</span>
                        )}
                      </td>
                      <td>
                        <span className={`exp-badge exp-badge-${exp.status}`}>
                          {exp.status.replace('_', ' ')}
                        </span>
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
                          <button
                            type="button"
                            className="exp-btn-action"
                            onClick={() => {
                              setSelectedExpense(exp);
                              setIsDetailModalOpen(true);
                            }}
                          >
                            Details
                          </button>

                          {isEligibleCancel && (
                            <button
                              type="button"
                              className="exp-btn-action"
                              style={{ color: '#dc2626' }}
                              onClick={() => handleCancel(exp.id)}
                            >
                              Cancel
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modals */}
      <SubmitExpenseModal
        isOpen={isSubmitModalOpen}
        onClose={() => setIsSubmitModalOpen(false)}
        onSuccess={(created) => {
          setSuccessMessage('Expense claim submitted successfully.');
          setExpenses((prev) => [created, ...prev]);
        }}
      />

      <ExpenseDetailModal
        isOpen={isDetailModalOpen}
        onClose={() => setIsDetailModalOpen(false)}
        expense={selectedExpense}
        isAdminView={false}
        onCancelExpense={handleCancel}
      />
    </div>
  );
};
