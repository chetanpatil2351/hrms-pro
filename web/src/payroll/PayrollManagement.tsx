import { useState, useEffect } from 'react';
import {
  getPeriods,
  getPeriodRuns,
  createPeriod,
  calculatePayroll,
  approvePeriod,
  finalizePeriod,
  type PayrollPeriodDto,
  type PayrollRunDto,
} from '../services/payrollService';

import PayrollDashboard from './PayrollDashboard';
import PayrollPeriodManagement from './PayrollPeriodManagement';
import PayrollPreview from './PayrollPreview';
import PayrollRunHistory from './PayrollRunHistory';
import PayrollRunDetail from './PayrollRunDetail';
import SalaryStructures from './SalaryStructures';

type Props = {
  tenantName: string;
  onBack: () => void;
  onLogout: () => void;
  onNavigateToAttendance?: () => void;
  onNavigateToShifts?: () => void;
};

export default function PayrollManagement({
  tenantName,
  onBack,
  onLogout,
  onNavigateToAttendance,
  onNavigateToShifts,
}: Props) {
  // Navigation tabs
  const [activeTab, setActiveTab] = useState<
    'dashboard' | 'periods' | 'preview' | 'history' | 'run-detail' | 'structures'
  >('dashboard');

  // Selected entities
  const [selectedPeriodId, setSelectedPeriodId] = useState<string | null>(null);
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);

  // Global payroll data
  const [periods, setPeriods] = useState<PayrollPeriodDto[]>([]);
  const [recentRuns, setRecentRuns] = useState<PayrollRunDto[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string>('');
  const [success, setSuccess] = useState<string>('');

  // Create Period Modal visibility
  const [showCreateModal, setShowCreateModal] = useState<boolean>(false);

  // Load periods and recent runs
  async function loadData() {
    try {
      setLoading(true);
      setError('');

      const periodRes = await getPeriods(0, 50, 'startDate,desc');

      if (!periodRes.success) {
        throw new Error(periodRes.message || 'Unable to load payroll periods');
      }

      const fetchedPeriods = periodRes.data?.content || [];
      setPeriods(fetchedPeriods);

      // If we don't have a selected period yet, select the most recent one
      if (fetchedPeriods.length > 0) {
        const targetPeriod = selectedPeriodId
          ? fetchedPeriods.find(p => p.id === selectedPeriodId) || fetchedPeriods[0]
          : fetchedPeriods[0];
        
        setSelectedPeriodId(targetPeriod.id);

        // Load runs for this period
        try {
          const runRes = await getPeriodRuns(targetPeriod.id);
          if (runRes.success && runRes.data) {
            setRecentRuns(runRes.data);
          }
        } catch {
          // ignore run errors if none exist
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch payroll data');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  // Handlers
  async function handleCreatePeriod(payload: {
    year: number;
    month: number;
    startDate?: string;
    endDate?: string;
  }) {
    try {
      setError('');
      setSuccess('');
      const res = await createPeriod(payload);
      if (!res.success) throw new Error(res.message);

      setSuccess(`Payroll period for ${payload.month}/${payload.year} created successfully.`);
      await loadData();
      if (res.data?.id) {
        setSelectedPeriodId(res.data.id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create period');
      throw err;
    }
  }

  async function handleCalculatePayroll(periodId: string) {
    try {
      setError('');
      setSuccess('');
      const res = await calculatePayroll(periodId);
      if (!res.success) throw new Error(res.message);

      setSuccess(`Payroll calculated successfully! Processed ${res.data.processedEmployees} employees.`);
      await loadData();
      if (res.data?.runId) {
        setSelectedRunId(res.data.runId);
        setActiveTab('run-detail');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Payroll calculation failed');
      throw err;
    }
  }

  async function handleApprovePeriod(periodId: string) {
    try {
      setError('');
      setSuccess('');
      const res = await approvePeriod(periodId);
      if (!res.success) throw new Error(res.message);

      setSuccess('Payroll period has been approved successfully.');
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Approval failed');
      throw err;
    }
  }

  async function handleFinalizePeriod(periodId: string) {
    try {
      setError('');
      setSuccess('');
      const res = await finalizePeriod(periodId);
      if (!res.success) throw new Error(res.message);

      setSuccess('Payroll period finalized and locked! Official employee payslips have been generated.');
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Finalization failed');
      throw err;
    }
  }

  function handleNavigateTab(tab: 'dashboard' | 'periods' | 'preview' | 'history' | 'structures', periodId?: string) {
    if (periodId) {
      setSelectedPeriodId(periodId);
    }
    setActiveTab(tab);
    setError('');
    setSuccess('');
  }

  function handleViewRunDetails(runId: string) {
    setSelectedRunId(runId);
    setActiveTab('run-detail');
    setError('');
    setSuccess('');
  }

  return (
    <div className="app-layout">
      {/* Sidebar Navigation */}
      <aside className="sidebar">
        <div className="brand">HRMS<span>Pro</span></div>
        <p className="nav-heading">WORKSPACE</p>
        <nav>
          <a className="nav-item" href="#" onClick={e => { e.preventDefault(); onBack(); }}>
            Overview
          </a>
          <a className="nav-item" href="#" onClick={e => { e.preventDefault(); onBack(); }}>
            Employees
          </a>
          <a
            className="nav-item"
            href="#"
            onClick={e => {
              e.preventDefault();
              if (onNavigateToAttendance) onNavigateToAttendance();
            }}
          >
            Attendance
          </a>
          <a
            className="nav-item"
            href="#"
            onClick={e => {
              e.preventDefault();
              if (onNavigateToShifts) onNavigateToShifts();
            }}
          >
            Shifts
          </a>
          <a className="nav-item" href="#">Leave Management</a>
          <a className="nav-item active" href="#" onClick={e => { e.preventDefault(); }}>
            Payroll
          </a>
          <a className="nav-item" href="#">Departments</a>
          <a className="nav-item" href="#">Settings</a>
        </nav>
        <div className="sidebar-bottom">
          <div className="profile-avatar">A</div>
          <div><strong>Administrator</strong><small>{tenantName}</small></div>
        </div>
      </aside>

      {/* Main Content Pane */}
      <main className="main-content">
        {/* Topbar Header */}
        <header className="topbar">
          <div>
            <span className="breadcrumb">Workspace / Payroll</span>
            <h2>Payroll & Compensation Management</h2>
          </div>
          <div className="top-actions">
            <button className="secondary-btn" onClick={onLogout}>Sign out</button>
          </div>
        </header>

        {/* Global Alert Messages */}
        <div style={{ marginTop: '20px' }}>
          {error && <div className="error-message">{error}</div>}
          {success && <div className="success-message" style={{ margin: '0 0 16px' }}>{success}</div>}
        </div>

        {/* Payroll Sub-Navigation Tabs */}
        <div className="payroll-subnav">
          <button
            type="button"
            className={`payroll-tab-btn ${activeTab === 'dashboard' ? 'active' : ''}`}
            onClick={() => setActiveTab('dashboard')}
          >
            📊 Dashboard
          </button>

          <button
            type="button"
            className={`payroll-tab-btn ${activeTab === 'periods' ? 'active' : ''}`}
            onClick={() => setActiveTab('periods')}
          >
            🗓 Payroll Periods <span className="badge-count">{periods.length}</span>
          </button>

          <button
            type="button"
            className={`payroll-tab-btn ${activeTab === 'preview' ? 'active' : ''}`}
            onClick={() => setActiveTab('preview')}
          >
            👁 Payroll Preview
          </button>

          <button
            type="button"
            className={`payroll-tab-btn ${activeTab === 'history' || activeTab === 'run-detail' ? 'active' : ''}`}
            onClick={() => setActiveTab('history')}
          >
            📋 Run History
          </button>

          <button
            type="button"
            className={`payroll-tab-btn ${activeTab === 'structures' ? 'active' : ''}`}
            onClick={() => setActiveTab('structures')}
          >
            ⚙ Salary Structures
          </button>
        </div>

        {/* Screen Routing */}
        {activeTab === 'dashboard' && (
          <PayrollDashboard
            periods={periods}
            recentRuns={recentRuns}
            loading={loading}
            onSelectPeriod={id => setSelectedPeriodId(id)}
            onNavigateTab={handleNavigateTab}
            onOpenCreatePeriodModal={() => setShowCreateModal(true)}
            onCalculatePayroll={handleCalculatePayroll}
            onViewRunDetails={handleViewRunDetails}
            onRefresh={loadData}
          />
        )}

        {activeTab === 'periods' && (
          <PayrollPeriodManagement
            periods={periods}
            loading={loading}
            onRefresh={loadData}
            onCreatePeriod={handleCreatePeriod}
            onCalculatePayroll={handleCalculatePayroll}
            onApprovePeriod={handleApprovePeriod}
            onFinalizePeriod={handleFinalizePeriod}
            onPreviewPeriod={id => {
              setSelectedPeriodId(id);
              setActiveTab('preview');
            }}
            onViewRuns={id => {
              setSelectedPeriodId(id);
              setActiveTab('history');
            }}
            showCreateModal={showCreateModal}
            setShowCreateModal={setShowCreateModal}
          />
        )}

        {activeTab === 'preview' && (
          <PayrollPreview
            periods={periods}
            selectedPeriodId={selectedPeriodId}
            onSelectPeriod={id => setSelectedPeriodId(id)}
            onCalculatePayroll={handleCalculatePayroll}
            onNavigateToPeriods={() => setActiveTab('periods')}
          />
        )}

        {activeTab === 'history' && (
          <PayrollRunHistory
            periods={periods}
            selectedPeriodId={selectedPeriodId}
            onSelectPeriod={id => setSelectedPeriodId(id)}
            onViewRunDetails={handleViewRunDetails}
            onNavigateToPreview={id => {
              setSelectedPeriodId(id);
              setActiveTab('preview');
            }}
          />
        )}

        {activeTab === 'run-detail' && selectedRunId && (
          <PayrollRunDetail
            runId={selectedRunId}
            periods={periods}
            onBack={() => setActiveTab('history')}
            onApprovePeriod={handleApprovePeriod}
            onFinalizePeriod={handleFinalizePeriod}
          />
        )}

        {activeTab === 'structures' && (
          <SalaryStructures
            onBack={() => setActiveTab('dashboard')}
          />
        )}
      </main>
    </div>
  );
}
