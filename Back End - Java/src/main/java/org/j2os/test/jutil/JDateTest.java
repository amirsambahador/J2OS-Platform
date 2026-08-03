package org.j2os.test.jutil;

import org.j2os.platform.jutil.date.JDate;

import java.sql.Timestamp;

/**
 * Standalone, dependency-free test suite for {@link JDate}.
 * <p>
 * This class intentionally does <b>not</b> use JUnit or any other testing
 * framework: it is a plain Java class with a {@code main} method that runs
 * every test case sequentially, prints a PASS/FAIL line for each one, and
 * prints a final summary. Run it directly:
 * <pre>{@code
 * javac org/j2os/platform/jutil/date/JDate.java org/j2os/test/jutil/JDateTest.java
 * java org.j2os.test.jutil.JDateTest
 * }</pre>
 * A non-zero process exit code indicates at least one failed test.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JDateTest {

    private static int passedCount = 0;
    private static int failedCount = 0;

    /**
     * Runs every test case in this suite and prints a final summary.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== JDate test suite ===");

        testDefaultConstructorProducesTodayInBothCalendars();
        testGregorianConstructorRoundTripsThroughGetGregorianDate();
        testGregorianToPersianKnownConversion();
        testPersianToGregorianKnownConversion();
        testPersianGregorianRoundTrip();
        testWeekDayNameForKnownDate();
        testPersianWeekDayNameForKnownDate();
        testIsLeapForKnownLeapYear();
        testIsLeapForKnownNonLeapYear();
        testIsLeapInstanceMethodMatchesPersianYear();
        testAddDaysForward();
        testAddDaysBackward();
        testNextDaySingle();
        testNextDayMultiple();
        testPreviousDaySingle();
        testPreviousDayMultiple();
        testAddDaysAcrossPersianYearBoundary();
        testAddDaysAcrossGregorianMonthBoundary();
        testGetGregorianDateTimestampValidInput();
        testGetPersianDateStringValidInput();
        testGetGregorianDateTimestampRejectsNull();
        testGetGregorianDateTimestampRejectsWrongPartCount();
        testGetGregorianDateTimestampRejectsNonNumeric();
        testGetGregorianDateTimestampRejectsZeroYear();
        testGetGregorianDateTimestampRejectsNegativeYear();
        testGetGregorianDateTimestampRejectsMonthTooLow();
        testGetGregorianDateTimestampRejectsMonthTooHigh();
        testGetGregorianDateTimestampRejectsDayTooLow();
        testGetGregorianDateTimestampRejectsDayTooHigh();
        testSetGregorianDateRejectsInvalidMonth();
        testSetPersianDateRejectsInvalidDay();
        testGetNumericDayAndMonthZeroPadding();
        testToStringContainsExpectedParts();
        testDayOfWeekIsWithinValidRange();
        testGregorianDateTimestampAndPersianDateStringAreInverses();
        testWhitespaceIsTrimmedFromDateStringParts();

        System.out.println();
        System.out.println("=== Summary: " + passedCount + " passed, " + failedCount + " failed ===");
        if (failedCount > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // Construction and basic round-trips
    // ------------------------------------------------------------------

    /** Verifies the no-arg constructor produces a well-formed date (yyyy/MM/dd) in both calendars. */
    private static void testDefaultConstructorProducesTodayInBothCalendars() {
        String testName = "testDefaultConstructorProducesTodayInBothCalendars";
        try {
            JDate date = new JDate();
            // Both representations must be non-empty and well-formed
            // (yyyy/MM/dd), regardless of what "today" actually is.
            assertTrue(testName, date.getGregorianDate().matches("\\d+/\\d{2}/\\d{2}"),
                    "Gregorian date should match yyyy/MM/dd, was: " + date.getGregorianDate());
            assertTrue(testName, date.getPersianDate().matches("\\d+/\\d{2}/\\d{2}"),
                    "Persian date should match yyyy/MM/dd, was: " + date.getPersianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies the Gregorian (year, month, day) constructor round-trips through getGregorianDate(). */
    private static void testGregorianConstructorRoundTripsThroughGetGregorianDate() {
        String testName = "testGregorianConstructorRoundTripsThroughGetGregorianDate";
        try {
            JDate date = new JDate(2024, 3, 20);
            assertEquals(testName, "2024/03/20", date.getGregorianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Known conversion pairs
    // Nowruz (Persian New Year) 1403 fell on 2024-03-20, a Wednesday.
    // ------------------------------------------------------------------

    /** Verifies a known Gregorian date converts to the expected Persian date. */
    private static void testGregorianToPersianKnownConversion() {
        String testName = "testGregorianToPersianKnownConversion";
        try {
            JDate date = new JDate(2024, 3, 20);
            assertEquals(testName, "1403/01/01", date.getPersianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies a known Persian date converts to the expected Gregorian date. */
    private static void testPersianToGregorianKnownConversion() {
        String testName = "testPersianToGregorianKnownConversion";
        try {
            JDate date = new JDate();
            date.setPersianDate(1403, 1, 1);
            assertEquals(testName, "2024/03/20", date.getGregorianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies converting Gregorian -> Persian -> Gregorian (via a fresh instance) round-trips to the same Persian date. */
    private static void testPersianGregorianRoundTrip() {
        String testName = "testPersianGregorianRoundTrip";
        try {
            JDate date = new JDate(1999, 12, 31);
            String gregorian = date.getGregorianDate();
            JDate roundTrip = new JDate();
            String[] parts = gregorian.split("/");
            roundTrip.setGregorianDate(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            assertEquals(testName, date.getPersianDate(), roundTrip.getPersianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Week day names
    // ------------------------------------------------------------------

    /** Verifies getWeekDayName() returns the correct English week day name for a known date. */
    private static void testWeekDayNameForKnownDate() {
        String testName = "testWeekDayNameForKnownDate";
        try {
            JDate date = new JDate(2024, 3, 20); // known Wednesday
            assertEquals(testName, "Wednesday", date.getWeekDayName());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getPersianWeekDayName() returns the correct Persian week day name for a known date. */
    private static void testPersianWeekDayNameForKnownDate() {
        String testName = "testPersianWeekDayNameForKnownDate";
        try {
            JDate date = new JDate(2024, 3, 20); // known Wednesday -> "چهار شنبه"
            assertEquals(testName, "چهار شنبه", date.getPersianWeekDayName());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Leap year detection
    // 1403 is a well-known recent Persian leap year (Esfand has 30 days).
    // ------------------------------------------------------------------

    /** Verifies isLeap(int) correctly identifies a known Persian leap year. */
    private static void testIsLeapForKnownLeapYear() {
        String testName = "testIsLeapForKnownLeapYear";
        try {
            JDate date = new JDate();
            assertTrue(testName, date.isLeap(1403), "1403 should be a Persian leap year");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies isLeap(int) correctly identifies a known non-leap Persian year. */
    private static void testIsLeapForKnownNonLeapYear() {
        String testName = "testIsLeapForKnownNonLeapYear";
        try {
            JDate date = new JDate();
            assertFalse(testName, date.isLeap(1402), "1402 should not be a Persian leap year");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies the no-arg isLeap() instance method agrees with isLeap(int) for the instance's own Persian year. */
    private static void testIsLeapInstanceMethodMatchesPersianYear() {
        String testName = "testIsLeapInstanceMethodMatchesPersianYear";
        try {
            JDate date = new JDate();
            date.setPersianDate(1403, 6, 15);
            assertEquals(testName, date.isLeap(1403), date.isLeap());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Day arithmetic
    // ------------------------------------------------------------------

    /** Verifies addDays(int) with a positive value moves the date forward correctly. */
    private static void testAddDaysForward() {
        String testName = "testAddDaysForward";
        try {
            JDate date = new JDate(2024, 3, 20);
            date.addDays(15);
            assertEquals(testName, "2024/04/04", date.getGregorianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies addDays(int) with a negative value moves the date backward correctly. */
    private static void testAddDaysBackward() {
        String testName = "testAddDaysBackward";
        try {
            JDate date = new JDate(2024, 3, 20);
            date.addDays(-20);
            assertEquals(testName, "2024/02/29", date.getGregorianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies nextDay() with no argument advances the date by exactly one day. */
    private static void testNextDaySingle() {
        String testName = "testNextDaySingle";
        try {
            JDate date = new JDate(2024, 3, 20);
            date.nextDay();
            assertEquals(testName, "2024/03/21", date.getGregorianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies nextDay(int) advances the date by the given number of days. */
    private static void testNextDayMultiple() {
        String testName = "testNextDayMultiple";
        try {
            JDate date = new JDate(2024, 3, 20);
            date.nextDay(5);
            assertEquals(testName, "2024/03/25", date.getGregorianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies previousDay() with no argument moves the date back by exactly one day. */
    private static void testPreviousDaySingle() {
        String testName = "testPreviousDaySingle";
        try {
            JDate date = new JDate(2024, 3, 20);
            date.previousDay();
            assertEquals(testName, "2024/03/19", date.getGregorianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies previousDay(int) moves the date back by the given number of days. */
    private static void testPreviousDayMultiple() {
        String testName = "testPreviousDayMultiple";
        try {
            JDate date = new JDate(2024, 3, 20);
            date.previousDay(5);
            assertEquals(testName, "2024/03/15", date.getGregorianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies advancing one day correctly crosses a Persian year boundary (1402 -> 1403). */
    private static void testAddDaysAcrossPersianYearBoundary() {
        String testName = "testAddDaysAcrossPersianYearBoundary";
        try {
            JDate date = new JDate();
            date.setPersianDate(1402, 12, 29); // last day of 1402 (non-leap)
            date.nextDay();
            assertEquals(testName, "1403/01/01", date.getPersianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies advancing one day correctly crosses a Gregorian month boundary (Jan 31 -> Feb 1). */
    private static void testAddDaysAcrossGregorianMonthBoundary() {
        String testName = "testAddDaysAcrossGregorianMonthBoundary";
        try {
            JDate date = new JDate(2024, 1, 31);
            date.nextDay();
            assertEquals(testName, "2024/02/01", date.getGregorianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Timestamp <-> Persian string conversions
    // ------------------------------------------------------------------

    /** Verifies getGregorianDateTimestamp(String) converts a valid Persian date string to the expected Timestamp. */
    private static void testGetGregorianDateTimestampValidInput() {
        String testName = "testGetGregorianDateTimestampValidInput";
        try {
            Timestamp timestamp = new JDate().getGregorianDateTimestamp("1403/01/01");
            Timestamp expected = Timestamp.valueOf("2024-03-20 00:00:00");
            assertEquals(testName, expected, timestamp);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getPersianDateString(Timestamp) converts a valid Timestamp to the expected Persian date string. */
    private static void testGetPersianDateStringValidInput() {
        String testName = "testGetPersianDateStringValidInput";
        try {
            String persian = new JDate().getPersianDateString(Timestamp.valueOf("2024-03-20 00:00:00"));
            assertEquals(testName, "1403/01/01", persian);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getGregorianDateTimestamp(String) and getPersianDateString(Timestamp) are inverses of each other. */
    private static void testGregorianDateTimestampAndPersianDateStringAreInverses() {
        String testName = "testGregorianDateTimestampAndPersianDateStringAreInverses";
        try {
            JDate date = new JDate();
            String originalPersianDate = "1400/05/10";
            Timestamp timestamp = date.getGregorianDateTimestamp(originalPersianDate);
            String roundTrippedPersianDate = date.getPersianDateString(timestamp);
            assertEquals(testName, originalPersianDate, roundTrippedPersianDate);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Validation / error handling
    // ------------------------------------------------------------------

    /** Verifies getGregorianDateTimestamp(String) rejects a null input. */
    private static void testGetGregorianDateTimestampRejectsNull() {
        String testName = "testGetGregorianDateTimestampRejectsNull";
        assertThrowsIllegalArgumentException(testName, () -> new JDate().getGregorianDateTimestamp(null));
    }

    /** Verifies getGregorianDateTimestamp(String) rejects a string with the wrong number of yyyy/MM/dd parts. */
    private static void testGetGregorianDateTimestampRejectsWrongPartCount() {
        String testName = "testGetGregorianDateTimestampRejectsWrongPartCount";
        assertThrowsIllegalArgumentException(testName, () -> new JDate().getGregorianDateTimestamp("1403/01"));
    }

    /** Verifies getGregorianDateTimestamp(String) rejects a non-numeric year part. */
    private static void testGetGregorianDateTimestampRejectsNonNumeric() {
        String testName = "testGetGregorianDateTimestampRejectsNonNumeric";
        assertThrowsIllegalArgumentException(testName, () -> new JDate().getGregorianDateTimestamp("abcd/01/01"));
    }

    /** Verifies getGregorianDateTimestamp(String) rejects a year of zero. */
    private static void testGetGregorianDateTimestampRejectsZeroYear() {
        String testName = "testGetGregorianDateTimestampRejectsZeroYear";
        assertThrowsIllegalArgumentException(testName, () -> new JDate().getGregorianDateTimestamp("0/01/01"));
    }

    /** Verifies getGregorianDateTimestamp(String) rejects a negative year. */
    private static void testGetGregorianDateTimestampRejectsNegativeYear() {
        String testName = "testGetGregorianDateTimestampRejectsNegativeYear";
        assertThrowsIllegalArgumentException(testName, () -> new JDate().getGregorianDateTimestamp("-1403/01/01"));
    }

    /** Verifies getGregorianDateTimestamp(String) rejects a month below the valid range (0). */
    private static void testGetGregorianDateTimestampRejectsMonthTooLow() {
        String testName = "testGetGregorianDateTimestampRejectsMonthTooLow";
        assertThrowsIllegalArgumentException(testName, () -> new JDate().getGregorianDateTimestamp("1403/00/01"));
    }

    /** Verifies getGregorianDateTimestamp(String) rejects a month above the valid range (13). */
    private static void testGetGregorianDateTimestampRejectsMonthTooHigh() {
        String testName = "testGetGregorianDateTimestampRejectsMonthTooHigh";
        assertThrowsIllegalArgumentException(testName, () -> new JDate().getGregorianDateTimestamp("1403/13/01"));
    }

    /** Verifies getGregorianDateTimestamp(String) rejects a day below the valid range (0). */
    private static void testGetGregorianDateTimestampRejectsDayTooLow() {
        String testName = "testGetGregorianDateTimestampRejectsDayTooLow";
        assertThrowsIllegalArgumentException(testName, () -> new JDate().getGregorianDateTimestamp("1403/01/00"));
    }

    /** Verifies getGregorianDateTimestamp(String) rejects a day above the valid range (32). */
    private static void testGetGregorianDateTimestampRejectsDayTooHigh() {
        String testName = "testGetGregorianDateTimestampRejectsDayTooHigh";
        assertThrowsIllegalArgumentException(testName, () -> new JDate().getGregorianDateTimestamp("1403/01/32"));
    }

    /** Verifies setGregorianDate(int, int, int) rejects an invalid month. */
    private static void testSetGregorianDateRejectsInvalidMonth() {
        String testName = "testSetGregorianDateRejectsInvalidMonth";
        assertThrowsIllegalArgumentException(testName, () -> new JDate().setGregorianDate(2024, 13, 1));
    }

    /** Verifies setPersianDate(int, int, int) rejects an invalid day. */
    private static void testSetPersianDateRejectsInvalidDay() {
        String testName = "testSetPersianDateRejectsInvalidDay";
        assertThrowsIllegalArgumentException(testName, () -> new JDate().setPersianDate(1403, 1, 0));
    }

    // ------------------------------------------------------------------
    // Formatting / misc
    // ------------------------------------------------------------------

    /** Verifies single-digit day/month values are zero-padded in the Gregorian date string. */
    private static void testGetNumericDayAndMonthZeroPadding() {
        String testName = "testGetNumericDayAndMonthZeroPadding";
        try {
            JDate date = new JDate(2024, 3, 5);
            assertEquals(testName, "2024/03/05", date.getGregorianDate());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies toString() includes the week day name, the Gregorian date, and the Persian date. */
    private static void testToStringContainsExpectedParts() {
        String testName = "testToStringContainsExpectedParts";
        try {
            JDate date = new JDate(2024, 3, 20);
            String text = date.toString();
            assertTrue(testName, text.contains("Wednesday"), "toString() should contain the week day name, was: " + text);
            assertTrue(testName, text.contains("2024/03/20"), "toString() should contain the Gregorian date, was: " + text);
            assertTrue(testName, text.contains("1403/01/01"), "toString() should contain the Persian date, was: " + text);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getDayOfWeek() always returns a value within the valid [0, 6] range. */
    private static void testDayOfWeekIsWithinValidRange() {
        String testName = "testDayOfWeekIsWithinValidRange";
        try {
            JDate date = new JDate();
            int dayOfWeek = date.getDayOfWeek();
            assertTrue(testName, dayOfWeek >= 0 && dayOfWeek <= 6, "Day of week should be in [0, 6], was: " + dayOfWeek);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getGregorianDateTimestamp(String) trims surrounding/internal whitespace from each date part. */
    private static void testWhitespaceIsTrimmedFromDateStringParts() {
        String testName = "testWhitespaceIsTrimmedFromDateStringParts";
        try {
            Timestamp timestamp = new JDate().getGregorianDateTimestamp(" 1403 / 01 / 01 ");
            Timestamp expected = Timestamp.valueOf("2024-03-20 00:00:00");
            assertEquals(testName, expected, timestamp);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Minimal assertion helpers (deliberately not using any test framework)
    // ------------------------------------------------------------------

    /** A no-argument, possibly-throwing action, used by {@link #assertThrowsIllegalArgumentException}. */
    @FunctionalInterface
    private interface ThrowingRunnable {
        /**
         * Runs the action.
         *
         * @throws Exception if the action fails
         */
        void run() throws Exception;
    }

    /**
     * Runs an action and records a pass if it throws {@link IllegalArgumentException}, or a
     * failure otherwise (whether nothing was thrown, or the wrong exception type was).
     *
     * @param testName the name of the test case, printed in the report
     * @param action   the action expected to throw
     */
    private static void assertThrowsIllegalArgumentException(String testName, ThrowingRunnable action) {
        try {
            action.run();
            fail(testName, "Expected IllegalArgumentException but no exception was thrown");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        } catch (Exception unexpectedException) {
            fail(testName, "Expected IllegalArgumentException but got: " + unexpectedException.getClass().getSimpleName());
        }
    }

    /**
     * Records a pass if {@code expected} equals {@code actual} (null-safe), otherwise a failure.
     *
     * @param testName the name of the test case, printed in the report
     * @param expected the expected value
     * @param actual   the actual value
     */
    private static void assertEquals(String testName, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            pass(testName);
        } else {
            fail(testName, "Expected <" + expected + "> but was <" + actual + ">");
        }
    }

    /**
     * Records a pass if {@code condition} is true, otherwise a failure with the given message.
     *
     * @param testName       the name of the test case, printed in the report
     * @param condition      the condition that must be true for the test to pass
     * @param failureMessage the message to report if {@code condition} is false
     */
    private static void assertTrue(String testName, boolean condition, String failureMessage) {
        if (condition) {
            pass(testName);
        } else {
            fail(testName, failureMessage);
        }
    }

    /**
     * Records a pass if {@code condition} is false, otherwise a failure with the given message.
     *
     * @param testName       the name of the test case, printed in the report
     * @param condition      the condition that must be false for the test to pass
     * @param failureMessage the message to report if {@code condition} is true
     */
    private static void assertFalse(String testName, boolean condition, String failureMessage) {
        assertTrue(testName, !condition, failureMessage);
    }

    /**
     * Records and prints a passing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void pass(String testName) {
        passedCount++;
        System.out.println("[PASS] " + testName);
    }

    /**
     * Records and prints a failing test case.
     *
     * @param testName the name of the test case, printed in the report
     * @param reason   why the test failed, printed alongside the test name
     */
    private static void fail(String testName, String reason) {
        failedCount++;
        System.out.println("[FAIL] " + testName + " -> " + reason);
    }
}
