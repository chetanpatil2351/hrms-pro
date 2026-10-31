package com.hrmspro.modules.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayslipDto {
    private UUID id;
    private UUID employeeId;
    private String employeeCode;
    private String employeeName;
    private UUID payrollPeriodId;
    private Integer year;
    private Integer month;
    private String payslipNumber;
    private LocalDate issueDate;
    private BigDecimal grossSalary;
    private BigDecimal totalDeductions;
    private BigDecimal netSalary;
    private String paymentStatus;
    private String payslipDataJson;
}
