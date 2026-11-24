import React, { useState, useEffect } from 'react';
import {
  getMembers,
  updateMemberStatus,
  updateMemberRoles,
  getInvitations,
  inviteMember,
  revokeInvitation,
  getBranches,
  getDepartments,
  type Member,
  type Invitation,
  type Branch,
  type Department,
} from '../../services/tenantService';
import { getRoles, type Role } from '../../services/rbacService';

export const MemberManagementView: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'members' | 'invitations'>('members');
  const [members, setMembers] = useState<Member[]>([]);
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Invite modal
  const [inviteModalOpen, setInviteModalOpen] = useState(false);
  const [inviteForm, setInviteForm] = useState({
    email: '',
    roleId: '',
    branchId: '',
    departmentId: '',
    designation: '',
    employeeCode: '',
  });

  // Edit Roles modal
  const [rolesModalOpen, setRolesModalOpen] = useState(false);
  const [selectedMember, setSelectedMember] = useState<Member | null>(null);
  const [selectedRoleIds, setSelectedRoleIds] = useState<string[]>([]);

  useEffect(() => {
    loadAll();
  }, []);

  async function loadAll() {
    setLoading(true);
    setError(null);
    try {
      const [membersRes, invitesRes, rolesRes, branchesRes, deptsRes] = await Promise.all([
        getMembers(),
        getInvitations(),
        getRoles(),
        getBranches(),
        getDepartments(),
      ]);
      setMembers(membersRes.data?.content || []);
      setInvitations(invitesRes.data || []);
      setRoles(rolesRes.data || []);
      setBranches(branchesRes.data || []);
      setDepartments(deptsRes.data || []);
    } catch (err: any) {
      setError(err.message || 'Failed to load member directory');
    } finally {
      setLoading(false);
    }
  }

  function openInviteModal() {
    const defaultRole = roles.find(r => r.name === 'EMPLOYEE') || roles[0];
    setInviteForm({
      email: '',
      roleId: defaultRole ? defaultRole.id : '',
      branchId: '',
      departmentId: '',
      designation: '',
      employeeCode: '',
    });
    setInviteModalOpen(true);
  }

  async function handleInviteSubmit(e: React.FormEvent) {
    e.preventDefault();
    try {
      const res = await inviteMember({
        email: inviteForm.email,
        roleId: inviteForm.roleId,
        branchId: inviteForm.branchId || undefined,
        departmentId: inviteForm.departmentId || undefined,
        designation: inviteForm.designation || undefined,
        employeeCode: inviteForm.employeeCode || undefined,
      });
      alert(`Invitation sent to ${inviteForm.email}!`);
      setInviteModalOpen(false);
      loadAll();
    } catch (err: any) {
      alert(err.message || 'Failed to send invitation');
    }
  }

  async function handleRevokeInvitation(id: string) {
    if (!confirm('Are you sure you want to revoke this invitation?')) return;
    try {
      await revokeInvitation(id);
      loadAll();
    } catch (err: any) {
      alert(err.message || 'Failed to revoke invitation');
    }
  }

  async function handleToggleStatus(m: Member) {
    const newStatus = m.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    if (!confirm(`Are you sure you want to change status to ${newStatus} for ${m.email}?`)) return;
    try {
      await updateMemberStatus(m.membershipId, newStatus);
      loadAll();
    } catch (err: any) {
      alert(err.message || 'Failed to update member status');
    }
  }

  function openEditRoles(m: Member) {
    setSelectedMember(m);
    setSelectedRoleIds(m.roleIds || []);
    setRolesModalOpen(true);
  }

  function handleRoleCheckbox(roleId: string) {
    setSelectedRoleIds(prev =>
      prev.includes(roleId) ? prev.filter(id => id !== roleId) : [...prev, roleId]
    );
  }

  async function handleSaveRoles(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedMember) return;
    if (selectedRoleIds.length === 0) {
      alert('A member must be assigned at least one role');
      return;
    }
    try {
      await updateMemberRoles(selectedMember.membershipId, selectedRoleIds);
      setRolesModalOpen(false);
      loadAll();
    } catch (err: any) {
      alert(err.message || 'Failed to update member roles');
    }
  }

  return (
    <div className="management-container">
      <div className="section-header">
        <div>
          <h2>Company Members & Access</h2>
          <p className="subtitle">Manage user access, role assignments, and active invitations.</p>
        </div>
        <button className="primary-btn" onClick={openInviteModal}>+ Invite Team Member</button>
      </div>

      <div className="tabs-header">
        <button
          className={`tab-btn ${activeTab === 'members' ? 'active' : ''}`}
          onClick={() => setActiveTab('members')}
        >
          Active Members ({members.length})
        </button>
        <button
          className={`tab-btn ${activeTab === 'invitations' ? 'active' : ''}`}
          onClick={() => setActiveTab('invitations')}
        >
          Pending Invitations ({invitations.filter(i => i.status === 'PENDING').length})
        </button>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {loading ? (
        <div className="loading-state">Loading company members...</div>
      ) : activeTab === 'members' ? (
        <div className="table-card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Member</th>
                <th>Designation</th>
                <th>Branch & Dept</th>
                <th>Assigned Roles</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {members.length === 0 ? (
                <tr>
                  <td colSpan={6} className="empty-cell">No members found.</td>
                </tr>
              ) : (
                members.map(m => (
                  <tr key={m.membershipId}>
                    <td>
                      <div>
                        <strong>{m.firstName} {m.lastName}</strong>
                      </div>
                      <div className="subtext">{m.email}</div>
                    </td>
                    <td>{m.designation || 'Staff'} {m.employeeCode ? `(${m.employeeCode})` : ''}</td>
                    <td>
                      <div>{m.branchName || 'Primary Branch'}</div>
                      <div className="subtext">{m.departmentName || 'General'}</div>
                    </td>
                    <td>
                      <div className="badge-row">
                        {m.roles.map(r => (
                          <span key={r} className="role-tag">{r}</span>
                        ))}
                      </div>
                    </td>
                    <td>
                      <span className={`status-badge ${m.status.toLowerCase()}`}>
                        {m.status}
                      </span>
                    </td>
                    <td>
                      <button className="text-btn" onClick={() => openEditRoles(m)}>Edit Roles</button>
                      <button className="text-btn danger-text" onClick={() => handleToggleStatus(m)}>
                        {m.status === 'ACTIVE' ? 'Suspend' : 'Reactivate'}
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
                <th>Invitee Email</th>
                <th>Assigned Role</th>
                <th>Branch & Department</th>
                <th>Designation</th>
                <th>Status</th>
                <th>Expires</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {invitations.length === 0 ? (
                <tr>
                  <td colSpan={7} className="empty-cell">No invitations on record.</td>
                </tr>
              ) : (
                invitations.map(inv => (
                  <tr key={inv.id}>
                    <td><strong>{inv.email}</strong></td>
                    <td><span className="role-tag">{inv.roleName}</span></td>
                    <td>{inv.branchName || 'Global'} / {inv.departmentName || 'General'}</td>
                    <td>{inv.designation || 'Staff'}</td>
                    <td>
                      <span className={`status-badge ${inv.status.toLowerCase()}`}>
                        {inv.status}
                      </span>
                    </td>
                    <td>{new Date(inv.expiresAt).toLocaleDateString()}</td>
                    <td>
                      {inv.status === 'PENDING' && (
                        <button className="text-btn danger-text" onClick={() => handleRevokeInvitation(inv.id)}>
                          Revoke
                        </button>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Invite Member Modal */}
      {inviteModalOpen && (
        <div className="modal-backdrop">
          <div className="modal-content">
            <div className="modal-header">
              <h3>Invite New Team Member</h3>
              <button className="close-btn" onClick={() => setInviteModalOpen(false)}>&times;</button>
            </div>
            <form onSubmit={handleInviteSubmit}>
              <div className="form-group">
                <label>Email Address *</label>
                <input
                  type="email"
                  required
                  placeholder="employee@company.com"
                  value={inviteForm.email}
                  onChange={e => setInviteForm({ ...inviteForm, email: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>Initial Role *</label>
                <select
                  required
                  value={inviteForm.roleId}
                  onChange={e => setInviteForm({ ...inviteForm, roleId: e.target.value })}
                >
                  <option value="" disabled>Select Role</option>
                  {roles.map(r => (
                    <option key={r.id} value={r.id}>{r.name} {r.isSystemRole ? '(Standard)' : '(Custom)'}</option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label>Branch Assignment</label>
                <select
                  value={inviteForm.branchId}
                  onChange={e => setInviteForm({ ...inviteForm, branchId: e.target.value })}
                >
                  <option value="">Default Branch</option>
                  {branches.filter(b => b.active).map(b => (
                    <option key={b.id} value={b.id}>{b.name}</option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label>Department Assignment</label>
                <select
                  value={inviteForm.departmentId}
                  onChange={e => setInviteForm({ ...inviteForm, departmentId: e.target.value })}
                >
                  <option value="">General Department</option>
                  {departments.filter(d => d.active).map(d => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label>Job Designation</label>
                <input
                  type="text"
                  placeholder="e.g. Senior Software Engineer"
                  value={inviteForm.designation}
                  onChange={e => setInviteForm({ ...inviteForm, designation: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>Employee Code</label>
                <input
                  type="text"
                  placeholder="e.g. EMP-1042"
                  value={inviteForm.employeeCode}
                  onChange={e => setInviteForm({ ...inviteForm, employeeCode: e.target.value })}
                />
              </div>

              <div className="modal-actions">
                <button type="button" className="secondary-btn" onClick={() => setInviteModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="primary-btn">
                  Send Invitation
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Roles Modal */}
      {rolesModalOpen && selectedMember && (
        <div className="modal-backdrop">
          <div className="modal-content">
            <div className="modal-header">
              <h3>Manage Roles: {selectedMember.firstName} {selectedMember.lastName}</h3>
              <button className="close-btn" onClick={() => setRolesModalOpen(false)}>&times;</button>
            </div>
            <form onSubmit={handleSaveRoles}>
              <p className="subtext">Select the roles assigned to this member within this company.</p>
              <div className="checkbox-list">
                {roles.map(r => (
                  <label key={r.id} className="checkbox-item">
                    <input
                      type="checkbox"
                      checked={selectedRoleIds.includes(r.id)}
                      onChange={() => handleRoleCheckbox(r.id)}
                    />
                    <div>
                      <strong>{r.name}</strong>
                      <span className="subtext d-block">{r.description || (r.isSystemRole ? 'Standard system role' : 'Custom company role')}</span>
                    </div>
                  </label>
                ))}
              </div>

              <div className="modal-actions">
                <button type="button" className="secondary-btn" onClick={() => setRolesModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="primary-btn">
                  Save Roles
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
