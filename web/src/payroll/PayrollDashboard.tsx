import { useState } from 'react';
import type { PayrollPeriodDto, PayrollRunDto } from '../services/payrollService';
import { formatCurrency, formatDate, formatDateTime, getMonthName, getStatusBadge } from './payrollUtils';

type Props = {
  periods: PayrollPeriodDto[];
  recentRuns: PayrollRunDto[];
  loading: boolean;
  onSelectPeriod: (periodId: string) => void;
  onNavigateTab: (tab: 'dashboard' | 'periods' | 'preview' | 'history' | 'structures', periodId?: string) => void;
  onOpenCreatePeriodModal: () => void;
  onCalculatePayroll: (periodId: string) => Promise<void>;
  onViewRunDetails: (runId: string) => void;
  onRefresh: () => void;
};

export default function PayrollDashboard({
  periods,
  recentRuns,
  loading,
  onNavigateTab,
  onOpenCreatePeriodModal,
  onCalculatePayroll,
  onViewRunDetails,
  onRefresh,
}: Props) {
  const [calculatingId, setCalculatingId] = useState<string | null>(null);

  // Current active period is the most recent period (or the latest draft/review/approved period)
  const currentPeriod = periods.length > 0 ? periods[0] : null;

  async function handleQuickCalculate(period: PayrollPeriodDto) {
    if (!window.confirm(`Are you sure you want to run payroll calculation for ${getMonthName(period.month)} ${period.year}? This will calculate salary for all eligible employees based on attendance, approved leaves and statutory rules.`)) {
      return;
    }

    try {
      setCalculatingId(period.id);
      await onCalculatePayroll(period.id);
    } finally {
      setCalculatingId(null);
    }
  }

  // Calculate high-level metrics from current period or overall summary
  const totalEmployees = currentPeriod?.totalEmployees || 0;
  const grossSalary = currentPeriod?.totalGrossPay || 0;
  const netPayroll = currentPeriod?.totalNetPay || 0;
  const employerCost = currentPeriod?.totalEmployerCost || 0;
  const totalDeductions = Math.max(0, grossSalary - netPayroll);

  const currentStatusBadge = currentPeriod ? getStatusBadge(currentPeriod.status) : null;

  return (
    <div>
      {/* Top Welcome / Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 700, margin: '0 0 4px', color: '#1e293b' }}>
            Payroll Overview
          </h2>
          <p style={{ margin: 0, fontSize: '13px', color: '#64748b' }}>
            Comprehensive compensation, deductions and statutory disbursement summary.
          </p>
        </div>

        {/* Quick Action Buttons */}
        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
          <button
            type="button"
            className="secondary-btn"
            onClick={onRefresh}
            disabled={loading}
            style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
          >
            ↻ Refresh
          </button>

          <button
            type="button"
            className="secondary-btn"
            onClick={() => onNavigateTab('structures')}
          >
            ⚙ Salary Structures
          </button>

          <button
            type="button"
            className="secondary-btn"
            onClick={() => onNavigateTab('history')}
          >
            📋 All Runs
          </button>

          <button
            type="button"
            className="primary-btn"
            onClick={onOpenCreatePeriodModal}
            style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
          >
            + Create Period
          </button>
        </div>
      </div>

      {/* KPI Stats Grid */}
      <section className="stats-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
        {/* Metric 1: Total Employees */}
        <div className="stat-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <span>Eligible Employees</span>
            <span style={{ fontSize: '18px' }}>👥</span>
          </div>
          <h2>{loading ? '...' : totalEmployees}</h2>
          <small>{currentPeriod ? `${getMonthName(currentPeriod.month)} ${currentPeriod.year}` : 'No active cycle'}</small>
        </div>

        {/* Metric 2: Gross Salary */}
        <div className="stat-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <span>Total Gross Salary</span>
            <span style={{ fontSize: '18px' }}>💰</span>
          </div>
          <h2>{loading ? '...' : formatCurrency(grossSalary)}</h2>
          <small>Pro-rated base earnings & allowances</small>
        </div>

        {/* Metric 3: Total Deductions */}
        <div className="stat-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <span>Total Deductions</span>
            <span style={{ fontSize: '18px' }}>📉</span>
          </div>
          <h2 style={{ color: '#b91c1c' }}>{loading ? '...' : formatCurrency(totalDeductions)}</h2>
          <small>EPF, ESI, Professional Tax & LOP</small>
        </div>

        {/* Metric 4: Net Payroll */}
        <div className="stat-card" style={{ borderColor: '#c7d2fe' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <span style={{ color: '#4338ca', fontWeight: 600 }}>Net Disbursable</span>
            <span style={{ fontSize: '18px' }}>💵</span>
          </div>
          <h2 style={{ color: '#4338ca' }}>{loading ? '...' : formatCurrency(netPayroll)}</h2>
          <small>Payable net salary to employees</small>
        </div>

        {/* Metric 5: Employer Statutory Cost */}
        <div className="stat-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <span>Employer Contributions</span>
            <span style={{ fontSize: '18px' }}>🏢</span>
          </div>
          <h2>{loading ? '...' : formatCurrency(employerCost)}</h2>
          <small>PF & ESI statutory employer liability</small>
        </div>
      </section>

      {/* Current Cycle Banner */}
      {currentPeriod && (
        <section
          className="table-card"
          style={{
            marginBottom: '24px',
            background: 'linear-gradient(135deg, #ffffff 0%, #f8fafc 100%)',
            border: '1px solid #e2e8f0',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
                <span style={{ fontSize: '12px', fontWeight: 600, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                  Current Payroll Period
                </span>
                {currentStatusBadge && (
                  <span className={currentStatusBadge.className}>
                    {currentStatusBadge.label}
                  </span>
                )}
              </div>
              <h3 style={{ fontSize: '20px', fontWeight: 700, margin: '0 0 6px', color: '#1e293b' }}>
                {getMonthName(currentPeriod.month)} {currentPeriod.year}
              </h3>
              <p style={{ margin: 0, fontSize: '13px', color: '#64748b' }}>
                Cycle window: <strong>{formatDate(currentPeriod.startDate)}</strong> to <strong>{formatDate(currentPeriod.endDate)}</strong>
              </p>
            </div>

            <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
              {/* If Draft or Processing, offer Preview and Run */}
              {['DRAFT', 'PROCESSING'].includes(currentPeriod.status) && (
                <>
                  <button
                    type="button"
                    className="secondary-btn"
                    onClick={() => onNavigateTab('preview', currentPeriod.id)}
                  >
                    👁 Preview Calculation
                  </button>
                  <button
                    type="button"
                    className="primary-btn"
                    disabled={calculatingId === currentPeriod.id}
                    onClick={() => handleQuickCalculate(currentPeriod)}
                  >
                    {calculatingId === currentPeriod.id ? 'Processing...' : '▶ Run Payroll'}
                  </button>
                </>
              )}

              {/* If Review or Approved, offer Preview or Period management */}
              {['REVIEW', 'APPROVED'].includes(currentPeriod.status) && (
                <>
                  <button
                    type="button"
                    className="secondary-btn"
                    onClick={() => onNavigateTab('preview', currentPeriod.id)}
                  >
                    👁 View Preview
                  </button>
                  <button
                    type="button"
                    className="primary-btn"
                    onClick={() => onNavigateTab('periods')}
                  >
                    Manage Approval / Finalize →
                  </button>
                </>
              )}

              {currentPeriod.status === 'FINALIZED' && (
                <button
                  type="button"
                  className="secondary-btn"
                  onClick={() => onNavigateTab('history', currentPeriod.id)}
                >
                  📜 View Finalized Runs
                </button>
              )}
            </div>
          </div>
        </section>
      )}

      {/* Main Grid: Monthly Summary & Recent Runs */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: '24px' }}>
        {/* Section 1: Monthly Payroll Summary */}
        <section className="table-card">
          <div className="table-header">
            <div>
              <h3>Monthly Payroll Cycles</h3>
              <p>Recent periods and disbursement totals</p>
            </div>
            <button
              type="button"
              className="action-link-btn"
              onClick={() => onNavigateTab('periods')}
            >
              View All Cycles →
            </button>
          </div>

          {loading ? (
            <div className="empty-state">Loading payroll periods...</div>
          ) : periods.length === 0 ? (
            <div className="empty-state">
              <p>No payroll periods have been created yet.</p>
              <button
                type="button"
                className="primary-btn"
                onClick={onOpenCreatePeriodModal}
              >
                + Create First Period
              </button>
            </div>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>PERIOD</th>
                    <th>EMPLOYEES</th>
                    <th>GROSS</th>
                    <th>NET PAY</th>
                    <th>STATUS</th>
                    <th>ACTION</th>
                  </tr>
                </thead>
                <tbody>
                  {periods.slice(0, 5).map(period => {
                    const badge = getStatusBadge(period.status);
                    return (
                      <tr key={period.id}>
                        <td>
                          <strong>{getMonthName(period.month)} {period.year}</strong>
                          <br />
                          <small style={{ color: '#94a3b8' }}>
                            {formatDate(period.startDate)} - {formatDate(period.endDate)}
                          </small>
                        </td>
                        <td>{period.totalEmployees || 0}</td>
                        <td>{formatCurrency(period.totalGrossPay)}</td>
                        <td>
                          <strong style={{ color: '#4338ca' }}>
                            {formatCurrency(period.totalNetPay)}
                          </strong>
                        </td>
                        <td>
                          <span className={badge.className}>{badge.label}</span>
                        </td>
                        <td>
                          <button
                            type="button"
                            className="action-link-btn"
                            onClick={() => onNavigateTab('preview', period.id)}
                          >
                            Preview
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

        {/* Section 2: Recent Payroll Runs */}
        <section className="table-card">
          <div className="table-header">
            <div>
              <h3>Recent Payroll Runs</h3>
              <p>Calculation batches and processed runs</p>
            </div>
            <button
              type="button"
              className="action-link-btn"
              onClick={() => onNavigateTab('history')}
            >
              View Run History →
            </button>
          </div>

          {loading ? (
            <div className="empty-state">Loading recent runs...</div>
          ) : recentRuns.length === 0 ? (
            <div className="empty-state">
              <p>No payroll calculation runs recorded yet.</p>
              <small style={{ color: '#94a3b8' }}>
                Run calculations from the Preview or Periods tab to generate runs.
              </small>
            </div>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>RUN</th>
                    <th>PROCESSED</th>
                    <th>NET TOTAL</th>
                    <th>DATE</th>
                    <th>STATUS</th>
                    <th>ACTION</th>
                  </tr>
                </thead>
                <tbody>
                  {recentRuns.slice(0, 5).map(run => {
                    const badge = getStatusBadge(run.status);
                    return (
                      <tr key={run.runId}>
                        <td>
                          <strong>Run #{run.runNumber}</strong>
                          <br />
                          <small style={{ color: '#64748b' }}>{run.runType}</small>
                        </td>
                        <td>{run.processedEmployees} employees</td>
                        <td>
                          <strong style={{ color: '#15803d' }}>
                            {formatCurrency(run.totalNet)}
                          </strong>
                        </td>
                        <td>
                          <small>{formatDateTime(run.completedAt || run.createdAt)}</small>
                        </td>
                        <td>
                          <span className={badge.className}>{badge.label}</span>
                        </td>
                        <td>
                          <button
                            type="button"
                            className="action-link-btn"
                            onClick={() => onViewRunDetails(run.runId)}
                          >
                            Details
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
    </div>
  );
}
