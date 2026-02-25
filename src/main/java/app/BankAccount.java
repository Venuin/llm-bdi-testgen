package app;

/**
 * Bank Transaction Processor
 * 
 * Processes deposits and withdrawals with account-type-dependent fees,
 * overdraft protection, and VIP bonuses.
 * 
 * Benchmark Category: HARD
 * Key Challenge: Many interdependent conditions across account types,
 *                transaction directions, overdraft logic, and fee calculations.
 *                Derived variables (totalDeduction) create deep sub-logic paths.
 */
public class BankAccount {

    /**
     * Process a bank transaction and return the resulting balance.
     *
     * @param currentBalance the current account balance
     * @param amount          positive for deposit, negative for withdrawal
     * @param accountType     0 = Basic, 1 = Premium, 2 = VIP
     * @param isOverdraftEnabled whether overdraft protection is active
     * @return new balance, or a special error code:
     *         -999999.0 = invalid account type
     *         -888888.0 = overdraft limit exceeded
     *         -777777.0 = insufficient funds
     */
    public double processTransaction(double currentBalance, double amount,
                                     int accountType, boolean isOverdraftEnabled) {
        // Validate account type
        if (accountType < 0 || accountType > 2) {
            return -999999.0;
        }

        // No-op for zero amount
        if (amount == 0) {
            return currentBalance;
        }

        double newBalance;
        double fee = 0.0;

        // ===== DEPOSIT PATH =====
        if (amount > 0) {
            // Anti-money-laundering fee for large deposits
            if (amount > 50000) {
                fee = amount * 0.001;
            }

            // VIP deposit bonus
            double bonus = 0.0;
            if (accountType == 2 && amount >= 10000) {
                bonus = amount * 0.005;
            }

            newBalance = currentBalance + amount + bonus - fee;

        // ===== WITHDRAWAL PATH =====
        } else {
            double withdrawAmount = Math.abs(amount);

            // Account-type-based transaction fee
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
            // VIP: no fee

            double totalDeduction = withdrawAmount + fee;

            // Insufficient funds handling
            if (totalDeduction > currentBalance) {
                if (isOverdraftEnabled) {
                    // Overdraft limit depends on account type
                    double overdraftLimit;
                    if (accountType == 2) {
                        overdraftLimit = 10000.0;
                    } else if (accountType == 1) {
                        overdraftLimit = 5000.0;
                    } else {
                        overdraftLimit = 1000.0;
                    }

                    if (totalDeduction - currentBalance <= overdraftLimit) {
                        double overdraftFee = (totalDeduction - currentBalance) * 0.05;
                        newBalance = currentBalance - totalDeduction - overdraftFee;
                    } else {
                        return -888888.0; // Overdraft limit exceeded
                    }
                } else {
                    return -777777.0; // Insufficient funds
                }
            } else {
                newBalance = currentBalance - totalDeduction;
            }
        }

        // Safety check
        if (newBalance < 0 && !isOverdraftEnabled) {
            return -777777.0;
        }

        return Math.round(newBalance * 100.0) / 100.0;
    }
}
