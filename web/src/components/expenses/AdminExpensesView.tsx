import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  listAllExpenses,
  getSummaryMetrics,
  approveExpense,
  reimburseExpense,
  downloadReceiptFile,
  type ExpenseResponseDto,
  type ExpenseSummaryDto,
  type ExpenseCategory,
  type ExpenseStatus,
} from '../../services/expenseService';
import { getEmployees, type Employee } from '../../services/employeeService';
import { formatCurrency, formatDate } from '../../payroll/payrollUtils';
import { ExpenseDetailModal } from './ExpenseDetailModal';
import { RejectExpenseModal } from './RejectExpenseModal';
import './expenses.css';

interface AdminExpensesViewProps {
  onBack?: () => void;
  onNavigateToMyExpenses?: () => void;
}

type AdminTab = 'ALL' | 'PENDING' | 'APPROVED' | 'REIMBURSED' | 'REJECTED';

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

export const AdminExpensesView: React.FC<AdminExpensesViewProps> = ({
  onBack,
  onNavigateToMyExpenses,
}) => {
  const [expenses, setExpenses] = useState<ExpenseResponseDto[]>([]);
  const [summary, setSummary] = useState<ExpenseSummaryDto | null>(null);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Filters
  const [activeTab, setActiveTab] = useState<AdminTab>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedEmployeeId, setSelectedEmployeeId] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedStatus, setSelectedStatus] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  // Modals
  const [selectedExpense, setSelectedExpense] = useState<ExpenseResponseDto | null>(null);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
  const [rejectModalExpense, setRejectModalExpense] = useState<ExpenseResponseDto | null>(null);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const [listRes, summaryRes, empRes] = await Promise.all([
        listAllExpenses({
          employeeId: selectedEmployeeId || undefined,
          category: selectedCategory || undefined,
          status: selectedStatus || undefined,
          startDate: startDate || undefined,
          endDate: endDate || undefined,
          size: 200,
        }),
        getSummaryMetrics().catch(() => null),
        getEmployees('', 0, 200).catch(() => ({ success: false, data: { content: [] } })),
      ]);

      if (listRes.success && listRes.data) {
        setExpenses(listRes.data.content || []);
      } else {
        setExpenses([]);
      }

      if (summaryRes?.success && summaryRes.data) {
        setSummary(summaryRes.data);
      }

      if (empRes?.success && empRes.data?.content) {
        setEmployees(empRes.data.content);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load company expense claims');
    } finally {
      setLoading(false);
    }
  }, [selectedEmployeeId, selectedCategory, selectedStatus, startDate, endDate]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Approve action
  const handleApprove = async (expenseId: string) => {
    try {
      setActionLoading(true);
      setError(null);
      const res = await approveExpense(expenseId);
      if (res.success && res.data) {
        setSuccessMessage('Expense claim approved and queued for payroll reimbursement.');
        setExpenses((prev) =>
          prev.map((e) => (e.id === expenseId ? res.data : e))
        );
        // Refresh summary metrics
        getSummaryMetrics().then((s) => s.success && setSummary(s.data));
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to approve expense');
    } finally {
      setActionLoading(false);
    }
  };

  // Reimburse action
  const handleReimburse = async (expenseId: string) => {
    try {
      setActionLoading(true);
      setError(null);
      const res = await reimburseExpense(expenseId);
      if (res.success && res.data) {
        setSuccessMessage('Expense claim marked as reimbursed.');
        setExpenses((prev) =>
          prev.map((e) => (e.id === expenseId ? res.data : e))
        );
        // Refresh summary metrics
        getSummaryMetrics().then((s) => s.success && setSummary(s.data));
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to mark as reimbursed');
    } finally {
      setActionLoading(false);
    }
  };

  // Rejection callback
  const handleRejectSuccess = (updated: ExpenseResponseDto) => {
    setSuccessMessage('Expense claim rejected.');
    setExpenses((prev) =>
      prev.map((e) => (e.id === updated.id ? updated : e))
    );
    getSummaryMetrics().then((s) => s.success && setSummary(s.data));
  };

  // Tab & search client-side filtering
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

      // Search query filter (matches employee name, code, description)
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchesEmp =
          exp.employeeName?.toLowerCase().includes(q) ||
          exp.employeeCode?.toLowerCase().includes(q);
        const matchesCategory = exp.category.toLowerCase().includes(q);
        const matchesDesc = exp.description?.toLowerCase().includes(q);
        const matchesAmount = exp.amount.toString().includes(q);
        if (!matchesEmp && !matchesCategory && !matchesDesc && !matchesAmount) return false;
      }

      return true;
    });
  }, [expenses, activeTab, searchQuery]);

  const clearFilters = () => {
    setSearchQuery('');
    setSelectedEmployeeId('');
    setSelectedCategory('');
    setSelectedStatus('');
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
            <h2>💼 Expenses & Reimbursement Dashboard</h2>
          </div>
          <p>
            Company-wide claims oversight, multi-stage approval workflow, and payroll reimbursement tracking.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          {onNavigateToMyExpenses && (
            <button
              type="button"
              className="secondary-btn"
              onClick={onNavigateToMyExpenses}
            >
              🧾 My Personal Expenses
            </button>
          )}
          <button
            type="button"
            className="exp-btn-action"
            onClick={loadData}
            disabled={loading}
          >
            🔄 Refresh
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

      {/* Summary Metrics */}
      <div className="exp-metrics-grid">
        <div className="exp-metric-card accent-blue">
          <span className="label">Total Claims</span>
          <span className="value">{summary ? summary.totalClaims : expenses.length}</span>
          <span className="sub-label">Company-wide lifetime claims</span>
        </div>

        <div className="exp-metric-card accent-amber">
          <span className="label">Pending Review</span>
          <span className="value">
            {summary ? formatCurrency(Number(summary.pendingAmount)) : '₹0.00'}
          </span>
          <span className="sub-label">
            {summary ? summary.pendingCount : 0} claim(s) awaiting approval
          </span>
        </div>

        <div className="exp-metric-card accent-purple">
          <span className="label">Approved for Payroll</span>
          <span className="value">
            {summary ? formatCurrency(Number(summary.approvedAmount)) : '₹0.00'}
          </span>
          <span className="sub-label">
            {summary ? summary.approvedCount : 0} claim(s) ready for disbursement
          </span>
        </div>

        <div className="exp-metric-card accent-green">
          <span className="label">Reimbursed Payouts</span>
          <span className="value">
            {summary ? formatCurrency(Number(summary.reimbursedAmount)) : '₹0.00'}
          </span>
          <span className="sub-label">
            {summary ? summary.reimbursedCount : 0} claim(s) settled in payroll
          </span>
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
            Pending <span className="exp-tab-count">{summary?.pendingCount ?? 0}</span>
          </button>
          <button
            type="button"
            className={`exp-tab-btn ${activeTab === 'APPROVED' ? 'active' : ''}`}
            onClick={() => setActiveTab('APPROVED')}
          >
            Approved <span className="exp-tab-count">{summary?.approvedCount ?? 0}</span>
          </button>
          <button
            type="button"
            className={`exp-tab-btn ${activeTab === 'REIMBURSED' ? 'active' : ''}`}
            onClick={() => setActiveTab('REIMBURSED')}
          >
            Reimbursed <span className="exp-tab-count">{summary?.reimbursedCount ?? 0}</span>
          </button>
          <button
            type="button"
            className={`exp-tab-btn ${activeTab === 'REJECTED' ? 'active' : ''}`}
            onClick={() => setActiveTab('REJECTED')}
          >
            Rejected
          </button>
        </div>

        <div className="exp-filters-bar">
          <input
            type="text"
            placeholder="Search employee, reason..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="exp-input"
            style={{ width: '180px' }}
          />

          <select
            value={selectedEmployeeId}
            onChange={(e) => setSelectedEmployeeId(e.target.value)}
            className="exp-select"
          >
            <option value="">All Employees</option>
            {employees.map((emp) => (
              <option key={emp.id} value={emp.id}>
                {emp.firstName} {emp.lastName} ({emp.employeeCode || '—'})
              </option>
            ))}
          </select>

          <select
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value)}
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

          {(searchQuery || selectedEmployeeId || selectedCategory || selectedStatus || startDate || endDate) && (
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
            Loading company claims...
          </div>
        ) : filteredExpenses.length === 0 ? (
          <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
            <div style={{ fontSize: '32px', marginBottom: '8px' }}>📋</div>
            <h4 style={{ margin: '0 0 6px 0', color: '#0f172a' }}>No Expense Claims Found</h4>
            <p style={{ margin: 0, fontSize: '13px' }}>
              {expenses.length === 0
                ? 'No employee expense claims have been submitted in this company.'
                : 'No claims match your filter criteria.'}
            </p>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="exp-table">
              <thead>
                <tr>
                  <th>Employee</th>
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
                  const isPending =
                    exp.status === 'SUBMITTED' ||
                    exp.status === 'PENDING' ||
                    exp.status === 'UNDER_REVIEW';
                  const isApproved = exp.status === 'APPROVED';

                  return (
                    <tr
                      key={exp.id}
                      style={{ cursor: 'pointer' }}
                      onClick={() => {
                        setSelectedExpense(exp);
                        setIsDetailModalOpen(true);
                      }}
                    >
                      <td>
                        <div style={{ fontWeight: 600, color: '#0f172a' }}>
                          {exp.employeeName || 'Unknown Employee'}
                        </div>
                        <div style={{ fontSize: '11px', color: '#64748b' }}>
                          {exp.employeeCode || '—'} • {exp.departmentName || '—'}
                        </div>
                      </td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        {formatDate(exp.expenseDate)}
                      </td>
                      <td>
                        <span className="exp-category-pill">{exp.category}</span>
                      </td>
                      <td style={{ maxWidth: '220px' }}>
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
                        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end', alignItems: 'center' }}>
                          <button
                            type="button"
                            className="exp-btn-action"
                            onClick={() => {
                              setSelectedExpense(exp);
                              setIsDetailModalOpen(true);
                            }}
                          >
                            View
                          </button>

                          {isPending && (
                            <>
                              <button
                                type="button"
                                className="exp-btn-approve"
                                onClick={() => handleApprove(exp.id)}
                                disabled={actionLoading}
                              >
                                Approve
                              </button>
                              <button
                                type="button"
                                className="exp-btn-reject"
                                onClick={() => setRejectModalExpense(exp)}
                                disabled={actionLoading}
                              >
                                Reject
                              </button>
                            </>
                          )}

                          {isApproved && (
                            <button
                              type="button"
                              className="exp-btn-reimburse"
                              onClick={() => handleReimburse(exp.id)}
                              disabled={actionLoading}
                            >
                              Reimburse
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
      <ExpenseDetailModal
        isOpen={isDetailModalOpen}
        onClose={() => setIsDetailModalOpen(false)}
        expense={selectedExpense}
        isAdminView={true}
        onApprove={handleApprove}
        onRejectPrompt={(exp) => setRejectModalExpense(exp)}
        onReimburse={handleReimburse}
      />

      <RejectExpenseModal
        isOpen={Boolean(rejectModalExpense)}
        onClose={() => setRejectModalExpense(null)}
        expense={rejectModalExpense}
        onSuccess={handleRejectSuccess}
      />
    </div>
  );
};
