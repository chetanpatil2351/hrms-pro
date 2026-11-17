import React, { useState, useEffect, useMemo } from 'react';
import {
  previewPayroll,
  type PayrollPeriodDto,
  type PayrollPreviewDto,
} from '../services/payrollService';
import { formatCurrency, getMonthName } from './payrollUtils';

type Props = {
  periods: PayrollPeriodDto[];
  selectedPeriodId?: string | null;
  onSelectPeriod: (id: string) => void;
  onCalculatePayroll: (periodId: string) => Promise<void>;
  onNavigateToPeriods: () => void;
};

export default function PayrollPreview({
  periods,
  selectedPeriodId,
  onSelectPeriod,
  onCalculatePayroll,
  onNavigateToPeriods,
}: Props) {
  // Find current selected period or default to the first non-finalized period
  const activePeriod = useMemo(() => {
    if (selectedPeriodId) {
      return periods.find(p => p.id === selectedPeriodId) || null;
    }
    // Default to first period that is not finalized
    return periods.find(p => !['FINALIZED', 'PAID'].includes(p.status)) || periods[0] || null;
  }, [periods, selectedPeriodId]);

  const [previewData, setPreviewData] = useState<PayrollPreviewDto | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string>('');
  const [calculating, setCalculating] = useState<boolean>(false);

  // Filters & Pagination
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [expandedEmployeeIds, setExpandedEmployeeIds] = useState<Set<string>>(new Set());
  const [currentPage, setCurrentPage] = useState<number>(0);
  const pageSize = 10;

  // Load preview data when activePeriod changes
  useEffect(() => {
    if (!activePeriod) {
      setPreviewData(null);
      return;
    }

    if (['FINALIZED', 'PAID'].includes(activePeriod.status)) {
      setError(`Cannot preview payroll for a finalized or paid period (${activePeriod.status}). Please view the historical run details instead.`);
      setPreviewData(null);
      return;
    }

    let isMounted = true;
    async function loadPreview() {
      try {
        setLoading(true);
        setError('');
        const res = await previewPayroll(activePeriod!.id);
        if (isMounted) {
          if (res.success && res.data) {
            setPreviewData(res.data);
            setCurrentPage(0);
          } else {
            setError(res.message || 'Unable to generate payroll preview.');
          }
        }
      } catch (err) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'Failed to fetch payroll preview.');
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    loadPreview();

    return () => {
      isMounted = false;
    };
  }, [activePeriod?.id, activePeriod?.status]);

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
    if (!previewData?.employeeSummaries) return;
    if (expandedEmployeeIds.size === filteredEmployees.length) {
      setExpandedEmployeeIds(new Set());
    } else {
      setExpandedEmployeeIds(new Set(filteredEmployees.map(e => e.employeeId)));
    }
  }

  // Filter employees
  const filteredEmployees = useMemo(() => {
    if (!previewData?.employeeSummaries) return [];
    if (!searchQuery.trim()) return previewData.employeeSummaries;
    const q = searchQuery.toLowerCase();
    return previewData.employeeSummaries.filter(emp =>
      emp.employeeName?.toLowerCase().includes(q) ||
      emp.employeeCode?.toLowerCase().includes(q)
    );
  }, [previewData?.employeeSummaries, searchQuery]);

  // Paginated employees
  const paginatedEmployees = useMemo(() => {
    const start = currentPage * pageSize;
    return filteredEmployees.slice(start, start + pageSize);
  }, [filteredEmployees, currentPage, pageSize]);

  const totalPages = Math.ceil(filteredEmployees.length / pageSize);

  // Handle Calculate Action
  async function handleRunCalculation() {
    if (!activePeriod) return;
    const name = `${getMonthName(activePeriod.month)} ${activePeriod.year}`;
    if (!window.confirm(`Execute and calculate official payroll for ${name}?\n\nThis will generate an official Payroll Run and transition the period to 'REVIEW'.`)) {
      return;
    }

    try {
      setCalculating(true);
      await onCalculatePayroll(activePeriod.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Calculation failed.');
    } finally {
      setCalculating(false);
    }
  }

  return (
    <div>
      {/* Header & Period Switcher */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 700, margin: '0 0 4px', color: '#1e293b' }}>
            Payroll Preview & Simulation
          </h2>
          <p style={{ margin: 0, fontSize: '13px', color: '#64748b' }}>
            Simulate pro-rated salary calculations, loss of pay (LOP) deductions, and statutory taxes before committing.
          </p>
        </div>

        {/* Period Selector Dropdown */}
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

          {activePeriod && ['DRAFT', 'PROCESSING'].includes(activePeriod.status) && (
            <button
              type="button"
              className="primary-btn"
              disabled={calculating || loading}
              onClick={handleRunCalculation}
              style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
            >
              {calculating ? 'Processing...' : '▶ Execute Payroll Run'}
            </button>
          )}
        </div>
      </div>

      {error && (
        <div className="error-message" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>{error}</span>
          <button
            type="button"
            className="action-link-btn"
            onClick={onNavigateToPeriods}
            style={{ color: '#b91c1c', fontWeight: 600 }}
          >
            Go to Periods →
          </button>
        </div>
      )}

      {/* Summary KPI Cards if preview data is loaded */}
      {previewData && (
        <section className="stats-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))' }}>
          <div className="stat-card">
            <span>Simulated Employees</span>
            <h2>{previewData.processedEmployees}</h2>
            <small>Active & eligible workforce</small>
          </div>

          <div className="stat-card">
            <span>Estimated Gross</span>
            <h2>{formatCurrency(previewData.totalGross)}</h2>
            <small>Earnings before deductions</small>
          </div>

          <div className="stat-card">
            <span>Total Deductions</span>
            <h2 style={{ color: '#b91c1c' }}>{formatCurrency(previewData.totalDeductions)}</h2>
            <small>EPF, ESI, PT & Unpaid Leaves</small>
          </div>

          <div className="stat-card" style={{ borderColor: '#c7d2fe' }}>
            <span style={{ color: '#4338ca', fontWeight: 600 }}>Net Disbursable</span>
            <h2 style={{ color: '#4338ca' }}>{formatCurrency(previewData.totalNet)}</h2>
            <small>Net take-home pay</small>
          </div>
        </section>
      )}

      {/* Table Section */}
      <section className="table-card">
        {/* Table Filter Bar */}
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

          <div style={{ display: 'flex', gap: '8px' }}>
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
        </div>

        {/* Loading State */}
        {loading ? (
          <div className="empty-state" style={{ padding: '40px 20px' }}>
            <div className="skeleton-box" style={{ width: '60px', height: '60px', margin: '0 auto 16px', borderRadius: '50%' }} />
            <h3 style={{ margin: '0 0 8px' }}>Simulating deterministic payroll calculation...</h3>
            <p style={{ margin: 0, color: '#64748b' }}>
              Fetching attendance, calculating payable days, pro-rating allowances, and applying statutory slabs.
            </p>
          </div>
        ) : !previewData || previewData.employeeSummaries.length === 0 ? (
          <div className="empty-state">
            <h3>No employee calculations available</h3>
            <p>Ensure active employees are assigned a Salary Structure template and joining date is within or before this cycle.</p>
          </div>
        ) : (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th style={{ width: '40px' }}></th>
                    <th>EMPLOYEE</th>
                    <th>CALENDAR DAYS</th>
                    <th>PAYABLE DAYS</th>
                    <th>UNPAID LEAVE (LOP)</th>
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
                    
                    // Categorize line items
                    const earnings = emp.lineItems.filter(li => li.type === 'EARNING');
                    const deductions = emp.lineItems.filter(li => li.type === 'DEDUCTION');
                    const employerCosts = emp.lineItems.filter(li => li.type === 'EMPLOYER_CONTRIBUTION');

                    return (
                      <React.Fragment key={emp.employeeId}>
                        <tr style={{ background: isExpanded ? '#f8fafc' : undefined }}>
                          {/* Toggle arrow */}
                          <td>
                            <button
                              type="button"
                              onClick={() => toggleEmployeeExpand(emp.employeeId)}
                              style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '14px', color: '#64748b' }}
                            >
                              {isExpanded ? '▼' : '▶'}
                            </button>
                          </td>

                          {/* Employee Name & Code */}
                          <td>
                            <div className="employee-cell">
                              <div className="employee-avatar">
                                {emp.employeeName?.[0] || 'E'}
                              </div>
                              <div>
                                <strong>{emp.employeeName}</strong>
                                <small>{emp.employeeCode}</small>
                              </div>
                            </div>
                          </td>

                          {/* Calendar Days */}
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

                          {/* Gross Earnings */}
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

                          {/* Expand button */}
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

                        {/* Expandable Line-Items Breakdown Row */}
                        {isExpanded && (
                          <tr>
                            <td colSpan={10} style={{ padding: '0 16px 16px', background: '#f8fafc', borderBottom: '2px solid #e2e8f0' }}>
                              <div className="breakdown-drawer">
                                <h4 style={{ margin: '0 0 12px', fontSize: '13px', color: '#1e293b' }}>
                                  Detailed Salary Breakdown for {emp.employeeName} ({emp.employeeCode})
                                </h4>

                                <div className="breakdown-columns">
                                  {/* Col 1: Earnings */}
                                  <div className="breakdown-col">
                                    <div className="breakdown-col-header">
                                      <h4>Earnings (Additions)</h4>
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

                                  {/* Col 2: Deductions */}
                                  <div className="breakdown-col">
                                    <div className="breakdown-col-header">
                                      <h4>Deductions (Employee)</h4>
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

                                  {/* Col 3: Employer Contributions & Net */}
                                  <div className="breakdown-col">
                                    <div className="breakdown-col-header">
                                      <h4>Employer Contributions</h4>
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
                                      <span>Net Take Home:</span>
                                      <span>{formatCurrency(emp.netSalary)}</span>
                                    </div>
                                  </div>
                                </div>
                              </div>
                            </td>
                          </tr>
                        )}
                      </React.Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>

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
          </>
        )}
      </section>
    </div>
  );
}
