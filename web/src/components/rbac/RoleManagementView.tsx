import React, { useState, useEffect } from 'react';
import {
  getRoles,
  getPermissions,
  createRole,
  updateRole,
  deleteRole,
  getRoleAuditLogs,
  type Role,
  type Permission,
  type RoleAuditLog,
} from '../../services/rbacService';

interface RoleManagementViewProps {
  onBack?: () => void;
}

export const RoleManagementView: React.FC<RoleManagementViewProps> = ({ onBack }) => {
  const [activeTab, setActiveTab] = useState<'roles' | 'audit'>('roles');
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [auditLogs, setAuditLogs] = useState<RoleAuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Modals
  const [modalMode, setModalMode] = useState<'create' | 'edit' | 'view' | null>(null);
  const [selectedRole, setSelectedRole] = useState<Role | null>(null);
  const [roleForm, setRoleForm] = useState({
    name: '',
    description: '',
    selectedPermissionIds: new Set<string>(),
  });
  const [formSubmitting, setFormSubmitting] = useState(false);

  // Audit pagination
  const [auditPage, setAuditPage] = useState(0);
  const [auditTotalPages, setAuditTotalPages] = useState(0);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    setLoading(true);
    setError(null);
    try {
      const [rolesRes, permsRes] = await Promise.all([getRoles(), getPermissions()]);
      if (rolesRes.success && rolesRes.data) {
        setRoles(rolesRes.data);
      }
      if (permsRes.success && permsRes.data) {
        setPermissions(permsRes.data);
      }
    } catch (err: any) {
      setError(err?.message || 'Failed to load roles and permissions');
    } finally {
      setLoading(false);
    }
  }

  async function loadAuditLogs(page = 0) {
    setLoading(true);
    setError(null);
    try {
      const res = await getRoleAuditLogs(page, 20);
      if (res.success && res.data) {
        setAuditLogs(res.data.content || []);
        setAuditPage(page);
        setAuditTotalPages(res.data.totalPages || 0);
      }
    } catch (err: any) {
      setError(err?.message || 'Failed to load audit logs');
    } finally {
      setLoading(false);
    }
  }

  function handleTabChange(tab: 'roles' | 'audit') {
    setActiveTab(tab);
    if (tab === 'audit') {
      loadAuditLogs(0);
    } else {
      loadData();
    }
  }

  function openCreateModal() {
    setRoleForm({
      name: '',
      description: '',
      selectedPermissionIds: new Set<string>(),
    });
    setSelectedRole(null);
    setModalMode('create');
  }

  function openEditModal(role: Role) {
    setSelectedRole(role);
    setRoleForm({
      name: role.name,
      description: role.description || '',
      selectedPermissionIds: new Set(role.permissions.map((p) => p.id)),
    });
    setModalMode('edit');
  }

  function openViewModal(role: Role) {
    setSelectedRole(role);
    setRoleForm({
      name: role.name,
      description: role.description || '',
      selectedPermissionIds: new Set(role.permissions.map((p) => p.id)),
    });
    setModalMode('view');
  }

  function closeModal() {
    setModalMode(null);
    setSelectedRole(null);
  }

  function togglePermission(permId: string) {
    if (modalMode === 'view') return;
    setRoleForm((prev) => {
      const next = new Set(prev.selectedPermissionIds);
      if (next.has(permId)) {
        next.delete(permId);
      } else {
        next.add(permId);
      }
      return { ...prev, selectedPermissionIds: next };
    });
  }

  function toggleModulePermissions(_moduleName: string, permsInModule: Permission[]) {
    if (modalMode === 'view') return;
    setRoleForm((prev) => {
      const next = new Set(prev.selectedPermissionIds);
      const allSelected = permsInModule.every((p) => next.has(p.id));
      if (allSelected) {
        permsInModule.forEach((p) => next.delete(p.id));
      } else {
        permsInModule.forEach((p) => next.add(p.id));
      }
      return { ...prev, selectedPermissionIds: next };
    });
  }

  async function handleSaveRole(e: React.FormEvent) {
    e.preventDefault();
    if (!roleForm.name.trim()) {
      setError('Role name is required');
      return;
    }
    setFormSubmitting(true);
    setError(null);
    try {
      if (modalMode === 'create') {
        const res = await createRole({
          name: roleForm.name.trim(),
          description: roleForm.description.trim() || undefined,
          permissionIds: Array.from(roleForm.selectedPermissionIds),
        });
        if (res.success) {
          setSuccessMessage(`Custom role "${roleForm.name}" created successfully`);
          closeModal();
          loadData();
        } else {
          setError(res.message || 'Failed to create role');
        }
      } else if (modalMode === 'edit' && selectedRole) {
        const res = await updateRole(selectedRole.id, {
          name: roleForm.name.trim(),
          description: roleForm.description.trim() || undefined,
          permissionIds: Array.from(roleForm.selectedPermissionIds),
        });
        if (res.success) {
          setSuccessMessage(`Role "${roleForm.name}" updated successfully`);
          closeModal();
          loadData();
        } else {
          setError(res.message || 'Failed to update role');
        }
      }
    } catch (err: any) {
      setError(err?.message || 'Action failed');
    } finally {
      setFormSubmitting(false);
    }
  }

  async function handleDeleteRole(role: Role) {
    if (role.isSystemRole) {
      alert('System roles cannot be deleted.');
      return;
    }
    if (!window.confirm(`Are you sure you want to delete role "${role.name}"? This cannot be undone.`)) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await deleteRole(role.id);
      if (res.success) {
        setSuccessMessage(`Role "${role.name}" deleted successfully.`);
        loadData();
      } else {
        setError(res.message || 'Failed to delete role');
      }
    } catch (err: any) {
      setError(err?.message || 'Failed to delete role');
    } finally {
      setLoading(false);
    }
  }

  // Group permissions by module
  const permissionsByModule = permissions.reduce((acc, p) => {
    if (!acc[p.module]) {
      acc[p.module] = [];
    }
    acc[p.module].push(p);
    return acc;
  }, {} as Record<string, Permission[]>);

  return (
    <div className="rbac-management-view">
      <div className="view-header">
        <div>
          <h2>Roles & Permissions (RBAC)</h2>
          <p className="subtitle">
            Configure system roles, fine-grained access control, and manage custom tenant roles.
          </p>
        </div>
        <div className="header-actions">
          {onBack && (
            <button className="secondary-btn" onClick={onBack}>
              ← Back
            </button>
          )}
          {activeTab === 'roles' && (
            <button className="primary-btn" onClick={openCreateModal}>
              + Create Custom Role
            </button>
          )}
        </div>
      </div>

      <div className="tab-navigation">
        <button
          className={`tab-btn ${activeTab === 'roles' ? 'active' : ''}`}
          onClick={() => handleTabChange('roles')}
        >
          Roles & Permissions ({roles.length})
        </button>
        <button
          className={`tab-btn ${activeTab === 'audit' ? 'active' : ''}`}
          onClick={() => handleTabChange('audit')}
        >
          Role Audit Trail
        </button>
      </div>

      {error && <div className="error-banner">{error}</div>}
      {successMessage && <div className="success-banner">{successMessage}</div>}

      {loading && <div className="loading-state">Loading data...</div>}

      {/* ROLES TAB */}
      {!loading && activeTab === 'roles' && (
        <div className="roles-grid">
          {roles.map((role) => (
            <div key={role.id} className="role-card">
              <div className="role-card-header">
                <div className="role-title-row">
                  <h3 className="role-name">{role.name}</h3>
                  {role.isSystemRole ? (
                    <span className="badge badge-system">SYSTEM ROLE</span>
                  ) : (
                    <span className="badge badge-custom">CUSTOM ROLE</span>
                  )}
                </div>
                <p className="role-desc">{role.description || 'No description provided'}</p>
              </div>

              <div className="role-card-body">
                <div className="perm-count">
                  <strong>{role.permissions?.length || 0}</strong> permissions assigned
                </div>
                <div className="perm-tags-preview">
                  {role.permissions?.slice(0, 4).map((p) => (
                    <span key={p.id} className="perm-tag">
                      {p.code}
                    </span>
                  ))}
                  {(role.permissions?.length || 0) > 4 && (
                    <span className="perm-tag-more">
                      +{(role.permissions?.length || 0) - 4} more
                    </span>
                  )}
                </div>
              </div>

              <div className="role-card-footer">
                <button className="btn-link" onClick={() => openViewModal(role)}>
                  View Permissions
                </button>
                {!role.isSystemRole && (
                  <>
                    <button className="secondary-btn btn-sm" onClick={() => openEditModal(role)}>
                      Edit
                    </button>
                    <button
                      className="danger-btn btn-sm"
                      onClick={() => handleDeleteRole(role)}
                    >
                      Delete
                    </button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* AUDIT TRAIL TAB */}
      {!loading && activeTab === 'audit' && (
        <div className="audit-section">
          {auditLogs.length === 0 ? (
            <p className="empty-state">No role audit events recorded yet.</p>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Action</th>
                  <th>Role Name</th>
                  <th>Performed By</th>
                  <th>Details</th>
                </tr>
              </thead>
              <tbody>
                {auditLogs.map((log) => (
                  <tr key={log.id}>
                    <td>{new Date(log.createdAt).toLocaleString()}</td>
                    <td>
                      <span className={`badge badge-${log.action.toLowerCase()}`}>
                        {log.action}
                      </span>
                    </td>
                    <td><strong>{log.roleName}</strong></td>
                    <td>{log.actorName || 'System'}</td>
                    <td>
                      <code className="audit-json-code">
                        {log.detailsJson || '—'}
                      </code>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {auditTotalPages > 1 && (
            <div className="pagination">
              <button
                className="secondary-btn"
                disabled={auditPage === 0}
                onClick={() => loadAuditLogs(auditPage - 1)}
              >
                Previous
              </button>
              <span>
                Page {auditPage + 1} of {auditTotalPages}
              </span>
              <button
                className="secondary-btn"
                disabled={auditPage >= auditTotalPages - 1}
                onClick={() => loadAuditLogs(auditPage + 1)}
              >
                Next
              </button>
            </div>
          )}
        </div>
      )}

      {/* CREATE / EDIT / VIEW MODAL */}
      {modalMode && (
        <div className="modal-backdrop">
          <div className="modal-dialog modal-large">
            <div className="modal-header">
              <h3>
                {modalMode === 'create' && 'Create Custom Role'}
                {modalMode === 'edit' && `Edit Role: ${selectedRole?.name}`}
                {modalMode === 'view' && `Role Permissions: ${selectedRole?.name}`}
              </h3>
              <button className="close-btn" onClick={closeModal}>
                ×
              </button>
            </div>

            <form onSubmit={handleSaveRole}>
              <div className="modal-body">
                {modalMode !== 'view' && (
                  <div className="form-group-row">
                    <div className="form-group">
                      <label>Role Name *</label>
                      <input
                        type="text"
                        required
                        value={roleForm.name}
                        onChange={(e) => setRoleForm({ ...roleForm, name: e.target.value })}
                        placeholder="e.g., Attendance Specialist"
                      />
                    </div>
                    <div className="form-group">
                      <label>Description</label>
                      <input
                        type="text"
                        value={roleForm.description}
                        onChange={(e) =>
                          setRoleForm({ ...roleForm, description: e.target.value })
                        }
                        placeholder="What permissions or duties this role has"
                      />
                    </div>
                  </div>
                )}

                {modalMode === 'view' && (
                  <div className="view-summary">
                    <p>
                      <strong>Description:</strong> {selectedRole?.description || 'None'}
                    </p>
                    <p>
                      <strong>Type:</strong>{' '}
                      {selectedRole?.isSystemRole ? 'System Protected Role' : 'Custom Tenant Role'}
                    </p>
                    <p>
                      <strong>Total Permissions:</strong>{' '}
                      {roleForm.selectedPermissionIds.size} of {permissions.length}
                    </p>
                  </div>
                )}

                <div className="permissions-matrix-container">
                  <h4>Assign Fine-Grained Permissions ({roleForm.selectedPermissionIds.size} selected)</h4>
                  <p className="hint">
                    Select the actions users with this role are authorized to execute.
                  </p>

                  <div className="modules-list">
                    {Object.entries(permissionsByModule).map(([moduleName, modulePerms]) => {
                      const allSelected = modulePerms.every((p) =>
                        roleForm.selectedPermissionIds.has(p.id)
                      );
                      const someSelected = modulePerms.some((p) =>
                        roleForm.selectedPermissionIds.has(p.id)
                      );

                      return (
                        <div key={moduleName} className="module-perm-group">
                          <div className="module-group-header">
                            <label className="module-select-all">
                              <input
                                type="checkbox"
                                disabled={modalMode === 'view'}
                                checked={allSelected}
                                ref={(el) => {
                                  if (el) el.indeterminate = someSelected && !allSelected;
                                }}
                                onChange={() => toggleModulePermissions(moduleName, modulePerms)}
                              />
                              <span className="module-name">{moduleName}</span>
                            </label>
                            <span className="module-counter">
                              {modulePerms.filter((p) => roleForm.selectedPermissionIds.has(p.id)).length} / {modulePerms.length}
                            </span>
                          </div>

                          <div className="perm-checkbox-grid">
                            {modulePerms.map((perm) => (
                              <label key={perm.id} className="perm-checkbox-item">
                                <input
                                  type="checkbox"
                                  disabled={modalMode === 'view'}
                                  checked={roleForm.selectedPermissionIds.has(perm.id)}
                                  onChange={() => togglePermission(perm.id)}
                                />
                                <div className="perm-info">
                                  <span className="perm-code">{perm.code}</span>
                                  <span className="perm-desc">{perm.description}</span>
                                </div>
                              </label>
                            ))}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>

              <div className="modal-actions">
                <button type="button" className="secondary-btn" onClick={closeModal}>
                  {modalMode === 'view' ? 'Close' : 'Cancel'}
                </button>
                {modalMode !== 'view' && (
                  <button type="submit" className="primary-btn" disabled={formSubmitting}>
                    {formSubmitting ? 'Saving...' : modalMode === 'create' ? 'Create Role' : 'Save Changes'}
                  </button>
                )}
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
