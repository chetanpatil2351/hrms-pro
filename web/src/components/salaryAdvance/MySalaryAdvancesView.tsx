import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  getMyAdvances,
  type SalaryAdvanceResponseDto,
} from '../../services/salaryAdvanceService';
import { formatCurrency, formatDate } from '../../payroll/payrollUtils';
import { SalaryAdvanceRequestModal } from './SalaryAdvanceRequestModal';
import { SalaryAdvanceDetailModal } from './SalaryAdvanceDetailModal';
import './salaryAdvance.css';

interface MySalaryAdvancesViewProps {
  onBack?: () => void;
  onNavigateToAdmin?: () => void;
}

type FilterTab = 'ALL' | 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'REJECTED';

export const MySalaryAdvancesView: React.FC<MySalaryAdvancesViewProps> = ({
  onBack,
  onNavigateToAdmin,
}) => {
  const [advances, setAdvances] = useState<SalaryAdvanceResponseDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [activeTab, setActiveTab] = useState<FilterTab>('ALL');
  const [isRequestModalOpen, setIsRequestModalOpen] = useState(false);
  const [selectedAdvance, setSelectedAdvance] = useState<SalaryAdvanceResponseDto | null>(null);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await getMyAdvances();
      if (res.success && res.data) {
        setAdvances(res.data);
      } else {
        setAdvances([]);
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

  // Metrics summary
  const metrics = useMemo(() => {
    const totalCount = advances.length;
    let pendingCount = 0;
    let totalApproved = 0;
    let totalOutstanding = 0;

    for (const adv of advances) {
      if (adv.status === 'REQUESTED') {
        pendingCount++;
      }
      if (adv.approvedAmount && adv.status !== 'REJECTED' && adv.status !== 'CANCELLED') {
        totalApproved += adv.approvedAmount;
      }
      if (adv.outstandingBalance && adv.status !== 'REJECTED' && adv.status !== 'CANCELLED') {
        totalOutstanding += adv.outstandingBalance;
      }
    }

    return { totalCount, pendingCount, totalApproved, totalOutstanding };
  }, [advances]);

  // Tab counts
  const tabCounts = useMemo(() => {
    const counts = {
      ALL: advances.length,
      PENDING: 0,
      ACTIVE: 0,
      COMPLETED: 0,
      REJECTED: 0,
    };

    for (const adv of advances) {
      if (adv.status === 'REQUESTED') {
        counts.PENDING++;
      } else if (['APPROVED', 'ACTIVE', 'PARTIALLY_REPAID'].includes(adv.status)) {
        counts.ACTIVE++;
      } else if (adv.status === 'FULLY_REPAID') {
        counts.COMPLETED++;
      } else if (['REJECTED', 'CANCELLED'].includes(adv.status)) {
        counts.REJECTED++;
      }
    }

    return counts;
  }, [advances]);

  // Filtered advances
  const filteredAdvances = useMemo(() => {
    switch (activeTab) {
      case 'PENDING':
        return advances.filter(a => a.status === 'REQUESTED');
      case 'ACTIVE':
        return advances.filter(a => ['APPROVED', 'ACTIVE', 'PARTIALLY_REPAID'].includes(a.status));
      case 'COMPLETED':
        return advances.filter(a => a.status === 'FULLY_REPAID');
      case 'REJECTED':
        return advances.filter(a => ['REJECTED', 'CANCELLED'].includes(a.status));
      case 'ALL':
      default:
        return advances;
    }
  }, [advances, activeTab]);

  const handleRequestSuccess = (newAdvance: SalaryAdvanceResponseDto) => {
    setSuccessMessage(`Salary advance request for ${formatCurrency(newAdvance.requestedAmount)} submitted successfully!`);
    loadData();
    setTimeout(() => {
      setSuccessMessage(null);
    }, 6000);
  };

  const handleOpenDetail = (advance: SalaryAdvanceResponseDto) => {
    setSelectedAdvance(advance);
    setIsDetailModalOpen(true);
  };

  return (
    <div className="sa-container">
      {/* View Header */}
      <div className="view-header">
        <div>
          <h2>💵 My Salary Advances</h2>
          <p className="subtitle">
            Request salary advances, track approval status, and monitor repayment schedules.
          </p>
        </div>
        <div className="header-actions">
          {onNavigateToAdmin && (
            <button className="secondary-btn" onClick={onNavigateToAdmin}>
              💼 Admin Approvals →
            </button>
          )}
          {onBack && (
            <button className="secondary-btn" onClick={onBack}>
              ← Back to Dashboard
            </button>
          )}
          <button className="primary-btn" onClick={() => setIsRequestModalOpen(true)}>
            + Request Advance
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
          <small>{metrics.pendingCount} pending review</small>
        </div>
        <div className="sa-stat-card">
          <span>Total Approved</span>
          <h2>{formatCurrency(metrics.totalApproved)}</h2>
          <small>Across approved advances</small>
        </div>
        <div className="sa-stat-card">
          <span>Outstanding Balance</span>
          <h2 style={{ color: metrics.totalOutstanding > 0 ? '#ef4444' : '#10b981' }}>
            {formatCurrency(metrics.totalOutstanding)}
          </h2>
          <small>Remaining repayment amount</small>
        </div>
        <div className="sa-stat-card">
          <span>Active Repayments</span>
          <h2>{tabCounts.ACTIVE}</h2>
          <small>Currently in deduction</small>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="sa-tabs">
        <button
          className={`sa-tab-btn ${activeTab === 'ALL' ? 'active' : ''}`}
          onClick={() => setActiveTab('ALL')}
        >
          All Requests <span className="badge-count">{tabCounts.ALL}</span>
        </button>
        <button
          className={`sa-tab-btn ${activeTab === 'PENDING' ? 'active' : ''}`}
          onClick={() => setActiveTab('PENDING')}
        >
          Pending Review <span className="badge-count">{tabCounts.PENDING}</span>
        </button>
        <button
          className={`sa-tab-btn ${activeTab === 'ACTIVE' ? 'active' : ''}`}
          onClick={() => setActiveTab('ACTIVE')}
        >
          Active Repayment <span className="badge-count">{tabCounts.ACTIVE}</span>
        </button>
        <button
          className={`sa-tab-btn ${activeTab === 'COMPLETED' ? 'active' : ''}`}
          onClick={() => setActiveTab('COMPLETED')}
        >
          Fully Repaid <span className="badge-count">{tabCounts.COMPLETED}</span>
        </button>
        <button
          className={`sa-tab-btn ${activeTab === 'REJECTED' ? 'active' : ''}`}
          onClick={() => setActiveTab('REJECTED')}
        >
          Rejected <span className="badge-count">{tabCounts.REJECTED}</span>
        </button>
      </div>

      {/* Main Content Table Card */}
      <div className="table-card">
        {loading && advances.length === 0 ? (
          <div className="loading-state">
            <p>Loading your salary advances...</p>
          </div>
        ) : advances.length === 0 ? (
          <div className="empty-state">
            <h3>No Salary Advances Yet</h3>
            <p>You have not submitted any salary advance requests.</p>
            <button className="primary-btn" onClick={() => setIsRequestModalOpen(true)}>
              + Request Salary Advance
            </button>
          </div>
        ) : filteredAdvances.length === 0 ? (
          <div className="empty-state">
            <h3>No Requests Found</h3>
            <p>No salary advances match the selected filter tab.</p>
          </div>
        ) : (
          <div className="table-scroll">
            <table className="sa-table">
              <thead>
                <tr>
                  <th>Request Date</th>
                  <th>Requested Amount</th>
                  <th>Approved Amount</th>
                  <th>Schedule</th>
                  <th>Monthly Deduction</th>
                  <th>Outstanding Balance</th>
                  <th>Status</th>
                  <th>Reason</th>
                  <th style={{ textAlign: 'right' }}>Action</th>
                </tr>
              </thead>
              <tbody>
                {filteredAdvances.map(advance => (
                  <tr key={advance.id}>
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
                      {advance.installmentCount
                        ? `${advance.installmentCount} mo`
                        : '1 mo'}
                    </td>
                    <td>
                      {advance.installmentAmount && advance.installmentAmount > 0
                        ? `${formatCurrency(advance.installmentAmount)}/mo`
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
                    <td style={{ maxWidth: '180px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={advance.notes || ''}>
                      {advance.notes || '—'}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <button
                        className="sa-action-btn"
                        onClick={() => handleOpenDetail(advance)}
                      >
                        View Details
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Request Advance Modal */}
      <SalaryAdvanceRequestModal
        isOpen={isRequestModalOpen}
        onClose={() => setIsRequestModalOpen(false)}
        onSuccess={handleRequestSuccess}
      />

      {/* Advance Detail Modal */}
      <SalaryAdvanceDetailModal
        isOpen={isDetailModalOpen}
        advance={selectedAdvance}
        onClose={() => {
          setIsDetailModalOpen(false);
          setSelectedAdvance(null);
        }}
      />
    </div>
  );
};
