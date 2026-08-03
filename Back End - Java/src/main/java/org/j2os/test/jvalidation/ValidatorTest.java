package org.j2os.test.jvalidation;

import org.j2os.platform.jvalidation.Patterns;
import org.j2os.platform.jvalidation.ValidationException;
import org.j2os.platform.jvalidation.ValidationResult;
import org.j2os.platform.jvalidation.Validator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Standalone, dependency-free test suite for {@link Validator}, {@link Validator.Field},
 * {@link Patterns}, {@link ValidationResult}, and {@link ValidationException}.
 * <p>
 * This class intentionally does <b>not</b> use JUnit or any other testing
 * framework: it is a plain Java class with a {@code main} method that runs
 * every test case sequentially, prints a PASS/FAIL line for each one, and
 * prints a final summary. Run it directly:
 * <pre>{@code
 * javac -d out org/j2os/platform/jutil/date/JDate.java org/j2os/platform/jvalidation/*.java
 * java -cp out org.j2os.platform.jvalidation.ValidatorTest
 * }</pre>
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.4
 */
public class ValidatorTest {

    private static int passedCount = 0;
    private static int failedCount = 0;

    /** Minimal record used as a validation target across the tests. */
    private record Sample(
            String username,
            String code,
            String bio,
            String email,
            String nationalCode,
            BigDecimal price,
            String quantityText,
            Boolean accepted,
            String acceptedText,
            String birthGregorian,
            String birthPersian,
            String futureEventGregorian,
            String invalidDateGregorian,
            List<String> tags,
            String password,
            String confirmPassword,
            boolean isCompany,
            String companyName
    ) {
    }

    /**
     * Formats {@code today + yearsFromNow} as a {@code yyyy/MM/dd} string.
     * Used instead of a hardcoded literal (e.g. the old {@code "2030/01/01"})
     * so future-dated fixtures stay future no matter when the suite runs.
     */
    private static String futureDate(int yearsFromNow) {
        LocalDate date = LocalDate.now().plusYears(yearsFromNow);
        return String.format("%04d/%02d/%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    private static Sample validSample() {
        return new Sample(
                "ali_2000", "AB12", "developer at IR", "user@example.com", "0123456789",
                new BigDecimal("1234.56"), "42", Boolean.TRUE, "TRUE",
                "2000/05/10", "1378/12/25", futureDate(5), "2020/13/40",
                List.of("a", "b", "c"), "Passw0rd!", "Passw0rd!", true, "Acme Co"
        );
    }

    public static void main(String[] args) {
        System.out.println("=== Validator test suite ===");

        // String rules
        testRequiredFailsOnNull();
        testRequiredFailsOnEmptyString();
        testRequiredPassesOnWhitespaceOnlyString();
        testNotBlankFailsOnWhitespaceOnlyString();
        testMinLengthPasses();
        testMinLengthFails();
        testMaxLengthFails();
        testLengthBetweenPasses();
        testRegexStringPasses();
        testRegexPatternPasses();
        testRegexStringFailsOnInvalidPatternSyntax();
        testRegexStringFailsOnNullPattern();
        testRegexPatternFailsOnNullPattern();
        testContainsOnString();
        testContainsOnCollection();
        testContainsOnMap();
        testStartsWithFails();
        testEndsWithPasses();

        // Numeric rules
        testNumberPassesOnValidNumericString();
        testNumberFailsOnNonNumericStringRecordsError();
        testMinFailsOnNonNumericStringRecordsErrorInsteadOfThrowing();
        testMaxPasses();
        testBetweenNumericPasses();
        testPositiveFailsOnZero();
        testDigitsPassesAfterStrippingTrailingZeros();
        testDigitsFailsWhenTooManyIntegerDigits();

        // Boolean rules
        testBoolFailsOnInvalidString();
        testIsTrueWorksOnStringTrue();
        testIsFalseWorksOnActualBoolean();

        // Date rules
        testDatePassesOnValidGregorianDate();
        testDateFailsOnInvalidGregorianDate();
        testPersianDatePassesOnValidPersianLeapDate();
        testPastPassesOnPastDate();
        testFuturePassesOnFutureDate();
        testMinimumAgePasses();
        testMinimumAgeFailsWhenTooYoung();
        testMinimumAgeFailsOnFutureBirthDateWithDistinctErrorCode();
        testPastRecordsDateContextMissingInsteadOfThrowing();
        testBeforePasses();
        testAfterPasses();
        testDateComparisonRulesRecordErrorOnInvalidComparisonDateInsteadOfThrowing();
        testDateBetweenPasses();
        testDateBetweenFailsOutsideRange();

        // Collection rules
        testNotEmptyPassesOnNonEmptyCollection();
        testSizeExactMatch();
        testMinSizeAndMaxSize();
        testUniqueFailsOnDuplicateElements();
        testNotEmptyRecordsErrorOnNonCollectionValueInsteadOfThrowing();
        testAsCollectionSupportsPrimitiveIntArray();
        testAsCollectionSupportsObjectStringArray();

        // Cross-field / flow control
        testEqualToPasses();
        testEqualToFails();
        testWhenEndWhenSkipsInactiveRule();
        testWhenGenuinelySkipsRuleLogicNotJustErrorRecording();
        testFieldAccessErrorRecordedInsteadOfThrowing();
        testWithMethodCustomRule();
        testWithMethodFailsWhenPredicateReturnsFalse();
        testWithMethodRecordsErrorWhenRuleThrowsInsteadOfPropagating();

        // message()/messageKey()
        testMessageReplacesFailedRuleMessage();
        testMessageKeySetsLocalizationKey();
        testMessageHasNoEffectWhenPriorRulePassed();

        // validate()/validateOrThrow()
        testValidateReturnsAllErrorsAcrossFields();
        testValidateOrThrowThrowsOnFailure();
        testValidateOrThrowDoesNotThrowOnSuccess();

        // ValidationResult / ValidationException
        testValidationResultIsValidWhenNoErrors();
        testValidationResultErrorCountMatches();
        testValidationExceptionCarriesResult();

        // Patterns
        testMobilePattern();
        testEmailPattern();
        testUuidPattern();
        testIpv4Pattern();

        // Concurrency
        testConcurrentValidationNeverCorruptsSharedCachesOrDateContext();

        System.out.println();
        System.out.println("=== Summary: " + passedCount + " passed, " + failedCount + " failed ===");
        if (failedCount > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // String rules
    // ------------------------------------------------------------------

    private static void testRequiredFailsOnNull() {
        String testName = "testRequiredFailsOnNull";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> (String) null).required()
                    .validate();
            assertFalse(testName, result.isValid(), "Expected required() to fail on null");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testRequiredFailsOnEmptyString() {
        String testName = "testRequiredFailsOnEmptyString";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "").required()
                    .validate();
            assertFalse(testName, result.isValid(), "Expected required() to fail on an empty string");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testRequiredPassesOnWhitespaceOnlyString() {
        String testName = "testRequiredPassesOnWhitespaceOnlyString";
        try {
            // required() only checks null/empty, not blank - so " " should pass.
            ValidationResult result = Validator.of(validSample())
                    .field(s -> " ").required()
                    .validate();
            assertTrue(testName, result.isValid(), "Expected required() to pass on a whitespace-only string");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testNotBlankFailsOnWhitespaceOnlyString() {
        String testName = "testNotBlankFailsOnWhitespaceOnlyString";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "   ").notBlank()
                    .validate();
            assertFalse(testName, result.isValid(), "Expected notBlank() to fail on a whitespace-only string");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testMinLengthPasses() {
        String testName = "testMinLengthPasses";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::username).minLength(5)
                    .validate();
            assertTrue(testName, result.isValid(), "Expected minLength(5) to pass for \"ali_2000\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testMinLengthFails() {
        String testName = "testMinLengthFails";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::username).minLength(100)
                    .validate();
            assertFalse(testName, result.isValid(), "Expected minLength(100) to fail for \"ali_2000\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testMaxLengthFails() {
        String testName = "testMaxLengthFails";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::username).maxLength(3)
                    .validate();
            assertFalse(testName, result.isValid(), "Expected maxLength(3) to fail for \"ali_2000\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testLengthBetweenPasses() {
        String testName = "testLengthBetweenPasses";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::code).lengthBetween(2, 10)
                    .validate();
            assertTrue(testName, result.isValid(), "Expected lengthBetween(2, 10) to pass for \"AB12\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testRegexStringPasses() {
        String testName = "testRegexStringPasses";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::email).regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                    .validate();
            assertTrue(testName, result.isValid(), "Expected the email regex to pass for a valid email");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testRegexPatternPasses() {
        String testName = "testRegexPatternPasses";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::nationalCode).regex(Patterns.NATIONAL_CODE)
                    .validate();
            assertTrue(testName, result.isValid(), "Expected Patterns.NATIONAL_CODE to match a 10-digit code");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * Regression test: {@code regex(String)} must record an {@code INVALID_PATTERN}
     * error - not let {@link java.util.regex.PatternSyntaxException} escape - when
     * given a syntactically invalid regular expression.
     */
    private static void testRegexStringFailsOnInvalidPatternSyntax() {
        String testName = "testRegexStringFailsOnInvalidPatternSyntax";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::username).regex("[unclosed")
                    .validate();
            assertFalse(testName, result.isValid(),
                    "Expected regex() to record an error instead of throwing PatternSyntaxException");
            assertEquals(testName, "INVALID_PATTERN", result.errors().get(0).getCode());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception escaped regex(): " + unexpectedException);
        }
    }

    /**
     * Regression test: {@code regex((String) null)} must record an
     * {@code INVALID_PATTERN} error instead of throwing {@link NullPointerException}.
     */
    private static void testRegexStringFailsOnNullPattern() {
        String testName = "testRegexStringFailsOnNullPattern";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::username).regex((String) null)
                    .validate();
            assertFalse(testName, result.isValid(), "Expected regex(null) to record an error instead of throwing");
            assertEquals(testName, "INVALID_PATTERN", result.errors().get(0).getCode());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * Regression test: {@code regex((Pattern) null)} must record an
     * {@code INVALID_PATTERN} error instead of throwing {@link NullPointerException}.
     */
    private static void testRegexPatternFailsOnNullPattern() {
        String testName = "testRegexPatternFailsOnNullPattern";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::username).regex((Pattern) null)
                    .validate();
            assertFalse(testName, result.isValid(),
                    "Expected regex((Pattern) null) to record an error instead of throwing");
            assertEquals(testName, "INVALID_PATTERN", result.errors().get(0).getCode());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testContainsOnString() {
        String testName = "testContainsOnString";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::bio).contains("developer")
                    .validate();
            assertTrue(testName, result.isValid(), "Expected contains(\"developer\") to pass for \"developer at IR\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testContainsOnCollection() {
        String testName = "testContainsOnCollection";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::tags).contains("b")
                    .validate();
            assertTrue(testName, result.isValid(), "Expected contains(\"b\") to pass for a list containing \"b\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** {@code contains(...)} on a {@link Map} checks the map's values, per its javadoc. */
    private static void testContainsOnMap() {
        String testName = "testContainsOnMap";
        try {
            Map<String, String> roles = Map.of("owner", "admin", "guest", "readonly");
            ValidationResult result = Validator.of(validSample())
                    .field(s -> roles).contains("admin")
                    .validate();
            assertTrue(testName, result.isValid(), "Expected contains(\"admin\") to pass for a map containing that value");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testStartsWithFails() {
        String testName = "testStartsWithFails";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::bio).startsWith("senior")
                    .validate();
            assertFalse(testName, result.isValid(), "Expected startsWith(\"senior\") to fail for \"developer at IR\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testEndsWithPasses() {
        String testName = "testEndsWithPasses";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::bio).endsWith("IR")
                    .validate();
            assertTrue(testName, result.isValid(), "Expected endsWith(\"IR\") to pass for \"developer at IR\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Numeric rules
    // ------------------------------------------------------------------

    private static void testNumberPassesOnValidNumericString() {
        String testName = "testNumberPassesOnValidNumericString";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::quantityText).number()
                    .validate();
            assertTrue(testName, result.isValid(), "Expected number() to pass for \"42\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testNumberFailsOnNonNumericStringRecordsError() {
        String testName = "testNumberFailsOnNonNumericStringRecordsError";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "abc").number()
                    .validate();
            assertFalse(testName, result.isValid(), "Expected number() to fail for \"abc\"");
            assertEquals(testName, "NUMBER", result.errors().get(0).getCode());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testMinFailsOnNonNumericStringRecordsErrorInsteadOfThrowing() {
        String testName = "testMinFailsOnNonNumericStringRecordsErrorInsteadOfThrowing";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "not-a-number").min(1)
                    .validate();
            assertFalse(testName, result.isValid(), "Expected min(1) to fail (as a recorded error) for \"not-a-number\"");
            assertEquals(testName, "MIN", result.errors().get(0).getCode());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testMaxPasses() {
        String testName = "testMaxPasses";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::price).max(5000)
                    .validate();
            assertTrue(testName, result.isValid(), "Expected max(5000) to pass for 1234.56");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testBetweenNumericPasses() {
        String testName = "testBetweenNumericPasses";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::price).between(100, 5000)
                    .validate();
            assertTrue(testName, result.isValid(), "Expected between(100, 5000) to pass for 1234.56");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testPositiveFailsOnZero() {
        String testName = "testPositiveFailsOnZero";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> BigDecimal.ZERO).positive()
                    .validate();
            assertFalse(testName, result.isValid(), "Expected positive() to fail for zero");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testDigitsPassesAfterStrippingTrailingZeros() {
        String testName = "testDigitsPassesAfterStrippingTrailingZeros";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> new BigDecimal("1200.00")).digits(4, 2)
                    .validate();
            assertTrue(testName, result.isValid(), "Expected digits(4, 2) to pass for 1200.00 (4 integer digits, 0 fraction digits after stripping)");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testDigitsFailsWhenTooManyIntegerDigits() {
        String testName = "testDigitsFailsWhenTooManyIntegerDigits";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> new BigDecimal("12345.6")).digits(4, 2)
                    .validate();
            assertFalse(testName, result.isValid(), "Expected digits(4, 2) to fail for 12345.6 (5 integer digits)");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Boolean rules
    // ------------------------------------------------------------------

    private static void testBoolFailsOnInvalidString() {
        String testName = "testBoolFailsOnInvalidString";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "yep").bool()
                    .validate();
            assertFalse(testName, result.isValid(), "Expected bool() to fail for \"yep\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testIsTrueWorksOnStringTrue() {
        String testName = "testIsTrueWorksOnStringTrue";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::acceptedText).bool().isTrue()
                    .validate();
            assertTrue(testName, result.isValid(), "Expected isTrue() to pass for the string \"TRUE\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testIsFalseWorksOnActualBoolean() {
        String testName = "testIsFalseWorksOnActualBoolean";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> Boolean.FALSE).isFalse()
                    .validate();
            assertTrue(testName, result.isValid(), "Expected isFalse() to pass for Boolean.FALSE");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Date rules
    // ------------------------------------------------------------------

    private static void testDatePassesOnValidGregorianDate() {
        String testName = "testDatePassesOnValidGregorianDate";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::birthGregorian).date()
                    .validate();
            assertTrue(testName, result.isValid(), "Expected date() to pass for \"2000/05/10\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testDateFailsOnInvalidGregorianDate() {
        String testName = "testDateFailsOnInvalidGregorianDate";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::invalidDateGregorian).date()
                    .validate();
            assertFalse(testName, result.isValid(), "Expected date() to fail for \"2020/13/40\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testPersianDatePassesOnValidPersianLeapDate() {
        String testName = "testPersianDatePassesOnValidPersianLeapDate";
        try {
            // 1403 is a Persian leap year, so Esfand (month 12) has 30 days.
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "1403/12/30").persianDate()
                    .validate();
            assertTrue(testName, result.isValid(), "Expected persianDate() to pass for \"1403/12/30\" (1403 is a leap year)");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testPastPassesOnPastDate() {
        String testName = "testPastPassesOnPastDate";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::birthGregorian).date().past()
                    .validate();
            assertTrue(testName, result.isValid(), "Expected past() to pass for \"2000/05/10\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testFuturePassesOnFutureDate() {
        String testName = "testFuturePassesOnFutureDate";
        try {
            // futureEventGregorian is computed relative to today (see futureDate()),
            // not a hardcoded literal, so this stays correct regardless of when it runs.
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::futureEventGregorian).date().future()
                    .validate();
            assertTrue(testName, result.isValid(), "Expected future() to pass for a date 5 years from today");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testMinimumAgePasses() {
        String testName = "testMinimumAgePasses";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::birthGregorian).date().minimumAge(18)
                    .validate();
            assertTrue(testName, result.isValid(), "Expected minimumAge(18) to pass for a birth date in year 2000");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testMinimumAgeFailsWhenTooYoung() {
        String testName = "testMinimumAgeFailsWhenTooYoung";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "2020/01/01").date().minimumAge(18)
                    .validate();
            assertFalse(testName, result.isValid(), "Expected minimumAge(18) to fail for a birth date in 2020");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * A birth date in the future must fail {@code minimumAge()} with the distinct
     * {@code FUTURE_BIRTH_DATE} code, not the generic {@code MINIMUM_AGE} code -
     * see {@link Validator.Field#minimumAge(int)}'s javadoc.
     */
    private static void testMinimumAgeFailsOnFutureBirthDateWithDistinctErrorCode() {
        String testName = "testMinimumAgeFailsOnFutureBirthDateWithDistinctErrorCode";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> futureDate(5)).date().minimumAge(18)
                    .validate();
            assertFalse(testName, result.isValid(), "Expected minimumAge() to fail for a birth date in the future");
            assertEquals(testName, "FUTURE_BIRTH_DATE", result.errors().get(0).getCode());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testPastRecordsDateContextMissingInsteadOfThrowing() {
        String testName = "testPastRecordsDateContextMissingInsteadOfThrowing";
        try {
            // ── Scenario 1: date() failed → only DATE error, no DATE_CONTEXT_MISSING ──
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::invalidDateGregorian)   // "2020/13/40"
                    .date()
                    .past()
                    .validate();

            assertFalse(testName, result.isValid(),
                    "Expected past() after a failed date() to record errors instead of throwing");

            boolean hasDateError = result.errors().stream()
                    .anyMatch(e -> "DATE".equals(e.getCode()));
            assertTrue(testName, hasDateError,
                    "Expected a DATE error to be recorded when date() fails");

            boolean hasContextMissingAfterFailedDate = result.errors().stream()
                    .anyMatch(e -> "DATE_CONTEXT_MISSING".equals(e.getCode()));
            assertFalse(testName, hasContextMissingAfterFailedDate,
                    "DATE_CONTEXT_MISSING must not be recorded when date() already failed");

            // ── Scenario 2: past() without any prior date()/persianDate() call ──
            ValidationResult result2 = Validator.of(validSample())
                    .field(Sample::birthGregorian)
                    .past()
                    .validate();

            assertFalse(testName, result2.isValid(),
                    "Expected past() without prior date()/persianDate() to fail");

            boolean hasContextMissing = result2.errors().stream()
                    .anyMatch(e -> "DATE_CONTEXT_MISSING".equals(e.getCode()));
            assertTrue(testName, hasContextMissing,
                    "Expected DATE_CONTEXT_MISSING when date()/persianDate() was never called");

        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testBeforePasses() {
        String testName = "testBeforePasses";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "2019/01/01").date().before("2021/01/01")
                    .validate();
            assertTrue(testName, result.isValid(), "Expected before(\"2021/01/01\") to pass for \"2019/01/01\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Previously missing: the old "before and after" test never actually exercised after(). */
    private static void testAfterPasses() {
        String testName = "testAfterPasses";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "2021/01/01").date().after("2019/01/01")
                    .validate();
            assertTrue(testName, result.isValid(), "Expected after(\"2019/01/01\") to pass for \"2021/01/01\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * Regression test: {@code before()}/{@code after()}/{@code between(String, String)}
     * used to throw a raw {@link NullPointerException} when given a malformed comparison
     * date string; they now record an {@code INVALID_COMPARISON_DATE} error instead.
     */
    private static void testDateComparisonRulesRecordErrorOnInvalidComparisonDateInsteadOfThrowing() {
        String testName = "testDateComparisonRulesRecordErrorOnInvalidComparisonDateInsteadOfThrowing";
        try {
            ValidationResult beforeResult = Validator.of(validSample())
                    .field(Sample::birthGregorian).date().before("not-a-date")
                    .validate();
            assertFalse(testName, beforeResult.isValid(),
                    "Expected before() with a malformed comparison date to record an error");
            assertEquals(testName, "INVALID_COMPARISON_DATE", beforeResult.errors().get(0).getCode());

            ValidationResult afterResult = Validator.of(validSample())
                    .field(Sample::birthGregorian).date().after("2020/13/40")
                    .validate();
            assertFalse(testName, afterResult.isValid(),
                    "Expected after() with a malformed comparison date to record an error");
            assertEquals(testName, "INVALID_COMPARISON_DATE", afterResult.errors().get(0).getCode());

            ValidationResult betweenResult = Validator.of(validSample())
                    .field(Sample::birthGregorian).date().between("bad-start", "2020/12/29")
                    .validate();
            assertFalse(testName, betweenResult.isValid(),
                    "Expected between() with a malformed start date to record an error");
            assertEquals(testName, "INVALID_COMPARISON_DATE", betweenResult.errors().get(0).getCode());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception escaped a date comparison rule: " + unexpectedException);
        }
    }

    private static void testDateBetweenPasses() {
        String testName = "testDateBetweenPasses";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "2020/06/15").date().between("2020/01/01", "2020/12/29")
                    .validate();
            assertTrue(testName, result.isValid(), "Expected between(...) to pass for a date inside the range");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testDateBetweenFailsOutsideRange() {
        String testName = "testDateBetweenFailsOutsideRange";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "2021/06/15").date().between("2020/01/01", "2020/12/29")
                    .validate();
            assertFalse(testName, result.isValid(), "Expected between(...) to fail for a date outside the range");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Collection rules
    // ------------------------------------------------------------------

    private static void testNotEmptyPassesOnNonEmptyCollection() {
        String testName = "testNotEmptyPassesOnNonEmptyCollection";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::tags).notEmpty()
                    .validate();
            assertTrue(testName, result.isValid(), "Expected notEmpty() to pass for a non-empty list");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testSizeExactMatch() {
        String testName = "testSizeExactMatch";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::tags).size(3)
                    .validate();
            assertTrue(testName, result.isValid(), "Expected size(3) to pass for [\"a\", \"b\", \"c\"]");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testMinSizeAndMaxSize() {
        String testName = "testMinSizeAndMaxSize";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::tags).minSize(1).maxSize(5)
                    .validate();
            assertTrue(testName, result.isValid(), "Expected minSize(1)/maxSize(5) to pass for a 3-element list");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testUniqueFailsOnDuplicateElements() {
        String testName = "testUniqueFailsOnDuplicateElements";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> List.of("a", "a", "b")).unique()
                    .validate();
            assertFalse(testName, result.isValid(), "Expected unique() to fail for a list with a duplicate element");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testNotEmptyRecordsErrorOnNonCollectionValueInsteadOfThrowing() {
        String testName = "testNotEmptyRecordsErrorOnNonCollectionValueInsteadOfThrowing";
        try {
            // Regression test: applying a collection-only rule to a plain
            // String field must record a validation error (like every other
            // rule in this framework, e.g. number(), bool()), not throw a
            // raw IllegalArgumentException.
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::username).notEmpty()
                    .validate();
            assertFalse(testName, result.isValid(), "Expected notEmpty() to fail (as a recorded error) for a non-collection String field");
            assertEquals(testName, "NOT_EMPTY", result.errors().get(0).getCode());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * Regression test: {@code asCollection()} previously threw a raw
     * {@link ClassCastException} on primitive arrays because it cast
     * directly to {@code Object[]}; it now uses reflection and handles
     * primitive arrays correctly.
     */
    private static void testAsCollectionSupportsPrimitiveIntArray() {
        String testName = "testAsCollectionSupportsPrimitiveIntArray";
        try {
            int[] scores = {10, 20, 30};
            ValidationResult result = Validator.of(validSample())
                    .field(s -> scores).notEmpty().size(3)
                    .validate();
            assertTrue(testName, result.isValid(),
                    "Expected notEmpty()/size(3) to work on a primitive int[] instead of throwing ClassCastException");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception escaped asCollection() on a primitive array: " + unexpectedException);
        }
    }

    /** Companion to {@link #testAsCollectionSupportsPrimitiveIntArray()}, for an object array. */
    private static void testAsCollectionSupportsObjectStringArray() {
        String testName = "testAsCollectionSupportsObjectStringArray";
        try {
            String[] labels = {"a", "b"};
            ValidationResult result = Validator.of(validSample())
                    .field(s -> labels).minSize(1).maxSize(5).unique()
                    .validate();
            assertTrue(testName, result.isValid(), "Expected minSize()/maxSize()/unique() to work on a String[] array");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Cross-field / flow control
    // ------------------------------------------------------------------

    private static void testEqualToPasses() {
        String testName = "testEqualToPasses";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::password).equalTo(Sample::confirmPassword)
                    .validate();
            assertTrue(testName, result.isValid(), "Expected equalTo() to pass when password == confirmPassword");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testEqualToFails() {
        String testName = "testEqualToFails";
        try {
            Sample sample = new Sample(
                    "ali_2000", "AB12", "developer at IR", "user@example.com", "0123456789",
                    new BigDecimal("1234.56"), "42", Boolean.TRUE, "TRUE",
                    "2000/05/10", "1378/12/25", futureDate(5), "2020/13/40",
                    List.of("a", "b", "c"), "Passw0rd!", "SomethingElse!", true, "Acme Co"
            );
            ValidationResult result = Validator.of(sample)
                    .field(Sample::password).equalTo(Sample::confirmPassword)
                    .validate();
            assertFalse(testName, result.isValid(), "Expected equalTo() to fail when password != confirmPassword");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testWhenEndWhenSkipsInactiveRule() {
        String testName = "testWhenEndWhenSkipsInactiveRule";
        try {
            Sample notCompany = new Sample(
                    "ali_2000", "AB12", "developer at IR", "user@example.com", "0123456789",
                    new BigDecimal("1234.56"), "42", Boolean.TRUE, "TRUE",
                    "2000/05/10", "1378/12/25", futureDate(5), "2020/13/40",
                    List.of("a", "b", "c"), "Passw0rd!", "Passw0rd!", false, null
            );
            // companyName is null, but the when(isCompany) block should skip
            // the required() rule entirely since isCompany is false.
            ValidationResult result = Validator.of(notCompany)
                    .when(Sample::isCompany)
                    .field(Sample::companyName).required()
                    .endWhen()
                    .validate();
            assertTrue(testName, result.isValid(), "Expected required() to be skipped when the when() condition is false");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * Regression test for the deeper {@code when()}/{@code skipIfInactive()} fix:
     * an inactive rule must never run its own logic at all - not merely have its
     * error suppressed. Uses a {@link Validator.Field#withMethod} predicate that
     * throws if invoked; if {@code skipIfInactive()} were broken (only suppressing
     * the error, as it used to), the predicate would still be called and its
     * exception would surface as a recorded {@code SHOULD_NOT_FIRE} error.
     */
    private static void testWhenGenuinelySkipsRuleLogicNotJustErrorRecording() {
        String testName = "testWhenGenuinelySkipsRuleLogicNotJustErrorRecording";
        try {
            ValidationResult result = Validator.of(validSample())
                    .when(s -> false)
                    .field(Sample::username)
                    .withMethod((sample, v) -> { throw new RuntimeException("must not be invoked while inactive"); },
                            "SHOULD_NOT_FIRE", "should not fire")
                    .endWhen()
                    .validate();
            assertTrue(testName, result.isValid(),
                    "Expected an inactive rule's logic to never run at all (not just have its error suppressed)");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: an inactive rule's logic ran and threw: " + unexpectedException);
        }
    }

    /**
     * Regression test: {@link Validator#field(Validator.SerializableFunction)} must
     * record a {@code FIELD_ACCESS_ERROR} - not let the getter's exception propagate -
     * when the getter itself throws.
     */
    private static void testFieldAccessErrorRecordedInsteadOfThrowing() {
        String testName = "testFieldAccessErrorRecordedInsteadOfThrowing";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> { throw new IllegalStateException("boom"); })
                    .required()
                    .validate();
            assertFalse(testName, result.isValid(), "Expected a throwing getter to be recorded as a validation error");
            assertEquals(testName, "FIELD_ACCESS_ERROR", result.errors().get(0).getCode());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception escaped field(): " + unexpectedException);
        }
    }

    private static void testWithMethodCustomRule() {
        String testName = "testWithMethodCustomRule";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "SUMMER10")
                    .withMethod((sample, v) -> v != null && v.startsWith("SUMMER"), "COUPON_PREFIX", "must start with SUMMER")
                    .validate();
            assertTrue(testName, result.isValid(), "Expected withMethod() to pass for \"SUMMER10\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Companion to {@link #testWithMethodCustomRule()}: the predicate-returns-false branch. */
    private static void testWithMethodFailsWhenPredicateReturnsFalse() {
        String testName = "testWithMethodFailsWhenPredicateReturnsFalse";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "WINTER10")
                    .withMethod((sample, v) -> v != null && v.startsWith("SUMMER"), "COUPON_PREFIX", "must start with SUMMER")
                    .validate();
            assertFalse(testName, result.isValid(), "Expected withMethod() to fail for \"WINTER10\"");
            assertEquals(testName, "COUPON_PREFIX", result.errors().get(0).getCode());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * Regression/coverage test: per {@link Validator.Field#withMethod}'s javadoc, if
     * the supplied rule itself throws, that exception must be caught and recorded as
     * a normal validation error rather than propagating out of {@code validate()}.
     */
    private static void testWithMethodRecordsErrorWhenRuleThrowsInsteadOfPropagating() {
        String testName = "testWithMethodRecordsErrorWhenRuleThrowsInsteadOfPropagating";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(s -> "ANYVALUE")
                    .withMethod((sample, v) -> { throw new RuntimeException("scanner unavailable"); },
                            "VIRUS_UNSAFE", "The uploaded file was flagged as infected")
                    .validate();
            assertFalse(testName, result.isValid(),
                    "Expected a throwing rule to be recorded as a validation error instead of propagating");
            assertEquals(testName, "VIRUS_UNSAFE", result.errors().get(0).getCode());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception escaped withMethod(): " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // message() / messageKey()
    // ------------------------------------------------------------------

    private static void testMessageReplacesFailedRuleMessage() {
        String testName = "testMessageReplacesFailedRuleMessage";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::username).minLength(100).message("custom message")
                    .validate();
            assertEquals(testName, "custom message", result.errors().get(0).getMessage());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testMessageKeySetsLocalizationKey() {
        String testName = "testMessageKeySetsLocalizationKey";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::username).minLength(100).messageKey("user.username.tooShort")
                    .validate();
            assertEquals(testName, "user.username.tooShort", result.errors().get(0).getMessageKey());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testMessageHasNoEffectWhenPriorRulePassed() {
        String testName = "testMessageHasNoEffectWhenPriorRulePassed";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::username).minLength(1).message("should be ignored")
                    .validate();
            assertTrue(testName, result.isValid(), "Expected no error to exist when the preceding rule passed");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // validate() / validateOrThrow()
    // ------------------------------------------------------------------

    private static void testValidateReturnsAllErrorsAcrossFields() {
        String testName = "testValidateReturnsAllErrorsAcrossFields";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::username).minLength(100)
                    .field(Sample::code).length(10)
                    .validate();
            assertEquals(testName, 2, result.errorCount());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testValidateOrThrowThrowsOnFailure() {
        String testName = "testValidateOrThrowThrowsOnFailure";
        try {
            Validator.of(validSample())
                    .field(Sample::username).minLength(100)
                    .validateOrThrow();
            fail(testName, "Expected ValidationException but none was thrown");
        } catch (ValidationException expected) {
            pass(testName);
        } catch (Exception unexpectedException) {
            fail(testName, "Expected ValidationException but got: " + unexpectedException.getClass().getSimpleName());
        }
    }

    private static void testValidateOrThrowDoesNotThrowOnSuccess() {
        String testName = "testValidateOrThrowDoesNotThrowOnSuccess";
        try {
            ValidationResult result = Validator.of(validSample())
                    .field(Sample::username).minLength(1)
                    .validateOrThrow();
            assertTrue(testName, result.isValid(), "Expected validateOrThrow() not to throw when validation succeeds");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // ValidationResult / ValidationException
    // ------------------------------------------------------------------

    private static void testValidationResultIsValidWhenNoErrors() {
        String testName = "testValidationResultIsValidWhenNoErrors";
        try {
            ValidationResult result = new ValidationResult(List.of());
            assertTrue(testName, result.isValid(), "Expected isValid() to be true for an empty error list");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testValidationResultErrorCountMatches() {
        String testName = "testValidationResultErrorCountMatches";
        try {
            ValidationResult result = new ValidationResult(List.of(
                    new ValidationResult.Error("field1", "CODE1", "msg1", "v1"),
                    new ValidationResult.Error("field2", "CODE2", "msg2", "v2")
            ));
            assertEquals(testName, 2, result.errorCount());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testValidationExceptionCarriesResult() {
        String testName = "testValidationExceptionCarriesResult";
        try {
            ValidationResult result = new ValidationResult(List.of(
                    new ValidationResult.Error("field1", "CODE1", "msg1", "v1")
            ));
            ValidationException exception = new ValidationException(result);
            assertEquals(testName, result, exception.getResult());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Patterns
    // ------------------------------------------------------------------

    private static void testMobilePattern() {
        String testName = "testMobilePattern";
        try {
            assertTrue(testName, Patterns.MOBILE.matcher("09123456789").matches(), "Expected MOBILE to match \"09123456789\"");
            assertFalse(testName, Patterns.MOBILE.matcher("123456789").matches(), "Expected MOBILE not to match \"123456789\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testEmailPattern() {
        String testName = "testEmailPattern";
        try {
            assertTrue(testName, Patterns.EMAIL.matcher("user@example.com").matches(), "Expected EMAIL to match a valid email");
            assertFalse(testName, Patterns.EMAIL.matcher("not-an-email").matches(), "Expected EMAIL not to match \"not-an-email\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testUuidPattern() {
        String testName = "testUuidPattern";
        try {
            assertTrue(testName, Patterns.UUID.matcher("123e4567-e89b-12d3-a456-426614174000").matches(),
                    "Expected UUID to match a well-formed UUID");
            assertFalse(testName, Patterns.UUID.matcher("not-a-uuid").matches(), "Expected UUID not to match \"not-a-uuid\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testIpv4Pattern() {
        String testName = "testIpv4Pattern";
        try {
            assertTrue(testName, Patterns.IPV4.matcher("192.168.1.1").matches(), "Expected IPV4 to match \"192.168.1.1\"");
            assertFalse(testName, Patterns.IPV4.matcher("999.999.999.999").matches(), "Expected IPV4 not to match \"999.999.999.999\"");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Concurrency
    // ------------------------------------------------------------------

    /**
     * Heavy concurrency stress test targeting {@link Validator}'s <em>shared static</em> state -
     * not a single {@link Validator} instance's own chain, which the class javadoc already
     * documents as not thread-safe by design, but the {@code WRITE_REPLACE_CACHE}/
     * {@code FIELD_NAME_CACHE} field-name-extraction caches and {@code DateUtil}'s
     * lazily-refreshed "today" cache, both of which are genuinely shared across every
     * {@link Validator} instance in the JVM. This mirrors the actual supported usage pattern
     * (e.g. a web server validating many concurrent requests, each building and driving its own
     * {@code Validator} chain on its own thread).
     * <p>
     * Many threads each build many fresh, independent {@link Validator} chains touching six
     * different fields - including a date field exercised through {@code date().past()} - with
     * exactly one rule designed to fail. If the shared field-name caches or the shared date
     * cache were ever corrupted by concurrent access, this would most plausibly surface as: the
     * recorded error naming the wrong field (a name resolved for a different, concurrently
     * running thread's field), an extra or missing error (a rule that should have passed/failed
     * flipping outcome), or an exception escaping validation entirely.
     */
    private static void testConcurrentValidationNeverCorruptsSharedCachesOrDateContext() {
        String testName = "Concurrent validation across many threads never corrupts the shared field-name/date caches";
        int threadCount = 24;
        int iterationsPerThread = 800;

        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);
        AtomicInteger wrongErrorCountCount = new AtomicInteger(0);
        AtomicInteger wrongFieldNameCount = new AtomicInteger(0);
        AtomicInteger wrongErrorCodeCount = new AtomicInteger(0);

        List<Thread> threads = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        for (int t = 0; t < threadCount; t++) {
            threads.add(new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        Sample sample = validSample();
                        ValidationResult result = Validator.of(sample)
                                .field(Sample::username).minLength(100)               // deliberately fails: MIN_LENGTH
                                .field(Sample::code).length(4)                        // passes
                                .field(Sample::price).between(100, 5000)              // passes
                                .field(Sample::birthGregorian).date().past().minimumAge(18) // passes
                                .field(Sample::tags).notEmpty().size(3)               // passes
                                .field(Sample::email).regex(Patterns.EMAIL)           // passes
                                .validate();

                        if (result.errorCount() != 1) {
                            wrongErrorCountCount.incrementAndGet();
                            continue;
                        }
                        ValidationResult.Error error = result.errors().get(0);
                        if (!"username".equals(error.getField())) {
                            wrongFieldNameCount.incrementAndGet();
                        }
                        if (!"MIN_LENGTH".equals(error.getCode())) {
                            wrongErrorCodeCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (Exception unexpected) {
                    unexpectedErrorCount.incrementAndGet();
                }
            }));
        }

        threads.forEach(Thread::start);
        startLatch.countDown();

        boolean finishedInTime = true;
        for (Thread thread : threads) {
            try {
                thread.join(60_000);
                if (thread.isAlive()) {
                    finishedInTime = false;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                finishedInTime = false;
            }
        }

        assertTrue(testName,
                finishedInTime
                        && unexpectedErrorCount.get() == 0
                        && wrongErrorCountCount.get() == 0
                        && wrongFieldNameCount.get() == 0
                        && wrongErrorCodeCount.get() == 0,
                "finishedInTime=" + finishedInTime
                        + ", unexpectedErrors=" + unexpectedErrorCount.get()
                        + ", wrongErrorCounts=" + wrongErrorCountCount.get()
                        + " (of " + (threadCount * iterationsPerThread) + ")"
                        + ", wrongFieldNames=" + wrongFieldNameCount.get()
                        + ", wrongErrorCodes=" + wrongErrorCodeCount.get());
    }

    // ------------------------------------------------------------------
    // Minimal assertion helpers (deliberately not using any test framework)
    // ------------------------------------------------------------------

    private static void assertEquals(String testName, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            pass(testName);
        } else {
            fail(testName, "Expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertTrue(String testName, boolean condition, String failureMessage) {
        if (condition) {
            pass(testName);
        } else {
            fail(testName, failureMessage);
        }
    }

    private static void assertFalse(String testName, boolean condition, String failureMessage) {
        assertTrue(testName, !condition, failureMessage);
    }

    private static void pass(String testName) {
        passedCount++;
        System.out.println("[PASS] " + testName);
    }

    private static void fail(String testName, String reason) {
        failedCount++;
        System.out.println("[FAIL] " + testName + " -> " + reason);
    }
}