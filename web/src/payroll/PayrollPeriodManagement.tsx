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

  // Action state
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);

  // Modal state
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

  function handleMonthYearChange(year: number, month: number) {
    setFormYear(year);
    setFormMonth(month);
    const end = new Date(year, month, 0);
    const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
    const startStr = `${year}-${pad(month)}-01`;
    const endStr = `${year}-${pad(month)}-${pad(end.getDate())}`;
    setFormStartDate(startStr);
    setFormEndDate(endStr);
  }

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

  const availableYears = useMemo(() => {
    const years = Array.from(new Set(periods.map(p => p.year)));
    if (!years.includes(currentYear)) years.push(currentYear);
    return years.sort((a, b) => b - a);
  }, [periods, currentYear]);

  async function handleCalculate(period: PayrollPeriodDto) {
    const name = `${getMonthName(period.month)} ${period.year}`;
    if (!window.confirm(`Calculate payroll for ${name}?\n\nThis will compute gross earnings, statutory deductions and net pay.`)) return;
    try {
      setActionInProgress(`calc-${period.id}`);
      await onCalculatePayroll(period.id);
    } finally {
      setActionInProgress(null);
    }
  }

  async function handleApprove(period: PayrollPeriodDto) {
    const name = `${getMonthName(period.month)} ${period.year}`;
    if (!window.confirm(`Approve calculated payroll for ${name}?`)) return;
    try {
      setActionInProgress(`approve-${period.id}`);
      await onApprovePeriod(period.id);
    } finally {
      setActionInProgress(null);
    }
  }

  async function handleFinalize(period: PayrollPeriodDto) {
    const name = `${getMonthName(period.month)} ${period.year}`;
    if (!window.confirm(`Finalize and lock payroll for ${name}?`)) return;
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
      setCreateError('Year and month are required');
      return;
    }
    if (formStartDate > formEndDate) {
      setCreateError('Start date must be before end date');
      return;
    }
    try {
      setCreateSubmitting(true);
      await onCreatePeriod({ year: formYear, month: formMonth, startDate: formStartDate, endDate: formEndDate });
      setShowCreateModal(false);
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : 'Failed to create period');
    } finally {
      setCreateSubmitting(false);
    }
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 700, margin: '0 0 4px', color: '#1e293b' }}>Payroll Period Management</h2>
          <p style={{ margin: 0, fontSize: '13px', color: '#64748b' }}>Manage monthly cycles, calculations, approvals and finalisation.</p>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <button type="button" className="secondary-btn" onClick={onRefresh} disabled={loading}>↻ Refresh</button>
          <button type="button" className="primary-btn" onClick={() => { setCreateError(''); setShowCreateModal(true); }}>+ New Payroll Period</button>
        </div>
      </div>

      {/* Filter Toolbar */}
      <section className="table-card" style={{ marginBottom: '20px', padding: '16px 20px' }}>
        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ flex: '1 1 220px' }}>
            <input type="search" placeholder="Search by month or year..." value={searchQuery} onChange={e => setSearchQuery(e.target.value)} style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '13px' }} />
          </div>
          <div style={{ minWidth: '140px' }}>
            <select value={selectedYear} onChange={e => setSelectedYear(e.target.value)} style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '13px', background: 'white' }}>
              <option value="">All Years</option>
              {availableYears.map(y => (<option key={y} value={y}>{y}</option>))}
            </select>
          </div>
          <div style={{ minWidth: '160px' }}>
            <select value={selectedStatus} onChange={e => setSelectedStatus(e.target.value)} style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '13px', background: 'white' }}>
              <option value="">All Statuses</option>
              <option value="DRAFT">Draft</option>
              <option value="PROCESSING">Processing</option>
              <option value="REVIEW">Review</option>
              <option value="APPROVED">Approved</option>
              <option value="FINALIZED">Finalized</option>
              <option value="PAID">Paid</option>
            </select>
          </div>
          {(selectedYear || selectedStatus || searchQuery) && (
            <button type="button" className="secondary-btn" onClick={() => { setSelectedYear(''); setSelectedStatus(''); setSearchQuery(''); }} style={{ padding: '9px 14px' }}>Reset</button>
          )}
        </div>
      </section>

      {/* Periods Grid */}
      <section className="table-card">
        <div className="table-header"><div><h3>All Payroll Cycles</h3><p>Showing {filteredPeriods.length} of {periods.length} cycles</p></div></div>
        {loading ? (
          <div className="empty-state">Loading payroll periods...</div>
        ) : filteredPeriods.length === 0 ? (
          <div className="empty-state">
            <h3>No payroll cycles found</h3>
            <p>{periods.length === 0 ? 'Get started by creating your first payroll period.' : 'No periods match your filter.'}</p>
            {periods.length === 0 && (<button type="button" className="primary-btn" onClick={() => setShowCreateModal(true)}>+ Create Payroll Period</button>)}
          </div>
        ) : (
          <div className="grid gap-4" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))' }}>
            {filteredPeriods.map(period => {
              const badge = getStatusBadge(period.status);
              const isCalculating = actionInProgress === `calc-${period.id}`;
              const isApproving = actionInProgress === `approve-${period.id}`;
              const isFinalizing = actionInProgress === `finalize-${period.id}`;
              const deductions = Math.max(0, (period.totalGrossPay || 0) - (period.totalNetPay || 0));
              return (
                <div key={period.id} className="border rounded p-4 hover:shadow" style={{ background: '#fff' }}>
                  <div className="flex justify-between items-start mb-2">
                    <div>
                      <strong>{getMonthName(period.month)} {period.year}</strong>
                      <div className="text-sm text-gray-500">{formatDate(period.startDate)} → {formatDate(period.endDate)}</div>
                    </div>
                    <span className={badge.className}>{badge.label}</span>
                  </div>
                  <div className="text-sm mb-2">
                    <div><strong>{period.totalEmployees || 0}</strong> <span className="text-gray-500">employees</span></div>
                    <div>Gross: {formatCurrency(period.totalGrossPay)}</div>
                    <div>Deduction: {formatCurrency(deductions)}</div>
                    <div>Net: <strong style={{ color: '#4338ca' }}>{formatCurrency(period.totalNetPay)}</strong></div>
                    <div>Employer Cost: {formatCurrency(period.totalEmployerCost)}</div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {!['FINALIZED', 'PAID'].includes(period.status) && (<button type="button" className="action-link-btn" onClick={() => onPreviewPeriod(period.id)} title="Preview payroll">Preview</button>)}
                    {['DRAFT', 'PROCESSING'].includes(period.status) && (<button type="button" className="btn-sm btn-purple" disabled={isCalculating} onClick={() => handleCalculate(period)}>{isCalculating ? 'Computing...' : 'Calculate'}</button>)}
                    {period.status === 'REVIEW' && (<button type="button" className="btn-sm btn-success" disabled={isApproving} onClick={() => handleApprove(period)}>{isApproving ? 'Approving...' : '✓ Approve'}</button>)}
                    {period.status === 'APPROVED' && (<button type="button" className="btn-sm btn-warning" disabled={isFinalizing} onClick={() => handleFinalize(period)}>{isFinalizing ? 'Finalizing...' : '🔒 Finalize'}</button>)}
                    <button type="button" className="action-link-btn" onClick={() => onViewRuns(period.id)} style={{ color: '#64748b' }}>Runs</button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>

      {/* Create Modal */}
      {showCreateModal && (
        <div className="modal-backdrop">
          <div className="employee-modal" style={{ maxWidth: '520px' }}>
            <div className="modal-header">
              <div>
                <h2>Create Payroll Period</h2>
                <p>Define a new monthly payroll cycle.</p>
              </div>
              <button type="button" className="close-btn" onClick={() => setShowCreateModal(false)}>×</button>
            </div>
            {createError && <div className="error-message">{createError}</div>}
            <form onSubmit={handleCreateSubmit}>
              <div className="form-grid" style={{ gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                <label>
                  Year *
                  <select value={formYear} onChange={e => handleMonthYearChange(Number(e.target.value), formMonth)}>
                    {[currentYear - 1, currentYear, currentYear + 1].map(y => (<option key={y} value={y}>{y}</option>))}
                  </select>
                </label>
                <label>
                  Month *
                  <select value={formMonth} onChange={e => handleMonthYearChange(formYear, Number(e.target.value))}>
                    {MONTH_NAMES.map((m, idx) => (<option key={m} value={idx + 1}>{m}</option>))}
                  </select>
                </label>
                <label>
                  Start Date *
                  <input type="date" required value={formStartDate} onChange={e => setFormStartDate(e.target.value)} />
                </label>
                <label>
                  End Date *
                  <input type="date" required value={formEndDate} onChange={e => setFormEndDate(e.target.value)} />
                </label>
              </div>
              <div style={{ marginTop: '16px', padding: '12px', background: '#f8fafc', borderRadius: '6px', fontSize: '12px', color: '#64748b' }}>
                ℹ️ Creating a period starts it in <strong>DRAFT</strong> status.
              </div>
              <div className="modal-actions">
                <button type="button" className="secondary-btn" onClick={() => setShowCreateModal(false)}>Cancel</button>
                <button type="submit" className="primary-btn" disabled={createSubmitting}>{createSubmitting ? 'Creating...' : 'Create Period'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
