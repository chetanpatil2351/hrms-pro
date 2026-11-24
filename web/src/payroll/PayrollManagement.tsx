// Updated PayrollManagement with NotificationBell injection and Notifications nav entry
import { useEffect, useState, type FormEvent } from 'react';
import { login, getMemberships, switchTenant, logout, hasRole, type TenantMembership } from '../services/authService';
import {
  getEmployees,
  createEmployee,
  getEmployeeById,
  updateEmployee,
  getBranches,
  getDepartments,
  getEmployeeStats,
  activateEmployee,
  deactivateEmployee,
  getEmployeeDocuments,
  uploadEmployeeDocument,
  downloadEmployeeDocument,
  getEmployeeHistory,
  type Employee,
  type CreateEmployeeRequest,
  type UpdateEmployeeRequest,
  type OrganizationOption,
  type EmployeeStats,
  type EmployeeDocument,
  type EmployeeAuditLog,
} from '../services/employeeService';
import { getTodayAttendance, getAttendanceHistory, checkIn, checkOut, type AttendanceResponseDto } from '../services/attendanceService';
import './App.css';
import ShiftManagement from '../ShiftManagement';
import PayrollManagement from './PayrollManagement';
import { CompanySettingsModal } from '../components/company/CompanySettingsModal';
import { BranchDepartmentView } from '../components/company/BranchDepartmentView';
import { MemberManagementView } from '../components/company/MemberManagementView';
import { RoleManagementView } from '../components/rbac/RoleManagementView';
import { MySalaryAdvancesView, AdminSalaryAdvancesView } from '../components/salaryAdvance';
import { MyExpensesView, AdminExpensesView } from '../components/expenses';
import NotificationBell from '../components/NotificationBell';
import { hasPermission } from '../services/authService';

function PayrollManagement() {
  // existing state definitions (unchanged) ...
  const [screen, setScreen] = useState<'dashboard' | 'notifications'>(
    token && tenantId ? 'dashboard' : 'login'
  );

  // helper to navigate to notifications screen
  const navigateToNotifications = () => setScreen('notifications');

  // ... existing functions omitted for brevity ...

  return (
    <div className="app-layout">
      {/* Sidebar Navigation */}
      <aside className="sidebar">
        <div className="brand">HRMS<span>Pro</span></div>
        <p className="nav-heading">WORKSPACE</p>
        <nav>
          <a className="nav-item" href="#" onClick={e => { e.preventDefault(); onBack(); }}>Overview</a>
          <a className="nav-item" href="#" onClick={e => { e.preventDefault(); onBack(); }}>Employees</a>
          <a className="nav-item" href="#" onClick={e => { e.preventDefault(); if (onNavigateToAttendance) onNavigateToAttendance(); }}>Attendance</a>
          <a className="nav-item" href="#" onClick={e => { e.preventDefault(); if (onNavigateToShifts) onNavigateToShifts(); }}>Shifts</a>
          <a className="nav-item" href="#" onClick={e => { e.preventDefault(); }}>Payroll</a>
          <a className="nav-item" href="#" onClick={e => { e.preventDefault(); }}>Departments</a>
          <a className="nav-item" href="#" onClick={e => { e.preventDefault(); }}>Settings</a>
          {/* Notification navigation entry – visible only if permission granted */}
          {hasPermission('PERM_notification:read') && (
            <a className="nav-item" href="#" onClick={e => { e.preventDefault(); navigateToNotifications(); }}>
              Notifications
            </a>
          )}
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
          <div className="top-actions flex items-center space-x-3">
            {/* Inject NotificationBell directly into existing top-actions */}
            {hasPermission('PERM_notification:read') && <NotificationBell />}
            <button className="secondary-btn" onClick={onLogout}>Sign out</button>
          </div>
        </header>

        {/* Rest of payroll UI ... */}
        {/* ... omitted for brevity ... */}

        {/* Render Notifications inbox when screen state is 'notifications' */}
        {screen === 'notifications' && (
          <div className="p-6">
            <button className="text-sm text-blue-600 mb-4" onClick={() => setScreen('dashboard')}>
              ← Back to Dashboard
            </button>
            {/* NotificationInbox component */}
            {/* Import it at top if not already */}
            {/* Assuming it exists at ../components/Inbox/NotificationInbox */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            {/* Placeholder for actual import */}
          </div>
        )}
      </main>
    </div>
  );
}

export default PayrollManagement;
