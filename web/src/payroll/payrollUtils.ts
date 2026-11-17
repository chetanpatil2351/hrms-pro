/**
 * Utility functions for Payroll formatting and presentation
 */

export const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];

export function getMonthName(monthNumber: number): string {
  if (monthNumber >= 1 && monthNumber <= 12) {
    return MONTH_NAMES[monthNumber - 1];
  }
  return `Month ${monthNumber}`;
}

export function formatCurrency(amount?: number | null): string {
  if (amount == null || isNaN(amount)) {
    return '₹0.00';
  }
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function formatDate(dateStr?: string | null): string {
  if (!dateStr) return '—';
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleDateString('en-IN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return dateStr;
  }
}

export function formatDateTime(dateTimeStr?: string | null): string {
  if (!dateTimeStr) return '—';
  try {
    const d = new Date(dateTimeStr);
    if (isNaN(d.getTime())) return dateTimeStr;
    return d.toLocaleString('en-IN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return dateTimeStr;
  }
}

export function getStatusBadge(status?: string): { label: string; className: string } {
  if (!status) return { label: 'Unknown', className: 'status draft' };
  
  switch (status.toUpperCase()) {
    case 'DRAFT':
      return { label: 'Draft', className: 'status draft' };
    case 'PROCESSING':
      return { label: 'Processing', className: 'status processing' };
    case 'REVIEW':
      return { label: 'Under Review', className: 'status review' };
    case 'APPROVED':
      return { label: 'Approved', className: 'status approved' };
    case 'FINALIZED':
      return { label: 'Finalized', className: 'status finalized' };
    case 'PAID':
      return { label: 'Paid', className: 'status paid' };
    case 'COMPLETED':
      return { label: 'Completed', className: 'status completed' };
    case 'FAILED':
      return { label: 'Failed', className: 'status failed' };
    case 'PREVIEW':
      return { label: 'Preview', className: 'status review' };
    default:
      return { label: status, className: 'status draft' };
  }
}
