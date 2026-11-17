import { useState, useEffect, useMemo } from 'react';
import {
  getPeriodRuns,
  type PayrollPeriodDto,
  type PayrollRunDto,
} from '../services/payrollService';
import { formatCurrency, formatDateTime, getMonthName, getStatusBadge } from './payrollUtils';

type Props = {
  periods: PayrollPeriodDto[];
  selectedPeriodId?: string | null;
  onSelectPeriod: (periodId: string) => void;
  onViewRunDetails: (runId: string) => void;
  onNavigateToPreview: (periodId: string) => void;
};

export default function PayrollRunHistory({
  periods,
  selectedPeriodId,
  onSelectPeriod,
  onViewRunDetails,
  onNavigateToPreview,
}: Props) {
  // Current active period
  const activePeriod = useMemo(() => {
    if (selectedPeriodId) {
      return periods.find(p => p.id === selectedPeriodId) || null;
    }
    return periods[0] || null;
  }, [periods, selectedPeriodId]);

  const [runs, setRuns] = useState<PayrollRunDto[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string>('');

  // Filters
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('');

  useEffect(() => {
    if (!activePeriod) {
      setRuns([]);
      return;
    }

    let isMounted = true;
    async function loadRuns() {
      try {
        setLoading(true);
        setError('');
        const res = await getPeriodRuns(activePeriod!.id);
        if (isMounted) {
          if (res.success && res.data) {
            setRuns(res.data);
          } else {
            setError(res.message || 'Unable to load payroll runs.');
          }
        }
      } catch (err) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'Failed to fetch payroll runs.');
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    loadRuns();

    return () => {
      isMounted = false;
    };
  }, [activePeriod]);

  // Filtered runs
  const filteredRuns = useMemo(() => {
    return runs.filter(run => {
      if (statusFilter && run.status !== statusFilter) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const match = `run ${run.runNumber} ${run.runType} ${run.status}`.toLowerCase();
        if (!match.includes(q)) return false;
      }
      return true;
    });
  }, [runs, statusFilter, searchQuery]);

  return (
    <div>
      {/* Header & Controls */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 700, margin: '0 0 4px', color: '#1e293b' }}>
            Payroll Run History
          </h2>
          <p style={{ margin: 0, fontSize: '13px', color: '#64748b' }}>
            Audit log of all calculation batches, iterations, employee headcounts and payout summaries.
          </p>
        </div>

        {/* Period Selector */}
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <label style={{ fontSize: '13px', fontWeight: 600, color: '#475569' }}>
            Cycle:
          </label>
          <select
            value={activePeriod?.id || ''}
            onChange={e => onSelectPeriod(e.target.value)}
            style={{ padding: '9px 14px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '13px', fontWeight: 600, background: 'white' }}
          >
            {periods.map(p => (
              <option key={p.id} value={p.id}>
                {getMonthName(p.month)} {p.year} ({p.status})
              </option>
            ))}
          </select>

          {activePeriod && !['FINALIZED', 'PAID'].includes(activePeriod.status) && (
            <button
              type="button"
              className="secondary-btn"
              onClick={() => onNavigateToPreview(activePeriod.id)}
            >
              👁 Preview / Run
            </button>
          )}
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      {/* Filter toolbar */}
      <section className="table-card" style={{ marginBottom: '20px', padding: '16px 20px' }}>
        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ flex: '1 1 240px' }}>
            <input
              type="search"
              placeholder="Search by run # or type..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '13px' }}
            />
          </div>

          <div style={{ minWidth: '160px' }}>
            <select
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value)}
              style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '13px', background: 'white' }}
            >
              <option value="">All Statuses</option>
              <option value="COMPLETED">Completed</option>
              <option value="PROCESSING">Processing</option>
              <option value="FAILED">Failed</option>
            </select>
          </div>

          {(searchQuery || statusFilter) && (
            <button
              type="button"
              className="secondary-btn"
              onClick={() => {
                setSearchQuery('');
                setStatusFilter('');
              }}
              style={{ padding: '9px 14px' }}
            >
              Reset
            </button>
          )}
        </div>
      </section>

      {/* Runs Table */}
      <section className="table-card">
        <div className="table-header">
          <div>
            <h3>
              {activePeriod ? `${getMonthName(activePeriod.month)} ${activePeriod.year} Runs` : 'Calculation Runs'}
            </h3>
            <p>Showing {filteredRuns.length} run iteration(s)</p>
          </div>
        </div>

        {loading ? (
          <div className="empty-state">Loading payroll run history...</div>
        ) : filteredRuns.length === 0 ? (
          <div className="empty-state">
            <h3>No payroll runs found</h3>
            <p>
              {runs.length === 0
                ? 'No calculations have been executed for this payroll period yet.'
                : 'No runs match your search query.'}
            </p>
            {activePeriod && ['DRAFT', 'PROCESSING'].includes(activePeriod.status) && (
              <button
                type="button"
                className="primary-btn"
                onClick={() => onNavigateToPreview(activePeriod.id)}
              >
                Go to Preview & Run Payroll
              </button>
            )}
          </div>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>RUN NUMBER</th>
                  <th>TYPE</th>
                  <th>HEADCOUNT</th>
                  <th>GROSS TOTAL</th>
                  <th>DEDUCTIONS</th>
                  <th>NET PAYOUT</th>
                  <th>TIMESTAMP</th>
                  <th>STATUS</th>
                  <th>ACTIONS</th>
                </tr>
              </thead>
              <tbody>
                {filteredRuns.map(run => {
                  const badge = getStatusBadge(run.status);
                  return (
                    <tr key={run.runId}>
                      <td>
                        <strong>Run #{run.runNumber}</strong>
                      </td>
                      <td>
                        <span style={{ fontSize: '12px', fontWeight: 600, color: '#475569' }}>
                          {run.runType}
                        </span>
                      </td>
                      <td>
                        <strong>{run.processedEmployees}</strong>
                        <small style={{ display: 'block', color: '#94a3b8' }}>employees</small>
                      </td>
                      <td>{formatCurrency(run.totalGross)}</td>
                      <td style={{ color: '#b91c1c' }}>
                        {formatCurrency(run.totalDeductions)}
                      </td>
                      <td>
                        <strong style={{ color: '#4338ca', fontSize: '13px' }}>
                          {formatCurrency(run.totalNet)}
                        </strong>
                      </td>
                      <td>
                        <small style={{ color: '#64748b' }}>
                          {formatDateTime(run.completedAt || run.createdAt)}
                        </small>
                      </td>
                      <td>
                        <span className={badge.className}>{badge.label}</span>
                      </td>
                      <td>
                        <button
                          type="button"
                          className="primary-btn btn-sm"
                          onClick={() => onViewRunDetails(run.runId)}
                        >
                          View Details →
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
