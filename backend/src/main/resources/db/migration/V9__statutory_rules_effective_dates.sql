-- V9__statutory_rules_effective_dates.sql
-- Add effective dating, jurisdiction, source reference, and version columns to statutory_rules.
-- This migration is additive and preserves existing data.

ALTER TABLE statutory_rules
    ADD COLUMN effective_from DATE NOT NULL DEFAULT '1970-01-01',
    ADD COLUMN effective_to   DATE,
    ADD COLUMN jurisdiction   VARCHAR(50) NOT NULL DEFAULT 'ALL',
    ADD COLUMN source_reference VARCHAR(255),
    ADD COLUMN rule_version   VARCHAR(20),
    ADD COLUMN disability_ceiling DECIMAL(12,2);

-- Adjust unique constraint to include effective_from and jurisdiction.
ALTER TABLE statutory_rules DROP CONSTRAINT IF EXISTS uk_statutory_rule;
ALTER TABLE statutory_rules ADD CONSTRAINT uk_statutory_rule UNIQUE (tenant_id, rule_type, state, effective_from, jurisdiction);

-- Insert statutory rules for EPF and ESI with effective dates
INSERT INTO statutory_rules (tenant_id, rule_type, state, effective_year, employee_rate, employer_rate, wage_ceiling, minimum_wage_threshold, is_active, effective_from, effective_to, jurisdiction, source_reference, rule_version)
SELECT 
    t.id, 'EPF', NULL, 2026, 0.12, 0.12, 15000, 0, true, '1900-01-01', '2026-09-16', 'ALL', 'Gov Gazette S.O. 3502(D)', 'v1'
FROM tenants t
ON CONFLICT (tenant_id, rule_type, state, effective_from, jurisdiction) DO NOTHING;

INSERT INTO statutory_rules (tenant_id, rule_type, state, effective_year, employee_rate, employer_rate, wage_ceiling, minimum_wage_threshold, is_active, effective_from, effective_to, jurisdiction, source_reference, rule_version)
SELECT 
    t.id, 'EPF', NULL, 2026, 0.12, 0.12, 25000, 0, true, '2026-09-17', NULL, 'ALL', 'Gov Gazette S.O. 5109(E)', 'v2'
FROM tenants t
ON CONFLICT (tenant_id, rule_type, state, effective_from, jurisdiction) DO NOTHING;

INSERT INTO statutory_rules (tenant_id, rule_type, state, effective_year, employee_rate, employer_rate, wage_ceiling, minimum_wage_threshold, is_active, effective_from, effective_to, jurisdiction, source_reference, rule_version)
SELECT 
    t.id, 'ESI', NULL, 2026, 0.0075, 0.0325, 21000, 0, true, '1900-01-01', NULL, 'ALL', 'Labour Ministry Circular', 'v1'
FROM tenants t
ON CONFLICT (tenant_id, rule_type, state, effective_from, jurisdiction) DO NOTHING;

