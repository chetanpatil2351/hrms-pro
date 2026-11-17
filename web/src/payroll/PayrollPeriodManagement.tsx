import { useState, useMemo, type FormEvent } from 'react';
import type { PayrollPeriodDto } from '../services/payrollService';
import { formatCurrency, formatDate, getMonthName, getStatusBadge, MONTH_NAMES } from './payrollUtils';

type Props = {
  periods: PayrollPeriodDto[];
  loading: boolean;
  onRefresh: () => void;
  onCreatePeriod: (data: { year: number; month: number; startDate?: string; endDate?: string }) => Promise<void>;
  onCalculatePayroll: (periodId: string) => Promise<void>;
  onApprovePeriod: (periodId: string) => Promise<void>;
  onFinalizePeriod: (periodId: string) => Promise<void>;
  onPreviewPeriod: (periodId: string) => void;
  onViewRuns: (periodId: string) => void;
  showCreateModal: boolean;
  setShowCreateModal: (show: boolean) => void;
};

export default function PayrollPeriodManagement({
  periods,
  loading,
  onRefresh,
  onCreatePeriod,
  onCalculatePayroll,
  onApprovePeriod,
  onFinalizePeriod,
  onPreviewPeriod,
  onViewRuns,
  showCreateModal,
  setShowCreateModal,
}: Props) {
  // Filters
  const [selectedYear, setSelectedYear] = useState<string>('');
  const [selectedStatus, setSelectedStatus] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Processing state per action
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);

  // Create Modal State
  const [currentYear] = useState(() => new Date().getFullYear());

  const [formYear, setFormYear] = useState<number>(() => new Date().getFullYear());
  const [formMonth, setFormMonth] = useState<number>(() => new Date().getMonth() + 1);
  const [formStartDate, setFormStartDate] = useState<string>(() => {
    const d = new Date();
    const start = new Date(d.getFullYear(), d.getMonth(), 1);
    return start.toISOString().slice(0, 10);
  });
  const [formEndDate, setFormEndDate] = useState<string>(() => {
    const d = new Date();
    const end = new Date(d.getFullYear(), d.getMonth() + 1, 0);
    return end.toISOString().slice(0, 10);
  });
  const [createError, setCreateError] = useState<string>('');
  const [createSubmitting, setCreateSubmitting] = useState<boolean>(false);

  // Auto-adjust start & end dates when year or month changes in modal
  function handleMonthYearChange(year: number, month: number) {
    setFormYear(year);
    setFormMonth(month);
    const end = new Date(year, month, 0);
    
    // Format YYYY-MM-DD
    const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
    const startStr = `${year}-${pad(month)}-01`;
    const endStr = `${year}-${pad(month)}-${pad(end.getDate())}`;

    setFormStartDate(startStr);
    setFormEndDate(endStr);
  }

  // Filtered periods
  const filteredPeriods = useMemo(() => {
    return periods.filter(p => {
      if (selectedYear && String(p.year) !== selectedYear) return false;
      if (selectedStatus && p.status !== selectedStatus) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const monthName = getMonthName(p.month).toLowerCase();
        const matchStr = `${monthName} ${p.year} ${p.status}`.toLowerCase();
        if (!matchStr.includes(q)) return false;
      }
      return true;
    });
  }, [periods, selectedYear, selectedStatus, searchQuery]);

  // Unique years for filter
  const availableYears = useMemo(() => {
    const years = Array.from(new Set(periods.map(p => p.year)));
    if (!years.includes(currentYear)) years.push(currentYear);
    return years.sort((a, b) => b - a);
  }, [periods, currentYear]);

  // Handlers with confirmation
  async function handleCalculate(period: PayrollPeriodDto) {
    const name = `${getMonthName(period.month)} ${period.year}`;
    if (!window.confirm(`Calculate payroll for ${name}?\n\nThis will compute gross earnings, statutory deductions (EPF, ESI, PT) and net pay for all active employees based on attendance and loss-of-pay records.`)) {
      return;
    }

    try {
      setActionInProgress(`calc-${period.id}`);
      await onCalculatePayroll(period.id);
    } finally {
      setActionInProgress(null);
    }
  }

  async function handleApprove(period: PayrollPeriodDto) {
    const name = `${getMonthName(period.month)} ${period.year}`;
    if (!window.confirm(`Approve calculated payroll for ${name}?\n\nApproving this period certifies that the numbers are verified. You can then proceed to finalize the period.`)) {
      return;
    }

    try {
      setActionInProgress(`approve-${period.id}`);
      await onApprovePeriod(period.id);
    } finally {
      setActionInProgress(null);
    }
  }

  async function handleFinalize(period: PayrollPeriodDto) {
    const name = `${getMonthName(period.month)} ${period.year}`;
    if (!window.confirm(`⚠️ FINAL WARNING: Finalize and lock payroll for ${name}?\n\nThis will:\n1. Lock all payroll run records.\n2. Generate official monthly payslips for all processed employees.\n3. Prevent further adjustments or recalculations for this cycle.`)) {
      return;
    }

    try {
      setActionInProgress(`finalize-${period.id}`);
      await onFinalizePeriod(period.id);
    } finally {
      setActionInProgress(null);
    }
  }

  async function handleCreateSubmit(e: FormEvent) {
    e.preventDefault();
    setCreateError('');

    if (!formYear || !formMonth) {
      setCreateError('Please select both year and month.');
      return;
    }

    if (formStartDate > formEndDate) {
      setCreateError('Cycle start date must be before or equal to cycle end date.');
      return;
    }

    try {
      setCreateSubmitting(true);
      await onCreatePeriod({
        year: formYear,
        month: formMonth,
        startDate: formStartDate,
        endDate: formEndDate,
      });
      setShowCreateModal(false);
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : 'Failed to create payroll period');
    } finally {
      setCreateSubmitting(false);
    }
  }

  return (
    <div>
      {/* Header & Controls */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 700, margin: '0 0 4px', color: '#1e293b' }}>
            Payroll Period Management
          </h2>
          <p style={{ margin: 0, fontSize: '13px', color: '#64748b' }}>
            Manage monthly cycles, calculation workflows, approvals, and finalization.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px' }}>
          <button
            type="button"
            className="secondary-btn"
            onClick={onRefresh}
            disabled={loading}
          >
            ↻ Refresh
          </button>
          <button
            type="button"
            className="primary-btn"
            onClick={() => {
              setCreateError('');
              setShowCreateModal(true);
            }}
          >
            + New Payroll Period
          </button>
        </div>
      </div>

      {/* Filter Toolbar */}
      <section className="table-card" style={{ marginBottom: '20px', padding: '16px 20px' }}>
        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center' }}>
          {/* Search */}
          <div style={{ flex: '1 1 220px' }}>
            <input
              type="search"
              placeholder="Search by month or year..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '13px' }}
            />
          </div>

          {/* Year Filter */}
          <div style={{ minWidth: '140px' }}>
            <select
              value={selectedYear}
              onChange={e => setSelectedYear(e.target.value)}
              style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '13px', background: 'white' }}
            >
              <option value="">All Years</option>
              {availableYears.map(y => (
                <option key={y} value={y}>{y}</option>
              ))}
            </select>
          </div>

          {/* Status Filter */}
          <div style={{ minWidth: '160px' }}>
            <select
              value={selectedStatus}
              onChange={e => setSelectedStatus(e.target.value)}
              style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '13px', background: 'white' }}
            >
              <option value="">All Statuses</option>
              <option value="DRAFT">Draft</option>
              <option value="PROCESSING">Processing</option>
              <option value="REVIEW">Under Review</option>
              <option value="APPROVED">Approved</option>
              <option value="FINALIZED">Finalized</option>
              <option value="PAID">Paid</option>
            </select>
          </div>

          {(selectedYear || selectedStatus || searchQuery) && (
            <button
              type="button"
              className="secondary-btn"
              onClick={() => {
                setSelectedYear('');
                setSelectedStatus('');
                setSearchQuery('');
              }}
              style={{ padding: '9px 14px' }}
            >
              Reset
            </button>
          )}
        </div>
      </section>

      {/* Periods Table */}
      <section className="table-card">
        <div className="table-header">
          <div>
            <h3>All Payroll Cycles</h3>
            <p>Showing {filteredPeriods.length} of {periods.length} total cycles</p>
          </div>
        </div>

        {loading ? (
          <div className="empty-state">Loading payroll periods...</div>
        ) : filteredPeriods.length === 0 ? (
          <div className="empty-state">
            <h3>No payroll cycles found</h3>
            <p>
              {periods.length === 0
                ? 'Get started by creating your first monthly payroll period.'
                : 'No periods match your current filter criteria.'}
            </p>
            {periods.length === 0 && (
              <button
                type="button"
                className="primary-btn"
                onClick={() => setShowCreateModal(true)}
              >
                + Create Payroll Period
              </button>
            )}
          </div>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>PERIOD</th>
                  <th>CYCLE DATES</th>
                  <th>HEADCOUNT</th>
                  <th>GROSS PAY</th>
                  <th>DEDUCTIONS</th>
                  <th>NET PAY</th>
                  <th>EMPLOYER COST</th>
                  <th>STATUS</th>
                  <th>ACTIONS</th>
                </tr>
              </thead>
              <tbody>
                {filteredPeriods.map(period => {
                  const badge = getStatusBadge(period.status);
                  const isCalculating = actionInProgress === `calc-${period.id}`;
                  const isApproving = actionInProgress === `approve-${period.id}`;
                  const isFinalizing = actionInProgress === `finalize-${period.id}`;
                  const deductions = Math.max(0, (period.totalGrossPay || 0) - (period.totalNetPay || 0));

                  return (
                    <tr key={period.id}>
                      {/* Period Name */}
                      <td>
                        <strong>{getMonthName(period.month)} {period.year}</strong>
                      </td>

                      {/* Dates */}
                      <td>
                        <small style={{ color: '#64748b' }}>
                          {formatDate(period.startDate)} → {formatDate(period.endDate)}
                        </small>
                      </td>

                      {/* Headcount */}
                      <td>
                        <strong>{period.totalEmployees || 0}</strong>
                        <small style={{ display: 'block', color: '#94a3b8' }}>employees</small>
                      </td>

                      {/* Gross Pay */}
                      <td>{formatCurrency(period.totalGrossPay)}</td>

                      {/* Deductions */}
                      <td style={{ color: '#b91c1c' }}>
                        {formatCurrency(deductions)}
                      </td>

                      {/* Net Pay */}
                      <td>
                        <strong style={{ color: '#4338ca' }}>
                          {formatCurrency(period.totalNetPay)}
                        </strong>
                      </td>

                      {/* Employer Cost */}
                      <td>{formatCurrency(period.totalEmployerCost)}</td>

                      {/* Status */}
                      <td>
                        <span className={badge.className}>{badge.label}</span>
                      </td>

                      {/* Actions */}
                      <td>
                        <div style={{ display: 'flex', gap: '6px', alignItems: 'center', flexWrap: 'wrap' }}>
                          {/* Preview is available for non-finalized periods */}
                          {!['FINALIZED', 'PAID'].includes(period.status) && (
                            <button
                              type="button"
                              className="action-link-btn"
                              onClick={() => onPreviewPeriod(period.id)}
                              title="Preview payroll calculation before running"
                            >
                              Preview
                            </button>
                          )}

                          {/* Calculate action for DRAFT and PROCESSING */}
                          {['DRAFT', 'PROCESSING'].includes(period.status) && (
                            <button
                              type="button"
                              className="btn-sm btn-purple"
                              disabled={isCalculating}
                              onClick={() => handleCalculate(period)}
                            >
                              {isCalculating ? 'Computing...' : 'Calculate'}
                            </button>
                          )}

                          {/* Approve action for REVIEW status */}
                          {period.status === 'REVIEW' && (
                            <button
                              type="button"
                              className="btn-sm btn-success"
                              disabled={isApproving}
                              onClick={() => handleApprove(period)}
                            >
                              {isApproving ? 'Approving...' : '✓ Approve'}
                            </button>
                          )}

                          {/* Finalize action for APPROVED status */}
                          {period.status === 'APPROVED' && (
                            <button
                              type="button"
                              className="btn-sm btn-warning"
                              disabled={isFinalizing}
                              onClick={() => handleFinalize(period)}
                            >
                              {isFinalizing ? 'Finalizing...' : '🔒 Finalize'}
                            </button>
                          )}

                          {/* Runs link */}
                          <button
                            type="button"
                            className="action-link-btn"
                            onClick={() => onViewRuns(period.id)}
                            style={{ color: '#64748b' }}
                          >
                            Runs
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Create Payroll Period Modal */}
      {showCreateModal && (
        <div className="modal-backdrop">
          <div className="employee-modal" style={{ maxWidth: '520px' }}>
            <div className="modal-header">
              <div>
                <h2>Create Payroll Period</h2>
                <p>Define a new monthly compensation and disbursement period.</p>
              </div>
              <button
                type="button"
                className="close-btn"
                onClick={() => setShowCreateModal(false)}
              >
                ×
              </button>
            </div>

            {createError && <div className="error-message">{createError}</div>}

            <form onSubmit={handleCreateSubmit}>
              <div className="form-grid" style={{ gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                <label>
                  Year *
                  <select
                    value={formYear}
                    onChange={e => handleMonthYearChange(Number(e.target.value), formMonth)}
                  >
                    {[currentYear - 1, currentYear, currentYear + 1].map(y => (
                      <option key={y} value={y}>{y}</option>
                    ))}
                  </select>
                </label>

                <label>
                  Month *
                  <select
                    value={formMonth}
                    onChange={e => handleMonthYearChange(formYear, Number(e.target.value))}
                  >
                    {MONTH_NAMES.map((m, idx) => (
                      <option key={m} value={idx + 1}>{m}</option>
                    ))}
                  </select>
                </label>

                <label>
                  Start Date *
                  <input
                    type="date"
                    required
                    value={formStartDate}
                    onChange={e => setFormStartDate(e.target.value)}
                  />
                </label>

                <label>
                  End Date *
                  <input
                    type="date"
                    required
                    value={formEndDate}
                    onChange={e => setFormEndDate(e.target.value)}
                  />
                </label>
              </div>

              <div style={{ marginTop: '16px', padding: '12px', background: '#f8fafc', borderRadius: '6px', fontSize: '12px', color: '#64748b' }}>
                ℹ️ Creating a period initializes it in <strong>DRAFT</strong> status. Once created, you can preview calculations and trigger deterministic payroll processing based on employee attendance and statutory rules.
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  className="secondary-btn"
                  onClick={() => setShowCreateModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="primary-btn"
                  disabled={createSubmitting}
                >
                  {createSubmitting ? 'Creating Period...' : 'Create Period'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
