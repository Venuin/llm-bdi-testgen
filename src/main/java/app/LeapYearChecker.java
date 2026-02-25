package app;

/**
 * Leap Year and Calendar Algorithm
 * 
 * Computes the number of days in a given month, taking into account
 * the leap year rules of the Gregorian calendar.
 * 
 * Benchmark Category: EASY-MEDIUM
 * Key Challenge: Modular arithmetic conditions (divisibility by 4, 100, 400)
 *                combined with month-specific branching logic.
 */
public class LeapYearChecker {

    /**
     * Returns the number of days in the specified month and year.
     *
     * @param year  the calendar year (must be >= 1)
     * @param month the month number (1-12)
     * @return number of days, or a negative error code for invalid input
     */
    public int getDaysInMonth(int year, int month) {
        // Validate year
        if (year < 1) {
            return -1;
        }

        // Validate month
        if (month < 1 || month > 12) {
            return -2;
        }

        // Determine leap year using Gregorian rules
        boolean isLeapYear;
        if (year % 400 == 0) {
            isLeapYear = true;
        } else if (year % 100 == 0) {
            isLeapYear = false;
        } else if (year % 4 == 0) {
            isLeapYear = true;
        } else {
            isLeapYear = false;
        }

        // Compute days in month
        int days;
        if (month == 2) {
            if (isLeapYear) {
                days = 29;
            } else {
                days = 28;
            }
        } else if (month == 4 || month == 6 || month == 9 || month == 11) {
            days = 30;
        } else {
            days = 31;
        }

        return days;
    }
}
