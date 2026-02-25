package app;

public class PayrollCalculator2 {

    public static class Employee {
        public String department;
        public double baseSalary;
        public int yearsOfService;
        public boolean isFullTime;
        public int performanceScore;
    }

    public double calculateNetSalary(Employee emp) {
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

        double seniorityBonus = 0.0;

        if (emp.yearsOfService >= 20) {
            seniorityBonus = emp.baseSalary * 0.12;
        } else if (emp.yearsOfService >= 10) {
            seniorityBonus = emp.baseSalary * 0.08;
        } else if (emp.yearsOfService >= 5) {
            seniorityBonus = emp.baseSalary * 0.04;
        }

        double performanceBonus = 0.0;

        if (emp.performanceScore == 5) {
            performanceBonus = emp.baseSalary * 0.20;
            if (dept.equals("MANAGEMENT")) {
                performanceBonus += 500.0;
            }
        } else if (emp.performanceScore == 4) {
            performanceBonus = emp.baseSalary * 0.10;
        } else if (emp.performanceScore == 3) {
            performanceBonus = emp.baseSalary * 0.03;
        }

        double grossSalary;

        if (emp.isFullTime) {
            grossSalary = emp.baseSalary + departmentAllowance + seniorityBonus + performanceBonus;
        } else {
            grossSalary = (emp.baseSalary * 0.5) + (departmentAllowance * 0.5) + seniorityBonus + (performanceBonus * 0.5);
        }

        double taxRate;

        if (grossSalary > 50000) {
            taxRate = 0.35;
        } else if (grossSalary >= 30000) {
            taxRate = 0.30;
        } else if (grossSalary > 15000) {
            taxRate = 0.25;
        } else if (grossSalary > 5000) {
            taxRate = 0.15;
        } else {
            taxRate = 0.05;
        }

        double tax = grossSalary * taxRate;

        double deduction = 0.0;

        if (dept.equals("ENGINEERING") && emp.yearsOfService >= 10 && emp.performanceScore <= 2) {
            deduction = 200.0;
        }

        if (dept.equals("SALES") && emp.performanceScore == 1) {
            deduction += grossSalary * 0.05;
        }

        double netSalary = grossSalary - tax - deduction;

        if (netSalary < 0) {
            netSalary = 0.0;
        }

        return Math.round(netSalary * 100.0) / 100.0;
    }
}
