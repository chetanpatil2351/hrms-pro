import { useEffect, useState, type FormEvent } from 'react';
import {
  login,
  getMemberships,
  switchTenant,
  logout,
  hasRole,
  type TenantMembership,
} from './services/authService';
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
} from './services/employeeService';
import { getTodayAttendance, getAttendanceHistory, checkIn, checkOut, type AttendanceResponseDto } from './services/attendanceService';
import './App.css';
import ShiftManagement from './ShiftManagement';
import PayrollManagement from './payroll/PayrollManagement';
import { CompanySettingsModal } from './components/company/CompanySettingsModal';
import { BranchDepartmentView } from './components/company/BranchDepartmentView';
import { MemberManagementView } from './components/company/MemberManagementView';
import { RoleManagementView } from './components/rbac/RoleManagementView';
import { MySalaryAdvancesView, AdminSalaryAdvancesView } from './components/salaryAdvance';
import { MyExpensesView, AdminExpensesView } from './components/expenses';

function App() {
  const [token, setToken] = useState(() => localStorage.getItem('accessToken'));
  const [tenantId, setTenantId] = useState(() => localStorage.getItem('activeTenantId'));
  const [tenantName, setTenantName] = useState(() => localStorage.getItem('activeTenantName') || '');
  const [memberships, setMemberships] = useState<TenantMembership[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [selectedEmployee, setSelectedEmployee] = useState<Employee | null>(null);
  const [editingEmployee, setEditingEmployee] = useState(false);
  const [editForm, setEditForm] = useState<UpdateEmployeeRequest | null>(null);
  const [branches, setBranches] = useState<OrganizationOption[]>([]);
  const [departments, setDepartments] = useState<OrganizationOption[]>([]);
  const [loadingOptions, setLoadingOptions] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [search, setSearch] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalEmployees, setTotalEmployees] = useState(0);
  const [stats, setStats] = useState<EmployeeStats | null>(null);

  // Profile Sub-features
  const [todayAttendance, setTodayAttendance] = useState<any>(null);
  const [attendanceLoading, setAttendanceLoading] = useState(false);
  const [attendanceError, setAttendanceError] = useState('');
  const [historyData, setHistoryData] = useState<any>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const [profileTab, setProfileTab] = useState<'details' | 'documents' | 'history'>('details');
  const [documents, setDocuments] = useState<EmployeeDocument[]>([]);
  const [history, setHistory] = useState<EmployeeAuditLog[]>([]);
  const [uploadDocType, setUploadDocType] = useState('RESUME');
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [docLoading, setDocLoading] = useState(false);
  const [actionSuccess, setActionSuccess] = useState('');

  const [branchFilter, setBranchFilter] = useState('');
  const [departmentFilter, setDepartmentFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [companySettingsModalOpen, setCompanySettingsModalOpen] = useState(false);
  // State for screen navigation
  const [screen, setScreen] = useState<
    'login' | 'companies' | 'dashboard' | 'attendance' | 'shifts' | 'payroll' | 'salary-advances' | 'salary-advance-approvals' | 'expenses' | 'expense-approvals' | 'company' | 'members' | 'roles'
  >(
    token && tenantId ? 'dashboard' : 'login'
  );

  const isHrOrAdmin = hasRole(['ADMIN', 'HR_MANAGER']) ||
    memberships.some(m => m.tenantId === tenantId && m.roles?.some(r => r === 'ADMIN' || r === 'HR_MANAGER'));
  // Attendance pagination
  const [attHistoryPage, setAttHistoryPage] = useState(0);
  const [punchLoading, setPunchLoading] = useState(false);
  const [attendanceSuccess, setAttendanceSuccess] = useState('');

  // GPS Attendance State
  const [gpsLoading, setGpsLoading] = useState(false);
  const [gpsPermission, setGpsPermission] = useState<'prompt' | 'granted' | 'denied' | 'unsupported'>('prompt');
  const [lastPosition, setLastPosition] = useState<{ latitude: number; longitude: number; accuracy: number; timestamp: number } | null>(null);
  const [lastPunchResponse, setLastPunchResponse] = useState<AttendanceResponseDto | null>(null);

  const [form, setForm] = useState<CreateEmployeeRequest>({
    firstName: '',
    lastName: '',
    joiningDate: '',
    designation: '',
    employmentType: 'FULL_TIME',
    createAccountInvitation: false,
  });

  async function loadEmployees(
    query = search,
    page = 0,
    filters = {
      branchId: branchFilter,
      departmentId: departmentFilter,
      employmentStatus: statusFilter,
    }
  ) {
    try {
      setLoading(true);
      setError('');

      const response = await getEmployees(query, page, 10, {
        branchId: filters.branchId || undefined,
        departmentId: filters.departmentId || undefined,
        employmentStatus: filters.employmentStatus || undefined,
      });

      if (!response.success) {
        throw new Error(response.message);
      }

      const data = response.data;

      setEmployees(data.content || []);
      setCurrentPage(data.number ?? 0);
      setTotalPages(data.totalPages ?? 0);
      setTotalEmployees(data.totalElements ?? 0);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load employees');
    } finally {
      setLoading(false);
    }
  }

  async function loadDashboardStats() {
    try {
      const response = await getEmployeeStats();
      if (response.success && response.data) {
        setStats(response.data);
      }
    } catch {
      // ignore
    }
  }

  async function loadOrganizationOptions() {
    try {
      setLoadingOptions(true);

      const [branchResponse, departmentResponse] = await Promise.all([
        getBranches(),
        getDepartments(),
      ]);

      if (!branchResponse.success) {
        throw new Error(branchResponse.message || 'Unable to load branches');
      }

      if (!departmentResponse.success) {
        throw new Error(departmentResponse.message || 'Unable to load departments');
      }

      setBranches(branchResponse.data || []);
      setDepartments(departmentResponse.data || []);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Unable to load branch and department options'
      );
    } finally {
      setLoadingOptions(false);
    }
  }

  useEffect(() => {
    if (token && tenantId) {
      loadEmployees();
      loadDashboardStats();
      loadOrganizationOptions();
      getMemberships().then(setMemberships).catch(() => {});
    }
  }, []);

  async function handleLogin(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError('');

    try {
      const result = await login(email, password);
      setToken(result.accessToken);

      if (result.activeTenantId) {
        localStorage.setItem('activeTenantId', result.activeTenantId);
        localStorage.setItem('activeTenantName', result.activeTenantName || '');
        setTenantId(result.activeTenantId);
        setTenantName(result.activeTenantName || '');
        setScreen('dashboard');
        await loadEmployees();
      } else {
        const list = await getMemberships();
        setMemberships(list);

        if (list.length === 1) {
          await chooseCompany(list[0].tenantId);
        } else if (list.length > 1) {
          setScreen('companies');
        } else {
          setError('Your account is not linked to a company yet. Please create a company first.');
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setLoading(false);
    }
  }

  async function chooseCompany(id: string) {
    if (!id) return;
    setLoading(true);
    setError('');

    try {
      const result = await switchTenant(id);
      setToken(result.accessToken);
      setTenantId(result.activeTenantId);
      setTenantName(result.activeTenantName || '');

      if (result.activeTenantId) {
        localStorage.setItem('activeTenantId', result.activeTenantId);
      }
      localStorage.setItem('activeTenantName', result.activeTenantName || '');

      setScreen('dashboard');
      await loadEmployees();
      await loadOrganizationOptions();
      getMemberships().then(setMemberships).catch(() => {});
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Company selection failed');
    } finally {
      setLoading(false);
    }
  }

  function handleSearchSubmit(event: FormEvent) {
    event.preventDefault();
    setCurrentPage(0);
    loadEmployees(search, 0, {
      branchId: branchFilter,
      departmentId: departmentFilter,
      employmentStatus: statusFilter,
    });
  }

  function handleBranchFilterChange(value: string) {
    setBranchFilter(value);
    setCurrentPage(0);
    loadEmployees(search, 0, {
      branchId: value,
      departmentId: departmentFilter,
      employmentStatus: statusFilter,
    });
  }

  function handleDepartmentFilterChange(value: string) {
    setDepartmentFilter(value);
    setCurrentPage(0);
    loadEmployees(search, 0, {
      branchId: branchFilter,
      departmentId: value,
      employmentStatus: statusFilter,
    });
  }

  function handleStatusFilterChange(value: string) {
    setStatusFilter(value);
    setCurrentPage(0);
    loadEmployees(search, 0, {
      branchId: branchFilter,
      departmentId: departmentFilter,
      employmentStatus: value,
    });
  }

  function handlePageChange(newPage: number) {
    if (newPage < 0 || newPage >= totalPages || loading) return;
    loadEmployees(search, newPage, {
      branchId: branchFilter,
      departmentId: departmentFilter,
      employmentStatus: statusFilter,
    });
  }

  async function handleCreateEmployee(event: FormEvent) {
    event.preventDefault();

    // Frontend validation
    if (!form.firstName.trim()) {
      setError('First name is required');
      return;
    }
    if (!form.lastName.trim()) {
      setError('Last name is required');
      return;
    }
    if (!form.designation.trim()) {
      setError('Designation is required');
      return;
    }
    if (!form.joiningDate) {
      setError('Joining date is required');
      return;
    }
    if (form.workEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.workEmail.trim())) {
      setError('Please provide a valid work email address');
      return;
    }
    if (form.phone && !/^[0-9+\-\s()]{7,20}$/.test(form.phone.trim())) {
      setError('Please provide a valid phone number (digits and optional +)');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await createEmployee(form);
      if (!response.success) throw new Error(response.message);

      setShowForm(false);
      setForm({
        firstName: '',
        lastName: '',
        joiningDate: '',
        designation: '',
        employmentType: 'FULL_TIME',
        createAccountInvitation: false,
      });
      await loadEmployees(search);
      await loadDashboardStats();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to create employee');
    } finally {
      setLoading(false);
    }
  }

  async function openEmployeeProfile(id: string) {
    try {
      setLoading(true);
      setError('');
      setActionSuccess('');

      const response = await getEmployeeById(id);

      if (!response.success) {
        throw new Error(response.message);
      }

      setSelectedEmployee(response.data);
      setEditingEmployee(false);
      setProfileTab('details');

      // Load documents and audit history in parallel
      loadEmployeeDocs(id);
      loadEmployeeHist(id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load employee');
    } finally {
      setLoading(false);
    }
  }

  async function loadEmployeeDocs(empId: string) {
    try {
      const res = await getEmployeeDocuments(empId);
      if (res.success && res.data) {
        setDocuments(res.data);
      }
    } catch { }
  }

  async function loadEmployeeHist(empId: string) {
    try {
      const res = await getEmployeeHistory(empId);
      if (res.success && res.data) {
        setHistory(res.data);
      }
    } catch { }
  }

  async function handleToggleStatus(empId: string, currentStatus: string) {
    try {
      setLoading(true);
      setError('');
      setActionSuccess('');

      const isCurrentlyActive = currentStatus === 'ACTIVE';
      const response = isCurrentlyActive
        ? await deactivateEmployee(empId)
        : await activateEmployee(empId);

      if (!response.success) throw new Error(response.message);

      setSelectedEmployee(response.data);
      setActionSuccess(
        isCurrentlyActive
          ? 'Employee successfully deactivated.'
          : 'Employee successfully activated.'
      );

      await loadEmployeeHist(empId);
      await loadEmployees(search);
      await loadDashboardStats();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to update employee status');
    } finally {
      setLoading(false);
    }
  }

  async function handleUploadDocument(e: FormEvent) {
    e.preventDefault();
    if (!selectedEmployee || !uploadFile || docLoading) return;

    // Validate size (5 MB)
    const MAX_SIZE = 5 * 1024 * 1024;
    if (uploadFile.size > MAX_SIZE) {
      setError('File size exceeds the 5 MB limit');
      return;
    }

    // Validate extension
    const allowedExtensions = ['pdf', 'jpg', 'jpeg', 'png'];
    const parts = uploadFile.name.split('.');
    const ext = parts.length > 1 ? parts.pop()?.toLowerCase() : '';
    if (!ext || !allowedExtensions.includes(ext)) {
      setError('Only PDF, JPG, JPEG, and PNG files are allowed');
      return;
    }

    try {
      setDocLoading(true);
      setError('');
      setActionSuccess('');

      const res = await uploadEmployeeDocument(selectedEmployee.id, uploadFile, uploadDocType);
      if (!res.success) throw new Error(res.message);

      setActionSuccess('Document uploaded successfully.');
      setUploadFile(null);
      await loadEmployeeDocs(selectedEmployee.id);
      await loadEmployeeHist(selectedEmployee.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to upload document');
    } finally {
      setDocLoading(false);
    }
  }

  async function handleDownloadDocument(docId: string, fileName: string) {
    if (!selectedEmployee) return;
    try {
      await downloadEmployeeDocument(selectedEmployee.id, docId, fileName);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to download document');
    }
  }

  async function startEditingEmployee(id?: string) {
    const targetId = typeof id === 'string' ? id : selectedEmployee?.id;
    if (!targetId) return;

    try {
      setLoading(true);
      setError('');
      const response = await getEmployeeById(targetId);
      if (!response.success) {
        throw new Error(response.message);
      }
      const emp = response.data;
      setSelectedEmployee(emp);

      setEditForm({
        firstName: emp.firstName || '',
        middleName: emp.middleName || '',
        lastName: emp.lastName || '',
        dateOfBirth: emp.dateOfBirth || undefined,
        gender: emp.gender || '',
        personalEmail: emp.personalEmail || '',
        workEmail: emp.workEmail || '',
        phone: emp.phone || '',
        address: emp.address || '',
        profilePhotoUrl: emp.profilePhotoUrl || '',
        employmentType: emp.employmentType || 'FULL_TIME',
        designation: emp.designation || '',
        branchId: emp.branchId || undefined,
        departmentId: emp.departmentId || undefined,
        reportingManagerId: emp.reportingManagerId || undefined,
        probationEndDate: emp.probationEndDate || undefined,
        emergencyContactName: emp.emergencyContactName || '',
        emergencyContactRelation: emp.emergencyContactRelation || '',
        emergencyContactPhone: emp.emergencyContactPhone || '',
      });

      setEditingEmployee(true);
      loadOrganizationOptions();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load employee for editing');
    } finally {
      setLoading(false);
    }
  }

  async function handleUpdateEmployee(event: FormEvent) {
    event.preventDefault();

    if (!selectedEmployee || !editForm) return;

    if (!editForm.firstName.trim()) {
      setError('First name is required');
      return;
    }
    if (!editForm.lastName.trim()) {
      setError('Last name is required');
      return;
    }
    if (editForm.workEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(editForm.workEmail.trim())) {
      setError('Please provide a valid work email address');
      return;
    }
    if (editForm.phone && !/^[0-9+\-\s()]{7,20}$/.test(editForm.phone.trim())) {
      setError('Please provide a valid phone number');
      return;
    }

    try {
      setLoading(true);
      setError('');
      setActionSuccess('');

      const response = await updateEmployee(selectedEmployee.id, editForm);

      if (!response.success) {
        throw new Error(response.message);
      }

      setSelectedEmployee(response.data);
      setEditingEmployee(false);
      setActionSuccess('Employee profile updated successfully.');

      await loadEmployeeHist(selectedEmployee.id);
      await loadEmployees(search);
      await loadDashboardStats();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to update employee');
    } finally {
      setLoading(false);
    }
  }

  function updateEditField(
    field: keyof UpdateEmployeeRequest,
    value: string
  ) {
    setEditForm(previous =>
      previous ? { ...previous, [field]: value } : previous
    );
  }

  function handleLogout() {
    logout();
    localStorage.removeItem('activeTenantId');
    localStorage.removeItem('activeTenantName');
    setToken(null);
    setTenantId(null);
    setTenantName('');
    setEmployees([]);
    setScreen('login');
  }

  // --- Attendance Functions ---

  async function loadTodayAttendance() {
    try {
      setAttendanceLoading(true);
      setAttendanceError('');
      const res = await getTodayAttendance();
      if (res.success) {
        setTodayAttendance(res.data || null);
      }
    } catch (err) {
      setAttendanceError(err instanceof Error ? err.message : "Failed to load today's attendance");
    } finally {
      setAttendanceLoading(false);
    }
  }

  async function loadAttendanceHistory(page = 0) {
    try {
      setHistoryLoading(true);
      setHistoryError('');
      const res = await getAttendanceHistory(page, 20);
      if (res.success && res.data) {
        setHistoryData(res.data);
        setAttHistoryPage(res.data.number ?? page);
      }
    } catch (err) {
      setHistoryError(err instanceof Error ? err.message : 'Failed to load attendance history');
    } finally {
      setHistoryLoading(false);
    }
  }

  // --- GPS Location Capture Helper ---
  function getFreshBrowserCoordinates(): Promise<GeolocationPosition> {
    return new Promise((resolve, reject) => {
      if (!navigator.geolocation) {
        setGpsPermission('unsupported');
        reject(new Error('Geolocation is not supported by your browser'));
        return;
      }

      setGpsLoading(true);

      const options: PositionOptions = {
        enableHighAccuracy: true,
        timeout: 15000,
        maximumAge: 0, // Never use stale cached coordinates
      };

      navigator.geolocation.getCurrentPosition(
        (pos) => {
          setGpsLoading(false);
          setGpsPermission('granted');
          setLastPosition({
            latitude: pos.coords.latitude,
            longitude: pos.coords.longitude,
            accuracy: pos.coords.accuracy,
            timestamp: pos.timestamp,
          });
          resolve(pos);
        },
        (err) => {
          setGpsLoading(false);
          if (err.code === err.PERMISSION_DENIED) {
            setGpsPermission('denied');
            reject(new Error('Location permission denied. Please enable location access in browser settings to punch attendance.'));
          } else if (err.code === err.POSITION_UNAVAILABLE) {
            reject(new Error('GPS location unavailable. Please check that location services or GPS are turned on.'));
          } else if (err.code === err.TIMEOUT) {
            reject(new Error('GPS location request timed out. Please ensure good signal coverage and try again.'));
          } else {
            reject(new Error(err.message || 'Unable to retrieve current GPS location.'));
          }
        },
        options
      );
    });
  }

  async function handleCheckIn() {
    if (punchLoading || gpsLoading) return;
    try {
      setPunchLoading(true);
      setAttendanceError('');
      setAttendanceSuccess('');

      // 1. Capture fresh GPS location
      const pos = await getFreshBrowserCoordinates();

      // 2. Validate coordinates range and accuracy upfront
      const lat = pos.coords.latitude;
      const lon = pos.coords.longitude;
      const accuracy = pos.coords.accuracy;

      if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
        throw new Error('Invalid GPS coordinates detected from device.');
      }

      if (accuracy > 100) {
        throw new Error(`GPS accuracy is insufficient (${Math.round(accuracy)}m). Maximum allowed is 100m. Please move to an open area.`);
      }

      // 3. Dispatch to backend API
      const res = await checkIn({
        latitude: lat,
        longitude: lon,
        accuracyMeters: accuracy,
        deviceInfo: typeof navigator !== 'undefined' ? navigator.userAgent : undefined,
      });

      if (res.success) {
        setTodayAttendance(res.data);
        setLastPunchResponse(res.data);
        setAttendanceSuccess('Check-in successful!');
        loadAttendanceHistory(0);
      } else {
        setAttendanceError(res.message || 'Check-in failed');
      }
    } catch (err) {
      setAttendanceError(err instanceof Error ? err.message : 'Check-in failed');
    } finally {
      setPunchLoading(false);
      setGpsLoading(false);
    }
  }

  async function handleCheckOut() {
    if (punchLoading || gpsLoading) return;
    try {
      setPunchLoading(true);
      setAttendanceError('');
      setAttendanceSuccess('');

      // 1. Capture fresh GPS location
      const pos = await getFreshBrowserCoordinates();

      // 2. Validate coordinates range and accuracy upfront
      const lat = pos.coords.latitude;
      const lon = pos.coords.longitude;
      const accuracy = pos.coords.accuracy;

      if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
        throw new Error('Invalid GPS coordinates detected from device.');
      }

      if (accuracy > 100) {
        throw new Error(`GPS accuracy is insufficient (${Math.round(accuracy)}m). Maximum allowed is 100m. Please move to an open area.`);
      }

      // 3. Dispatch to backend API
      const res = await checkOut({
        latitude: lat,
        longitude: lon,
        accuracyMeters: accuracy,
        deviceInfo: typeof navigator !== 'undefined' ? navigator.userAgent : undefined,
      });

      if (res.success) {
        setTodayAttendance(res.data);
        setLastPunchResponse(res.data);
        setAttendanceSuccess('Check-out successful!');
        loadAttendanceHistory(0);
      } else {
        setAttendanceError(res.message || 'Check-out failed');
      }
    } catch (err) {
      setAttendanceError(err instanceof Error ? err.message : 'Check-out failed');
    } finally {
      setPunchLoading(false);
      setGpsLoading(false);
    }
  }

  function navigateToAttendance() {
    setScreen('attendance');
    setSelectedEmployee(null);
    setAttendanceError('');
    setAttendanceSuccess('');
    loadTodayAttendance();
    loadAttendanceHistory(0);
  }

  function navigateToEmployees() {
    setScreen('dashboard');
    setAttendanceError('');
    setAttendanceSuccess('');
  }

  function formatTime(isoString?: string): string {
    if (!isoString) return '—';
    try {
      return new Date(isoString).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch { return '—'; }
  }

  function formatMinutes(mins?: number): string {
    if (mins == null) return '—';
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    return `${h}h ${m}m`;
  }

  function getStatusLabel(status?: string): string {
    if (!status) return 'Unknown';
    switch (status) {
      case 'IN_PROGRESS': return 'Checked In';
      case 'PRESENT': return 'Present';
      case 'HALF_DAY': return 'Half Day';
      case 'INCOMPLETE': return 'Incomplete';
      default: return status;
    }
  }

  const updateField = (field: keyof CreateEmployeeRequest, value: string | boolean) => {
    setForm(previous => ({ ...previous, [field]: value }));
  };
  const filteredDepartments = departments.filter(
    department =>
      !form.branchId ||
      !department.branchId ||
      department.branchId === form.branchId
  );

  if (screen === 'login') {
    return (
      <div className="auth-page">
        <form className="auth-card" onSubmit={handleLogin}>
          <div className="brand auth-brand">HRMS<span>Pro</span></div>
          <h1>Welcome back</h1>
          <p>Sign in to manage your workforce.</p>

          {error && <div className="error-message">{error}</div>}

          <label>Email address</label>
          <input
            type="email"
            required
            value={email}
            onChange={event => setEmail(event.target.value)}
            placeholder="you@company.com"
          />

          <label>Password</label>
          <input
            type="password"
            required
            value={password}
            onChange={event => setPassword(event.target.value)}
            placeholder="Enter your password"
          />

          <button className="primary-btn full-btn" disabled={loading}>
            {loading ? 'Signing in...' : 'Sign in'}
          </button>
        </form>
      </div>
    );
  }

  if (screen === 'companies') {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <div className="brand auth-brand">HRMS<span>Pro</span></div>
          <h1>Select your company</h1>
          <p>Choose the workspace you want to open.</p>

          {error && <div className="error-message">{error}</div>}

          <div className="company-list">
            {memberships.map(company => (
              <button
                key={company.membershipId}
                className="company-option"
                onClick={() => chooseCompany(company.tenantId)}
                disabled={loading}
              >
                <strong>{company.tenantName}</strong>
              </button>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="app-layout">
        {/* Sidebar */}
        <aside className="sidebar">
          <div className="brand">HRMS <span>Pro</span></div>
          <div className="nav-heading" style={{ margin: '8px 0 4px', paddingLeft: '12px' }}>MAIN</div>
          <button className={`nav-btn ${screen === 'dashboard' ? 'active' : ''}`} onClick={() => setScreen('dashboard')}>👥 Employees</button>
          <button className={`nav-btn ${screen === 'attendance' ? 'active' : ''}`} onClick={navigateToAttendance}>⏱️ Attendance</button>
          <button className={`nav-btn ${screen === 'shifts' ? 'active' : ''}`} onClick={() => setScreen('shifts')}>📅 Shifts</button>
          <button className={`nav-btn ${screen === 'payroll' ? 'active' : ''}`} onClick={() => setScreen('payroll')}>💰 Payroll</button>
          <button className={`nav-btn ${screen === 'salary-advances' ? 'active' : ''}`} onClick={() => setScreen('salary-advances')}>💵 Salary Advances</button>
          {isHrOrAdmin && (
            <button className={`nav-btn ${screen === 'salary-advance-approvals' ? 'active' : ''}`} onClick={() => setScreen('salary-advance-approvals')}>💼 Advance Approvals</button>
          )}
          <button className={`nav-btn ${screen === 'expenses' ? 'active' : ''}`} onClick={() => setScreen('expenses')}>🧾 My Expenses</button>
          {isHrOrAdmin && (
            <button className={`nav-btn ${screen === 'expense-approvals' ? 'active' : ''}`} onClick={() => setScreen('expense-approvals')}>💼 Expense Management</button>
          )}
          
          <div className="nav-heading" style={{ margin: '16px 0 4px', paddingLeft: '12px' }}>ORGANIZATION</div>
          <button className={`nav-btn ${screen === 'company' ? 'active' : ''}`} onClick={() => setScreen('company')}>🏢 Branches & Depts</button>
          <button className={`nav-btn ${screen === 'members' ? 'active' : ''}`} onClick={() => setScreen('members')}>👥 Team Members</button>
          <button className={`nav-btn ${screen === 'roles' ? 'active' : ''}`} onClick={() => setScreen('roles')}>🛡️ Roles & RBAC</button>
        </aside>
        {/* Main content */}
        <main className="main-content">
          {screen === 'dashboard' && (
            <section className="dashboard-section">
              <div className="topbar">
                <h2>Dashboard</h2>
                <div className="topbar-actions">
                  {memberships.length > 1 && (
                    <select
                      className="company-select-dropdown"
                      value={tenantId || ''}
                      onChange={(e) => chooseCompany(e.target.value)}
                    >
                      {memberships.map((m) => (
                        <option key={m.tenantId} value={m.tenantId}>
                          {m.tenantName} ({m.roles?.join(', ') || 'Member'})
                        </option>
                      ))}
                    </select>
                  )}
                  <span className="tenant-badge">🏢 {tenantName}</span>
                  <button onClick={() => setCompanySettingsModalOpen(true)} className="secondary-btn">
                    ⚙️ Settings
                  </button>
                  <button onClick={handleLogout} className="secondary-btn">Logout</button>
                </div>
              </div>
              {/* Stats and other components */}
            </section>
          )}
          {/* Employee List */}
          {screen === 'dashboard' && (
            <section className="employee-list-section">
              <div className="list-header">
                <h3>Employees</h3>
                <button className="primary-btn" onClick={() => setShowForm(true)}>Add Employee</button>
              </div>
              {/* Filters */}
              <div className="filters">
                <form className="search-group" onSubmit={handleSearchSubmit}>
                  <input
                    type="search"
                    placeholder="Search..."
                    value={search}
                    onChange={e => setSearch(e.target.value)}
                  />
                  <button type="submit" className="secondary-btn" disabled={loading}>
                    Search
                  </button>
                </form>
                <select
                  value={branchFilter}
                  onChange={e => handleBranchFilterChange(e.target.value)}
                  disabled={loadingOptions}
                >
                  <option value="">All Branches</option>
                  {branches.map(b => (
                    <option key={b.id} value={b.id}>{b.name}</option>
                  ))}
                </select>
                <select
                  value={departmentFilter}
                  onChange={e => handleDepartmentFilterChange(e.target.value)}
                  disabled={loadingOptions}
                >
                  <option value="">All Departments</option>
                  {departments.map(d => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </select>
                <select
                  value={statusFilter}
                  onChange={e => handleStatusFilterChange(e.target.value)}
                >
                  <option value="">All Statuses</option>
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                </select>
              </div>
              {/* Employee Table */}
              <table className="employee-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Designation</th>
                    <th>Branch</th>
                    <th>Department</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {employees.map(emp => (
                    <tr key={emp.id}>
                      <td>{emp.firstName} {emp.lastName}</td>
                      <td>{emp.designation}</td>
                      <td>{emp.branchName}</td>
                      <td>{emp.departmentName}</td>
                      <td>{emp.employmentStatus}</td>
                      <td>
                        <button className="secondary-btn" onClick={() => openEmployeeProfile(emp.id)}>View</button>
                        <button className="secondary-btn" onClick={() => startEditingEmployee(emp.id)}>Edit</button>
                        <button className="secondary-btn" onClick={() => handleToggleStatus(emp.id, emp.employmentStatus)}>
                          {emp.employmentStatus === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {/* Pagination */}
              {totalPages > 0 && (
                <div className="pagination">
                  <span>
                    Page {currentPage + 1} of {totalPages} | {totalEmployees} employees
                  </span>
                  <div>
                    <button
                      className="secondary-btn"
                      disabled={currentPage <= 0 || loading}
                      onClick={() => handlePageChange(currentPage - 1)}
                    >
                      Previous
                    </button>
                    <button
                      className="secondary-btn"
                      disabled={currentPage >= totalPages - 1 || loading}
                      onClick={() => handlePageChange(currentPage + 1)}
                    >
                      Next
                    </button>
                  </div>
                </div>
              )}
            </section>
          )}
          {/* Attendance Screen */}
          {screen === 'attendance' && (
            <section className="attendance-section">
              <h2>Attendance</h2>
              {attendanceError && <div className="error-message">{attendanceError}</div>}
              {attendanceSuccess && <div className="success-message">{attendanceSuccess}</div>}
              <div className="today-attendance">
                <h3>Today</h3>
                {attendanceLoading ? (
                  <p>Loading...</p>
                ) : (
                  <p>{todayAttendance ? `Status: ${todayAttendance.status}` : 'No attendance recorded'}</p>
                )}
                <button onClick={handleCheckIn} disabled={punchLoading}>Check In</button>
                <button onClick={handleCheckOut} disabled={punchLoading}>Check Out</button>
              </div>
              <div className="attendance-history">
                <h3>History</h3>
                {historyLoading ? (
                  <p>Loading history...</p>
                ) : (
                  <table className="history-table">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Check‑In</th>
                        <th>Check‑Out</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {historyData && historyData.content && historyData.content.map((h: any) => (
                        <tr key={h.id}>
                          <td>{new Date(h.date).toLocaleDateString()}</td>
                          <td>{formatTime(h.checkInTime)}</td>
                          <td>{formatTime(h.checkOutTime)}</td>
                          <td>{getStatusLabel(h.status)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
                {/* Pagination for history */}
                {historyData && historyData.totalPages > 1 && (
                  <div className="pagination">
                    <button
                      className="secondary-btn"
                      disabled={attHistoryPage === 0}
                      onClick={() => loadAttendanceHistory(attHistoryPage - 1)}
                    >Prev</button>
                    <span>Page {attHistoryPage + 1} of {historyData.totalPages}</span>
                    <button
                      className="secondary-btn"
                      disabled={attHistoryPage >= historyData.totalPages - 1}
                      onClick={() => loadAttendanceHistory(attHistoryPage + 1)}
                    >Next</button>
                  </div>
                )}
              </div>
            </section>
          )}
          {/* Shift Management */}
          {screen === 'shifts' && (
            <ShiftManagement tenantName={tenantName} onBack={() => setScreen('dashboard')} onLogout={handleLogout} />
          )}
          {/* Payroll */}
          {screen === 'payroll' && (
            <PayrollManagement tenantName={tenantName} onBack={() => setScreen('dashboard')} onLogout={handleLogout} />
          )}
          {/* Company Org (Branches & Departments) */}
          {screen === 'company' && (
            <div style={{ padding: '24px' }}>
              <BranchDepartmentView />
            </div>
          )}
          {/* Team Members & Invitations */}
          {screen === 'members' && (
            <div style={{ padding: '24px' }}>
              <MemberManagementView />
            </div>
          )}
          {/* Roles & Permissions (RBAC) */}
          {screen === 'roles' && (
            <div style={{ padding: '24px' }}>
              <RoleManagementView onBack={() => setScreen('dashboard')} />
            </div>
          )}
          {/* Salary Advances (Employee View) */}
          {screen === 'salary-advances' && (
            <div style={{ padding: '24px' }}>
              <MySalaryAdvancesView
                onBack={() => setScreen('dashboard')}
                onNavigateToAdmin={isHrOrAdmin ? () => setScreen('salary-advance-approvals') : undefined}
              />
            </div>
          )}
          {/* Salary Advances (HR / Admin Approvals) */}
          {screen === 'salary-advance-approvals' && (
            <div style={{ padding: '24px' }}>
              <AdminSalaryAdvancesView
                onBack={() => setScreen('dashboard')}
                onNavigateToMyAdvances={() => setScreen('salary-advances')}
              />
            </div>
          )}
          {/* Expenses & Reimbursements (Employee View) */}
          {screen === 'expenses' && (
            <div style={{ padding: '24px' }}>
              <MyExpensesView
                onBack={() => setScreen('dashboard')}
                onNavigateToAdmin={isHrOrAdmin ? () => setScreen('expense-approvals') : undefined}
              />
            </div>
          )}
          {/* Expenses & Reimbursements (HR / Admin Approvals) */}
          {screen === 'expense-approvals' && (
            <div style={{ padding: '24px' }}>
              <AdminExpensesView
                onBack={() => setScreen('dashboard')}
                onNavigateToMyExpenses={() => setScreen('expenses')}
              />
            </div>
          )}
          {/* Employee Profile Modal */}
          {selectedEmployee && (
            <div className="modal-backdrop">
              <div className="employee-modal">
                <div className="modal-header">
                  <div>
                    <h2>{selectedEmployee.firstName} {selectedEmployee.lastName}</h2>
                  </div>
                  <button className="close-btn" onClick={() => setSelectedEmployee(null)}>×</button>
                </div>
                {profileTab === 'details' && (
                  <div className="profile-details">
                    {/* Show employee details */}
                    <p><strong>Designation:</strong> {selectedEmployee.designation}</p>
                    <p><strong>Branch:</strong> {selectedEmployee.branchName}</p>
                    <p><strong>Department:</strong> {selectedEmployee.departmentName}</p>
                    <p><strong>Status:</strong> {selectedEmployee.employmentStatus}</p>
                    <button className="secondary-btn" onClick={() => startEditingEmployee(selectedEmployee.id)}>Edit</button>
                    <button className="secondary-btn" onClick={() => handleToggleStatus(selectedEmployee.id, selectedEmployee.employmentStatus)}>
                      {selectedEmployee.employmentStatus === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                    </button>
                  </div>
                )}
                {profileTab === 'documents' && (
                  <div className="profile-documents">
                    <h3>Documents</h3>
                    {documents.length === 0 && <p>No documents uploaded.</p>}
                    <ul>
                      {documents.map(doc => (
                        <li key={doc.id}>
                          {doc.fileName} ({doc.documentType})
                          <button className="secondary-btn" onClick={() => handleDownloadDocument(doc.id, doc.fileName)}>Download</button>
                        </li>
                      ))}
                    </ul>
                    <form onSubmit={handleUploadDocument}>
                      <select value={uploadDocType} onChange={e => setUploadDocType(e.target.value)}>
                        <option value="RESUME">Resume</option>
                        <option value="OTHER">Other</option>
                      </select>
                      <input type="file" onChange={e => setUploadFile(e.target.files?.[0] || null)} />
                      <button type="submit" disabled={docLoading}>Upload</button>
                    </form>
                  </div>
                )}
                {profileTab === 'history' && (
                  <div className="profile-history">
                    <h3>Audit History</h3>
                    
                    {history.length === 0 && <p>No history entries.</p>}
                    <ul>
                      {history.map(entry => (
                        <li key={entry.id}>
                          {new Date(entry.createdAt).toLocaleString()}: {entry.action}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                <div className="modal-tabs">
                  <button className={profileTab === 'details' ? 'active' : ''} onClick={() => setProfileTab('details')}>Details</button>
                  <button className={profileTab === 'documents' ? 'active' : ''} onClick={() => setProfileTab('documents')}>Documents</button>
                  <button className={profileTab === 'history' ? 'active' : ''} onClick={() => setProfileTab('history')}>History</button>
                </div>
                {actionSuccess && <div className="success-message">{actionSuccess}</div>}
                {error && <div className="error-message">{error}</div>}
                {editingEmployee && (
                  <form className="edit-employee-form" onSubmit={handleUpdateEmployee}>
                    {/* Similar fields as create employee, using editForm */}
                    <label>First Name
                      <input type="text" value={editForm?.firstName || ''} onChange={e => updateEditField('firstName', e.target.value)} />
                    </label>
                    <label>Last Name
                      <input type="text" value={editForm?.lastName || ''} onChange={e => updateEditField('lastName', e.target.value)} />
                    </label>
                    {/* Additional fields omitted for brevity */}
                    <button type="submit" className="primary-btn" disabled={loading}>Save</button>
                    <button type="button" className="secondary-btn" onClick={() => setEditingEmployee(false)}>Cancel</button>
                  </form>
                )}
              </div>
            </div>
          )}
          {/* Create Employee Modal */}
          {showForm && (
            <div className="modal-backdrop">
              <div className="employee-modal">
                <div className="modal-header">
                  <div>
                    <h2>Add Employee</h2>
                  </div>
                  <button className="close-btn" onClick={() => setShowForm(false)}>×</button>
                </div>
                <form onSubmit={handleCreateEmployee}>
                  <div className="form-grid">
                    <label>First Name *
                      <input required value={form.firstName} onChange={e => updateField('firstName', e.target.value)} />
                    </label>
                    <label>Last Name *
                      <input required value={form.lastName} onChange={e => updateField('lastName', e.target.value)} />
                    </label>
                    <label>Middle Name
                      <input value={form.middleName || ''} onChange={e => updateField('middleName', e.target.value)} />
                    </label>
                    <label>Designation *
                      <input required value={form.designation} onChange={e => updateField('designation', e.target.value)} />
                    </label>
                    <label>Work Email
                      <input type="email" value={form.workEmail || ''} onChange={e => updateField('workEmail', e.target.value)} />
                    </label>
                    <label>Phone
                      <input value={form.phone || ''} onChange={e => updateField('phone', e.target.value)} />
                    </label>
                    <label>Joining Date *
                      <input type="date" required value={form.joiningDate} onChange={e => updateField('joiningDate', e.target.value)} />
                    </label>
                    <label>Employment Type
                      <select value={form.employmentType} onChange={e => updateField('employmentType', e.target.value)}>
                        <option value="FULL_TIME">Full Time</option>
                        <option value="PART_TIME">Part Time</option>
                        <option value="CONTRACT">Contract</option>
                        <option value="INTERN">Intern</option>
                      </select>
                    </label>
                    <label>
                      Branch
                      <select
                        value={form.branchId || ''}
                        disabled={loadingOptions}
                        onChange={e =>
                          setForm(previous => ({
                            ...previous,
                            branchId: e.target.value || undefined,
                            departmentId: undefined,
                          }))
                        }
                      >
                        <option value="">
                          {loadingOptions ? 'Loading branches...' : 'Select Branch'}
                        </option>
                        {branches.map(branch => (
                          <option key={branch.id} value={branch.id}>
                            {branch.name} {branch.code ? `(${branch.code})` : ''}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Department
                      <select
                        value={form.departmentId || ''}
                        disabled={loadingOptions}
                        onChange={e =>
                          setForm(previous => ({
                            ...previous,
                            departmentId: e.target.value || undefined,
                          }))
                        }
                      >
                        <option value="">
                          {loadingOptions ? 'Loading departments...' : 'Select Department'}
                        </option>
                        {filteredDepartments.map(department => (
                          <option key={department.id} value={department.id}>
                            {department.name} {department.code ? `(${department.code})` : ''}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="checkbox-label">
                      <input type="checkbox" checked={form.createAccountInvitation} onChange={e => updateField('createAccountInvitation', e.target.checked)} />
                      Send account invitation
                    </label>
                  </div>
                  <div className="modal-actions">
                    <button type="button" className="secondary-btn" onClick={() => setShowForm(false)}>Cancel</button>
                    <button type="submit" className="primary-btn" disabled={loading}>{loading ? 'Saving...' : 'Save Employee'}</button>
                  </div>
                </form>
              </div>
            </div>
          )}
          {/* Company Settings Modal */}
          {companySettingsModalOpen && (
            <CompanySettingsModal
              isOpen={companySettingsModalOpen}
              onClose={() => setCompanySettingsModalOpen(false)}
              onUpdated={(updatedTenant) => {
                setTenantName(updatedTenant.name);
                localStorage.setItem('activeTenantName', updatedTenant.name);
              }}
            />
          )}
        </main>
      </div>
    );
}

export default App;
