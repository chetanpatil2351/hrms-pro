package com.hrmspro.modules.payroll.entity;

/**
 * Lifecycle status values for a SalaryAdvance.
 *
 * The enum is stored as a {@code VARCHAR} column in the {@code salary_advances}
 * table via the default JPA {@code EnumType.STRING} mapping used in the
 * {@link SalaryAdvance} entity.
 *
 * The statuses reflect the full life‑cycle from request creation through
 * approval, repayment and final closure.
 */
public enum SalaryAdvanceStatus {
    /**
     * Employee has submitted a request but it has not yet been reviewed.
     */
    REQUESTED,

    /**
     * The request was approved by a manager/HR. Repayment can now begin.
     */
    APPROVED,

    /**
     * The request was rejected; no repayment will occur.
     */
    REJECTED,

    /**
     * The employee or admin cancelled the request before approval.
     */
    CANCELLED,

    /**
     * The advance is approved and repayment is in progress.
     */
    ACTIVE,

    /**
     * Some repayments have been made but the outstanding balance is not zero.
     */
    PARTIALLY_REPAID,

    /**
     * The outstanding balance has been fully repaid.
     */
    FULLY_REPAID
}
