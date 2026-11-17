
import { useEffect, useState, type FormEvent } from 'react';
import {
  getShifts,
  createShift,
  updateShift,
  updateShiftStatus,
  getShiftAssignments,
  assignShift,
  bulkAssignShift,
  cancelShiftAssignment,
  type ShiftTemplate,
  type ShiftRequest,
  type ShiftAssignment,
} from './services/shiftService';
import { getEmployees, type Employee } from './services/employeeService';
import { getBranches } from './services/employeeService';

const DAYS = [
  'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
  'FRIDAY', 'SATURDAY', 'SUNDAY',
];

const emptyForm: ShiftRequest = {
  name: '',
  code: '',
  description: '',
  startTime: '09:00',
  endTime: '18:00',
  isOvernight: false,
  timezone: 'Asia/Kolkata',
  gracePeriodMinutes: 10,
  lateArrivalPolicy: 'MARK_LATE',
  earlyDeparturePolicy: 'MARK_EARLY',
  breakPolicy: 'FLEXIBLE',
  breakAllowedMinutes: 60,
  weeklyOffDays: ['SUNDAY'],
  effectiveFrom: new Date().toISOString().slice(0, 10),
  isActive: true,
};

type Props = {
  tenantName: string;
  onBack: () => void;
  onLogout: () => void;
};

export default function ShiftManagement({
  tenantName,
  onBack,
  onLogout,
}: Props) {
  const [shifts, setShifts] = useState<ShiftTemplate[]>([]);
  const [assignments, setAssignments] = useState<ShiftAssignment[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [branches, setBranches] = useState<{ id: string; name: string }[]>([]);

  const [form, setForm] = useState<ShiftRequest>(emptyForm);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [employeeId, setEmployeeId] = useState('');
  const [shiftId, setShiftId] = useState('');
  const [assignmentType, setAssignmentType] = useState('PERMANENT');
  const [effectiveStart, setEffectiveStart] = useState(
    new Date().toISOString().slice(0, 10)
  );
  const [effectiveEnd, setEffectiveEnd] = useState('');
  const [notes, setNotes] = useState('');
  const [selectedEmployees, setSelectedEmployees] = useState<string[]>([]);
  const [skipConflicting, setSkipConflicting] = useState(true);

  async function loadData() {
    try {
      setLoading(true);
      setError('');

      const [shiftRes, assignmentRes, employeeRes, branchRes] =
        await Promise.all([
          getShifts(0, 100),
          getShiftAssignments(0, 100),
          getEmployees('', 0, 100),
          getBranches(),
        ]);

      if (!shiftRes.success) throw new Error(shiftRes.message);
      if (!assignmentRes.success) throw new Error(assignmentRes.message);
      if (!employeeRes.success) throw new Error(employeeRes.message);
      if (!branchRes.success) throw new Error(branchRes.message);

      setShifts(shiftRes.data?.content || []);
      setAssignments(assignmentRes.data?.content || []);
      setEmployees(
        (employeeRes.data?.content || []).filter(
          e => e.employmentStatus === 'ACTIVE' ||
               e.employmentStatus === 'PROBATION'
        )
      );
      setBranches(branchRes.data || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load shift data');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  function updateField<K extends keyof ShiftRequest>(
    field: K,
    value: ShiftRequest[K]
  ) {
    setForm(previous => ({ ...previous, [field]: value }));
  }

  function openCreateForm() {
    setForm(emptyForm);
    setEditingId(null);
    setShowForm(true);
    setError('');
  }

  function openEditForm(shift: ShiftTemplate) {
    setForm({
      branchId: shift.branchId,
      name: shift.name,
      code: shift.code,
      description: shift.description || '',
      startTime: shift.startTime,
      endTime: shift.endTime,
      isOvernight: shift.isOvernight,
      timezone: shift.timezone || 'Asia/Kolkata',
      gracePeriodMinutes: shift.gracePeriodMinutes,
      lateArrivalPolicy: shift.lateArrivalPolicy,
      earlyDeparturePolicy: shift.earlyDeparturePolicy,
      breakPolicy: shift.breakPolicy,
      breakAllowedMinutes: shift.breakAllowedMinutes,
      weeklyOffDays: shift.weeklyOffDays || [],
      effectiveFrom: shift.effectiveFrom,
      effectiveTo: shift.effectiveTo,
      isActive: shift.isActive,
    });
    setEditingId(shift.id);
    setShowForm(true);
    setError('');
  }

  async function handleSaveShift(event: FormEvent) {
    event.preventDefault();

    try {
      setLoading(true);
      setError('');
      setSuccess('');

      if (!form.name.trim() || !form.code.trim()) {
        throw new Error('Shift name and code are required');
      }

      if (form.endTime <= form.startTime && !form.isOvernight) {
        throw new Error('For an overnight shift, enable the overnight option');
      }

      const response = editingId
        ? await updateShift(editingId, form)
        : await createShift(form);

      if (!response.success) throw new Error(response.message);

      setShowForm(false);
      setSuccess(editingId ? 'Shift updated successfully.' : 'Shift created successfully.');
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to save shift');
    } finally {
      setLoading(false);
    }
  }

  async function handleToggleShift(shift: ShiftTemplate) {
    try {
      setError('');
      setSuccess('');
      const response = await updateShiftStatus(shift.id, !shift.isActive);
      if (!response.success) throw new Error(response.message);
      setSuccess(`Shift ${shift.isActive ? 'deactivated' : 'activated'} successfully.`);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to update shift status');
    }
  }

  async function handleAssign(event: FormEvent) {
    event.preventDefault();

    try {
      setLoading(true);
      setError('');
      setSuccess('');

      if (!employeeId || !shiftId) {
        throw new Error('Select an employee and a shift');
      }

      const response = await assignShift({
        employeeId,
        shiftTemplateId: shiftId,
        assignmentType,
        effectiveStart,
        effectiveEnd: effectiveEnd || undefined,
        notes: notes || undefined,
      });

      if (!response.success) throw new Error(response.message);

      setSuccess('Shift assigned successfully.');
      setEmployeeId('');
      setEffectiveEnd('');
      setNotes('');
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to assign shift');
    } finally {
      setLoading(false);
    }
  }

  async function handleBulkAssign(event: FormEvent) {
    event.preventDefault();

    try {
      setLoading(true);
      setError('');
      setSuccess('');

      if (!selectedEmployees.length || !shiftId) {
        throw new Error('Select a shift and at least one employee');
      }

      const response = await bulkAssignShift({
        employeeIds: selectedEmployees,
        shiftTemplateId: shiftId,
        assignmentType,
        effectiveStart,
        effectiveEnd: effectiveEnd || undefined,
        notes: notes || undefined,
        skipConflicting,
      });

      if (!response.success) throw new Error(response.message);

      const result = response.data;
      setSuccess(
        `Bulk assignment completed. Success: ${result.succeeded ?? 0}, ` +
        `Skipped: ${result.skipped ?? 0}, Failed: ${result.failed ?? 0}`
      );
      setSelectedEmployees([]);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Bulk assignment failed');
    } finally {
      setLoading(false);
    }
  }

  async function handleCancelAssignment(id: string) {
    const reason = window.prompt('Enter cancellation reason:');
    if (!reason?.trim()) return;

    try {
      setLoading(true);
      setError('');
      setSuccess('');

      const response = await cancelShiftAssignment(id, reason.trim());
      if (!response.success) throw new Error(response.message);

      setSuccess('Shift assignment cancelled.');
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to cancel assignment');
    } finally {
      setLoading(false);
    }
  }

  function toggleEmployee(id: string) {
    setSelectedEmployees(previous =>
      previous.includes(id)
        ? previous.filter(item => item !== id)
        : [...previous, id]
    );
  }

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="brand">HRMS<span>Pro</span></div>
        <p className="nav-heading">WORKSPACE</p>
        <nav>
          <button className="nav-item" onClick={onBack}>Employees</button>
          <button className="nav-item active">Shifts</button>
        </nav>
        <div className="sidebar-bottom">
          <div className="profile-avatar">A</div>
          <div><strong>Administrator</strong><small>{tenantName}</small></div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div>
            <span className="breadcrumb">Workspace / Shifts</span>
            <h2>Shift Management</h2>
          </div>
          <div className="top-actions">
            <button className="secondary-btn" onClick={onLogout}>Sign out</button>
            <button className="primary-btn" onClick={openCreateForm}>+ Create Shift</button>
          </div>
        </header>

        <section className="welcome-section">
          <h1>Shift Management</h1>
          <p>Create shift templates and manage employee schedules.</p>
        </section>

        {error && <div className="error-message">{error}</div>}
        {success && <div className="success-message">{success}</div>}

        <section className="stats-grid">
          <div className="stat-card">
            <span>Total Shifts</span>
            <h2>{shifts.length}</h2>
          </div>
          <div className="stat-card">
            <span>Active Shifts</span>
            <h2>{shifts.filter(s => s.isActive).length}</h2>
          </div>
          <div className="stat-card">
            <span>Assignments</span>
            <h2>{assignments.filter(a => a.status === 'ACTIVE').length}</h2>
          </div>
          <div className="stat-card">
            <span>Available Employees</span>
            <h2>{employees.length}</h2>
          </div>
        </section>

        <section className="table-card">
          <div className="table-header">
            <div>
              <h3>Shift Templates</h3>
              <p>Manage your company's shift timings and policies.</p>
            </div>
            <button className="secondary-btn" onClick={loadData} disabled={loading}>
              ↻ Refresh
            </button>
          </div>

          {loading ? <div className="empty-state">Loading shifts...</div> :
            shifts.length === 0 ? (
              <div className="empty-state">No shifts created yet.</div>
            ) : (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>SHIFT</th>
                      <th>CODE</th>
                      <th>TIMING</th>
                      <th>BRANCH</th>
                      <th>WEEKLY OFF</th>
                      <th>STATUS</th>
                      <th>ACTIONS</th>
                    </tr>
                  </thead>
                  <tbody>
                    {shifts.map(shift => (
                      <tr key={shift.id}>
                        <td><strong>{shift.name}</strong></td>
                        <td>{shift.code}</td>
                        <td>{shift.startTime} – {shift.endTime}{shift.isOvernight ? ' (+1 day)' : ''}</td>
                        <td>{shift.branchName || 'All branches'}</td>
                        <td>{shift.weeklyOffDays?.join(', ') || 'None'}</td>
                        <td>
                          <span className={`status ${shift.isActive ? 'active' : 'inactive'}`}>
                            {shift.isActive ? 'ACTIVE' : 'INACTIVE'}
                          </span>
                        </td>
                        <td>
                          <button className="action-link-btn" onClick={() => openEditForm(shift)}>
                            Edit
                          </button>
                          {' '}
                          <button className="action-link-btn" onClick={() => handleToggleShift(shift)}>
                            {shift.isActive ? 'Deactivate' : 'Activate'}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
        </section>

        <section className="table-card">
          <div className="table-header">
            <div>
              <h3>Assign Shift</h3>
              <p>Assign a shift to one employee or multiple employees.</p>
            </div>
          </div>

          <form onSubmit={handleAssign}>
            <div className="form-grid">
              <label>
                Employee
                <select required value={employeeId} onChange={e => setEmployeeId(e.target.value)}>
                  <option value="">Select employee</option>
                  {employees.map(employee => (
                    <option key={employee.id} value={employee.id}>
                      {employee.fullName || `${employee.firstName} ${employee.lastName}`} ({employee.employeeCode})
                    </option>
                  ))}
                </select>
              </label>

              <label>
                Shift
                <select required value={shiftId} onChange={e => setShiftId(e.target.value)}>
                  <option value="">Select shift</option>
                  {shifts.filter(s => s.isActive).map(shift => (
                    <option key={shift.id} value={shift.id}>{shift.name} ({shift.code})</option>
                  ))}
                </select>
              </label>

              <label>
                Assignment Type
                <select value={assignmentType} onChange={e => setAssignmentType(e.target.value)}>
                  <option value="PERMANENT">Permanent</option>
                  <option value="TEMPORARY">Temporary</option>
                  <option value="ROTATING">Rotating</option>
                </select>
              </label>

              <label>
                Effective Start
                <input type="date" required value={effectiveStart} onChange={e => setEffectiveStart(e.target.value)} />
              </label>

              <label>
                Effective End (optional)
                <input type="date" value={effectiveEnd} onChange={e => setEffectiveEnd(e.target.value)} />
              </label>

              <label>
                Notes
                <input value={notes} onChange={e => setNotes(e.target.value)} placeholder="Optional notes" />
              </label>
            </div>

            <button className="primary-btn" type="submit" disabled={loading}>
              Assign to Employee
            </button>
          </form>

          <hr />

          <form onSubmit={handleBulkAssign}>
            <h3>Bulk Assignment</h3>
            <p>Select multiple employees for the same shift and date range.</p>

            <div className="employee-filters">
              <label>
                <input
                  type="checkbox"
                  checked={skipConflicting}
                  onChange={e => setSkipConflicting(e.target.checked)}
                />
                Skip employees with conflicting assignments
              </label>
              <button
                type="button"
                className="secondary-btn"
                onClick={() => setSelectedEmployees(employees.map(e => e.id))}
              >
                Select all
              </button>
              <button
                type="button"
                className="secondary-btn"
                onClick={() => setSelectedEmployees([])}
              >
                Clear selection
              </button>
            </div>

            <div className="employee-filters">
              {employees.map(employee => (
                <label key={employee.id} className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={selectedEmployees.includes(employee.id)}
                    onChange={() => toggleEmployee(employee.id)}
                  />
                  {employee.fullName || `${employee.firstName} ${employee.lastName}`}
                  {' '}({employee.employeeCode})
                </label>
              ))}
            </div>

            <p>{selectedEmployees.length} employee(s) selected</p>
            <button className="primary-btn" type="submit" disabled={loading || selectedEmployees.length === 0}>
              Bulk Assign Shift
            </button>
          </form>
        </section>

        <section className="table-card">
          <div className="table-header">
            <div>
              <h3>Shift Assignments</h3>
              <p>View and cancel employee shift assignments.</p>
            </div>
          </div>

          {assignments.length === 0 ? (
            <div className="empty-state">No shift assignments found.</div>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>EMPLOYEE</th>
                    <th>SHIFT</th>
                    <th>TYPE</th>
                    <th>START</th>
                    <th>END</th>
                    <th>STATUS</th>
                    <th>ACTION</th>
                  </tr>
                </thead>
                <tbody>
                  {assignments.map(item => (
                    <tr key={item.id}>
                      <td>{item.employeeName} ({item.employeeCode})</td>
                      <td>{item.shiftName} ({item.shiftCode})</td>
                      <td>{item.assignmentType}</td>
                      <td>{item.effectiveStart}</td>
                      <td>{item.effectiveEnd || 'Open-ended'}</td>
                      <td>{item.status}</td>
                      <td>
                        {item.status !== 'CANCELLED' && (
                          <button
                            className="action-link-btn"
                            onClick={() => handleCancelAssignment(item.id)}
                          >
                            Cancel
                          </button>
                        )}
                      </td>
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
              <div>
                <h2>{editingId ? 'Edit Shift' : 'Create Shift'}</h2>
                <p>Configure shift timing and attendance policies.</p>
              </div>
              <button className="close-btn" onClick={() => setShowForm(false)}>×</button>
            </div>

            <form onSubmit={handleSaveShift}>
              <div className="form-grid">
                <label>
                  Shift Name *
                  <input required value={form.name} onChange={e => updateField('name', e.target.value)} />
                </label>

                <label>
                  Shift Code *
                  <input required value={form.code} onChange={e => updateField('code', e.target.value.toUpperCase())} />
                </label>

                <label>
                  Branch
                  <select value={form.branchId || ''} onChange={e => updateField('branchId', e.target.value || undefined)}>
                    <option value="">All branches</option>
                    {branches.map(branch => (
                      <option key={branch.id} value={branch.id}>{branch.name}</option>
                    ))}
                  </select>
                </label>

                <label>
                  Start Time *
                  <input type="time" required value={form.startTime} onChange={e => updateField('startTime', e.target.value)} />
                </label>

                <label>
                  End Time *
                  <input type="time" required value={form.endTime} onChange={e => updateField('endTime', e.target.value)} />
                </label>

                <label>
                  Timezone
                  <input value={form.timezone} onChange={e => updateField('timezone', e.target.value)} />
                </label>

                <label>
                  Grace Period (minutes)
                  <input type="number" min="0" value={form.gracePeriodMinutes} onChange={e => updateField('gracePeriodMinutes', Number(e.target.value))} />
                </label>

                <label>
                  Break Allowed (minutes)
                  <input type="number" min="0" value={form.breakAllowedMinutes} onChange={e => updateField('breakAllowedMinutes', Number(e.target.value))} />
                </label>

                <label>
                  Late Arrival Policy
                  <select value={form.lateArrivalPolicy} onChange={e => updateField('lateArrivalPolicy', e.target.value)}>
                    <option value="MARK_LATE">Mark Late</option>
                    <option value="HALF_DAY">Half Day</option>
                    <option value="ABSENT">Absent</option>
                  </select>
                </label>

                <label>
                  Early Departure Policy
                  <select value={form.earlyDeparturePolicy} onChange={e => updateField('earlyDeparturePolicy', e.target.value)}>
                    <option value="MARK_EARLY">Mark Early</option>
                    <option value="HALF_DAY">Half Day</option>
                  </select>
                </label>

                <label>
                  Break Policy
                  <select value={form.breakPolicy} onChange={e => updateField('breakPolicy', e.target.value)}>
                    <option value="NONE">None</option>
                    <option value="FLEXIBLE">Flexible</option>
                    <option value="FIXED">Fixed</option>
                  </select>
                </label>

                <label>
                  Effective From
                  <input type="date" required value={form.effectiveFrom} onChange={e => updateField('effectiveFrom', e.target.value)} />
                </label>

                <label>
                  Effective To
                  <input type="date" value={form.effectiveTo || ''} onChange={e => updateField('effectiveTo', e.target.value || undefined)} />
                </label>

                <label>
                  Description
                  <input value={form.description || ''} onChange={e => updateField('description', e.target.value)} />
                </label>
              </div>

              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={form.isOvernight}
                  onChange={e => updateField('isOvernight', e.target.checked)}
                />
                Overnight shift
              </label>

              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={form.isActive}
                  onChange={e => updateField('isActive', e.target.checked)}
                />
                Active shift
              </label>

              <div>
                <strong>Weekly Off Days</strong>
                <div className="employee-filters">
                  {DAYS.map(day => (
                    <label className="checkbox-label" key={day}>
                      <input
                        type="checkbox"
                        checked={form.weeklyOffDays.includes(day)}
                        onChange={e => {
                          const next = e.target.checked
                            ? [...form.weeklyOffDays, day]
                            : form.weeklyOffDays.filter(d => d !== day);
                          updateField('weeklyOffDays', next);
                        }}
                      />
                      {day.slice(0, 3)}
                    </label>
                  ))}
                </div>
              </div>

              <div className="modal-actions">
                <button type="button" className="secondary-btn" onClick={() => setShowForm(false)}>
                  Cancel
                </button>
                <button type="submit" className="primary-btn" disabled={loading}>
                  {loading ? 'Saving...' : editingId ? 'Update Shift' : 'Create Shift'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
