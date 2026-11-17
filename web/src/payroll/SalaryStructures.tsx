import { useState, useEffect, type FormEvent } from 'react';
import {
  getComponents,
  createComponent,
  getStructures,
  createStructure,
  assignStructure,
  type SalaryComponentDto,
  type SalaryStructureDto,
  type EmployeeSalaryStructureDto,
} from '../services/payrollService';
import { getEmployees, type Employee } from '../services/employeeService';

type Props = {
  onBack: () => void;
};

export default function SalaryStructures({ onBack }: Props) {
  const [activeTab, setActiveTab] = useState<'structures' | 'components' | 'assign'>('structures');

  const [components, setComponents] = useState<SalaryComponentDto[]>([]);
  const [structures, setStructures] = useState<SalaryStructureDto[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);

  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string>('');
  const [success, setSuccess] = useState<string>('');

  // Create Component Modal State
  const [showComponentModal, setShowComponentModal] = useState<boolean>(false);
  const [compName, setCompName] = useState<string>('');
  const [compCode, setCompCode] = useState<string>('');
  const [compType, setCompType] = useState<'EARNING' | 'DEDUCTION' | 'EMPLOYER_CONTRIBUTION'>('EARNING');
  const [compCalcType, setCompCalcType] = useState<'FIXED_AMOUNT' | 'PERCENTAGE'>('FIXED_AMOUNT');
  const [compPercentage, setCompPercentage] = useState<number>(0);
  const [compTaxable, setCompTaxable] = useState<boolean>(true);
  const [compStatutory, setCompStatutory] = useState<boolean>(false);
  const [submittingComp, setSubmittingComp] = useState<boolean>(false);

  // Create Structure Modal State
  const [showStructureModal, setShowStructureModal] = useState<boolean>(false);
  const [structName, setStructName] = useState<string>('');
  const [structCode, setStructCode] = useState<string>('');
  const [structDesc, setStructDesc] = useState<string>('');
  const [selectedCompIds, setSelectedCompIds] = useState<string[]>([]);
  const [submittingStruct, setSubmittingStruct] = useState<boolean>(false);

  // Assign Structure Form State
  const [assignEmployeeId, setAssignEmployeeId] = useState<string>('');
  const [assignStructId, setAssignStructId] = useState<string>('');
  const [assignEffectiveFrom, setAssignEffectiveFrom] = useState<string>(() => new Date().toISOString().slice(0, 10));
  const [assignAnnualCtc, setAssignAnnualCtc] = useState<number>(600000);
  const [assignMonthlyBase, setAssignMonthlyBase] = useState<number>(25000);
  const [assignReason, setAssignReason] = useState<string>('New employment package assignment');
  const [submittingAssign, setSubmittingAssign] = useState<boolean>(false);

  // Load Data
  async function loadAllData() {
    try {
      setLoading(true);
      setError('');
      const [compRes, structRes, empRes] = await Promise.all([
        getComponents(),
        getStructures(0, 50),
        getEmployees('', 0, 100),
      ]);

      if (compRes.success && compRes.data) {
        setComponents(compRes.data);
      }
      if (structRes.success && structRes.data) {
        setStructures(structRes.data.content || []);
      }
      if (empRes.success && empRes.data) {
        setEmployees(empRes.data.content || []);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load salary structure data');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadAllData();
  }, []);

  // Handle Create Component
  async function handleCreateComponent(e: FormEvent) {
    e.preventDefault();
    if (!compName.trim() || !compCode.trim()) {
      setError('Component Name and Code are required.');
      return;
    }

    try {
      setSubmittingComp(true);
      setError('');
      setSuccess('');
      const res = await createComponent({
        name: compName.trim(),
        code: compCode.trim().toUpperCase(),
        type: compType,
        calculationType: compCalcType,
        percentageRate: compCalcType === 'PERCENTAGE' ? compPercentage : undefined,
        isTaxable: compTaxable,
        isStatutory: compStatutory,
        isActive: true,
      });

      if (!res.success) throw new Error(res.message);

      setSuccess(`Salary component '${compName}' created successfully.`);
      setShowComponentModal(false);
      setCompName('');
      setCompCode('');
      await loadAllData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create salary component');
    } finally {
      setSubmittingComp(false);
    }
  }

  // Handle Create Structure
  async function handleCreateStructure(e: FormEvent) {
    e.preventDefault();
    if (!structName.trim() || !structCode.trim()) {
      setError('Structure Name and Code are required.');
      return;
    }

    try {
      setSubmittingStruct(true);
      setError('');
      setSuccess('');

      const mappedComponents = selectedCompIds.map((cid, idx) => ({
        componentId: cid,
        displayOrder: idx + 1,
      }));

      const res = await createStructure({
        name: structName.trim(),
        code: structCode.trim().toUpperCase(),
        description: structDesc.trim() || undefined,
        isActive: true,
        components: mappedComponents,
      });

      if (!res.success) throw new Error(res.message);

      setSuccess(`Salary structure '${structName}' created successfully.`);
      setShowStructureModal(false);
      setStructName('');
      setStructCode('');
      setStructDesc('');
      setSelectedCompIds([]);
      await loadAllData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create salary structure');
    } finally {
      setSubmittingStruct(false);
    }
  }

  // Handle Assign Structure
  async function handleAssignStructure(e: FormEvent) {
    e.preventDefault();
    if (!assignEmployeeId || !assignStructId || !assignEffectiveFrom || !assignAnnualCtc) {
      setError('Please fill in all required fields to assign a salary structure.');
      return;
    }

    try {
      setSubmittingAssign(true);
      setError('');
      setSuccess('');

      const payload: Partial<EmployeeSalaryStructureDto> = {
        employeeId: assignEmployeeId,
        salaryStructureId: assignStructId,
        effectiveFrom: assignEffectiveFrom,
        annualCtc: Number(assignAnnualCtc),
        monthlyBaseSalary: Number(assignMonthlyBase),
        revisionReason: assignReason,
      };

      const res = await assignStructure(payload);
      if (!res.success) throw new Error(res.message);

      const emp = employees.find(e => e.id === assignEmployeeId);
      setSuccess(`Salary package successfully assigned to ${emp?.firstName} ${emp?.lastName || ''}!`);
      setAssignReason('Annual increment / structure update');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to assign salary structure');
    } finally {
      setSubmittingAssign(false);
    }
  }

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <button
            type="button"
            className="action-link-btn"
            onClick={onBack}
            style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', fontWeight: 600, color: '#6257e8', marginBottom: '6px' }}
          >
            ← Back to Payroll Dashboard
          </button>
          <h2 style={{ fontSize: '20px', fontWeight: 700, margin: '0 0 4px', color: '#1e293b' }}>
            Salary Structures & Components
          </h2>
          <p style={{ margin: 0, fontSize: '13px', color: '#64748b' }}>
            Configure compensation packages, allowances, statutory rules and employee package assignments.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px' }}>
          <button
            type="button"
            className="secondary-btn"
            onClick={() => setShowComponentModal(true)}
          >
            + New Component
          </button>
          <button
            type="button"
            className="primary-btn"
            onClick={() => setShowStructureModal(true)}
          >
            + Create Structure
          </button>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message" style={{ margin: '0 0 16px' }}>{success}</div>}

      {/* Internal Tabs */}
      <div className="payroll-subnav">
        <button
          type="button"
          className={`payroll-tab-btn ${activeTab === 'structures' ? 'active' : ''}`}
          onClick={() => setActiveTab('structures')}
        >
          Templates & Structures <span className="badge-count">{structures.length}</span>
        </button>

        <button
          type="button"
          className={`payroll-tab-btn ${activeTab === 'components' ? 'active' : ''}`}
          onClick={() => setActiveTab('components')}
        >
          Salary Components <span className="badge-count">{components.length}</span>
        </button>

        <button
          type="button"
          className={`payroll-tab-btn ${activeTab === 'assign' ? 'active' : ''}`}
          onClick={() => setActiveTab('assign')}
        >
          Assign to Employee
        </button>
      </div>

      {/* Tab 1: Structures */}
      {activeTab === 'structures' && (
        <section className="table-card">
          <div className="table-header">
            <div>
              <h3>Configured Salary Structures</h3>
              <p>Salary packages and component groupings used for payroll computation</p>
            </div>
            <button type="button" className="secondary-btn" onClick={loadAllData} disabled={loading}>
              ↻ Refresh
            </button>
          </div>

          {loading ? (
            <div className="empty-state">Loading salary structures...</div>
          ) : structures.length === 0 ? (
            <div className="empty-state">
              <h3>No salary structures found</h3>
              <p>Create a template structure with basic earnings and deductions.</p>
              <button
                type="button"
                className="primary-btn"
                onClick={() => setShowStructureModal(true)}
              >
                + Create Salary Structure
              </button>
            </div>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>STRUCTURE NAME</th>
                    <th>CODE</th>
                    <th>DESCRIPTION</th>
                    <th>COMPONENTS</th>
                    <th>STATUS</th>
                    <th>ACTION</th>
                  </tr>
                </thead>
                <tbody>
                  {structures.map(struct => (
                    <tr key={struct.id}>
                      <td><strong>{struct.name}</strong></td>
                      <td><code>{struct.code}</code></td>
                      <td>{struct.description || 'Standard compensation package'}</td>
                      <td>
                        <strong>{struct.components?.length || 0}</strong> components
                      </td>
                      <td>
                        <span className={`status ${struct.isActive ? 'active' : 'inactive'}`}>
                          {struct.isActive ? 'ACTIVE' : 'INACTIVE'}
                        </span>
                      </td>
                      <td>
                        <button
                          type="button"
                          className="action-link-btn"
                          onClick={() => {
                            setAssignStructId(struct.id);
                            setActiveTab('assign');
                          }}
                        >
                          Assign to Employee →
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {/* Tab 2: Components */}
      {activeTab === 'components' && (
        <section className="table-card">
          <div className="table-header">
            <div>
              <h3>Active Salary Components</h3>
              <p>Earnings, deductions, allowances, and statutory contributions</p>
            </div>
            <button
              type="button"
              className="primary-btn"
              onClick={() => setShowComponentModal(true)}
            >
              + Add Component
            </button>
          </div>

          {loading ? (
            <div className="empty-state">Loading components...</div>
          ) : components.length === 0 ? (
            <div className="empty-state">
              <h3>No salary components defined</h3>
              <p>Add basic components such as BASIC, HRA, SPECIAL_ALLOWANCE, and EPF.</p>
            </div>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>COMPONENT NAME</th>
                    <th>CODE</th>
                    <th>TYPE</th>
                    <th>CALCULATION</th>
                    <th>TAXABLE</th>
                    <th>STATUTORY</th>
                    <th>STATUS</th>
                  </tr>
                </thead>
                <tbody>
                  {components.map(comp => (
                    <tr key={comp.id}>
                      <td><strong>{comp.name}</strong></td>
                      <td><code>{comp.code}</code></td>
                      <td>
                        <span style={{
                          fontSize: '11px',
                          fontWeight: 700,
                          color: comp.type === 'EARNING' ? '#16a34a' : comp.type === 'DEDUCTION' ? '#b91c1c' : '#4338ca'
                        }}>
                          {comp.type}
                        </span>
                      </td>
                      <td>
                        {comp.calculationType === 'PERCENTAGE'
                          ? `${comp.percentageRate ?? 0}% Rate`
                          : 'Fixed Amount'}
                      </td>
                      <td>{comp.isTaxable ? 'Yes' : 'Exempt'}</td>
                      <td>{comp.isStatutory ? '🛡️ Statutory' : 'Standard'}</td>
                      <td>
                        <span className="status active">ACTIVE</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {/* Tab 3: Assign Structure to Employee */}
      {activeTab === 'assign' && (
        <section className="table-card" style={{ maxWidth: '780px' }}>
          <div className="table-header">
            <div>
              <h3>Assign Salary Structure to Employee</h3>
              <p>Map an employee to a structure with annual CTC and effective date.</p>
            </div>
          </div>

          <form onSubmit={handleAssignStructure}>
            <div className="form-grid">
              <label>
                Select Employee *
                <select
                  required
                  value={assignEmployeeId}
                  onChange={e => setAssignEmployeeId(e.target.value)}
                >
                  <option value="">-- Choose Employee --</option>
                  {employees.map(emp => (
                    <option key={emp.id} value={emp.id}>
                      {emp.firstName} {emp.lastName} ({emp.employeeCode}) — {emp.designation}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                Salary Structure Template *
                <select
                  required
                  value={assignStructId}
                  onChange={e => setAssignStructId(e.target.value)}
                >
                  <option value="">-- Choose Structure --</option>
                  {structures.map(s => (
                    <option key={s.id} value={s.id}>
                      {s.name} ({s.code})
                    </option>
                  ))}
                </select>
              </label>

              <label>
                Effective From Date *
                <input
                  type="date"
                  required
                  value={assignEffectiveFrom}
                  onChange={e => setAssignEffectiveFrom(e.target.value)}
                />
              </label>

              <label>
                Annual CTC (₹) *
                <input
                  type="number"
                  required
                  min={0}
                  step="1000"
                  value={assignAnnualCtc}
                  onChange={e => {
                    const ctc = Number(e.target.value);
                    setAssignAnnualCtc(ctc);
                    // Standard base salary recommendation = 40-50% of monthly CTC
                    const monthlyCtc = ctc / 12;
                    setAssignMonthlyBase(Math.round(monthlyCtc * 0.5));
                  }}
                />
              </label>

              <label>
                Monthly Base Salary (₹)
                <input
                  type="number"
                  min={0}
                  step="500"
                  value={assignMonthlyBase}
                  onChange={e => setAssignMonthlyBase(Number(e.target.value))}
                />
              </label>

              <label>
                Revision / Assignment Reason
                <input
                  type="text"
                  value={assignReason}
                  onChange={e => setAssignReason(e.target.value)}
                  placeholder="e.g. New hiring offer, Annual revision"
                />
              </label>
            </div>

            <div style={{ marginTop: '24px', display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
              <button
                type="submit"
                className="primary-btn"
                disabled={submittingAssign}
              >
                {submittingAssign ? 'Assigning Package...' : 'Assign Salary Package'}
              </button>
            </div>
          </form>
        </section>
      )}

      {/* Modal: New Component */}
      {showComponentModal && (
        <div className="modal-backdrop">
          <div className="employee-modal" style={{ maxWidth: '540px' }}>
            <div className="modal-header">
              <div>
                <h2>Create Salary Component</h2>
                <p>Define an earning, deduction or statutory rule component.</p>
              </div>
              <button type="button" className="close-btn" onClick={() => setShowComponentModal(false)}>×</button>
            </div>

            <form onSubmit={handleCreateComponent}>
              <div className="form-grid">
                <label>
                  Component Name *
                  <input
                    required
                    placeholder="e.g. Basic Salary, HRA"
                    value={compName}
                    onChange={e => setCompName(e.target.value)}
                  />
                </label>

                <label>
                  Component Code *
                  <input
                    required
                    placeholder="e.g. BASIC, HRA, EPF"
                    value={compCode}
                    onChange={e => setCompCode(e.target.value)}
                  />
                </label>

                <label>
                  Component Type *
                  <select
                    value={compType}
                    onChange={e => setCompType(e.target.value as any)}
                  >
                    <option value="EARNING">Earning (Addition)</option>
                    <option value="DEDUCTION">Deduction (Employee deduction)</option>
                    <option value="EMPLOYER_CONTRIBUTION">Employer Contribution</option>
                  </select>
                </label>

                <label>
                  Calculation Type *
                  <select
                    value={compCalcType}
                    onChange={e => setCompCalcType(e.target.value as any)}
                  >
                    <option value="FIXED_AMOUNT">Fixed Monthly Amount</option>
                    <option value="PERCENTAGE">Percentage Rate (%)</option>
                  </select>
                </label>

                {compCalcType === 'PERCENTAGE' && (
                  <label>
                    Percentage Rate (%)
                    <input
                      type="number"
                      step="0.01"
                      value={compPercentage}
                      onChange={e => setCompPercentage(Number(e.target.value))}
                    />
                  </label>
                )}
              </div>

              <div style={{ marginTop: '16px', display: 'flex', gap: '20px' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px' }}>
                  <input
                    type="checkbox"
                    checked={compTaxable}
                    onChange={e => setCompTaxable(e.target.checked)}
                  />
                  Subject to Tax (Taxable)
                </label>

                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px' }}>
                  <input
                    type="checkbox"
                    checked={compStatutory}
                    onChange={e => setCompStatutory(e.target.checked)}
                  />
                  Statutory Compliance Rule
                </label>
              </div>

              <div className="modal-actions">
                <button type="button" className="secondary-btn" onClick={() => setShowComponentModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="primary-btn" disabled={submittingComp}>
                  {submittingComp ? 'Saving...' : 'Save Component'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: New Structure */}
      {showStructureModal && (
        <div className="modal-backdrop">
          <div className="employee-modal" style={{ maxWidth: '600px' }}>
            <div className="modal-header">
              <div>
                <h2>Create Salary Structure Template</h2>
                <p>Bundle components into a reusable template for employee assignments.</p>
              </div>
              <button type="button" className="close-btn" onClick={() => setShowStructureModal(false)}>×</button>
            </div>

            <form onSubmit={handleCreateStructure}>
              <div className="form-grid">
                <label>
                  Structure Name *
                  <input
                    required
                    placeholder="e.g. Standard Executive Structure"
                    value={structName}
                    onChange={e => setStructName(e.target.value)}
                  />
                </label>

                <label>
                  Structure Code *
                  <input
                    required
                    placeholder="e.g. EXEC_STD_2026"
                    value={structCode}
                    onChange={e => setStructCode(e.target.value)}
                  />
                </label>

                <div style={{ gridColumn: '1 / -1' }}>
                  <label>
                    Description
                    <input
                      placeholder="e.g. Standard corporate CTC package with HRA, PF and medical allowances"
                      value={structDesc}
                      onChange={e => setStructDesc(e.target.value)}
                    />
                  </label>
                </div>
              </div>

              {/* Component Checkboxes */}
              <div style={{ marginTop: '16px' }}>
                <strong style={{ fontSize: '13px', display: 'block', marginBottom: '8px' }}>
                  Map Components to Structure:
                </strong>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: '8px', maxHeight: '180px', overflowY: 'auto', padding: '10px', background: '#f8fafc', borderRadius: '6px' }}>
                  {components.map(comp => (
                    <label key={comp.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px' }}>
                      <input
                        type="checkbox"
                        checked={selectedCompIds.includes(comp.id)}
                        onChange={e => {
                          if (e.target.checked) {
                            setSelectedCompIds(prev => [...prev, comp.id]);
                          } else {
                            setSelectedCompIds(prev => prev.filter(id => id !== comp.id));
                          }
                        }}
                      />
                      <span><strong>{comp.name}</strong> ({comp.code})</span>
                    </label>
                  ))}
                </div>
              </div>

              <div className="modal-actions">
                <button type="button" className="secondary-btn" onClick={() => setShowStructureModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="primary-btn" disabled={submittingStruct}>
                  {submittingStruct ? 'Creating...' : 'Create Structure'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
