package org.j2os.test.jutil;

import org.j2os.platform.jutil.number.JNumber;

/**
 * Standalone, dependency-free test suite for {@link JNumber}.
 * <p>
 * This class intentionally does <b>not</b> use JUnit or any other testing
 * framework: it is a plain Java class with a {@code main} method that runs
 * every test case sequentially, prints a PASS/FAIL line for each one, and
 * prints a final summary. Run it directly:
 * <pre>{@code
 * javac -d out org/j2os/platform/jutil/number/JNumber.java org/j2os/test/jutil/JNumberTest.java
 * java -Dfile.encoding=UTF-8 -cp out org.j2os.test.jutil.JNumberTest
 * }</pre>
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JNumberTest {

    private static int passedCount = 0;
    private static int failedCount = 0;

    /**
     * Runs every test case in this suite and prints a final summary.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== JNumber test suite ===");

        // getEnglishWords
        testEnglishWordsForZero();
        testEnglishWordsForSingleDigit();
        testEnglishWordsForTeen();
        testEnglishWordsForTensAndOnes();
        testEnglishWordsForExactHundred();
        testEnglishWordsForHundredsWithRemainder();
        testEnglishWordsForMaxThreeDigitGroup();
        testEnglishWordsForExactThousand();
        testEnglishWordsForThousandWithRemainder();
        testEnglishWordsForMillionsRange();
        testEnglishWordsForNegativeDecimal();
        testEnglishWordsForNegativeZeroHasNoNegativeWord();
        testEnglishWordsStripsLeadingZeros();
        testEnglishWordsAcceptsCommaFormattedInput();
        testEnglishWordsForNullReturnsEmptyString();
        testEnglishWordsForEmptyStringReturnsEmptyString();
        testEnglishWordsRejectsNonDigitCharacters();
        testEnglishWordsRejectsMultipleDecimalPoints();
        testEnglishWordsRejectsNumberTooLargeToConvert();

        // getPersianWords
        testPersianWordsForZero();
        testPersianWordsForCompoundNumberUsesJoiner();
        testPersianWordsForExactThousand();
        testPersianWordsForNegativeDecimal();

        // getPersianNumber
        testPersianNumberGroupsThousands();
        testPersianNumberWithNegativeDecimal();
        testPersianNumberForZero();
        testPersianNumberForNegativeZeroHasNoMinusSign();
        testPersianNumberForNullReturnsEmptyString();

        // getEnglishNumber
        testEnglishNumberConvertsFromPersianDigits();
        testEnglishNumberWithNegativeDecimalUsesPersianSeparator();
        testEnglishNumberForNullReturnsEmptyString();
        testEnglishNumberStripsStrayWhitespace();
        testEnglishNumberStripsNonDigitJunkCharacters();
        testEnglishNumberHandlesMultipleDecimalPointsGracefully();

        // getEnglishNumberWithoutCommas
        testEnglishNumberWithoutCommasStripsSeparators();
        testEnglishNumberWithoutCommasNormalizesPersianSeparator();
        testEnglishNumberWithoutCommasRemovesTrailingDot();
        testEnglishNumberWithoutCommasForNullReturnsEmptyString();

        System.out.println();
        System.out.println("=== Summary: " + passedCount + " passed, " + failedCount + " failed ===");
        if (failedCount > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // getEnglishWords
    // ------------------------------------------------------------------

    /** Verifies getEnglishWords("0") returns "Zero". */
    private static void testEnglishWordsForZero() {
        String testName = "testEnglishWordsForZero";
        try {
            assertEquals(testName, "Zero", JNumber.getEnglishWords("0"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords converts a single digit correctly. */
    private static void testEnglishWordsForSingleDigit() {
        String testName = "testEnglishWordsForSingleDigit";
        try {
            assertEquals(testName, "Seven", JNumber.getEnglishWords("7"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords converts a teen number (10-19) correctly. */
    private static void testEnglishWordsForTeen() {
        String testName = "testEnglishWordsForTeen";
        try {
            assertEquals(testName, "Fifteen", JNumber.getEnglishWords("15"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords converts a two-digit tens+ones number correctly. */
    private static void testEnglishWordsForTensAndOnes() {
        String testName = "testEnglishWordsForTensAndOnes";
        try {
            assertEquals(testName, "Forty Two", JNumber.getEnglishWords("42"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords converts an exact hundred with no remainder correctly. */
    private static void testEnglishWordsForExactHundred() {
        String testName = "testEnglishWordsForExactHundred";
        try {
            assertEquals(testName, "One Hundred", JNumber.getEnglishWords("100"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords converts a hundreds value with a remainder correctly. */
    private static void testEnglishWordsForHundredsWithRemainder() {
        String testName = "testEnglishWordsForHundredsWithRemainder";
        try {
            assertEquals(testName, "One Hundred Five", JNumber.getEnglishWords("105"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords converts the maximum value of a single three-digit group (999) correctly. */
    private static void testEnglishWordsForMaxThreeDigitGroup() {
        String testName = "testEnglishWordsForMaxThreeDigitGroup";
        try {
            assertEquals(testName, "Nine Hundred Ninety Nine", JNumber.getEnglishWords("999"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords converts an exact thousand with no remainder correctly. */
    private static void testEnglishWordsForExactThousand() {
        String testName = "testEnglishWordsForExactThousand";
        try {
            assertEquals(testName, "One Thousand", JNumber.getEnglishWords("1000"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords converts a thousands value with a remainder correctly. */
    private static void testEnglishWordsForThousandWithRemainder() {
        String testName = "testEnglishWordsForThousandWithRemainder";
        try {
            assertEquals(testName, "One Thousand One", JNumber.getEnglishWords("1001"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords converts a value spanning the millions range correctly. */
    private static void testEnglishWordsForMillionsRange() {
        String testName = "testEnglishWordsForMillionsRange";
        try {
            assertEquals(testName, "One Million Two Hundred Thirty Four Thousand Five Hundred Sixty Seven",
                    JNumber.getEnglishWords("1234567"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords converts a negative decimal number, including the "Point" separator. */
    private static void testEnglishWordsForNegativeDecimal() {
        String testName = "testEnglishWordsForNegativeDecimal";
        try {
            assertEquals(testName, "Negative Forty Five Point Six", JNumber.getEnglishWords("-45.6"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords does not prepend "Negative" for a negative-zero input like "-0.00". */
    private static void testEnglishWordsForNegativeZeroHasNoNegativeWord() {
        String testName = "testEnglishWordsForNegativeZeroHasNoNegativeWord";
        try {
            String result = JNumber.getEnglishWords("-0.00");
            assertTrue(testName, !result.contains("Negative"), "Expected no \"Negative\" word for -0.00, was: " + result);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords strips leading zeros from the input before converting. */
    private static void testEnglishWordsStripsLeadingZeros() {
        String testName = "testEnglishWordsStripsLeadingZeros";
        try {
            assertEquals(testName, "Seven", JNumber.getEnglishWords("007"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords accepts an already comma-formatted input string. */
    private static void testEnglishWordsAcceptsCommaFormattedInput() {
        String testName = "testEnglishWordsAcceptsCommaFormattedInput";
        try {
            assertEquals(testName, "One Thousand", JNumber.getEnglishWords("1,000"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords(null) returns an empty string rather than throwing. */
    private static void testEnglishWordsForNullReturnsEmptyString() {
        String testName = "testEnglishWordsForNullReturnsEmptyString";
        try {
            assertEquals(testName, "", JNumber.getEnglishWords(null));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords("") returns an empty string rather than throwing. */
    private static void testEnglishWordsForEmptyStringReturnsEmptyString() {
        String testName = "testEnglishWordsForEmptyStringReturnsEmptyString";
        try {
            assertEquals(testName, "", JNumber.getEnglishWords(""));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishWords rejects input containing non-digit characters. */
    private static void testEnglishWordsRejectsNonDigitCharacters() {
        String testName = "testEnglishWordsRejectsNonDigitCharacters";
        try {
            JNumber.getEnglishWords("12a");
            fail(testName, "Expected IllegalArgumentException but no exception was thrown");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        } catch (Exception unexpectedException) {
            fail(testName, "Expected IllegalArgumentException but got: " + unexpectedException.getClass().getSimpleName());
        }
    }

    /** Verifies getEnglishWords rejects input with more than one decimal point. */
    private static void testEnglishWordsRejectsMultipleDecimalPoints() {
        String testName = "testEnglishWordsRejectsMultipleDecimalPoints";
        try {
            JNumber.getEnglishWords("1.2.3");
            fail(testName, "Expected IllegalArgumentException but no exception was thrown");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        } catch (Exception unexpectedException) {
            fail(testName, "Expected IllegalArgumentException but got: " + unexpectedException.getClass().getSimpleName());
        }
    }

    /** Verifies getEnglishWords rejects a number too large to convert, with ArithmeticException. */
    private static void testEnglishWordsRejectsNumberTooLargeToConvert() {
        String testName = "testEnglishWordsRejectsNumberTooLargeToConvert";
        try {
            StringBuilder hugeNumber = new StringBuilder();
            for (int i = 0; i < 40; i++) hugeNumber.append('9');
            JNumber.getEnglishWords(hugeNumber.toString());
            fail(testName, "Expected ArithmeticException but no exception was thrown");
        } catch (ArithmeticException expected) {
            pass(testName);
        } catch (Exception unexpectedException) {
            fail(testName, "Expected ArithmeticException but got: " + unexpectedException.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------
    // getPersianWords
    // ------------------------------------------------------------------

    /** Verifies getPersianWords("0") returns "صفر". */
    private static void testPersianWordsForZero() {
        String testName = "testPersianWordsForZero";
        try {
            assertEquals(testName, "صفر", JNumber.getPersianWords("0"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getPersianWords joins a compound number (tens + ones) with the Persian "و" joiner. */
    private static void testPersianWordsForCompoundNumberUsesJoiner() {
        String testName = "testPersianWordsForCompoundNumberUsesJoiner";
        try {
            assertEquals(testName, "بیست و یک", JNumber.getPersianWords("21"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getPersianWords converts an exact thousand with no remainder correctly. */
    private static void testPersianWordsForExactThousand() {
        String testName = "testPersianWordsForExactThousand";
        try {
            assertEquals(testName, "یک هزار", JNumber.getPersianWords("1000"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getPersianWords converts a negative decimal number, including the "ممیز" separator. */
    private static void testPersianWordsForNegativeDecimal() {
        String testName = "testPersianWordsForNegativeDecimal";
        try {
            assertEquals(testName, "منفی چهل و پنج ممیز شش", JNumber.getPersianWords("-45.6"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // getPersianNumber
    // ------------------------------------------------------------------

    /** Verifies getPersianNumber groups thousands with commas and uses Persian digits. */
    private static void testPersianNumberGroupsThousands() {
        String testName = "testPersianNumberGroupsThousands";
        try {
            assertEquals(testName, "۱,۲۳۴,۵۶۷", JNumber.getPersianNumber("1234567"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getPersianNumber formats a negative decimal with the minus sign and Persian decimal separator. */
    private static void testPersianNumberWithNegativeDecimal() {
        String testName = "testPersianNumberWithNegativeDecimal";
        try {
            assertEquals(testName, "-۱,۲۳۴٫۵", JNumber.getPersianNumber("-1234.5"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getPersianNumber("0") returns the Persian digit "۰". */
    private static void testPersianNumberForZero() {
        String testName = "testPersianNumberForZero";
        try {
            assertEquals(testName, "۰", JNumber.getPersianNumber("0"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getPersianNumber does not prepend a minus sign for a negative-zero input like "-0". */
    private static void testPersianNumberForNegativeZeroHasNoMinusSign() {
        String testName = "testPersianNumberForNegativeZeroHasNoMinusSign";
        try {
            assertEquals(testName, "۰", JNumber.getPersianNumber("-0"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getPersianNumber(null) returns an empty string rather than throwing. */
    private static void testPersianNumberForNullReturnsEmptyString() {
        String testName = "testPersianNumberForNullReturnsEmptyString";
        try {
            assertEquals(testName, "", JNumber.getPersianNumber(null));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // getEnglishNumber
    // ------------------------------------------------------------------

    /** Verifies getEnglishNumber converts a Persian-digit input into a comma-grouped Latin-digit string. */
    private static void testEnglishNumberConvertsFromPersianDigits() {
        String testName = "testEnglishNumberConvertsFromPersianDigits";
        try {
            assertEquals(testName, "1,234,567", JNumber.getEnglishNumber("۱۲۳۴۵۶۷"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishNumber still uses the Persian decimal separator character for a negative decimal. */
    private static void testEnglishNumberWithNegativeDecimalUsesPersianSeparator() {
        String testName = "testEnglishNumberWithNegativeDecimalUsesPersianSeparator";
        try {
            // Note: despite the "English" name, the decimal separator is
            // still the Persian separator character ('٫'), matching
            // getPersianNumber's output convention.
            assertEquals(testName, "-1,234٫5", JNumber.getEnglishNumber("-1234.5"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishNumber(null) returns an empty string rather than throwing. */
    private static void testEnglishNumberForNullReturnsEmptyString() {
        String testName = "testEnglishNumberForNullReturnsEmptyString";
        try {
            assertEquals(testName, "", JNumber.getEnglishNumber(null));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishNumber strips stray surrounding whitespace before formatting (regression test). */
    private static void testEnglishNumberStripsStrayWhitespace() {
        String testName = "testEnglishNumberStripsStrayWhitespace";
        try {
            // Regression test: getEnglishNumber must sanitize input the same
            // way getPersianNumber does, instead of leaving stray characters
            // embedded in the formatted result.
            assertEquals(testName, "1,234", JNumber.getEnglishNumber(" 1234 "));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishNumber strips non-digit junk characters embedded in the input. */
    private static void testEnglishNumberStripsNonDigitJunkCharacters() {
        String testName = "testEnglishNumberStripsNonDigitJunkCharacters";
        try {
            assertEquals(testName, "1,234", JNumber.getEnglishNumber("abc1234xyz"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishNumber keeps only the first decimal point when given multiple. */
    private static void testEnglishNumberHandlesMultipleDecimalPointsGracefully() {
        String testName = "testEnglishNumberHandlesMultipleDecimalPointsGracefully";
        try {
            // Mirrors getPersianNumber's behavior: only the first decimal
            // point is kept, any further dots are discarded.
            assertEquals(testName, "1,234٫56", JNumber.getEnglishNumber("1234.5.6"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // getEnglishNumberWithoutCommas
    // ------------------------------------------------------------------

    /** Verifies getEnglishNumberWithoutCommas strips thousands separators from an already-formatted number. */
    private static void testEnglishNumberWithoutCommasStripsSeparators() {
        String testName = "testEnglishNumberWithoutCommasStripsSeparators";
        try {
            assertEquals(testName, "1234567", JNumber.getEnglishNumberWithoutCommas("1,234,567"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishNumberWithoutCommas normalizes the Persian decimal separator to a standard '.'. */
    private static void testEnglishNumberWithoutCommasNormalizesPersianSeparator() {
        String testName = "testEnglishNumberWithoutCommasNormalizesPersianSeparator";
        try {
            assertEquals(testName, "1234.56", JNumber.getEnglishNumberWithoutCommas("1,234٫56"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishNumberWithoutCommas removes a trailing decimal point with no digits after it. */
    private static void testEnglishNumberWithoutCommasRemovesTrailingDot() {
        String testName = "testEnglishNumberWithoutCommasRemovesTrailingDot";
        try {
            assertEquals(testName, "12", JNumber.getEnglishNumberWithoutCommas("12."));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies getEnglishNumberWithoutCommas(null) returns an empty string rather than throwing. */
    private static void testEnglishNumberWithoutCommasForNullReturnsEmptyString() {
        String testName = "testEnglishNumberWithoutCommasForNullReturnsEmptyString";
        try {
            assertEquals(testName, "", JNumber.getEnglishNumberWithoutCommas(null));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Minimal assertion helpers (deliberately not using any test framework)
    // ------------------------------------------------------------------

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
