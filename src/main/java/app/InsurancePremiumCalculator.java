package app;

/**
 * Insurance Premium Calculator
 * 
 * Calculates monthly insurance premiums based on multiple risk factors
 * including age, income, smoking status, risk score, and chronic illness.
 * 
 * Benchmark Category: VERY HARD
 * Key Challenge: Five parameters with deep nesting, multiplicative interactions
 *                (smoker × chronic disease), and non-trivial boundary conditions
 *                across tiered brackets.
 */
public class InsurancePremiumCalculator {

    /**
     * Calculate the monthly insurance premium.
     *
     * @param age               applicant age (18–100)
     * @param annualIncome      applicant annual income (>= 0)
     * @param isSmoker          true if the applicant is a smoker
     * @param riskScore         risk assessment score (0–100)
     * @param hasChronicDisease true if the applicant has a chronic disease
     * @return monthly premium, or -1.0 for invalid input
     */
    public double calculatePremium(int age, double annualIncome, boolean isSmoker,
                                   int riskScore, boolean hasChronicDisease) {
        // ===== INPUT VALIDATION =====
        if (age < 18 || age > 100) {
            return -1.0;
        }
        if (annualIncome < 0) {
            return -1.0;
        }
        if (riskScore < 0 || riskScore > 100) {
            return -1.0;
        }

        // ===== 1. BASE PREMIUM BY AGE BRACKET =====
        double basePremium;
        if (age < 25) {
            basePremium = 150.0;
        } else if (age < 35) {
            basePremium = 120.0;
        } else if (age < 45) {
            basePremium = 180.0;
        } else if (age < 55) {
            basePremium = 250.0;
        } else if (age < 65) {
            basePremium = 350.0;
        } else {
            basePremium = 500.0;
        }

        // ===== 2. SMOKING SURCHARGE =====
        double smokingMultiplier = 1.0;
        if (isSmoker) {
            if (age >= 50) {
                smokingMultiplier = 2.5;
            } else if (age >= 35) {
                smokingMultiplier = 2.0;
            } else {
                smokingMultiplier = 1.5;
            }
        }

        // ===== 3. RISK SCORE ADJUSTMENT =====
        double riskMultiplier;
        if (riskScore <= 20) {
            riskMultiplier = 0.8;
        } else if (riskScore <= 40) {
            riskMultiplier = 0.9;
        } else if (riskScore <= 60) {
            riskMultiplier = 1.0;
        } else if (riskScore <= 80) {
            riskMultiplier = 1.3;
        } else {
            riskMultiplier = 1.6;
        }

        // ===== 4. CHRONIC DISEASE SURCHARGE =====
        double chronicSurcharge = 0.0;
        if (hasChronicDisease) {
            if (age >= 60) {
                chronicSurcharge = basePremium * 0.8;
            } else if (age >= 40) {
                chronicSurcharge = basePremium * 0.5;
            } else {
                chronicSurcharge = basePremium * 0.3;
            }

            // Compound risk: smoker with chronic disease
            if (isSmoker) {
                chronicSurcharge *= 1.5;
            }
        }

        // ===== 5. INCOME-BASED ADJUSTMENT =====
        double incomeAdjustment = 1.0;
        if (annualIncome < 20000) {
            incomeAdjustment = 0.85;
        } else if (annualIncome > 150000) {
            incomeAdjustment = 1.15;
        }

        // ===== 6. COMPUTE FINAL PREMIUM =====
        double premium = (basePremium * smokingMultiplier * riskMultiplier
                          + chronicSurcharge) * incomeAdjustment;

        // Maximum cap
        if (premium > 2000.0) {
            premium = 2000.0;
        }

        // Minimum floor
        if (premium < 50.0) {
            premium = 50.0;
        }

        return Math.round(premium * 100.0) / 100.0;
    }
}
