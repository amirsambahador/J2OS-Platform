package org.j2os.platform.jutil.date;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Represents a single calendar date and provides conversion between the
 * Persian (Jalali) calendar and the Gregorian calendar.
 * <p>
 * Internally every instance keeps three synchronized representations of the
 * same point in time: the Persian date fields, the Gregorian date fields and
 * the corresponding Julian Day Number ({@link #julianDayNumber}), which acts
 * as the single source of truth used to derive the other two whenever the
 * date changes (see {@link #recomputeAllCalendars()}).
 * <p>
 * The conversion algorithm is based on the well-known Kazimierz Borkowski /
 * Roozbeh Pournader Jalali-Gregorian conversion algorithm, using
 * {@link #CALENDAR_BREAKPOINTS} to correctly account for the 33-year (and
 * irregular) leap cycles of the Persian calendar.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Today's date
 * JDate today = new JDate();
 * System.out.println(today); // e.g. Wednesday, Gregorian:[2024/03/20], Persian:[1403/01/01]
 *
 * // Build a date from Gregorian components and read it back as Persian
 * JDate date = new JDate(2024, 3, 20);
 * System.out.println(date.getPersianDate()); // "1403/01/01"
 *
 * // Build a date from Persian components
 * JDate persian = new JDate();
 * persian.setPersianDate(1403, 1, 1);
 * System.out.println(persian.getGregorianDate()); // "2024/03/20"
 *
 * // Move the date forward/backward
 * date.nextDay(10);
 * date.previousDay(3);
 * }</pre>
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JDate implements Serializable {

    /**
     * Break-point years (in the Persian calendar) used by the leap-year
     * algorithm to divide history into intervals with a consistent leap
     * cycle. Values come from the standard Jalali calendar conversion
     * algorithm and must not be modified.
     */
    private static final int[] CALENDAR_BREAKPOINTS = {
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
            1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    };

    /** Persian (Jalali) year component of the current date. */
    private int persianYear;

    /** Persian (Jalali) month component of the current date (1-12). */
    private int persianMonth;

    /** Persian (Jalali) day-of-month component of the current date. */
    private int persianDay;

    /** Gregorian year component of the current date. */
    private int gregorianYear;

    /** Gregorian month component of the current date (1-12). */
    private int gregorianMonth;

    /** Gregorian day-of-month component of the current date. */
    private int gregorianDay;

    /**
     * Julian Day Number of the current date. This is the canonical,
     * calendar-agnostic representation from which both the Persian and
     * Gregorian fields are recomputed whenever the date changes.
     */
    private int julianDayNumber;

    /**
     * Creates a new instance initialized to today's date, using the
     * system's default {@link GregorianCalendar}.
     */
    public JDate() {
        Calendar calendar = new GregorianCalendar();
        setGregorianDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * Creates a new instance initialized to the given Gregorian date.
     *
     * @param year  the Gregorian year
     * @param month the Gregorian month (1-12)
     * @param day   the Gregorian day of month (1-31)
     */
    public JDate(int year, int month, int day) {
        setGregorianDate(year, month, day);
    }

    /**
     * Private constructor that creates an uninitialized instance, used
     * internally as a scratch object by conversion helper methods so that a
     * temporary {@code JDate} can be built without going through the public
     * constructors (which require a fully valid date up front).
     *
     * @param skipInitialization unused marker parameter, only present to
     *                           distinguish this constructor's signature
     *                           from the public ones; the instance is left
     *                           completely uninitialized regardless of its
     *                           value
     */
    private JDate(boolean skipInitialization) {
        // intentionally does nothing
    }

    /**
     * Computes leap-cycle information for the given Persian year: the
     * corresponding Gregorian year, the Gregorian day of March on which
     * that Persian year starts, and the position of the year within its
     * 4-year leap cycle (0 indicates a leap year).
     *
     * @param targetPersianYear the Persian year to compute leap information for
     * @return the computed {@link LeapInfo} for the given year
     */
    private static LeapInfo computeLeapInfo(int targetPersianYear) {
        int breakpointYear, yearsIntoInterval, leapDaysJalali, leapDaysGregorian, previousBreakpointYear, breakpointIndex, intervalLength;
        int computedGregorianYear = targetPersianYear + 621;
        leapDaysJalali = -14;
        previousBreakpointYear = CALENDAR_BREAKPOINTS[0];

        // Walk through the known break-point years to find the interval
        // that contains the target year and accumulate the leap days
        // contributed by each fully-passed interval.
        breakpointIndex = 1;
        do {
            breakpointYear = CALENDAR_BREAKPOINTS[breakpointIndex];
            intervalLength = breakpointYear - previousBreakpointYear;
            if (targetPersianYear >= breakpointYear) {
                leapDaysJalali += (intervalLength / 33 * 8 + (intervalLength % 33) / 4);
                previousBreakpointYear = breakpointYear;
            }
            breakpointIndex++;
        } while ((breakpointIndex < 20) && (targetPersianYear >= breakpointYear));
        yearsIntoInterval = targetPersianYear - previousBreakpointYear;

        // Add the leap days contributed by the partial interval leading up
        // to the target year.
        leapDaysJalali += (yearsIntoInterval / 33 * 8 + ((yearsIntoInterval % 33) + 3) / 4);
        if (((intervalLength % 33) == 4) && ((intervalLength - yearsIntoInterval) == 4)) leapDaysJalali++;

        leapDaysGregorian = computedGregorianYear / 4 - ((computedGregorianYear / 100 + 1) * 3 / 4) - 150;
        int persianYearStartMarchDay = 20 + leapDaysJalali - leapDaysGregorian;

        // Near the end of an interval, look ahead into the next interval so
        // the leap-cycle remainder is computed relative to the correct
        // 33-year grouping.
        if ((intervalLength - yearsIntoInterval) < 6) {
            yearsIntoInterval = yearsIntoInterval - intervalLength + ((intervalLength + 4) / 33 * 33);
        }
        int leapCycleRemainder = (((yearsIntoInterval + 1) % 33) - 1) % 4;
        if (leapCycleRemainder == -1) leapCycleRemainder = 4;

        return new LeapInfo(computedGregorianYear, persianYearStartMarchDay, leapCycleRemainder);
    }

    /**
     * Parses a Persian date string in {@code yyyy/MM/dd} format and
     * converts it to the equivalent Gregorian {@link Timestamp} at
     * midnight.
     *
     * <p>Example:</p>
     * <pre>{@code
     * Timestamp timestamp = new JDate().getGregorianDateTimestamp("1403/01/01");
     * // timestamp -> 2024-03-20 00:00:00.0
     * }</pre>
     *
     * @param persianDateString the Persian date, formatted as {@code yyyy/MM/dd}
     * @return a {@link Timestamp} representing the start of the equivalent Gregorian day
     * @throws IllegalArgumentException if {@code persianDateString} is {@code null},
     *                                   is not in the expected format, or contains an
     *                                   invalid year, month or day
     */
    public Timestamp getGregorianDateTimestamp(String persianDateString) {
        int[] parsedDateComponents = parseDateString(persianDateString);
        JDate scratchDate = new JDate(true);
        scratchDate.setPersianDate(parsedDateComponents[0], parsedDateComponents[1], parsedDateComponents[2]);
        LocalDate localDate = LocalDate.of(scratchDate.getGregorianYear(), scratchDate.getGregorianMonth(), scratchDate.getGregorianDay());
        return Timestamp.valueOf(localDate.atStartOfDay());
    }

    /**
     * Converts a Gregorian {@link Timestamp} to the equivalent Persian date
     * string, formatted as {@code yyyy/MM/dd}.
     *
     * <p>Example:</p>
     * <pre>{@code
     * String persian = new JDate().getPersianDateString(Timestamp.valueOf("2024-03-20 00:00:00"));
     * // persian -> "1403/01/01"
     * }</pre>
     *
     * @param timestamp the Gregorian timestamp to convert
     * @return the equivalent Persian date, formatted as {@code yyyy/MM/dd}
     */
    public String getPersianDateString(Timestamp timestamp) {
        LocalDateTime localDateTime = timestamp.toLocalDateTime();
        JDate scratchDate = new JDate(true);
        scratchDate.setGregorianDate(localDateTime.getYear(), localDateTime.getMonthValue(), localDateTime.getDayOfMonth());
        return scratchDate.getPersianDate();
    }

    /**
     * Parses a date string in {@code yyyy/MM/dd} format into its numeric
     * components.
     *
     * @param dateString the date string to parse, formatted as {@code yyyy/MM/dd}
     * @return a 3-element array containing {@code [year, month, day]}
     * @throws IllegalArgumentException if {@code dateString} is {@code null},
     *                                   does not have exactly three
     *                                   {@code /}-separated parts, contains
     *                                   non-numeric parts, has a
     *                                   non-positive year, or has an
     *                                   out-of-range month or day
     */
    private int[] parseDateString(String dateString) {
        if (dateString == null) {
            throw new IllegalArgumentException("Date string must not be null");
        }
        String[] dateParts = dateString.trim().split("/");
        if (dateParts.length != 3) {
            throw new IllegalArgumentException("Expected format yyyy/MM/dd but got: \"" + dateString + "\"");
        }
        try {
            int year = Integer.parseInt(dateParts[0].trim());
            int month = Integer.parseInt(dateParts[1].trim());
            int day = Integer.parseInt(dateParts[2].trim());
            if (year <= 0) {
                throw new IllegalArgumentException("Year must be positive, got: " + year);
            }
            validateMonthAndDay(month, day);
            return new int[]{year, month, day};
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException("Expected format yyyy/MM/dd but got: \"" + dateString + "\"", numberFormatException);
        }
    }

    /**
     * Validates that a month/day pair falls within the generic accepted
     * ranges (1-12 for month, 1-31 for day). This is a coarse, calendar-
     * agnostic pre-check only (used while parsing a raw date string, before
     * it is known to be a Persian or Gregorian date) - it does not verify
     * the day against the actual number of days in the given month. The
     * precise, calendar-aware checks used by {@link #setGregorianDate(int, int, int)}
     * and {@link #setPersianDate(int, int, int)} are
     * {@link #validateGregorianDate(int, int, int)} and
     * {@link #validatePersianDate(int, int, int)} respectively.
     *
     * @param month the month to validate (expected 1-12)
     * @param day   the day of month to validate (expected 1-31)
     * @throws IllegalArgumentException if {@code month} is not between 1
     *                                   and 12, or {@code day} is not
     *                                   between 1 and 31
     */
    private void validateMonthAndDay(int month, int day) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12, got: " + month);
        }
        if (day < 1 || day > 31) {
            throw new IllegalArgumentException("Day must be between 1 and 31, got: " + day);
        }
    }

    /**
     * Determines whether the given Gregorian year is a leap year (standard
     * Gregorian leap-year rule).
     *
     * @param year the Gregorian year
     * @return {@code true} if {@code year} is a Gregorian leap year
     */
    private static boolean isLeapGregorian(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    /**
     * Returns the number of days in the given Gregorian month/year,
     * accounting for leap years.
     *
     * @param year  the Gregorian year
     * @param month the Gregorian month (1-12)
     * @return the number of days in that month
     */
    private static int gregorianDaysInMonth(int year, int month) {
        int[] daysInMonth = {31, isLeapGregorian(year) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        return daysInMonth[month - 1];
    }

    /**
     * Validates a full Gregorian date, rejecting not just an out-of-range
     * month but also a day that does not actually exist in that
     * month/year (e.g. {@code 2024/02/30}, or February 29 in a non-leap
     * year) - unlike the coarse {@link #validateMonthAndDay(int, int)}
     * check, which previously let such dates through and had them silently
     * normalized into a different, unrelated date instead of being
     * rejected.
     *
     * @param year  the Gregorian year
     * @param month the Gregorian month, expected 1-12
     * @param day   the Gregorian day of month
     * @throws IllegalArgumentException if {@code month} is not between 1
     *                                   and 12, or {@code day} is not a
     *                                   valid day of that Gregorian
     *                                   month/year
     */
    private void validateGregorianDate(int year, int month, int day) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12, got: " + month);
        }
        int maxDay = gregorianDaysInMonth(year, month);
        if (day < 1 || day > maxDay) {
            throw new IllegalArgumentException("Day must be between 1 and " + maxDay
                    + " for Gregorian " + year + "/" + month + ", got: " + day);
        }
    }

    /**
     * Validates a full Persian (Jalali) date, rejecting not just an
     * out-of-range month but also a day that does not actually exist in
     * that month/year - months 1-6 have 31 days, months 7-11 have 30 days,
     * and month 12 (Esfand) has 29 or 30 days depending on whether
     * {@code year} is a Persian leap year (see {@link #isLeap(int)}) -
     * unlike the coarse {@link #validateMonthAndDay(int, int)} check,
     * which previously let such dates through and had them silently
     * normalized into a different, unrelated date instead of being
     * rejected.
     *
     * @param year  the Persian year
     * @param month the Persian month, expected 1-12
     * @param day   the Persian day of month
     * @throws IllegalArgumentException if {@code month} is not between 1
     *                                   and 12, or {@code day} is not a
     *                                   valid day of that Persian
     *                                   month/year
     */
    private void validatePersianDate(int year, int month, int day) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12, got: " + month);
        }
        int maxDay;
        if (month <= 6) {
            maxDay = 31;
        } else if (month <= 11) {
            maxDay = 30;
        } else {
            maxDay = isLeap(year) ? 30 : 29;
        }
        if (day < 1 || day > maxDay) {
            throw new IllegalArgumentException("Day must be between 1 and " + maxDay
                    + " for Persian " + year + "/" + month + ", got: " + day);
        }
    }

    /**
     * Formats a day or month numeric value with a leading zero when it is
     * a single digit, so that date components are always rendered with two
     * digits.
     *
     * @param dateValue the day-of-month or month value to format
     * @return the value as a zero-padded two-character string
     */
    private String getNumericDayAndMonth(int dateValue) {
        if (dateValue < 10) return "0" + dateValue;
        return String.valueOf(dateValue);
    }

    /**
     * Returns the Persian-language name of the day of week for this date.
     *
     * @return the Persian name of the day of the week
     */
    public String getPersianWeekDayName() {
        String[] weekDayNamesPersian = {"سه شنبه", "چهار شنبه", "پنج شنبه", "جمعه", "شنبه", "یک شنبه", "دو شنبه"};
        int dayIndex = (getDayOfWeek() + 6) % 7;
        return weekDayNamesPersian[dayIndex];
    }

    /**
     * Returns the Persian (Jalali) year component of this date.
     *
     * @return the Persian year
     */
    public int getPersianYear() {
        return persianYear;
    }

    /**
     * Returns the Persian (Jalali) month component of this date.
     *
     * @return the Persian month (1-12)
     */
    public int getPersianMonth() {
        return persianMonth;
    }

    /**
     * Returns the Persian (Jalali) day-of-month component of this date.
     *
     * @return the Persian day of month
     */
    public int getPersianDay() {
        return persianDay;
    }

    /**
     * Returns the Gregorian year component of this date.
     *
     * @return the Gregorian year
     */
    public int getGregorianYear() {
        return gregorianYear;
    }

    /**
     * Returns the Gregorian month component of this date.
     *
     * @return the Gregorian month (1-12)
     */
    public int getGregorianMonth() {
        return gregorianMonth;
    }

    /**
     * Returns the Gregorian day-of-month component of this date.
     *
     * @return the Gregorian day of month
     */
    public int getGregorianDay() {
        return gregorianDay;
    }

    /**
     * Returns the Persian representation of this date, formatted as
     * {@code yyyy/MM/dd} with zero-padded month and day.
     *
     * @return the Persian date string
     */
    public String getPersianDate() {
        return (persianYear + "/" + getNumericDayAndMonth(persianMonth) + "/" + getNumericDayAndMonth(persianDay));
    }

    /**
     * Returns the Gregorian representation of this date, formatted as
     * {@code yyyy/MM/dd} with zero-padded month and day.
     *
     * @return the Gregorian date string
     */
    public String getGregorianDate() {
        return (gregorianYear + "/" + getNumericDayAndMonth(gregorianMonth) + "/" + getNumericDayAndMonth(gregorianDay));
    }

    /**
     * Returns the English-language name of the day of week for this date.
     *
     * @return the English name of the day of the week
     */
    public String getWeekDayName() {
        String[] weekDayNames = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        return (weekDayNames[getDayOfWeek()]);
    }

    /**
     * Returns a human-readable summary of this date, including the day of
     * week, and both the Gregorian and Persian representations.
     *
     * @return a string describing this date
     */
    public String toString() {
        return (getWeekDayName() + ", Gregorian:[" + getGregorianDate() + "], Persian:[" + getPersianDate() + "]");
    }

    /**
     * Returns the zero-based day of week for this date, where {@code 0} is
     * Monday and {@code 6} is Sunday.
     *
     * @return the day of week, in the range {@code [0, 6]}
     */
    public int getDayOfWeek() {
        return (((julianDayNumber % 7) + 7) % 7);
    }

    /**
     * Shifts this date by the given number of days (positive to move
     * forward, negative to move backward) and recomputes both the Persian
     * and Gregorian representations.
     *
     * <p>Example:</p>
     * <pre>{@code
     * JDate date = new JDate(2024, 3, 20);
     * date.addDays(15);
     * System.out.println(date.getGregorianDate()); // "2024/04/04"
     * }</pre>
     *
     * @param daysToAdd the number of days to add; may be negative
     */
    public void addDays(int daysToAdd) {
        julianDayNumber += daysToAdd;
        recomputeAllCalendars();
    }

    /**
     * Advances this date by one day.
     */
    public void nextDay() {
        addDays(1);
    }

    /**
     * Advances this date by the given number of days.
     *
     * @param days the number of days to advance by
     */
    public void nextDay(int days) {
        addDays(days);
    }

    /**
     * Moves this date back by one day.
     */
    public void previousDay() {
        addDays(-1);
    }

    /**
     * Moves this date back by the given number of days.
     *
     * @param days the number of days to move back by
     */
    public void previousDay(int days) {
        addDays(-days);
    }

    /**
     * Sets this instance to the given Persian (Jalali) date and recomputes
     * the Julian Day Number and the Gregorian representation accordingly.
     *
     * @param year  the Persian year
     * @param month the Persian month (1-12)
     * @param day   the Persian day of month (1-31, further constrained by
     *              {@code month} - see {@link #validatePersianDate(int, int, int)})
     * @throws IllegalArgumentException if {@code month} is not between 1
     *                                   and 12, or {@code day} is not a
     *                                   valid day of that Persian
     *                                   month/year
     */
    public void setPersianDate(int year, int month, int day) {
        validatePersianDate(year, month, day);
        persianYear = year;
        persianMonth = month;
        persianDay = day;
        julianDayNumber = persianDateToJulianDayNumber();
        recomputeAllCalendars();
    }

    /**
     * Sets this instance to the given Gregorian date and recomputes the
     * Julian Day Number and the Persian representation accordingly.
     *
     * @param year  the Gregorian year
     * @param month the Gregorian month (1-12)
     * @param day   the Gregorian day of month (1-31, further constrained
     *              by {@code month} and leap year - see
     *              {@link #validateGregorianDate(int, int, int)})
     * @throws IllegalArgumentException if {@code month} is not between 1
     *                                   and 12, or {@code day} is not a
     *                                   valid day of that Gregorian
     *                                   month/year
     */
    public void setGregorianDate(int year, int month, int day) {
        validateGregorianDate(year, month, day);
        gregorianYear = year;
        gregorianMonth = month;
        gregorianDay = day;
        julianDayNumber = gregorianDateToJulianDayNumber(year, month, day);
        recomputeAllCalendars();
    }

    /**
     * Recomputes both the Persian and Gregorian date fields from the
     * current {@link #julianDayNumber}, keeping all three representations
     * in sync.
     */
    private void recomputeAllCalendars() {
        julianDayNumberToPersianDate();
        julianDayNumberToGregorianDate();
    }

    /**
     * Determines whether the given Persian year is a leap year.
     *
     * <p>Example:</p>
     * <pre>{@code
     * boolean leap = new JDate().isLeap(1403); // true
     * }</pre>
     *
     * @param year the Persian year to check
     * @return {@code true} if the given Persian year is a leap year
     */
    public boolean isLeap(int year) {
        LeapInfo leapInfo = computeLeapInfo(year);
        return leapInfo.leapCycleRemainder == 0;
    }

    /**
     * Determines whether the Persian year of this date is a leap year.
     *
     * @return {@code true} if this date's Persian year is a leap year
     */
    public boolean isLeap() {
        return isLeap(persianYear);
    }

    /**
     * Converts the current Persian date fields ({@link #persianYear},
     * {@link #persianMonth}, {@link #persianDay}) into a Julian Day Number.
     *
     * @return the Julian Day Number corresponding to the current Persian date fields
     */
    private int persianDateToJulianDayNumber() {
        LeapInfo leapInfo = computeLeapInfo(persianYear);
        return (gregorianDateToJulianDayNumber(leapInfo.gregorianYear, 3, leapInfo.persianYearStartMarchDay)
                + (persianMonth - 1) * 31 - persianMonth / 7 * (persianMonth - 7) + persianDay - 1);
    }

    /**
     * Recomputes {@link #persianYear}, {@link #persianMonth} and
     * {@link #persianDay} from the current {@link #julianDayNumber}.
     * <p>
     * Relies on {@link #gregorianYear} already having been computed (via
     * {@link #julianDayNumberToGregorianDate()}) to determine the
     * approximate Persian year before locating the exact month and day
     * within it.
     */
    private void julianDayNumberToPersianDate() {
        julianDayNumberToGregorianDate();
        persianYear = gregorianYear - 621;
        LeapInfo leapInfo = computeLeapInfo(persianYear);
        int firstDayOfPersianYearAsJulianDayNumber = gregorianDateToJulianDayNumber(leapInfo.gregorianYear, 3, leapInfo.persianYearStartMarchDay);
        int dayOffsetInPersianYear = julianDayNumber - firstDayOfPersianYearAsJulianDayNumber;
        if (dayOffsetInPersianYear >= 0) {
            if (dayOffsetInPersianYear <= 185) {
                // Within the first six 31-day months (Farvardin-Shahrivar).
                persianMonth = 1 + dayOffsetInPersianYear / 31;
                persianDay = (dayOffsetInPersianYear % 31) + 1;
                return;
            } else dayOffsetInPersianYear -= 186;
        } else {
            // The Julian Day Number falls before the computed start of the
            // estimated Persian year, so it actually belongs to the
            // previous Persian year; adjust accordingly.
            persianYear--;
            dayOffsetInPersianYear += 179;
            if (leapInfo.leapCycleRemainder == 1) dayOffsetInPersianYear++;
        }
        // Within the last six months (Mehr-Esfand), which have 30 days each
        // (29 in Esfand on non-leap years).
        persianMonth = 7 + dayOffsetInPersianYear / 30;
        persianDay = (dayOffsetInPersianYear % 30) + 1;
    }

    /**
     * Converts a Gregorian date into its Julian Day Number.
     *
     * @param year  the Gregorian year
     * @param month the Gregorian month (1-12)
     * @param day   the Gregorian day of month
     * @return the corresponding Julian Day Number
     */
    private int gregorianDateToJulianDayNumber(int year, int month, int day) {
        int julianDayNumberResult = (year + (month - 8) / 6 + 100100) * 1461 / 4 + (153 * ((month + 9) % 12) + 2) / 5 + day - 34840408;
        julianDayNumberResult = julianDayNumberResult - (year + 100100 + (month - 8) / 6) / 100 * 3 / 4 + 752;
        return (julianDayNumberResult);
    }

    /**
     * Recomputes {@link #gregorianYear}, {@link #gregorianMonth} and
     * {@link #gregorianDay} from the current {@link #julianDayNumber}.
     */
    private void julianDayNumberToGregorianDate() {
        int adjustedJulianDayNumber = 4 * julianDayNumber + 139361631;
        adjustedJulianDayNumber = adjustedJulianDayNumber + (((((4 * julianDayNumber + 183187720) / 146097) * 3) / 4) * 4 - 3908);
        int dayOfYearIndex = ((adjustedJulianDayNumber % 1461) / 4) * 5 + 308;
        gregorianDay = (dayOfYearIndex % 153) / 5 + 1;
        gregorianMonth = ((dayOfYearIndex / 153) % 12) + 1;
        gregorianYear = adjustedJulianDayNumber / 1461 - 100100 + (8 - gregorianMonth) / 6;
    }

    /**
     * Immutable holder for the intermediate values produced by
     * {@link JDate#computeLeapInfo(int)}: the Gregorian year corresponding
     * to a Persian year, the Gregorian day of March that Persian year
     * starts on, and that year's position within its leap cycle.
     *
     * @param gregorianYear            the Gregorian year corresponding to the target Persian year
     * @param persianYearStartMarchDay the day of March (in the Gregorian calendar) on which the Persian year begins
     * @param leapCycleRemainder       the year's position within its 4-year leap cycle; {@code 0} means the year is a leap year
     */
    private record LeapInfo(int gregorianYear, int persianYearStartMarchDay, int leapCycleRemainder) {
    }
}