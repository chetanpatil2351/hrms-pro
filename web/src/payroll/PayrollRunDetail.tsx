import { useState, useEffect, useMemo, Fragment } from 'react';
import {
  getRunDetails,
  type PayrollPeriodDto,
  type PayrollRunResultDto,
} from '../services/payrollService';
import { formatCurrency, getMonthName, getStatusBadge } from './payrollUtils';

type Props = {
  runId: string;
  periods: PayrollPeriodDto[];
  onBack: () => void;
  onApprovePeriod: (periodId: string) => Promise<void>;
  onFinalizePeriod: (periodId: string) => Promise<void>;
};

export default function PayrollRunDetail({
  runId,
  periods,
  onBack,
  onApprovePeriod,
  onFinalizePeriod,
}: Props) {
  const [runDetails, setRunDetails] = useState<PayrollRunResultDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string>('');
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);

  // Filters & Expansion
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [expandedEmployeeIds, setExpandedEmployeeIds] = useState<Set<string>>(new Set());
  const [currentPage, setCurrentPage] = useState<number>(0);
  const pageSize = 10;

  // Load Run Details
  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      try {
        setLoading(true);
        setError('');
        const res = await getRunDetails(runId);
        if (isMounted) {
          if (res.success && res.data) {
            setRunDetails(res.data);
          } else {
            setError(res.message || 'Unable to load run details.');
          }
        }
      } catch (err) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'Failed to fetch payroll run details.');
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    loadData();

    return () => {
      isMounted = false;
    };
  }, [runId]);

  // Match corresponding period to check if Approve / Finalize actions can be performed
  const matchingPeriod = useMemo(() => {
    if (!runDetails?.periodId) return null;
    return periods.find(p => p.id === runDetails.periodId) || null;
  }, [periods, runDetails?.periodId]);

  // Toggle single employee breakdown
  function toggleEmployeeExpand(id: string) {
    setExpandedEmployeeIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  // Toggle expand all
  function toggleExpandAll() {
    if (!runDetails?.employeeSummaries) return;
    if (expandedEmployeeIds.size === filteredEmployees.length) {
      setExpandedEmployeeIds(new Set());
    } else {
      setExpandedEmployeeIds(new Set(filteredEmployees.map(e => e.employeeId)));
    }
  }

  // Filtered employees
  const filteredEmployees = useMemo(() => {
    if (!runDetails?.employeeSummaries) return [];
    if (!searchQuery.trim()) return runDetails.employeeSummaries;
    const q = searchQuery.toLowerCase();
    return runDetails.employeeSummaries.filter(emp =>
      emp.employeeName?.toLowerCase().includes(q) ||
      emp.employeeCode?.toLowerCase().includes(q)
    );
  }, [runDetails?.employeeSummaries, searchQuery]);

  // Paginated employees
  const paginatedEmployees = useMemo(() => {
    const start = currentPage * pageSize;
    return filteredEmployees.slice(start, start + pageSize);
  }, [filteredEmployees, currentPage, pageSize]);

  const totalPages = Math.ceil(filteredEmployees.length / pageSize);

  // Workflow Handlers
  async function handleApprove() {
    if (!matchingPeriod) return;
    const name = `${getMonthName(matchingPeriod.month)} ${matchingPeriod.year}`;
    if (!window.confirm(`Approve payroll for ${name} based on Run #${runDetails?.runNumber}?\n\nThis moves the period into 'APPROVED' status.`)) {
      return;
    }

    try {
      setActionInProgress('approve');
      await onApprovePeriod(matchingPeriod.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Approval failed');
    } finally {
      setActionInProgress(null);
    }
  }

  async function handleFinalize() {
    if (!matchingPeriod) return;
    const name = `${getMonthName(matchingPeriod.month)} ${matchingPeriod.year}`;
    if (!window.confirm(`Finalize and lock payroll for ${name} based on Run #${runDetails?.runNumber}?\n\nThis freezes the calculation records and generates official employee payslips.`)) {
      return;
    }

    try {
      setActionInProgress('finalize');
      await onFinalizePeriod(matchingPeriod.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Finalization failed');
    } finally {
      setActionInProgress(null);
    }
  }

  return (
    <div>
      {/* Top Breadcrumb & Actions */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <button
            type="button"
            className="action-link-btn"
            onClick={onBack}
            style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', fontWeight: 600, color: '#6257e8', marginBottom: '6px' }}
          >
            ← Back to Run History
          </button>
          <h2 style={{ fontSize: '20px', fontWeight: 700, margin: '0 0 4px', color: '#1e293b' }}>
            Payroll Run Details {runDetails ? `— Run #${runDetails.runNumber}` : ''}
          </h2>
          <p style={{ margin: 0, fontSize: '13px', color: '#64748b' }}>
            {matchingPeriod ? `Cycle: ${getMonthName(matchingPeriod.month)} ${matchingPeriod.year}` : 'Detailed batch calculation breakdown'}
          </p>
        </div>

        {/* Workflow Action Buttons */}
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
          <button
            type="button"
            className="secondary-btn"
            onClick={() => window.print()}
          >
            🖨 Print / Export
          </button>

          {/* If Period is in REVIEW, allow HR/Admin to approve */}
          {matchingPeriod?.status === 'REVIEW' && (
            <button
              type="button"
              className="primary-btn btn-success"
              disabled={actionInProgress === 'approve'}
              onClick={handleApprove}
            >
              {actionInProgress === 'approve' ? 'Approving...' : '✓ Approve Payroll'}
            </button>
          )}

          {/* If Period is in APPROVED, allow Admin to finalize */}
          {matchingPeriod?.status === 'APPROVED' && (
            <button
              type="button"
              className="primary-btn btn-warning"
              disabled={actionInProgress === 'finalize'}
              onClick={handleFinalize}
            >
              {actionInProgress === 'finalize' ? 'Finalizing...' : '🔒 Finalize Payroll'}
            </button>
          )}

          {matchingPeriod?.status === 'FINALIZED' && (
            <span className="status finalized" style={{ fontSize: '12px', padding: '6px 12px' }}>
              ✓ Finalized & Payslips Generated
            </span>
          )}
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      {/* Loading Skeleton */}
      {loading ? (
        <div className="empty-state">
          <div className="skeleton-box" style={{ width: '50px', height: '50px', margin: '0 auto 12px', borderRadius: '50%' }} />
          <h3>Loading run summary and line items...</h3>
        </div>
      ) : !runDetails ? (
        <div className="empty-state">
          <h3>Run record not found</h3>
          <p>The requested payroll run could not be retrieved from the server.</p>
          <button type="button" className="primary-btn" onClick={onBack}>
            Back to Run History
          </button>
        </div>
      ) : (
        <>
          {/* Summary KPI Cards */}
          <section className="stats-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))' }}>
            <div className="stat-card">
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Run Status</span>
                <span className={getStatusBadge(runDetails.status).className}>
                  {getStatusBadge(runDetails.status).label}
                </span>
              </div>
              <h2>Run #{runDetails.runNumber}</h2>
              <small>Type: {runDetails.runType}</small>
            </div>

            <div className="stat-card">
              <span>Processed Employees</span>
              <h2>{runDetails.processedEmployees}</h2>
              <small>All calculated employees</small>
            </div>

            <div className="stat-card">
              <span>Total Gross Pay</span>
              <h2>{formatCurrency(runDetails.totalGross)}</h2>
              <small>Base earnings & allowances</small>
            </div>

            <div className="stat-card">
              <span>Total Deductions</span>
              <h2 style={{ color: '#b91c1c' }}>{formatCurrency(runDetails.totalDeductions)}</h2>
              <small>EPF, ESI, PT, TDS & LOP</small>
            </div>

            <div className="stat-card" style={{ borderColor: '#c7d2fe' }}>
              <span style={{ color: '#4338ca', fontWeight: 600 }}>Total Net Disbursable</span>
              <h2 style={{ color: '#4338ca' }}>{formatCurrency(runDetails.totalNet)}</h2>
              <small>Disbursable payout amount</small>
            </div>
          </section>

          {/* Employee-wise Table */}
          <section className="table-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', flexWrap: 'wrap', gap: '12px' }}>
              <div style={{ flex: '1 1 260px' }}>
                <input
                  type="search"
                  placeholder="Filter by employee name or code..."
                  value={searchQuery}
                  onChange={e => {
                    setSearchQuery(e.target.value);
                    setCurrentPage(0);
                  }}
                  style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '13px' }}
                />
              </div>

              {filteredEmployees.length > 0 && (
                <button
                  type="button"
                  className="secondary-btn"
                  onClick={toggleExpandAll}
                  style={{ fontSize: '12px', padding: '8px 14px' }}
                >
                  {expandedEmployeeIds.size === filteredEmployees.length ? '▲ Collapse All' : '▼ Expand All Breakdowns'}
                </button>
              )}
            </div>

            {filteredEmployees.length === 0 ? (
              <div className="empty-state">
                <p>No employees match your search query.</p>
              </div>
            ) : (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th style={{ width: '40px' }}></th>
                      <th>EMPLOYEE</th>
                      <th>TOTAL DAYS</th>
                      <th>PAYABLE DAYS</th>
                      <th>UNPAID LEAVE</th>
                      <th>GROSS EARNINGS</th>
                      <th>DEDUCTIONS</th>
                      <th>EMPLOYER COST</th>
                      <th>NET SALARY</th>
                      <th>BREAKDOWN</th>
                    </tr>
                  </thead>
                  <tbody>
                    {paginatedEmployees.map(emp => {
                      const isExpanded = expandedEmployeeIds.has(emp.employeeId);
                      const earnings = emp.lineItems.filter(li => li.type === 'EARNING');
                      const deductions = emp.lineItems.filter(li => li.type === 'DEDUCTION');
                      const employerCosts = emp.lineItems.filter(li => li.type === 'EMPLOYER_CONTRIBUTION');

                      return (
                        <Fragment key={emp.employeeId}>
                          <tr style={{ background: isExpanded ? '#f8fafc' : undefined }}>
                            {/* Toggle */}
                            <td>
                              <button
                                type="button"
                                onClick={() => toggleEmployeeExpand(emp.employeeId)}
                                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '14px', color: '#64748b' }}
                              >
                                {isExpanded ? '▼' : '▶'}
                              </button>
                            </td>

                            {/* Employee */}
                            <td>
                              <div className="employee-cell">
                                <div className="employee-avatar">{emp.employeeName?.[0] || 'E'}</div>
                                <div>
                                  <strong>{emp.employeeName}</strong>
                                  <small>{emp.employeeCode}</small>
                                </div>
                              </div>
                            </td>

                            {/* Total Days */}
                            <td>{emp.totalDays} days</td>

                            {/* Payable Days */}
                            <td>
                              <strong style={{ color: emp.payableDays < emp.totalDays ? '#b45309' : '#1e293b' }}>
                                {emp.payableDays} days
                              </strong>
                            </td>

                            {/* Unpaid Leave */}
                            <td>
                              {emp.unpaidLeaveDays > 0 ? (
                                <span style={{ color: '#b91c1c', fontWeight: 600 }}>
                                  {emp.unpaidLeaveDays} days LOP
                                </span>
                              ) : (
                                <span style={{ color: '#64748b' }}>0 days</span>
                              )}
                            </td>

                            {/* Gross */}
                            <td>{formatCurrency(emp.grossEarnings)}</td>

                            {/* Deductions */}
                            <td style={{ color: '#b91c1c' }}>
                              {formatCurrency(emp.totalDeductions)}
                            </td>

                            {/* Employer Contribution */}
                            <td>{formatCurrency(emp.employerContributions)}</td>

                            {/* Net Salary */}
                            <td>
                              <strong style={{ color: '#4338ca', fontSize: '13px' }}>
                                {formatCurrency(emp.netSalary)}
                              </strong>
                            </td>

                            {/* Action */}
                            <td>
                              <button
                                type="button"
                                className="action-link-btn"
                                onClick={() => toggleEmployeeExpand(emp.employeeId)}
                              >
                                {isExpanded ? 'Hide' : 'Breakdown'}
                              </button>
                            </td>
                          </tr>

                          {/* Expanded Breakdown */}
                          {isExpanded && (
                            <tr>
                              <td colSpan={10} style={{ padding: '0 16px 16px', background: '#f8fafc', borderBottom: '2px solid #e2e8f0' }}>
                                <div className="breakdown-drawer">
                                  <h4 style={{ margin: '0 0 12px', fontSize: '13px', color: '#1e293b' }}>
                                    Itemized Calculation Audit for {emp.employeeName} ({emp.employeeCode})
                                  </h4>

                                  <div className="breakdown-columns">
                                    {/* Earnings */}
                                    <div className="breakdown-col">
                                      <div className="breakdown-col-header">
                                        <h4>Earnings</h4>
                                        <span style={{ fontSize: '11px', color: '#16a34a', fontWeight: 600 }}>
                                          {formatCurrency(emp.grossEarnings)}
                                        </span>
                                      </div>
                                      {earnings.length === 0 ? (
                                        <div style={{ color: '#94a3b8', fontSize: '12px' }}>No earning components</div>
                                      ) : (
                                        earnings.map((item, idx) => (
                                          <div key={idx} className="breakdown-item">
                                            <span>{item.componentName} <small style={{ color: '#94a3b8' }}>({item.componentCode})</small></span>
                                            <strong>{formatCurrency(item.amount)}</strong>
                                          </div>
                                        ))
                                      )}
                                      <div className="breakdown-total" style={{ color: '#16a34a' }}>
                                        <span>Total Gross:</span>
                                        <span>{formatCurrency(emp.grossEarnings)}</span>
                                      </div>
                                    </div>

                                    {/* Deductions */}
                                    <div className="breakdown-col">
                                      <div className="breakdown-col-header">
                                        <h4>Deductions</h4>
                                        <span style={{ fontSize: '11px', color: '#b91c1c', fontWeight: 600 }}>
                                          {formatCurrency(emp.totalDeductions)}
                                        </span>
                                      </div>
                                      {deductions.length === 0 ? (
                                        <div style={{ color: '#94a3b8', fontSize: '12px' }}>No deductions applied</div>
                                      ) : (
                                        deductions.map((item, idx) => (
                                          <div key={idx} className="breakdown-item">
                                            <span>{item.componentName} <small style={{ color: '#94a3b8' }}>({item.componentCode})</small></span>
                                            <strong style={{ color: '#b91c1c' }}>-{formatCurrency(item.amount)}</strong>
                                          </div>
                                        ))
                                      )}
                                      <div className="breakdown-total" style={{ color: '#b91c1c' }}>
                                        <span>Total Deductions:</span>
                                        <span>-{formatCurrency(emp.totalDeductions)}</span>
                                      </div>
                                    </div>

                                    {/* Employer Contribution */}
                                    <div className="breakdown-col">
                                      <div className="breakdown-col-header">
                                        <h4>Employer Contribution</h4>
                                        <span style={{ fontSize: '11px', color: '#4338ca', fontWeight: 600 }}>
                                          {formatCurrency(emp.employerContributions)}
                                        </span>
                                      </div>
                                      {employerCosts.length === 0 ? (
                                        <div style={{ color: '#94a3b8', fontSize: '12px' }}>No employer contributions</div>
                                      ) : (
                                        employerCosts.map((item, idx) => (
                                          <div key={idx} className="breakdown-item">
                                            <span>{item.componentName} <small style={{ color: '#94a3b8' }}>({item.componentCode})</small></span>
                                            <strong>{formatCurrency(item.amount)}</strong>
                                          </div>
                                        ))
                                      )}
                                      <div className="breakdown-total" style={{ color: '#4338ca', marginTop: '14px', borderTop: '2px solid #c7d2fe' }}>
                                        <span>Net Payout:</span>
                                        <span>{formatCurrency(emp.netSalary)}</span>
                                      </div>
                                    </div>
                                  </div>
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="pagination" style={{ marginTop: '16px' }}>
                <span>
                  Page {currentPage + 1} of {totalPages} | {filteredEmployees.length} total employees
                </span>
                <div>
                  <button
                    type="button"
                    className="secondary-btn"
                    disabled={currentPage === 0}
                    onClick={() => setCurrentPage(prev => Math.max(0, prev - 1))}
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    className="secondary-btn"
                    disabled={currentPage >= totalPages - 1}
                    onClick={() => setCurrentPage(prev => Math.min(totalPages - 1, prev + 1))}
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}
