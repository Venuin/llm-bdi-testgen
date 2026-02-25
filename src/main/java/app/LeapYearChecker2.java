package app;

public class LeapYearChecker2 {

    public int getDaysInMonth(int year, int month) {
        if (year < 1) {
            return -1;
        }

        if (month < 1 || month > 12) {
            return -2;
        }

        boolean isLeapYear;
        if (year % 4 == 0) {
            isLeapYear = true;
        } else if (year % 100 == 0) {
            isLeapYear = false;
        } else if (year % 400 == 0) {
            isLeapYear = true;
        } else {
            isLeapYear = false;
        }

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
