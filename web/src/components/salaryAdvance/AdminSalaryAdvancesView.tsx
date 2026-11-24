import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  listAllForTenant,
  approveAdvance,
  rejectAdvance,
  type SalaryAdvanceResponseDto,
} from '../../services/salaryAdvanceService';
import { getEmployees, type Employee } from '../../services/employeeService';
import { formatCurrency, formatDate } from '../../payroll/payrollUtils';
import { AdminSalaryAdvanceDetailModal } from './AdminSalaryAdvanceDetailModal';
import './salaryAdvance.css';

interface AdminSalaryAdvancesViewProps {
  onBack?: () => void;
  onNavigateToMyAdvances?: () => void;
}

type AdminFilterTab = 'ALL' | 'REQUESTED' | 'APPROVED' | 'ACTIVE' | 'FULLY_REPAID' | 'REJECTED';

interface ConfirmActionState {
  type: 'APPROVE' | 'REJECT';
  advance: SalaryAdvanceResponseDto;
}

export const AdminSalaryAdvancesView: React.FC<AdminSalaryAdvancesViewProps> = ({
  onBack,
  onNavigateToMyAdvances,
}) => {
  const [advances, setAdvances] = useState<SalaryAdvanceResponseDto[]>([]);
  const [employees, setEmployees] = useState<Map<string, Employee>>(new Map());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [activeTab, setActiveTab] = useState<AdminFilterTab>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedAdvance, setSelectedAdvance] = useState<SalaryAdvanceResponseDto | null>(null);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);

  // Confirmation modal state
  const [confirmAction, setConfirmAction] = useState<ConfirmActionState | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const [advancesRes, employeesRes] = await Promise.all([
        listAllForTenant(),
        getEmployees('', 0, 200).catch(() => ({ success: false, data: { content: [] } })),
      ]);

      if (advancesRes.success && advancesRes.data) {
        setAdvances(advancesRes.data);
      } else {
        setAdvances([]);
      }

      if (employeesRes.success && employeesRes.data?.content) {
        const empMap = new Map<string, Employee>();
        for (const emp of employeesRes.data.content) {
          empMap.set(emp.id, emp);
        }
        setEmployees(empMap);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Unable to load salary advances');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'REQUESTED':
        return 'Pending Approval';
      case 'APPROVED':
        return 'Approved';
      case 'ACTIVE':
        return 'Active Repayment';
      case 'PARTIALLY_REPAID':
        return 'Partially Repaid';
      case 'FULLY_REPAID':
        return 'Fully Repaid';
      case 'REJECTED':
        return 'Rejected';
      case 'CANCELLED':
        return 'Cancelled';
      default:
        return status;
    }
  };

  const getInitials = (firstName?: string, lastName?: string) => {
    const f = firstName?.[0] || '';
    const l = lastName?.[0] || '';
    return (f + l).toUpperCase() || 'EM';
  };

  // Metrics summary
  const metrics = useMemo(() => {
    const totalCount = advances.length;
    let pendingCount = 0;
    let totalDisbursed = 0;
    let totalOutstanding = 0;

    for (const adv of advances) {
      if (adv.status === 'REQUESTED') {
        pendingCount++;
      }
      if (adv.approvedAmount && adv.status !== 'REJECTED' && adv.status !== 'CANCELLED') {
        totalDisbursed += adv.approvedAmount;
      }
      if (adv.outstandingBalance && adv.status !== 'REJECTED' && adv.status !== 'CANCELLED') {
        totalOutstanding += adv.outstandingBalance;
      }
    }

    return { totalCount, pendingCount, totalDisbursed, totalOutstanding };
  }, [advances]);

  // Tab counts
  const tabCounts = useMemo(() => {
    const counts = {
      ALL: advances.length,
      REQUESTED: 0,
      APPROVED: 0,
      ACTIVE: 0,
      FULLY_REPAID: 0,
      REJECTED: 0,
    };

    for (const adv of advances) {
      if (adv.status === 'REQUESTED') {
        counts.REQUESTED++;
      } else if (adv.status === 'APPROVED') {
        counts.APPROVED++;
      } else if (['ACTIVE', 'PARTIALLY_REPAID'].includes(adv.status)) {
        counts.ACTIVE++;
      } else if (adv.status === 'FULLY_REPAID') {
        counts.FULLY_REPAID++;
      } else if (['REJECTED', 'CANCELLED'].includes(adv.status)) {
        counts.REJECTED++;
      }
    }

    return counts;
  }, [advances]);

  // Filtered & searched advances
  const filteredAdvances = useMemo(() => {
    return advances.filter(adv => {
      // Tab filter (resolving PENDING -> REQUESTED)
      if (activeTab === 'REQUESTED' && adv.status !== 'REQUESTED') return false;
      if (activeTab === 'APPROVED' && adv.status !== 'APPROVED') return false;
      if (activeTab === 'ACTIVE' && !['ACTIVE', 'PARTIALLY_REPAID'].includes(adv.status)) return false;
      if (activeTab === 'FULLY_REPAID' && adv.status !== 'FULLY_REPAID') return false;
      if (activeTab === 'REJECTED' && !['REJECTED', 'CANCELLED'].includes(adv.status)) return false;

      // Text search filter
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const emp = employees.get(adv.employeeId);
        const nameMatch = emp ? `${emp.firstName} ${emp.lastName}`.toLowerCase().includes(q) : false;
        const codeMatch = emp?.employeeCode?.toLowerCase().includes(q) || false;
        const deptMatch = emp?.departmentName?.toLowerCase().includes(q) || false;
        const notesMatch = adv.notes?.toLowerCase().includes(q) || false;
        const idMatch = adv.id.toLowerCase().includes(q);

        return nameMatch || codeMatch || deptMatch || notesMatch || idMatch;
      }

      return true;
    });
  }, [advances, activeTab, searchQuery, employees]);

  // Handle Approve Confirmation
  const handleConfirmApprove = async () => {
    if (!confirmAction || confirmAction.type !== 'APPROVE') return;
    const { advance } = confirmAction;

    try {
      setActionLoading(true);
      setError(null);

      const res = await approveAdvance(advance.id);
      if (!res.success) {
        throw new Error(res.message || 'Failed to approve advance');
      }

      const emp = employees.get(advance.employeeId);
      const empName = emp ? `${emp.firstName} ${emp.lastName}` : `Employee #${advance.employeeId.slice(0, 8)}`;
      setSuccessMessage(`Salary advance of ${formatCurrency(advance.requestedAmount)} for ${empName} approved successfully.`);

      setConfirmAction(null);
      await loadData();

      setTimeout(() => {
        setSuccessMessage(null);
      }, 6000);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Error approving salary advance');
    } finally {
      setActionLoading(false);
    }
  };

  // Handle Reject Confirmation
  const handleConfirmReject = async () => {
    if (!confirmAction || confirmAction.type !== 'REJECT') return;
    const { advance } = confirmAction;

    try {
      setActionLoading(true);
      setError(null);

      const res = await rejectAdvance(advance.id);
      if (!res.success) {
        throw new Error(res.message || 'Failed to reject advance');
      }

      const emp = employees.get(advance.employeeId);
      const empName = emp ? `${emp.firstName} ${emp.lastName}` : `Employee #${advance.employeeId.slice(0, 8)}`;
      setSuccessMessage(`Salary advance request for ${empName} has been rejected.`);

      setConfirmAction(null);
      await loadData();

      setTimeout(() => {
        setSuccessMessage(null);
      }, 6000);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Error rejecting salary advance');
    } finally {
      setActionLoading(false);
    }
  };

  const handleOpenDetail = (adv: SalaryAdvanceResponseDto) => {
    setSelectedAdvance(adv);
    setIsDetailModalOpen(true);
  };

  return (
    <div className="sa-container">
      {/* View Header */}
      <div className="view-header">
        <div>
          <h2>💼 Salary Advance Management (HR & Admin)</h2>
          <p className="subtitle">
            Review and authorize salary advance requests, track tenant loan disbursements, and monitor repayment progress.
          </p>
        </div>
        <div className="header-actions">
          {onNavigateToMyAdvances && (
            <button className="secondary-btn" onClick={onNavigateToMyAdvances}>
              👤 My Salary Advances
            </button>
          )}
          {onBack && (
            <button className="secondary-btn" onClick={onBack}>
              ← Back to Dashboard
            </button>
          )}
          <button className="primary-btn" onClick={loadData} disabled={loading}>
            {loading ? 'Refreshing...' : '↻ Refresh'}
          </button>
        </div>
      </div>

      {/* Success banner */}
      {successMessage && (
        <div className="success-banner" role="status">
          {successMessage}
        </div>
      )}

      {/* Error banner */}
      {error && (
        <div className="error-banner" role="alert" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>{error}</span>
          <button className="secondary-btn" onClick={loadData} style={{ padding: '4px 10px', fontSize: '12px' }}>
            Try Again
          </button>
        </div>
      )}

      {/* Stats Cards */}
      <div className="sa-stats-grid">
        <div className="sa-stat-card">
          <span>Total Requests</span>
          <h2>{metrics.totalCount}</h2>
          <small>{metrics.pendingCount} awaiting approval</small>
        </div>
        <div className="sa-stat-card">
          <span>Pending Approvals</span>
          <h2 style={{ color: metrics.pendingCount > 0 ? '#d97706' : '#10b981' }}>
            {metrics.pendingCount}
          </h2>
          <small>Action required</small>
        </div>
        <div className="sa-stat-card">
          <span>Total Disbursed</span>
          <h2>{formatCurrency(metrics.totalDisbursed)}</h2>
          <small>Approved advances amount</small>
        </div>
        <div className="sa-stat-card">
          <span>Outstanding Balance</span>
          <h2 style={{ color: metrics.totalOutstanding > 0 ? '#ef4444' : '#10b981' }}>
            {formatCurrency(metrics.totalOutstanding)}
          </h2>
          <small>Remaining across tenant</small>
        </div>
      </div>

      {/* Search & Tabs */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '8px' }}>
        <div className="sa-tabs" style={{ marginBottom: 0 }}>
          <button
            className={`sa-tab-btn ${activeTab === 'ALL' ? 'active' : ''}`}
            onClick={() => setActiveTab('ALL')}
          >
            All Requests <span className="badge-count">{tabCounts.ALL}</span>
          </button>
          <button
            className={`sa-tab-btn ${activeTab === 'REQUESTED' ? 'active' : ''}`}
            onClick={() => setActiveTab('REQUESTED')}
          >
            Pending <span className="badge-count" style={{ backgroundColor: tabCounts.REQUESTED > 0 ? '#fef3c7' : undefined, color: tabCounts.REQUESTED > 0 ? '#92400e' : undefined }}>{tabCounts.REQUESTED}</span>
          </button>
          <button
            className={`sa-tab-btn ${activeTab === 'APPROVED' ? 'active' : ''}`}
            onClick={() => setActiveTab('APPROVED')}
          >
            Approved <span className="badge-count">{tabCounts.APPROVED}</span>
          </button>
          <button
            className={`sa-tab-btn ${activeTab === 'ACTIVE' ? 'active' : ''}`}
            onClick={() => setActiveTab('ACTIVE')}
          >
            Active <span className="badge-count">{tabCounts.ACTIVE}</span>
          </button>
          <button
            className={`sa-tab-btn ${activeTab === 'FULLY_REPAID' ? 'active' : ''}`}
            onClick={() => setActiveTab('FULLY_REPAID')}
          >
            Fully Repaid <span className="badge-count">{tabCounts.FULLY_REPAID}</span>
          </button>
          <button
            className={`sa-tab-btn ${activeTab === 'REJECTED' ? 'active' : ''}`}
            onClick={() => setActiveTab('REJECTED')}
          >
            Rejected <span className="badge-count">{tabCounts.REJECTED}</span>
          </button>
        </div>

        <div className="sa-search-bar" style={{ marginBottom: 0 }}>
          <input
            type="search"
            placeholder="Search employee, ID, notes..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
          />
        </div>
      </div>

      {/* Table Card */}
      <div className="table-card">
        {loading && advances.length === 0 ? (
          <div className="loading-state">
            <p>Loading salary advances and employee directory...</p>
          </div>
        ) : advances.length === 0 ? (
          <div className="empty-state">
            <h3>No Salary Advances</h3>
            <p>No salary advances have been submitted by employees in this company yet.</p>
          </div>
        ) : filteredAdvances.length === 0 ? (
          <div className="empty-state">
            <h3>No Matching Requests</h3>
            <p>No salary advances match the selected filter or search query.</p>
          </div>
        ) : (
          <div className="table-scroll">
            <table className="sa-table">
              <thead>
                <tr>
                  <th>Employee</th>
                  <th>Request Date</th>
                  <th>Requested Amount</th>
                  <th>Approved Amount</th>
                  <th>Schedule</th>
                  <th>Monthly Deduction</th>
                  <th>Outstanding Balance</th>
                  <th>Status</th>
                  <th>Reason</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredAdvances.map(advance => {
                  const emp = employees.get(advance.employeeId);
                  const empName = emp ? `${emp.firstName} ${emp.lastName}` : `Employee #${advance.employeeId.slice(0, 8)}`;

                  return (
                    <tr key={advance.id}>
                      <td>
                        <div className="sa-employee-cell">
                          <div className="sa-employee-avatar">
                            {getInitials(emp?.firstName, emp?.lastName)}
                          </div>
                          <div className="sa-employee-info">
                            <strong>{empName}</strong>
                            <small>
                              {emp?.employeeCode ? `${emp.employeeCode} • ` : ''}
                              {emp?.departmentName || emp?.designation || 'Staff'}
                            </small>
                          </div>
                        </div>
                      </td>
                      <td>
                        <strong>{formatDate(advance.requestDate)}</strong>
                      </td>
                      <td>{formatCurrency(advance.requestedAmount)}</td>
                      <td>
                        {advance.approvedAmount && advance.approvedAmount > 0 ? (
                          <strong style={{ color: '#10b981' }}>
                            {formatCurrency(advance.approvedAmount)}
                          </strong>
                        ) : (
                          <span style={{ color: '#94a3b8' }}>—</span>
                        )}
                      </td>
                      <td>
                        {advance.installmentCount ? `${advance.installmentCount} mo` : '1 mo'}
                      </td>
                      <td>
                        {advance.installmentAmount && advance.installmentAmount > 0
                          ? `${formatCurrency(advance.installmentAmount)}/mo`
                          : advance.requestedAmount && advance.installmentCount
                          ? `Est. ${formatCurrency(advance.requestedAmount / advance.installmentCount)}/mo`
                          : '—'}
                      </td>
                      <td>
                        <strong style={{ color: (advance.outstandingBalance || 0) > 0 ? '#ef4444' : '#10b981' }}>
                          {formatCurrency(advance.outstandingBalance)}
                        </strong>
                      </td>
                      <td>
                        <span className={`sa-badge sa-badge-${advance.status}`}>
                          {getStatusLabel(advance.status)}
                        </span>
                      </td>
                      <td style={{ maxWidth: '160px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={advance.notes || ''}>
                        {advance.notes || '—'}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', gap: '6px' }}>
                          {advance.status === 'REQUESTED' ? (
                            <>
                              <button
                                className="sa-btn-approve"
                                title="Approve Request"
                                onClick={() => setConfirmAction({ type: 'APPROVE', advance })}
                              >
                                Approve
                              </button>
                              <button
                                className="sa-btn-reject"
                                title="Reject Request"
                                onClick={() => setConfirmAction({ type: 'REJECT', advance })}
                              >
                                Reject
                              </button>
                              <button
                                className="sa-action-btn"
                                title="View Details"
                                onClick={() => handleOpenDetail(advance)}
                              >
                                Details
                              </button>
                            </>
                          ) : (
                            <button
                              className="sa-action-btn"
                              onClick={() => handleOpenDetail(advance)}
                            >
                              View Details
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

      {/* Admin Advance Detail Modal */}
      <AdminSalaryAdvanceDetailModal
        isOpen={isDetailModalOpen}
        advance={selectedAdvance}
        employee={selectedAdvance ? employees.get(selectedAdvance.employeeId) : undefined}
        onClose={() => {
          setIsDetailModalOpen(false);
          setSelectedAdvance(null);
        }}
        onApprove={(adv) => setConfirmAction({ type: 'APPROVE', advance: adv })}
        onReject={(adv) => setConfirmAction({ type: 'REJECT', advance: adv })}
      />

      {/* Confirmation Dialog Modal */}
      {confirmAction && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="sa-confirm-card">
            <h3 className="sa-confirm-title">
              {confirmAction.type === 'APPROVE' ? '✓ Approve Salary Advance' : '✕ Reject Salary Advance'}
            </h3>

            <div className="sa-confirm-body">
              {confirmAction.type === 'APPROVE' ? (
                <>
                  Are you sure you want to approve this salary advance request?
                  <div className="sa-confirm-details">
                    <div>
                      <strong>Employee:</strong>{' '}
                      {employees.get(confirmAction.advance.employeeId)?.firstName}{' '}
                      {employees.get(confirmAction.advance.employeeId)?.lastName || confirmAction.advance.employeeId}
                    </div>
                    <div>
                      <strong>Requested Amount:</strong> {formatCurrency(confirmAction.advance.requestedAmount)}
                    </div>
                    <div>
                      <strong>Repayment Schedule:</strong> {confirmAction.advance.installmentCount || 1} Months
                    </div>
                    <div>
                      <strong>Estimated Monthly Deduction:</strong>{' '}
                      {formatCurrency(confirmAction.advance.requestedAmount / (confirmAction.advance.installmentCount || 1))} / month
                    </div>
                  </div>
                  Automated monthly payroll deductions will be scheduled starting the next active pay run.
                </>
              ) : (
                <>
                  Are you sure you want to reject this salary advance request?
                  <div className="sa-confirm-details">
                    <div>
                      <strong>Employee:</strong>{' '}
                      {employees.get(confirmAction.advance.employeeId)?.firstName}{' '}
                      {employees.get(confirmAction.advance.employeeId)?.lastName || confirmAction.advance.employeeId}
                    </div>
                    <div>
                      <strong>Requested Amount:</strong> {formatCurrency(confirmAction.advance.requestedAmount)}
                    </div>
                  </div>
                  The request will be marked as REJECTED and no repayments will be scheduled.
                </>
              )}
            </div>

            <div className="modal-actions" style={{ justifyContent: 'flex-end', gap: '10px' }}>
              <button
                type="button"
                className="secondary-btn"
                onClick={() => setConfirmAction(null)}
                disabled={actionLoading}
              >
                Cancel
              </button>
              {confirmAction.type === 'APPROVE' ? (
                <button
                  type="button"
                  className="sa-btn-approve"
                  style={{ padding: '9px 18px', fontSize: '13px' }}
                  onClick={handleConfirmApprove}
                  disabled={actionLoading}
                >
                  {actionLoading ? 'Approving...' : 'Confirm Approval'}
                </button>
              ) : (
                <button
                  type="button"
                  className="sa-btn-reject"
                  style={{ padding: '9px 18px', fontSize: '13px' }}
                  onClick={handleConfirmReject}
                  disabled={actionLoading}
                >
                  {actionLoading ? 'Rejecting...' : 'Confirm Rejection'}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
