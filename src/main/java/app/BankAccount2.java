package app;

public class BankAccount2 {

    public double processTransaction(double currentBalance, double amount,
                                     int accountType, boolean isOverdraftEnabled) {
        if (accountType < 0 || accountType > 2) {
            return -999999.0;
        }

        if (amount == 0) {
            return currentBalance;
        }

        double newBalance;
        double fee = 0.0;

        if (amount > 0) {
            if (amount > 50000) {
                fee = amount * 0.001;
            }

            double bonus = 0.0;
            if (accountType == 2 && amount >= 1000) {
                bonus = amount * 0.005;
            }

            newBalance = currentBalance + amount + bonus - fee;

        } else {
            double withdrawAmount = Math.abs(amount);

            if (accountType == 0) {
                if (withdrawAmount > 1000) {
                    fee = 5.0;
                } else {
                    fee = 2.0;
                }
            } else if (accountType == 1) {
                if (withdrawAmount > 5000) {
                    fee = 3.0;
                }
            }

            double totalDeduction = withdrawAmount + fee;

            if (totalDeduction > currentBalance) {
                if (isOverdraftEnabled) {
                    double overdraftLimit;
                    if (accountType == 2) {
                        overdraftLimit = 10000.0;
                    } else if (accountType == 1) {
                        overdraftLimit = 5000.0;
                    } else {
                        overdraftLimit = 1000.0;
                    }

                    if (totalDeduction - currentBalance <= overdraftLimit) {
                        double overdraftFee = currentBalance * 0.05;
                        newBalance = currentBalance - totalDeduction - overdraftFee;
                    } else {
                        return -888888.0;
                    }
                } else {
                    return -777777.0;
                }
            } else {
                newBalance = currentBalance - totalDeduction;
            }
        }

        if (newBalance < 0 && !isOverdraftEnabled) {
            return -777777.0;
        }

        return Math.round(newBalance * 100.0) / 100.0;
    }
}
