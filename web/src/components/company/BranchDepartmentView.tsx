import React, { useState, useEffect } from 'react';
import {
  getBranches,
  createBranch,
  updateBranch,
  toggleBranchStatus,
  getDepartments,
  createDepartment,
  updateDepartment,
  toggleDepartmentStatus,
  type Branch,
  type Department,
} from '../../services/tenantService';

export const BranchDepartmentView: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'branches' | 'departments'>('branches');
  const [branches, setBranches] = useState<Branch[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Modals state
  const [branchModalOpen, setBranchModalOpen] = useState(false);
  const [editingBranch, setEditingBranch] = useState<Branch | null>(null);
  const [branchForm, setBranchForm] = useState({ name: '', code: '', city: '', state: '', address: '' });

  const [deptModalOpen, setDeptModalOpen] = useState(false);
  const [editingDept, setEditingDept] = useState<Department | null>(null);
  const [deptForm, setDeptForm] = useState({ name: '', code: '', branchId: '' });

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    setLoading(true);
    setError(null);
    try {
      const [branchesRes, deptsRes] = await Promise.all([getBranches(), getDepartments()]);
      setBranches(branchesRes.data || []);
      setDepartments(deptsRes.data || []);
    } catch (err: any) {
      setError(err.message || 'Failed to load organization hierarchy');
    } finally {
      setLoading(false);
    }
  }

  function openCreateBranch() {
    setEditingBranch(null);
    setBranchForm({ name: '', code: '', city: '', state: '', address: '' });
    setBranchModalOpen(true);
  }

  function openEditBranch(b: Branch) {
    setEditingBranch(b);
    setBranchForm({
      name: b.name,
      code: b.code,
      city: b.city || '',
      state: b.state || '',
      address: b.address || '',
    });
    setBranchModalOpen(true);
  }

  async function handleBranchSubmit(e: React.FormEvent) {
    e.preventDefault();
    try {
      if (editingBranch) {
        await updateBranch(editingBranch.id, branchForm);
      } else {
        await createBranch(branchForm);
      }
      setBranchModalOpen(false);
      loadData();
    } catch (err: any) {
      alert(err.message || 'Error saving branch');
    }
  }

  async function handleToggleBranch(b: Branch) {
    if (!confirm(`Are you sure you want to ${b.active ? 'deactivate' : 'activate'} ${b.name}?`)) return;
    try {
      await toggleBranchStatus(b.id, !b.active);
      loadData();
    } catch (err: any) {
      alert(err.message || 'Error toggling branch status');
    }
  }

  function openCreateDept() {
    setEditingDept(null);
    setDeptForm({ name: '', code: '', branchId: '' });
    setDeptModalOpen(true);
  }

  function openEditDept(d: Department) {
    setEditingDept(d);
    setDeptForm({
      name: d.name,
      code: d.code,
      branchId: d.branchId || '',
    });
    setDeptModalOpen(true);
  }

  async function handleDeptSubmit(e: React.FormEvent) {
    e.preventDefault();
    try {
      const payload: Partial<Department> = {
        name: deptForm.name,
        code: deptForm.code,
        branchId: deptForm.branchId ? deptForm.branchId : undefined,
      };
      if (editingDept) {
        await updateDepartment(editingDept.id, payload);
      } else {
        await createDepartment(payload);
      }
      setDeptModalOpen(false);
      loadData();
    } catch (err: any) {
      alert(err.message || 'Error saving department');
    }
  }

  async function handleToggleDept(d: Department) {
    if (!confirm(`Are you sure you want to ${d.active ? 'deactivate' : 'activate'} ${d.name}?`)) return;
    try {
      await toggleDepartmentStatus(d.id, !d.active);
      loadData();
    } catch (err: any) {
      alert(err.message || 'Error toggling department status');
    }
  }

  return (
    <div className="management-container">
      <div className="section-header">
        <div>
          <h2>Organization Structure</h2>
          <p className="subtitle">Manage branches, regional operating sites, and business departments.</p>
        </div>
        <div className="header-actions">
          {activeTab === 'branches' ? (
            <button className="primary-btn" onClick={openCreateBranch}>+ Add Branch</button>
          ) : (
            <button className="primary-btn" onClick={openCreateDept}>+ Add Department</button>
          )}
        </div>
      </div>

      <div className="tabs-header">
        <button
          className={`tab-btn ${activeTab === 'branches' ? 'active' : ''}`}
          onClick={() => setActiveTab('branches')}
        >
          Branches ({branches.length})
        </button>
        <button
          className={`tab-btn ${activeTab === 'departments' ? 'active' : ''}`}
          onClick={() => setActiveTab('departments')}
        >
          Departments ({departments.length})
        </button>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {loading ? (
        <div className="loading-state">Loading organization structure...</div>
      ) : activeTab === 'branches' ? (
        <div className="table-card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Branch Name</th>
                <th>Code</th>
                <th>Location</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {branches.length === 0 ? (
                <tr>
                  <td colSpan={5} className="empty-cell">No branches configured yet.</td>
                </tr>
              ) : (
                branches.map(b => (
                  <tr key={b.id}>
                    <td><strong>{b.name}</strong></td>
                    <td><code>{b.code}</code></td>
                    <td>{b.city ? `${b.city}, ${b.state || ''}` : 'Primary Location'}</td>
                    <td>
                      <span className={`status-badge ${b.active ? 'active' : 'inactive'}`}>
                        {b.active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td>
                      <button className="text-btn" onClick={() => openEditBranch(b)}>Edit</button>
                      <button className="text-btn danger-text" onClick={() => handleToggleBranch(b)}>
                        {b.active ? 'Deactivate' : 'Activate'}
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="table-card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Department Name</th>
                <th>Code</th>
                <th>Branch Assignment</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {departments.length === 0 ? (
                <tr>
                  <td colSpan={5} className="empty-cell">No departments configured yet.</td>
                </tr>
              ) : (
                departments.map(d => (
                  <tr key={d.id}>
                    <td><strong>{d.name}</strong></td>
                    <td><code>{d.code}</code></td>
                    <td>{d.branchName || 'All Branches (Global)'}</td>
                    <td>
                      <span className={`status-badge ${d.active ? 'active' : 'inactive'}`}>
                        {d.active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td>
                      <button className="text-btn" onClick={() => openEditDept(d)}>Edit</button>
                      <button className="text-btn danger-text" onClick={() => handleToggleDept(d)}>
                        {d.active ? 'Deactivate' : 'Activate'}
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Branch Modal */}
      {branchModalOpen && (
        <div className="modal-backdrop">
          <div className="modal-content">
            <div className="modal-header">
              <h3>{editingBranch ? 'Edit Branch' : 'Add New Branch'}</h3>
              <button className="close-btn" onClick={() => setBranchModalOpen(false)}>&times;</button>
            </div>
            <form onSubmit={handleBranchSubmit}>
              <div className="form-group">
                <label>Branch Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Pune R&D Center"
                  value={branchForm.name}
                  onChange={e => setBranchForm({ ...branchForm, name: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label>Branch Code *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. PN-RD"
                  value={branchForm.code}
                  onChange={e => setBranchForm({ ...branchForm, code: e.target.value.toUpperCase() })}
                />
              </div>
              <div className="form-group">
                <label>City</label>
                <input
                  type="text"
                  value={branchForm.city}
                  onChange={e => setBranchForm({ ...branchForm, city: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label>State</label>
                <input
                  type="text"
                  value={branchForm.state}
                  onChange={e => setBranchForm({ ...branchForm, state: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label>Address</label>
                <input
                  type="text"
                  value={branchForm.address}
                  onChange={e => setBranchForm({ ...branchForm, address: e.target.value })}
                />
              </div>
              <div className="modal-actions">
                <button type="button" className="secondary-btn" onClick={() => setBranchModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="primary-btn">
                  {editingBranch ? 'Update Branch' : 'Create Branch'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Department Modal */}
      {deptModalOpen && (
        <div className="modal-content-backdrop">
          <div className="modal-content">
            <div className="modal-header">
              <h3>{editingDept ? 'Edit Department' : 'Add New Department'}</h3>
              <button className="close-btn" onClick={() => setDeptModalOpen(false)}>&times;</button>
            </div>
            <form onSubmit={handleDeptSubmit}>
              <div className="form-group">
                <label>Department Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Engineering, Human Resources"
                  value={deptForm.name}
                  onChange={e => setDeptForm({ ...deptForm, name: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label>Department Code *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. ENG, HR"
                  value={deptForm.code}
                  onChange={e => setDeptForm({ ...deptForm, code: e.target.value.toUpperCase() })}
                />
              </div>
              <div className="form-group">
                <label>Assigned Branch</label>
                <select
                  value={deptForm.branchId}
                  onChange={e => setDeptForm({ ...deptForm, branchId: e.target.value })}
                >
                  <option value="">All Branches (Global)</option>
                  {branches.filter(b => b.active).map(b => (
                    <option key={b.id} value={b.id}>{b.name} ({b.code})</option>
                  ))}
                </select>
              </div>
              <div className="modal-actions">
                <button type="button" className="secondary-btn" onClick={() => setDeptModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="primary-btn">
                  {editingDept ? 'Update Department' : 'Create Department'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
