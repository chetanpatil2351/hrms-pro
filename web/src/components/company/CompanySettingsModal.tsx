import React, { useState, useEffect } from 'react';
import { getCurrentTenant, updateCurrentTenant, type TenantDetails, type UpdateTenantRequest } from '../../services/tenantService';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess?: () => void;
  onUpdated?: (tenant: TenantDetails) => void;
}

export const CompanySettingsModal: React.FC<Props> = ({ isOpen, onClose, onSuccess, onUpdated }) => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tenant, setTenant] = useState<TenantDetails | null>(null);

  const [form, setForm] = useState<UpdateTenantRequest>({
    name: '',
    legalName: '',
    email: '',
    phone: '',
    address: '',
    city: '',
    state: '',
    country: 'India',
    postalCode: '',
    industry: '',
    website: '',
    timezone: 'Asia/Kolkata',
  });

  useEffect(() => {
    if (isOpen) {
      loadTenant();
    }
  }, [isOpen]);

  async function loadTenant() {
    setLoading(true);
    setError(null);
    try {
      const res = await getCurrentTenant();
      setTenant(res.data);
      setForm({
        name: res.data.name || '',
        legalName: res.data.legalName || '',
        email: res.data.email || '',
        phone: res.data.phone || '',
        address: res.data.address || '',
        city: res.data.city || '',
        state: res.data.state || '',
        country: res.data.country || 'India',
        postalCode: res.data.postalCode || '',
        industry: res.data.industry || '',
        website: res.data.website || '',
        timezone: res.data.timezone || 'Asia/Kolkata',
      });
    } catch (err: any) {
      setError(err.message || 'Failed to load company details');
    } finally {
      setLoading(false);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const res = await updateCurrentTenant(form);
      if (res.data) {
        onUpdated?.(res.data);
      }
      onSuccess?.();
      onClose();
    } catch (err: any) {
      setError(err.message || 'Failed to update company settings');
    } finally {
      setSaving(false);
    }
  }

  if (!isOpen) return null;

  return (
    <div className="modal-backdrop">
      <div className="modal-content large-modal">
        <div className="modal-header">
          <h2>Company Profile & Settings</h2>
          <button className="close-btn" onClick={onClose}>&times;</button>
        </div>

        {loading ? (
          <div className="loading-state">Loading company profile...</div>
        ) : (
          <form onSubmit={handleSubmit} className="settings-form">
            {error && <div className="error-banner">{error}</div>}

            {tenant && (
              <div className="capacity-badge-card">
                <div>
                  <strong>Subscription:</strong> {tenant.subscriptionPlan} ({tenant.subscriptionStatus})
                </div>
                <div>
                  <strong>Team Capacity:</strong> {tenant.currentEmployeeCount} / {tenant.maxEmployees} active staff
                </div>
                <div className="progress-bar-container">
                  <div
                    className="progress-bar-fill"
                    style={{ width: `${Math.min(100, (tenant.currentEmployeeCount / tenant.maxEmployees) * 100)}%` }}
                  />
                </div>
              </div>
            )}

            <div className="form-grid">
              <div className="form-group">
                <label>Company Trading Name *</label>
                <input
                  type="text"
                  required
                  value={form.name}
                  onChange={e => setForm({ ...form, name: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>Legal / Registered Entity Name</label>
                <input
                  type="text"
                  value={form.legalName || ''}
                  onChange={e => setForm({ ...form, legalName: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>Official Email *</label>
                <input
                  type="email"
                  required
                  value={form.email}
                  onChange={e => setForm({ ...form, email: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>Primary Phone</label>
                <input
                  type="tel"
                  value={form.phone || ''}
                  onChange={e => setForm({ ...form, phone: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>Industry</label>
                <input
                  type="text"
                  value={form.industry || ''}
                  onChange={e => setForm({ ...form, industry: e.target.value })}
                  placeholder="e.g. Information Technology, Retail"
                />
              </div>

              <div className="form-group">
                <label>Website URL</label>
                <input
                  type="url"
                  value={form.website || ''}
                  onChange={e => setForm({ ...form, website: e.target.value })}
                  placeholder="https://company.com"
                />
              </div>

              <div className="form-group full-width">
                <label>Street Address</label>
                <input
                  type="text"
                  value={form.address || ''}
                  onChange={e => setForm({ ...form, address: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>City</label>
                <input
                  type="text"
                  value={form.city || ''}
                  onChange={e => setForm({ ...form, city: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>State</label>
                <input
                  type="text"
                  value={form.state || ''}
                  onChange={e => setForm({ ...form, state: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>Postal Code</label>
                <input
                  type="text"
                  value={form.postalCode || ''}
                  onChange={e => setForm({ ...form, postalCode: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>Timezone</label>
                <select
                  value={form.timezone || 'Asia/Kolkata'}
                  onChange={e => setForm({ ...form, timezone: e.target.value })}
                >
                  <option value="Asia/Kolkata">Asia/Kolkata (IST)</option>
                  <option value="UTC">UTC</option>
                  <option value="America/New_York">America/New_York (EST)</option>
                  <option value="Europe/London">Europe/London (GMT)</option>
                  <option value="Asia/Dubai">Asia/Dubai (GST)</option>
                  <option value="Asia/Singapore">Asia/Singapore (SGT)</option>
                </select>
              </div>
            </div>

            <div className="modal-actions">
              <button type="button" className="secondary-btn" onClick={onClose} disabled={saving}>
                Cancel
              </button>
              <button type="submit" className="primary-btn" disabled={saving}>
                {saving ? 'Saving Changes...' : 'Save Settings'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};
