package app;

/**
 * Employee Payroll Calculator
 * 
 * Calculates monthly net salary using an Employee object that encapsulates
 * personal and contractual data. Demonstrates object field access,
 * derived computations from object state, and multi-level branching
 * based on object properties.
 * 
 * Benchmark Category: HARD
 * Key Challenge: Object instantiation and field-based branching,
 *                derived variables from multiple object fields,
 *                tiered tax brackets, and conditional bonuses
 *                that depend on composite object state.
 */
public class PayrollCalculator {

    /** Represents an employee's payroll data. */
    public static class Employee {
        public String department;   // "ENGINEERING", "SALES", "HR", "MANAGEMENT"
        public double baseSalary;   // monthly base salary
        public int yearsOfService;  // years worked at the company
        public boolean isFullTime;  // full-time vs part-time
        public int performanceScore; // 1-5 performance rating
    }

    /**
     * Calculates the net monthly pay for the given employee.
     *
     * @param emp the employee record
     * @return net salary after tax, bonuses, and deductions; or -1 for invalid input
     */
    public double calculateNetSalary(Employee emp) {
        // ===== INPUT VALIDATION =====
        if (emp == null) {
            return -1.0;
        }

        if (emp.department == null || emp.department.isEmpty()) {
            return -1.0;
        }

        if (emp.baseSalary < 0) {
            return -1.0;
        }

        if (emp.performanceScore < 1 || emp.performanceScore > 5) {
            return -1.0;
        }

        if (emp.yearsOfService < 0) {
            return -1.0;
        }

        // ===== 1. DEPARTMENT ALLOWANCE =====
        double departmentAllowance;
        String dept = emp.department.toUpperCase();

        if (dept.equals("ENGINEERING")) {
            departmentAllowance = emp.baseSalary * 0.15;
        } else if (dept.equals("SALES")) {
            departmentAllowance = emp.baseSalary * 0.10;
        } else if (dept.equals("MANAGEMENT")) {
            departmentAllowance = emp.baseSalary * 0.20;
        } else if (dept.equals("HR")) {
            departmentAllowance = emp.baseSalary * 0.08;
        } else {
            departmentAllowance = 0.0;
        }

        // ===== 2. SENIORITY BONUS =====
        double seniorityBonus = 0.0;

        if (emp.yearsOfService >= 20) {
            seniorityBonus = emp.baseSalary * 0.12;
        } else if (emp.yearsOfService >= 10) {
            seniorityBonus = emp.baseSalary * 0.08;
        } else if (emp.yearsOfService >= 5) {
            seniorityBonus = emp.baseSalary * 0.04;
        }

        // ===== 3. PERFORMANCE BONUS =====
        double performanceBonus = 0.0;

        if (emp.performanceScore == 5) {
            performanceBonus = emp.baseSalary * 0.20;
            // Top performer in management gets extra
            if (dept.equals("MANAGEMENT")) {
                performanceBonus += 500.0;
            }
        } else if (emp.performanceScore == 4) {
            performanceBonus = emp.baseSalary * 0.10;
        } else if (emp.performanceScore == 3) {
            performanceBonus = emp.baseSalary * 0.03;
        }
        // Score 1-2: no bonus

        // ===== 4. FULL-TIME vs PART-TIME ADJUSTMENT =====
        double grossSalary;

        if (emp.isFullTime) {
            grossSalary = emp.baseSalary + departmentAllowance + seniorityBonus + performanceBonus;
        } else {
            // Part-time: half base, reduced bonuses
            grossSalary = (emp.baseSalary * 0.5) + (departmentAllowance * 0.5) + (performanceBonus * 0.5);
            // No seniority bonus for part-time
        }

        // ===== 5. TAX BRACKET CALCULATION =====
        double taxRate;

        if (grossSalary > 50000) {
            taxRate = 0.35;
        } else if (grossSalary > 30000) {
            taxRate = 0.30;
        } else if (grossSalary > 15000) {
            taxRate = 0.25;
        } else if (grossSalary > 5000) {
            taxRate = 0.15;
        } else {
            taxRate = 0.05;
        }

        double tax = grossSalary * taxRate;

        // ===== 6. SPECIAL DEDUCTIONS =====
        double deduction = 0.0;

        // Senior engineers with low performance get mandatory training deduction
        if (dept.equals("ENGINEERING") && emp.yearsOfService >= 10 && emp.performanceScore <= 2) {
            deduction = 200.0;
        }

        // Sales underperformers lose commission
        if (dept.equals("SALES") && emp.performanceScore == 1) {
            deduction += grossSalary * 0.05;
        }

        // ===== 7. NET SALARY =====
        double netSalary = grossSalary - tax - deduction;

        // Ensure non-negative
        if (netSalary < 0) {
            netSalary = 0.0;
        }

        return Math.round(netSalary * 100.0) / 100.0;
    }
}
