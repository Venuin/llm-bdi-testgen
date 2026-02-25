package app;

public class InsurancePremiumCalculator2 {

    public double calculatePremium(int age, double annualIncome, boolean isSmoker,
                                   int riskScore, boolean hasChronicDisease) {
        if (age < 18 || age > 100) {
            return -1.0;
        }
        if (annualIncome < 0) {
            return -1.0;
        }
        if (riskScore < 0 || riskScore > 100) {
            return -1.0;
        }

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

        double smokingMultiplier = 1.0;
        if (isSmoker) {
            if (age >= 50) {
                smokingMultiplier = 1.5;
            } else if (age >= 35) {
                smokingMultiplier = 2.0;
            } else {
                smokingMultiplier = 2.5;
            }
        }

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

        double chronicSurcharge = 0.0;
        if (hasChronicDisease) {
            if (age >= 60) {
                chronicSurcharge = basePremium * 0.8;
            } else if (age >= 40) {
                chronicSurcharge = basePremium * 0.5;
            } else {
                chronicSurcharge = basePremium * 0.3;
            }

            if (isSmoker) {
                chronicSurcharge *= 1.5;
            }
        }

        double incomeAdjustment = 1.0;
        if (annualIncome < 20000) {
            incomeAdjustment = 0.85;
        } else if (annualIncome > 150000) {
            incomeAdjustment = 1.15;
        }

        double premium = (basePremium * smokingMultiplier * riskMultiplier
                          + chronicSurcharge) * incomeAdjustment;

        if (premium < 2000.0) {
            premium = 2000.0;
        }

        if (premium < 50.0) {
            premium = 50.0;
        }

        return Math.round(premium * 100.0) / 100.0;
    }
}
