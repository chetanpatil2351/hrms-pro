
import { useEffect, useState, type FormEvent } from 'react';
import {
  login,
  getMemberships,
  switchTenant,
  logout,
  type TenantMembership,
} from './services/authService';
import {
  getEmployees,
  createEmployee,
  getEmployeeById,
  updateEmployee,
  getBranches,
  getDepartments,
  type Employee,
  type CreateEmployeeRequest,
  type UpdateEmployeeRequest,
  type OrganizationOption,
} from './services/employeeService';
import './App.css';

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
  const [screen, setScreen] = useState<'login' | 'companies' | 'dashboard'>(
    token && tenantId ? 'dashboard' : 'login'
  );

  const [form, setForm] = useState<CreateEmployeeRequest>({
    firstName: '',
    lastName: '',
    joiningDate: '',
    designation: '',
    employmentType: 'FULL_TIME',
    createAccountInvitation: false,
  });

  async function loadEmployees(query = '') {
    try {
      setLoading(true);
      setError('');
      const response = await getEmployees(query);
      if (!response.success) throw new Error(response.message);
      setEmployees(response.data.content || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load employees');
    } finally {
      setLoading(false);
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
    if (token && tenantId) loadEmployees();
  }, []);

  useEffect(() => {
    if (showForm) {
      loadOrganizationOptions();
    }
  }, [showForm]);

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
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Company selection failed');
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateEmployee(event: FormEvent) {
    event.preventDefault();
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

    const response = await getEmployeeById(id);

    if (!response.success) {
      throw new Error(response.message);
    }

    setSelectedEmployee(response.data);
    setEditingEmployee(false);
  } catch (err) {
    setError(err instanceof Error ? err.message : 'Unable to load employee');
  } finally {
    setLoading(false);
  }
}

function startEditingEmployee() {
  if (!selectedEmployee) return;

  setEditForm({
    firstName: selectedEmployee.firstName || '',
    middleName: selectedEmployee.middleName || '',
    lastName: selectedEmployee.lastName || '',
    dateOfBirth: selectedEmployee.dateOfBirth || undefined,
    gender: selectedEmployee.gender || '',
    personalEmail: selectedEmployee.personalEmail || '',
    workEmail: selectedEmployee.workEmail || '',
    phone: selectedEmployee.phone || '',
    address: selectedEmployee.address || '',
    profilePhotoUrl: selectedEmployee.profilePhotoUrl || '',
    employmentType: selectedEmployee.employmentType || 'FULL_TIME',
    designation: selectedEmployee.designation || '',
    branchId: selectedEmployee.branchId || undefined,
    departmentId: selectedEmployee.departmentId || undefined,
    reportingManagerId: selectedEmployee.reportingManagerId || undefined,
    probationEndDate: selectedEmployee.probationEndDate || undefined,
    emergencyContactName: selectedEmployee.emergencyContactName || '',
    emergencyContactRelation: selectedEmployee.emergencyContactRelation || '',
    emergencyContactPhone: selectedEmployee.emergencyContactPhone || '',
  });

  setEditingEmployee(true);
  loadOrganizationOptions();
}

async function handleUpdateEmployee(event: FormEvent) {
    event.preventDefault();

    if (!selectedEmployee || !editForm) return;

    try {
      setLoading(true);
      setError('');

      const response = await updateEmployee(selectedEmployee.id, editForm);

      if (!response.success) {
        throw new Error(response.message);
      }

      setSelectedEmployee(response.data);
      setEditingEmployee(false);

      await loadEmployees(search);
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
                <small>{company.designation || company.roles?.join(', ')}</small>
              </button>
            ))}
          </div>
          <button className="secondary-btn full-btn" onClick={handleLogout}>Sign out</button>
        </div>
      </div>
    );
  }

  if (selectedEmployee) {
  const employee = selectedEmployee;

  return (
    <div className="app-layout">
      <main className="main-content">
        <header className="topbar">
          <div>
            <span className="breadcrumb">Employees / Profile</span>
            <h2>Employee Profile</h2>
          </div>

          <div className="top-actions">
            <button
              className="secondary-btn"
              onClick={() => {
                setSelectedEmployee(null);
                setEditingEmployee(false);
              }}
            >
              Back to Employees
            </button>

            {!editingEmployee && (
              <button
                className="primary-btn"
                onClick={startEditingEmployee}
              >
                Edit Employee
              </button>
            )}
          </div>
        </header>

        {error && <div className="error-message">{error}</div>}

        {!editingEmployee ? (
          <>
            <section className="profile-card">
              <div className="profile-avatar large">
                {employee.firstName?.[0]}
              </div>

              <div>
                <h1>{employee.fullName || `${employee.firstName} ${employee.lastName}`}</h1>
                <p>{employee.designation}</p>
                <span>{employee.employeeCode}</span>
              </div>

              <span className={`status ${employee.employmentStatus?.toLowerCase()}`}>
                {employee.employmentStatus}
              </span>
            </section>

            <section className="profile-grid">
              <div className="table-card profile-section">
                <h3>Personal Information</h3>
                <p><strong>First Name:</strong> {employee.firstName}</p>
                <p><strong>Middle Name:</strong> {employee.middleName || '—'}</p>
                <p><strong>Last Name:</strong> {employee.lastName}</p>
                <p><strong>Date of Birth:</strong> {employee.dateOfBirth || '—'}</p>
                <p><strong>Gender:</strong> {employee.gender || '—'}</p>
                <p><strong>Personal Email:</strong> {employee.personalEmail || '—'}</p>
                <p><strong>Work Email:</strong> {employee.workEmail || '—'}</p>
                <p><strong>Phone:</strong> {employee.phone || '—'}</p>
                <p><strong>Address:</strong> {employee.address || '—'}</p>
              </div>

              <div className="table-card profile-section">
                <h3>Employment Details</h3>
                <p><strong>Employee Code:</strong> {employee.employeeCode}</p>
                <p><strong>Designation:</strong> {employee.designation}</p>
                <p><strong>Joining Date:</strong> {employee.joiningDate}</p>
                <p><strong>Employment Type:</strong> {employee.employmentType}</p>
                <p><strong>Status:</strong> {employee.employmentStatus}</p>
                <p><strong>Branch:</strong> {employee.branchName || '—'}</p>
                <p><strong>Department:</strong> {employee.departmentName || '—'}</p>
                <p><strong>Reporting Manager:</strong> {employee.reportingManagerName || '—'}</p>
                <p><strong>Probation End:</strong> {employee.probationEndDate || '—'}</p>
              </div>

              <div className="table-card profile-section">
                <h3>Emergency Contact</h3>
                <p><strong>Name:</strong> {employee.emergencyContactName || '—'}</p>
                <p><strong>Relation:</strong> {employee.emergencyContactRelation || '—'}</p>
                <p><strong>Phone:</strong> {employee.emergencyContactPhone || '—'}</p>
              </div>
            </section>
          </>
        ) : (
          <section className="table-card profile-edit-card">
            <h3>Edit Employee Information</h3>

            {editForm && (
              <form onSubmit={handleUpdateEmployee}>
                <div className="form-grid">
                  <label>
                    First Name *
                    <input
                      required
                      value={editForm.firstName}
                      onChange={e => updateEditField('firstName', e.target.value)}
                    />
                  </label>

                  <label>
                    Middle Name
                    <input
                      value={editForm.middleName || ''}
                      onChange={e => updateEditField('middleName', e.target.value)}
                    />
                  </label>

                  <label>
                    Last Name *
                    <input
                      required
                      value={editForm.lastName}
                      onChange={e => updateEditField('lastName', e.target.value)}
                    />
                  </label>

                  <label>
                    Date of Birth
                    <input
                      type="date"
                      value={editForm.dateOfBirth || ''}
                      onChange={e => updateEditField('dateOfBirth', e.target.value)}
                    />
                  </label>

                  <label>
                    Gender
                    <select
                      value={editForm.gender || ''}
                      onChange={e => updateEditField('gender', e.target.value)}
                    >
                      <option value="">Select Gender</option>
                      <option value="MALE">Male</option>
                      <option value="FEMALE">Female</option>
                      <option value="OTHER">Other</option>
                    </select>
                  </label>

                  <label>
                    Personal Email
                    <input
                      type="email"
                      value={editForm.personalEmail || ''}
                      onChange={e => updateEditField('personalEmail', e.target.value)}
                    />
                  </label>

                  <label>
                    Work Email
                    <input
                      type="email"
                      value={editForm.workEmail || ''}
                      onChange={e => updateEditField('workEmail', e.target.value)}
                    />
                  </label>

                  <label>
                    Phone
                    <input
                      value={editForm.phone || ''}
                      onChange={e => updateEditField('phone', e.target.value)}
                    />
                  </label>

                  <label>
                    Designation *
                    <input
                      required
                      value={editForm.designation}
                      onChange={e => updateEditField('designation', e.target.value)}
                    />
                  </label>

                  <label>
                    Employment Type
                    <select
                      value={editForm.employmentType || 'FULL_TIME'}
                      onChange={e => updateEditField('employmentType', e.target.value)}
                    >
                      <option value="FULL_TIME">Full Time</option>
                      <option value="PART_TIME">Part Time</option>
                      <option value="CONTRACT">Contract</option>
                      <option value="INTERN">Intern</option>
                    </select>
                  </label>

                  <label>
                    Branch
                    <select
                      value={editForm.branchId || ''}
                      disabled={loadingOptions}
                      onChange={e => {
                        setEditForm(previous =>
                          previous
                            ? {
                                ...previous,
                                branchId: e.target.value || undefined,
                                departmentId: undefined,
                              }
                            : previous
                        );
                      }}
                    >
                      <option value="">Select Branch</option>
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
                      value={editForm.departmentId || ''}
                      disabled={loadingOptions}
                      onChange={e => updateEditField('departmentId', e.target.value)}
                    >
                      <option value="">Select Department</option>
                      {departments.filter(
                        department =>
                          !editForm.branchId ||
                          !department.branchId ||
                          department.branchId === editForm.branchId
                      ).map(department => (
                        <option key={department.id} value={department.id}>
                          {department.name}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label>
                    Probation End Date
                    <input
                      type="date"
                      value={editForm.probationEndDate || ''}
                      onChange={e => updateEditField('probationEndDate', e.target.value)}
                    />
                  </label>

                  <label>
                    Address
                    <input
                      value={editForm.address || ''}
                      onChange={e => updateEditField('address', e.target.value)}
                    />
                  </label>

                  <label>
                    Emergency Contact Name
                    <input
                      value={editForm.emergencyContactName || ''}
                      onChange={e => updateEditField('emergencyContactName', e.target.value)}
                    />
                  </label>

                  <label>
                    Emergency Contact Relation
                    <input
                      value={editForm.emergencyContactRelation || ''}
                      onChange={e => updateEditField('emergencyContactRelation', e.target.value)}
                    />
                  </label>

                  <label>
                    Emergency Contact Phone
                    <input
                      value={editForm.emergencyContactPhone || ''}
                      onChange={e => updateEditField('emergencyContactPhone', e.target.value)}
                    />
                  </label>
                </div>

                <div className="modal-actions">
                  <button
                    type="button"
                    className="secondary-btn"
                    onClick={() => setEditingEmployee(false)}
                  >
                    Cancel
                  </button>

                  <button
                    type="submit"
                    className="primary-btn"
                    disabled={loading}
                  >
                    {loading ? 'Saving...' : 'Save Changes'}
                  </button>
                </div>
              </form>
            )}
          </section>
        )}
      </main>
    </div>
  );
}

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="brand">HRMS<span>Pro</span></div>
        <p className="nav-heading">WORKSPACE</p>
        <nav>
          <a className="nav-item" href="#">Overview</a>
          <a className="nav-item active" href="#">Employees</a>
          <a className="nav-item" href="#">Attendance</a>
          <a className="nav-item" href="#">Shifts</a>
          <a className="nav-item" href="#">Leave Management</a>
          <a className="nav-item" href="#">Payroll</a>
          <a className="nav-item" href="#">Departments</a>
          <a className="nav-item" href="#">Settings</a>
        </nav>
        <div className="sidebar-bottom">
          <div className="profile-avatar">A</div>
          <div><strong>Administrator</strong><small>{tenantName}</small></div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div>
            <span className="breadcrumb">Workspace / Employees</span>
            <h2>Employee Management</h2>
          </div>
          <div className="top-actions">
            <button className="secondary-btn" onClick={handleLogout}>Sign out</button>
            <button className="primary-btn" onClick={() => setShowForm(true)}>+ Add Employee</button>
          </div>
        </header>

        <section className="welcome-section">
          <h1>Employees</h1>
          <p>Manage your workforce, employee profiles and information.</p>
        </section>

        <section className="stats-grid">
          <div className="stat-card"><span>Total Employees</span><h2>{employees.length}</h2><small>Loaded records</small></div>
          <div className="stat-card"><span>Active Employees</span><h2>{employees.filter(e => e.employmentStatus === 'ACTIVE').length}</h2><small>Currently active</small></div>
          <div className="stat-card"><span>Departments</span><h2>{new Set(employees.map(e => e.departmentId).filter(Boolean)).size}</h2><small>With employees</small></div>
        </section>

        <section className="table-card">
          <div className="table-header">
            <div><h3>All Employees</h3><p>Company: {tenantName}</p></div>
            <div className="search-group">
              <input
                type="search"
                placeholder="Search employees..."
                value={search}
                onChange={event => setSearch(event.target.value)}
                onKeyDown={event => event.key === 'Enter' && loadEmployees(search)}
              />
              <button className="secondary-btn" onClick={() => loadEmployees(search)}>Search</button>
            </div>
          </div>

          {error && <div className="error-message">{error}</div>}

          {loading ? <div className="empty-state">Loading...</div> :
            employees.length === 0 ? (
              <div className="empty-state">
                <h3>No employees found</h3>
                <p>Add your first employee to get started.</p>
                <button className="primary-btn" onClick={() => setShowForm(true)}>+ Add Employee</button>
              </div>
            ) : (
              <div className="table-scroll">
                <table>
                  <thead><tr><th>EMPLOYEE</th><th>DESIGNATION</th><th>DEPARTMENT</th><th>BRANCH</th><th>JOINING DATE</th><th>STATUS</th></tr></thead>
                  <tbody>
                    {employees.map(employee => (
                      <tr key={employee.id}>
                        <td><div className="employee-cell"><div className="employee-avatar">{employee.firstName?.[0]}</div><div><button
  type="button"
  className="employee-name-link"
  onClick={() => openEmployeeProfile(employee.id)}
>
  {employee.fullName || `${employee.firstName} ${employee.lastName}`}
</button><small>{employee.employeeCode || 'No code'}</small></div></div></td>
                        <td>{employee.designation}</td>
                        <td>{employee.departmentName || '—'}</td>
                        <td>{employee.branchName || '—'}</td>
                        <td>{employee.joiningDate || '—'}</td>
                        <td><span className={`status ${employee.employmentStatus?.toLowerCase()}`}>{employee.employmentStatus || 'UNKNOWN'}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
        </section>
      </main>

      {showForm && (
        <div className="modal-backdrop">
          <div className="employee-modal">
            <div className="modal-header">
              <div><h2>Add Employee</h2><p>Enter basic employee information.</p></div>
              <button className="close-btn" onClick={() => setShowForm(false)}>×</button>
            </div>
            <form onSubmit={handleCreateEmployee}>
              <div className="form-grid">
                <label>First Name *<input required value={form.firstName} onChange={e => updateField('firstName', e.target.value)} /></label>
                <label>Last Name *<input required value={form.lastName} onChange={e => updateField('lastName', e.target.value)} /></label>
                <label>Middle Name<input value={form.middleName || ''} onChange={e => updateField('middleName', e.target.value)} /></label>
                <label>Designation *<input required value={form.designation} onChange={e => updateField('designation', e.target.value)} /></label>
                <label>Work Email<input type="email" value={form.workEmail || ''} onChange={e => updateField('workEmail', e.target.value)} /></label>
                <label>Phone<input value={form.phone || ''} onChange={e => updateField('phone', e.target.value)} /></label>
                <label>Joining Date *<input type="date" required value={form.joiningDate} onChange={e => updateField('joiningDate', e.target.value)} /></label>
                <label>Employment Type<select value={form.employmentType} onChange={e => updateField('employmentType', e.target.value)}><option value="FULL_TIME">Full Time</option><option value="PART_TIME">Part Time</option><option value="CONTRACT">Contract</option><option value="INTERN">Intern</option></select></label>
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
              </div>
              <label className="checkbox-label"><input type="checkbox" checked={form.createAccountInvitation} onChange={e => updateField('createAccountInvitation', e.target.checked)} /> Send account invitation</label>
              <div className="modal-actions">
                <button type="button" className="secondary-btn" onClick={() => setShowForm(false)}>Cancel</button>
                <button type="submit" className="primary-btn" disabled={loading}>{loading ? 'Saving...' : 'Save Employee'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
